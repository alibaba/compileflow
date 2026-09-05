import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scripts.verify_durable_production_evidence import (
    ALL_INVARIANTS,
    BASE_REQUIRED_SCENARIOS,
    CAMPAIGN_SCHEMA,
    ENVIRONMENT_FIELDS,
    MEDIA_TYPES,
    REPLICATION_MODES,
    REQUIRED_APPROVAL_ROLES,
    REQUIRED_INVARIANTS,
    REQUIRED_SCENARIOS,
    RESTORE_SPECIFIC_INVARIANTS,
    SCENARIO_SPECIFIC_INVARIANTS,
    SUBJECT_SCHEMA,
    TOPOLOGIES,
    ProductionEvidenceError,
    build_campaign,
    discover_subjects,
    write_evidence,
)


COMMIT = "a" * 40
CAMPAIGN = "production-candidate-1"
ENVIRONMENT = "design-partner-a"
DIGEST = "b" * 64


def subject(root: Path, scenario: str) -> Path:
    directory = root / scenario.lower()
    directory.mkdir(parents=True)
    artifact = directory / "observations.json"
    content = json.dumps(
        {"scenario": scenario, "sanitized": True},
        sort_keys=True,
    ).encode("utf-8")
    artifact.write_bytes(content)
    timeline_after = (
        "timeline-2"
        if scenario
        in {
            "PITR_NEW_TIMELINE",
            "PRIMARY_FAILOVER",
            "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
        }
        else "timeline-1"
    )
    document = {
        "schema": "compileflow-durable-production-drill-evidence/v1",
        "campaignId": CAMPAIGN,
        "evidenceId": "evidence-" + scenario.lower().replace("_", "-"),
        "commit": COMMIT,
        "environment": {
            "id": ENVIRONMENT,
            "topology": "HA_PRIMARY_STANDBY",
            "replicationMode": "SYNCHRONOUS",
            "javaVendor": "Eclipse Adoptium",
            "javaVersion": "21.0.8+9-LTS",
            "postgres": "17.11",
            "databaseSystemIdentifier": "system-1",
            "databaseTimelineBefore": "timeline-1",
            "applicationArtifactSha256": DIGEST,
            "configurationSha256": "c" * 64,
            "schemaHistorySha256": "d" * 64,
            "runbookSha256": "e" * 64,
            "faultInjectorSha256": "f" * 64,
            "workloadSha256": "1" * 64,
        },
        "scenario": scenario,
        "startedAt": "2026-07-31T10:00:00Z",
        "completedAt": "2026-07-31T10:05:00Z",
        "thresholds": {
            "maximumRecoverySeconds": 300.0,
            "maximumDataLossRecords": 0,
        },
        "observations": {
            "result": "PASSED",
            "recoverySeconds": 30.0,
            "dataLossRecords": 0,
            "databaseSystemIdentifierAfter": "system-1",
            "databaseTimelineAfter": timeline_after,
        },
        "artifacts": [
            {
                "path": artifact.name,
                "sha256": hashlib.sha256(content).hexdigest(),
                "sizeBytes": len(content),
                "mediaType": "application/json",
            }
        ],
        "invariants": [
            {
                "id": invariant,
                "status": "PASSED",
                "evidenceArtifact": artifact.name,
            }
            for invariant in sorted(
                REQUIRED_INVARIANTS
                | SCENARIO_SPECIFIC_INVARIANTS.get(scenario, frozenset())
            )
        ],
        "approvals": [
            {
                "role": role,
                "principal": "principal-" + role.lower().replace("_", "-"),
                "approvedAt": "2026-07-31T10:10:00Z",
            }
            for role in sorted(REQUIRED_APPROVAL_ROLES)
        ],
    }
    path = directory / f"{scenario.lower()}-durable-drill-evidence.json"
    path.write_text(json.dumps(document, sort_keys=True), encoding="utf-8")
    return path


def complete_campaign(root: Path) -> list[Path]:
    return [subject(root, scenario) for scenario in sorted(REQUIRED_SCENARIOS)]


def single_primary_campaign(root: Path) -> list[Path]:
    paths = [subject(root, scenario) for scenario in sorted(BASE_REQUIRED_SCENARIOS)]
    for path in paths:
        document = json.loads(path.read_text(encoding="utf-8"))
        document["environment"]["topology"] = "SINGLE_PRIMARY"
        document["environment"]["replicationMode"] = "NONE"
        path.write_text(json.dumps(document, sort_keys=True), encoding="utf-8")
    return paths


def build(paths: list[Path]) -> dict[str, object]:
    return build_campaign(
        paths=paths,
        commit=COMMIT,
        campaign=CAMPAIGN,
        environment=ENVIRONMENT,
        maximum_recovery_seconds=300.0,
        maximum_data_loss_records=0,
    )


class VerifyDurableProductionEvidenceTest(unittest.TestCase):

    def test_published_subject_schema_matches_verifier_policy(self) -> None:
        schema_path = (
            Path(__file__).resolve().parents[2]
            / "docs/specs/compileflow-durable-production-drill-evidence-v1.schema.json"
        )
        schema = json.loads(schema_path.read_text(encoding="utf-8"))

        self.assertEqual(SUBJECT_SCHEMA, schema["properties"]["schema"]["const"])
        self.assertEqual(
            REQUIRED_SCENARIOS,
            frozenset(schema["properties"]["scenario"]["enum"]),
        )
        self.assertEqual(
            ALL_INVARIANTS,
            frozenset(schema["$defs"]["invariant"]["properties"]["id"]["enum"]),
        )
        self.assertEqual(6, schema["properties"]["invariants"]["minItems"])
        self.assertEqual(8, schema["properties"]["invariants"]["maxItems"])
        self.assertEqual(
            frozenset(
                {
                    "result",
                    "recoverySeconds",
                    "dataLossRecords",
                    "databaseSystemIdentifierAfter",
                    "databaseTimelineAfter",
                }
            ),
            frozenset(schema["properties"]["observations"]["required"]),
        )
        self.assertEqual(
            REQUIRED_APPROVAL_ROLES,
            frozenset(schema["$defs"]["approval"]["properties"]["role"]["enum"]),
        )
        self.assertEqual(
            TOPOLOGIES,
            frozenset(schema["$defs"]["environment"]["properties"]["topology"]["enum"]),
        )
        self.assertEqual(
            REPLICATION_MODES,
            frozenset(
                schema["$defs"]["environment"]["properties"]
                ["replicationMode"]["enum"]
            ),
        )
        self.assertEqual(
            MEDIA_TYPES,
            frozenset(schema["$defs"]["artifact"]["properties"]["mediaType"]["enum"]),
        )
        self.assertEqual(
            ENVIRONMENT_FIELDS,
            frozenset(schema["$defs"]["environment"]["required"]),
        )

    def test_builds_complete_same_environment_campaign(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            campaign = build(complete_campaign(Path(directory)))

        self.assertEqual(CAMPAIGN_SCHEMA, campaign["schema"])
        self.assertEqual(18, len(REQUIRED_SCENARIOS))
        self.assertEqual(17, len(BASE_REQUIRED_SCENARIOS))
        self.assertEqual(len(REQUIRED_SCENARIOS), campaign["summary"]["subjects"])
        self.assertTrue(campaign["summary"]["allPassed"])
        self.assertEqual(30.0, campaign["summary"]["maximumObservedRecoverySeconds"])
        self.assertEqual(
            "Eclipse Adoptium",
            campaign["environment"]["javaVendor"],
        )
        self.assertNotIn("approvals", campaign["subjects"][0])
        self.assertEqual(
            {
                scenario: sorted(invariants)
                for scenario, invariants in sorted(
                    SCENARIO_SPECIFIC_INVARIANTS.items()
                )
            },
            campaign["policy"]["scenarioSpecificInvariants"],
        )

    def test_builds_single_primary_campaign_without_ha_only_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            campaign = build(single_primary_campaign(Path(directory)))

        self.assertEqual(len(BASE_REQUIRED_SCENARIOS), campaign["summary"]["subjects"])
        self.assertEqual(
            sorted(BASE_REQUIRED_SCENARIOS),
            campaign["policy"]["requiredScenarios"],
        )
        self.assertEqual("SINGLE_PRIMARY", campaign["environment"]["topology"])
        self.assertEqual("NONE", campaign["environment"]["replicationMode"])

    def test_rejects_replication_mode_that_conflicts_with_topology(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = [
                subject(Path(directory), scenario)
                for scenario in sorted(BASE_REQUIRED_SCENARIOS)
            ]
            for path in paths:
                document = json.loads(path.read_text(encoding="utf-8"))
                document["environment"]["topology"] = "SINGLE_PRIMARY"
                path.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "replication mode does not match topology",
            ):
                build(paths)

    def test_discovers_nested_inputs_and_writes_sanitized_aggregate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = root / "downloaded"
            complete_campaign(inputs)
            paths = discover_subjects(inputs)
            campaign = build(list(paths))
            output = root / "campaign.json"

            write_evidence(output, campaign)
            persisted = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(len(REQUIRED_SCENARIOS), len(paths))
        self.assertEqual(campaign, persisted)

    def test_rejects_symlinked_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_root = root / "evidence"
            evidence_root.mkdir()
            outside = root / "outside-durable-drill-evidence.json"
            outside.write_text("{}", encoding="utf-8")
            link = evidence_root / "linked-durable-drill-evidence.json"
            link.symlink_to(outside)

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "drill manifest is missing or unsafe",
            ):
                discover_subjects(evidence_root)

    def test_rejects_missing_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            paths.pop()

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "drill scenario matrix mismatch.*missing",
            ):
                build(paths)

    def test_allows_predeclared_rpo_for_pitr(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            pitr = next(
                path for path in paths if "pitr_new_timeline" in path.name
            )
            document = json.loads(pitr.read_text(encoding="utf-8"))
            document["thresholds"]["maximumDataLossRecords"] = 2
            document["observations"]["dataLossRecords"] = 1
            pitr.write_text(json.dumps(document), encoding="utf-8")

            campaign = build_campaign(
                paths=paths,
                commit=COMMIT,
                campaign=CAMPAIGN,
                environment=ENVIRONMENT,
                maximum_recovery_seconds=300.0,
                maximum_data_loss_records=2,
            )

        self.assertEqual(1, campaign["summary"]["maximumObservedDataLossRecords"])

    def test_rejects_nonzero_loss_for_non_rpo_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            scenario = document["scenario"]
            self.assertNotIn(
                scenario,
                {
                    "PITR_NEW_TIMELINE",
                    "PRIMARY_FAILOVER",
                    "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
                },
            )
            document["thresholds"]["maximumDataLossRecords"] = 1
            document["observations"]["dataLossRecords"] = 1
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                f"{scenario} must have zero committed-record loss",
            ):
                build_campaign(
                    paths=paths,
                    commit=COMMIT,
                    campaign=CAMPAIGN,
                    environment=ENVIRONMENT,
                    maximum_recovery_seconds=300.0,
                    maximum_data_loss_records=1,
                )

    def test_async_failover_may_use_predeclared_rpo(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            for path in paths:
                document = json.loads(path.read_text(encoding="utf-8"))
                document["environment"]["replicationMode"] = "ASYNCHRONOUS"
                if document["scenario"] == "PRIMARY_FAILOVER":
                    document["thresholds"]["maximumDataLossRecords"] = 1
                    document["observations"]["dataLossRecords"] = 1
                path.write_text(json.dumps(document), encoding="utf-8")

            campaign = build_campaign(
                paths=paths,
                commit=COMMIT,
                campaign=CAMPAIGN,
                environment=ENVIRONMENT,
                maximum_recovery_seconds=300.0,
                maximum_data_loss_records=1,
            )

        self.assertEqual("ASYNCHRONOUS", campaign["environment"]["replicationMode"])
        self.assertEqual(1, campaign["summary"]["maximumObservedDataLossRecords"])

    def test_sync_failover_requires_zero_loss(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            failover = next(
                path for path in paths if "primary_failover" in path.name
            )
            document = json.loads(failover.read_text(encoding="utf-8"))
            document["thresholds"]["maximumDataLossRecords"] = 1
            document["observations"]["dataLossRecords"] = 1
            failover.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "synchronous PRIMARY_FAILOVER must have zero",
            ):
                build_campaign(
                    paths=paths,
                    commit=COMMIT,
                    campaign=CAMPAIGN,
                    environment=ENVIRONMENT,
                    maximum_recovery_seconds=300.0,
                    maximum_data_loss_records=1,
                )

    def test_rejects_mixed_commit_and_environment(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["commit"] = "d" * 40
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "commit does not match",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["environment"]["configurationSha256"] = "d" * 64
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "environment identity differs",
            ):
                build(paths)

    def test_rejects_weakened_or_missed_rto_rpo(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["thresholds"]["maximumRecoverySeconds"] = 301.0
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "thresholds weaken the campaign policy",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["observations"]["dataLossRecords"] = 1
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "exceeds declared thresholds",
            ):
                build(paths)

    def test_rejects_failed_or_missing_invariant(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["invariants"][0]["status"] = "FAILED"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "invariant .* did not pass",
            ):
                build(paths)

    def test_requires_scenario_specific_invariants(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            outbox_outage = next(
                path for path in paths if "outbox_consumer_outage" in path.name
            )
            document = json.loads(outbox_outage.read_text(encoding="utf-8"))
            document["invariants"] = [
                invariant
                for invariant in document["invariants"]
                if invariant["id"] != "PENDING_OUTBOX_NOT_LOST"
            ]
            outbox_outage.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "invariant matrix mismatch.*PENDING_OUTBOX_NOT_LOST",
            ):
                build(paths)

    def test_requires_restore_artifact_and_isolation_invariants(
        self,
    ) -> None:
        for scenario in (
            "PITR_NEW_TIMELINE",
            "RESTORE_DURABLE_INVENTORY_RECONCILIATION",
        ):
            for missing in sorted(RESTORE_SPECIFIC_INVARIANTS):
                with self.subTest(scenario=scenario, missing=missing):
                    with tempfile.TemporaryDirectory() as directory:
                        paths = complete_campaign(Path(directory))
                        recovery = next(
                            path
                            for path in paths
                            if json.loads(
                                path.read_text(encoding="utf-8")
                            )["scenario"] == scenario
                        )
                        document = json.loads(
                            recovery.read_text(encoding="utf-8")
                        )
                        document["invariants"] = [
                            invariant
                            for invariant in document["invariants"]
                            if invariant["id"] != missing
                        ]
                        recovery.write_text(
                            json.dumps(document),
                            encoding="utf-8",
                        )

                        with self.assertRaisesRegex(
                            ProductionEvidenceError,
                            "invariant matrix mismatch",
                        ):
                            build(paths)

    def test_rejects_scenario_specific_invariant_on_unrelated_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            worker = next(
                path for path in paths if "worker_kill_after_claim" in path.name
            )
            document = json.loads(worker.read_text(encoding="utf-8"))
            document["invariants"].append(
                {
                    "id": "PENDING_OUTBOX_NOT_LOST",
                    "status": "PASSED",
                    "evidenceArtifact": "observations.json",
                }
            )
            worker.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "invariant matrix mismatch.*PENDING_OUTBOX_NOT_LOST",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["invariants"].pop()
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "invariant matrix mismatch",
            ):
                build(paths)

    def test_rejects_tampered_or_unsafe_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            artifact = paths[0].parent / document["artifacts"][0]["path"]
            artifact.write_text("tampered", encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "size does not match|digest does not match",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["artifacts"][0]["path"] = "../outside.json"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "artifact path .* is unsafe",
            ):
                build(paths)

    def test_rejects_pitr_without_new_timeline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            pitr = next(
                path for path in paths if "pitr_new_timeline" in path.name
            )
            document = json.loads(pitr.read_text(encoding="utf-8"))
            document["observations"]["databaseTimelineAfter"] = "timeline-1"
            pitr.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "PITR_NEW_TIMELINE must record a new database timeline",
            ):
                build(paths)

    def test_rejects_restore_reconciliation_without_new_timeline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            restore = next(
                path
                for path in paths
                if "restore_durable_inventory_reconciliation" in path.name
            )
            document = json.loads(restore.read_text(encoding="utf-8"))
            document["observations"]["databaseTimelineAfter"] = "timeline-1"
            restore.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "RESTORE_DURABLE_INVENTORY_RECONCILIATION must record a new database timeline",
            ):
                build(paths)

    def test_rejects_changed_database_system_identifier(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["observations"]["databaseSystemIdentifierAfter"] = "system-2"
            paths[0].write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "recovered database system identifier does not match",
            ):
                build(paths)

    def test_rejects_hidden_timeline_change_in_non_recovery_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            worker = next(
                path for path in paths if "worker_kill_after_claim" in path.name
            )
            document = json.loads(worker.read_text(encoding="utf-8"))
            document["observations"]["databaseTimelineAfter"] = "timeline-2"
            worker.write_text(json.dumps(document), encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "WORKER_KILL_AFTER_CLAIM must not hide a database timeline change",
            ):
                build(paths)

    def test_rejects_duplicate_json_key(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            content = paths[0].read_text(encoding="utf-8")
            content = content.replace(
                '"schema":',
                '"schema": "shadow", "schema":',
                1,
            )
            paths[0].write_text(content, encoding="utf-8")

            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "duplicate key 'schema'",
            ):
                build(paths)

    def test_rejects_recovery_outside_window_or_invalid_failover(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["completedAt"] = "2026-07-31T10:00:20Z"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "recovery exceeds the recorded drill window",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            for path in paths:
                document = json.loads(path.read_text(encoding="utf-8"))
                document["environment"]["topology"] = "SINGLE_PRIMARY"
                document["environment"]["replicationMode"] = "NONE"
                path.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "PRIMARY_FAILOVER requires HA_PRIMARY_STANDBY",
            ):
                build(paths)

    def test_rejects_missing_or_premature_approval(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["approvals"].pop()
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "approval roles must be exactly",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["approvals"][0]["approvedAt"] = "2026-07-31T10:04:00Z"
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "predates drill completion",
            ):
                build(paths)

        with tempfile.TemporaryDirectory() as directory:
            paths = complete_campaign(Path(directory))
            document = json.loads(paths[0].read_text(encoding="utf-8"))
            document["approvals"][1]["principal"] = document["approvals"][0][
                "principal"
            ]
            paths[0].write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(
                ProductionEvidenceError,
                "approval roles require distinct principals",
            ):
                build(paths)


if __name__ == "__main__":
    unittest.main()
