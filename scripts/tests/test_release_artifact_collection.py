"""Execute the release workflow's actual collector without Maven or Java."""

import os
import re
import subprocess
import tempfile
import textwrap
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VERSION = "2.0.0"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class ReleaseArtifactCollectionTest(unittest.TestCase):
    def setUp(self) -> None:
        workflow = (ROOT / ".github/workflows/release-build.yml").read_text(encoding="utf-8")
        step = workflow.split("- name: Collect release artifacts\n", 1)[1]
        body = textwrap.dedent(step.split("        run: |\n", 1)[1].split("\n      - name:", 1)[0])
        # Run collection and set checks, stopping before unrelated Java/license checks.
        self.collector = body.split("for artifact in staging/compileflow-*.jar; do", 1)[0]
        declaration = re.search(r"expected_artifacts=\((.*?)\n\)", self.collector, re.DOTALL)
        self.assertIsNotNone(declaration)
        self.artifacts = declaration.group(1).split()
        self.assertEqual(len(self.artifacts), len(set(self.artifacts)))
        temporary = tempfile.TemporaryDirectory(prefix="compileflow-release-collector-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for artifact in self.artifacts:
            for classifier in ("", "-sources", "-javadoc"):
                self.write_jar(self.artifact_path(artifact, classifier), artifact)
        self.bundled = self.root / "compileflow-workbench-server/target" / f"compileflow-workbench-all-in-one-{VERSION}.jar"
        self.write_jar(self.bundled, "compileflow-workbench-server")
        self.expected = {f"{artifact}-{VERSION}{classifier}.jar"
                         for artifact in self.artifacts for classifier in ("", "-sources", "-javadoc")}
        self.expected.add(self.bundled.name)

    def artifact_path(self, artifact: str, classifier: str = "", version: str = VERSION) -> Path:
        parent = "compileflow-durable" if artifact.startswith("compileflow-durable-") else (
            "compileflow-deploy" if artifact.startswith("compileflow-deploy-") else "")
        return self.root / parent / artifact / "target" / f"{artifact}-{version}{classifier}.jar"

    def write_jar(self, path: Path, artifact: str, version: str = VERSION) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\nImplementation-Version: {version}\n")
            archive.writestr(f"META-INF/maven/com.alibaba.compileflow/{artifact}/pom.properties",
                             f"groupId=com.alibaba.compileflow\nartifactId={artifact}\nversion={version}\n")

    def run_collector(self) -> subprocess.CompletedProcess:
        return subprocess.run(["bash", "-c", self.collector], cwd=self.root,
                              env={**os.environ, "RELEASE_TAG": "v" + VERSION},
                              text=True, capture_output=True, timeout=30)

    def assert_exact_collection(self) -> None:
        result = self.run_collector()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(self.expected, {path.name for path in (self.root / "staging").iterdir()})

    def test_collects_exact_release_set_including_published_testkits(self) -> None:
        self.assertIn("compileflow-deploy-testkit", self.artifacts)
        self.assertIn("compileflow-durable-testkit", self.artifacts)
        self.assert_exact_collection()

    def test_ignores_real_integration_module_shape_without_javadoc(self) -> None:
        pom = ET.parse(ROOT / "compileflow-integration-tests/pom.xml").getroot()
        artifact = pom.findtext("m:artifactId", namespaces=NS)
        self.assertEqual("true", pom.findtext("m:properties/m:maven.deploy.skip", namespaces=NS))
        for classifier in ("", "-sources"):
            self.write_jar(self.artifact_path(artifact, classifier), artifact)
        self.assert_exact_collection()

    def test_ignores_old_versions_and_snapshot_residue(self) -> None:
        for version in ("1.9.0", "2.0.0-SNAPSHOT"):
            for classifier in ("", "-sources", "-javadoc"):
                self.write_jar(self.artifact_path("compileflow-api", classifier, version), "compileflow-api", version)
        self.assert_exact_collection()

    def test_ignores_nonrelease_module_and_extra_classifier(self) -> None:
        self.write_jar(self.artifact_path("compileflow-unpublished"), "compileflow-unpublished")
        self.write_jar(self.artifact_path("compileflow-api", "-tests"), "compileflow-api")
        self.assert_exact_collection()

    def test_does_not_collect_same_basename_from_unrelated_target(self) -> None:
        self.write_jar(self.root / "unrelated/target" / f"compileflow-api-{VERSION}.jar", "compileflow-api")
        self.assert_exact_collection()

    def test_rejects_missing_classifier(self) -> None:
        for classifier in ("-sources", "-javadoc"):
            with self.subTest(classifier=classifier):
                path = self.artifact_path("compileflow-api", classifier)
                path.unlink()
                result = self.run_collector()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Missing expected release artifact", result.stdout + result.stderr)
                self.write_jar(path, "compileflow-api")
                for staged in (self.root / "staging").iterdir():
                    staged.unlink()

    def test_rejects_duplicate_matching_module_outputs(self) -> None:
        duplicate = self.root / "duplicate/compileflow-api/target" / f"compileflow-api-{VERSION}.jar"
        self.write_jar(duplicate, "compileflow-api")
        result = self.run_collector()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Duplicate release artifact", result.stdout + result.stderr)

    def test_rejects_missing_bundled_application(self) -> None:
        self.bundled.unlink()
        result = self.run_collector()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing bundled Workbench artifact", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
