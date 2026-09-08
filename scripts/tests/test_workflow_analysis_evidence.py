"""Bind CI analysis evidence to the actual selected Maven dependency closure."""

import shlex
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def reactor_projects(directory: Path) -> dict[str, Path]:
    project = ET.parse(directory / "pom.xml").getroot()
    projects = {project.findtext("m:artifactId", namespaces=NS): directory}
    for module in project.findall("m:modules/m:module", NS):
        projects.update(reactor_projects(directory / module.text.strip()))
    return projects


class WorkflowAnalysisEvidenceTest(unittest.TestCase):
    def test_core_spotbugs_selects_every_required_report_module(self) -> None:
        workflow = (ROOT / ".github/workflows/java-core-ci.yml").read_text(encoding="utf-8")
        step = workflow.split("- name: Run core SpotBugs\n", 1)[1].split("\n      - ", 1)[0]
        commands = step.replace("\\\n", " ")
        analysis = shlex.split(next(line.strip() for line in commands.splitlines() if "./mvnw " in line))
        verifier = shlex.split(next(
            line.strip() for line in commands.splitlines() if "scripts/verify_spotbugs_reports.py " in line
        ))
        self.assertIn("spotbugs:check", analysis)
        self.assertIn("-am", analysis)
        projects = reactor_projects(ROOT)
        pending = [(ROOT / module).resolve() for module in analysis[analysis.index("-pl") + 1].split(",")]
        analyzed = set()
        while pending:
            directory = pending.pop()
            if directory in analyzed:
                continue
            analyzed.add(directory)
            project = ET.parse(directory / "pom.xml").getroot()
            for dependency in project.findall("m:dependencies/m:dependency", NS):
                if dependency.findtext("m:groupId", namespaces=NS) == "com.alibaba.compileflow":
                    artifact = dependency.findtext("m:artifactId", namespaces=NS)
                    pending.append(projects[artifact].resolve())
        required = {(ROOT / module).resolve() for module in verifier[2:]}
        missing = sorted(path.relative_to(ROOT).as_posix() for path in required - analyzed)
        self.assertEqual([], missing, "SpotBugs report modules outside the selected reactor")


if __name__ == "__main__":
    unittest.main()
