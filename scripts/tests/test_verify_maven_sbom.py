"""Tests for the aggregate Maven CycloneDX BOM verifier."""

from __future__ import annotations

import unittest

from scripts.verify_maven_sbom import (
    DURABLE_AGGREGATOR,
    DURABLE_ARTIFACTS,
    GROUP,
    SbomVerificationError,
    maven_ref,
    parse_bom,
    verify_bom,
)


VERSION = "2.0.0"


def valid_bom() -> dict:
    """Create the smallest valid project BOM graph."""
    root_ref = maven_ref("compileflow", VERSION, "pom")
    aggregator_ref = maven_ref(DURABLE_AGGREGATOR, VERSION, "pom")
    module_refs = [
        maven_ref(artifact, VERSION, "jar") for artifact in DURABLE_ARTIFACTS
    ]

    def component(artifact: str, packaging: str) -> dict:
        reference = maven_ref(artifact, VERSION, packaging)
        return {
            "type": "library",
            "bom-ref": reference,
            "group": GROUP,
            "name": artifact,
            "version": VERSION,
            "purl": reference,
        }

    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "metadata": {
            "component": component("compileflow", "pom"),
        },
        "components": [
            component(DURABLE_AGGREGATOR, "pom"),
            *(component(artifact, "jar") for artifact in DURABLE_ARTIFACTS),
        ],
        "dependencies": [
            {"ref": root_ref, "dependsOn": [aggregator_ref]},
            {"ref": aggregator_ref, "dependsOn": module_refs},
            *({"ref": reference, "dependsOn": []} for reference in module_refs),
        ],
    }


class VerifyMavenSbomTest(unittest.TestCase):
    """Reject superficially valid BOMs that omit the Durable surface."""

    def test_accepts_exact_seven_module_graph(self) -> None:
        self.assertEqual(VERSION, verify_bom(valid_bom(), VERSION))

    def test_rejects_missing_durable_component(self) -> None:
        bom = valid_bom()
        bom["components"].pop()
        with self.assertRaisesRegex(SbomVerificationError, "misses Durable component"):
            verify_bom(bom)

    def test_rejects_weakened_aggregator_edges(self) -> None:
        bom = valid_bom()
        bom["dependencies"][1]["dependsOn"].pop()
        with self.assertRaisesRegex(SbomVerificationError, "exactly the seven"):
            verify_bom(bom)

    def test_rejects_wrong_release_version(self) -> None:
        with self.assertRaisesRegex(SbomVerificationError, "does not match"):
            verify_bom(valid_bom(), "2.0.1")

    def test_rejects_duplicate_json_keys(self) -> None:
        with self.assertRaisesRegex(SbomVerificationError, "duplicate JSON key"):
            parse_bom('{"bomFormat":"CycloneDX","bomFormat":"other"}')


if __name__ == "__main__":
    unittest.main()
