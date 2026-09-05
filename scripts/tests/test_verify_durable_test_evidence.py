import tempfile
import unittest
from pathlib import Path

from scripts.verify_durable_test_evidence import (
    EvidenceError,
    build_evidence,
    parse_metadata,
)


def report(
    directory: Path,
    name: str,
    *,
    tests: int = 3,
    failures: int = 0,
    errors: int = 0,
    skipped: int = 0,
    flakes: int = 0,
) -> Path:
    outcomes = (
        ["failure"] * failures
        + ["error"] * errors
        + ["skipped"] * skipped
        + [""] * (tests - failures - errors - skipped)
    )
    testcases = []
    for index, outcome in enumerate(outcomes):
        body = f"<{outcome}/>" if outcome else ""
        if flakes and index == len(outcomes) - 1:
            body += "<flakyFailure/>"
        testcases.append(
            f'<testcase classname="{name}" name="case{index}">'
            f"{body}</testcase>"
        )
    path = directory / f"TEST-{name}.xml"
    path.write_text(
        (
            f'<testsuite name="{name}" tests="{tests}" '
            f'failures="{failures}" errors="{errors}" '
            f'skipped="{skipped}" flakes="{flakes}">'
            + "".join(testcases)
            + "</testsuite>"
        ),
        encoding="utf-8",
    )
    return path


class VerifyDurableTestEvidenceTest(unittest.TestCase):

    def test_builds_clean_deterministic_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = [
                report(root, "B", tests=2),
                report(root, "A", tests=3),
            ]

            evidence = build_evidence(
                suite="postgres-contract",
                reports=reports,
                expected_reports=2,
                minimum_tests=5,
                reject_skips=True,
                required_testcases=["A#case0", "B#case1"],
                metadata={"postgres": "18.6", "commit": "abc"},
            )

        self.assertEqual(
            "compileflow-durable-test-evidence/v2",
            evidence["schema"],
        )
        self.assertEqual(5, evidence["summary"]["passed"])
        self.assertEqual(2, evidence["summary"]["reports"])
        self.assertEqual(
            ["TEST-A.xml", "TEST-B.xml"],
            [item["file"] for item in evidence["reports"]],
        )
        self.assertEqual(
            {"commit": "abc", "postgres": "18.6"},
            evidence["metadata"],
        )
        self.assertEqual(
            ["A#case0", "B#case1"],
            evidence["policy"]["requiredTestCases"],
        )
        self.assertEqual(1, evidence["policy"]["minimumPassed"])

    def test_rejects_missing_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [report(Path(directory), "Only")]

            with self.assertRaisesRegex(
                EvidenceError, "expected 2 reports, found 1"
            ):
                build_evidence(
                    suite="postgres-contract",
                    reports=reports,
                    expected_reports=2,
                    minimum_tests=1,
                    reject_skips=True,
                )

    def test_rejects_failures_even_when_skips_are_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [
                report(Path(directory), "Failed", failures=1)
            ]

            with self.assertRaisesRegex(
                EvidenceError, "contains failures"
            ):
                build_evidence(
                    suite="kernel",
                    reports=reports,
                    expected_reports=None,
                    minimum_tests=1,
                    reject_skips=False,
                )

    def test_rejects_skips_for_non_skippable_suite(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [
                report(Path(directory), "Skipped", skipped=1)
            ]

            with self.assertRaisesRegex(
                EvidenceError, "contains skipped tests"
            ):
                build_evidence(
                    suite="example",
                    reports=reports,
                    expected_reports=1,
                    minimum_tests=1,
                    reject_skips=True,
                )

    def test_records_allowed_skips(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [
                report(Path(directory), "Optional", skipped=1)
            ]

            evidence = build_evidence(
                suite="kernel",
                reports=reports,
                expected_reports=1,
                minimum_tests=1,
                reject_skips=False,
            )

        self.assertEqual(1, evidence["summary"]["skipped"])
        self.assertEqual(2, evidence["summary"]["passed"])

    def test_rejects_when_too_few_tests_actually_passed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [
                report(Path(directory), "MostlySkipped", tests=3, skipped=2)
            ]

            with self.assertRaisesRegex(
                EvidenceError, "expected at least 2 passed tests"
            ):
                build_evidence(
                    suite="kernel",
                    reports=reports,
                    expected_reports=None,
                    minimum_tests=3,
                    minimum_passed=2,
                    reject_skips=False,
                )

    def test_rejects_duplicate_testsuite_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = report(root, "Duplicate")
            second = root / "TEST-Second.xml"
            second.write_text(first.read_text(encoding="utf-8"), encoding="utf-8")

            with self.assertRaisesRegex(
                EvidenceError, "duplicate testsuite names"
            ):
                build_evidence(
                    suite="kernel",
                    reports=[first, second],
                    expected_reports=2,
                    minimum_tests=1,
                    reject_skips=False,
                )

    def test_rejects_secret_metadata_and_duplicates(self) -> None:
        with self.assertRaisesRegex(EvidenceError, "may expose a secret"):
            parse_metadata(["database_password=value"])
        with self.assertRaisesRegex(EvidenceError, "duplicate metadata"):
            parse_metadata(["commit=one", "commit=two"])

    def test_rejects_missing_required_testcase_and_flake(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            reports = [report(Path(directory), "Required")]
            with self.assertRaisesRegex(
                EvidenceError,
                "required testcases are missing",
            ):
                build_evidence(
                    suite="kernel",
                    reports=reports,
                    expected_reports=1,
                    minimum_tests=1,
                    reject_skips=False,
                    required_testcases=["Required#absent"],
                )

        with tempfile.TemporaryDirectory() as directory:
            reports = [report(Path(directory), "Flaky", flakes=1)]
            with self.assertRaisesRegex(EvidenceError, "flaky tests"):
                build_evidence(
                    suite="kernel",
                    reports=reports,
                    expected_reports=1,
                    minimum_tests=1,
                    reject_skips=False,
                )


if __name__ == "__main__":
    unittest.main()
