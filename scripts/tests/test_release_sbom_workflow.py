"""Keep application and published-library SBOM release paths independent."""

import shlex
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def step_body(workflow: str, name: str) -> str:
    step = workflow.split(f"- name: {name}\n", 1)[1].split("\n      - name:", 1)[0]
    return textwrap.dedent(step.split("        run: |\n", 1)[1])


class ReleaseSbomWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = (ROOT / ".github/workflows/release-build.yml").read_text(encoding="utf-8")

    def test_application_metadata_precedes_aggregate_regeneration(self) -> None:
        application = self.workflow.index("- name: Generate and verify Workbench Server SBOM\n")
        aggregate = self.workflow.index("- name: Generate and verify aggregate SBOM\n")
        self.assertLess(application, aggregate)
        body = step_body(self.workflow, "Generate and verify Workbench Server SBOM").replace("\\\n", " ")
        commands = [shlex.split(line) for line in body.splitlines() if line.strip().startswith("./mvnw ")]
        self.assertEqual(2, len(commands))
        self.assertIn("org.cyclonedx:cyclonedx-maven-plugin:makeBom@workbench-application", commands[0])
        self.assertIn("-am", commands[0])
        self.assertIn("dependency:copy@workbench-sbom-loader", commands[1])
        for command in commands:
            self.assertIn("-Pworkbench-sbom", command)
            self.assertEqual("compileflow-workbench-server", command[command.index("-pl") + 1])
            self.assertNotIn("package", command)
            self.assertNotIn("install", command)
            self.assertNotIn("-Pworkbench-bundled", command)
        packaging = step_body(self.workflow, "Build bundled Workbench distribution")
        self.assertNotIn("workbench-sbom", packaging)
        self.assertNotIn("skipNotDeployed=false", step_body(self.workflow, "Generate and verify aggregate SBOM"))

    def test_release_installs_current_tested_libraries_before_metadata_resolution(self) -> None:
        name = "Build and verify release artifacts"
        body = step_body(self.workflow, name).replace("\\\n", " ")
        commands = [shlex.split(line) for line in body.splitlines() if line.strip().startswith("./mvnw ")]
        self.assertEqual(2, len(commands))
        command = commands[0]
        self.assertIn("checkstyle:check", commands[1])
        self.assertEqual(["./mvnw", "clean", "install"], command[:3])
        self.assertEqual("$release_modules", command[command.index("-pl") + 1])
        self.assertIn("-am", command)
        self.assertIn("-Prelease-artifacts", command)
        self.assertNotIn("-Pworkbench-sbom", command)
        self.assertFalse(any("skipTests" in token or "maven.test.skip" in token or token == "deploy" for token in command))
        self.assertLess(self.workflow.index(f"- name: {name}\n"),
                        self.workflow.index("- name: Generate and verify Workbench Server SBOM\n"))

    def test_supply_chain_installs_default_reactor_before_aggregate(self) -> None:
        workflow = (ROOT / ".github/workflows/supply-chain.yml").read_text(encoding="utf-8")
        body = step_body(workflow, "Generate Maven aggregate SBOM").replace("\\\n", " ")
        commands = [shlex.split(line) for line in body.splitlines() if line.strip().startswith("./mvnw ")]
        self.assertEqual(2, len(commands), "Inventory generation must first install the current reactor locally")
        self.assertEqual(["./mvnw", "install", "-DskipTests", "-B", "-V", "--no-transfer-progress"], commands[0])
        self.assertIn("org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom", commands[1])
        self.assertIn("-Dcyclonedx.skipAttach=true", commands[1])
        self.assertFalse(any(token.startswith("-P") or token in ("-pl", "deploy")
                             or "skipNotDeployed" in token for command in commands for token in command))

    def test_all_three_boms_reach_checksum_and_attestation_subjects(self) -> None:
        collection = step_body(self.workflow, "Collect release artifacts")
        for path in ("target/compileflow-bom.json", "target/compileflow-workbench-web-bom.json",
                     "compileflow-workbench-server/target/compileflow-workbench-server-bom.json"):
            self.assertIn(f"cp {path} staging/", collection)
        self.assertIn("*-bom.json", step_body(self.workflow, "Compute artifact hashes"))
        self.assertIn("subject-checksums: staging/SHA256SUMS", self.workflow)
        self.assertIn("staging/*-bom.json", self.workflow)

    def test_application_verifier_binds_bom_bundle_loader_and_release_version(self) -> None:
        body = step_body(self.workflow, "Generate and verify Workbench Server SBOM").replace("\\\n", " ")
        commands = [shlex.split(line) for line in body.splitlines() if line.strip().startswith("python3 ")]
        self.assertEqual(2, len(commands), "Both distributed Workbench JARs require application verification")
        for command, artifact in zip(commands, ("compileflow-workbench-all-in-one", "compileflow-workbench-server")):
            self.assertEqual("scripts/verify_workbench_sbom.py", command[1])
            self.assertEqual("compileflow-workbench-server/target/compileflow-workbench-server-bom.json", command[2])
            self.assertEqual(f"compileflow-workbench-server/target/{artifact}-${{GITHUB_REF_NAME#v}}.jar", command[3])
            self.assertEqual("compileflow-workbench-server/target/sbom/spring-boot-loader.jar",
                             command[command.index("--loader-jar") + 1])
            self.assertEqual("${GITHUB_REF_NAME#v}", command[command.index("--expected-version") + 1])


if __name__ == "__main__":
    unittest.main()
