#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# How to run:
#   python3 scripts/qa/download_folia.py --server-dir .omo/qa/folia-server
#   python3 scripts/qa/download_folia.py --server-dir .omo/qa/folia-server --minecraft-version 1.21.8

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from collections.abc import Iterable, Sequence


PAPER_API = "https://api.papermc.io/v2/projects/folia"
USER_AGENT = "DouM-Folia-QA/1.0"
REQUEST_TIMEOUT_SECONDS = 30

JsonValue = str | int | float | bool | None | list["JsonValue"] | dict[str, "JsonValue"]


@dataclass(frozen=True, slots=True)
class DownloadError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class FoliaArtifact:
    version: str
    build: int
    jar_path: Path


@dataclass(frozen=True, slots=True)
class BuildDownload:
    build: int
    name: str
    sha256: str


def download_latest_compatible(
    server_dir: Path,
    compatible_versions: Iterable[str] = (),
) -> FoliaArtifact:
    cache_dir = server_dir / "cache"
    cache_dir.mkdir(parents=True, exist_ok=True)

    versions = project_versions()
    candidates = compatible_candidates(versions, compatible_versions)
    for version in candidates:
        download = latest_build_download(version)
        if download is None:
            continue
        jar_path = cache_dir / f"folia-{version}-{download.build}.jar"
        if valid_cached_file(jar_path, download.sha256):
            return FoliaArtifact(version, download.build, jar_path)
        download_build(version, download, jar_path)
        return FoliaArtifact(version, download.build, jar_path)

    raise DownloadError(
        "No Folia build matched Mineflayer-compatible versions: "
        + ", ".join(candidates[:10])
    )


def project_versions() -> list[str]:
    payload = fetch_json(PAPER_API)
    if not isinstance(payload, dict):
        raise DownloadError("PaperMC project response was not an object")
    raw_versions = payload.get("versions")
    if not isinstance(raw_versions, list):
        raise DownloadError("PaperMC project response did not include versions")
    versions = [item for item in raw_versions if isinstance(item, str)]
    if not versions:
        raise DownloadError("PaperMC project response included no Folia versions")
    return versions


def compatible_candidates(versions: Sequence[str], compatible_versions: Iterable[str]) -> list[str]:
    supported = {version.strip() for version in compatible_versions if version.strip()}
    newest_first = list(reversed(versions))
    if not supported:
        return newest_first
    return [version for version in newest_first if version in supported]


def latest_build_download(version: str) -> BuildDownload | None:
    payload = fetch_json(f"{PAPER_API}/versions/{version}/builds")
    if not isinstance(payload, dict):
        raise DownloadError(f"PaperMC builds response for {version} was not an object")
    raw_builds = payload.get("builds")
    if not isinstance(raw_builds, list):
        raise DownloadError(f"PaperMC builds response for {version} did not include builds")

    downloads: list[BuildDownload] = []
    for raw_build in raw_builds:
        parsed = parse_build_download(raw_build)
        if parsed is not None:
            downloads.append(parsed)
    if not downloads:
        return None
    return max(downloads, key=lambda item: item.build)


def parse_build_download(raw_build: JsonValue) -> BuildDownload | None:
    if not isinstance(raw_build, dict):
        return None
    build_number = raw_build.get("build")
    raw_downloads = raw_build.get("downloads")
    if not isinstance(build_number, int) or not isinstance(raw_downloads, dict):
        return None
    application = raw_downloads.get("application")
    if not isinstance(application, dict):
        return None
    name = application.get("name")
    sha256 = application.get("sha256")
    if not isinstance(name, str) or not isinstance(sha256, str):
        return None
    return BuildDownload(build_number, name, sha256)


def download_build(version: str, download: BuildDownload, destination: Path) -> None:
    url = (
        f"{PAPER_API}/versions/{version}/builds/{download.build}/"
        f"downloads/{download.name}"
    )
    temporary_path = destination.with_suffix(destination.suffix + ".tmp")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            with temporary_path.open("wb") as output:
                shutil.copyfileobj(response, output)
    except urllib.error.URLError as error:
        raise DownloadError(f"Could not download Folia from PaperMC: {error}") from error

    if not valid_cached_file(temporary_path, download.sha256):
        temporary_path.unlink(missing_ok=True)
        raise DownloadError(f"Downloaded Folia jar failed sha256 check: {destination.name}")
    temporary_path.replace(destination)


def valid_cached_file(path: Path, sha256: str) -> bool:
    return path.is_file() and sha256_file(path) == sha256.lower()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as input_file:
        for chunk in iter(lambda: input_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_json(url: str) -> JsonValue:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as error:
        raise DownloadError(f"Could not fetch PaperMC API {url}: {error}") from error
    except json.JSONDecodeError as error:
        raise DownloadError(f"PaperMC API returned invalid JSON for {url}") from error


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download a Folia server jar for QA.")
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--minecraft-version", action="append", default=[])
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        artifact = download_latest_compatible(args.server_dir, args.minecraft_version)
    except DownloadError as error:
        print(f"FAIL download-folia {error}", file=sys.stderr)
        return 1
    print(f"{artifact.jar_path}")
    print(f"Folia version={artifact.version} build={artifact.build}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
