#!/usr/bin/env python3
"""Aggregate a complete same-commit Visibility feasibility matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable

try:
    from scripts.evidence_io import write_json as write_evidence
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_json as write_evidence


SUBJECT_SCHEMA = "compileflow-durable-visibility-feasibility/v2"
MATRIX_SCHEMA = "compileflow-durable-visibility-feasibility-matrix/v2"
CAPABILITY_PROFILE = "TYPED_EQUALITY_V1"
COMMIT = re.compile(r"[0-9a-f]{40}")
POSTGRES_VERSION = re.compile(r"[0-9]+\.[0-9]+")
EXPECTED_QUERIES = frozenset(
    {
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
    }
)
EXPECTED_MUTATIONS = frozenset({"runRow", "eightChangedValues"})
EXPECTED_QUERY_INDEX = {
    name: (
        "cf_vis_value_keyword_idx"
        if name.startswith("keyword-")
        else "cf_vis_value_long_idx"
        if name.startswith("long-")
        else "cf_vis_value_boolean_idx"
        if name.startswith("boolean-")
        else "cf_vis_value_instant_idx"
    )
    for name in EXPECTED_QUERIES
}
MAXIMUM_BUFFER_BLOCKS = 10_000


class VisibilityEvidenceError(ValueError):
    """The Visibility evidence matrix is incomplete or untrustworthy."""


def parse_args() -> argparse.Namespace:
    """Parse the exact matrix policy."""
    parser = argparse.ArgumentParser(
        description=(
            "Verify a complete same-commit PostgreSQL Visibility "
            "feasibility matrix and emit a compact aggregate."
        )
    )
    parser.add_argument("--evidence-root", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--expected-runs", required=True, type=int)
    parser.add_argument(
        "--expected-postgres",
        action="append",
        required=True,
    )
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def require_object(value: object, field: str, path: Path) -> dict[str, object]:
    """Require one JSON object."""
    if not isinstance(value, dict):
        raise VisibilityEvidenceError(f"{path}: {field} must be an object")
    return value


def require_list(value: object, field: str, path: Path) -> list[object]:
    """Require one JSON array."""
    if not isinstance(value, list):
        raise VisibilityEvidenceError(f"{path}: {field} must be an array")
    return value


def require_text(
    value: object,
    field: str,
    path: Path,
    maximum: int = 2_048,
) -> str:
    """Require bounded non-empty text."""
    if not isinstance(value, str) or not value or len(value) > maximum:
        raise VisibilityEvidenceError(
            f"{path}: {field} must contain 1..{maximum} characters"
        )
    return value


def require_count(value: object, field: str, path: Path) -> int:
    """Require a non-negative integer."""
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value < 0
    ):
        raise VisibilityEvidenceError(
            f"{path}: {field} must be a non-negative integer"
        )
    return value


def require_integral_count(
    value: object,
    field: str,
    path: Path,
) -> int:
    """Accept an integer-valued JSON number and normalize it to int."""
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(value)
        or value < 0
        or int(value) != value
    ):
        raise VisibilityEvidenceError(
            f"{path}: {field} must be a non-negative integer-valued number"
        )
    return int(value)


def require_finite(value: object, field: str, path: Path) -> float:
    """Require a finite non-negative timing."""
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(value)
        or value < 0
    ):
        raise VisibilityEvidenceError(
            f"{path}: {field} must be a finite non-negative number"
        )
    return float(value)


def normalize_versions(values: Iterable[str]) -> tuple[str, ...]:
    """Validate an exact PostgreSQL matrix without duplicates."""
    result = tuple(values)
    if not result:
        raise VisibilityEvidenceError("expected-postgres must not be empty")
    invalid = [value for value in result if not POSTGRES_VERSION.fullmatch(value)]
    if invalid:
        raise VisibilityEvidenceError(
            f"expected-postgres contains invalid versions: {invalid}"
        )
    duplicates = sorted(
        value
        for value, count in Counter(result).items()
        if count > 1
    )
    if duplicates:
        raise VisibilityEvidenceError(
            f"expected-postgres contains duplicates: {duplicates}"
        )
    return result


def discover_subjects(root: Path) -> tuple[Path, ...]:
    """Discover raw per-version reports, excluding any aggregate."""
    return tuple(
        sorted(
            path.resolve()
            for path in root.glob("**/*visibility-feasibility.json")
            if path.is_file()
            and "matrix" not in path.name
        )
    )


def load_document(path: Path) -> tuple[dict[str, object], str]:
    """Load a report and bind its exact bytes by SHA-256."""
    try:
        content = path.read_bytes()
        document = json.loads(content)
    except (OSError, json.JSONDecodeError) as failure:
        raise VisibilityEvidenceError(
            f"{path}: cannot read evidence JSON: {failure}"
        ) from failure
    return require_object(document, "root", path), hashlib.sha256(content).hexdigest()


def expected_server_version_num(version: str) -> int:
    """Convert PostgreSQL major.minor to server_version_num."""
    major, minor = (int(part) for part in version.split("."))
    return major * 10_000 + minor


def plan_nodes(plan: dict[str, object]) -> Iterable[dict[str, object]]:
    """Yield every node in a retained PostgreSQL JSON plan."""
    yield plan
    children = plan.get("Plans", [])
    if isinstance(children, list):
        for child in children:
            if isinstance(child, dict):
                yield from plan_nodes(child)


def validate_query(
    value: object,
    path: Path,
    index: int,
) -> dict[str, object]:
    """Validate one raw admitted query and its structural checks."""
    query = require_object(value, f"queries[{index}]", path)
    name = require_text(query.get("name"), f"queries[{index}].name", path, 128)
    required = require_list(
        query.get("requiredIndexes"),
        f"queries[{index}].requiredIndexes",
        path,
    )
    required_indexes = {
        require_text(item, f"queries[{index}].requiredIndexes[]", path, 128)
        for item in required
    }
    if len(required_indexes) != len(required) or not required_indexes:
        raise VisibilityEvidenceError(
            f"{path}: query {name} required indexes are empty or duplicated"
        )
    expected_index = EXPECTED_QUERY_INDEX.get(name)
    if required_indexes != {expected_index}:
        raise VisibilityEvidenceError(
            f"{path}: query {name} requires unexpected indexes "
            f"{sorted(required_indexes)}"
        )
    violations = require_list(
        query.get("violations"),
        f"queries[{index}].violations",
        path,
    )
    if violations:
        raise VisibilityEvidenceError(
            f"{path}: query {name} contains plan violations: {violations}"
        )
    summary = require_object(
        query.get("summary"),
        f"queries[{index}].summary",
        path,
    )
    indexes = {
        require_text(item, f"queries[{index}].summary.indexes[]", path, 128)
        for item in require_list(
            summary.get("indexes"),
            f"queries[{index}].summary.indexes",
            path,
        )
    }
    if not required_indexes <= indexes:
        raise VisibilityEvidenceError(
            f"{path}: query {name} summary omits a required index"
        )
    maximum_buffer_blocks = require_count(
        query.get("maximumBufferBlocks"),
        f"queries[{index}].maximumBufferBlocks",
        path,
    )
    if maximum_buffer_blocks <= 0:
        raise VisibilityEvidenceError(
            f"{path}: query {name} maximumBufferBlocks must be positive"
        )
    node_types = {
        require_text(item, f"queries[{index}].summary.nodeTypes[]", path, 128)
        for item in require_list(
            summary.get("nodeTypes"),
            f"queries[{index}].summary.nodeTypes",
            path,
        )
    }
    actual_rows = require_count(
        summary.get("actualRows"),
        f"queries[{index}].summary.actualRows",
        path,
    )
    minimum_rows = 1 if name.endswith("selective") else 100
    if actual_rows < minimum_rows:
        raise VisibilityEvidenceError(
            f"{path}: query {name} returns fewer than {minimum_rows} rows"
        )
    planning_ms = require_finite(
        summary.get("planningMs"),
        f"queries[{index}].summary.planningMs",
        path,
    )
    execution_ms = require_finite(
        summary.get("executionMs"),
        f"queries[{index}].summary.executionMs",
        path,
    )
    explain = require_list(
        query.get("explain"),
        f"queries[{index}].explain",
        path,
    )
    if len(explain) != 1:
        raise VisibilityEvidenceError(
            f"{path}: query {name} must retain one raw EXPLAIN document"
        )
    raw = require_object(explain[0], f"queries[{index}].explain[0]", path)
    raw_plan = require_object(
        raw.get("Plan"),
        f"queries[{index}].explain[0].Plan",
        path,
    )
    raw_nodes = tuple(plan_nodes(raw_plan))
    raw_indexes = {
        require_text(
            node.get("Index Name"),
            f"queries[{index}].explain[].Index Name",
            path,
            128,
        )
        for node in raw_nodes
        if node.get("Index Name") is not None
    }
    raw_node_types = {
        require_text(
            node.get("Node Type"),
            f"queries[{index}].explain[].Node Type",
            path,
            128,
        )
        for node in raw_nodes
        if node.get("Node Type") is not None
    }
    if raw_indexes != indexes or raw_node_types != node_types:
        raise VisibilityEvidenceError(
            f"{path}: query {name} summary does not match raw plan"
        )
    if not required_indexes <= raw_indexes:
        raise VisibilityEvidenceError(
            f"{path}: query {name} raw plan omits a required index"
        )
    if any(
        node.get("Node Type") == "Seq Scan"
        and node.get("Relation Name") in {"cf_vis_value", "cf_vis_run"}
        for node in raw_nodes
    ):
        raise VisibilityEvidenceError(
            f"{path}: query {name} raw plan contains a base-table Seq Scan"
        )
    if not any(
        node.get("Relation Name") == "cf_vis_run"
        and node.get("Node Type") in {"Index Scan", "Index Only Scan"}
        for node in raw_nodes
    ):
        raise VisibilityEvidenceError(
            f"{path}: query {name} raw Run lookup is not index-bounded"
        )
    if require_integral_count(
        raw_plan.get("Actual Rows"),
        f"queries[{index}].explain[].Plan.Actual Rows",
        path,
    ) != actual_rows:
        raise VisibilityEvidenceError(
            f"{path}: query {name} actual row summary mismatch"
        )
    if require_finite(
        raw.get("Planning Time"),
        f"queries[{index}].explain[].Planning Time",
        path,
    ) != planning_ms or require_finite(
        raw.get("Execution Time"),
        f"queries[{index}].explain[].Execution Time",
        path,
    ) != execution_ms:
        raise VisibilityEvidenceError(
            f"{path}: query {name} timing summary mismatch"
        )
    shared_hit_blocks = require_count(
        summary.get("sharedHitBlocks"),
        f"queries[{index}].summary.sharedHitBlocks",
        path,
    )
    shared_read_blocks = require_count(
        summary.get("sharedReadBlocks"),
        f"queries[{index}].summary.sharedReadBlocks",
        path,
    )
    temp_read_blocks = require_count(
        summary.get("tempReadBlocks"),
        f"queries[{index}].summary.tempReadBlocks",
        path,
    )
    temp_written_blocks = require_count(
        summary.get("tempWrittenBlocks"),
        f"queries[{index}].summary.tempWrittenBlocks",
        path,
    )
    observed_buffer_blocks = (
        shared_hit_blocks
        + shared_read_blocks
        + temp_read_blocks
        + temp_written_blocks
    )
    raw_buffers = {
        "Shared Hit Blocks": shared_hit_blocks,
        "Shared Read Blocks": shared_read_blocks,
        "Temp Read Blocks": temp_read_blocks,
        "Temp Written Blocks": temp_written_blocks,
    }
    if any(
        require_count(
            raw_plan.get(field, 0),
            f"queries[{index}].explain[].Plan.{field}",
            path,
        ) != value
        for field, value in raw_buffers.items()
    ):
        raise VisibilityEvidenceError(
            f"{path}: query {name} buffer summary mismatch"
        )
    if maximum_buffer_blocks != MAXIMUM_BUFFER_BLOCKS:
        raise VisibilityEvidenceError(
            f"{path}: query {name} changes the fixed buffer budget"
        )
    if observed_buffer_blocks > maximum_buffer_blocks:
        raise VisibilityEvidenceError(
            f"{path}: query {name} exceeds its buffer budget"
        )
    return {
        "name": name,
        "actualRows": actual_rows,
        "planningMs": planning_ms,
        "executionMs": execution_ms,
        "maximumBufferBlocks": maximum_buffer_blocks,
        "observedBufferBlocks": observed_buffer_blocks,
        "sharedHitBlocks": shared_hit_blocks,
        "sharedReadBlocks": shared_read_blocks,
    }


def validate_mutations(value: object, path: Path) -> dict[str, object]:
    """Validate upper-bound mutation WAL diagnostics."""
    mutations = require_object(value, "mutations", path)
    if set(mutations) != EXPECTED_MUTATIONS:
        raise VisibilityEvidenceError(
            f"{path}: mutation set mismatch: {sorted(mutations)}"
        )
    result: dict[str, object] = {}
    for name in sorted(mutations):
        mutation = require_object(mutations[name], f"mutations.{name}", path)
        explain = require_list(
            mutation.get("explain"),
            f"mutations.{name}.explain",
            path,
        )
        if len(explain) != 1:
            raise VisibilityEvidenceError(
                f"{path}: mutation {name} must retain raw EXPLAIN"
            )
        raw = require_object(
            explain[0], f"mutations.{name}.explain[0]", path
        )
        raw_plan = require_object(
            raw.get("Plan"), f"mutations.{name}.explain[0].Plan", path
        )
        execution_ms = require_finite(
            mutation.get("executionMs"),
            f"mutations.{name}.executionMs",
            path,
        )
        planning_ms = require_finite(
            mutation.get("planningMs"),
            f"mutations.{name}.planningMs",
            path,
        )
        wal_records = require_count(
            mutation.get("walRecords"),
            f"mutations.{name}.walRecords",
            path,
        )
        wal_fpi = require_count(
            mutation.get("walFpi"),
            f"mutations.{name}.walFpi",
            path,
        )
        wal_bytes = require_count(
            mutation.get("walBytes"),
            f"mutations.{name}.walBytes",
            path,
        )
        shared_hit_blocks = require_count(
            mutation.get("sharedHitBlocks"),
            f"mutations.{name}.sharedHitBlocks",
            path,
        )
        shared_read_blocks = require_count(
            mutation.get("sharedReadBlocks"),
            f"mutations.{name}.sharedReadBlocks",
            path,
        )
        raw_expected = {
            "WAL Records": wal_records,
            "WAL FPI": wal_fpi,
            "WAL Bytes": wal_bytes,
            "Shared Hit Blocks": shared_hit_blocks,
            "Shared Read Blocks": shared_read_blocks,
        }
        if any(
            require_count(
                raw_plan.get(field, 0),
                f"mutations.{name}.explain[0].Plan.{field}",
                path,
            ) != value
            for field, value in raw_expected.items()
        ) or require_finite(
            raw.get("Execution Time"),
            f"mutations.{name}.explain[0].Execution Time",
            path,
        ) != execution_ms or require_finite(
            raw.get("Planning Time"),
            f"mutations.{name}.explain[0].Planning Time",
            path,
        ) != planning_ms:
            raise VisibilityEvidenceError(
                f"{path}: mutation {name} summary does not match raw plan"
            )
        result[name] = {
            "executionMs": execution_ms,
            "walRecords": wal_records,
            "walFpi": wal_fpi,
            "walBytes": wal_bytes,
        }
    return result


def validate_subject(
    path: Path,
    document: dict[str, object],
    digest: str,
    *,
    commit: str,
    expected_runs: int,
    expected_versions: set[str],
) -> dict[str, object]:
    """Validate one PostgreSQL subject without trusting its summary flags."""
    if document.get("schema") != SUBJECT_SCHEMA:
        raise VisibilityEvidenceError(
            f"{path}: unsupported schema {document.get('schema')!r}"
        )
    metadata = require_object(document.get("metadata"), "metadata", path)
    observed_commit = require_text(metadata.get("commit"), "metadata.commit", path)
    if observed_commit != commit:
        raise VisibilityEvidenceError(
            f"{path}: commit {observed_commit!r} does not match {commit!r}"
        )
    capability_profile = require_text(
        metadata.get("capabilityProfile"),
        "metadata.capabilityProfile",
        path,
        64,
    )
    if capability_profile != CAPABILITY_PROFILE:
        raise VisibilityEvidenceError(
            f"{path}: unsupported capability profile "
            f"{capability_profile!r}"
        )
    version = require_text(
        metadata.get("declaredPostgres"),
        "metadata.declaredPostgres",
        path,
        32,
    )
    if version not in expected_versions:
        raise VisibilityEvidenceError(
            f"{path}: unexpected PostgreSQL version {version}"
        )
    image = require_text(
        metadata.get("declaredImage"),
        "metadata.declaredImage",
        path,
    )
    image_pattern = re.compile(
        rf"postgres:{re.escape(version)}-[^@]+@sha256:[0-9a-f]{{64}}"
    )
    if not image_pattern.fullmatch(image):
        raise VisibilityEvidenceError(
            f"{path}: declared image is not immutable and version-matching"
        )
    server_version = require_text(
        metadata.get("serverVersion"),
        "metadata.serverVersion",
        path,
        128,
    )
    server_version_num = require_count(
        metadata.get("serverVersionNum"),
        "metadata.serverVersionNum",
        path,
    )
    if (
        not server_version.startswith(version)
        or server_version_num != expected_server_version_num(version)
    ):
        raise VisibilityEvidenceError(
            f"{path}: server identity does not match declared {version}"
        )

    corpus = require_object(document.get("corpus"), "corpus", path)
    requested_runs = require_count(
        corpus.get("requestedRuns"), "corpus.requestedRuns", path
    )
    runs = require_count(corpus.get("runs"), "corpus.runs", path)
    values_per_run = require_count(
        corpus.get("valuesPerRun"), "corpus.valuesPerRun", path
    )
    values = require_count(corpus.get("values"), "corpus.values", path)
    if (
        requested_runs != expected_runs
        or runs != expected_runs
        or values_per_run != 8
        or values != expected_runs * 8
    ):
        raise VisibilityEvidenceError(
            f"{path}: corpus cardinality does not match {expected_runs}*8"
        )
    storage_fields = (
        "schemaBytes",
        "fieldBytes",
        "runBytes",
        "valueHeapBytes",
        "valueIndexBytes",
        "valueTotalBytes",
    )
    storage = {
        field: require_count(corpus.get(field), f"corpus.{field}", path)
        for field in storage_fields
    }
    if any(value <= 0 for value in storage.values()):
        raise VisibilityEvidenceError(f"{path}: storage sizes must be positive")

    queries_value = require_list(document.get("queries"), "queries", path)
    queries = [
        validate_query(value, path, index)
        for index, value in enumerate(queries_value)
    ]
    names = [str(query["name"]) for query in queries]
    if len(names) != len(set(names)) or set(names) != EXPECTED_QUERIES:
        raise VisibilityEvidenceError(
            f"{path}: query matrix mismatch: {sorted(names)}"
        )
    checks = require_object(document.get("checks"), "checks", path)
    if checks.get("passed") is not True:
        raise VisibilityEvidenceError(f"{path}: checks.passed is not true")
    if checks.get("corpusCardinalityPassed") is not True:
        raise VisibilityEvidenceError(
            f"{path}: corpusCardinalityPassed is not true"
        )
    if checks.get("allAdmittedPlansPassed") is not True:
        raise VisibilityEvidenceError(
            f"{path}: allAdmittedPlansPassed is not true"
        )
    if require_list(checks.get("violations"), "checks.violations", path):
        raise VisibilityEvidenceError(f"{path}: checks contain violations")

    return {
        "postgres": version,
        "capabilityProfile": capability_profile,
        "serverVersion": server_version,
        "declaredImage": image,
        "inputSha256": digest,
        "setupSeconds": require_finite(
            document.get("setupSeconds"), "setupSeconds", path
        ),
        "storage": storage,
        "queries": sorted(queries, key=lambda item: str(item["name"])),
        "mutations": validate_mutations(document.get("mutations"), path),
    }


def build_matrix(
    *,
    paths: Iterable[Path],
    commit: str,
    expected_runs: int,
    expected_postgres: Iterable[str],
) -> dict[str, object]:
    """Build the exact same-commit matrix aggregate."""
    if not COMMIT.fullmatch(commit):
        raise VisibilityEvidenceError(
            "commit must be a lowercase 40-character Git SHA"
        )
    if not 10_000 <= expected_runs <= 5_000_000:
        raise VisibilityEvidenceError(
            "expected-runs must be in 10000..5000000"
        )
    versions = normalize_versions(expected_postgres)
    input_paths = tuple(sorted({path.resolve() for path in paths}))
    if len(input_paths) != len(versions):
        raise VisibilityEvidenceError(
            f"expected {len(versions)} subjects, found {len(input_paths)}"
        )
    subjects: dict[str, dict[str, object]] = {}
    for path in input_paths:
        document, digest = load_document(path)
        subject = validate_subject(
            path,
            document,
            digest,
            commit=commit,
            expected_runs=expected_runs,
            expected_versions=set(versions),
        )
        version = str(subject["postgres"])
        if version in subjects:
            raise VisibilityEvidenceError(
                f"duplicate PostgreSQL subject {version}"
            )
        subjects[version] = subject
    if set(subjects) != set(versions):
        raise VisibilityEvidenceError(
            "PostgreSQL matrix mismatch; missing="
            f"{sorted(set(versions) - set(subjects))}"
        )
    ordered = [subjects[version] for version in versions]
    return {
        "schema": MATRIX_SCHEMA,
        "commit": commit,
        "capabilityProfile": CAPABILITY_PROFILE,
        "corpus": {
            "runs": expected_runs,
            "valuesPerRun": 8,
        },
        "postgres": list(versions),
        "subjects": ordered,
        "summary": {
            "subjects": len(ordered),
            "queriesPerSubject": len(EXPECTED_QUERIES),
            "allPassed": True,
        },
    }


def main() -> int:
    """Verify and aggregate the requested report directory."""
    arguments = parse_args()
    try:
        paths = discover_subjects(arguments.evidence_root)
        matrix = build_matrix(
            paths=paths,
            commit=arguments.commit,
            expected_runs=arguments.expected_runs,
            expected_postgres=arguments.expected_postgres,
        )
        write_evidence(arguments.output, matrix)
    except VisibilityEvidenceError as failure:
        print(
            f"Durable Visibility evidence failed: {failure}",
            file=sys.stderr,
        )
        return 1
    print(
        "Durable Visibility evidence passed: "
        f"commit={matrix['commit']} subjects={matrix['summary']['subjects']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
