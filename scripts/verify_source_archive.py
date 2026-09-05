#!/usr/bin/env python3
"""Validate the repository source archive published with a release."""

from __future__ import annotations

import argparse
import tarfile
from pathlib import Path, PurePosixPath


FORBIDDEN_DIRECTORIES = {
    ".AppleDouble",
    "__pycache__",
    "coverage",
    "dist",
    "node_modules",
    "playwright-report",
    "target",
    "test-results",
}
ALLOWED_HIDDEN_DIRECTORIES = {
    ".github",
    ".mvn",
}
FORBIDDEN_FILES = {
    ".DS_Store",
    "dependency-reduced-pom.xml",
}
FORBIDDEN_SUFFIXES = {
    ".iml",
    ".pyc",
    ".tsbuildinfo",
}
REQUIRED_FILES = {
    ".mvn/wrapper/maven-wrapper.jar",
    ".mvn/wrapper/maven-wrapper.properties",
    "CONTRIBUTING.md",
    "LICENSE",
    "NOTICE",
    "README.md",
    "SECURITY.md",
    "compileflow-workbench/package.json",
    "compileflow-workbench/pnpm-lock.yaml",
    "compileflow-workbench/pnpm-workspace.yaml",
    "mvnw",
    "mvnw.cmd",
    "pom.xml",
    "release-baselines.json",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path, help="release source .tar.gz")
    parser.add_argument("version", help="release version without the v tag prefix")
    return parser.parse_args()


def validate_member_name(name: str, root: str) -> PurePosixPath:
    if "\\" in name:
        raise ValueError(f"archive path contains a backslash: {name!r}")
    path = PurePosixPath(name)
    parts = path.parts
    if not parts or path.is_absolute() or parts[0] != root:
        raise ValueError(f"archive member escapes the expected root {root!r}: {name!r}")
    normalized = path.as_posix()
    if normalized != name.rstrip("/") or any(part in {"", ".", ".."} for part in parts):
        raise ValueError(f"archive member is not normalized: {name!r}")
    return path


def validate_archive(archive_path: Path, version: str) -> int:
    if not archive_path.is_file() or archive_path.stat().st_size == 0:
        raise ValueError(f"source archive is missing or empty: {archive_path}")

    root = f"compileflow-{version}"
    seen: set[str] = set()
    regular_files: set[str] = set()
    file_sizes: dict[str, int] = {}
    executable_files: set[str] = set()

    with tarfile.open(archive_path, mode="r:gz") as archive:
        members = archive.getmembers()
        if not members:
            raise ValueError("source archive contains no members")

        for member in members:
            path = validate_member_name(member.name, root)
            normalized = path.as_posix()
            if normalized in seen:
                raise ValueError(f"source archive contains a duplicate member: {normalized}")
            seen.add(normalized)

            relative_parts = path.parts[1:]
            if any(part in FORBIDDEN_DIRECTORIES for part in relative_parts):
                raise ValueError(f"source archive contains a generated or private directory: {normalized}")
            directory_parts = relative_parts if member.isdir() else relative_parts[:-1]
            if any(
                part.startswith(".") and part not in ALLOWED_HIDDEN_DIRECTORIES
                for part in directory_parts
            ):
                raise ValueError(f"source archive contains an unapproved hidden directory: {normalized}")
            if relative_parts:
                basename = relative_parts[-1]
                if (
                    basename in FORBIDDEN_FILES
                    or basename.startswith("._")
                    or any(basename.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES)
                ):
                    raise ValueError(f"source archive contains a generated or private file: {normalized}")

            if member.issym() or member.islnk():
                raise ValueError(f"source archive must not contain links: {normalized}")
            if not member.isfile() and not member.isdir():
                raise ValueError(f"source archive contains an unsupported member: {normalized}")
            if member.isfile():
                relative = PurePosixPath(*relative_parts).as_posix()
                regular_files.add(relative)
                file_sizes[relative] = member.size
                if member.mode & 0o111:
                    executable_files.add(relative)

    missing = sorted(REQUIRED_FILES - regular_files)
    if missing:
        raise ValueError("source archive is missing required files: " + ", ".join(missing))
    empty = sorted(path for path in REQUIRED_FILES if file_sizes[path] == 0)
    if empty:
        raise ValueError("source archive contains empty required files: " + ", ".join(empty))
    if "mvnw" not in executable_files:
        raise ValueError("source archive Maven wrapper is not executable: mvnw")
    return len(regular_files)


def main() -> int:
    args = parse_args()
    try:
        file_count = validate_archive(args.archive, args.version)
    except (OSError, tarfile.TarError, ValueError) as error:
        raise SystemExit(f"Invalid source archive: {error}") from error
    print(f"Validated release source archive: files={file_count}, version={args.version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
