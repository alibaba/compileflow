import re
import tempfile
import unittest
from pathlib import Path

from scripts.verify_database_contract_evidence import verify


def write_report(root: Path, test_class: str, outcome: str = "passed") -> None:
    report = root / test_class / "target" / "surefire-reports" / f"TEST-example.{test_class}.xml"
    report.parent.mkdir(parents=True, exist_ok=True)
    child = "" if outcome == "passed" else f"<{outcome}/>"
    counts = {"failures": 0, "errors": 0, "skipped": 0}
    if outcome != "passed":
        counts[{"failure": "failures", "error": "errors", "skipped": "skipped"}[outcome]] = 1
    report.write_text(
        f'<testsuite name="{test_class}" tests="1" failures="{counts["failures"]}" '
        f'errors="{counts["errors"]}" skipped="{counts["skipped"]}">'
        f'<testcase classname="example.{test_class}" name="contract">{child}</testcase></testsuite>',
        encoding="utf-8",
    )


class VerifyDatabaseContractEvidenceTest(unittest.TestCase):
    def test_mysql_workflow_requires_payload_and_aggregation_contracts(self) -> None:
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/workbench-server-ci.yml").read_text(encoding="utf-8")
        mysql_job = workflow.split("\n  mysql-contract:", 1)[1].split("\n  runtime-compatibility:", 1)[0]
        selected = set()
        for selection in re.findall(r"-Dtest=([^\s]+)", mysql_job):
            selected.update(selection.split(","))
        required = set(re.findall(r"--require\s+(\w+)", mysql_job))
        for test_class in ("ProcessDraftServicePersistenceTest", "ExecutionLogAggregationPersistenceTest"):
            with self.subTest(test_class=test_class):
                self.assertIn(test_class, selected)
                self.assertIn(test_class, required)
                self.assertIn(f"TEST-*{test_class}.xml", mysql_job)

    def test_rejects_report_containing_an_unrelated_test_class(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "UnrelatedTest")
            report = next(root.glob("**/TEST-*.xml"))
            report.rename(report.with_name("TEST-example.StoreContractTest.xml"))
            errors = verify(root, "MySQL", ["StoreContractTest"])
            self.assertTrue(any("classname" in error for error in errors))

    def test_accepts_clean_required_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "StoreContractTest")
            self.assertEqual([], verify(root, "MySQL", ["StoreContractTest"]))

    def test_rejects_missing_and_skipped_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "SkippedContractTest", "skipped")
            errors = verify(root, "MySQL", ["MissingContractTest", "SkippedContractTest"])
            self.assertTrue(any("found 0" in error for error in errors))
            self.assertTrue(any("skipped=1" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
