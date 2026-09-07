import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from scripts.verify_workbench_postgres_evidence import REQUIRED_METHODS, verify_reports


def write_report(
    root: Path,
    test_class: str,
    methods: set[str] | frozenset[str],
    *,
    module: str = "module",
    skipped: set[str] | None = None,
    declared_tests: int | None = None,
    classname: str | None = None,
) -> None:
    skipped = skipped or set()
    report_dir = root / module / "target" / "surefire-reports"
    report_dir.mkdir(parents=True, exist_ok=True)
    suite = ET.Element(
        "testsuite",
        {
            "name": test_class,
            "tests": str(len(methods) if declared_tests is None else declared_tests),
            "failures": "0",
            "errors": "0",
            "skipped": str(len(skipped)),
        },
    )
    for method in sorted(methods):
        case = ET.SubElement(
            suite,
            "testcase",
            {
                "classname": classname or f"example.{test_class}",
                "name": method,
            },
        )
        if method in skipped:
            ET.SubElement(case, "skipped")
    path = report_dir / f"TEST-example.{test_class}.xml"
    path.write_bytes(ET.tostring(suite, encoding="utf-8"))


def write_complete_reports(root: Path) -> None:
    for test_class, methods in REQUIRED_METHODS.items():
        write_report(root, test_class, methods, module=test_class)


class VerifyWorkbenchPostgresEvidenceTest(unittest.TestCase):

    def test_accepts_complete_clean_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)

            self.assertEqual([], verify_reports(root))

    def test_rejects_missing_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            missing_class = next(iter(REQUIRED_METHODS))
            for path in root.glob(
                f"**/target/surefire-reports/TEST-*{missing_class}.xml"
            ):
                path.unlink()

            errors = verify_reports(root)

            self.assertTrue(any("found 0" in error for error in errors))

    def test_requires_embedded_execution_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for test_class, methods in REQUIRED_METHODS.items():
                if test_class != "EmbeddedDeploymentExecutionIntegrationTest":
                    write_report(root, test_class, methods, module=test_class)

            errors = verify_reports(root)

            self.assertTrue(
                any("EmbeddedDeploymentExecutionIntegrationTest, found 0" in error for error in errors)
            )

    def test_rejects_duplicate_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            test_class = next(iter(REQUIRED_METHODS))
            write_report(
                root,
                test_class,
                REQUIRED_METHODS[test_class],
                module="duplicate",
            )

            errors = verify_reports(root)

            self.assertTrue(any("found 2" in error for error in errors))

    def test_rejects_missing_required_method(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            test_class = next(iter(REQUIRED_METHODS))
            methods = set(REQUIRED_METHODS[test_class])
            methods.pop()
            write_report(root, test_class, methods, module=test_class)

            errors = verify_reports(root)

            self.assertTrue(any("missing required testcase" in error for error in errors))

    def test_rejects_method_reported_under_another_class(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            test_class = next(iter(REQUIRED_METHODS))
            write_report(
                root,
                test_class,
                REQUIRED_METHODS[test_class],
                module=test_class,
                classname="example.UnrelatedTest",
            )

            errors = verify_reports(root)

            self.assertTrue(any("missing required testcase" in error for error in errors))

    def test_rejects_skipped_testcase(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            test_class = next(iter(REQUIRED_METHODS))
            skipped = {next(iter(REQUIRED_METHODS[test_class]))}
            write_report(
                root,
                test_class,
                REQUIRED_METHODS[test_class],
                module=test_class,
                skipped=skipped,
            )

            errors = verify_reports(root)

            self.assertTrue(any("skipped=1" in error for error in errors))

    def test_rejects_inconsistent_declared_counts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_complete_reports(root)
            test_class = next(iter(REQUIRED_METHODS))
            write_report(
                root,
                test_class,
                REQUIRED_METHODS[test_class],
                module=test_class,
                declared_tests=999,
            )

            errors = verify_reports(root)

            self.assertTrue(any("declares tests=999" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
