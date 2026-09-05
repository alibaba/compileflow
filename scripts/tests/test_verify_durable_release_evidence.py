import hashlib
import json
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from scripts.verify_durable_release_evidence import (
    REQUIRED_TEST_CASES,
    SUITE_POLICIES,
    ReleaseEvidenceError,
    build_release_evidence,
    discover_manifests,
    write_evidence,
)


COMMIT = "a" * 40
DIGEST = "b" * 64


def manifest(
    root: Path,
    filename: str,
    suite: str,
    metadata: dict[str, str],
    *,
    skipped: int = 0,
    failures: int = 0,
) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    (
        expected_reports,
        minimum_tests,
        minimum_passed,
        reject_skips,
    ) = SUITE_POLICIES[suite]
    report_count = expected_reports or 1
    required = sorted(REQUIRED_TEST_CASES[suite])
    tests = max(
        minimum_tests,
        minimum_passed + skipped + failures,
        len(required) + skipped + failures,
    )
    identities = required + [
        f"example.{filename}.FillerTest#case{index}"
        for index in range(tests - len(required))
    ]
    statuses = ["PASSED"] * len(identities)
    for index in range(failures):
        statuses[len(required) + index] = "FAILURE"
    for index in range(skipped):
        statuses[len(required) + failures + index] = "SKIPPED"
    cases = [
        {"id": identity, "status": status}
        for identity, status in zip(identities, statuses, strict=True)
    ]
    reports = []
    for report_index in range(report_count):
        report_cases = cases[report_index::report_count]
        report_file = f"TEST-{filename}-{report_index}.xml"
        report_name = f"example.{filename}.Report{report_index}"
        report_failures = sum(
            item["status"] == "FAILURE" for item in report_cases
        )
        report_errors = sum(
            item["status"] == "ERROR" for item in report_cases
        )
        report_skipped = sum(
            item["status"] == "SKIPPED" for item in report_cases
        )
        report_tests = len(report_cases)
        testsuite = ET.Element(
            "testsuite",
            {
                "name": report_name,
                "tests": str(report_tests),
                "failures": str(report_failures),
                "errors": str(report_errors),
                "skipped": str(report_skipped),
                "flakes": "0",
            },
        )
        for item in report_cases:
            classname, method = item["id"].split("#", 1)
            testcase = ET.SubElement(
                testsuite,
                "testcase",
                {"classname": classname, "name": method},
            )
            status = item["status"]
            if status != "PASSED":
                ET.SubElement(testcase, status.lower())
        report_content = ET.tostring(testsuite, encoding="utf-8")
        (root / report_file).write_bytes(report_content)
        reports.append(
            {
                "file": report_file,
                "name": report_name,
                "xmlSha256": hashlib.sha256(report_content).hexdigest(),
                "tests": report_tests,
                "failures": report_failures,
                "errors": report_errors,
                "skipped": report_skipped,
                "flakes": 0,
                "passed": report_tests - report_failures - report_skipped,
                "testCases": report_cases,
            }
        )
    passed = tests - skipped - failures
    document = {
        "schema": "compileflow-durable-test-evidence/v2",
        "suite": suite,
        "policy": {
            "expectedReports": expected_reports,
            "minimumTests": minimum_tests,
            "minimumPassed": minimum_passed,
            "rejectSkips": reject_skips,
            "requiredTestCases": required,
        },
        "metadata": {"commit": COMMIT, **metadata},
        "summary": {
            "tests": tests,
            "failures": failures,
            "errors": 0,
            "skipped": skipped,
            "flakes": 0,
            "passed": passed,
            "reports": report_count,
        },
        "reports": reports,
    }
    path = root / f"{filename}-test-evidence.json"
    path.write_text(
        json.dumps(document, sort_keys=True),
        encoding="utf-8",
    )
    java = metadata["java"]
    runtime = f"{java}.0.1+1"
    (root / "jdk-environment.txt").write_text(
        "\n".join(
            (
                f"commit={COMMIT}",
                f"declared_java={java}",
                f"java_runtime_version={runtime}",
                f"java_specification_version={java}",
                "java_vendor=Eclipse Adoptium",
                "java_vm_name=OpenJDK 64-Bit Server VM",
                f"java_vm_version={runtime}",
                "os_arch=x86_64",
                "os_name=Linux",
            )
        )
        + "\n",
        encoding="utf-8",
    )
    if suite == "postgres-contract":
        declared_image = metadata["declared-image"]
        image_digest = declared_image.rsplit("@sha256:", 1)[1]
        (root / "postgres-environment.txt").write_text(
            "\n".join(
                (
                    f"commit={COMMIT}",
                    f"declared_postgres={metadata['postgres']}",
                    f"declared_image={declared_image}",
                    f"resolved_image=postgres@sha256:{image_digest}",
                )
            )
            + "\n",
            encoding="utf-8",
        )
    return path


def complete_matrix(root: Path) -> list[Path]:
    paths = [
        manifest(
            root / "kernel17",
            "kernel17",
            "durable-kernel",
            {"java": "17"},
            skipped=1,
        ),
        manifest(
            root / "kernel21",
            "kernel21",
            "durable-kernel",
            {"java": "21"},
        ),
        manifest(
            root / "postgres17",
            "postgres17",
            "postgres-contract",
            {
                "java": "17",
                "postgres": "17.11",
                "declared-image": (
                    "postgres:17.11-alpine3.24@sha256:" + DIGEST
                ),
            },
        ),
        manifest(
            root / "postgres18",
            "postgres18",
            "postgres-contract",
            {
                "java": "17",
                "postgres": "18.6",
                "declared-image": (
                    "postgres:18.6-alpine3.24@sha256:" + DIGEST
                ),
            },
        ),
        manifest(
            root / "example",
            "example",
            "durable-postgres-example",
            {"java": "17"},
        ),
    ]
    return paths


class VerifyDurableReleaseEvidenceTest(unittest.TestCase):

    def test_release_policy_matches_the_executable_ci_gates(self) -> None:
        repository = Path(__file__).resolve().parents[2]
        workflow = (repository / ".github/workflows/durable-ci.yml").read_text(
            encoding="utf-8"
        )
        gates = (
            (
                "durable-kernel",
                "Verify Durable kernel test evidence",
                "Upload Durable test reports",
            ),
            (
                "postgres-contract",
                "Reject missing or skipped PostgreSQL evidence",
                "Record PostgreSQL image identity",
            ),
            (
                "durable-postgres-example",
                "Verify executable example evidence",
                "Upload example report",
            ),
        )
        for suite, start, end in gates:
            with self.subTest(suite=suite):
                match = re.search(
                    rf"- name: {re.escape(start)}(?P<body>.*?)"
                    rf"\n\s*- name: {re.escape(end)}",
                    workflow,
                    flags=re.DOTALL,
                )
                self.assertIsNotNone(match)
                body = match.group("body")
                configured = set(
                    re.findall(
                        r"--require-testcase '([^']+)'",
                        body,
                    )
                )
                self.assertSetEqual(
                    set(REQUIRED_TEST_CASES[suite]),
                    configured,
                )
                expected_reports, minimum_tests, minimum_passed, reject_skips = (
                    SUITE_POLICIES[suite]
                )
                self.assertIn(f"--minimum-tests {minimum_tests}", body)
                self.assertIn(f"--minimum-passed {minimum_passed}", body)
                if expected_reports is None:
                    self.assertNotIn("--expected-reports", body)
                else:
                    self.assertIn(
                        f"--expected-reports {expected_reports}",
                        body,
                    )
                self.assertEqual(
                    reject_skips,
                    "--reject-skips" in body,
                )

    def test_builds_complete_same_commit_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            evidence = build_release_evidence(
                paths=complete_matrix(Path(directory)),
                commit=COMMIT,
                expected_java=["17", "21"],
                expected_postgres=["17.11", "18.6"],
            )

        self.assertEqual(
            "compileflow-durable-release-evidence/v2",
            evidence["schema"],
        )
        self.assertEqual(5, evidence["summary"]["subjects"])
        self.assertEqual(1, evidence["summary"]["skipped"])
        postgres_subjects = [
            subject
            for subject in evidence["subjects"]
            if subject["subjectId"].startswith("postgres-")
            and subject["subjectId"] != "postgres-example-java-17"
        ]
        self.assertTrue(
            all("environmentSha256" in subject for subject in postgres_subjects)
        )
        self.assertTrue(
            all(
                subject["javaEnvironment"]["environmentSha256"]
                for subject in evidence["subjects"]
            )
        )
        self.assertEqual(
            [
                "kernel-java-17",
                "kernel-java-21",
                "postgres-17.11",
                "postgres-18.6",
                "postgres-example-java-17",
            ],
            [subject["subjectId"] for subject in evidence["subjects"]],
        )

    def test_builds_the_workflow_seven_subject_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = complete_matrix(root)
            paths.extend(
                (
                    manifest(
                        root / "kernel25",
                        "kernel25",
                        "durable-kernel",
                        {"java": "25"},
                    ),
                    manifest(
                        root / "postgres16",
                        "postgres16",
                        "postgres-contract",
                        {
                            "java": "17",
                            "postgres": "16.15",
                            "declared-image": (
                                "postgres:16.15-alpine3.24@sha256:" + DIGEST
                            ),
                        },
                    ),
                )
            )

            evidence = build_release_evidence(
                paths=paths,
                commit=COMMIT,
                expected_java=["17", "21", "25"],
                expected_postgres=["16.15", "17.11", "18.6"],
            )

        self.assertEqual(7, evidence["summary"]["subjects"])
        self.assertEqual(
            {
                "kernel-java-17",
                "kernel-java-21",
                "kernel-java-25",
                "postgres-16.15",
                "postgres-17.11",
                "postgres-18.6",
                "postgres-example-java-17",
            },
            {subject["subjectId"] for subject in evidence["subjects"]},
        )

    def test_discovers_nested_inputs_and_writes_aggregate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = root / "downloaded" / "artifact"
            inputs.mkdir(parents=True)
            complete_matrix(inputs)
            paths = discover_manifests(root / "downloaded")
            evidence = build_release_evidence(
                paths=paths,
                commit=COMMIT,
                expected_java=["17", "21"],
                expected_postgres=["17.11", "18.6"],
            )
            output = root / "release-evidence.json"

            write_evidence(output, evidence)
            persisted = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(5, len(paths))
        self.assertEqual(evidence, persisted)

    def test_rejects_missing_matrix_subject(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            paths.pop()

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "expected 5 evidence manifests, found 4",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_mixed_commits(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["metadata"]["commit"] = "c" * 40
            paths[0].write_text(
                json.dumps(document),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "does not match",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_unpinned_or_wrong_postgres_image(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[2].read_text(encoding="utf-8"))
            document["metadata"]["declared-image"] = "postgres:latest"
            paths[2].write_text(
                json.dumps(document),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "not an immutable, version-matching digest",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_missing_postgres_environment_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            (
                paths[2].parent / "postgres-environment.txt"
            ).unlink()

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "PostgreSQL environment evidence is missing",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_missing_java_environment_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            (paths[0].parent / "jdk-environment.txt").unlink()

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "Java environment evidence is missing",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_java_environment_feature_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            environment = paths[0].parent / "jdk-environment.txt"
            environment.write_text(
                environment.read_text(encoding="utf-8").replace(
                    "declared_java=17",
                    "declared_java=21",
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "Java environment feature does not match",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_symlinked_java_environment_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = complete_matrix(root)
            environment = paths[0].parent / "jdk-environment.txt"
            outside = root / "outside-jdk-environment.txt"
            outside.write_bytes(environment.read_bytes())
            environment.unlink()
            environment.symlink_to(outside)

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "Java environment evidence is missing or unsafe",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_resolved_postgres_digest_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            environment = paths[2].parent / "postgres-environment.txt"
            environment.write_text(
                environment.read_text(encoding="utf-8").replace(
                    f"resolved_image=postgres@sha256:{DIGEST}",
                    "resolved_image=postgres@sha256:" + "c" * 64,
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "resolved PostgreSQL image digest does not match",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_tampered_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["summary"]["tests"] = 99
            paths[0].write_text(
                json.dumps(document),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "summary.tests does not match",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_tampered_report_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["policy"]["expectedReports"] = 2
            paths[0].write_text(
                json.dumps(document),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "expected-reports policy is not satisfied",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_weakened_minimum_passed_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["policy"]["minimumPassed"] = 1
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "release policy is weaker than required",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_invalid_raw_report_digest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["reports"][0]["xmlSha256"] = "not-a-digest"
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "xmlSha256 is invalid",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_tampered_raw_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = complete_matrix(root)
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            raw_report = paths[0].parent / document["reports"][0]["file"]
            raw_report.write_text("tampered", encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "exactly one digest match",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_digest_valid_malformed_raw_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = complete_matrix(root)
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            raw_report = paths[0].parent / document["reports"][0]["file"]
            malformed = b"not-junit-xml"
            raw_report.write_bytes(malformed)
            document["reports"][0]["xmlSha256"] = hashlib.sha256(
                malformed
            ).hexdigest()
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "raw XML report .* is invalid",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_raw_report_path_escape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["reports"][0]["file"] = "../TEST-outside.xml"
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "raw report filename .* is invalid",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_postgres_skips(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = complete_matrix(root)
            paths[2] = manifest(
                paths[2].parent,
                "postgres17",
                "postgres-contract",
                {
                    "java": "17",
                    "postgres": "17.11",
                    "declared-image": (
                        "postgres:17.11-alpine3.24@sha256:" + DIGEST
                    ),
                },
                skipped=1,
            )

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "non-skippable evidence contains skips",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_tampered_testcase_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            critical = document["policy"]["requiredTestCases"][0]
            testcase = next(
                item
                for report in document["reports"]
                for item in report["testCases"]
                if item["id"] == critical
            )
            testcase["id"] = "example.TamperedTest#replacement"
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "does not match the independently parsed raw XML",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_undeclared_root_or_metadata_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["claimedResult"] = "PASSED"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "root fields must be exactly",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["metadata"]["source-tree"] = "dirty"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "metadata fields must be exactly",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_duplicate_json_key(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            content = paths[0].read_text(encoding="utf-8")
            content = content.replace(
                '"schema":',
                '"schema": "shadow", "schema":',
                1,
            )
            paths[0].write_text(content, encoding="utf-8")

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "duplicate key 'schema'",
            ):
                build_release_evidence(
                    paths=paths,
                    commit=COMMIT,
                    expected_java=["17", "21"],
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_symlinked_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_root = root / "evidence"
            evidence_root.mkdir()
            outside = root / "outside-test-evidence.json"
            outside.write_text("{}", encoding="utf-8")
            link = evidence_root / "linked-test-evidence.json"
            link.symlink_to(outside)

            with self.assertRaisesRegex(
                ReleaseEvidenceError,
                "evidence manifest is missing or unsafe",
            ):
                discover_manifests(evidence_root)


if __name__ == "__main__":
    unittest.main()
