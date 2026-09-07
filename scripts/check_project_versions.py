#!/usr/bin/env python3
"""Validate Maven and Workbench package version alignment."""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
SEMVER_PATTERN = re.compile(
    r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
    r"(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
)
WORKBENCH_PACKAGES = (
    Path("compileflow-workbench/package.json"),
    Path("compileflow-workbench/apps/web/package.json"),
    Path("compileflow-workbench/apps/dev-gateway/package.json"),
)


def validate_version_syntax(version: str) -> None:
    if not SEMVER_PATTERN.fullmatch(version):
        raise ValueError(f"version must use semantic version syntax: {version!r}")
    prerelease = version.partition("-")[2]
    for identifier in prerelease.split(".") if prerelease else ():
        if identifier.isdigit() and len(identifier) > 1 and identifier.startswith("0"):
            raise ValueError(
                "numeric prerelease identifiers must not contain leading zeroes: "
                f"{version!r}"
            )


def root_maven_version() -> str:
    version = ET.parse("pom.xml").getroot().findtext(
        "m:version", namespaces=MAVEN_NAMESPACE
    )
    if not version:
        raise ValueError("pom.xml does not declare a project version")
    return version


def validate_project_versions(expected: str, release: bool) -> None:
    validate_version_syntax(expected)
    if release and expected.endswith("-SNAPSHOT"):
        raise ValueError(f"release version must not be a snapshot: {expected!r}")

    errors: list[str] = []
    actual_root = root_maven_version()
    if actual_root != expected:
        errors.append(f"pom.xml: version {actual_root!r}, expected {expected!r}")

    for path in Path(".").rglob("pom.xml"):
        if any(part in {"node_modules", "target"} for part in path.parts):
            continue
        project = ET.parse(path).getroot()
        parent = project.find("m:parent", MAVEN_NAMESPACE)
        group = project.findtext("m:groupId", namespaces=MAVEN_NAMESPACE)
        if group is None and parent is not None:
            group = parent.findtext("m:groupId", namespaces=MAVEN_NAMESPACE)
        if group == "com.alibaba.compileflow":
            version = project.findtext("m:version", namespaces=MAVEN_NAMESPACE)
            if version is not None and version != expected:
                errors.append(f"{path}: version {version!r}, expected {expected!r}")
            alignment = project.findtext(
                "m:properties/m:compileflow.version", namespaces=MAVEN_NAMESPACE
            )
            if alignment is not None and alignment != expected:
                errors.append(
                    f"{path}: compileflow.version {alignment!r}, expected {expected!r}"
                )
        if parent is None:
            continue
        group = parent.findtext("m:groupId", namespaces=MAVEN_NAMESPACE)
        artifact = parent.findtext("m:artifactId", namespaces=MAVEN_NAMESPACE)
        parent_version = parent.findtext("m:version", namespaces=MAVEN_NAMESPACE)
        if group == "com.alibaba.compileflow" and parent_version != expected:
            errors.append(
                f"{path}: CompileFlow parent {artifact!r} uses "
                f"{parent_version!r}, expected {expected!r}"
            )

    for path in WORKBENCH_PACKAGES:
        package_version = json.loads(path.read_text(encoding="utf-8")).get("version")
        if package_version != expected:
            errors.append(
                f"{path}: package version {package_version!r}, expected {expected!r}"
            )

    if errors:
        raise ValueError("\n".join(errors))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--expected",
        help="expected project version; defaults to the root Maven version",
    )
    parser.add_argument(
        "--release",
        action="store_true",
        help="reject snapshot versions",
    )
    parser.add_argument(
        "--syntax-only",
        metavar="VERSION",
        help="validate one version string without reading project files",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.syntax_only:
            validate_version_syntax(args.syntax_only)
            print(f"Validated version syntax: {args.syntax_only}")
            return 0
        expected = args.expected or root_maven_version()
        validate_project_versions(expected, args.release)
    except (ET.ParseError, OSError, ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"Project version validation failed: {error}") from error
    print(f"Validated aligned project version: {expected}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
