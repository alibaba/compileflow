import json
import tempfile
import unittest
from pathlib import Path

from scripts.verify_durable_visibility_evidence import (
    VisibilityEvidenceError,
    build_matrix,
    discover_subjects,
    write_evidence,
)


COMMIT = "a" * 40
DIGEST = "b" * 64
QUERIES = (
    "keyword-common",
    "keyword-selective",
    "keyword-continuation",
    "long-common",
    "long-equality-selective",
    "long-continuation",
    "boolean-equality",
    "boolean-continuation",
    "instant-equality-common",
    "instant-equality-continuation",
)


def plan(value_index: str, rows: int) -> list[dict[str, object]]:
    return [
        {
            "Plan": {
                "Node Type": "Limit",
                "Actual Rows": rows,
                "Shared Hit Blocks": 100,
                "Shared Read Blocks": 2,
                "Temp Read Blocks": 0,
                "Temp Written Blocks": 0,
                "WAL Records": 8,
                "WAL FPI": 0,
                "WAL Bytes": 1024,
                "Plans": [
                    {
                        "Node Type": "Nested Loop",
                        "Plans": [
                            {
                                "Node Type": "Index Only Scan",
                                "Relation Name": "cf_vis_value",
                                "Index Name": value_index,
                            },
                            {
                                "Node Type": "Index Scan",
                                "Relation Name": "cf_vis_run",
                                "Index Name": "cf_vis_run_pkey",
                            },
                        ],
                    }
                ],
            },
            "Planning Time": 0.1,
            "Execution Time": 4.0,
        }
    ]


def subject(root: Path, version: str) -> Path:
    major, minor = (int(part) for part in version.split("."))
    queries = []
    for name in QUERIES:
        value_index = (
            "cf_vis_value_long_idx"
            if name.startswith("long-")
            else "cf_vis_value_boolean_idx"
            if name.startswith("boolean-")
            else "cf_vis_value_instant_idx"
            if name.startswith("instant-")
            else "cf_vis_value_keyword_idx"
        )
        rows = 1 if name.endswith("selective") else 100
        queries.append(
            {
                "name": name,
                "requiredIndexes": [value_index],
                "maximumBufferBlocks": 10_000,
                "summary": {
                    "actualRows": rows,
                    "planningMs": 0.1,
                    "executionMs": 4.0,
                    "indexes": [value_index, "cf_vis_run_pkey"],
                    "nodeTypes": [
                        "Index Only Scan",
                        "Index Scan",
                        "Limit",
                        "Nested Loop",
                    ],
                    "sharedHitBlocks": 100,
                    "sharedReadBlocks": 2,
                    "tempReadBlocks": 0,
                    "tempWrittenBlocks": 0,
                },
                "violations": [],
                "explain": plan(value_index, rows),
            }
        )
    mutation = {
        "executionMs": 4.0,
        "planningMs": 0.1,
        "walRecords": 8,
        "walFpi": 0,
        "walBytes": 1024,
        "sharedHitBlocks": 100,
        "sharedReadBlocks": 2,
        "explain": plan("cf_vis_value_pkey", 8),
    }
    document = {
        "schema": "compileflow-durable-visibility-feasibility/v2",
        "metadata": {
            "commit": COMMIT,
            "capabilityProfile": "TYPED_EQUALITY_V1",
            "declaredPostgres": version,
            "declaredImage": (
                f"postgres:{version}-alpine3.24@sha256:{DIGEST}"
            ),
            "serverVersion": version,
            "serverVersionNum": major * 10_000 + minor,
        },
        "corpus": {
            "requestedRuns": 1_000_000,
            "valuesPerRun": 8,
            "runs": 1_000_000,
            "values": 8_000_000,
            "schemaBytes": 8192,
            "fieldBytes": 16384,
            "runBytes": 128_000_000,
            "valueHeapBytes": 512_000_000,
            "valueIndexBytes": 640_000_000,
            "valueTotalBytes": 1_152_000_000,
        },
        "setupSeconds": 60.0,
        "queries": queries,
        "mutations": {
            "runRow": mutation,
            "eightChangedValues": mutation,
        },
        "checks": {
            "corpusCardinalityPassed": True,
            "allAdmittedPlansPassed": True,
            "violations": [],
            "passed": True,
        },
    }
    path = root / f"postgres-{version}-visibility-feasibility.json"
    path.write_text(json.dumps(document, sort_keys=True), encoding="utf-8")
    return path


def complete_matrix(root: Path) -> list[Path]:
    return [subject(root, "17.11"), subject(root, "18.6")]


class VerifyDurableVisibilityEvidenceTest(unittest.TestCase):

    def test_builds_complete_same_commit_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            matrix = build_matrix(
                paths=complete_matrix(Path(directory)),
                commit=COMMIT,
                expected_runs=1_000_000,
                expected_postgres=["17.11", "18.6"],
            )

        self.assertEqual(
            "compileflow-durable-visibility-feasibility-matrix/v2",
            matrix["schema"],
        )
        self.assertEqual(2, matrix["summary"]["subjects"])
        self.assertTrue(matrix["summary"]["allPassed"])

    def test_accepts_postgres_integral_float_raw_row_counts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            for path in paths:
                document = json.loads(path.read_text(encoding="utf-8"))
                for query in document["queries"]:
                    query["explain"][0]["Plan"]["Actual Rows"] = float(
                        query["summary"]["actualRows"]
                    )
                path.write_text(json.dumps(document), encoding="utf-8")

            matrix = build_matrix(
                paths=paths,
                commit=COMMIT,
                expected_runs=1_000_000,
                expected_postgres=["17.11", "18.6"],
            )

        self.assertTrue(matrix["summary"]["allPassed"])

    def test_discovers_nested_subjects_and_writes_aggregate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            nested = root / "downloaded" / "subject"
            nested.mkdir(parents=True)
            complete_matrix(nested)
            paths = discover_subjects(root)
            matrix = build_matrix(
                paths=paths,
                commit=COMMIT,
                expected_runs=1_000_000,
                expected_postgres=["17.11", "18.6"],
            )
            output = root / "visibility-feasibility-matrix.json"

            write_evidence(output, matrix)
            persisted = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(2, len(paths))
        self.assertEqual(matrix, persisted)

    def test_rejects_missing_or_duplicate_subject(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            with self.assertRaisesRegex(
                VisibilityEvidenceError,
                "expected 2 subjects, found 1",
            ):
                build_matrix(
                    paths=paths[:1],
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_mixed_commit_and_tampered_check(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["metadata"]["commit"] = "c" * 40
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(VisibilityEvidenceError, "does not match"):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["queries"][0]["violations"] = ["seq scan"]
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(VisibilityEvidenceError, "violations"):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_wrong_corpus_and_unpinned_image(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["corpus"]["values"] = 7_999_999
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(VisibilityEvidenceError, "cardinality"):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["metadata"]["declaredImage"] = "postgres:latest"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(VisibilityEvidenceError, "immutable"):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

    def test_rejects_wrong_profile_and_tampered_raw_plan(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["metadata"]["capabilityProfile"] = "RANGE_V1"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                VisibilityEvidenceError,
                "unsupported capability profile",
            ):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_matrix(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            raw_value_scan = document["queries"][0]["explain"][0][
                "Plan"
            ]["Plans"][0]["Plans"][0]
            raw_value_scan["Index Name"] = "tampered_index"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                VisibilityEvidenceError,
                "summary does not match raw plan",
            ):
                build_matrix(
                    paths=paths,
                    commit=COMMIT,
                    expected_runs=1_000_000,
                    expected_postgres=["17.11", "18.6"],
                )

if __name__ == "__main__":
    unittest.main()
