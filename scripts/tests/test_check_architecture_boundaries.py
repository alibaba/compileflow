#!/usr/bin/env python3
"""Tests for architecture checks that do not need a repository fixture."""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from scripts.check_architecture_boundaries import (
    ROOT,
    SKIP_DIRECTORIES,
    check_workbench_web_dependency_directions,
    find_case_mismatched_paths,
    has_internal_package_segment,
    public_top_level_type_name,
)


class RepositoryTextScanTest(unittest.TestCase):

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
