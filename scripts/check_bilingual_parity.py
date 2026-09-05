#!/usr/bin/env python3
"""Bilingual parity checker for CompileFlow documentation.

Verifies that user-facing documentation in `docs/en/` and `docs/zh/` is kept in
parity: every English file must have a Chinese counterpart and vice versa. It also
rejects avoidable spaces inside Chinese prose. Section header counts are reported as
informational warnings (not failures) so that natural differences in translation
depth do not block CI, but major content drift is surfaced.

Exit code 0 = parity OK (filenames match; section count warnings allowed).
Exit code 1 = parity or Chinese typography failure.

Run: python3 scripts/check_bilingual_parity.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Files that are intentionally single-language (governance, license, changelog, etc.).
# These follow industry conventions: CHANGELOG is English-only; top-level governance
# documents (LICENSE/NOTICE/SECURITY/CONTRIBUTING/CODE_OF_CONDUCT/MAINTAINERS/SUPPORT)
# are English-canonical.
SINGLE_LANGUAGE_TOP_LEVEL = {
    "README.md",
    "CHANGELOG.md",
    "LICENSE",
    "NOTICE",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "SUPPORT.md",
    "MAINTAINERS.md",
}

# Architecture and specs directory: files use `.en.md` / `.zh.md` suffixes.
SUFFIX_LANGUAGE_DIRS = {
    "docs/architecture",
    "docs/specs",
}

# Flat directory pairs: files have the same name in both `en/` and `zh/`.
FLAT_LANGUAGE_DIRS = {
    "docs/en": "docs/zh",
}


SECTION_HEADER_RE = re.compile(r"^#{1,6}\s+\S", re.MULTILINE)
AVOIDABLE_CHINESE_SPACE_RE = re.compile(
    r"(?<=[\u3400-\u9fff]) +(?=[\u3400-\u9fff，。；：、！？）】])"
    r"|(?<=[A-Za-z0-9`]) +(?=[，。；：、！？）】])"
    r"|(?<=[，。；：、！？）】]) +(?=[\u3400-\u9fffA-Za-z0-9`])"
)
FENCED_CODE_RE = re.compile(r"^[ ]{0,3}(`{3,}|~{3,})")


def count_section_headers(path: Path) -> int:
    """Count markdown section headers (lines starting with #..###### followed by text)."""
    text = path.read_text(encoding="utf-8", errors="replace")
    return len(SECTION_HEADER_RE.findall(text))


def avoidable_chinese_spacing_lines(text: str) -> list[int]:
    """Return lines containing spaces that interrupt Chinese prose."""
    findings: list[int] = []
    fence_character: str | None = None
    fence_length = 0
    for line_number, line in enumerate(text.splitlines(), start=1):
        fence = FENCED_CODE_RE.match(line)
        if fence_character is None:
            if fence:
                marker = fence.group(1)
                fence_character = marker[0]
                fence_length = len(marker)
                continue
            if AVOIDABLE_CHINESE_SPACE_RE.search(line):
                findings.append(line_number)
            continue

        if fence and fence.group(1)[0] == fence_character:
            marker = fence.group(1)
            remainder = line[fence.end():]
            if len(marker) >= fence_length and not remainder.strip():
                fence_character = None
                fence_length = 0
    return findings


def check_chinese_typography(directory: Path) -> list[str]:
    """Reject mechanical spacing artifacts in Chinese Markdown prose."""
    errors: list[str] = []
    for path in sorted(directory.rglob("*.md")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for line_number in avoidable_chinese_spacing_lines(text):
            errors.append(
                f"{path.relative_to(ROOT)}:{line_number} contains avoidable "
                "whitespace inside Chinese prose"
            )
    return errors


def check_flat_pair(en_dir: Path, zh_dir: Path) -> list[str]:
    """Check that every .md file in en_dir has a same-named .md file in zh_dir, and vice versa."""
    errors: list[str] = []
    en_files = {p.name for p in en_dir.glob("*.md") if p.name != "README.md"}
    zh_files = {p.name for p in zh_dir.glob("*.md") if p.name != "README.md"}

    en_only = en_files - zh_files
    zh_only = zh_files - en_files

    for name in sorted(en_only):
        errors.append(f"docs/en/{name} has no Chinese counterpart in docs/zh/")
    for name in sorted(zh_only):
        errors.append(f"docs/zh/{name} has no English counterpart in docs/en/")

    return errors


def check_suffix_pair(directory: Path) -> list[str]:
    """Check that every `.en.md` file has a `.zh.md` counterpart and vice versa."""
    errors: list[str] = []
    en_files = {p.name for p in directory.glob("*.en.md")}
    zh_files = {p.name for p in directory.glob("*.zh.md")}

    en_only = {n.replace(".en.md", ".md") for n in en_files} - {
        n.replace(".zh.md", ".md") for n in zh_files
    }
    zh_only = {n.replace(".zh.md", ".md") for n in zh_files} - {
        n.replace(".en.md", ".md") for n in en_files
    }

    for base in sorted(en_only):
        errors.append(f"{directory.relative_to(ROOT)}/{base.replace('.md', '.en.md')} has no Chinese counterpart")
    for base in sorted(zh_only):
        errors.append(f"{directory.relative_to(ROOT)}/{base.replace('.md', '.zh.md')} has no English counterpart")

    return errors


def report_section_count_warnings(en_dir: Path, zh_dir: Path) -> list[str]:
    """Report (not fail) when section header counts differ by more than 50%."""
    warnings: list[str] = []
    en_files = {p.name: p for p in en_dir.glob("*.md") if p.name != "README.md"}
    zh_files = {p.name: p for p in zh_dir.glob("*.md") if p.name != "README.md"}

    for name in sorted(set(en_files) & set(zh_files)):
        en_count = count_section_headers(en_files[name])
        zh_count = count_section_headers(zh_files[name])
        if en_count == 0 and zh_count == 0:
            continue
        diff = abs(en_count - zh_count)
        max_count = max(en_count, zh_count)
        if max_count == 0:
            continue
        ratio = diff / max_count
        if ratio > 0.5:
            warnings.append(
                f"Section header count differs by {ratio:.0%} for docs/en/{name} "
                f"(en={en_count}, zh={zh_count}); consider aligning content depth"
            )
    return warnings


def report_suffix_section_warnings(directory: Path) -> list[str]:
    """Report (not fail) when `.en.md` / `.zh.md` section counts differ significantly."""
    warnings: list[str] = []
    en_files = {p.stem.replace(".en", ""): p for p in directory.glob("*.en.md")}
    zh_files = {p.stem.replace(".zh", ""): p for p in directory.glob("*.zh.md")}

    for stem in sorted(set(en_files) & set(zh_files)):
        en_count = count_section_headers(en_files[stem])
        zh_count = count_section_headers(zh_files[stem])
        if en_count == 0 and zh_count == 0:
            continue
        diff = abs(en_count - zh_count)
        max_count = max(en_count, zh_count)
        if max_count == 0:
            continue
        ratio = diff / max_count
        if ratio > 0.5:
            rel = directory.relative_to(ROOT)
            warnings.append(
                f"Section header count differs by {ratio:.0%} for {rel}/{stem}.en.md "
                f"(en={en_count}, zh={zh_count}); consider aligning content depth"
            )
    return warnings


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    # Flat en/zh pairs (docs/en/, docs/zh/).
    for en_rel, zh_rel in FLAT_LANGUAGE_DIRS.items():
        en_dir = ROOT / en_rel
        zh_dir = ROOT / zh_rel
        if not en_dir.exists() or not zh_dir.exists():
            continue
        errors.extend(check_flat_pair(en_dir, zh_dir))
        warnings.extend(report_section_count_warnings(en_dir, zh_dir))

    # Suffix-pair directories (docs/architecture/, docs/specs/).
    for rel in SUFFIX_LANGUAGE_DIRS:
        directory = ROOT / rel
        if not directory.exists():
            continue
        errors.extend(check_suffix_pair(directory))
        warnings.extend(report_suffix_section_warnings(directory))

    errors.extend(check_chinese_typography(ROOT / "docs"))

    if errors:
        print("Bilingual parity check FAILED:")
        for err in errors:
            print(f"  - {err}")
    if warnings:
        print("Bilingual parity warnings (informational):")
        for w in warnings:
            print(f"  - {w}")
    if not errors and not warnings:
        print("Bilingual parity check passed (filenames match, section counts aligned).")
    elif not errors:
        print("Bilingual parity check passed (filenames match).")

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
