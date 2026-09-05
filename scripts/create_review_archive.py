#!/usr/bin/env python3
"""Create and verify a deterministic review archive from Git's source file contract."""

from __future__ import annotations

import argparse
import os
import subprocess
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

from verify_source_archive import (
    FORBIDDEN_DIRECTORIES,
    FORBIDDEN_FILES,
    FORBIDDEN_SUFFIXES,
    REQUIRED_FILES,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path, help="destination .tar.zst outside the repository")
    return parser.parse_args()


def repository_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], check=True, stdout=subprocess.PIPE, text=True
    )
    return Path(result.stdout.strip()).resolve()


def selected_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    )
    selected: list[Path] = []
    seen: set[str] = set()
    for encoded in result.stdout.split(b"\0"):
        if not encoded:
            continue
        relative = Path(os.fsdecode(encoded))
        source = root / relative
        if not source.exists():
            continue
        normalized = PurePosixPath(relative.as_posix())
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"review archive source must be a regular file: {normalized}")
        if any(part in FORBIDDEN_DIRECTORIES for part in normalized.parts):
            raise ValueError(f"generated or private directory selected by Git contract: {normalized}")
        basename = normalized.name
        if (
            basename in FORBIDDEN_FILES
            or basename.startswith("._")
            or any(basename.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES)
        ):
            raise ValueError(f"generated or private file selected by Git contract: {normalized}")
        name = normalized.as_posix()
        if name in seen:
            raise ValueError(f"duplicate review archive source: {name}")
        seen.add(name)
        selected.append(relative)
    missing = sorted(REQUIRED_FILES - seen)
    if missing:
        raise ValueError("review archive is missing required source files: " + ", ".join(missing))
    return sorted(selected, key=lambda item: item.as_posix())


def normalize_tar_info(info: tarfile.TarInfo) -> tarfile.TarInfo:
    """Remove host metadata while preserving whether a source file is executable."""
    info.uid = 0
    info.gid = 0
    info.uname = ""
    info.gname = ""
    info.mtime = 0
    info.mode = 0o755 if info.mode & 0o111 else 0o644
    info.pax_headers = {}
    return info


def verify_archive(archive_path: Path, files: list[Path]) -> None:
    """Verify the compressed artifact, not only the source selection."""
    expected = {f"compileflow/{relative.as_posix()}" for relative in files}
    required = {f"compileflow/{relative}" for relative in REQUIRED_FILES}
    seen: set[str] = set()
    sizes: dict[str, int] = {}
    executable: set[str] = set()
    decompressor = subprocess.Popen(
        ["zstd", "--decompress", "--quiet", "--stdout", archive_path],
        stdout=subprocess.PIPE,
    )
    assert decompressor.stdout is not None
    try:
        with tarfile.open(fileobj=decompressor.stdout, mode="r|") as archive:
            for member in archive:
                if member.name in seen:
                    raise ValueError(f"review archive contains a duplicate member: {member.name}")
                if member.name not in expected:
                    raise ValueError(f"review archive contains an unexpected member: {member.name}")
                if not member.isfile() or member.issym() or member.islnk():
                    raise ValueError(f"review archive member is not a regular file: {member.name}")
                seen.add(member.name)
                sizes[member.name] = member.size
                if member.mode & 0o111:
                    executable.add(member.name)
    finally:
        decompressor.stdout.close()
    if decompressor.wait() != 0:
        raise RuntimeError("zstd failed while verifying review archive")
    if seen != expected:
        missing = sorted(expected - seen)
        raise ValueError("review archive is missing selected files: " + ", ".join(missing))
    empty_required = sorted(name for name in required if sizes[name] == 0)
    if empty_required:
        raise ValueError("review archive contains empty required files: " + ", ".join(empty_required))
    if "compileflow/mvnw" not in executable:
        raise ValueError("review archive Maven wrapper is not executable: mvnw")


def create_archive(root: Path, output: Path, files: list[Path]) -> None:
    destination = output.expanduser().resolve()
    if destination.suffixes[-2:] != [".tar", ".zst"]:
        raise ValueError("review archive output must end with .tar.zst")
    try:
        destination.relative_to(root)
    except ValueError:
        pass
    else:
        raise ValueError("review archive output must be outside the repository")
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=destination.name + ".", dir=destination.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        with temporary.open("wb") as compressed:
            compressor = subprocess.Popen(
                ["zstd", "-19", "--threads=0", "--quiet", "--stdout"],
                stdin=subprocess.PIPE,
                stdout=compressed,
            )
            assert compressor.stdin is not None
            try:
                with tarfile.open(fileobj=compressor.stdin, mode="w|") as archive:
                    for relative in files:
                        archive.add(
                            root / relative,
                            arcname=PurePosixPath("compileflow") / relative,
                            recursive=False,
                            filter=normalize_tar_info,
                        )
            finally:
                compressor.stdin.close()
            if compressor.wait() != 0:
                raise RuntimeError("zstd failed while creating review archive")
        verify_archive(temporary, files)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    arguments = parse_args()
    root = repository_root()
    files = selected_files(root)
    create_archive(root, arguments.output, files)
    print(f"Created review-equivalent archive: {arguments.output.resolve()} (files={len(files)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
