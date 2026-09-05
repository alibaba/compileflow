"""Tests for repository hygiene checks."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.check_internal_links import (
    STALE_API_DOC_RE,
    STALE_DURABLE_RECOVERY_DOC_RE,
    extract_micrometer_metric_names,
    find_active_documentation_retired_semantics,
    find_author_only_javadocs,
    find_internal_identifier_casing_errors,
    find_java_security_release_gate_errors,
    find_java_build_output_bins,
    find_maven_project_identity_errors,
    markdown_heading_anchors,
    markdown_structure_errors,
)


class DocumentationDriftPatternTest(unittest.TestCase):
    def test_rejects_removed_storage_and_tracing_type_names(self) -> None:
        self.assertIsNotNone(STALE_API_DOC_RE.search("`FlowStorage` exposes these operations"))
        self.assertIsNotNone(
            STALE_API_DOC_RE.search("An integration may adapt the current `SpanContext`")
        )

    def test_accepts_current_storage_and_host_tracing_terms(self) -> None:
        current = "`ProcessStorage` persists drafts and may read the host application's span context."

        self.assertIsNone(STALE_API_DOC_RE.search(current))


class DurableRecoveryDocumentationTest(unittest.TestCase):
    def test_rejects_removed_version_recovery_and_monotonic_lease_claims(self) -> None:
        self.assertIsNotNone(
            STALE_DURABLE_RECOVERY_DOC_RE.search(
                "A Durable Run binds one exact Process Version."
            )
        )
        self.assertIsNotNone(
            STALE_DURABLE_RECOVERY_DOC_RE.search(
                "Monotonic claim token that rejects stale workers"
            )
        )

    def test_allows_process_id_recovery_and_random_lease_tokens(self) -> None:
        current = (
            "A Durable Run binds one exact stored Process identity. "
            "A lease-specific random token rejects stale workers."
        )

        self.assertIsNone(STALE_DURABLE_RECOVERY_DOC_RE.search(current))


class ActiveDocumentationRetiredSemanticsTest(unittest.TestCase):
    def test_rejects_retired_semantics_in_current_documents(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docs = root / "docs"
            docs.mkdir()
            (docs / "definitions.md").write_text(
                "`ProcessDefinition` supports `Inline`, `File`, and `Classpath`.\n",
                encoding="utf-8",
            )
            (docs / "execution.md").write_text(
                "`ProcessExecution` includes trace IDs, parent invocation and call depth.\n",
                encoding="utf-8",
            )
            (docs / "threat-model.md").write_text(
                "Persisted Run state contains leases, child Runs, and Outbox records.\n",
                encoding="utf-8",
            )

            errors = find_active_documentation_retired_semantics(root)

            self.assertEqual(3, len(errors))
            self.assertTrue(any("ProcessDefinition.File" in error for error in errors))
            self.assertTrue(any("ProcessExecution" in error for error in errors))
            self.assertTrue(any("Child Run" in error for error in errors))

    def test_checks_all_public_documentation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docs = root / "docs"
            architecture = docs / "architecture"
            architecture.mkdir(parents=True)
            (docs / "current.md").write_text(
                "`ProcessDefinition` supports `Inline` and `Classpath`. "
                "`ProcessExecution` contains trace and invocation IDs. "
                "`ProcessEvent.ExecutionAttribution` contains call depth.\n",
                encoding="utf-8",
            )
            (architecture / "legacy.md").write_text(
                "`ProcessDefinition` supports `Inline`, `File`, and `Classpath`.\n",
                encoding="utf-8",
            )

            errors = find_active_documentation_retired_semantics(root)
            self.assertEqual(1, len(errors))
            self.assertIn("ProcessDefinition.File", errors[0])


class JavaSecurityReleaseGateTest(unittest.TestCase):
    def test_accepts_one_reusable_scan_that_blocks_release(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workflows = root / ".github" / "workflows"
            workflows.mkdir(parents=True)
            (workflows / "java-security.yml").write_text(
                "workflow_call:\n"
                "  NVD_API_KEY:\n"
                "    required: true\n"
                "run: ./mvnw verify -Psecurity-scan\n"
                "uses: actions/upload-artifact@digest\n",
                encoding="utf-8",
            )
            caller = (
                "uses: ./.github/workflows/java-security.yml\n"
                "NVD_API_KEY: ${{ secrets.NVD_API_KEY }}\n"
            )
            (workflows / "supply-chain.yml").write_text(caller, encoding="utf-8")
            (workflows / "release.yml").write_text(
                "java-security-candidate-evidence:\n"
                + caller
                + "needs:\n  - java-security-candidate-evidence\n",
                encoding="utf-8",
            )

            self.assertEqual([], find_java_security_release_gate_errors(root))

    def test_rejects_release_that_does_not_depend_on_security_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workflows = root / ".github" / "workflows"
            workflows.mkdir(parents=True)
            (workflows / "java-security.yml").write_text(
                "workflow_call:\nNVD_API_KEY:\nrequired: true\n-Psecurity-scan\n"
                "actions/upload-artifact@digest\n",
                encoding="utf-8",
            )
            caller = (
                "uses: ./.github/workflows/java-security.yml\n"
                "NVD_API_KEY: ${{ secrets.NVD_API_KEY }}\n"
            )
            (workflows / "supply-chain.yml").write_text(caller, encoding="utf-8")
            (workflows / "release.yml").write_text(
                "java-security-candidate-evidence:\n" + caller,
                encoding="utf-8",
            )

            errors = find_java_security_release_gate_errors(root)

            self.assertEqual(1, len(errors))
            self.assertIn("blocking build dependency", errors[0])


class MicrometerMetricExtractionTest(unittest.TestCase):
    def test_extracts_literal_and_prefix_composed_metric_names(self) -> None:
        source = '''
        private static final String METRIC_PREFIX = "compileflow.engine.";
        registry.gauge(METRIC_PREFIX + "executor.threads", config, ignored -> 1);
        FunctionCounter.builder("compileflow.deploy.operations", metrics, ignored -> 1);
        '''

        self.assertEqual(
            {
                "compileflow.deploy.operations",
                "compileflow.engine.executor.threads",
            },
            extract_micrometer_metric_names(source),
        )


class JavaBuildOutputBinTest(unittest.TestCase):
    def test_detects_ide_copy_of_maven_module(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "module" / "bin"
            (output / "src" / "main" / "java").mkdir(parents=True)
            (output / "pom.xml").touch()

            self.assertEqual([Path("module/bin")], find_java_build_output_bins(root))

    def test_detects_compiled_classes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            class_file = root / "module" / "bin" / "com" / "example" / "Flow.class"
            class_file.parent.mkdir(parents=True)
            class_file.touch()

            self.assertEqual([Path("module/bin")], find_java_build_output_bins(root))

    def test_allows_regular_script_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / "tools" / "bin" / "compileflow"
            script.parent.mkdir(parents=True)
            script.touch()

            self.assertEqual([], find_java_build_output_bins(root))


class MavenProjectIdentityTest(unittest.TestCase):
    POM = """\
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <artifactId>{artifact_id}</artifactId>
</project>
"""

    def test_accepts_directory_matching_artifact_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "spring-boot-basic"
            module.mkdir()
            (module / "pom.xml").write_text(
                self.POM.format(artifact_id="spring-boot-basic"), encoding="utf-8"
            )

            self.assertEqual([], find_maven_project_identity_errors(root))

    def test_rejects_second_maven_identity_for_module_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "spring-boot-basic"
            module.mkdir()
            (module / "pom.xml").write_text(
                self.POM.format(artifact_id="compileflow-example-spring-boot-basic"),
                encoding="utf-8",
            )

            errors = find_maven_project_identity_errors(root)

            self.assertEqual(1, len(errors))
            self.assertIn("directory 'spring-boot-basic'", errors[0])
            self.assertIn("artifactId 'compileflow-example-spring-boot-basic'", errors[0])

    def test_ignores_repository_root_pom(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "pom.xml").write_text(
                self.POM.format(artifact_id="compileflow"), encoding="utf-8"
            )

            self.assertEqual([], find_maven_project_identity_errors(root))


class MarkdownHeadingAnchorTest(unittest.TestCase):
    def test_generates_unicode_and_duplicate_heading_anchors(self) -> None:
        text = "# API Design\n\n## Durable 架构\n\n## Durable 架构\n"

        self.assertEqual(
            {"api-design", "durable-架构", "durable-架构-1"},
            markdown_heading_anchors(text),
        )

    def test_ignores_headings_inside_fenced_code(self) -> None:
        text = "# Visible\n\n````markdown\n## Hidden\n```text\n## Still hidden\n```\n````\n"

        self.assertEqual({"visible"}, markdown_heading_anchors(text))


class MarkdownStructureTest(unittest.TestCase):
    def test_accepts_one_h1_and_ignores_fenced_headings(self) -> None:
        text = "# Guide\n\n```shell\n# command comment\n```\n\n## Install\n"

        self.assertEqual([], markdown_structure_errors(text))

    def test_rejects_missing_h1_heading_jump_and_unclosed_fence(self) -> None:
        text = "## Guide\n\n#### Detail\n\n```shell\ncommand\n"

        errors = markdown_structure_errors(text)

        self.assertTrue(any("jumps from H2 to H4" in error for error in errors))
        self.assertTrue(any("unclosed fenced code block" in error for error in errors))
        self.assertTrue(any("exactly one H1" in error for error in errors))

    def test_rejects_multiple_h1s_and_missing_final_newline(self) -> None:
        text = "# First\n\n# Second"

        errors = markdown_structure_errors(text)

        self.assertTrue(any("found 1, 3" in error for error in errors))
        self.assertIn("file must end with a newline", errors)


class InternalIdentifierCasingTest(unittest.TestCase):
    def test_rejects_owned_uppercase_acronyms_and_allows_protocol_constants(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java_source = root / "module" / "src" / "main" / "java" / "XMLSource.java"
            java_source.parent.mkdir(parents=True)
            java_source.write_text("final class XMLSource {}\n", encoding="utf-8")
            ts_source = root / "compileflow-workbench" / "apps" / "web" / "src" / "export.ts"
            ts_source.parent.mkdir(parents=True)
            ts_source.write_text(
                "const DEFAULT_BPMN_XML = '<xml />'\nexport function exportToPNG() {}\n",
                encoding="utf-8",
            )

            errors = find_internal_identifier_casing_errors(root)

            self.assertEqual(2, len(errors))
            self.assertTrue(any("XMLSource.java" in error for error in errors))
            self.assertTrue(any("exportToPNG" in error for error in errors))

    def test_accepts_natural_camel_case_acronyms(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java_source = root / "module" / "src" / "main" / "java" / "XmlSource.java"
            java_source.parent.mkdir(parents=True)
            java_source.write_text("final class XmlSource {}\n", encoding="utf-8")
            ts_source = root / "compileflow-workbench" / "apps" / "web" / "src" / "export.ts"
            ts_source.parent.mkdir(parents=True)
            ts_source.write_text("export function exportToPng() {}\n", encoding="utf-8")

            self.assertEqual([], find_internal_identifier_casing_errors(root))


class AuthorOnlyJavadocTest(unittest.TestCase):
    def test_rejects_author_only_javadoc(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "src" / "Example.java"
            source.parent.mkdir(parents=True)
            source.write_text(
                "/**\n * @author contributor\n */\nfinal class Example {}\n",
                encoding="utf-8",
            )

            errors = find_author_only_javadocs(root)

            self.assertEqual(1, len(errors))
            self.assertIn("src/Example.java:1", errors[0])

    def test_accepts_documented_author_tag(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "src" / "Example.java"
            source.parent.mkdir(parents=True)
            source.write_text(
                "/**\n * Represents an executable example.\n *\n * @author contributor\n */\n"
                "final class Example {}\n",
                encoding="utf-8",
            )

            self.assertEqual([], find_author_only_javadocs(root))


if __name__ == "__main__":
    unittest.main()
