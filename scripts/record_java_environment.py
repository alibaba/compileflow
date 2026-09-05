#!/usr/bin/env python3
"""Record a bounded, non-secret Java runtime identity for release evidence."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

try:
    from scripts.evidence_io import write_text
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_text


COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
FEATURE_PATTERN = re.compile(r"[0-9]+")
PROPERTY_PATTERN = re.compile(r"^\s*([a-zA-Z0-9._-]+)\s*=\s*(.*?)\s*$")
REQUIRED_PROPERTIES = {
    "java.runtime.version": "java_runtime_version",
    "java.specification.version": "java_specification_version",
    "java.vendor": "java_vendor",
    "java.vm.name": "java_vm_name",
    "java.vm.version": "java_vm_version",
    "os.name": "os_name",
    "os.arch": "os_arch",
}


class JavaEnvironmentError(ValueError):
    """The selected Java runtime cannot produce trustworthy evidence."""


def require(condition: bool, message: str) -> None:
    """Raise one actionable environment failure."""
    if not condition:
        raise JavaEnvironmentError(message)


def parse_java_properties(output: str) -> dict[str, str]:
    """Parse the property block emitted by -XshowSettings:properties."""
    properties: dict[str, str] = {}
    for line in output.splitlines():
        match = PROPERTY_PATTERN.fullmatch(line)
        if match is None:
            continue
        key, value = match.groups()
        if key in REQUIRED_PROPERTIES:
            require(key not in properties, f"duplicate Java property {key!r}")
            require(
                0 < len(value) <= 512
                and not any(character in value for character in "\r\n\0"),
                f"Java property {key!r} is empty, oversized, or unsafe",
            )
            properties[key] = value
    missing = sorted(set(REQUIRED_PROPERTIES) - set(properties))
    require(not missing, f"Java runtime did not report required properties: {missing}")
    return properties


def build_environment(
    *,
    commit: str,
    declared_java: str,
    properties: dict[str, str],
) -> dict[str, str]:
    """Validate and normalize one Java runtime identity."""
    require(COMMIT_PATTERN.fullmatch(commit) is not None, "commit must be a 40-character Git SHA")
    require(FEATURE_PATTERN.fullmatch(declared_java) is not None, "declared Java must be a feature version")
    missing = sorted(set(REQUIRED_PROPERTIES) - set(properties))
    require(not missing, f"Java properties are incomplete: {missing}")
    specification = properties["java.specification.version"]
    require(
        specification == declared_java,
        f"Java specification {specification!r} does not match declared {declared_java!r}",
    )
    runtime = properties["java.runtime.version"]
    require(
        re.fullmatch(rf"{re.escape(declared_java)}(?:[.+_-].*)?", runtime) is not None,
        f"Java runtime {runtime!r} does not match declared {declared_java!r}",
    )
    result = {
        "commit": commit,
        "declared_java": declared_java,
    }
    for property_name, evidence_name in REQUIRED_PROPERTIES.items():
        value = properties[property_name]
        require(
            isinstance(value, str)
            and 0 < len(value) <= 512
            and not any(character in value for character in "\r\n\0="),
            f"Java property {property_name!r} is empty, oversized, or unsafe",
        )
        result[evidence_name] = value
    return dict(sorted(result.items()))


def inspect_java() -> dict[str, str]:
    """Inspect the java executable selected by the current environment."""
    try:
        process = subprocess.run(
            ["java", "-XshowSettings:properties", "-version"],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise JavaEnvironmentError(f"cannot inspect java: {error}") from error
    require(process.returncode == 0, f"java inspection exited with {process.returncode}")
    return parse_java_properties(process.stdout)


def write_environment(path: Path, values: dict[str, str]) -> None:
    """Atomically write deterministic key/value evidence."""
    write_text(
        path,
        "".join(f"{key}={value}\n" for key, value in sorted(values.items())),
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--declared-java", required=True)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    """Record the active runtime as a release-evidence input."""
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        values = build_environment(
            commit=args.commit,
            declared_java=args.declared_java,
            properties=inspect_java(),
        )
        write_environment(args.output, values)
    except (JavaEnvironmentError, OSError) as error:
        print(f"Java environment recording failed: {error}", file=sys.stderr)
        return 1
    print(
        "Java environment recorded: "
        f"java={values['java_runtime_version']}, vendor={values['java_vendor']}, "
        f"arch={values['os_arch']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
