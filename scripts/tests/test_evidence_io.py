"""Tests for shared deterministic evidence writes."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.evidence_io import write_json, write_text


class EvidenceIoTest(unittest.TestCase):
    def test_writes_text_and_sorted_json_without_leaving_temporary_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            text_path = root / "nested" / "environment.txt"
            json_path = root / "evidence.json"

            write_text(text_path, "java=21\n")
            write_json(json_path, {"z": 1, "a": "值"})

            self.assertEqual("java=21\n", text_path.read_text(encoding="utf-8"))
            content = json_path.read_text(encoding="utf-8")
            self.assertEqual({"a": "值", "z": 1}, json.loads(content))
            self.assertLess(content.index('"a"'), content.index('"z"'))
            self.assertFalse(list(root.rglob("*.tmp")))


if __name__ == "__main__":
    unittest.main()
