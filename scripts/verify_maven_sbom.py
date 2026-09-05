#!/usr/bin/env python3
"""Verify the aggregate Maven CycloneDX BOM and Durable release surface."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


GROUP = "com.alibaba.compileflow"
ROOT_ARTIFACT = "compileflow"
DURABLE_AGGREGATOR = "compileflow-durable"
DURABLE_ARTIFACTS = (
    "compileflow-durable-api",
    "compileflow-durable-spi",
    "compileflow-durable-testkit",
    "compileflow-durable-runtime",
    "compileflow-durable-postgres",
    "compileflow-durable-spring-boot-autoconfigure",
    "compileflow-durable-spring-boot-starter",
    "compileflow-durable-spring-boot-starter-postgres",
)
MAX_BOM_BYTES = 32 * 1024 * 1024


class SbomVerificationError(ValueError):
    """The aggregate BOM does not prove the expected release surface."""


def require(condition: bool, message: str) -> None:
    """Raise one actionable verification failure."""
    if not condition:
        raise SbomVerificationError(message)


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    """Reject ambiguous JSON objects instead of accepting the last key."""
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise SbomVerificationError(f"duplicate JSON key: {key!r}")
        result[key] = value
    return result


def parse_bom(text: str) -> dict[str, Any]:
    """Parse one BOM with duplicate-key rejection."""
    try:
        value = json.loads(text, object_pairs_hook=_unique_object)
    except json.JSONDecodeError as error:
        raise SbomVerificationError(f"invalid JSON: {error}") from error
    require(isinstance(value, dict), "CycloneDX BOM root must be an object")
    return value


def load_bom(path: Path) -> dict[str, Any]:
    """Load one bounded regular BOM file without following a symlink."""
    require(not path.is_symlink(), f"BOM path must not be a symlink: {path}")
    require(path.is_file(), f"BOM is missing or not a regular file: {path}")
    size = path.stat().st_size
    require(0 < size <= MAX_BOM_BYTES, f"BOM size is outside the allowed range: {size}")
    try:
        return parse_bom(path.read_text(encoding="utf-8"))
    except UnicodeDecodeError as error:
        raise SbomVerificationError("BOM is not valid UTF-8") from error


def maven_ref(artifact: str, version: str, packaging: str) -> str:
    """Build the exact Package URL emitted for a local Maven component."""
    return f"pkg:maven/{GROUP}/{artifact}@{version}?type={packaging}"


def _component_index(components: Any) -> dict[str, dict[str, Any]]:
    """Index components by unique, non-empty BOM reference."""
    require(isinstance(components, list) and components, "BOM contains no components")
    result: dict[str, dict[str, Any]] = {}
    for ordinal, component in enumerate(components):
        require(isinstance(component, dict), f"component {ordinal} must be an object")
        reference = component.get("bom-ref")
        require(
            isinstance(reference, str) and reference,
            f"component {ordinal} has no non-empty bom-ref",
        )
        require(reference not in result, f"duplicate component bom-ref: {reference}")
        result[reference] = component
    return result


def _dependency_index(dependencies: Any) -> dict[str, frozenset[str]]:
    """Index dependency edges and reject duplicate or ambiguous entries."""
    require(isinstance(dependencies, list), "BOM dependencies must be an array")
    result: dict[str, frozenset[str]] = {}
    for ordinal, dependency in enumerate(dependencies):
        require(isinstance(dependency, dict), f"dependency {ordinal} must be an object")
        reference = dependency.get("ref")
        values = dependency.get("dependsOn")
        require(
            isinstance(reference, str) and reference,
            f"dependency {ordinal} has no non-empty ref",
        )
        require(reference not in result, f"duplicate dependency ref: {reference}")
        require(isinstance(values, list), f"dependency {reference} has no dependsOn array")
        require(
            all(isinstance(value, str) and value for value in values),
            f"dependency {reference} contains an invalid target",
        )
        require(
            len(values) == len(set(values)),
            f"dependency {reference} contains duplicate targets",
        )
        result[reference] = frozenset(values)
    return result


def verify_bom(data: dict[str, Any], expected_version: str | None = None) -> str:
    """Verify root identity, seven Durable components, and their graph edges."""
    require(data.get("bomFormat") == "CycloneDX", "BOM format must be CycloneDX")
    require(data.get("specVersion") == "1.6", "CycloneDX specVersion must be 1.6")

    metadata = data.get("metadata")
    require(isinstance(metadata, dict), "BOM metadata must be an object")
    root = metadata.get("component")
    require(isinstance(root, dict), "BOM metadata.component must be an object")
    version = root.get("version")
    require(isinstance(version, str) and version, "BOM root version must be non-empty")
    if expected_version is not None:
        require(
            version == expected_version,
            f"BOM root version {version!r} does not match {expected_version!r}",
        )

    root_ref = maven_ref(ROOT_ARTIFACT, version, "pom")
    for field, expected in (
        ("type", "library"),
        ("group", GROUP),
        ("name", ROOT_ARTIFACT),
        ("bom-ref", root_ref),
        ("purl", root_ref),
    ):
        require(root.get(field) == expected, f"BOM root {field} must be {expected!r}")

    components = _component_index(data.get("components"))
    dependencies = _dependency_index(data.get("dependencies"))
    aggregator_ref = maven_ref(DURABLE_AGGREGATOR, version, "pom")
    required_refs = {
        maven_ref(artifact, version, "jar") for artifact in DURABLE_ARTIFACTS
    }

    require(
        aggregator_ref in dependencies.get(root_ref, frozenset()),
        "CompileFlow root must depend on the Durable aggregator in the BOM graph",
    )
    require(
        dependencies.get(aggregator_ref) == required_refs,
        "Durable aggregator must depend on exactly the seven kernel modules",
    )

    expected_components = ((DURABLE_AGGREGATOR, "pom"),) + tuple(
        (artifact, "jar") for artifact in DURABLE_ARTIFACTS
    )
    for artifact, packaging in expected_components:
        reference = maven_ref(artifact, version, packaging)
        component = components.get(reference)
        require(component is not None, f"BOM misses Durable component {artifact}")
        for field, expected in (
            ("type", "library"),
            ("group", GROUP),
            ("name", artifact),
            ("version", version),
            ("bom-ref", reference),
            ("purl", reference),
        ):
            require(
                component.get(field) == expected,
                f"Durable component {artifact} has invalid {field}",
            )
        require(
            reference in dependencies,
            f"BOM dependency graph misses Durable component {artifact}",
        )

    return version


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bom", type=Path, help="aggregate CycloneDX JSON BOM")
    parser.add_argument("--expected-version", help="exact release version")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    """Run the verifier as a command-line release gate."""
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        data = load_bom(args.bom)
        version = verify_bom(data, args.expected_version)
    except (OSError, SbomVerificationError) as error:
        print(f"Maven SBOM verification failed: {error}", file=sys.stderr)
        return 1
    print(
        "Maven SBOM verification passed: "
        f"version={version}, durableModules={len(DURABLE_ARTIFACTS)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
