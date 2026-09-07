"""Tests for the aggregate Maven CycloneDX BOM verifier."""

from __future__ import annotations

import unittest

from scripts.verify_maven_sbom import (
    COMPOSITION_EDGES,
    DEPLOY_AGGREGATOR,
    DEPLOY_ARTIFACTS,
    DURABLE_AGGREGATOR,
    DURABLE_ARTIFACTS,
    ENGINE_ARTIFACTS,
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
    durable_aggregator_ref = maven_ref(DURABLE_AGGREGATOR, VERSION, "pom")
    deploy_aggregator_ref = maven_ref(DEPLOY_AGGREGATOR, VERSION, "pom")
    durable_module_refs = [maven_ref(artifact, VERSION, "jar") for artifact in DURABLE_ARTIFACTS]
    deploy_module_refs = [maven_ref(artifact, VERSION, "jar") for artifact in DEPLOY_ARTIFACTS]
    engine_module_refs = [maven_ref(artifact, VERSION, "jar") for artifact in ENGINE_ARTIFACTS]
    module_refs = durable_module_refs + deploy_module_refs + engine_module_refs
    composition_edges = {
        maven_ref(artifact, VERSION, "jar"): [
            maven_ref(dependency, VERSION, "jar") for dependency in dependencies
        ]
        for artifact, dependencies in COMPOSITION_EDGES.items()
    }

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
            component(DEPLOY_AGGREGATOR, "pom"),
            *(component(artifact, "jar") for artifact in ENGINE_ARTIFACTS),
            *(component(artifact, "jar") for artifact in DURABLE_ARTIFACTS),
            *(component(artifact, "jar") for artifact in DEPLOY_ARTIFACTS),
        ],
        "dependencies": [
            {
                "ref": root_ref,
                "dependsOn": [
                    durable_aggregator_ref,
                    deploy_aggregator_ref,
                    *engine_module_refs,
                ],
            },
            {"ref": durable_aggregator_ref, "dependsOn": durable_module_refs},
            {"ref": deploy_aggregator_ref, "dependsOn": deploy_module_refs},
            *(
                {"ref": reference, "dependsOn": composition_edges.get(reference, [])}
                for reference in module_refs
            ),
        ],
    }


def set_dependencies(bom: dict, artifact: str, dependencies: tuple[str, ...]) -> None:
    """Set direct dependencies for one CompileFlow artifact in a test BOM."""
    reference = maven_ref(artifact, VERSION, "jar")
    entry = next(item for item in bom["dependencies"] if item["ref"] == reference)
    entry["dependsOn"] = [maven_ref(dependency, VERSION, "jar") for dependency in dependencies]


class VerifyMavenSbomTest(unittest.TestCase):
    """Reject superficially valid BOMs that omit or miscompose product surfaces."""

    def test_accepts_exact_product_graphs(self) -> None:
        self.assertEqual(VERSION, verify_bom(valid_bom(), VERSION))

    def test_rejects_missing_engine_starter(self) -> None:
        bom = valid_bom()
        missing = maven_ref("compileflow-spring-boot-starter-bpmn", VERSION, "jar")
        bom["components"] = [component for component in bom["components"] if component["bom-ref"] != missing]
        with self.assertRaisesRegex(SbomVerificationError, "misses engine component"):
            verify_bom(bom)

    def test_rejects_frontend_leak_from_neutral_starter(self) -> None:
        bom = valid_bom()
        set_dependencies(
            bom,
            "compileflow-spring-boot-starter",
            ("compileflow-spring-boot-autoconfigure", "compileflow-tbbpm"),
        )
        with self.assertRaisesRegex(SbomVerificationError, "expected CompileFlow composition edges"):
            verify_bom(bom)

    def test_rejects_frontend_leak_from_durable_starter(self) -> None:
        bom = valid_bom()
        set_dependencies(
            bom,
            "compileflow-durable-spring-boot-starter",
            ("compileflow-durable-spring-boot-autoconfigure", "compileflow-bpmn"),
        )
        with self.assertRaisesRegex(SbomVerificationError, "expected CompileFlow composition edges"):
            verify_bom(bom)

    def test_rejects_missing_durable_component(self) -> None:
        bom = valid_bom()
        missing = maven_ref(DURABLE_ARTIFACTS[-1], VERSION, "jar")
        bom["components"] = [component for component in bom["components"] if component["bom-ref"] != missing]
        with self.assertRaisesRegex(SbomVerificationError, "misses Durable component"):
            verify_bom(bom)

    def test_rejects_weakened_aggregator_edges(self) -> None:
        bom = valid_bom()
        bom["dependencies"][1]["dependsOn"].pop()
        with self.assertRaisesRegex(SbomVerificationError, "Durable aggregator must depend on exactly"):
            verify_bom(bom)

    def test_rejects_missing_deploy_component(self) -> None:
        bom = valid_bom()
        missing = maven_ref(DEPLOY_ARTIFACTS[-1], VERSION, "jar")
        bom["components"] = [component for component in bom["components"] if component["bom-ref"] != missing]
        with self.assertRaisesRegex(SbomVerificationError, "misses Deploy component"):
            verify_bom(bom)

    def test_rejects_weakened_deploy_aggregator_edges(self) -> None:
        bom = valid_bom()
        bom["dependencies"][2]["dependsOn"].pop()
        with self.assertRaisesRegex(SbomVerificationError, "Deploy aggregator must depend on exactly"):
            verify_bom(bom)

    def test_rejects_wrong_release_version(self) -> None:
        with self.assertRaisesRegex(SbomVerificationError, "does not match"):
            verify_bom(valid_bom(), "2.0.1")

    def test_rejects_duplicate_json_keys(self) -> None:
        with self.assertRaisesRegex(SbomVerificationError, "duplicate JSON key"):
            parse_bom('{"bomFormat":"CycloneDX","bomFormat":"other"}')


if __name__ == "__main__":
    unittest.main()
