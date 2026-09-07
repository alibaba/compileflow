#!/usr/bin/env python3
"""Check recursive English/Chinese documentation parity and Chinese typography.

Human-readable documentation lives under ``docs/en`` and ``docs/zh`` with the
same relative path in both locale trees. Language-neutral generated contracts
and fixtures under ``docs/specs`` are intentionally outside this check.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EN_DIR = ROOT / "docs" / "en"
ZH_DIR = ROOT / "docs" / "zh"

SECTION_HEADER_RE = re.compile(r"^#{1,6}\s+\S", re.MULTILINE)
AVOIDABLE_CHINESE_SPACE_RE = re.compile(
    r"(?<=[\u3400-\u9fff]) +(?=[\u3400-\u9fff，。；：、！？）】])"
    r"|(?<=[A-Za-z0-9`]) +(?=[，。；：、！？）】])"
    r"|(?<=[，。；：、！？）】]) +(?=[\u3400-\u9fffA-Za-z0-9`])"
)
FENCED_CODE_RE = re.compile(r"^[ ]{0,3}(`{3,}|~{3,})")


def markdown_files(directory: Path) -> dict[Path, Path]:
    """Return Markdown files keyed by their locale-relative path."""
    return {path.relative_to(directory): path for path in directory.rglob("*.md")}


def count_section_headers(path: Path) -> int:
    text = path.read_text(encoding="utf-8", errors="replace")
    return len(SECTION_HEADER_RE.findall(text))


def avoidable_chinese_spacing_lines(text: str) -> list[int]:
    """Return prose lines containing mechanical spaces around Chinese text."""
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
            elif AVOIDABLE_CHINESE_SPACE_RE.search(line):
                findings.append(line_number)
            continue

        if fence and fence.group(1)[0] == fence_character:
            marker = fence.group(1)
            remainder = line[fence.end():]
            if len(marker) >= fence_length and not remainder.strip():
                fence_character = None
                fence_length = 0

    return findings


def check_file_parity() -> list[str]:
    errors: list[str] = []
    en_files = markdown_files(EN_DIR)
    zh_files = markdown_files(ZH_DIR)

    for relative in sorted(en_files.keys() - zh_files.keys()):
        errors.append(f"docs/en/{relative} has no counterpart at docs/zh/{relative}")
    for relative in sorted(zh_files.keys() - en_files.keys()):
        errors.append(f"docs/zh/{relative} has no counterpart at docs/en/{relative}")

    return errors


def section_count_warnings() -> list[str]:
    """Surface large translation-depth drift without blocking natural translations."""
    warnings: list[str] = []
    en_files = markdown_files(EN_DIR)
    zh_files = markdown_files(ZH_DIR)

    for relative in sorted(en_files.keys() & zh_files.keys()):
        en_count = count_section_headers(en_files[relative])
        zh_count = count_section_headers(zh_files[relative])
        max_count = max(en_count, zh_count)
        if max_count == 0:
            continue
        ratio = abs(en_count - zh_count) / max_count
        if ratio > 0.5:
            warnings.append(
                f"section count differs by {ratio:.0%} for {relative} "
                f"(en={en_count}, zh={zh_count}); review translation depth"
            )

    return warnings


def check_chinese_typography() -> list[str]:
    errors: list[str] = []
    for relative, path in sorted(markdown_files(ZH_DIR).items()):
        text = path.read_text(encoding="utf-8", errors="replace")
        for line_number in avoidable_chinese_spacing_lines(text):
            errors.append(
                f"docs/zh/{relative}:{line_number} contains avoidable whitespace "
                "inside Chinese prose"
            )
    return errors


def main() -> int:
    errors = check_file_parity() + check_chinese_typography()
    warnings = section_count_warnings()

    if errors:
        print("Bilingual parity check FAILED:")
        for error in errors:
            print(f"  - {error}")
    if warnings:
        print("Bilingual parity warnings (informational):")
        for warning in warnings:
            print(f"  - {warning}")
    if not errors and not warnings:
        print("Bilingual parity check passed (recursive paths and section depth aligned).")
    elif not errors:
        print("Bilingual parity check passed (recursive paths aligned).")

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
