#!/usr/bin/env python3
"""Resolve the immediately preceding stable release in the same MAJOR line."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass
from typing import Iterable


SEMVER_PATTERN = re.compile(
    r"(?P<major>0|[1-9][0-9]*)"
    r"\.(?P<minor>0|[1-9][0-9]*)"
    r"\.(?P<patch>0|[1-9][0-9]*)"
    r"(?:-(?P<prerelease>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)
STABLE_TAG_PATTERN = re.compile(
    r"v(?P<major>0|[1-9][0-9]*)"
    r"\.(?P<minor>0|[1-9][0-9]*)"
    r"\.(?P<patch>0|[1-9][0-9]*)"
)


@dataclass(frozen=True, order=True)
class ReleaseVersion:
    """Parsed semantic release identity."""

    major: int
    minor: int
    patch: int
    prerelease: str | None = None

    @property
    def stable(self) -> bool:
        """Whether this version carries no pre-release identifier."""
        return self.prerelease is None

    @property
    def core(self) -> tuple[int, int, int]:
        """Return the numeric precedence tuple."""
        return self.major, self.minor, self.patch

    def __str__(self) -> str:
        """Return canonical semantic-version text."""
        suffix = f"-{self.prerelease}" if self.prerelease else ""
        return f"{self.major}.{self.minor}.{self.patch}{suffix}"


def parse_release_version(value: str) -> ReleaseVersion:
    """Parse and validate one release version without build metadata."""
    match = SEMVER_PATTERN.fullmatch(value)
    if match is None:
        raise ValueError(
            f"release version must be semantic version text without build metadata: {value!r}"
        )
    prerelease = match.group("prerelease")
    for identifier in prerelease.split(".") if prerelease else ():
        if identifier.isdigit() and len(identifier) > 1 and identifier.startswith("0"):
            raise ValueError(
                "numeric prerelease identifiers must not contain leading zeroes: "
                f"{value!r}"
            )
    return ReleaseVersion(
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
        prerelease,
    )


def parse_stable_tag(tag: str) -> ReleaseVersion | None:
    """Parse a stable ``vMAJOR.MINOR.PATCH`` tag or return ``None``."""
    match = STABLE_TAG_PATTERN.fullmatch(tag)
    if match is None:
        return None
    return ReleaseVersion(
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
    )


def resolve_baseline(
    release_version: str,
    tags: Iterable[str],
) -> ReleaseVersion | None:
    """Return the latest earlier stable tag in the release's MAJOR line."""
    release = parse_release_version(release_version)
    candidates = [
        version
        for tag in tags
        if (version := parse_stable_tag(tag.strip())) is not None
        and version.major == release.major
        and version.core < release.core
    ]
    if candidates:
        return max(candidates)

    if release.stable and (release.minor != 0 or release.patch != 0):
        raise ValueError(
            f"stable release {release} has no earlier stable v{release.major}.x tag; "
            "release history may be shallow or incomplete"
        )
    return None


def repository_tags() -> list[str]:
    """Read Git tags reachable from the release commit."""
    result = subprocess.run(
        ["git", "tag", "--merged", "HEAD", "--list", "v*"],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--release-version", required=True)
    parser.add_argument(
        "--format",
        choices=("baseline", "json"),
        default="json",
        help="print only the baseline or a structured decision",
    )
    return parser.parse_args()


def main() -> int:
    """Resolve and print the compatibility-baseline decision."""
    args = parse_args()
    try:
        release = parse_release_version(args.release_version)
        baseline = resolve_baseline(args.release_version, repository_tags())
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit(
            f"API compatibility baseline resolution failed: {error}"
        ) from error

    if args.format == "baseline":
        print("" if baseline is None else baseline)
        return 0

    print(
        json.dumps(
            {
                "releaseVersion": str(release),
                "baselineVersion": None if baseline is None else str(baseline),
                "prerelease": not release.stable,
                "firstStableMajorRelease": (
                    release.stable
                    and release.minor == 0
                    and release.patch == 0
                    and baseline is None
                ),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
