#!/usr/bin/env python3
"""Validate JUnit XML and emit deterministic Durable test evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

try:
    from scripts.evidence_io import write_json as write_evidence
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_json as write_evidence


EVIDENCE_SCHEMA = "compileflow-durable-test-evidence/v2"
COUNT_FIELDS = ("tests", "failures", "errors", "skipped", "flakes")
METADATA_KEY = re.compile(r"[a-z][a-z0-9_.-]{0,63}")
FORBIDDEN_METADATA = ("password", "secret", "token", "credential")


class EvidenceError(ValueError):
    """JUnit evidence is incomplete, invalid, or not clean."""


def parse_args() -> argparse.Namespace:
    """Parse the command-line evidence policy."""
    parser = argparse.ArgumentParser(
        description=(
            "Validate Durable JUnit XML reports and write a deterministic "
            "machine-readable evidence manifest."
        )
    )
    parser.add_argument("--suite", required=True)
    parser.add_argument(
        "--report-root",
        action="append",
        required=True,
        type=Path,
        help="Root searched for reports; repeat for multiple roots",
    )
    parser.add_argument(
        "--report-pattern",
        default="TEST-*.xml",
        help="Path.glob pattern relative to each report root",
    )
    parser.add_argument("--expected-reports", type=int)
    parser.add_argument("--minimum-tests", type=int, required=True)
    parser.add_argument("--minimum-passed", type=int, required=True)
    parser.add_argument("--reject-skips", action="store_true")
    parser.add_argument(
        "--require-testcase",
        action="append",
        default=[],
        help=(
            "Exact required passed testcase as CLASS#METHOD; repeat for "
            "each release-critical contract"
        ),
    )
    parser.add_argument(
        "--metadata",
        action="append",
        default=[],
        metavar="KEY=VALUE",
        help="Non-secret environment identity; repeat as needed",
    )
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def parse_count(value: str | None, field: str, path: Path) -> int:
    """Parse one non-negative JUnit count."""
    try:
        count = int(value or "0")
    except ValueError as failure:
        raise EvidenceError(
            f"{path}: {field} must be an integer, found {value!r}"
        ) from failure
    if count < 0:
        raise EvidenceError(
            f"{path}: {field} must be non-negative, found {count}"
        )
    return count


def parse_metadata(values: Iterable[str]) -> dict[str, str]:
    """Parse bounded non-secret metadata pairs."""
    result: dict[str, str] = {}
    for entry in values:
        key, separator, value = entry.partition("=")
        if not separator or not METADATA_KEY.fullmatch(key):
            raise EvidenceError(
                "metadata must use a lowercase KEY=VALUE pair with a "
                f"bounded key, found {entry!r}"
            )
        if any(marker in key for marker in FORBIDDEN_METADATA):
            raise EvidenceError(
                f"metadata key {key!r} may expose a secret and is forbidden"
            )
        if key in result:
            raise EvidenceError(f"duplicate metadata key {key!r}")
        if not value or len(value) > 1_024:
            raise EvidenceError(
                f"metadata value for {key!r} must contain 1..1024 characters"
            )
        result[key] = value
    return dict(sorted(result.items()))


def discover_reports(
    roots: Iterable[Path], pattern: str
) -> tuple[Path, ...]:
    """Discover a unique, stable report set."""
    reports = {
        path.resolve()
        for root in roots
        for path in root.glob(pattern)
        if path.is_file()
    }
    return tuple(sorted(reports))


def testcase_identity(classname: str, name: str) -> str:
    """Build one bounded, unambiguous JUnit testcase identity."""
    identity = f"{classname}#{name}"
    if (
        not classname
        or not name
        or len(identity) > 1_024
        or any(character in identity for character in "\r\n\0")
    ):
        raise EvidenceError(
            "testcase classname/name must be non-empty, bounded, and "
            "single-line"
        )
    return identity


def normalize_required_testcases(values: Iterable[str]) -> tuple[str, ...]:
    """Validate exact required testcase identities without duplicates."""
    result = tuple(values)
    invalid = [
        value
        for value in result
        if (
            not isinstance(value, str)
            or value.count("#") != 1
            or value.startswith("#")
            or value.endswith("#")
            or len(value) > 1_024
            or any(character in value for character in "\r\n\0")
        )
    ]
    if invalid:
        raise EvidenceError(
            f"required testcases must use exact CLASS#METHOD IDs: {invalid}"
        )
    duplicates = sorted(
        value
        for value, count in Counter(result).items()
        if count > 1
    )
    if duplicates:
        raise EvidenceError(
            f"duplicate required testcase identities: {duplicates}"
        )
    return tuple(sorted(result))


def parse_report(path: Path) -> dict[str, object]:
    """Parse one Surefire-style JUnit testsuite."""
    try:
        content = path.read_bytes()
        root = ET.fromstring(content)
    except (OSError, ET.ParseError) as failure:
        raise EvidenceError(f"{path}: cannot parse JUnit XML: {failure}") from failure
    if root.tag != "testsuite":
        raise EvidenceError(
            f"{path}: expected a testsuite root, found {root.tag!r}"
        )
    name = root.get("name", "")
    if not name or len(name) > 512:
        raise EvidenceError(
            f"{path}: testsuite name must contain 1..512 characters"
        )

    declared = {
        field: parse_count(root.get(field), field, path)
        for field in COUNT_FIELDS
    }
    testcases: list[dict[str, str]] = []
    identities: set[str] = set()
    calculated = {
        "tests": 0,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "flakes": 0,
    }
    for index, testcase in enumerate(root.findall("testcase")):
        identity = testcase_identity(
            testcase.get("classname", ""),
            testcase.get("name", ""),
        )
        if identity in identities:
            raise EvidenceError(
                f"{path}: duplicate testcase identity {identity!r}"
            )
        identities.add(identity)
        outcomes = [
            label
            for label in ("failure", "error", "skipped")
            if testcase.find(label) is not None
        ]
        flaky = (
            testcase.find("flakyFailure") is not None
            or testcase.find("flakyError") is not None
        )
        if flaky:
            calculated["flakes"] += 1
        if len(outcomes) > 1:
            raise EvidenceError(
                f"{path}: testcase {index} has conflicting outcomes"
            )
        status = outcomes[0].upper() if outcomes else "PASSED"
        calculated["tests"] += 1
        if outcomes:
            count_field = {
                "failure": "failures",
                "error": "errors",
                "skipped": "skipped",
            }[outcomes[0]]
            calculated[count_field] += 1
        testcases.append({"id": identity, "status": status})

    if declared != calculated:
        raise EvidenceError(
            f"{path}: declared counts do not match testcase outcomes: "
            f"declared={declared}, calculated={calculated}"
        )
    if calculated["flakes"]:
        raise EvidenceError(f"{path}: JUnit evidence contains flaky tests")
    non_passed = (
        calculated["failures"]
        + calculated["errors"]
        + calculated["skipped"]
    )
    return {
        "file": path.name,
        "name": name,
        "xmlSha256": hashlib.sha256(content).hexdigest(),
        **calculated,
        "passed": calculated["tests"] - non_passed,
        "testCases": testcases,
    }


def build_evidence(
    *,
    suite: str,
    reports: Iterable[Path],
    expected_reports: int | None,
    minimum_tests: int,
    reject_skips: bool,
    minimum_passed: int = 1,
    required_testcases: Iterable[str] = (),
    metadata: dict[str, str] | None = None,
) -> dict[str, object]:
    """Validate reports and build one deterministic evidence object."""
    if not suite or len(suite) > 128:
        raise EvidenceError("suite must contain 1..128 characters")
    if minimum_tests <= 0:
        raise EvidenceError("minimum_tests must be greater than zero")
    if minimum_passed <= 0:
        raise EvidenceError("minimum_passed must be greater than zero")
    if expected_reports is not None and expected_reports <= 0:
        raise EvidenceError("expected_reports must be greater than zero")

    report_paths = tuple(sorted({path.resolve() for path in reports}))
    if not report_paths:
        raise EvidenceError("no JUnit reports were discovered")
    if (
        expected_reports is not None
        and len(report_paths) != expected_reports
    ):
        raise EvidenceError(
            f"expected {expected_reports} reports, found {len(report_paths)}"
        )

    required = normalize_required_testcases(required_testcases)
    parsed = [parse_report(path) for path in report_paths]
    report_names = [str(report["name"]) for report in parsed]
    duplicate_names = sorted(
        name for name, count in Counter(report_names).items()
        if count > 1
    )
    if duplicate_names:
        raise EvidenceError(
            "JUnit evidence contains duplicate testsuite names: "
            + ", ".join(duplicate_names)
        )
    observed_testcases: dict[str, str] = {}
    for report in parsed:
        for testcase in report["testCases"]:
            identity = str(testcase["id"])
            if identity in observed_testcases:
                raise EvidenceError(
                    "JUnit evidence contains duplicate testcase identity: "
                    + identity
                )
            observed_testcases[identity] = str(testcase["status"])
    summary = {
        field: sum(int(report[field]) for report in parsed)
        for field in COUNT_FIELDS
    }
    summary["passed"] = sum(int(report["passed"]) for report in parsed)
    summary["reports"] = len(parsed)

    if summary["tests"] < minimum_tests:
        raise EvidenceError(
            f"expected at least {minimum_tests} tests, "
            f"found {summary['tests']}"
        )
    if summary["passed"] < minimum_passed:
        raise EvidenceError(
            f"expected at least {minimum_passed} passed tests, "
            f"found {summary['passed']}"
        )
    if summary["failures"] or summary["errors"] or summary["flakes"]:
        raise EvidenceError(f"JUnit evidence contains failures: {summary}")
    if reject_skips and summary["skipped"]:
        raise EvidenceError(f"JUnit evidence contains skipped tests: {summary}")
    missing = [
        identity
        for identity in required
        if observed_testcases.get(identity) != "PASSED"
    ]
    if missing:
        raise EvidenceError(
            "required testcases are missing or not passed: "
            + ", ".join(missing)
        )

    return {
        "schema": EVIDENCE_SCHEMA,
        "suite": suite,
        "policy": {
            "expectedReports": expected_reports,
            "minimumTests": minimum_tests,
            "minimumPassed": minimum_passed,
            "rejectSkips": reject_skips,
            "requiredTestCases": list(required),
        },
        "metadata": dict(sorted((metadata or {}).items())),
        "summary": summary,
        "reports": parsed,
    }


def main() -> int:
    """Validate the requested report set."""
    arguments = parse_args()
    try:
        metadata = parse_metadata(arguments.metadata)
        reports = discover_reports(
            arguments.report_root,
            arguments.report_pattern,
        )
        evidence = build_evidence(
            suite=arguments.suite,
            reports=reports,
            expected_reports=arguments.expected_reports,
            minimum_tests=arguments.minimum_tests,
            reject_skips=arguments.reject_skips,
            minimum_passed=arguments.minimum_passed,
            required_testcases=arguments.require_testcase,
            metadata=metadata,
        )
        write_evidence(arguments.output, evidence)
    except EvidenceError as failure:
        print(f"Durable test evidence failed: {failure}", file=sys.stderr)
        return 1

    print(
        "Validated Durable test evidence: "
        f"{evidence['suite']} {evidence['summary']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
