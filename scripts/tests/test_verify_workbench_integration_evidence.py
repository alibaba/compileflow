import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from scripts.verify_workbench_integration_evidence import REQUIRED_CASES, verify_report


def report_xml(
    cases: list[tuple[str, str]],
    *,
    declared_tests: int | None = None,
    skipped: set[tuple[str, str]] | None = None,
) -> bytes:
    skipped = skipped or set()
    tests = len(cases) if declared_tests is None else declared_tests
    suite = ET.Element(
        "testsuite",
        {
            "name": "integration",
            "tests": str(tests),
            "failures": "0",
            "errors": "0",
            "skipped": str(len(skipped)),
        },
    )
    for classname, name in cases:
        case = ET.SubElement(suite, "testcase", {"classname": classname, "name": name})
        if (classname, name) in skipped:
            ET.SubElement(case, "skipped")
    root = ET.Element(
        "testsuites",
        {
            "tests": str(tests),
            "failures": "0",
            "errors": "0",
            "skipped": str(len(skipped)),
        },
    )
    root.append(suite)
    return ET.tostring(root, encoding="utf-8")


class VerifyWorkbenchIntegrationEvidenceTest(unittest.TestCase):

    def verify(self, content: bytes) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.xml"
            path.write_bytes(content)
            return verify_report(path)

    def test_accepts_complete_clean_evidence(self) -> None:
        self.assertEqual([], self.verify(report_xml(sorted(REQUIRED_CASES))))

    def test_rejects_missing_required_case(self) -> None:
        cases = sorted(REQUIRED_CASES)[:-1]

        errors = self.verify(report_xml(cases))

        self.assertTrue(any("missing required testcase" in error for error in errors))

    def test_rejects_skipped_required_case(self) -> None:
        cases = sorted(REQUIRED_CASES)

        errors = self.verify(report_xml(cases, skipped={cases[0]}))

        self.assertTrue(any("evidence is not clean" in error for error in errors))

    def test_rejects_inconsistent_declared_counts(self) -> None:
        errors = self.verify(report_xml(sorted(REQUIRED_CASES), declared_tests=999))

        self.assertTrue(any("declares tests=999" in error for error in errors))

    def test_rejects_duplicate_testcase_identity(self) -> None:
        cases = sorted(REQUIRED_CASES)

        errors = self.verify(report_xml(cases + [cases[0]]))

        self.assertTrue(any("duplicate testcase" in error for error in errors))

    def test_rejects_malformed_xml(self) -> None:
        errors = self.verify(b"<testsuites>")

        self.assertTrue(any("cannot parse JUnit report" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
