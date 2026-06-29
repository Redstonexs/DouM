#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///

# How to run
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly:
#      uv run scripts/qa/check_release_docs.py
# 3. Or with Python from the repo root:
#      python3 scripts/qa/check_release_docs.py

from __future__ import annotations

from pathlib import Path
from typing import Final


README_PATH: Final = Path("README.md")

REQUIRED_SNIPPETS: Final[tuple[tuple[str, str], ...]] = (
    ("Folia support note", "folia-supported: true"),
    ("install jar location", "plugins/"),
    ("block break operation", "block-break"),
    ("block place operation", "block-place"),
    ("craft item operation", "craft-item"),
    ("material target syntax", "material:minecraft:<key>"),
    ("result target syntax", "result:minecraft:<key>"),
    ("recipe target syntax", "recipe:minecraft:<key>"),
    ("gate resolution target overrides", "target-specific override"),
    ("gate resolution default", "default-required-deaths"),
    ("disabled operation behavior", "disabled operation allows"),
    ("bypass permission behavior", "deathgates.bypass.*"),
    ("reload command", "/doum reload"),
    ("deaths command", "/doum deaths <online-player>"),
    ("setdeaths command", "/doum setdeaths <online-player> <count>"),
    ("admin reload permission", "deathgates.admin.reload"),
    ("admin view permission", "deathgates.admin.view"),
    ("admin set permission", "deathgates.admin.set"),
    ("UUID persistence", "UUID"),
    ("data.yml persistence", "data.yml"),
    ("name metadata only", "player names are metadata only"),
    ("limitation economy", "No economy"),
    ("limitation database", "database"),
    ("limitation web", "web"),
    ("limitation GUI", "GUI"),
    ("limitation scoreboard", "scoreboard"),
    ("limitation PlaceholderAPI", "PlaceholderAPI"),
    ("limitation update checker", "update checker"),
    ("limitation broad versions", "No broad multi-version promise"),
    ("Gradle QA command", "GRADLE_USER_HOME=.gradle-user-home ./gradlew --no-daemon clean test build"),
    ("Folia QA command", "python3 scripts/qa/run_folia_surface_qa.py"),
    ("Folia QA PASS plugin-load", "PASS plugin-load"),
    ("Folia QA PASS break-gate", "PASS break-gate"),
    ("Folia QA PASS place-gate", "PASS place-gate"),
    ("Folia QA PASS craft-gate", "PASS craft-gate"),
    ("Folia QA PASS death-increment", "PASS death-increment"),
    ("Folia QA PASS persistence-restart", "PASS persistence-restart"),
    ("Folia QA PASS cleanup", "PASS cleanup"),
)


def collect_failures(readme_text: str) -> list[str]:
    return [label for label, snippet in REQUIRED_SNIPPETS if snippet not in readme_text]


def main() -> int:
    if not README_PATH.exists():
        print(f"FAIL release docs: {README_PATH} is missing")
        return 1

    failures = collect_failures(README_PATH.read_text(encoding="utf-8"))
    if failures:
        print("FAIL release docs: missing required README content")
        for label in failures:
            print(f"- {label}")
        return 1

    print("PASS release docs: README contains required Folia, config, command, persistence, limitation, and QA content")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
