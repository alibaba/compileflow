"""Version alignment includes parentless consumer POMs and explicit children."""

import json
import os
import shlex
import tempfile
import unittest
from pathlib import Path

from scripts import check_project_versions as versions


class CheckProjectVersionsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        previous = Path.cwd()
        os.chdir(self.temporary.name)
        self.addCleanup(os.chdir, previous)
        self.write_pom("pom.xml", "2.0.0")
        self.write_pom("compileflow-bom/pom.xml", "2.0.0", property_version="2.0.0")
        for path in versions.WORKBENCH_PACKAGES:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps({"version": "2.0.0"}), encoding="utf-8")

    @staticmethod
    def write_pom(path: str, version: str, *, property_version: str | None = None,
                  parent: bool = False) -> None:
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        parent_xml = (
            "<parent><groupId>com.alibaba.compileflow</groupId>"
            "<artifactId>compileflow</artifactId><version>2.0.0</version></parent>"
            if parent else ""
        )
        properties = (
            f"<properties><compileflow.version>{property_version}</compileflow.version></properties>"
            if property_version is not None else ""
        )
        target.write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            f"{parent_xml}<groupId>com.alibaba.compileflow</groupId>"
            f"<artifactId>{target.parent.name}</artifactId><version>{version}</version>"
            f"{properties}</project>", encoding="utf-8",
        )

    def test_accepts_aligned_parentless_bom(self) -> None:
        versions.validate_project_versions("2.0.0", release=True)

    def test_rejects_stale_parentless_bom_version(self) -> None:
        self.write_pom("compileflow-bom/pom.xml", "1.9.0", property_version="2.0.0")
        with self.assertRaisesRegex(ValueError, "compileflow-bom/pom.xml"):
            versions.validate_project_versions("2.0.0", release=True)

    def test_rejects_stale_bom_dependency_alignment(self) -> None:
        self.write_pom("compileflow-bom/pom.xml", "2.0.0", property_version="1.9.0")
        with self.assertRaisesRegex(ValueError, "compileflow.version"):
            versions.validate_project_versions("2.0.0", release=True)

    def test_rejects_child_version_overriding_aligned_parent(self) -> None:
        self.write_pom("child/pom.xml", "1.9.0", parent=True)
        with self.assertRaisesRegex(ValueError, "child/pom.xml"):
            versions.validate_project_versions("2.0.0", release=True)


class BumpVersionTest(unittest.TestCase):
    def test_updates_parentless_bom_identity_and_dependency_alignment(self) -> None:
        helper = Path(__file__).resolve().parents[1] / "bump_version"
        commands = [
            shlex.split(line)
            for line in helper.read_text(encoding="utf-8").replace("\\\n", " ").splitlines()
            if line.startswith("./mvnw ")
        ]
        bom_commands = [
            command for command in commands
            if "-f" in command
            and command[command.index("-f") + 1] == "compileflow-bom/pom.xml"
        ]
        for goal in ("versions:set", "versions:set-property"):
            with self.subTest(goal=goal):
                self.assertTrue(any(
                    goal in command and "-DnewVersion=$version" in command
                    and (goal != "versions:set-property"
                         or "-Dproperty=compileflow.version" in command)
                    for command in bom_commands
                ))


if __name__ == "__main__":
    unittest.main()
