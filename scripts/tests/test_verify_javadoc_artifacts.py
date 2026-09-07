"""Tests for release Javadoc artifact verification."""

from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.verify_javadoc_artifacts import verify_module


class VerifyJavadocArtifactsTest(unittest.TestCase):
    """Require release archives to cover public top-level types."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.module = Path(self.temporary.name)
        source = self.module / "src/main/java/example/api/ExampleService.java"
        source.parent.mkdir(parents=True)
        source.write_text(
            "package example.api;\npublic interface ExampleService {}\n",
            encoding="utf-8",
        )
        (self.module / "target").mkdir()

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_archive(self, entries: tuple[str, ...]) -> None:
        archive = self.module / "target/example-javadoc.jar"
        with zipfile.ZipFile(archive, mode="w") as contents:
            for entry in entries:
                contents.writestr(entry, "document")

    def test_accepts_complete_archive(self) -> None:
        self.write_archive(
            (
                "index.html",
                "element-list",
                "example/api/ExampleService.html",
            )
        )
        _, count = verify_module(self.module)
        self.assertEqual(1, count)

    def test_rejects_missing_public_type_page(self) -> None:
        self.write_archive(("index.html", "element-list"))
        with self.assertRaisesRegex(ValueError, "ExampleService.html"):
            verify_module(self.module)

    def test_rejects_path_traversal(self) -> None:
        self.write_archive(
            (
                "index.html",
                "element-list",
                "example/api/ExampleService.html",
                "../outside.html",
            )
        )
        with self.assertRaisesRegex(ValueError, "not normalized"):
            verify_module(self.module)

    def test_rejects_directories_in_place_of_required_pages(self) -> None:
        self.write_archive(
            ("index.html/", "element-list/", "example/api/ExampleService.html/")
        )
        with self.assertRaisesRegex(ValueError, "required"):
            verify_module(self.module)

    def test_rejects_empty_required_pages(self) -> None:
        archive = self.module / "target/example-javadoc.jar"
        with zipfile.ZipFile(archive, mode="w") as contents:
            for entry in ("index.html", "element-list", "example/api/ExampleService.html"):
                contents.writestr(entry, "")
        with self.assertRaisesRegex(ValueError, "empty"):
            verify_module(self.module)


if __name__ == "__main__":
    unittest.main()
