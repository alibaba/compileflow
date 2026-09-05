#!/usr/bin/env python3
"""Build and measure the proposed bounded-Visibility PostgreSQL shape.

This is a pre-implementation evidence runner. It owns one explicitly named
scratch schema in a disposable database, emits raw JSON plans, and removes the
schema on exit. It never migrates or reads CompileFlow production tables.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

try:
    from scripts.evidence_io import write_json as write_evidence
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_json as write_evidence


EVIDENCE_SCHEMA = "compileflow-durable-visibility-feasibility/v2"
CAPABILITY_PROFILE = "TYPED_EQUALITY_V1"
DEFAULT_SCHEMA = "cf_durable_visibility_feasibility"
SCHEMA_NAME = re.compile(r"cf_[a-z][a-z0-9_]{0,47}")
COMMIT = re.compile(r"[0-9a-f]{40}")
POSTGRES_VERSION = re.compile(r"[0-9]+\.[0-9]+")
FORBIDDEN_SCHEMAS = frozenset(
    {"public", "information_schema", "pg_catalog"}
)
EXPECTED_QUERY_NAMES = frozenset(
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


class FeasibilityError(ValueError):
    """The evidence request or observed database result is invalid."""


@dataclass(frozen=True)
class QueryScenario:
    """One admitted query-plan shape and its required indexes."""

    name: str
    sql: str
    required_indexes: tuple[str, ...]
    minimum_rows: int
    maximum_buffer_blocks: int = 10_000


def parse_args() -> argparse.Namespace:
    """Parse the destructive opt-in, corpus identity, and output path."""
    parser = argparse.ArgumentParser(
        description=(
            "Create an isolated bounded-Visibility corpus, verify its "
            "PostgreSQL plans, emit evidence, and remove the corpus."
        )
    )
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--schema", default=DEFAULT_SCHEMA)
    parser.add_argument("--runs", type=int, default=1_000_000)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--declared-postgres", required=True)
    parser.add_argument("--declared-image", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--allow-schema-reset",
        action="store_true",
        help="Required opt-in to drop and recreate only --schema",
    )
    parser.add_argument(
        "--keep-schema",
        action="store_true",
        help="Keep the scratch schema for local investigation",
    )
    return parser.parse_args()


def validate_request(arguments: argparse.Namespace) -> None:
    """Reject broad destructive targets and unbounded corpus requests."""
    if not arguments.allow_schema_reset:
        raise FeasibilityError("--allow-schema-reset is required")
    if (
        not SCHEMA_NAME.fullmatch(arguments.schema)
        or arguments.schema in FORBIDDEN_SCHEMAS
        or arguments.schema.startswith("pg_")
    ):
        raise FeasibilityError(
            "schema must be a bounded cf_* scratch-schema identifier"
        )
    if not 10_000 <= arguments.runs <= 5_000_000:
        raise FeasibilityError("runs must be in 10000..5000000")
    if not COMMIT.fullmatch(arguments.commit):
        raise FeasibilityError(
            "commit must be a lowercase 40-character Git SHA"
        )
    if not POSTGRES_VERSION.fullmatch(arguments.declared_postgres):
        raise FeasibilityError(
            "declared-postgres must use major.minor"
        )
    expected_image = re.compile(
        rf"postgres:{re.escape(arguments.declared_postgres)}-"
        r"[^@]+@sha256:[0-9a-f]{64}"
    )
    if not expected_image.fullmatch(arguments.declared_image):
        raise FeasibilityError(
            "declared-image must be an immutable, version-matching "
            "official PostgreSQL image"
        )


def require_connection_environment() -> None:
    """Require an explicit database target without inspecting credentials."""
    missing = [
        name
        for name in ("PGHOST", "PGPORT", "PGDATABASE", "PGUSER")
        if not os.environ.get(name)
    ]
    if missing:
        raise FeasibilityError(
            "missing PostgreSQL connection environment: "
            + ", ".join(missing)
        )


def run_psql(psql: str, sql: str) -> str:
    """Execute SQL through psql with user configuration disabled."""
    environment = os.environ.copy()
    environment.pop("PGOPTIONS", None)
    environment["PGAPPNAME"] = "compileflow-visibility-feasibility"
    result = subprocess.run(
        [
            psql,
            "-X",
            "--quiet",
            "--tuples-only",
            "--no-align",
            "--set",
            "ON_ERROR_STOP=1",
        ],
        input=sql,
        text=True,
        capture_output=True,
        env=environment,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip()[-4_000:]
        raise FeasibilityError(
            f"psql failed with exit code {result.returncode}: {detail}"
        )
    return result.stdout.strip()


def parse_json_output(output: str, subject: str) -> Any:
    """Parse one tuples-only JSON value returned by PostgreSQL."""
    try:
        return json.loads(output)
    except json.JSONDecodeError as failure:
        raise FeasibilityError(
            f"{subject} did not return one JSON value: {failure}"
        ) from failure


def setup_sql(schema: str, runs: int) -> str:
    """Return the deterministic compact dictionary/value corpus DDL."""
    digest = "a" * 64
    return f"""
DROP SCHEMA IF EXISTS {schema} CASCADE;
CREATE SCHEMA {schema};

CREATE TABLE {schema}.cf_vis_schema (
  visibility_schema_id bigint PRIMARY KEY,
  namespace text NOT NULL,
  process_code text NOT NULL,
  visibility_schema_digest character(64) NOT NULL,
  UNIQUE (namespace, process_code, visibility_schema_digest)
);

CREATE TABLE {schema}.cf_vis_schema_field (
  visibility_schema_id bigint NOT NULL,
  field_ordinal smallint NOT NULL,
  field_name text NOT NULL,
  value_type smallint NOT NULL CHECK (value_type BETWEEN 1 AND 4),
  PRIMARY KEY (visibility_schema_id, field_ordinal),
  UNIQUE (visibility_schema_id, field_name),
  FOREIGN KEY (visibility_schema_id)
    REFERENCES {schema}.cf_vis_schema (visibility_schema_id)
);

CREATE TABLE {schema}.cf_vis_run (
  run_id bigint PRIMARY KEY,
  visibility_schema_id bigint NOT NULL,
  status smallint NOT NULL,
  created_at timestamptz NOT NULL,
  UNIQUE (run_id, visibility_schema_id),
  FOREIGN KEY (visibility_schema_id)
    REFERENCES {schema}.cf_vis_schema (visibility_schema_id)
);

CREATE TABLE {schema}.cf_vis_value (
  run_id bigint NOT NULL,
  visibility_schema_id bigint NOT NULL,
  field_ordinal smallint NOT NULL,
  value_type smallint NOT NULL CHECK (value_type BETWEEN 1 AND 4),
  keyword_value text COLLATE "C",
  long_value bigint,
  boolean_value boolean,
  instant_value timestamptz,
  run_created_at timestamptz NOT NULL,
  changed_run_revision bigint NOT NULL,
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (run_id, field_ordinal),
  CHECK (
    num_nonnulls(
      keyword_value, long_value, boolean_value, instant_value
    ) = 1
    AND (
      (value_type = 1 AND keyword_value IS NOT NULL)
      OR (value_type = 2 AND long_value IS NOT NULL)
      OR (value_type = 3 AND boolean_value IS NOT NULL)
      OR (value_type = 4 AND instant_value IS NOT NULL)
    )
  )
);

INSERT INTO {schema}.cf_vis_schema
VALUES (1, 'default', 'orders', '{digest}');

INSERT INTO {schema}.cf_vis_schema_field
    (visibility_schema_id, field_ordinal, field_name, value_type)
VALUES
  (1, 1, 'region', 1),
  (1, 2, 'customer', 1),
  (1, 3, 'channel', 1),
  (1, 4, 'priority', 2),
  (1, 5, 'amount', 2),
  (1, 6, 'expedited', 3),
  (1, 7, 'due', 4),
  (1, 8, 'bucket', 2);

INSERT INTO {schema}.cf_vis_run
    (run_id, visibility_schema_id, status, created_at)
SELECT
  value,
  1,
  CASE WHEN value % 10 < 7 THEN 1 ELSE 2 END,
  timestamptz '2026-07-31 00:00:00+00'
    - (value % 120000) * interval '1 minute'
FROM generate_series(1, {runs}) AS value;

INSERT INTO {schema}.cf_vis_value
    (run_id, visibility_schema_id, field_ordinal, value_type,
     keyword_value, long_value, boolean_value, instant_value,
     run_created_at, changed_run_revision, updated_at)
SELECT
  run.run_id,
  1,
  projected.field_ordinal,
  projected.value_type,
  projected.keyword_value,
  projected.long_value,
  projected.boolean_value,
  projected.instant_value,
  run.created_at,
  1,
  timestamptz '2026-07-31 00:00:00+00'
FROM {schema}.cf_vis_run AS run
CROSS JOIN LATERAL (VALUES
  (1::smallint, 1::smallint,
   'R' || lpad(((run.run_id * 13 + run.run_id / 97) % 20)::text, 2, '0'),
   NULL::bigint, NULL::boolean, NULL::timestamptz),
  (2, 1, 'C' || lpad((run.run_id % 50000)::text, 5, '0'),
   NULL, NULL, NULL),
  (3, 1, 'CH' || ((run.run_id / 20) % 5)::text,
   NULL, NULL, NULL),
  (4, 2, NULL, (run.run_id / 7) % 10, NULL, NULL),
  (5, 2, NULL, (run.run_id * 7919) % 100000, NULL, NULL),
  (6, 3, NULL, NULL, (run.run_id * 17) % 4 = 0, NULL),
  (7, 4, NULL, NULL, NULL,
   run.created_at + (run.run_id % 7200) * interval '1 minute'),
  (8, 2, NULL, (run.run_id / 1000) % 1000, NULL, NULL)
) AS projected(
  field_ordinal, value_type, keyword_value, long_value,
  boolean_value, instant_value
);

CREATE INDEX cf_vis_value_keyword_idx
ON {schema}.cf_vis_value
  (visibility_schema_id, field_ordinal, keyword_value,
   run_created_at DESC, run_id DESC)
WHERE value_type = 1;

CREATE INDEX cf_vis_value_long_idx
ON {schema}.cf_vis_value
  (visibility_schema_id, field_ordinal, long_value,
   run_created_at DESC, run_id DESC)
WHERE value_type = 2;

CREATE INDEX cf_vis_value_boolean_idx
ON {schema}.cf_vis_value
  (visibility_schema_id, field_ordinal, boolean_value,
   run_created_at DESC, run_id DESC)
WHERE value_type = 3;

CREATE INDEX cf_vis_value_instant_idx
ON {schema}.cf_vis_value
  (visibility_schema_id, field_ordinal, instant_value,
   run_created_at DESC, run_id DESC)
WHERE value_type = 4;

ALTER TABLE {schema}.cf_vis_value
  ADD CONSTRAINT cf_vis_value_run_fk
  FOREIGN KEY (run_id, visibility_schema_id)
  REFERENCES {schema}.cf_vis_run (run_id, visibility_schema_id),
  ADD CONSTRAINT cf_vis_value_field_fk
  FOREIGN KEY (visibility_schema_id, field_ordinal)
  REFERENCES {schema}.cf_vis_schema_field
      (visibility_schema_id, field_ordinal);

VACUUM (ANALYZE, FREEZE) {schema}.cf_vis_run;
VACUUM (ANALYZE, FREEZE) {schema}.cf_vis_value;
"""


def lateral_run(schema: str) -> str:
    """Return the bounded status lookup used by every query template."""
    return f"""
CROSS JOIN LATERAL (
  SELECT run.status
  FROM {schema}.cf_vis_run AS run
  WHERE run.run_id = value.run_id
    AND run.status IN (1, 2)
  LIMIT 1
) AS admitted_run
"""


def query_scenarios(schema: str) -> tuple[QueryScenario, ...]:
    """Build every initially admitted typed-equality/keyset plan shape."""
    window = """
  AND value.run_created_at >= timestamptz '2026-07-01 00:00:00+00'
  AND value.run_created_at < timestamptz '2026-08-01 00:00:00+00'
"""
    order_limit = """
ORDER BY value.run_created_at DESC, value.run_id DESC
LIMIT 100
"""
    select_from = f"""
SELECT value.run_id, value.run_created_at, admitted_run.status
FROM {schema}.cf_vis_value AS value
{lateral_run(schema)}
WHERE value.visibility_schema_id = 1
"""
    return (
        QueryScenario(
            "keyword-common",
            select_from
            + """  AND value.field_ordinal = 1
  AND value.value_type = 1
  AND value.keyword_value = 'R03'
"""
            + window
            + order_limit,
            ("cf_vis_value_keyword_idx",),
            100,
        ),
        QueryScenario(
            "keyword-selective",
            select_from
            + """  AND value.field_ordinal = 2
  AND value.value_type = 1
  AND value.keyword_value = 'C01234'
"""
            + window
            + order_limit,
            ("cf_vis_value_keyword_idx",),
            1,
        ),
        QueryScenario(
            "keyword-continuation",
            select_from
            + """  AND value.field_ordinal = 1
  AND value.value_type = 1
  AND value.keyword_value = 'R03'
"""
            + window
            + """  AND (value.run_created_at, value.run_id)
      < (timestamptz '2026-07-28 00:00:00+00', 9223372036854775807)
"""
            + order_limit,
            ("cf_vis_value_keyword_idx",),
            100,
        ),
        QueryScenario(
            "long-common",
            select_from
            + """  AND value.field_ordinal = 4
  AND value.value_type = 2
  AND value.long_value = 3
"""
            + window
            + order_limit,
            ("cf_vis_value_long_idx",),
            100,
        ),
        QueryScenario(
            "long-equality-selective",
            select_from
            + """  AND value.field_ordinal = 5
  AND value.value_type = 2
  AND value.long_value = 7919
"""
            + window
            + order_limit,
            ("cf_vis_value_long_idx",),
            1,
        ),
        QueryScenario(
            "long-continuation",
            select_from
            + """  AND value.field_ordinal = 4
  AND value.value_type = 2
  AND value.long_value = 3
"""
            + window
            + """  AND (value.run_created_at, value.run_id)
      < (timestamptz '2026-07-28 00:00:00+00', 9223372036854775807)
"""
            + order_limit,
            ("cf_vis_value_long_idx",),
            100,
        ),
        QueryScenario(
            "boolean-equality",
            select_from
            + """  AND value.field_ordinal = 6
  AND value.value_type = 3
  AND value.boolean_value = true
"""
            + window
            + order_limit,
            ("cf_vis_value_boolean_idx",),
            100,
        ),
        QueryScenario(
            "boolean-continuation",
            select_from
            + """  AND value.field_ordinal = 6
  AND value.value_type = 3
  AND value.boolean_value = true
"""
            + window
            + """  AND (value.run_created_at, value.run_id)
      < (timestamptz '2026-07-28 00:00:00+00', 9223372036854775807)
"""
            + order_limit,
            ("cf_vis_value_boolean_idx",),
            100,
        ),
        QueryScenario(
            "instant-equality-common",
            select_from
            + """  AND value.field_ordinal = 7
  AND value.value_type = 4
  AND value.instant_value = timestamptz '2026-07-31 00:00:00+00'
"""
            + window
            + order_limit,
            ("cf_vis_value_instant_idx",),
            100,
        ),
        QueryScenario(
            "instant-equality-continuation",
            select_from
            + """  AND value.field_ordinal = 7
  AND value.value_type = 4
  AND value.instant_value = timestamptz '2026-07-31 00:00:00+00'
"""
            + window
            + """  AND (value.run_created_at, value.run_id)
      < (timestamptz '2026-07-28 00:00:00+00', 9223372036854775807)
"""
            + order_limit,
            ("cf_vis_value_instant_idx",),
            100,
        ),
    )


def explain_sql(query: str, *, wal: bool = False) -> str:
    """Wrap one query in bounded raw JSON EXPLAIN evidence."""
    wal_option = ", WAL" if wal else ""
    return f"""
SET statement_timeout = '20s';
SET lock_timeout = '2s';
EXPLAIN (ANALYZE, BUFFERS{wal_option}, FORMAT JSON)
{query.rstrip(';')};
"""


def plan_nodes(plan: dict[str, Any]) -> Iterable[dict[str, Any]]:
    """Yield every node in one PostgreSQL JSON plan tree."""
    yield plan
    for child in plan.get("Plans", []):
        if isinstance(child, dict):
            yield from plan_nodes(child)


def finite_number(value: object) -> bool:
    """Return whether value is a finite, non-boolean number."""
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
    )


def integral_count(value: object) -> int | None:
    """Normalize one finite non-negative integer-valued JSON number."""
    if not finite_number(value) or value < 0:
        return None
    normalized = int(value)
    if normalized != value:
        return None
    return normalized


def validate_query_plan(
    scenario: QueryScenario,
    document: object,
) -> list[str]:
    """Require intended indexes, finite timing, rows, and no broad scan."""
    if (
        not isinstance(document, list)
        or len(document) != 1
        or not isinstance(document[0], dict)
        or not isinstance(document[0].get("Plan"), dict)
    ):
        return [f"{scenario.name}: malformed EXPLAIN JSON"]
    root = document[0]
    nodes = tuple(plan_nodes(root["Plan"]))
    indexes = {
        node.get("Index Name")
        for node in nodes
        if isinstance(node.get("Index Name"), str)
    }
    errors = [
        f"{scenario.name}: missing required index {index}"
        for index in scenario.required_indexes
        if index not in indexes
    ]
    for node in nodes:
        if (
            node.get("Node Type") == "Seq Scan"
            and node.get("Relation Name")
            in {"cf_vis_value", "cf_vis_run"}
        ):
            errors.append(
                f"{scenario.name}: unbounded sequential scan on "
                f"{node.get('Relation Name')}"
            )
    if not any(
        node.get("Relation Name") == "cf_vis_run"
        and node.get("Node Type") in {"Index Scan", "Index Only Scan"}
        for node in nodes
    ):
        errors.append(
            f"{scenario.name}: Run status lookup is not index-bounded"
        )
    root_plan = root["Plan"]
    buffer_fields = (
        "Shared Hit Blocks",
        "Shared Read Blocks",
        "Temp Read Blocks",
        "Temp Written Blocks",
    )
    buffer_values: list[int] = []
    for field in buffer_fields:
        value = root_plan.get(field, 0)
        if (
            not isinstance(value, int)
            or isinstance(value, bool)
            or value < 0
        ):
            errors.append(
                f"{scenario.name}: {field} is not a non-negative integer"
            )
        else:
            buffer_values.append(value)
    buffer_blocks = sum(buffer_values)
    if buffer_blocks > scenario.maximum_buffer_blocks:
        errors.append(
            f"{scenario.name}: buffer blocks {buffer_blocks} exceed "
            f"hard budget {scenario.maximum_buffer_blocks}"
        )
    actual_rows_value = root["Plan"].get("Actual Rows")
    actual_rows = integral_count(actual_rows_value)
    if actual_rows is None or actual_rows < scenario.minimum_rows:
        errors.append(
            f"{scenario.name}: root Actual Rows {actual_rows_value!r} is below "
            f"{scenario.minimum_rows}"
        )
    for field in ("Planning Time", "Execution Time"):
        if not finite_number(root.get(field)):
            errors.append(f"{scenario.name}: {field} is not finite")
    return errors


def plan_summary(document: object) -> dict[str, object]:
    """Extract review-friendly structural and resource metrics."""
    if (
        not isinstance(document, list)
        or len(document) != 1
        or not isinstance(document[0], dict)
        or not isinstance(document[0].get("Plan"), dict)
    ):
        return {
            "actualRows": None,
            "planningMs": None,
            "executionMs": None,
            "nodeTypes": [],
            "indexes": [],
            "sharedHitBlocks": 0,
            "sharedReadBlocks": 0,
            "tempReadBlocks": 0,
            "tempWrittenBlocks": 0,
        }
    root = document[0]
    nodes = tuple(plan_nodes(root["Plan"]))
    return {
        "actualRows": integral_count(root["Plan"].get("Actual Rows")),
        "planningMs": root.get("Planning Time"),
        "executionMs": root.get("Execution Time"),
        "nodeTypes": sorted(
            {
                str(node["Node Type"])
                for node in nodes
                if "Node Type" in node
            }
        ),
        "indexes": sorted(
            {
                str(node["Index Name"])
                for node in nodes
                if "Index Name" in node
            }
        ),
        "sharedHitBlocks": root["Plan"].get("Shared Hit Blocks", 0),
        "sharedReadBlocks": root["Plan"].get("Shared Read Blocks", 0),
        "tempReadBlocks": root["Plan"].get("Temp Read Blocks", 0),
        "tempWrittenBlocks": root["Plan"].get(
            "Temp Written Blocks", 0
        ),
    }


def server_identity(psql: str) -> dict[str, object]:
    """Read safe server identity without recording connection credentials."""
    output = run_psql(
        psql,
        """
SELECT json_build_object(
  'serverVersion', current_setting('server_version'),
  'serverVersionNum', current_setting('server_version_num')::integer
);
""",
    )
    value = parse_json_output(output, "server identity")
    if not isinstance(value, dict):
        raise FeasibilityError("server identity must be a JSON object")
    return value


def corpus_and_storage(psql: str, schema: str) -> dict[str, object]:
    """Read exact cardinality and relation-size evidence."""
    output = run_psql(
        psql,
        f"""
SELECT json_build_object(
  'runs', (SELECT count(*) FROM {schema}.cf_vis_run),
  'values', (SELECT count(*) FROM {schema}.cf_vis_value),
  'schemaBytes', pg_total_relation_size('{schema}.cf_vis_schema'),
  'fieldBytes', pg_total_relation_size('{schema}.cf_vis_schema_field'),
  'runBytes', pg_total_relation_size('{schema}.cf_vis_run'),
  'valueHeapBytes', pg_relation_size('{schema}.cf_vis_value'),
  'valueIndexBytes', pg_indexes_size('{schema}.cf_vis_value'),
  'valueTotalBytes', pg_total_relation_size('{schema}.cf_vis_value')
);
""",
    )
    value = parse_json_output(output, "corpus and storage")
    if not isinstance(value, dict):
        raise FeasibilityError("corpus and storage must be a JSON object")
    return value


def mutation_diagnostics(psql: str, schema: str) -> dict[str, object]:
    """Capture WAL/buffer upper bounds for one Run and eight changed values."""
    operations = {
        "runRow": f"""
UPDATE {schema}.cf_vis_run
SET status = CASE WHEN status = 1 THEN 2 ELSE 1 END
WHERE run_id = 42
""",
        "eightChangedValues": f"""
UPDATE {schema}.cf_vis_value
SET keyword_value = CASE
      WHEN value_type = 1 THEN keyword_value || '-changed'
      ELSE keyword_value END,
    long_value = CASE
      WHEN value_type = 2 THEN long_value + 1 ELSE long_value END,
    boolean_value = CASE
      WHEN value_type = 3 THEN NOT boolean_value ELSE boolean_value END,
    instant_value = CASE
      WHEN value_type = 4 THEN instant_value + interval '1 millisecond'
      ELSE instant_value END,
    changed_run_revision = changed_run_revision + 1,
    updated_at = timestamptz '2026-07-31 00:00:01+00'
WHERE run_id = 42
""",
    }
    results: dict[str, object] = {}
    for name, operation in operations.items():
        output = run_psql(
            psql,
            "BEGIN;\n"
            + explain_sql(operation, wal=True)
            + "\nROLLBACK;",
        )
        document = parse_json_output(output, f"mutation {name}")
        if (
            not isinstance(document, list)
            or len(document) != 1
            or not isinstance(document[0], dict)
            or not isinstance(document[0].get("Plan"), dict)
        ):
            raise FeasibilityError(
                f"mutation {name} returned malformed EXPLAIN JSON"
            )
        root = document[0]
        results[name] = {
            "executionMs": root.get("Execution Time"),
            "planningMs": root.get("Planning Time"),
            "walRecords": root["Plan"].get("WAL Records", 0),
            "walFpi": root["Plan"].get("WAL FPI", 0),
            "walBytes": root["Plan"].get("WAL Bytes", 0),
            "sharedHitBlocks": root["Plan"].get(
                "Shared Hit Blocks", 0
            ),
            "sharedReadBlocks": root["Plan"].get(
                "Shared Read Blocks", 0
            ),
            "explain": document,
        }
    return results


def build_evidence(arguments: argparse.Namespace) -> dict[str, object]:
    """Create, measure, validate, and optionally retain one corpus."""
    validate_request(arguments)
    require_connection_environment()
    schema_created = False
    cleanup_failure: FeasibilityError | None = None
    started = time.monotonic()
    try:
        identity = server_identity(arguments.psql)
        schema_created = True
        run_psql(
            arguments.psql,
            setup_sql(arguments.schema, arguments.runs),
        )
        setup_seconds = time.monotonic() - started
        corpus = corpus_and_storage(arguments.psql, arguments.schema)
        query_results: list[dict[str, object]] = []
        all_errors: list[str] = []
        scenarios = query_scenarios(arguments.schema)
        if {scenario.name for scenario in scenarios} != EXPECTED_QUERY_NAMES:
            raise FeasibilityError(
                "internal admitted-query matrix does not match its contract"
            )
        for scenario in scenarios:
            # Warm the admitted shape once, then retain the measured raw plan.
            run_psql(arguments.psql, scenario.sql)
            output = run_psql(
                arguments.psql,
                explain_sql(scenario.sql),
            )
            document = parse_json_output(
                output,
                f"query {scenario.name}",
            )
            errors = validate_query_plan(scenario, document)
            all_errors.extend(errors)
            query_results.append(
                {
                    "name": scenario.name,
                    "requiredIndexes": list(scenario.required_indexes),
                    "maximumBufferBlocks": (
                        scenario.maximum_buffer_blocks
                    ),
                    "summary": plan_summary(document),
                    "violations": errors,
                    "explain": document,
                }
            )
        mutations = mutation_diagnostics(
            arguments.psql,
            arguments.schema,
        )
        cardinality_ok = (
            corpus.get("runs") == arguments.runs
            and corpus.get("values") == arguments.runs * 8
        )
        if not cardinality_ok:
            all_errors.append(
                "corpus cardinality does not equal runs and runs*8"
            )
        evidence = {
            "schema": EVIDENCE_SCHEMA,
            "metadata": {
                "commit": arguments.commit,
                "declaredPostgres": arguments.declared_postgres,
                "declaredImage": arguments.declared_image,
                "capabilityProfile": CAPABILITY_PROFILE,
                **identity,
            },
            "corpus": {
                "requestedRuns": arguments.runs,
                "valuesPerRun": 8,
                **corpus,
            },
            "setupSeconds": setup_seconds,
            "queries": query_results,
            "mutations": mutations,
            "checks": {
                "corpusCardinalityPassed": cardinality_ok,
                "allAdmittedPlansPassed": not any(
                    result["violations"] for result in query_results
                ),
                "violations": all_errors,
                "passed": not all_errors,
            },
        }
        write_evidence(arguments.output, evidence)
        if all_errors:
            raise FeasibilityError(
                "Visibility feasibility checks failed: "
                + "; ".join(all_errors)
            )
        return evidence
    finally:
        if schema_created and not arguments.keep_schema:
            try:
                run_psql(
                    arguments.psql,
                    f"DROP SCHEMA {arguments.schema} CASCADE;",
                )
            except FeasibilityError as failure:
                cleanup_failure = failure
        if cleanup_failure is not None and sys.exc_info()[0] is None:
            raise cleanup_failure


def main() -> int:
    """Run one evidence subject and report its high-level result."""
    arguments = parse_args()
    try:
        evidence = build_evidence(arguments)
    except FeasibilityError as failure:
        print(
            f"Durable Visibility feasibility failed: {failure}",
            file=sys.stderr,
        )
        return 1
    corpus = evidence["corpus"]
    print(
        "Durable Visibility feasibility passed: "
        f"PostgreSQL {evidence['metadata']['serverVersion']}, "
        f"runs={corpus['runs']}, values={corpus['values']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
