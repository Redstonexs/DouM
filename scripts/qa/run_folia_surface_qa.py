#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# How to run:
#   python3 scripts/qa/run_folia_surface_qa.py --plugin-jar build/libs/deathgates-*.jar --server-dir .omo/qa/folia-server --evidence .omo/evidence/task-7-folia-death-gates.log

from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import queue
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
import uuid
import zipfile
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

sys.dont_write_bytecode = True

from download_folia import DownloadError, FoliaArtifact, download_latest_compatible


READY_TIMEOUT_SECONDS = 180
BOT_TIMEOUT_SECONDS = 240
NPM_TIMEOUT_SECONDS = 180
STOP_TIMEOUT_SECONDS = 60
BASE_PORT = 25585
QA_PLAYER = "DeathGateQA"
ADMIN_PLAYER = "DeathGateAdmin"
PASS_MARKERS = (
    "PASS plugin-load",
    "PASS break-gate",
    "PASS place-gate",
    "PASS craft-gate",
    "PASS death-increment",
    "PASS persistence-restart",
    "PASS cleanup",
)


@dataclass(frozen=True, slots=True)
class QaError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class Paths:
    plugin_jar: Path
    server_dir: Path
    evidence: Path
    server_log: Path
    bot_log: Path
    mineflayer_work: Path


@dataclass(frozen=True, slots=True)
class CommandResult:
    stdout: str
    stderr: str


class Evidence:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def write(self, message: str) -> None:
        timestamp = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        with self.path.open("a", encoding="utf-8") as output:
            output.write(f"[{timestamp}] {message}\n")


class ManagedServer:
    def __init__(self, server_dir: Path, jar_path: Path, log_path: Path, evidence: Evidence) -> None:
        self.server_dir = server_dir
        self.jar_path = jar_path
        self.log_path = log_path
        self.evidence = evidence
        self.lines: queue.Queue[str] = queue.Queue()
        self.process: subprocess.Popen[str] | None = None
        self.reader: threading.Thread | None = None

    def start(self) -> None:
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        log_file = self.log_path.open("a", encoding="utf-8")
        process = subprocess.Popen(
            ["java", "-jar", str(self.jar_path), "--nogui"],
            cwd=self.server_dir,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        self.process = process

        def read_output() -> None:
            assert process.stdout is not None
            with log_file:
                for line in process.stdout:
                    log_file.write(line)
                    log_file.flush()
                    self.lines.put(line.rstrip("\n"))

        self.reader = threading.Thread(target=read_output, name="folia-log-reader", daemon=True)
        self.reader.start()

    def command(self, command: str) -> None:
        process = self.process
        if process is None or process.stdin is None:
            raise QaError("Folia server is not running")
        process.stdin.write(command + "\n")
        process.stdin.flush()

    def wait_ready(self) -> bool:
        deadline = time.monotonic() + READY_TIMEOUT_SECONDS
        saw_plugin = False
        plugin_load_error = ""
        while time.monotonic() < deadline:
            process = self.process
            if process is not None and process.poll() is not None:
                raise QaError(f"Folia server exited early with status {process.returncode}")
            try:
                line = self.lines.get(timeout=1)
            except queue.Empty:
                continue
            if "DouM enabled." in line:
                saw_plugin = True
            if "Could not load plugin 'DouM.jar'" in line:
                plugin_load_error = line
            if "Unsupported API version" in line:
                raise QaError(f"Folia rejected DouM plugin: {line}")
            if plugin_load_error and "Done (" in line:
                raise QaError(f"Folia did not load DouM plugin: {plugin_load_error}")
            if saw_plugin and ("Done (" in line or "For help, type" in line):
                return True
        return False

    def stop(self) -> None:
        process = self.process
        if process is None:
            return
        if process.poll() is None:
            try:
                self.command("stop")
                process.wait(timeout=STOP_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            except BrokenPipeError:
                process.terminate()
                process.wait(timeout=10)
        if self.reader is not None:
            self.reader.join(timeout=5)
        self.evidence.write(f"server stopped status={process.returncode}")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run real Folia DouM surface QA.")
    parser.add_argument("--plugin-jar", required=True, nargs="+")
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--evidence", required=True, type=Path)
    return parser.parse_args(argv)


def resolve_plugin_jar(values: Sequence[str]) -> Path:
    matches: list[Path] = []
    for value in values:
        expanded = [Path(match) for match in glob.glob(value)]
        matches.extend(expanded or [Path(value)])
    existing = [path for path in matches if path.is_file()]
    if len(existing) != 1:
        raise QaError(f"--plugin-jar must resolve to exactly one file, found {len(existing)}")
    return existing[0].resolve()


def choose_port() -> int:
    for port in range(BASE_PORT, BASE_PORT + 20):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(("127.0.0.1", port))
            except OSError:
                continue
            return port
    raise QaError(f"No localhost port available in range {BASE_PORT}-{BASE_PORT + 19}")


def prepare_paths(args: argparse.Namespace) -> Paths:
    plugin_jar = resolve_plugin_jar(args.plugin_jar)
    server_dir = args.server_dir.resolve()
    return Paths(
        plugin_jar=plugin_jar,
        server_dir=server_dir,
        evidence=args.evidence.resolve(),
        server_log=server_dir / "logs" / "deathgates-server.log",
        bot_log=server_dir / "logs" / "deathgates-bot.log",
        mineflayer_work=server_dir / "mineflayer-work",
    )


def prepare_server(paths: Paths, port: int) -> None:
    paths.server_dir.mkdir(parents=True, exist_ok=True)
    clean_runtime(paths.server_dir)
    (paths.server_dir / "plugins").mkdir(parents=True, exist_ok=True)
    (paths.server_dir / "logs").mkdir(parents=True, exist_ok=True)
    shutil.copy2(paths.plugin_jar, paths.server_dir / "plugins" / "DouM.jar")
    write_text(paths.server_dir / "eula.txt", "eula=true\n")
    write_text(paths.server_dir / "server.properties", server_properties(port))
    write_text(paths.server_dir / "ops.json", ops_json())
    write_text(paths.server_dir / "plugins" / "DouM" / "config.yml", qa_config())


def clean_runtime(server_dir: Path) -> None:
    keep = {"cache"}
    for child in server_dir.iterdir() if server_dir.exists() else ():
        if child.name in keep:
            continue
        if child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink()


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def server_properties(port: int) -> str:
    return "\n".join(
        (
            f"server-port={port}",
            "server-ip=127.0.0.1",
            "online-mode=false",
            "spawn-protection=0",
            "enforce-secure-profile=false",
            "enable-command-block=false",
            "gamemode=survival",
            "force-gamemode=false",
            "difficulty=peaceful",
            "pvp=false",
            "view-distance=3",
            "simulation-distance=3",
            "max-players=8",
            "level-seed=deathgates-surface-qa",
            "motd=DouM Folia QA",
            "allow-nether=false",
        )
    ) + "\n"


def offline_uuid(player_name: str) -> str:
    digest = hashlib.md5(f"OfflinePlayer:{player_name}".encode("utf-8")).digest()
    return str(uuid.UUID(bytes=digest, version=3))


def ops_json() -> str:
    return json.dumps(
        [
            {
                "uuid": offline_uuid(ADMIN_PLAYER),
                "name": ADMIN_PLAYER,
                "level": 4,
                "bypassesPlayerLimit": False,
            }
        ],
        indent=2,
    ) + "\n"


def qa_config() -> str:
    return """operations:
  block-break:
    enabled: true
    default-required-deaths: 0
    bypass-permission: deathgates.bypass.block-break
    deny-message: "QA_DENIED {player} {operation} {target} {required} {actual}"
    targets:
      "material:minecraft:dirt": 1
  block-place:
    enabled: true
    default-required-deaths: 0
    bypass-permission: deathgates.bypass.block-place
    deny-message: "QA_DENIED {player} {operation} {target} {required} {actual}"
    targets:
      "material:minecraft:gold_block": 1
  craft-item:
    enabled: true
    default-required-deaths: 0
    bypass-permission: deathgates.bypass.craft-item
    deny-message: "QA_DENIED {player} {operation} {target} {required} {actual}"
    targets:
      "recipe:minecraft:oak_planks": 1
      "result:minecraft:oak_planks": 1
"""


def prepare_mineflayer_work(paths: Paths) -> None:
    source_dir = Path("qa/mineflayer").resolve()
    if not (source_dir / "package-lock.json").is_file():
        raise QaError("qa/mineflayer/package-lock.json is missing; run npm install first")
    shutil.rmtree(paths.mineflayer_work, ignore_errors=True)
    paths.mineflayer_work.mkdir(parents=True, exist_ok=True)
    for name in ("package.json", "package-lock.json", "deathgates_surface_qa.mjs"):
        shutil.copy2(source_dir / name, paths.mineflayer_work / name)


def run_command(
    command: Sequence[str],
    cwd: Path,
    timeout_seconds: int,
    evidence: Evidence,
) -> CommandResult:
    evidence.write(f"RUN {' '.join(command)} cwd={cwd}")
    try:
        completed = subprocess.run(
            command,
            cwd=cwd,
            text=True,
            capture_output=True,
            timeout=timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as error:
        raise QaError(f"Command timed out after {timeout_seconds}s: {' '.join(command)}") from error
    evidence.write(f"EXIT {completed.returncode} {' '.join(command)}")
    if completed.returncode != 0:
        evidence.write(completed.stdout[-4000:])
        evidence.write(completed.stderr[-4000:])
        raise QaError(f"Command failed with status {completed.returncode}: {' '.join(command)}")
    return CommandResult(completed.stdout, completed.stderr)


def npm_ci(paths: Paths, evidence: Evidence) -> None:
    run_command(
        ["npm", "ci", "--ignore-scripts", "--no-audit", "--fund=false"],
        paths.mineflayer_work,
        NPM_TIMEOUT_SECONDS,
        evidence,
    )


def mineflayer_versions(paths: Paths, evidence: Evidence) -> list[str]:
    script = (
        "const data=require('minecraft-data');"
        "console.log([...new Set(data.versions.pc.map(v=>v.minecraftVersion))].join('\\n'))"
    )
    result = run_command(["node", "-e", script], paths.mineflayer_work, 30, evidence)
    versions = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not versions:
        raise QaError("Mineflayer minecraft-data exposed no supported versions")
    evidence.write("Mineflayer supported versions: " + ", ".join(versions[-8:]))
    return versions


def verify_plugin_metadata(plugin_jar: Path) -> None:
    with zipfile.ZipFile(plugin_jar) as archive:
        try:
            plugin_yml = archive.read("plugin.yml").decode("utf-8")
        except KeyError as error:
            raise QaError("Plugin jar does not contain plugin.yml") from error
    if "folia-supported: true" not in plugin_yml:
        raise QaError("plugin.yml does not contain folia-supported: true")


def run_bot(paths: Paths, port: int, mode: str, evidence: Evidence) -> str:
    env = os.environ.copy()
    env.update(
        {
            "QA_HOST": "127.0.0.1",
            "QA_PORT": str(port),
            "QA_PLAYER": QA_PLAYER,
            "QA_PLAYER_UUID": offline_uuid(QA_PLAYER),
            "QA_ADMIN": ADMIN_PLAYER,
            "QA_MODE": mode,
            "QA_TIMEOUT_MS": str(BOT_TIMEOUT_SECONDS * 1000),
        }
    )
    try:
        completed = subprocess.run(
            ["node", "deathgates_surface_qa.mjs"],
            cwd=paths.mineflayer_work,
            env=env,
            text=True,
            capture_output=True,
            timeout=BOT_TIMEOUT_SECONDS,
            check=False,
        )
    except subprocess.TimeoutExpired as error:
        raise QaError(f"Mineflayer bot timed out in mode={mode}") from error
    with paths.bot_log.open("a", encoding="utf-8") as output:
        output.write(f"\n--- mode={mode} stdout ---\n{completed.stdout}")
        output.write(f"\n--- mode={mode} stderr ---\n{completed.stderr}")
    evidence.write(f"Mineflayer mode={mode} exit={completed.returncode}")
    evidence.write(completed.stdout[-6000:])
    if completed.returncode != 0:
        evidence.write(completed.stderr[-6000:])
        raise QaError(f"Mineflayer bot failed in mode={mode} with status {completed.returncode}")
    return completed.stdout


def start_server(paths: Paths, artifact: FoliaArtifact, evidence: Evidence) -> ManagedServer:
    server = ManagedServer(paths.server_dir, artifact.jar_path, paths.server_log, evidence)
    server.start()
    if not server.wait_ready():
        server.stop()
        raise QaError("Folia server did not become ready before timeout")
    return server


def append_server_excerpt(paths: Paths, evidence: Evidence) -> None:
    if not paths.server_log.is_file():
        return
    lines = paths.server_log.read_text(encoding="utf-8", errors="replace").splitlines()
    evidence.write("server log excerpt:\n" + "\n".join(lines[-80:]))


def assert_markers(output: str, markers: Sequence[str]) -> None:
    missing = [marker for marker in markers if marker not in output]
    if missing:
        raise QaError("Mineflayer output missing markers: " + ", ".join(missing))


def run_surface_qa(paths: Paths, evidence: Evidence) -> None:
    verify_plugin_metadata(paths.plugin_jar)
    port = choose_port()
    prepare_server(paths, port)
    prepare_mineflayer_work(paths)
    npm_ci(paths, evidence)
    artifact = download_latest_compatible(paths.server_dir, mineflayer_versions(paths, evidence))
    evidence.write(f"Using Folia version={artifact.version} build={artifact.build} jar={artifact.jar_path}")

    server: ManagedServer | None = None
    try:
        server = start_server(paths, artifact, evidence)
        evidence.write("PASS plugin-load")
        output = run_bot(paths, port, "surface", evidence)
        assert_markers(output, ("PASS break-gate", "PASS place-gate", "PASS craft-gate", "PASS death-increment"))
    finally:
        if server is not None:
            server.stop()

    server = None
    try:
        server = start_server(paths, artifact, evidence)
        output = run_bot(paths, port, "persistence", evidence)
        assert_markers(output, ("PASS persistence-restart",))
    finally:
        if server is not None:
            server.stop()
    evidence.write("PASS cleanup")
    append_server_excerpt(paths, evidence)


def signal_exit(signum: int, _frame: object) -> None:
    raise SystemExit(128 + signum)


def main(argv: Sequence[str] | None = None) -> int:
    signal.signal(signal.SIGTERM, signal_exit)
    args = parse_args(sys.argv[1:] if argv is None else argv)
    evidence = Evidence(args.evidence.resolve())
    paths: Paths | None = None
    try:
        paths = prepare_paths(args)
        evidence.write("Todo 7 Folia surface QA starting")
        run_surface_qa(paths, evidence)
        for marker in PASS_MARKERS:
            if marker != "PASS cleanup":
                evidence.write(f"verified marker {marker}")
        return 0
    except (DownloadError, QaError) as error:
        if paths is not None:
            append_server_excerpt(paths, evidence)
        evidence.write(f"FAIL folia-surface-qa {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
