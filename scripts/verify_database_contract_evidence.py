#!/usr/bin/env python3
"""Reject missing, failed, or skipped real-database JUnit contract evidence."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__:
    from .junit_evidence import parse_junit_report, status_counts
else:
    from junit_evidence import parse_junit_report, status_counts


def verify(root: Path, database: str, required_classes: list[str]) -> list[str]:
    errors: list[str] = []
    for test_class in required_classes:
        reports = sorted(root.glob(f"**/target/surefire-reports/TEST-*{test_class}.xml"))
        if len(reports) != 1:
            errors.append(f"expected one {database} report for {test_class}, found {len(reports)}")
            continue
        cases, parse_errors = parse_junit_report(reports[0])
        errors.extend(parse_errors)
        for case in cases:
            if case.classname.rsplit(".", 1)[-1] != test_class:
                errors.append(
                    f"{reports[0]}: expected classname for {test_class}, found {case.classname!r}"
                )
        counts = status_counts(cases)
        if counts["tests"] < 1 or any(counts[name] for name in ("failures", "errors", "skipped")):
            errors.append(
                f"{reports[0]}: {database} evidence is not clean: tests={counts['tests']}, "
                f"failures={counts['failures']}, errors={counts['errors']}, skipped={counts['skipped']}"
            )
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--require", action="append", required=True, dest="required_classes")
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args(argv)
    errors = verify(args.root, args.database, args.required_classes)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Verified {len(args.required_classes)} clean {args.database} contract reports")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
