#!/usr/bin/env python3
"""Verify the required bundled Workbench integration evidence in a JUnit report."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

if __package__:
    from .junit_evidence import parse_junit_report, status_counts
else:
    from junit_evidence import parse_junit_report, status_counts


TestIdentity = tuple[str, str]

REQUIRED_CASES: frozenset[TestIdentity] = frozenset(
    {
        ("server-api.spec.ts", "compileflow-workbench-server integration › health endpoint is UP"),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › protected APIs reject requests without the configured key",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › managed edge replaces client-supplied upstream credentials",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › examples catalog is served",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › monitoring trends honors interval=1m for 1h range",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › operations health endpoints expose deployment, runtime, and async state",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › dead-letter requeue operations return refreshed health snapshots",
        ),
        (
            "server-api.spec.ts",
            "compileflow-workbench-server integration › log delete returns deletedAt",
        ),
        (
            "workbench-deployment-lifecycle.spec.ts",
            "publishes, canaries, promotes, rolls back, and executes the effective versions",
        ),
        (
            "workbench-operations.spec.ts",
            "bundled Workbench loads and operates through the trusted edge",
        ),
        (
            "workbench-runtime-chain.spec.ts",
            "persisted async invocation is idempotent, version-pinned, and observable",
        ),
        (
            "workbench-runtime-chain.spec.ts",
            "persisted async invocation exhausts retries, dead-letters, and requeues the pinned version",
        ),
    }
)


def verify_report(
    path: Path, required_cases: frozenset[TestIdentity] = REQUIRED_CASES
) -> list[str]:
    cases, errors = parse_junit_report(path)
    executed = {case.identity for case in cases if all(case.identity)}
    actual = status_counts(cases)
    if actual["failures"] or actual["errors"] or actual["skipped"]:
        errors.append(
            "integration evidence is not clean: "
            f"failures={actual['failures']}, errors={actual['errors']}, skipped={actual['skipped']}"
        )

    for classname, name in sorted(required_cases - executed):
        errors.append(f"missing required testcase: {classname} :: {name}")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="Playwright JUnit XML report")
    args = parser.parse_args(argv)

    errors = verify_report(args.report)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Verified {len(REQUIRED_CASES)} required Workbench integration testcases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
