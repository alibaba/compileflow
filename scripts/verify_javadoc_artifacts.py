#!/usr/bin/env python3
"""Verify that release Javadoc archives cover every public top-level type."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


PUBLIC_TYPE = re.compile(
    r"^public\s+(?:(?:abstract|final|sealed|non-sealed)\s+)*"
    r"(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)\b",
    re.MULTILINE,
)
PACKAGE = re.compile(
    r"^package\s+([A-Za-z_$][A-Za-z0-9_$.]*)\s*;",
    re.MULTILINE,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "modules",
        nargs="+",
        type=Path,
        help="Maven module directories containing release Javadoc archives",
    )
    return parser.parse_args()


def expected_type_pages(module: Path) -> set[str]:
    """Return archive paths for public top-level Java types in one module."""
    source_root = module / "src" / "main" / "java"
    if not source_root.is_dir():
        raise ValueError(f"Java source directory is missing: {source_root}")
    pages: set[str] = set()
    for source in sorted(source_root.rglob("*.java")):
        if source.name in {"module-info.java", "package-info.java"}:
            continue
        text = source.read_text(encoding="utf-8")
        package_match = PACKAGE.search(text)
        if package_match is None:
            raise ValueError(f"Java source has no package declaration: {source}")
        package_path = package_match.group(1).replace(".", "/")
        for type_name in PUBLIC_TYPE.findall(text):
            pages.add(f"{package_path}/{type_name}.html")
    if not pages:
        raise ValueError(f"module has no public top-level Java types: {module}")
    return pages


def javadoc_archive(module: Path) -> Path:
    """Resolve exactly one non-empty release Javadoc archive."""
    archives = sorted((module / "target").glob("*-javadoc.jar"))
    if len(archives) != 1:
        raise ValueError(
            f"expected exactly one Javadoc archive in {module / 'target'}, "
            f"found {[archive.name for archive in archives]}"
        )
    archive = archives[0]
    if archive.stat().st_size == 0:
        raise ValueError(f"Javadoc archive is empty: {archive}")
    return archive


def validate_entry(name: str) -> str:
    """Reject duplicate-friendly or path-traversing ZIP names."""
    if "\\" in name:
        raise ValueError(f"Javadoc entry contains a backslash: {name!r}")
    path = PurePosixPath(name)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError(f"Javadoc entry is not normalized: {name!r}")
    if path.as_posix() != name.rstrip("/"):
        raise ValueError(f"Javadoc entry is not normalized: {name!r}")
    return path.as_posix()


def verify_module(module: Path) -> tuple[Path, int]:
    """Verify one module and return its archive and public-type count."""
    module = module.resolve()
    archive = javadoc_archive(module)
    expected = expected_type_pages(module)
    with zipfile.ZipFile(archive) as contents:
        names: set[str] = set()
        for entry in contents.infolist():
            name = validate_entry(entry.filename)
            if name in names:
                raise ValueError(f"Javadoc archive has duplicate entry: {name}")
            names.add(name)
    required = {"index.html", "element-list"} | expected
    missing = sorted(required - names)
    if missing:
        preview = ", ".join(missing[:8])
        suffix = " ..." if len(missing) > 8 else ""
        raise ValueError(
            f"Javadoc archive {archive} misses {len(missing)} required "
            f"entry or entries: {preview}{suffix}"
        )
    return archive, len(expected)


def main() -> int:
    args = parse_args()
    try:
        total_types = 0
        for module in args.modules:
            archive, type_count = verify_module(module)
            total_types += type_count
            print(
                f"Verified Javadoc archive: {archive} "
                f"(publicTypes={type_count})"
            )
    except (OSError, UnicodeError, ValueError, zipfile.BadZipFile) as error:
        print(f"Javadoc artifact verification failed: {error}", file=sys.stderr)
        return 1
    print(
        f"Verified {len(args.modules)} Javadoc archive(s), "
        f"publicTypes={total_types}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
