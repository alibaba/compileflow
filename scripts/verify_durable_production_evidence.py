#!/usr/bin/env python3
"""Verify one complete, same-environment Durable production drill campaign."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Iterable

try:
    from scripts.evidence_io import write_json as write_evidence
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_json as write_evidence


SUBJECT_SCHEMA = "compileflow-durable-production-drill-evidence/v1"
CAMPAIGN_SCHEMA = "compileflow-durable-production-drill-campaign/v1"
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
IDENTIFIER_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
POSTGRES_PATTERN = re.compile(r"[0-9]+\.[0-9]+")
JAVA_VERSION_PATTERN = re.compile(r"([0-9]+)(?:[._+-][A-Za-z0-9]+)*")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
UTC_PATTERN = re.compile(
    r"[0-9]{4}-[0-9]{2}-[0-9]{2}T"
    r"[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z"
)

REQUIRED_SCENARIOS = frozenset(
    {
        "WORKER_KILL_AFTER_CLAIM",
        "WORKER_KILL_AFTER_EFFECT_REQUEST",
        "WORKER_KILL_AFTER_BOUNDARY_COMMIT",
        "OUTBOX_ACK_RESPONSE_LOSS",
        "DATABASE_SHORT_OUTAGE",
        "DATABASE_NETWORK_PARTITION",
        "CONNECTION_POOL_EXHAUSTION",
        "STATEMENT_TIMEOUT",
        "DEADLOCK_OR_SERIALIZATION",
        "PRIMARY_FAILOVER",
        "DISK_PRESSURE",
        "LONG_TRANSACTION_OR_WAL_LAG",
        "OUTBOX_CONSUMER_OUTAGE",
        "PITR_NEW_TIMELINE",
        "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
        "START_RESPONSE_LOSS",
        "TRIGGER_RESPONSE_LOSS",
        "OPERATOR_RESPONSE_LOSS",
    }
)
HA_ONLY_SCENARIOS = frozenset({"PRIMARY_FAILOVER"})
BASE_REQUIRED_SCENARIOS = REQUIRED_SCENARIOS - HA_ONLY_SCENARIOS
TIMELINE_CHANGING_SCENARIOS = frozenset(
    {
        "PITR_NEW_TIMELINE",
        "PRIMARY_FAILOVER",
        "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
    }
)
RPO_ELIGIBLE_SCENARIOS = frozenset(
    {
        "PITR_NEW_TIMELINE",
        "PRIMARY_FAILOVER",
        "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
    }
)
REQUIRED_INVARIANTS = frozenset(
    {
        "STALE_OWNER_CANNOT_COMMIT",
        "UNKNOWN_EFFECT_NOT_AUTO_RESOLVED",
        "COMMITTED_FACTS_IMMUTABLE",
        "IDEMPOTENT_RETRY_SAME_RESULT",
        "AUDIT_TRAIL_PRESERVED",
        "PROCESS_ARTIFACT_RECOVERABLE",
    }
)
RESTORE_SPECIFIC_INVARIANTS = frozenset(
    {
        "RESTORED_ENVIRONMENT_WRITE_ISOLATED",
    }
)
SCENARIO_SPECIFIC_INVARIANTS = {
    "PITR_NEW_TIMELINE": RESTORE_SPECIFIC_INVARIANTS,
    "RESTORE_DURABLE_INVENTORY_RECONCILIATION": (
        RESTORE_SPECIFIC_INVARIANTS
    ),
    "OUTBOX_CONSUMER_OUTAGE": frozenset(
        {
            "PENDING_OUTBOX_NOT_LOST",
            "OUTBOX_RECOVERY_ORDER_AND_DEDUPLICATION_PRESERVED",
        }
    ),
}
ALL_INVARIANTS = REQUIRED_INVARIANTS.union(
    *(values for values in SCENARIO_SPECIFIC_INVARIANTS.values())
)
REQUIRED_APPROVAL_ROLES = frozenset(
    {
        "APPLICATION_OWNER",
        "DATABASE_OPERATOR",
        "INCIDENT_COMMANDER",
    }
)
TOPOLOGIES = frozenset({"SINGLE_PRIMARY", "HA_PRIMARY_STANDBY"})
REPLICATION_MODES = frozenset({"NONE", "SYNCHRONOUS", "ASYNCHRONOUS"})
ENVIRONMENT_FIELDS = frozenset(
    {
        "id",
        "topology",
        "replicationMode",
        "javaVendor",
        "javaVersion",
        "postgres",
        "databaseSystemIdentifier",
        "databaseTimelineBefore",
        "applicationArtifactSha256",
        "configurationSha256",
        "schemaHistorySha256",
        "runbookSha256",
        "faultInjectorSha256",
        "workloadSha256",
    }
)
MEDIA_TYPES = frozenset(
    {
        "application/json",
        "application/sql",
        "application/vnd.tcpdump.pcap",
        "text/csv",
        "text/plain",
    }
)
MAXIMUM_MANIFEST_BYTES = 1_000_000
MAXIMUM_ARTIFACT_BYTES = 100_000_000


class ProductionEvidenceError(ValueError):
    """A drill campaign is incomplete, inconsistent, or unverifiable."""


def parse_args() -> argparse.Namespace:
    """Parse one exact campaign policy."""
    parser = argparse.ArgumentParser(
        description=(
            "Verify the full Durable production fault/recovery drill matrix "
            "and emit a sanitized aggregate manifest."
        )
    )
    parser.add_argument("--evidence-root", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--campaign", required=True)
    parser.add_argument("--environment", required=True)
    parser.add_argument(
        "--maximum-recovery-seconds",
        required=True,
        type=float,
    )
    parser.add_argument(
        "--maximum-data-loss-records",
        required=True,
        type=int,
        help="hard campaign RPO measured as committed records",
    )
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def require_object(
    value: object,
    field: str,
    path: Path,
) -> dict[str, object]:
    """Require one JSON object."""
    if not isinstance(value, dict):
        raise ProductionEvidenceError(f"{path}: {field} must be an object")
    return value


def require_list(value: object, field: str, path: Path) -> list[object]:
    """Require one JSON array."""
    if not isinstance(value, list):
        raise ProductionEvidenceError(f"{path}: {field} must be an array")
    return value


def require_exact_fields(
    value: dict[str, object],
    fields: Iterable[str],
    name: str,
    path: Path,
) -> None:
    """Reject missing and undeclared evidence fields."""
    expected = set(fields)
    if set(value) != expected:
        raise ProductionEvidenceError(
            f"{path}: {name} fields must be exactly {sorted(expected)}"
        )


def reject_duplicate_json_pairs(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    """Reject ambiguous JSON objects instead of silently taking the last key."""
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ProductionEvidenceError(
                f"JSON object contains duplicate key {key!r}"
            )
        result[key] = value
    return result


def require_text(
    value: object,
    field: str,
    path: Path,
    maximum: int = 1_024,
) -> str:
    """Require bounded, non-blank, single-line text."""
    if (
        not isinstance(value, str)
        or not value
        or len(value) > maximum
        or any(character in value for character in "\r\n\0")
    ):
        raise ProductionEvidenceError(
            f"{path}: {field} must contain 1..{maximum} single-line "
            "characters"
        )
    return value


def require_identifier(value: object, field: str, path: Path) -> str:
    """Require one stable opaque identifier."""
    result = require_text(value, field, path, 128)
    if not IDENTIFIER_PATTERN.fullmatch(result):
        raise ProductionEvidenceError(
            f"{path}: {field} must be a stable opaque identifier"
        )
    return result


def require_count(value: object, field: str, path: Path) -> int:
    """Require a non-negative integer."""
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value < 0
    ):
        raise ProductionEvidenceError(
            f"{path}: {field} must be a non-negative integer"
        )
    return value


def require_finite(value: object, field: str, path: Path) -> float:
    """Require a finite non-negative duration."""
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(value)
        or value < 0
    ):
        raise ProductionEvidenceError(
            f"{path}: {field} must be a finite non-negative number"
        )
    return float(value)


def require_sha256(value: object, field: str, path: Path) -> str:
    """Require a lowercase SHA-256 digest."""
    result = require_text(value, field, path, 64)
    if not SHA256_PATTERN.fullmatch(result):
        raise ProductionEvidenceError(
            f"{path}: {field} must be a lowercase SHA-256 digest"
        )
    return result


def require_utc(value: object, field: str, path: Path) -> datetime:
    """Parse one canonical UTC timestamp."""
    text = require_text(value, field, path, 32)
    if not UTC_PATTERN.fullmatch(text):
        raise ProductionEvidenceError(
            f"{path}: {field} must be canonical RFC 3339 UTC"
        )
    try:
        result = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as failure:
        raise ProductionEvidenceError(
            f"{path}: {field} is not a valid timestamp"
        ) from failure
    if result.tzinfo != timezone.utc:
        raise ProductionEvidenceError(
            f"{path}: {field} must use UTC"
        )
    return result


def discover_subjects(root: Path) -> tuple[Path, ...]:
    """Discover raw drill manifests below one campaign root."""
    if not root.is_dir() or root.is_symlink():
        raise ProductionEvidenceError(
            f"{root}: evidence root must be a real directory"
        )
    resolved_root = root.resolve()
    result: list[Path] = []
    for path in root.glob("**/*durable-drill-evidence.json"):
        resolved = path.resolve()
        if (
            not path.is_file()
            or path.is_symlink()
            or not resolved.is_relative_to(resolved_root)
        ):
            raise ProductionEvidenceError(
                f"{path}: drill manifest is missing or unsafe"
            )
        result.append(resolved)
    return tuple(sorted(result))


def load_document(path: Path) -> tuple[dict[str, object], str]:
    """Load one manifest and bind its exact bytes."""
    if not path.is_file() or path.is_symlink():
        raise ProductionEvidenceError(
            f"{path}: drill manifest must be a real file"
        )
    try:
        content = path.read_bytes()
    except OSError as failure:
        raise ProductionEvidenceError(
            f"{path}: cannot read drill evidence: {failure}"
        ) from failure
    if not content or len(content) > MAXIMUM_MANIFEST_BYTES:
        raise ProductionEvidenceError(
            f"{path}: drill manifest must contain 1..1000000 bytes"
        )
    try:
        document = json.loads(
            content,
            object_pairs_hook=reject_duplicate_json_pairs,
        )
    except json.JSONDecodeError as failure:
        raise ProductionEvidenceError(
            f"{path}: cannot parse drill evidence JSON: {failure}"
        ) from failure
    return (
        require_object(document, "root", path),
        hashlib.sha256(content).hexdigest(),
    )


def validate_environment(
    value: object,
    expected_environment: str,
    path: Path,
) -> dict[str, object]:
    """Validate sanitized, stable environment identity."""
    environment = require_object(value, "environment", path)
    require_exact_fields(
        environment,
        ENVIRONMENT_FIELDS,
        "environment",
        path,
    )
    identifier = require_identifier(environment.get("id"), "environment.id", path)
    if identifier != expected_environment:
        raise ProductionEvidenceError(
            f"{path}: environment.id does not match the requested campaign"
        )
    topology = require_text(
        environment.get("topology"),
        "environment.topology",
        path,
        32,
    )
    if topology not in TOPOLOGIES:
        raise ProductionEvidenceError(f"{path}: unsupported environment topology")
    replication_mode = require_text(
        environment.get("replicationMode"),
        "environment.replicationMode",
        path,
        32,
    )
    if replication_mode not in REPLICATION_MODES:
        raise ProductionEvidenceError(f"{path}: unsupported replication mode")
    if (
        topology == "SINGLE_PRIMARY" and replication_mode != "NONE"
    ) or (
        topology == "HA_PRIMARY_STANDBY" and replication_mode == "NONE"
    ):
        raise ProductionEvidenceError(
            f"{path}: replication mode does not match topology"
        )
    require_text(
        environment.get("javaVendor"),
        "environment.javaVendor",
        path,
        64,
    )
    java_version = require_text(
        environment.get("javaVersion"),
        "environment.javaVersion",
        path,
        64,
    )
    java_match = JAVA_VERSION_PATTERN.fullmatch(java_version)
    if java_match is None or int(java_match.group(1)) < 17:
        raise ProductionEvidenceError(
            f"{path}: environment.javaVersion is unsupported"
        )
    postgres = require_text(
        environment.get("postgres"),
        "environment.postgres",
        path,
        32,
    )
    if not POSTGRES_PATTERN.fullmatch(postgres):
        raise ProductionEvidenceError(f"{path}: environment.postgres is invalid")
    require_identifier(
        environment.get("databaseSystemIdentifier"),
        "environment.databaseSystemIdentifier",
        path,
    )
    require_identifier(
        environment.get("databaseTimelineBefore"),
        "environment.databaseTimelineBefore",
        path,
    )
    require_sha256(
        environment.get("applicationArtifactSha256"),
        "environment.applicationArtifactSha256",
        path,
    )
    require_sha256(
        environment.get("configurationSha256"),
        "environment.configurationSha256",
        path,
    )
    for field in (
        "schemaHistorySha256",
        "runbookSha256",
        "faultInjectorSha256",
        "workloadSha256",
    ):
        require_sha256(environment.get(field), f"environment.{field}", path)
    return environment


def validate_artifacts(
    value: object,
    manifest_path: Path,
) -> tuple[list[dict[str, object]], set[str]]:
    """Authenticate bounded, local raw evidence artifacts."""
    entries = require_list(value, "artifacts", manifest_path)
    if not entries or len(entries) > 64:
        raise ProductionEvidenceError(
            f"{manifest_path}: artifacts must contain 1..64 entries"
        )
    result: list[dict[str, object]] = []
    paths: set[str] = set()
    root = manifest_path.parent.resolve()
    for index, item in enumerate(entries):
        artifact = require_object(item, f"artifacts[{index}]", manifest_path)
        require_exact_fields(
            artifact,
            ("path", "sha256", "sizeBytes", "mediaType"),
            f"artifacts[{index}]",
            manifest_path,
        )
        relative = require_text(
            artifact.get("path"),
            f"artifacts[{index}].path",
            manifest_path,
            512,
        )
        pure = PurePosixPath(relative)
        if (
            pure.is_absolute()
            or ".." in pure.parts
            or "." in pure.parts
            or "\\" in relative
            or pure.as_posix() != relative
            or relative in paths
        ):
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact path {relative!r} is unsafe "
                "or duplicated"
            )
        candidate = manifest_path.parent.joinpath(*pure.parts)
        resolved = candidate.resolve()
        if (
            not resolved.is_relative_to(root)
            or not candidate.is_file()
            or candidate.is_symlink()
        ):
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact {relative!r} is missing or unsafe"
            )
        size = require_count(
            artifact.get("sizeBytes"),
            f"artifacts[{index}].sizeBytes",
            manifest_path,
        )
        try:
            actual_size = candidate.stat().st_size
        except OSError as failure:
            raise ProductionEvidenceError(
                f"{manifest_path}: cannot stat artifact {relative!r}: "
                f"{failure}"
            ) from failure
        if (
            size <= 0
            or size > MAXIMUM_ARTIFACT_BYTES
            or actual_size != size
        ):
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact {relative!r} size does not match"
            )
        try:
            content = candidate.read_bytes()
        except OSError as failure:
            raise ProductionEvidenceError(
                f"{manifest_path}: cannot read artifact {relative!r}: "
                f"{failure}"
            ) from failure
        if len(content) != size:
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact {relative!r} size changed "
                "while reading"
            )
        digest = require_sha256(
            artifact.get("sha256"),
            f"artifacts[{index}].sha256",
            manifest_path,
        )
        if hashlib.sha256(content).hexdigest() != digest:
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact {relative!r} digest does not match"
            )
        media_type = require_text(
            artifact.get("mediaType"),
            f"artifacts[{index}].mediaType",
            manifest_path,
            64,
        )
        if media_type not in MEDIA_TYPES:
            raise ProductionEvidenceError(
                f"{manifest_path}: artifact {relative!r} media type is not "
                "admitted"
            )
        paths.add(relative)
        result.append(
            {
                "path": relative,
                "sha256": digest,
                "sizeBytes": size,
                "mediaType": media_type,
            }
        )
    return result, paths


def validate_invariants(
    value: object,
    artifact_paths: set[str],
    scenario: str,
    path: Path,
) -> list[dict[str, str]]:
    """Require cross-cutting and scenario-specific invariants to pass."""
    entries = require_list(value, "invariants", path)
    observed: dict[str, dict[str, str]] = {}
    for index, item in enumerate(entries):
        invariant = require_object(item, f"invariants[{index}]", path)
        require_exact_fields(
            invariant,
            ("id", "status", "evidenceArtifact"),
            f"invariants[{index}]",
            path,
        )
        identifier = require_text(
            invariant.get("id"),
            f"invariants[{index}].id",
            path,
            64,
        )
        status = require_text(
            invariant.get("status"),
            f"invariants[{index}].status",
            path,
            16,
        )
        artifact = require_text(
            invariant.get("evidenceArtifact"),
            f"invariants[{index}].evidenceArtifact",
            path,
            512,
        )
        if identifier in observed:
            raise ProductionEvidenceError(
                f"{path}: duplicate invariant {identifier!r}"
            )
        if status != "PASSED":
            raise ProductionEvidenceError(
                f"{path}: invariant {identifier!r} did not pass"
            )
        if artifact not in artifact_paths:
            raise ProductionEvidenceError(
                f"{path}: invariant {identifier!r} references unknown "
                "artifact"
            )
        observed[identifier] = {
            "id": identifier,
            "status": status,
            "evidenceArtifact": artifact,
        }
    expected = REQUIRED_INVARIANTS | SCENARIO_SPECIFIC_INVARIANTS.get(
        scenario, frozenset()
    )
    if set(observed) != expected:
        raise ProductionEvidenceError(
            f"{path}: invariant matrix mismatch; missing="
            f"{sorted(expected - observed.keys())}, unexpected="
            f"{sorted(observed.keys() - expected)}"
        )
    return [observed[key] for key in sorted(observed)]


def validate_approvals(
    value: object,
    completed_at: datetime,
    path: Path,
) -> None:
    """Require all operational ownership roles to approve after the drill."""
    entries = require_list(value, "approvals", path)
    roles: list[str] = []
    principals: list[str] = []
    for index, item in enumerate(entries):
        approval = require_object(item, f"approvals[{index}]", path)
        require_exact_fields(
            approval,
            ("role", "principal", "approvedAt"),
            f"approvals[{index}]",
            path,
        )
        role = require_text(
            approval.get("role"),
            f"approvals[{index}].role",
            path,
            32,
        )
        principal = require_identifier(
            approval.get("principal"),
            f"approvals[{index}].principal",
            path,
        )
        approved = require_utc(
            approval.get("approvedAt"),
            f"approvals[{index}].approvedAt",
            path,
        )
        if approved < completed_at:
            raise ProductionEvidenceError(
                f"{path}: approval {role!r} predates drill completion"
            )
        roles.append(role)
        principals.append(principal)
    duplicates = sorted(
        role for role, count in Counter(roles).items() if count > 1
    )
    if duplicates or set(roles) != REQUIRED_APPROVAL_ROLES:
        raise ProductionEvidenceError(
            f"{path}: approval roles must be exactly "
            f"{sorted(REQUIRED_APPROVAL_ROLES)}"
        )
    if len(set(principals)) != len(REQUIRED_APPROVAL_ROLES):
        raise ProductionEvidenceError(
            f"{path}: approval roles require distinct principals"
        )


def validate_subject(
    path: Path,
    document: dict[str, object],
    digest: str,
    *,
    commit: str,
    campaign: str,
    environment_id: str,
    maximum_recovery_seconds: float,
    maximum_data_loss_records: int,
) -> dict[str, object]:
    """Validate one drill without trusting its declared pass result."""
    require_exact_fields(
        document,
        (
            "schema",
            "campaignId",
            "evidenceId",
            "commit",
            "environment",
            "scenario",
            "startedAt",
            "completedAt",
            "thresholds",
            "observations",
            "artifacts",
            "invariants",
            "approvals",
        ),
        "root",
        path,
    )
    if document.get("schema") != SUBJECT_SCHEMA:
        raise ProductionEvidenceError(f"{path}: unsupported subject schema")
    observed_campaign = require_identifier(
        document.get("campaignId"), "campaignId", path
    )
    if observed_campaign != campaign:
        raise ProductionEvidenceError(f"{path}: campaignId does not match")
    evidence_id = require_identifier(
        document.get("evidenceId"), "evidenceId", path
    )
    observed_commit = require_text(document.get("commit"), "commit", path, 40)
    if observed_commit != commit:
        raise ProductionEvidenceError(f"{path}: commit does not match")
    environment = validate_environment(
        document.get("environment"), environment_id, path
    )
    scenario = require_text(document.get("scenario"), "scenario", path, 64)
    if scenario not in REQUIRED_SCENARIOS:
        raise ProductionEvidenceError(f"{path}: unsupported drill scenario")
    started_at = require_utc(document.get("startedAt"), "startedAt", path)
    completed_at = require_utc(document.get("completedAt"), "completedAt", path)
    if completed_at <= started_at:
        raise ProductionEvidenceError(
            f"{path}: completedAt must be after startedAt"
        )
    elapsed_seconds = (completed_at - started_at).total_seconds()

    thresholds = require_object(document.get("thresholds"), "thresholds", path)
    require_exact_fields(
        thresholds,
        ("maximumRecoverySeconds", "maximumDataLossRecords"),
        "thresholds",
        path,
    )
    subject_recovery_limit = require_finite(
        thresholds.get("maximumRecoverySeconds"),
        "thresholds.maximumRecoverySeconds",
        path,
    )
    subject_loss_limit = require_count(
        thresholds.get("maximumDataLossRecords"),
        "thresholds.maximumDataLossRecords",
        path,
    )
    if (
        subject_recovery_limit > maximum_recovery_seconds
        or subject_loss_limit > maximum_data_loss_records
    ):
        raise ProductionEvidenceError(
            f"{path}: subject thresholds weaken the campaign policy"
        )

    observations = require_object(
        document.get("observations"), "observations", path
    )
    require_exact_fields(
        observations,
        (
            "result",
            "recoverySeconds",
            "dataLossRecords",
            "databaseSystemIdentifierAfter",
            "databaseTimelineAfter",
        ),
        "observations",
        path,
    )
    if observations.get("result") != "PASSED":
        raise ProductionEvidenceError(f"{path}: drill result did not pass")
    recovery_seconds = require_finite(
        observations.get("recoverySeconds"),
        "observations.recoverySeconds",
        path,
    )
    data_loss_records = require_count(
        observations.get("dataLossRecords"),
        "observations.dataLossRecords",
        path,
    )
    if (
        recovery_seconds > subject_recovery_limit
        or data_loss_records > subject_loss_limit
    ):
        raise ProductionEvidenceError(
            f"{path}: observed recovery exceeds declared thresholds"
        )
    if (
        scenario not in RPO_ELIGIBLE_SCENARIOS
        and (subject_loss_limit != 0 or data_loss_records != 0)
    ):
        raise ProductionEvidenceError(
            f"{path}: {scenario} must have zero committed-record loss"
        )
    if (
        scenario == "PRIMARY_FAILOVER"
        and environment["replicationMode"] == "SYNCHRONOUS"
        and (subject_loss_limit != 0 or data_loss_records != 0)
    ):
        raise ProductionEvidenceError(
            f"{path}: synchronous PRIMARY_FAILOVER must have zero "
            "committed-record loss"
        )
    if recovery_seconds > elapsed_seconds:
        raise ProductionEvidenceError(
            f"{path}: observed recovery exceeds the recorded drill window"
        )
    system_identifier_after = require_identifier(
        observations.get("databaseSystemIdentifierAfter"),
        "observations.databaseSystemIdentifierAfter",
        path,
    )
    if system_identifier_after != environment["databaseSystemIdentifier"]:
        raise ProductionEvidenceError(
            f"{path}: recovered database system identifier does not match "
            "the campaign environment"
        )
    timeline_after = require_identifier(
        observations.get("databaseTimelineAfter"),
        "observations.databaseTimelineAfter",
        path,
    )
    if (
        scenario in TIMELINE_CHANGING_SCENARIOS
        and timeline_after == environment["databaseTimelineBefore"]
    ):
        raise ProductionEvidenceError(
            f"{path}: {scenario} must record a new database timeline"
        )
    if (
        scenario not in TIMELINE_CHANGING_SCENARIOS
        and timeline_after != environment["databaseTimelineBefore"]
    ):
        raise ProductionEvidenceError(
            f"{path}: {scenario} must not hide a database timeline change"
        )
    if (
        scenario == "PRIMARY_FAILOVER"
        and environment["topology"] != "HA_PRIMARY_STANDBY"
    ):
        raise ProductionEvidenceError(
            f"{path}: PRIMARY_FAILOVER requires HA_PRIMARY_STANDBY topology"
        )

    artifacts, artifact_paths = validate_artifacts(
        document.get("artifacts"), path
    )
    invariants = validate_invariants(
        document.get("invariants"), artifact_paths, scenario, path
    )
    validate_approvals(document.get("approvals"), completed_at, path)
    return {
        "scenario": scenario,
        "evidenceId": evidence_id,
        "startedAt": document["startedAt"],
        "completedAt": document["completedAt"],
        "recoverySeconds": recovery_seconds,
        "dataLossRecords": data_loss_records,
        "databaseSystemIdentifierAfter": system_identifier_after,
        "databaseTimelineAfter": timeline_after,
        "artifacts": artifacts,
        "invariants": invariants,
        "environment": environment,
        "inputSha256": digest,
    }


def build_campaign(
    *,
    paths: Iterable[Path],
    commit: str,
    campaign: str,
    environment: str,
    maximum_recovery_seconds: float,
    maximum_data_loss_records: int,
) -> dict[str, object]:
    """Build one complete sanitized campaign manifest."""
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ProductionEvidenceError(
            "commit must be a lowercase 40-character Git SHA"
        )
    if not IDENTIFIER_PATTERN.fullmatch(campaign):
        raise ProductionEvidenceError("campaign is invalid")
    if not IDENTIFIER_PATTERN.fullmatch(environment):
        raise ProductionEvidenceError("environment is invalid")
    if (
        not math.isfinite(maximum_recovery_seconds)
        or maximum_recovery_seconds <= 0
    ):
        raise ProductionEvidenceError(
            "maximum_recovery_seconds must be finite and positive"
        )
    if (
        isinstance(maximum_data_loss_records, bool)
        or not isinstance(maximum_data_loss_records, int)
        or maximum_data_loss_records < 0
    ):
        raise ProductionEvidenceError(
            "maximum_data_loss_records must be non-negative"
        )

    input_paths = tuple(sorted({path.resolve() for path in paths}))
    if not input_paths or len(input_paths) > len(REQUIRED_SCENARIOS):
        raise ProductionEvidenceError(
            "expected 1.."
            f"{len(REQUIRED_SCENARIOS)} drill manifests before topology "
            f"validation, found {len(input_paths)}"
        )
    subjects: dict[str, dict[str, object]] = {}
    evidence_ids: set[str] = set()
    canonical_environment: dict[str, object] | None = None
    for path in input_paths:
        document, digest = load_document(path)
        subject = validate_subject(
            path,
            document,
            digest,
            commit=commit,
            campaign=campaign,
            environment_id=environment,
            maximum_recovery_seconds=maximum_recovery_seconds,
            maximum_data_loss_records=maximum_data_loss_records,
        )
        scenario = str(subject["scenario"])
        evidence_id = str(subject["evidenceId"])
        if scenario in subjects:
            raise ProductionEvidenceError(
                f"duplicate drill scenario {scenario!r}"
            )
        if evidence_id in evidence_ids:
            raise ProductionEvidenceError(
                f"duplicate drill evidenceId {evidence_id!r}"
            )
        subject_environment = require_object(
            subject.pop("environment"), "environment", path
        )
        if canonical_environment is None:
            canonical_environment = subject_environment
        elif subject_environment != canonical_environment:
            raise ProductionEvidenceError(
                f"{path}: environment identity differs within campaign"
            )
        evidence_ids.add(evidence_id)
        subjects[scenario] = subject

    assert canonical_environment is not None
    required_scenarios = (
        REQUIRED_SCENARIOS
        if canonical_environment["topology"] == "HA_PRIMARY_STANDBY"
        else BASE_REQUIRED_SCENARIOS
    )
    if set(subjects) != required_scenarios:
        raise ProductionEvidenceError(
            "drill scenario matrix mismatch; missing="
            f"{sorted(required_scenarios - subjects.keys())}, unexpected="
            f"{sorted(subjects.keys() - required_scenarios)}"
        )
    environment_bytes = json.dumps(
        canonical_environment,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
    ).encode("utf-8")
    ordered = [subjects[key] for key in sorted(subjects)]
    return {
        "schema": CAMPAIGN_SCHEMA,
        "campaignId": campaign,
        "commit": commit,
        "environmentId": environment,
        "environmentSha256": hashlib.sha256(environment_bytes).hexdigest(),
        "environment": canonical_environment,
        "policy": {
            "maximumRecoverySeconds": float(maximum_recovery_seconds),
            "maximumDataLossRecords": maximum_data_loss_records,
            "requiredScenarios": sorted(required_scenarios),
            "requiredInvariants": sorted(REQUIRED_INVARIANTS),
            "scenarioSpecificInvariants": {
                scenario: sorted(invariants)
                for scenario, invariants in sorted(
                    SCENARIO_SPECIFIC_INVARIANTS.items()
                )
            },
            "requiredApprovalRoles": sorted(REQUIRED_APPROVAL_ROLES),
        },
        "summary": {
            "subjects": len(ordered),
            "maximumObservedRecoverySeconds": max(
                float(subject["recoverySeconds"]) for subject in ordered
            ),
            "maximumObservedDataLossRecords": max(
                int(subject["dataLossRecords"]) for subject in ordered
            ),
            "allPassed": True,
        },
        "subjects": ordered,
    }


def main() -> int:
    """Verify a complete campaign and emit its sanitized index."""
    arguments = parse_args()
    try:
        evidence = build_campaign(
            paths=discover_subjects(arguments.evidence_root),
            commit=arguments.commit,
            campaign=arguments.campaign,
            environment=arguments.environment,
            maximum_recovery_seconds=arguments.maximum_recovery_seconds,
            maximum_data_loss_records=arguments.maximum_data_loss_records,
        )
        write_evidence(arguments.output, evidence)
    except ProductionEvidenceError as failure:
        print(
            f"Durable production evidence failed: {failure}",
            file=sys.stderr,
        )
        return 1
    print(f"Validated Durable production evidence: {evidence['summary']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
