"""Tests for deterministic Java runtime environment evidence."""

from __future__ import annotations

import unittest

from scripts.record_java_environment import (
    JavaEnvironmentError,
    build_environment,
    parse_java_properties,
)


COMMIT = "a" * 40
OUTPUT = """
Property settings:
    java.runtime.version = 21.0.12+8-LTS
    java.specification.version = 21
    java.vendor = Eclipse Adoptium
    java.vm.name = OpenJDK 64-Bit Server VM
    java.vm.version = 21.0.12+8-LTS
    os.arch = aarch64
    os.name = Linux
openjdk version "21.0.12" 2026-07-21 LTS
"""


class RecordJavaEnvironmentTest(unittest.TestCase):
    """Bind declared feature versions to exact, safe runtime properties."""

    def test_parses_and_builds_exact_environment(self) -> None:
        values = build_environment(
            commit=COMMIT,
            declared_java="21",
            properties=parse_java_properties(OUTPUT),
        )
        self.assertEqual("21.0.12+8-LTS", values["java_runtime_version"])
        self.assertEqual("Eclipse Adoptium", values["java_vendor"])
        self.assertEqual("aarch64", values["os_arch"])

    def test_rejects_declared_feature_mismatch(self) -> None:
        with self.assertRaisesRegex(JavaEnvironmentError, "does not match declared"):
            build_environment(
                commit=COMMIT,
                declared_java="25",
                properties=parse_java_properties(OUTPUT),
            )

    def test_rejects_missing_or_duplicate_property(self) -> None:
        with self.assertRaisesRegex(JavaEnvironmentError, "required properties"):
            parse_java_properties(OUTPUT.replace("    os.arch = aarch64\n", ""))
        with self.assertRaisesRegex(JavaEnvironmentError, "duplicate Java property"):
            parse_java_properties(OUTPUT + "    java.vendor = Other\n")


if __name__ == "__main__":
    unittest.main()
