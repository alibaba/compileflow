#!/usr/bin/env python3
"""Verify that SpotBugs completed a sound analysis for every requested module."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REPORT_PATH = Path("target/spotbugsXml.xml")


def parse_non_negative_count(element: ET.Element, attribute: str, report: Path) -> int:
    value = element.get(attribute)
    try:
        count = int(value) if value is not None else -1
    except ValueError:
        count = -1
    if count < 0:
        raise ValueError(
            f"{report}: Errors.{attribute} must be a non-negative integer, got {value!r}"
        )
    return count


def verify_report(report: Path) -> list[str]:
    errors: list[str] = []
    if not report.is_file():
        return [f"{report}: SpotBugs report is missing"]

    try:
        root = ET.parse(report).getroot()
    except (ET.ParseError, OSError) as failure:
        return [f"{report}: cannot parse SpotBugs report: {failure}"]

    if root.tag != "BugCollection":
        errors.append(f"{report}: expected BugCollection root, got {root.tag!r}")

    analysis_errors = root.find("Errors")
    if analysis_errors is None:
        errors.append(f"{report}: Errors summary is missing")
        return errors

    try:
        missing_count = parse_non_negative_count(analysis_errors, "missingClasses", report)
        error_count = parse_non_negative_count(analysis_errors, "errors", report)
    except ValueError as failure:
        errors.append(str(failure))
        return errors

    missing_classes = [
        child.text.strip()
        for child in analysis_errors.findall("MissingClass")
        if child.text and child.text.strip()
    ]
    if missing_count != len(missing_classes):
        errors.append(
            f"{report}: missingClasses={missing_count}, but the report names "
            f"{len(missing_classes)} missing classes"
        )
    if missing_count:
        details = ", ".join(missing_classes) if missing_classes else "details unavailable"
        errors.append(f"{report}: analysis is incomplete; missing classes: {details}")
    if error_count:
        errors.append(f"{report}: SpotBugs recorded {error_count} analysis error(s)")

    violations = len(root.findall("BugInstance"))
    if violations:
        errors.append(f"{report}: SpotBugs recorded {violations} violation(s)")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fail if requested SpotBugs XML reports are absent or incomplete."
    )
    parser.add_argument(
        "modules",
        nargs="+",
        type=Path,
        help="module directories whose target/spotbugsXml.xml reports must be sound",
    )
    args = parser.parse_args()

    reports = [module / REPORT_PATH for module in args.modules]
    errors = [error for report in reports for error in verify_report(report)]
    if errors:
        print("SpotBugs report verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Verified {len(reports)} complete SpotBugs report(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
