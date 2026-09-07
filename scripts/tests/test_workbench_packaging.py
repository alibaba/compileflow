"""The shared output tree must not leak bundled assets into standalone builds."""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


class WorkbenchPackagingTest(unittest.TestCase):
    def test_web_asset_reset_precedes_resource_copy_in_every_profile(self) -> None:
        root = Path(__file__).resolve().parents[2]
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        project = ET.parse(root / "compileflow-workbench-server/pom.xml").getroot()
        resets = project.findall(
            "m:build/m:plugins/m:plugin[m:artifactId='maven-clean-plugin']/m:executions/m:execution", ns
        )
        self.assertEqual(1, len(resets), "Every profile must reset the owned generated web asset tree")
        reset = resets[0]
        self.assertEqual("generate-resources", reset.findtext("m:phase", namespaces=ns))
        self.assertEqual("true", reset.findtext("m:configuration/m:excludeDefaultDirectories", namespaces=ns))
        directories = reset.findall("m:configuration/m:filesets/m:fileset/m:directory", ns)
        self.assertEqual(["${project.build.outputDirectory}/static"], [entry.text for entry in directories])
        bundle = project.find("m:profiles/m:profile[m:id='workbench-bundled']", ns)
        copies = bundle.findall(
            "m:build/m:plugins/m:plugin[m:artifactId='maven-resources-plugin']/m:executions/m:execution", ns
        )
        self.assertEqual(1, len(copies))
        self.assertEqual("process-resources", copies[0].findtext("m:phase", namespaces=ns))


if __name__ == "__main__":
    unittest.main()
