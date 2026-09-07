#!/usr/bin/env python3
"""Tests for architecture checks that do not need a repository fixture."""

from __future__ import annotations

import unittest
from contextlib import ExitStack
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from scripts.check_architecture_boundaries import (
    ROOT,
    SKIP_DIRECTORIES,
    check_spring_starter_boundaries,
    check_workbench_web_dependency_directions,
    check_runtime_realization_boundary,
    check_definition_and_lifecycle_ownership,
    check_routing_key_persistence_boundary,
    check_deploy_migration_owner,
    DEPLOY_MIGRATION_OWNERS,
    find_case_mismatched_paths,
    iter_repository_text_files,
    has_internal_package_segment,
    public_top_level_type_name,
)


def write_pom(path: Path, artifact_ids: tuple[str, ...]) -> None:
    """Write the minimum namespaced Maven dependency fixture."""
    dependencies = "".join(
        "<dependency><groupId>com.alibaba.compileflow</groupId>"
        f"<artifactId>{artifact_id}</artifactId></dependency>"
        for artifact_id in artifact_ids
    )
    path.parent.mkdir(parents=True)
    path.write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        f"<dependencies>{dependencies}</dependencies></project>",
        encoding="utf-8",
    )


class DeployMigrationOwnerTest(unittest.TestCase):

    def test_accepts_complete_null_safe_provider_constraints(self) -> None:
        for owner in DEPLOY_MIGRATION_OWNERS:
            migration = next(owner.rglob("V1__*.sql"))
            source = migration.read_text(encoding="utf-8")
            for candidate in (source, source.replace(" AND ", "\n  AND ")):
                with self.subTest(provider=str(owner)), patch(
                    "scripts.check_architecture_boundaries.production_migrations", return_value=[migration]
                ), patch(
                    "scripts.check_architecture_boundaries.DEPLOY_MIGRATION_OWNERS", (owner,)
                ), patch("scripts.check_architecture_boundaries.read_text", return_value=candidate):
                    self.assertEqual([], check_deploy_migration_owner())

    def test_rejects_missing_transition_predicates(self) -> None:
        for owner in DEPLOY_MIGRATION_OWNERS:
            migration = next(owner.rglob("V1__*.sql"))
            source = migration.read_text(encoding="utf-8")
            for predicate in ("sequence > 1", "from_phase IS NOT NULL", "from_phase = 'IN_PROGRESS'"):
                self.assertIn(predicate, source)
                with self.subTest(provider=str(owner), predicate=predicate), patch(
                    "scripts.check_architecture_boundaries.production_migrations", return_value=[migration]
                ), patch(
                    "scripts.check_architecture_boundaries.DEPLOY_MIGRATION_OWNERS", (owner,)
                ), patch(
                    "scripts.check_architecture_boundaries.read_text", return_value=source.replace(predicate, "TRUE")
                ):
                    self.assertTrue(check_deploy_migration_owner())


class RoutingPersistenceBoundaryTest(unittest.TestCase):

    def test_requires_admission_before_insert(self) -> None:
        validation = "requestedRouting(processCode, request.routing());"
        admission = "pinAliasRouting(processCode);"
        persistence = "store.insert(invocation);"
        for statements, accepted in (
            ((validation, admission, persistence), True),
            ((persistence, validation, admission), False),
            ((validation, persistence, admission), False),
        ):
            source = "\n".join(statements) + "\nrouting.validatePersistedInvocation(); new AliasRoutingOptions();"

            def read_source(path: Path) -> str:
                if path.name == "AsyncInvocationService.java":
                    return source
                if path.name == "ExecutionRoutingRequest.java":
                    return "Map<String, String> attributes; new AliasRoutingOptions(routingKey, attributes); validatePersistedInvocation()"
                return ""

            with self.subTest(statements=statements), patch(
                "scripts.check_architecture_boundaries.read_text", side_effect=read_source
            ):
                errors = check_routing_key_persistence_boundary()

            self.assertEqual(0 if accepted else 1, len(errors), errors)


class SpringStarterBoundaryTest(unittest.TestCase):

    def test_accepts_neutral_base_and_single_frontend_compositions(self) -> None:
        with TemporaryDirectory() as directory, ExitStack() as patches:
            root = Path(directory)
            base = root / "base"
            tbbpm = root / "tbbpm"
            bpmn = root / "bpmn"
            write_pom(base / "pom.xml", ("compileflow-spring-boot-autoconfigure",))
            write_pom(tbbpm / "pom.xml", ("compileflow-spring-boot-starter", "compileflow-tbbpm"))
            write_pom(bpmn / "pom.xml", ("compileflow-spring-boot-starter", "compileflow-bpmn"))
            patches.enter_context(patch("scripts.check_architecture_boundaries.ROOT", root))
            for name, value in (("ENGINE_STARTER", base), ("TBBPM_STARTER", tbbpm), ("BPMN_STARTER", bpmn)):
                patches.enter_context(patch(f"scripts.check_architecture_boundaries.{name}", value))

            self.assertEqual([], check_spring_starter_boundaries())

    def test_rejects_implicit_frontend_in_base_starter(self) -> None:
        with TemporaryDirectory() as directory, ExitStack() as patches:
            root = Path(directory)
            base = root / "base"
            tbbpm = root / "tbbpm"
            bpmn = root / "bpmn"
            write_pom(
                base / "pom.xml",
                ("compileflow-spring-boot-autoconfigure", "compileflow-tbbpm"),
            )
            write_pom(tbbpm / "pom.xml", ("compileflow-spring-boot-starter", "compileflow-tbbpm"))
            write_pom(bpmn / "pom.xml", ("compileflow-spring-boot-starter", "compileflow-bpmn"))
            patches.enter_context(patch("scripts.check_architecture_boundaries.ROOT", root))
            for name, value in (("ENGINE_STARTER", base), ("TBBPM_STARTER", tbbpm), ("BPMN_STARTER", bpmn)):
                patches.enter_context(patch(f"scripts.check_architecture_boundaries.{name}", value))

            errors = check_spring_starter_boundaries()

        self.assertEqual(1, len(errors))
        self.assertIn("format-neutral", errors[0])


class RuntimeRealizationBoundaryTest(unittest.TestCase):

    def test_accepts_semantic_compilation_value(self) -> None:
        with patch("scripts.check_architecture_boundaries.read_text", return_value=(
            "import com.alibaba.compileflow.engine.core.semantic."
            "ProcessSemanticCompiler.ProcessSemanticCompilation;"
        )):
            self.assertEqual([], check_runtime_realization_boundary())

    def test_rejects_frontend_and_source_dependencies(self) -> None:
        for source in (
            "import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;",
            "import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;",
            "private final ProcessSemanticCompiler<?> frontend;",
        ):
            with self.subTest(source=source), patch(
                "scripts.check_architecture_boundaries.read_text", return_value=source
            ):
                self.assertEqual(3, len(check_runtime_realization_boundary()))


class DefinitionAndLifecycleOwnershipTest(unittest.TestCase):

    def test_scans_repository_nested_under_an_external_target_directory(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory) / "target" / "checkout"
            for relative in ("core/src/main/java/Example.java", "core/target/src/main/java/Generated.java"):
                path = root / relative
                path.parent.mkdir(parents=True)
                path.write_text("Map<ProcessModelType, ProcessEngine> engines;", encoding="utf-8")
            with patch("scripts.check_architecture_boundaries.ROOT", root):
                errors = check_definition_and_lifecycle_ownership()
            self.assertEqual(1, len(errors), errors)
            self.assertIn("core/src/main/java/Example.java", errors[0])

    def test_rejects_each_removed_ownership_pattern(self) -> None:
        cases = (
            ("core/src/main/java/Example.java", "Map<ProcessModelType, ProcessEngine> engines;"),
            ("api/src/main/java/ProcessEngineConfig.java", "ProcessModelType modelType;"),
            ("compileflow-durable-runtime/src/main/java/Example.java", "ProcessEngineConfig config;"),
            ("compileflow-durable-runtime/src/main/java/Example.java", "import org.springframework.Bean;"),
            ("spring/src/main/java/durable/autoconfigure/Example.java", "new DurableTurnWorker(store);"),
        )
        for name, source in cases:
            with self.subTest(name=name, source=source), TemporaryDirectory() as directory:
                root = Path(directory)
                path = root / name
                path.parent.mkdir(parents=True)
                path.write_text(source, encoding="utf-8")
                with patch("scripts.check_architecture_boundaries.ROOT", root):
                    self.assertEqual(1, len(check_definition_and_lifecycle_ownership()))

    def test_accepts_typed_definition_and_independent_runtime_config(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "compileflow-durable-runtime/src/main/java/Example.java"
            path.parent.mkdir(parents=True)
            path.write_text("DurableProcessEngineConfig config; ProcessDefinition definition;", encoding="utf-8")
            with patch("scripts.check_architecture_boundaries.ROOT", root):
                self.assertEqual([], check_definition_and_lifecycle_ownership())

    def test_rejects_frontend_engine_bootstrap_descriptor(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            descriptor = root / ("compileflow-bpmn/src/main/resources/META-INF/services/"
                                 "com.alibaba.compileflow.engine.spi.ProcessEngineProvider")
            descriptor.parent.mkdir(parents=True)
            descriptor.write_text("example.Provider", encoding="utf-8")
            with patch("scripts.check_architecture_boundaries.ROOT", root):
                self.assertEqual(1, len(check_definition_and_lifecycle_ownership()))


class RepositoryTextScanTest(unittest.TestCase):

    def test_includes_authored_process_definitions_but_not_build_outputs(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("flow.bpm", "flow.bpmn", "target/generated.bpmn"):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("<process/>", encoding="utf-8")
            iter_repository_text_files.cache_clear()
            try:
                with patch("scripts.check_architecture_boundaries.ROOT", root):
                    names = {path.relative_to(root).as_posix() for path in iter_repository_text_files()}
                self.assertEqual({"flow.bpm", "flow.bpmn"}, names)
            finally:
                iter_repository_text_files.cache_clear()

    def test_excludes_generated_frontend_directories(self) -> None:
        self.assertTrue(
            {".next", ".turbo", "playwright-report", "test-results"}.issubset(
                SKIP_DIRECTORIES
            )
        )


class JavaPublicTypeNameTest(unittest.TestCase):

    def test_reads_supported_top_level_type_declarations(self) -> None:
        declarations = (
            ("public final class Example {}", "Example"),
            ("public sealed interface Operation {}", "Operation"),
            ("public record Result(String value) {}", "Result"),
            ("public @interface Supported {}", "Supported"),
        )

        for source, expected in declarations:
            with self.subTest(source=source):
                self.assertEqual(expected, public_top_level_type_name(source))

    def test_ignores_package_private_and_nested_public_types(self) -> None:
        source = """final class Envelope {
    public record Value(String text) {}
}
"""

        self.assertIsNone(public_top_level_type_name(source))


class JavaSourcePathCaseTest(unittest.TestCase):

    def test_reports_case_only_path_mismatch(self) -> None:
        mismatches = find_case_mismatched_paths(
            ("module/src/main/java/example/WorkFlowRouter.java",),
            ("module/src/main/java/example/WorkflowRouter.java",),
        )

        self.assertEqual(
            (
                (
                    "module/src/main/java/example/WorkFlowRouter.java",
                    "module/src/main/java/example/WorkflowRouter.java",
                ),
            ),
            mismatches,
        )

    def test_accepts_exact_path_case(self) -> None:
        path = "module/src/main/java/example/WorkflowRouter.java"

        self.assertEqual((), find_case_mismatched_paths((path,), (path,)))

    def test_ignores_untracked_and_deleted_paths(self) -> None:
        self.assertEqual(
            (),
            find_case_mismatched_paths(
                ("module/src/main/java/example/Deleted.java",),
                ("module/src/main/java/example/Generated.java",),
            ),
        )


class JavaPackageResponsibilityTest(unittest.TestCase):

    def test_rejects_generic_internal_path_or_package_segment(self) -> None:
        self.assertTrue(
            has_internal_package_segment(
                Path("module/src/main/java/example", "in" + "ternal", "Worker.java"),
                "example.runtime",
            )
        )
        self.assertTrue(
            has_internal_package_segment(
                Path("module/src/main/java/example/runtime/Worker.java"),
                "example.internal.runtime",
            )
        )

    def test_accepts_semantic_package(self) -> None:
        self.assertFalse(
            has_internal_package_segment(
                Path("module/src/main/java/example/runtime/Worker.java"),
                "example.runtime",
            )
        )


class WorkbenchWebDependencyDirectionTest(unittest.TestCase):

    def test_rejects_shared_to_domain_and_cross_domain_imports(self) -> None:
        with TemporaryDirectory(dir=ROOT) as temporary_directory:
            source_root = Path(temporary_directory)
            shared_file = source_root / "shared" / "sharedApi.ts"
            domain_file = source_root / "authoring" / "editor.ts"
            shared_file.parent.mkdir(parents=True)
            domain_file.parent.mkdir(parents=True)
            shared_file.write_text("import { value } from '@/operate/api'\n", encoding="utf-8")
            domain_file.write_text("const page = import('@/learn/pages/ExampleList')\n", encoding="utf-8")

            with patch(
                "scripts.check_architecture_boundaries.WORKBENCH_WEB_SOURCE",
                source_root,
            ):
                errors = check_workbench_web_dependency_directions()

        self.assertEqual(2, len(errors))
        self.assertTrue(any("shared -> operate" in error for error in errors))
        self.assertTrue(any("authoring -> learn" in error for error in errors))

    def test_accepts_domain_to_shared_and_shell_composition(self) -> None:
        with TemporaryDirectory(dir=ROOT) as temporary_directory:
            source_root = Path(temporary_directory)
            domain_file = source_root / "operate" / "page.ts"
            shell_file = source_root / "shell" / "routes.ts"
            domain_file.parent.mkdir(parents=True)
            shell_file.parent.mkdir(parents=True)
            domain_file.write_text("import { api } from '@/shared/api/client'\n", encoding="utf-8")
            shell_file.write_text("import Page from '@/operate/Page'\n", encoding="utf-8")

            with patch(
                "scripts.check_architecture_boundaries.WORKBENCH_WEB_SOURCE",
                source_root,
            ):
                errors = check_workbench_web_dependency_directions()

        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
