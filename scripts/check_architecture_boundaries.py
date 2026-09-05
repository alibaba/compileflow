#!/usr/bin/env python3
"""Enforce repository-level architecture boundaries that compilation cannot prove."""

from __future__ import annotations

import functools
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections.abc import Iterable
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
SKIP_DIRECTORIES = {
    ".git",
    ".idea",
    ".mvn",
    ".next",
    ".turbo",
    "__pycache__",
    "bin",
    "coverage",
    "dist",
    "node_modules",
    "playwright-report",
    "target",
    "test-results",
}
TEXT_SUFFIXES = {
    ".java",
    ".js",
    ".json",
    ".md",
    ".mjs",
    ".properties",
    ".py",
    ".sh",
    ".ts",
    ".tsx",
    ".xml",
    ".yaml",
    ".yml",
}

DEPLOY_API = ROOT / "compileflow-deploy" / "compileflow-deploy-api"
DEPLOY_CONTROL_PLANE = ROOT / "compileflow-deploy" / "compileflow-deploy-control-plane"
DEPLOY_RUNTIME = ROOT / "compileflow-deploy" / "compileflow-deploy-runtime"
SERVER = ROOT / "compileflow-workbench-server"
WORKBENCH_WEB_SOURCE = ROOT / "compileflow-workbench" / "apps" / "web" / "src"
AUTOCONFIGURE = ROOT / "compileflow-spring-boot-autoconfigure"
ENGINE_MODULES = (
    ROOT / "compileflow-api",
    ROOT / "compileflow-core",
    ROOT / "compileflow-tbbpm",
    ROOT / "compileflow-bpmn",
)
DURABLE = ROOT / "compileflow-durable"
DURABLE_API = DURABLE / "compileflow-durable-api"
DURABLE_SPI = DURABLE / "compileflow-durable-spi"
DURABLE_TESTKIT = DURABLE / "compileflow-durable-testkit"
DURABLE_RUNTIME = DURABLE / "compileflow-durable-runtime"
DURABLE_POSTGRES = DURABLE / "compileflow-durable-postgres"
DURABLE_AUTOCONFIGURE = (
    DURABLE / "compileflow-durable-spring-boot-autoconfigure"
)
DURABLE_STARTER = DURABLE / "compileflow-durable-spring-boot-starter"
DURABLE_POSTGRES_STARTER = (
    DURABLE / "compileflow-durable-spring-boot-starter-postgres"
)
LIBRARY_MODULES = (
    ROOT / "compileflow-api",
    ROOT / "compileflow-core",
    ROOT / "compileflow-tbbpm",
    ROOT / "compileflow-bpmn",
    ROOT / "compileflow-deploy" / "compileflow-deploy-api",
    ROOT / "compileflow-deploy" / "compileflow-deploy-control-plane",
    ROOT / "compileflow-deploy" / "compileflow-deploy-runtime",
    AUTOCONFIGURE,
    ROOT / "compileflow-spring-boot-starter",
    DURABLE_API,
    DURABLE_SPI,
    DURABLE_TESTKIT,
    DURABLE_RUNTIME,
    DURABLE_POSTGRES,
    DURABLE_AUTOCONFIGURE,
    DURABLE_STARTER,
    DURABLE_POSTGRES_STARTER,
)
LOGGING_CONFIGURATION_NAMES = {
    "log4j.properties",
    "log4j.xml",
    "log4j2-spring.xml",
    "log4j2.xml",
    "logback-spring.xml",
    "logback.xml",
    "logging.properties",
    "simplelogger.properties",
}
DEPLOY_MIGRATION_OWNER = (
    DEPLOY_CONTROL_PLANE
    / "src"
    / "main"
    / "resources"
    / "db"
    / "compileflow-deploy"
    / "migration"
)
WORKBENCH_MIGRATION_OWNER = (
    SERVER
    / "src"
    / "main"
    / "resources"
    / "db"
    / "compileflow-workbench-server"
    / "migration"
)
DURABLE_MIGRATION_OWNER = (
    DURABLE_POSTGRES
    / "src"
    / "main"
    / "resources"
    / "db"
    / "compileflow-durable"
    / "migration"
)

DEPLOY_TABLES = {
    "cf_process_alias",
    "cf_process_version",
    "cf_rollout",
    "cf_rollout_event",
    "cf_routing_outbox",
}
WORKBENCH_TABLES = {
    "cf_process_draft",
    "cf_execution_log",
    "cf_async_invocation",
    "cf_async_invocation_attempt",
}
LEGACY_DEPLOY_MARKERS = (
    "compileflow-deploy-common",
    "com.alibaba.compileflow.deploy.common",
    "com/alibaba/compileflow/deploy/common",
)
REMOVED_AUTOMATION_FIELDS = ("abortOnFailure", "abortTriggered")
LEGACY_ROUTING_KEY_MARKERS = ("stickyKey", "StickyKey")
FORBIDDEN_DEFAULT_METRIC_TAGS = (
    "alias",
    "artifactId",
    "digest",
    "invocationId",
    "namespace",
    "nodeId",
    "processCode",
    "routingKey",
    "version",
)


def relative(path: Path) -> str:
    """Return a stable repository-relative path."""
    return path.relative_to(ROOT).as_posix()


@functools.lru_cache(maxsize=None)
def read_text(path: Path) -> str:
    """Read repository text without hiding malformed bytes."""
    return path.read_text(encoding="utf-8")


@functools.lru_cache(maxsize=1)
def iter_repository_text_files() -> tuple[Path, ...]:
    """Return relevant source and documentation files without traversing generated trees."""
    this_script = Path(__file__).resolve()
    paths: list[Path] = []
    for directory, directory_names, file_names in os.walk(ROOT):
        directory_names[:] = sorted(
            name for name in directory_names if name not in SKIP_DIRECTORIES
        )
        parent = Path(directory)
        for file_name in sorted(file_names):
            path = parent / file_name
            if path.resolve() == this_script:
                continue
            if path.name == "pom.xml" or path.suffix in TEXT_SUFFIXES:
                paths.append(path)
    return tuple(paths)


def all_dependency_coordinates(pom: Path) -> list[tuple[str, str, str]]:
    """Return every direct Maven dependency from one module POM."""
    root = ET.parse(pom).getroot()
    dependencies: list[tuple[str, str, str]] = []
    for dependency in root.findall("m:dependencies/m:dependency", MAVEN_NAMESPACE):
        group_id = dependency.findtext("m:groupId", default="", namespaces=MAVEN_NAMESPACE).strip()
        artifact_id = dependency.findtext("m:artifactId", default="", namespaces=MAVEN_NAMESPACE).strip()
        scope = dependency.findtext("m:scope", default="compile", namespaces=MAVEN_NAMESPACE).strip()
        dependencies.append((group_id, artifact_id, scope))
    return dependencies


def dependency_coordinates(pom: Path) -> list[tuple[str, str, str]]:
    """Return direct non-test Maven dependencies from one module POM."""
    return [
        coordinate
        for coordinate in all_dependency_coordinates(pom)
        if coordinate[2] != "test"
    ]


def root_reactor_modules() -> tuple[str, ...]:
    """Return the default root reactor modules in declaration order."""
    root = ET.parse(ROOT / "pom.xml").getroot()
    return tuple(
        module.text.strip()
        for module in root.findall("m:modules/m:module", MAVEN_NAMESPACE)
        if module.text and module.text.strip()
    )


def reactor_modules(pom: Path) -> tuple[str, ...]:
    """Return one aggregator's modules in declaration order."""
    root = ET.parse(pom).getroot()
    return tuple(
        module.text.strip()
        for module in root.findall("m:modules/m:module", MAVEN_NAMESPACE)
        if module.text and module.text.strip()
    )


def check_deploy_api_dependencies() -> list[str]:
    """Keep the public deploy artifact independent from implementation frameworks."""
    errors: list[str] = []
    pom = DEPLOY_API / "pom.xml"
    if not pom.is_file():
        return [f"{relative(pom)} is required"]

    dependencies = dependency_coordinates(pom)
    internal_dependencies = [
        artifact_id
        for group_id, artifact_id, _ in dependencies
        if group_id == "com.alibaba.compileflow"
    ]
    if internal_dependencies.count("compileflow-api") != 1:
        errors.append(
            f"{relative(pom)} must have exactly one non-test dependency on compileflow-api"
        )
    unexpected_internal = sorted(
        artifact_id for artifact_id in internal_dependencies if artifact_id != "compileflow-api"
    )
    for artifact_id in unexpected_internal:
        errors.append(
            f"{relative(pom)} must not depend on implementation module "
            f"com.alibaba.compileflow:{artifact_id}"
        )

    forbidden_group_prefixes = (
        "com.alibaba.nacos",
        "com.zaxxer",
        "jakarta.persistence",
        "org.flywaydb",
        "org.postgresql",
        "org.springframework",
    )
    forbidden_artifact_tokens = ("jdbc", "nacos", "postgresql", "spring")
    for group_id, artifact_id, scope in dependencies:
        if group_id.startswith(forbidden_group_prefixes) or any(
            token in artifact_id.lower() for token in forbidden_artifact_tokens
        ):
            errors.append(
                f"{relative(pom)} has forbidden {scope} dependency "
                f"{group_id}:{artifact_id}"
            )
    return errors


def check_engine_api_dependencies() -> list[str]:
    """Keep compileflow-api a zero-runtime-dependency public contract artifact."""
    pom = ROOT / "compileflow-api" / "pom.xml"
    if not pom.is_file():
        return [f"{relative(pom)} is required"]
    dependencies = dependency_coordinates(pom)
    if not dependencies:
        return []
    rendered = ", ".join(
        f"{group_id}:{artifact_id}:{scope}"
        for group_id, artifact_id, scope in dependencies
    )
    return [
        f"{relative(pom)} must have zero non-test dependencies; found {rendered}"
    ]


def check_java_script_provider_boundary() -> list[str]:
    """Keep trusted Java Script execution as a built-in Core capability."""
    errors: list[str] = []
    core_root = ROOT / "compileflow-core" / "src" / "main" / "java"
    provider = core_root / "com" / "alibaba" / "compileflow" / "engine" / "core" / "runtime" / "script" \
        / "JavaSourceScriptExecutor.java"
    registry = core_root / "com" / "alibaba" / "compileflow" / "engine" / "core" / "runtime" / "script" \
        / "ScriptExecutorRegistry.java"
    if not provider.is_file():
        errors.append(f"{relative(provider)} is required as the built-in Java Script executor")
    registry_text = read_text(registry) if registry.is_file() else ""
    if 'resolved.put("java", new JavaSourceScriptExecutor())' not in registry_text:
        errors.append(f"{relative(registry)} must register JavaSourceScriptExecutor as a built-in language")
    return errors


def check_documented_semantic_contracts() -> list[str]:
    """Keep active architecture, ontology, and supported-surface docs aligned."""
    errors: list[str] = []
    module_docs = (
        ROOT / "docs" / "architecture" / "03-MODULE_MAP.en.md",
        ROOT / "docs" / "architecture" / "03-MODULE_MAP.zh.md",
    )
    for path in module_docs:
        text = read_text(path) if path.is_file() else ""
        for forbidden in ("DurableRuntime --> TBBPM", "DurableRuntime --> BPMN",
                          "DurableRuntime --> Tbbpm", "DurableRuntime --> Bpmn"):
            if forbidden in text:
                errors.append(f"{relative(path)} contains stale production edge: {forbidden}")
        for required in ("compileflow-api", "compileflow-durable-runtime", "compileflow-core"):
            if required not in text:
                errors.append(f"{relative(path)} must document current module boundary: {required}")

    ontology_docs = (
        ROOT / "docs" / "architecture" / "10-DURABLE_ARCHITECTURE.en.md",
        ROOT / "docs" / "architecture" / "10-DURABLE_ARCHITECTURE.zh.md",
        ROOT / "docs" / "specs" / "tbbpm-specification.en.md",
        ROOT / "docs" / "specs" / "tbbpm-specification.zh.md",
    )
    for path in ontology_docs:
        text = read_text(path) if path.is_file() else ""
        for forbidden in ('effect="pure"', "effect=pure", "effect=idempotent",
                          "effect=non-idempotent", "pure or idempotent", "pure` or `idempotent"):
            if forbidden in text:
                errors.append(f"{relative(path)} contains removed Action ontology: {forbidden}")
        if "execution=" not in text:
            errors.append(f"{relative(path)} must describe execution=replayable|effect")

    for suffix in ("en", "zh"):
        path = ROOT / "docs" / "architecture" / f"06-SUPPORTED_SURFACES.{suffix}.md"
        text = read_text(path) if path.is_file() else ""
        for required in (
            "AliasRoutingOptions",
            "ProcessDefinitionDigest",
            "ProcessIdentifiers",
            "ProcessText",
            "spi.script.*",
            "compileflow-core",
        ):
            if required not in text:
                errors.append(f"{relative(path)} must freeze supported surface: {required}")
    return errors


def check_release_compatibility_contracts() -> list[str]:
    """Keep pre-release compatibility decisions explicit and aligned with implementation."""
    errors: list[str] = []
    policy = ROOT / "docs" / "compatibility-policy.md"
    policy_text = read_text(policy) if policy.is_file() else ""
    for marker in (
        "ProcessEvent` is a sealed lifecycle hierarchy and is closed for the 2.x line",
        "Supported by deployment",
        "Flyway migration is immutable",
        "expectedArtifactDigest",
        "compileflow.deploy.*",
        "coordinated homogeneous protocol upgrades",
    ):
        if marker not in policy_text:
            errors.append(f"{relative(policy)} must retain compatibility fact: {marker}")

    protocol = DEPLOY_API.parent / "docs" / "PROTOCOL.md"
    protocol_text = read_text(protocol) if protocol.is_file() else ""
    for marker in (
        "coordinated homogeneous upgrades only",
        "Alias-state and artifact parsers accept exactly schema `1`",
        "Every key-construction call requires an explicit canonical alias",
    ):
        if marker not in protocol_text:
            errors.append(f"{relative(protocol)} must retain Deploy protocol fact: {marker}")

    routing_keys = (
        DEPLOY_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "deploy" / "api" / "protocol" / "routing" / "RoutingStateKeys.java"
    )
    routing_text = read_text(routing_keys) if routing_keys.is_file() else ""
    for forbidden in ("DEFAULT_ALIAS", "canonicalAlias"):
        if forbidden in routing_text:
            errors.append(f"{relative(routing_keys)} must require an explicit Alias; found {forbidden}")

    metadata_keys = (
        DEPLOY_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "deploy" / "api" / "release" / "ReleaseMetadataKeys.java"
    )
    metadata_text = read_text(metadata_keys) if metadata_keys.is_file() else ""
    if "ARTIFACT_DIGEST" in metadata_text:
        errors.append(f"{relative(metadata_keys)} must not encode integrity preconditions as metadata")

    binder = (
        AUTOCONFIGURE / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "engine" / "spring" / "boot" / "autoconfigure" / "observability"
        / "CompileFlowDeploymentMetricsBinder.java"
    )
    binder_text = read_text(binder) if binder.is_file() else ""
    for forbidden in (
        '"compileflow.runtime.',
        '"compileflow.alias.',
        '.tag("errorCode"',
    ):
        if forbidden in binder_text:
            errors.append(f"{relative(binder)} contains legacy Deploy telemetry fact: {forbidden}")
    return errors


def check_optional_deploy_configuration_binding() -> list[str]:
    """Keep unconditional property binding free of optional Deploy classes."""
    errors: list[str] = []
    properties = (
        AUTOCONFIGURE
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "engine"
        / "spring"
        / "boot"
        / "autoconfigure"
        / "properties"
    )
    if not properties.is_dir():
        return errors
    for path in sorted(properties.rglob("*.java")):
        if "com.alibaba.compileflow.deploy" in read_text(path):
            errors.append(
                f"{relative(path)} must not link optional Deploy classes; "
                "base starter configuration is bound even when Deploy is absent"
            )
    return errors


def check_module_dependency_directions() -> list[str]:
    """Prevent deploy implementation modules from depending back on their adapters."""
    errors: list[str] = []
    rules = (
        (
            DEPLOY_CONTROL_PLANE / "pom.xml",
            {"compileflow-workbench-server"},
            "deployment control plane must not depend on server",
        ),
        (
            DEPLOY_RUNTIME / "pom.xml",
            {"compileflow-deploy-control-plane", "compileflow-workbench-server"},
            "deploy-runtime must not depend on deployment control plane or server",
        ),
    )
    for pom, forbidden_artifacts, message in rules:
        if not pom.is_file():
            errors.append(f"{relative(pom)} is required")
            continue
        for group_id, artifact_id, _ in dependency_coordinates(pom):
            if group_id == "com.alibaba.compileflow" and artifact_id in forbidden_artifacts:
                errors.append(f"{relative(pom)}: {message}: {artifact_id}")
    return errors


def check_durable_product_boundary() -> list[str]:
    """Keep Durable opt-in, layered, and isolated from generic engine modules."""
    errors: list[str] = []
    aggregator_pom = DURABLE / "pom.xml"
    if not aggregator_pom.is_file():
        return [f"{relative(aggregator_pom)} is required"]

    durable_root_entries = tuple(
        module for module in root_reactor_modules()
        if module.startswith("compileflow-durable")
    )
    if durable_root_entries != ("compileflow-durable",):
        errors.append(
            "root pom.xml must expose Durable only through the "
            "compileflow-durable product aggregator"
        )

    expected_children = (
        "compileflow-durable-api",
        "compileflow-durable-spi",
        "compileflow-durable-testkit",
        "compileflow-durable-runtime",
        "compileflow-durable-postgres",
        "compileflow-durable-spring-boot-autoconfigure",
        "compileflow-durable-spring-boot-starter",
        "compileflow-durable-spring-boot-starter-postgres",
    )
    if reactor_modules(aggregator_pom) != expected_children:
        errors.append(
            f"{relative(aggregator_pom)} must declare the canonical Durable "
            "layer order exactly"
        )
    for child in expected_children:
        if not (DURABLE / child / "pom.xml").is_file():
            errors.append(
                f"{relative(DURABLE / child / 'pom.xml')} is required"
            )
        if (ROOT / child).exists():
            errors.append(
                f"{child} must be a child of compileflow-durable, not a "
                "root sibling"
            )
    unexpected_children = sorted(
        path.name
        for path in DURABLE.iterdir()
        if path.is_dir()
        and (path / "pom.xml").is_file()
        and path.name not in expected_children
    )
    if unexpected_children:
        errors.append(
            "CompileFlow Durable must remain an eight-module product; "
            f"unexpected modules: {unexpected_children}"
        )

    dependency_rules = (
        (
            DURABLE_API / "pom.xml",
            {"compileflow-api"},
            "Durable API may depend only on compileflow-api",
        ),
        (
            DURABLE_SPI / "pom.xml",
            {"compileflow-api", "compileflow-durable-api"},
            "Durable SPI may depend only on the foundational CompileFlow API"
            " and Durable API",
        ),
        (
            DURABLE_TESTKIT / "pom.xml",
            {
                "compileflow-api",
                "compileflow-durable-api",
                "compileflow-durable-spi",
            },
            "Durable Testkit may depend only on the foundational CompileFlow"
            " API, Durable API, and Kernel Provider SPI",
        ),
        (
            DURABLE_RUNTIME / "pom.xml",
            {
                "compileflow-api",
                "compileflow-core",
                "compileflow-durable-api",
                "compileflow-durable-spi",
            },
            "Durable Runtime may depend only on the public API, integration/provider SPI,"
            " and required CompileFlow engine contracts internally",
        ),
        (
            DURABLE_POSTGRES / "pom.xml",
            {
                "compileflow-api",
                "compileflow-durable-api",
                "compileflow-durable-spi",
            },
            "Durable PostgreSQL production code may depend only on the public"
            " API and Kernel Provider SPI",
        ),
        (
            DURABLE_AUTOCONFIGURE / "pom.xml",
            {
                "compileflow-api",
                "compileflow-core",
                "compileflow-durable-api",
                "compileflow-durable-postgres",
                "compileflow-durable-runtime",
                "compileflow-durable-spi",
                "compileflow-deploy-api",
                "compileflow-spring-boot-autoconfigure",
            },
            "Durable auto-configuration may depend only on the engine contracts,"
            " Durable kernel, PostgreSQL implementation, optional Deploy composition API,"
            " and generic Spring integration internally",
        ),
        (
            DURABLE_STARTER / "pom.xml",
            {
                "compileflow-durable-spring-boot-autoconfigure",
                "compileflow-spring-boot-starter",
            },
            "Provider-neutral Durable starter may only aggregate the generic"
            " starter and Durable integration internally",
        ),
        (
            DURABLE_POSTGRES_STARTER / "pom.xml",
            {
                "compileflow-durable-postgres",
                "compileflow-durable-spring-boot-starter",
            },
            "Durable PostgreSQL starter may only aggregate the provider-neutral"
            " Durable starter and PostgreSQL implementation internally",
        ),
    )
    for pom, allowed_internal, message in dependency_rules:
        if not pom.is_file():
            continue
        actual_internal = {
            artifact_id
            for group_id, artifact_id, _ in dependency_coordinates(pom)
            if group_id == "com.alibaba.compileflow"
        }
        if actual_internal != allowed_internal:
            errors.append(
                f"{relative(pom)}: {message}; found "
                f"{sorted(actual_internal)}"
            )

    postgres_pom = DURABLE_POSTGRES / "pom.xml"
    if postgres_pom.is_file():
        postgres_main_internal = {
            artifact_id
            for group_id, artifact_id, scope
            in dependency_coordinates(postgres_pom)
            if group_id == "com.alibaba.compileflow" and scope != "test"
        }
        expected_postgres_internal = {
            "compileflow-api",
            "compileflow-durable-api",
            "compileflow-durable-spi",
        }
        if postgres_main_internal != expected_postgres_internal:
            errors.append(
                f"{relative(postgres_pom)} production code must depend only on "
                "compileflow-api, compileflow-durable-api, and "
                "compileflow-durable-spi among CompileFlow modules"
            )

    framework_prefixes = (
        "java.sql",
        "javax.sql",
        "org.flywaydb",
        "org.postgresql",
        "org.springframework",
    )
    errors.extend(
        check_imports(
            DURABLE_API / "src" / "main" / "java",
            framework_prefixes
            + ("com.alibaba.compileflow.durable.runtime",),
            "Durable API must remain storage- and runtime-independent",
        )
    )
    errors.extend(
        check_imports(
            DURABLE_SPI / "src" / "main" / "java",
            framework_prefixes
            + ("com.alibaba.compileflow.durable.runtime",),
            "Durable integration/provider SPI must remain framework- and runtime-independent",
        )
    )
    errors.extend(
        check_imports(
            DURABLE_RUNTIME / "src" / "main" / "java",
            framework_prefixes,
            "Durable runtime must remain storage-framework-independent",
        )
    )
    errors.extend(
        check_imports(
            DURABLE_POSTGRES / "src" / "main" / "java",
            ("com.alibaba.compileflow.durable.runtime",),
            "Durable PostgreSQL Provider must depend on SPI rather than Runtime",
        )
    )
    errors.extend(
        check_imports(
            AUTOCONFIGURE / "src" / "main" / "java",
            ("com.alibaba.compileflow.durable",),
            "generic Spring auto-configuration must not activate Durable",
        )
    )

    generic_pom = AUTOCONFIGURE / "pom.xml"
    if generic_pom.is_file():
        for group_id, artifact_id, _ in dependency_coordinates(generic_pom):
            if (
                group_id == "com.alibaba.compileflow"
                and artifact_id.startswith("compileflow-durable")
            ):
                errors.append(
                    f"{relative(generic_pom)} must not depend on "
                    f"{artifact_id}"
                )

    durable_autoconfigure_root = (
        DURABLE_AUTOCONFIGURE / "src" / "main" / "java"
    )
    durable_autoconfigure_package = (
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure"
    )
    for path in sorted(durable_autoconfigure_root.rglob("*.java")):
        match = PACKAGE_RE.search(read_text(path))
        if match is None or not (
            match.group(1) == durable_autoconfigure_package
            or match.group(1).startswith(
                durable_autoconfigure_package + "."
            )
        ):
            errors.append(
                f"{relative(path)} must use the Durable-owned Spring "
                "auto-configuration package"
            )
    return errors


IMPORT_RE = re.compile(r"(?m)^\s*import\s+(?:static\s+)?([^;]+);")
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([^;]+);")
PUBLIC_TOP_LEVEL_TYPE_RE = re.compile(
    r"(?m)^public\s+"
    r"(?:(?:abstract|final|sealed|non-sealed|strictfp)\s+)*"
    r"(?:class|interface|enum|record|@interface)\s+"
    r"([A-Za-z_$][A-Za-z0-9_$]*)"
)
WORKBENCH_SERVER_PACKAGE = "com.alibaba.compileflow.workbench.server."


def java_package(path: Path) -> str | None:
    """Return the declared Java package without coupling checks to source layout."""
    match = PACKAGE_RE.search(read_text(path))
    return match.group(1) if match is not None else None


def has_internal_package_segment(path: Path, package_name: str | None) -> bool:
    """Return whether a Java source uses the forbidden generic package segment."""
    return "internal" in path.parts or (
        package_name is not None and "internal" in package_name.split(".")
    )


def check_no_internal_java_packages() -> list[str]:
    """Require every Java package to communicate a concrete architectural responsibility."""
    errors: list[str] = []
    for path in iter_repository_text_files():
        if path.suffix != ".java":
            continue
        package_name = java_package(path)
        if has_internal_package_segment(path.relative_to(ROOT), package_name):
            errors.append(
                f"{relative(path)} uses the forbidden generic package segment 'internal'"
            )
    return errors


def check_imports(source_root: Path, forbidden_prefixes: tuple[str, ...], rule: str) -> list[str]:
    """Reject Java imports that cross a declared package boundary."""
    errors: list[str] = []
    if not source_root.is_dir():
        return [f"{relative(source_root)} is required"]
    for path in sorted(source_root.rglob("*.java")):
        for imported_name in IMPORT_RE.findall(read_text(path)):
            if imported_name.startswith(forbidden_prefixes):
                errors.append(f"{relative(path)} imports {imported_name}: {rule}")
    return errors


def check_java_package_boundaries() -> list[str]:
    """Enforce package-level boundaries not represented by the Maven graph."""
    errors: list[str] = []
    errors.extend(
        check_imports(
            DEPLOY_API / "src" / "main" / "java",
            (
                "com.alibaba.compileflow.engine.core",
                "com.alibaba.compileflow.deploy.control",
                "com.alibaba.compileflow.deploy.integration",
                "com.alibaba.compileflow.deploy.runtime",
                "com.alibaba.nacos",
                "com.zaxxer.hikari",
                "jakarta.persistence",
                "java.sql",
                "javax.sql",
                "org.flywaydb",
                "org.postgresql",
                "org.springframework",
            ),
            "deploy-api must remain independent of engine and deployment implementations",
        )
    )
    errors.extend(
        check_imports(
            DEPLOY_RUNTIME / "src" / "main" / "java",
            (
                "com.alibaba.compileflow.deploy.control",
                "com.alibaba.compileflow.workbench.server",
            ),
            "deploy-runtime must not depend on control-plane or HTTP-adapter implementations",
        )
    )
    errors.extend(
        check_imports(
            DEPLOY_CONTROL_PLANE / "src" / "main" / "java",
            ("com.alibaba.compileflow.workbench.server",),
            "deployment control plane must not depend on the HTTP adapter",
        )
    )
    errors.extend(
        check_imports(
            SERVER / "src" / "main" / "java",
            ("com.alibaba.compileflow.deploy.control.repository",),
            "server must use the deploy facade instead of repositories",
        )
    )
    return errors


def check_java_source_layout() -> list[str]:
    """Keep Java declarations and tracked paths aligned with the source layout."""
    errors: list[str] = []
    source_markers = (("src", "main", "java"), ("src", "test", "java"))
    actual_java_paths: list[str] = []
    for path in iter_repository_text_files():
        if path.suffix != ".java":
            continue
        parts = path.relative_to(ROOT).parts
        marker_index = next(
            (
                index
                for marker in source_markers
                for index in range(len(parts) - len(marker) + 1)
                if parts[index:index + len(marker)] == marker
            ),
            None,
        )
        if marker_index is None:
            continue
        actual_java_paths.append(relative(path))
        source_start = marker_index + 3
        expected_directory = Path(*parts[source_start:-1])
        text = read_text(path)
        match = PACKAGE_RE.search(text)
        if match is None:
            errors.append(f"{relative(path)} must declare its Java package")
            continue
        declared_directory = Path(*match.group(1).split("."))
        if expected_directory != declared_directory:
            errors.append(
                f"{relative(path)} declares package {match.group(1)} but its "
                f"source path requires {'.'.join(expected_directory.parts)}"
            )
        public_type_name = public_top_level_type_name(text)
        if public_type_name is not None and path.stem != public_type_name:
            errors.append(
                f"{relative(path)} declares public type {public_type_name} "
                "but the source filename must match it exactly"
            )

    for tracked_path, actual_path in find_case_mismatched_paths(
        tracked_java_source_paths(), actual_java_paths
    ):
        errors.append(
            f"Git tracks {tracked_path} but the working tree uses {actual_path}; "
            "Java source path case must match exactly"
        )
    return errors


def public_top_level_type_name(source: str) -> str | None:
    """Return the repository-style top-level public Java type, if present."""
    match = PUBLIC_TOP_LEVEL_TYPE_RE.search(source)
    return match.group(1) if match is not None else None


def tracked_java_source_paths() -> tuple[str, ...]:
    """Return Java source paths from the Git index when the checkout has one."""
    result = subprocess.run(
        ("git", "ls-files", "--", "*.java"),
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return ()
    return tuple(
        path
        for path in result.stdout.splitlines()
        if "/src/main/java/" in path or "/src/test/java/" in path
    )


def find_case_mismatched_paths(
    tracked_paths: Iterable[str], actual_paths: Iterable[str]
) -> tuple[tuple[str, str], ...]:
    """Find index/worktree path pairs that differ only by character case."""
    actual_by_casefold: dict[str, list[str]] = {}
    for path in actual_paths:
        actual_by_casefold.setdefault(path.casefold(), []).append(path)

    mismatches: list[tuple[str, str]] = []
    for tracked_path in tracked_paths:
        matches = actual_by_casefold.get(tracked_path.casefold(), ())
        if tracked_path not in matches and len(matches) == 1:
            mismatches.append((tracked_path, matches[0]))
    return tuple(sorted(mismatches))


def check_library_split_packages() -> list[str]:
    """Keep every production Java package owned by exactly one library artifact."""
    errors: list[str] = []
    owners: dict[str, set[str]] = {}
    for module in LIBRARY_MODULES:
        source_root = module / "src" / "main" / "java"
        if not source_root.is_dir():
            continue
        for path in sorted(source_root.rglob("*.java")):
            match = PACKAGE_RE.search(read_text(path))
            if match is None:
                continue
            owners.setdefault(match.group(1), set()).add(relative(module))
    for package_name, modules in sorted(owners.items()):
        if len(modules) > 1:
            errors.append(
                f"production Java package {package_name} is split across "
                f"library artifacts: {sorted(modules)}"
            )
    return errors


def check_workbench_server_package_graph() -> list[str]:
    """Keep Workbench Server package slices and application roles explicit."""
    errors: list[str] = []
    source_root = SERVER / "src" / "main" / "java"
    edges: dict[str, set[str]] = {}
    restricted_targets = {
        "api": {"config"},
        "config": set(),
        "security": {"api", "config"},
    }

    for path in sorted(source_root.rglob("*.java")):
        if path.stem.endswith("Store"):
            errors.append(
                f"{relative(path)} must use Repository for application collection "
                "persistence; Store is reserved for atomic kernel persistence ports"
            )
        text = read_text(path)
        package_match = PACKAGE_RE.search(text)
        if package_match is None:
            continue
        package_name = package_match.group(1)
        if not package_name.startswith(WORKBENCH_SERVER_PACKAGE):
            continue
        source_slice = package_name[len(WORKBENCH_SERVER_PACKAGE):].split(".", 1)[0]
        edges.setdefault(source_slice, set())
        for imported_name in IMPORT_RE.findall(text):
            if not imported_name.startswith(WORKBENCH_SERVER_PACKAGE):
                continue
            target_slice = imported_name[len(WORKBENCH_SERVER_PACKAGE):].split(".", 1)[0]
            if target_slice == source_slice:
                continue
            edges[source_slice].add(target_slice)
            allowed = restricted_targets.get(source_slice)
            if allowed is not None and target_slice not in allowed:
                errors.append(
                    f"{relative(path)} imports {imported_name}: {source_slice} "
                    f"must not depend on feature package {target_slice}"
                )

    visited: set[str] = set()
    active: list[str] = []

    def visit(node: str) -> None:
        if node in active:
            cycle = active[active.index(node):] + [node]
            errors.append(
                "Workbench Server package dependency cycle: " + " -> ".join(cycle)
            )
            return
        if node in visited:
            return
        active.append(node)
        for target in sorted(edges.get(node, set())):
            visit(target)
        active.pop()
        visited.add(node)

    for node in sorted(edges):
        visit(node)
    return errors


def production_migrations() -> list[Path]:
    """Return Flyway migrations shipped from main resources."""
    migrations: list[Path] = []
    for path in ROOT.rglob("V*.sql"):
        parts = path.relative_to(ROOT).parts
        if "src" not in parts or "main" not in parts or "resources" not in parts:
            continue
        resources_index = parts.index("resources")
        resource_parts = parts[resources_index + 1:]
        if resource_parts and resource_parts[0] == "db" and "migration" in resource_parts:
            migrations.append(path)
    return sorted(migrations)


def check_migration_discovery_boundaries() -> list[str]:
    """Require library migrations to be explicitly selected by a product root."""
    errors: list[str] = []
    default_root_fragment = "/src/main/resources/db/migration/"
    leaked = [
        path for path in ROOT.rglob("V*.sql")
        if default_root_fragment in path.as_posix()
    ]
    for path in leaked:
        errors.append(
            f"{relative(path)} must live outside Flyway's default discovery "
            "root; product roots must opt in to each bounded-context migration"
        )

    owners = (
        (DEPLOY_MIGRATION_OWNER, "Deploy"),
        (WORKBENCH_MIGRATION_OWNER, "Workbench"),
        (DURABLE_MIGRATION_OWNER, "Durable"),
    )
    migrations = production_migrations()
    for owner, context in owners:
        if not any(owner in path.parents for path in migrations):
            errors.append(
                f"{relative(owner)} must contain the explicitly selected "
                f"{context} Flyway migrations"
            )
    return errors


def check_deploy_migration_owner() -> list[str]:
    """Require one production owner for every deploy table."""
    errors: list[str] = []
    owner_migrations = [
        path for path in production_migrations() if DEPLOY_MIGRATION_OWNER in path.parents
    ]
    if not owner_migrations:
        errors.append(f"{relative(DEPLOY_MIGRATION_OWNER)} must contain the deploy Flyway migration")
        return errors

    owner_text = "\n".join(read_text(path) for path in owner_migrations)
    for table in sorted(DEPLOY_TABLES):
        create_pattern = re.compile(rf"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{table}\b", re.IGNORECASE)
        if not create_pattern.search(owner_text):
            errors.append(f"{relative(DEPLOY_MIGRATION_OWNER)} must create {table}")

    for marker in (
        "CONSTRAINT ck_rollout_event_transition",
        "sequence = 1 AND from_phase IS NULL",
        "sequence > 1 AND from_phase = 'IN_PROGRESS'",
    ):
        if marker not in owner_text:
            errors.append(f"{relative(DEPLOY_MIGRATION_OWNER)} must enforce rollout event transition marker {marker}")

    for path in production_migrations():
        if DEPLOY_MIGRATION_OWNER in path.parents:
            continue
        text = read_text(path)
        for table in sorted(DEPLOY_TABLES):
            if re.search(rf"\b{table}\b", text):
                errors.append(
                    f"{relative(path)} references deploy-owned table {table}; "
                    "compileflow-deploy-control-plane is the sole migration owner"
                )
    return errors


def check_project_storage_boundaries() -> list[str]:
    """Keep storage out of the Engine and schemas inside one context owner."""
    errors: list[str] = []
    persistence_prefixes = (
        "com.zaxxer.hikari",
        "jakarta.persistence",
        "javax.persistence",
        "javax.sql",
        "org.flywaydb",
        "org.postgresql",
        "org.springframework.data.jpa",
        "org.springframework.jdbc",
        "org.springframework.orm.jpa",
    )
    for module in ENGINE_MODULES:
        errors.extend(
            check_imports(
                module / "src" / "main" / "java",
                persistence_prefixes,
                "Engine and format modules must remain persistence-free",
            )
        )

    for pom in sorted(ROOT.rglob("pom.xml")):
        if "target" in pom.parts:
            continue
        for group_id, artifact_id, scope in all_dependency_coordinates(pom):
            if group_id == "com.h2database" and scope != "test":
                errors.append(
                    f"{relative(pom)} must keep H2 test-scoped; found "
                    f"{artifact_id} with scope {scope}"
                )

    if (SERVER / "pom.xml").is_file():
        server_dependencies = all_dependency_coordinates(SERVER / "pom.xml")
        server_postgres = {
            (group_id, artifact_id, scope)
            for group_id, artifact_id, scope in server_dependencies
            if artifact_id in {"postgresql", "flyway-database-postgresql"}
        }
        expected_postgres = {
            ("org.postgresql", "postgresql", "runtime"),
            ("org.flywaydb", "flyway-database-postgresql", "compile"),
        }
        if server_postgres != expected_postgres:
            errors.append(
                f"{relative(SERVER / 'pom.xml')} must expose the exact "
                "PostgreSQL Workbench runtime boundary; found "
                f"{sorted(server_postgres)}"
            )
        for group_id, artifact_id, scope in server_dependencies:
            if (
                group_id == "com.alibaba.compileflow"
                and artifact_id.startswith("compileflow-durable")
                and scope != "test"
            ):
                errors.append(
                    f"{relative(SERVER / 'pom.xml')} must not merge the "
                    f"Workbench async queue with Durable: {artifact_id}"
                )

    owned_tables = (
        (WORKBENCH_MIGRATION_OWNER, WORKBENCH_TABLES, "Workbench"),
    )
    migrations = production_migrations()
    for owner, tables, context in owned_tables:
        owner_migrations = [path for path in migrations if owner in path.parents]
        if not owner_migrations:
            errors.append(f"{relative(owner)} must contain {context} migrations")
            continue
        owner_text = "\n".join(read_text(path) for path in owner_migrations)
        for table in sorted(tables):
            create_pattern = re.compile(
                rf"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{table}\b",
                re.IGNORECASE,
            )
            if not create_pattern.search(owner_text):
                errors.append(f"{relative(owner)} must create {table}")
            for path in migrations:
                if owner in path.parents:
                    continue
                if re.search(rf"\b{table}\b", read_text(path), re.IGNORECASE):
                    errors.append(
                        f"{relative(path)} references {context}-owned table "
                        f"{table}; cross-context SQL is forbidden"
                    )
        if context == "Workbench":
            for marker in (
                "CONSTRAINT ck_async_invocation_lifecycle",
                "status <> 'succeeded' OR error_code IS NULL",
                "status <> 'dead_letter' OR error_code IS NOT NULL",
                "available_at >= created_at",
                "completed_at >= started_at AND completed_at >= updated_at",
            ):
                if marker not in owner_text:
                    errors.append(f"{relative(owner)} must enforce async invocation marker {marker}")

    durable_migrations = [
        path for path in migrations if DURABLE_MIGRATION_OWNER in path.parents
    ]
    if not durable_migrations:
        errors.append(
            f"{relative(DURABLE_MIGRATION_OWNER)} must contain Durable migrations"
        )
    else:
        durable_text = "\n".join(read_text(path) for path in durable_migrations)
        if not re.search(
            r"\bCREATE\s+TABLE\s+(?:public\.)?cf_durable_[a-z0-9_]+\b",
            durable_text,
            re.IGNORECASE,
        ):
            errors.append(
                f"{relative(DURABLE_MIGRATION_OWNER)} must own cf_durable_*"
            )
        for path in migrations:
            if DURABLE_MIGRATION_OWNER in path.parents:
                continue
            if re.search(r"\bcf_durable_[a-z0-9_]+\b", read_text(path), re.IGNORECASE):
                errors.append(
                    f"{relative(path)} references Durable-owned tables; "
                    "cross-context SQL is forbidden"
                )
    return errors


def check_workbench_schema_admission() -> list[str]:
    """Keep Workbench DDL authority separate from mandatory schema admission."""
    errors: list[str] = []
    configuration = (
        SERVER
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "workbench"
        / "server"
        / "config"
        / "WorkbenchSchemaConfiguration.java"
    )
    required_configuration_markers = (
        "FlywayMigrationStrategy",
        "properties.getDatabase().isMigrate()",
        "flyway.migrate();",
        "flyway.validate();",
        "flyway.info().pending();",
        "spring.flyway.enabled",
    )
    if not configuration.is_file():
        errors.append(f"{relative(configuration)} must own Workbench schema admission")
    else:
        text = read_text(configuration)
        for marker in required_configuration_markers:
            if marker not in text:
                errors.append(
                    f"{relative(configuration)} must retain schema-admission marker: {marker}"
                )

    application = SERVER / "src" / "main" / "resources" / "application.yml"
    required_application_markers = (
        "ddl-auto: validate",
        "classpath:db/compileflow-deploy/migration",
        "classpath:db/compileflow-workbench-server/migration",
        "clean-disabled: true",
    )
    if not application.is_file():
        errors.append(f"{relative(application)} must configure product-root Flyway ownership")
    else:
        text = read_text(application)
        for marker in required_application_markers:
            if marker not in text:
                errors.append(
                    f"{relative(application)} must retain schema marker: {marker}"
                )

    contract = (
        SERVER
        / "src"
        / "test"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "workbench"
        / "server"
        / "persistence"
        / "WorkbenchExternalSchemaAdmissionTest.java"
    )
    if not contract.is_file():
        errors.append(
            f"{relative(contract)} must prove externally migrated startup admission"
        )
    workflow = ROOT / ".github" / "workflows" / "workbench-server-ci.yml"
    if not workflow.is_file() or "WorkbenchExternalSchemaAdmissionTest" not in read_text(
        workflow
    ):
        errors.append(
            f"{relative(workflow)} must run Workbench schema admission on PostgreSQL"
        )
    elif workflow.is_file():
        workflow_text = read_text(workflow)
        for marker in (
            "postgres: '16.15'",
            "postgres: '17.11'",
            "postgres: '18.6'",
            "Reject missing, failed, or skipped PostgreSQL evidence",
            "compileflow-integration-tests/**",
            "DeploymentRuntimeChainIntegrationTest",
            "ProcessDraftServicePersistenceTest",
            "ProcessOptimisticLockPersistenceTest",
            "SPRING_FLYWAY_LOCATIONS",
            "compileflow.test.deploy.postgres.required=true",
            "verify_workbench_postgres_evidence.py",
            "test_verify_workbench_postgres_evidence.py",
        ):
            if marker not in workflow_text:
                errors.append(
                    f"{relative(workflow)} must retain Workbench/Deploy "
                    f"PostgreSQL compatibility evidence marker: {marker}"
                )
    verifier = ROOT / "scripts" / "verify_workbench_postgres_evidence.py"
    if not verifier.is_file():
        errors.append(f"{relative(verifier)} must verify PostgreSQL test evidence")
    else:
        verifier_text = read_text(verifier)
        for marker in (
            "transactionalOutboxConvergesTwoNodesAcrossCanaryPromotionAndRollback",
            "reconcilerRepublishesTheAuthoritativeAliasForLateNodes",
            "processCallRemainsBoundToThePublishedChildVersionOnBothNodes",
            "concurrentImmutableVersionWritesPreserveOneExactIdentity",
            "expiredOutboxClaimIsReclaimedAndFencesThePreviousWorker",
            "parse_junit_report",
            'counts[name] for name in ("failures", "errors", "skipped")',
        ):
            if marker not in verifier_text:
                errors.append(
                    f"{relative(verifier)} must retain PostgreSQL evidence marker: {marker}"
                )
    return errors


def check_legacy_deploy_names() -> list[str]:
    """Prevent the removed deploy-common boundary from returning."""
    errors: list[str] = []
    for path in iter_repository_text_files():
        text = read_text(path)
        for marker in LEGACY_DEPLOY_MARKERS:
            if marker in text:
                errors.append(f"{relative(path)} contains removed deploy boundary name: {marker}")
    return errors


def check_removed_rollout_automation_contract() -> list[str]:
    """Keep deferred automatic rollout controls out of public documentation and UI contracts."""
    errors: list[str] = []
    guarded_roots = (
        ROOT / "docs",
        ROOT / "compileflow-deploy" / "docs",
        ROOT / "compileflow-workbench" / "apps" / "web" / "src" / "operate",
        ROOT / "compileflow-workbench" / "packages" / "shared",
    )
    for guarded_root in guarded_roots:
        if not guarded_root.exists():
            continue
        for path in sorted(guarded_root.rglob("*")):
            if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
                continue
            if "__tests__" in path.parts or path.name.endswith((".test.ts", ".test.tsx")):
                continue
            text = read_text(path)
            for field in REMOVED_AUTOMATION_FIELDS:
                if field in text:
                    errors.append(
                        f"{relative(path)} documents removed automatic rollout field: {field}"
                    )
    return errors


def check_legacy_routing_key_name() -> list[str]:
    """Keep one routing-key name across Java, HTTP, Workbench, and docs."""
    errors: list[str] = []
    for path in iter_repository_text_files():
        text = read_text(path)
        for marker in LEGACY_ROUTING_KEY_MARKERS:
            if marker in text:
                errors.append(
                    f"{relative(path)} contains removed routing-key name: {marker}"
                )
    return errors


def check_routing_key_persistence_boundary() -> list[str]:
    """Permit admission inputs while preventing their raw async persistence."""
    errors: list[str] = []
    service = (
        SERVER
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "workbench"
        / "server"
        / "execution"
        / "AsyncInvocationService.java"
    )
    persisted_routing = service.with_name("PersistedInvocationRouting.java")
    routing_request = (
        ROOT
        / "compileflow-workbench-server"
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "workbench"
        / "server"
        / "execution"
        / "ExecutionRoutingRequest.java"
    )
    if service.is_file():
        service_text = read_text(service)
        validation_index = service_text.find("requestedRouting(processCode, request.routing())")
        admission_index = service_text.find("pinAliasRouting(")
        persistence_index = service_text.find("repository.save(")
        if (
            validation_index < 0
            or admission_index < 0
            or persistence_index < 0
            or validation_index > persistence_index
            or admission_index > persistence_index
            or "routing.validatePersistedInvocation()" not in service_text
            or "new AliasRoutingOptions(" not in service_text
        ):
            errors.append(
                f"{relative(service)} must validate and pin Alias routing before persistence"
            )
    else:
        errors.append(f"{relative(service)} is required")
    if not persisted_routing.is_file():
        errors.append(f"{relative(persisted_routing)} is required")
    else:
        persisted_text = read_text(persisted_routing)
        for field in ('"routingKey"', '"attributes"'):
            if field in persisted_text:
                errors.append(
                    f"{relative(persisted_routing)} must not persist raw Alias admission input: {field}"
                )
    if not routing_request.is_file():
        errors.append(f"{relative(routing_request)} is required")
    else:
        routing_text = read_text(routing_request)
        if (
            "Map<String, String> attributes" not in routing_text
            or "new AliasRoutingOptions(routingKey, attributes)" not in routing_text
            or "validatePersistedInvocation()" not in routing_text
        ):
            errors.append(
                f"{relative(routing_request)} must expose bounded Alias admission inputs"
            )
    return errors


def check_workbench_product_boundary() -> list[str]:
    """Keep production Workbench independent from the development-only Node mock."""
    errors: list[str] = []
    root_version = ET.parse(ROOT / "pom.xml").getroot().findtext(
        "m:version", default="", namespaces=MAVEN_NAMESPACE
    )
    for package_json in (
        ROOT / "compileflow-workbench" / "package.json",
        ROOT / "compileflow-workbench" / "apps" / "web" / "package.json",
        ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / "package.json",
    ):
        if not package_json.is_file():
            errors.append(f"{relative(package_json)} is required")
            continue
        package_version = json.loads(read_text(package_json)).get("version")
        if package_version != root_version:
            errors.append(
                f"{relative(package_json)} version {package_version!r} must match "
                f"the project version {root_version!r}"
            )

    dev_gateway = ROOT / "compileflow-workbench" / "apps" / "dev-gateway"
    config = dev_gateway / "src" / "config.ts"
    if not config.is_file():
        errors.append(f"{relative(config)} is required")
    else:
        text = read_text(config)
        for fragment in ("host: '127.0.0.1'", "normalized === 'production'"):
            if fragment not in text:
                errors.append(
                    f"{relative(config)} must enforce the development-only boundary: {fragment}"
                )

    forbidden_gateway_markers = (
        "COMPILEFLOW_WORKBENCH_SERVER_AUTHENTICATION_API_KEY",
        "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY",
        "COMPILEFLOW_BRIDGE_",
        "UPSTREAM_URL",
    )
    for path in sorted((dev_gateway / "src").rglob("*.ts")):
        text = read_text(path)
        for marker in forbidden_gateway_markers:
            if marker in text:
                errors.append(
                    f"{relative(path)} must not contain production proxy or credential marker: "
                    f"{marker}"
                )

    vite_config = ROOT / "compileflow-workbench" / "apps" / "web" / "vite.config.ts"
    if not vite_config.is_file():
        errors.append(f"{relative(vite_config)} is required")
    elif "host: '127.0.0.1'" not in read_text(vite_config):
        errors.append(
            f"{relative(vite_config)} must not expose its development proxy "
            "outside the loopback interface"
        )

    for compose_name in ("docker-compose.yml", "docker-compose.all-in-one.yml"):
        compose = ROOT / "compileflow-workbench" / compose_name
        if not compose.is_file():
            errors.append(f"{relative(compose)} is required")
            continue
        text = read_text(compose)
        if "dev-gateway" in text:
            errors.append(
                f"{relative(compose)} must not package the development gateway"
            )

    web_config = ROOT / "compileflow-workbench" / "docker" / "nginx.web.conf"
    if not web_config.is_file():
        errors.append(f"{relative(web_config)} is required")
    else:
        text = read_text(web_config)
        for marker in ("location /api/", "X-API-Key", "workbench-server"):
            if marker in text:
                errors.append(
                    f"{relative(web_config)} must remain static-only: {marker}"
                )

    split_compose = ROOT / "compileflow-workbench" / "docker-compose.yml"
    if split_compose.is_file() and "local-gateway:" not in read_text(split_compose):
        errors.append(
            f"{relative(split_compose)} must isolate loopback credential injection "
            "from the Web image"
        )

    for workflow_name in ("workbench-ci.yml", "release.yml"):
        workflow = ROOT / ".github" / "workflows" / workflow_name
        if not workflow.is_file():
            errors.append(f"{relative(workflow)} is required")
            continue
        text = read_text(workflow)
        setup_count = text.count("pnpm/action-setup@")
        package_file_count = text.count(
            "package_json_file: compileflow-workbench/package.json"
        )
        if setup_count != package_file_count:
            errors.append(
                f"{relative(workflow)} must point every pnpm/action-setup step "
                "at the Workbench packageManager declaration"
            )

    server_pom = SERVER / "pom.xml"
    if not server_pom.is_file():
        errors.append(f"{relative(server_pom)} is required")
    else:
        text = read_text(server_pom)
        for fragment, purpose in (
            ("<id>workbench-bundled</id>", "declare the bundled packaging profile"),
            (
                "<requireFilesExist>",
                "fail when the compiled Workbench Web distribution is absent",
            ),
            (
                "compileflow-workbench-all-in-one-${project.version}",
                "name the bundled release artifact explicitly",
            ),
        ):
            if fragment not in text:
                errors.append(f"{relative(server_pom)} must {purpose}")
        internal_dependencies = {
            artifact_id
            for group_id, artifact_id, _ in dependency_coordinates(server_pom)
            if group_id == "com.alibaba.compileflow"
        }
        if "compileflow-spring-boot-starter" in internal_dependencies:
            errors.append(
                f"{relative(server_pom)} must compose product modules directly "
                "instead of depending on the user-facing starter"
            )
        for artifact_id in ("compileflow-tbbpm", "compileflow-bpmn"):
            if artifact_id not in internal_dependencies:
                errors.append(
                    f"{relative(server_pom)} must declare the runtime format "
                    f"provider {artifact_id} directly"
                )

    docker_directory = ROOT / "compileflow-workbench" / "docker"
    dockerfiles = (
        docker_directory / "Dockerfile.workbench-server",
        docker_directory / "Dockerfile.all-in-one",
    )
    for dockerfile in dockerfiles:
        if not dockerfile.is_file():
            errors.append(f"{relative(dockerfile)} is required")
            continue
        text = read_text(dockerfile)
        if "COPY LICENSE NOTICE ./" not in text:
            errors.append(
                f"{relative(dockerfile)} must include repository license notices "
                "in Maven-built artifacts"
            )
        for module in root_reactor_modules():
            full_copy = f"COPY {module} {module}"
            pom_copy = f"COPY {module}/pom.xml {module}/pom.xml"
            if full_copy not in text and pom_copy not in text:
                errors.append(
                    f"{relative(dockerfile)} must copy {module} or its POM so "
                    "Maven can construct the root reactor"
                )

    bundled = docker_directory / "Dockerfile.all-in-one"
    if bundled.is_file():
        text = read_text(bundled)
        if "-Pworkbench-bundled" not in text:
            errors.append(
                f"{relative(bundled)} must use the bundled Workbench packaging profile"
            )
        if "compileflow-workbench-all-in-one-*.jar" not in text:
            errors.append(
                f"{relative(bundled)} must run the bundled Workbench artifact"
            )
        if 'ENTRYPOINT ["java", "-jar", "app.jar"]' not in text:
            errors.append(
                f"{relative(bundled)} must run one Java application process"
            )
    return errors


def check_workbench_web_dependency_directions() -> list[str]:
    """Keep Shared below domains and prevent direct domain-to-domain imports."""
    errors: list[str] = []
    domains = {"authoring", "learn", "operate", "settings"}
    import_pattern = re.compile(
        r"(?:\bfrom\s+|\bimport\s*\(\s*)['\"]([^'\"]+)['\"]"
    )
    for path in sorted(
        source
        for suffix in ("*.ts", "*.tsx")
        for source in WORKBENCH_WEB_SOURCE.rglob(suffix)
    ):
        relative_path = path.relative_to(WORKBENCH_WEB_SOURCE)
        if (
            "__tests__" in relative_path.parts
            or path.name.endswith((".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx"))
        ):
            continue
        source_owner = relative_path.parts[0]
        text = read_text(path)
        for match in import_pattern.finditer(text):
            specifier = match.group(1)
            target_owner: str | None = None
            if specifier.startswith("@/"):
                target_owner = specifier[2:].split("/", 1)[0]
            elif specifier.startswith("."):
                target = (path.parent / specifier).resolve()
                try:
                    target_owner = target.relative_to(WORKBENCH_WEB_SOURCE.resolve()).parts[0]
                except ValueError:
                    continue
            if target_owner is None:
                continue
            forbidden = source_owner == "shared" and target_owner in {
                "app",
                "authoring",
                "learn",
                "operate",
                "settings",
                "shell",
            }
            forbidden = forbidden or (
                source_owner in domains
                and target_owner in domains
                and source_owner != target_owner
            )
            if forbidden:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{relative(path)}:{line} must not import {specifier!r}; "
                    f"forbidden Workbench dependency direction: {source_owner} -> {target_owner}"
                )
    return errors


def check_workbench_public_terminology() -> list[str]:
    """Freeze Process as the Workbench resource and wire-contract vocabulary."""
    errors: list[str] = []
    roots = (
        SERVER / "src" / "main",
        SERVER / "src" / "test",
        ROOT / "compileflow-workbench" / "apps" / "web" / "src",
        ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / "src",
    )
    contract = ROOT / "docs" / "specs" / "openapi" / "compileflow-workbench-server.openapi.json"
    candidates = [contract] if contract.is_file() else []
    for root in roots:
        if root.is_dir():
            candidates.extend(
                path
                for path in root.rglob("*")
                if path.is_file() and path.suffix in TEXT_SUFFIXES
            )

    forbidden_markers = (
        "/api/flows",
        "top-flows",
        "flowCode",
        "flow_code",
        "cf_flow",
        ".server.flow",
        "FlowController",
        "FlowService",
        "FlowDefinitionResponse",
    )
    for path in sorted(set(candidates)):
        text = read_text(path)
        for marker in forbidden_markers:
            if marker in text:
                errors.append(
                    f"{relative(path)} must use Process as the Workbench public "
                    f"resource vocabulary; found {marker!r}"
                )
        retired_workbench_terms = (
            "DeploymentEnvironment",
            "DeleteExecutionLogs",
            "LogDelete",
            "VersionHistory",
            "saveVersion(",
            "restoreVersion(",
            "getAllVersions(",
            "routedProcesses",
            "countRoutedProcesses",
        )
        for term in retired_workbench_terms:
            if term in text:
                errors.append(
                    f"{relative(path)} reintroduces retired Workbench vocabulary: {term!r}"
                )
        if re.search(r"['\"]\/api\/logs(?:['\"/?])", text):
            errors.append(
                f"{relative(path)} must use the /api/execution-logs HTTP resource"
            )
    return errors


def check_api_freeze_contracts() -> list[str]:
    """Keep the canonical identity, opaque-handle, and public-view API decisions frozen."""
    errors: list[str] = []

    contracts: tuple[tuple[Path, tuple[str, ...], tuple[str, ...]], ...] = (
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "ProcessExecution.java",
            (
                "private final ProcessRef.Version processVersion;",
                "public ProcessRef.Version getProcessVersion()",
            ),
            ("requestedRef", "effectiveAlias", "aliasRevision", "aliasTarget"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "AliasRoutingOptions.java",
            (
                "public record AliasRoutingOptions(String routingKey, Map<String, String> attributes)",
                "private static final int MAX_ROUTING_KEY_CHARACTERS = 512;",
                "private static final int MAX_ATTRIBUTE_ENTRIES = 32;",
                'private static final String ENGINE_METADATA_PREFIX = "__cf_";',
                'ProcessText.requireUnicode(routingKey, "routingKey");',
            ),
            ("ProcessAliasRoutingPolicy", "ProcessAliasRoutingContext"),
        ),
        (
            ROOT / "compileflow-core" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "core" / "routing" / "DeterministicAliasSelector.java",
            (
                "public final class DeterministicAliasSelector",
                'private static final byte[] MAGIC = "CFROUTE1"',
                "private static final int BUCKET_COUNT = 10_000;",
                "MessageDigest.getInstance(\"SHA-256\")",
                "effective routing key is required for candidate selection",
            ),
            ("ProcessAliasRoutingPolicy", "ProcessAliasRoutingContext"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "ProcessRef.java",
            (
                "public sealed interface ProcessRef permits ProcessRef.Version, ProcessRef.Alias",
                "record Version(",
                "record Alias(",
            ),
            ("ProcessRef.Code", "record Code(", "static Code code("),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "ProcessDefinition.java",
            ("static Classpath classpath(String code, String resourcePath)",),
            ("String namespace();",),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "routing" / "ProcessAliasRoute.java",
            (
                "public record ProcessAliasRoute(",
                "ProcessRef.Alias alias",
                "ProcessRef.Version stableVersion",
                "AliasTargeting targeting",
                "long revision",
            ),
            ("String actor", "Instant updatedAt", "dependencies"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "routing" / "ProcessAliasRouteSource.java",
            ("Optional<ProcessAliasRoute> find(ProcessRef.Alias alias);",),
            ("priority", "fallback"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "ProcessComponentResolver.java",
            ("<T> T resolve(String name, Class<T> requiredType);",),
            ("Object resolve(String name)",),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "ProcessEnginePluginContext.java",
            (
                "ProcessEnginePluginContext eventListener(ProcessEventListener listener);",
                "ProcessEnginePluginContext scriptExecutor(ScriptExecutor executor);",
                "ProcessEnginePluginContext aliasTargetingPolicy(ProcessAliasTargetingPolicy policy);",
                "ProcessEnginePluginContext retryPolicy(String name, RetryPolicy policy);",
                "ProcessEnginePluginContext failureHandler(String name, FailureHandler handler);",
            ),
            ("traceIdProvider(", "componentResolver(", "contextPropagator(", "aliasRouteSource("),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "execution" / "ProcessContextPropagator.java",
            (
                "public interface ProcessContextPropagator",
                "Snapshot capture();",
                "Scope open();",
                "interface Scope extends AutoCloseable",
                "never persisted or restored by",
            ),
            ("Map<String, Object>", "ThreadLocalAccessor", "io.micrometer"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "CompileFlowException.java",
            (
                "public class CompileFlowException extends RuntimeException",
                "public CompileFlowException(ErrorCode errorCode, String message)",
                "public CompileFlowException(ErrorCode errorCode, String message, Throwable cause)",
                "private final ErrorCode errorCode;",
            ),
            (
                "BusinessException",
                "SystemException",
                "isBusinessException()",
                "isSystemException()",
                "getTimestamp()",
                "private final long timestamp;",
            ),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "script" / "ScriptExecutor.java",
            ("Object evaluate(ScriptProgram script, Map<String, Object> context);",),
            ("ScriptEvaluationLimits", "ProcessCompilationConfig", "ProcessScriptConfig", "QlConfig"),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "event" / "ProcessEventListener.java",
            (
                "Observes best-effort process engine lifecycle telemetry.",
                "A listener must never be the authority for business",
            ),
            (),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "observability" / "TraceIdProvider.java",
            ("lightweight correlation identifiers", "not a tracing, span, baggage"),
            (),
        ),
        (
            ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
            / "compileflow" / "engine" / "spi" / "execution" / "ActionExecutionContext.java",
            ("The context is thread-confined.", "never into asynchronous tasks"),
            (),
        ),
        (
            DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "api" / "DurableProcessEngine.java",
            (
                "start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input)",
                "start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input)",
                "start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input)",
                "start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input, AliasRoutingOptions options)",
                "completeWait(WaitToken waitToken, Map<String, ?> result)",
                "getRun(ProcessRunId runId)",
                "listRuns(ProcessRunQuery query)",
                "getRunResult(ProcessRunId runId)",
            ),
            (
                "extends ProcessEngine",
                "start(ProcessRef process",
                "start(ProcessDefinition definition",
                "start(ProcessRef.Version version",
                "start(ProcessRef.Alias alias",
                "ProcessRoutingOptions",
                "start(ProcessModelType",
                " complete(WaitToken",
                " get(ProcessRunId",
                " list(ProcessRunQuery",
                " getResult(ProcessRunId",
            ),
        ),
        (
            DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "api" / "DurableOperatorService.java",
            (
                "pauseRun(PauseRunCommand command)",
                "resumeRun(ResumeRunCommand command)",
                "resolveEffect(ResolveEffectCommand command)",
                "resolveOutboxEvent(ResolveOutboxEventCommand command)",
            ),
            ("pauseRun(ProcessRunId", "resolveEffect(ProcessRunId"),
        ),
        (
            DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "api" / "error" / "DurableErrorCode.java",
            ("PROCESS_NOT_FOUND", "PROCESS_IDENTITY_MISMATCH", "UNSUPPORTED_PROCESS"),
            ("DEFINITION_NOT_FOUND", "PREPARATION_CONFLICT", "WORKER_NOT_READY"),
        ),
        (
            DURABLE_SPI / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "spi" / "admission" / "DurableVersionDefinitionSource.java",
            (
                "Optional<VersionDefinition> find(ProcessRef.Version version)",
                "record VersionDefinition(ProcessModelType modelType, ProcessDefinition.Inline definition,",
                "Map<String, ProcessRef.Version> callBindings",
            ),
            ("Optional<ProcessDefinition.Inline>",),
        ),
        (
            DURABLE_SPI / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "spi" / "wait" / "DurableWaitDescriptionProvider.java",
            ("describeWait(DurableWaitDescriptionContext context)",),
            ("describeWait(String nodeId",),
        ),
        (
            DURABLE_SPI / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "spi" / "wait" / "DurableWaitDescriptionContext.java",
            (
                "public record DurableWaitDescriptionContext(String processCode, String semanticDigest, String nodeId,",
                "Map<String, Object> state, Map<String, Object> lexicalBindings)",
            ),
            ("DataSource", "DurableStore", "ApplicationContext", "ProcessEngine"),
        ),
        (
            DURABLE_SPI / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "durable" / "spi" / "admission" / "DurableAliasState.java",
            (
                "ProcessRef.Version stableVersion",
                "ProcessRef.Version candidateVersion",
            ),
            ("String stableVersion", "String candidateVersion"),
        ),
        (
            DEPLOY_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
            / "deploy" / "api" / "rollout" / "ProcessRollout.java",
            (
                "private ProcessRollout(Builder builder)",
                "public static Builder builder()",
                "public ProcessRef.Alias getAlias()",
                "public ProcessRef.Version getTargetVersion()",
                "public long getRolloutRevision()",
                "public Instant getCreatedAt()",
            ),
            ("public ProcessRollout(", "public long getRevision()"),
        ),
    )
    for path, required, forbidden in contracts:
        if not path.is_file():
            errors.append(f"{relative(path)} is required by the API Freeze contract")
            continue
        text = read_text(path)
        for marker in required:
            if marker not in text:
                errors.append(f"{relative(path)} must retain API Freeze marker: {marker}")
        for marker in forbidden:
            if marker in text:
                errors.append(f"{relative(path)} contains removed API Freeze marker: {marker}")

    forbidden_spi_types = (
        "ProcessAuthorizationProvider",
        "SecretResolver",
        "HumanTaskProvider",
        "MessageCorrelationProvider",
        "BusinessKeyProvider",
        "ProcessRepository",
        "VersionRepository",
        "AliasRepository",
        "DurableSerializer",
        "ObjectCodec",
        "ClockProvider",
        "TransactionManager",
        "ProcessExecutionInterceptor",
        "AroundActionInterceptor",
        "RuntimeCacheProvider",
        "CompilerProvider",
        "ProcessCallResolver",
        "VersionResolver",
        "ProcessAliasRoutingPolicy",
        "ResourceResolver",
    )
    public_source_roots = (
        ROOT / "compileflow-api" / "src" / "main" / "java",
        DEPLOY_API / "src" / "main" / "java",
        DURABLE_API / "src" / "main" / "java",
        DURABLE_SPI / "src" / "main" / "java",
    )
    for source_root in public_source_roots:
        for type_name in forbidden_spi_types:
            for path in source_root.rglob(f"{type_name}.java"):
                errors.append(f"{relative(path)} reintroduces forbidden public SPI {type_name}")

    routing_docs = (
        (ROOT / "docs" / "architecture" / "05-VERSION_ROUTING.en.md", "occurrence identity"),
        (ROOT / "docs" / "architecture" / "05-VERSION_ROUTING.zh.md", "occurrence identity"),
        (ROOT / "docs" / "en" / "troubleshooting.md", "persisted invocation ID is"),
        (ROOT / "docs" / "zh" / "troubleshooting.md", "持久化 invocation ID 作为 cohort key"),
    )
    for path, required in routing_docs:
        text = read_text(path) if path.is_file() else ""
        if required not in text:
            errors.append(f"{relative(path)} must retain occurrence-based Alias admission semantics")
        for retired in ("missing routing keys select stable", "没有 key 的 Alias 固定选择 stable"):
            if retired in text:
                errors.append(f"{relative(path)} retains retired missing-key Alias semantics")

    removed_types = (
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "DurableProcessAdminService.java",
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "model" / "RegisteredProcess.java",
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "model" / "ProcessReadinessReport.java",
    )
    for path in removed_types:
        if path.exists():
            errors.append(f"{relative(path)} must remain outside the minimal Durable public API")

    canonical_durable_surfaces = (
        DURABLE / "README.md",
        ROOT / "docs" / "en" / "api-reference.md",
        ROOT / "docs" / "zh" / "api-reference.md",
        ROOT / "docs" / "en" / "configuration.md",
        ROOT / "docs" / "zh" / "configuration.md",
        ROOT / "docs" / "architecture" / "06-SUPPORTED_SURFACES.en.md",
        ROOT / "docs" / "architecture" / "06-SUPPORTED_SURFACES.zh.md",
        ROOT / "examples" / "spring-boot-durable-postgres" / "src",
    )
    removed_durable_names = (
        "DurableProcessAdminService",
        "RegisteredProcess",
        "ProcessReadinessReport",
        "ActiveChildView",
    )
    for root in canonical_durable_surfaces:
        paths = (root,) if root.is_file() else tuple(
            path for path in sorted(root.rglob("*"))
            if path.is_file() and path.suffix in TEXT_SUFFIXES
            and not any(part in SKIP_DIRECTORIES for part in path.parts)
        )
        for path in paths:
            text = read_text(path)
            for name in removed_durable_names:
                if name in text:
                    errors.append(
                        f"{relative(path)} documents or uses removed Durable API: {name}"
                    )

    durable_runtime_sources = DURABLE_RUNTIME / "src" / "main" / "java"
    obsolete_process_call_names = (
        "ChildRequest",
        "ChildWaiting",
        "Step.Child",
        "materializeChildRequest",
        "mapChildOutput",
        "requireChildCall",
        "DURABLE_CHILD_DEFAULT_EXPRESSION_UNSUPPORTED",
        "DURABLE_CHILD_INPUT_SOURCE_UNSUPPORTED",
    )
    for path in sorted(durable_runtime_sources.rglob("*.java")):
        text = read_text(path)
        for name in obsolete_process_call_names:
            if name in text:
                errors.append(
                    f"{relative(path)} must use same-Run ProcessCall terminology, not {name}"
                )

    opaque_models = (
        "WaitToken",
        "ProcessRunCursor",
        "ProcessTimelineCursor",
        "OutboxEventCursor",
    )
    model_root = (
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "model"
    )
    for type_name in opaque_models:
        path = model_root / f"{type_name}.java"
        marker = f"record {type_name}(String value)"
        if not path.is_file() or marker not in read_text(path):
            errors.append(f"{relative(path)} must remain an opaque scalar record")

    active_wait = model_root / "ActiveWait.java"
    if not active_wait.is_file() or "String elementId" not in read_text(active_wait):
        errors.append(f"{relative(active_wait)} must expose format-neutral elementId")

    event = (
        ROOT / "compileflow-api" / "src" / "main" / "java" / "com" / "alibaba"
        / "compileflow" / "engine" / "spi" / "event" / "ProcessEvent.java"
    )
    event_text = read_text(event) if event.is_file() else ""
    for record_name in ("ExecutionStarted", "TriggerStarted"):
        pattern = re.compile(rf"record\s+{record_name}\s*\(\s*String\s+namespace\b")
        if not pattern.search(event_text):
            errors.append(f"{relative(event)} {record_name} must retain namespace attribution")

    artifact = (
        DEPLOY_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "deploy" / "api" / "artifact" / "ProcessArtifact.java"
    )
    legacy_artifact = artifact.parent.parent / "protocol" / "artifact" / "ProcessArtifact.java"
    if not artifact.is_file():
        errors.append(f"{relative(artifact)} must own the Deploy domain artifact")
    else:
        artifact_text = read_text(artifact)
        for forbidden in ("ReleaseMetadata", "getMetadata()", "Map<String, String> metadata"):
            if forbidden in artifact_text:
                errors.append(
                    f"{relative(artifact)} must not project Control Plane release metadata: {forbidden}"
                )
    if legacy_artifact.exists():
        errors.append(f"{relative(legacy_artifact)} must not mix the domain artifact with wire helpers")

    artifact_protocol = artifact.parent.parent / "protocol" / "artifact"
    for protocol_type in ("ProcessArtifactPayload.java", "ProcessArtifactPayloads.java"):
        protocol_path = artifact_protocol / protocol_type
        if protocol_path.is_file() and re.search(r"\bmetadata\b", read_text(protocol_path)):
            errors.append(
                f"{relative(protocol_path)} must not carry Control Plane release metadata"
            )

    deploy_api_sources = DEPLOY_API / "src" / "main" / "java"
    for path in sorted(deploy_api_sources.rglob("*Metrics.java")):
        package = java_package(path)
        if package != "com.alibaba.compileflow.deploy.api.observability":
            errors.append(
                f"{relative(path)} is a cross-component Deploy metrics contract and must be owned by "
                "deploy.api.observability"
            )

    for suffix in ("en", "zh"):
        constitution = ROOT / "docs" / "architecture" / f"12-API_DESIGN.{suffix}.md"
        if not constitution.is_file():
            errors.append(f"{relative(constitution)} is required by the API Freeze contract")
    return errors


def check_durable_evolution_contract() -> list[str]:
    """Keep format evolution and released semantic recovery executable."""
    errors: list[str] = []
    store = (
        DURABLE_SPI / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "spi" / "store" / "DurableStore.java"
    )
    if not store.is_file():
        errors.append(f"{relative(store)} is required by the Durable evolution contract")
    else:
        text = read_text(store)
        for marker in (
            "final class Envelope",
            "private static final byte[] MAGIC = {'C', 'F', 'D'};",
            "private static final int CURRENT_FORMAT_VERSION = 1;",
            "public static Envelope fromStoredBytes(byte[] storedBytes)",
            "public byte[] payload()",
        ):
            if marker not in text:
                errors.append(f"{relative(store)} must retain persisted-envelope marker: {marker}")
        for forbidden in ("codecVersion", "ApplicationBuildId", "WorkerBuildId"):
            if forbidden in text:
                errors.append(f"{relative(store)} must not turn format evolution into identity: {forbidden}")

    corpus = DURABLE_RUNTIME / "src" / "test" / "resources" / "durable-compatibility" / "v1"
    required_fixtures = (
        "README.md",
        "bpmn-wait-process.bpmn",
        "bpmn-timer-process.bpmn",
        "bpmn-effect-process.bpmn",
        "bpmn-structured-process.bpmn",
        "wait-process.bpm",
        "timer-process.bpm",
        "effect-process.bpm",
        "foreach-loop.bpm",
        "wait-snapshot.base64",
        "loop-snapshot.base64",
        "wait-result.base64",
        "empty-snapshot.base64",
        "empty-result.base64",
        "kernel-fact.base64",
    )
    for name in required_fixtures:
        fixture = corpus / name
        if not fixture.is_file():
            errors.append(f"{relative(fixture)} is required by the Durable compatibility corpus")

    corpus_test = (
        DURABLE_RUNTIME / "src" / "test" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "runtime" / "program" / "DurableCompatibilityCorpusTest.java"
    )
    corpus_text = read_text(corpus_test) if corpus_test.is_file() else ""
    for marker in (
        "resumesTheFrozenWaitSnapshotAndCommittedResult",
        "resumesTheFrozenTimerSnapshot",
        "resumesTheFrozenEffectSnapshotAndCommittedResult",
        "resumesTheFrozenLoopSnapshotWithoutImplementationVersionRouting",
        "decodesTheFrozenKernelFactWithoutTreatingFormatAsIdentity",
        "compilesTheFrozenBpmnProfileThroughProductionDiscovery",
    ):
        if marker not in corpus_text:
            errors.append(f"{relative(corpus_test)} must exercise frozen corpus case: {marker}")

    compatibility = ROOT / "docs" / "compatibility-policy.md"
    compatibility_text = read_text(compatibility) if compatibility.is_file() else ""
    for marker in (
        "CompileFlow-owned Process semantic compatibility",
        "Operational tuning",
        "Java native serialization",
    ):
        if marker not in compatibility_text:
            errors.append(f"{relative(compatibility)} must retain evolution policy: {marker}")

    migration = DURABLE_MIGRATION_OWNER / "V1__durable_kernel.sql"
    migration_text = read_text(migration) if migration.is_file() else ""
    for marker in (
        "model_type",
        "ck_cf_durable_process_model_type",
        "process_id            uuid PRIMARY KEY",
        "CONSTRAINT uq_cf_durable_process_definition_digest UNIQUE",
        "CONSTRAINT ck_cf_durable_effect_readiness CHECK",
        "turn_fault_streak = 0 AND retry_code IS NULL AND retry_observed_at IS NULL",
        "turn_fault_streak > 0 AND retry_code IS NOT NULL AND retry_observed_at IS NOT NULL",
        "completed_at IS NULL OR completed_at >= updated_at",
        "status <> 'RUNNING' OR control_state <> 'PAUSED'",
        "status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED') OR control_state = 'ACTIVE'",
    ):
        if marker not in migration_text:
            errors.append(f"{relative(migration)} must retain Durable V1 schema fact: {marker}")
    if "ck_cf_durable_effect_capability" in migration_text:
        errors.append(f"{relative(migration)} uses retired Effect capability constraint naming")
    process_table = migration_text.partition("CREATE TABLE public.cf_durable_process")[2].partition(
        "CREATE FUNCTION public.cf_durable_process_reject_update"
    )[0]
    for forbidden in ("namespace", "process_version"):
        if forbidden in process_table:
            errors.append(
                f"{relative(migration)} must keep admission attribution out of cf_durable_process: {forbidden}"
            )
    run_table = migration_text.partition("CREATE TABLE public.cf_durable_run")[2].partition(
        "CREATE FUNCTION public.cf_durable_run_validate_root_process"
    )[0]
    if "process_version" not in run_table:
        errors.append(f"{relative(migration)} must retain optional Version attribution on cf_durable_run")

    durable_main = DURABLE_RUNTIME / "src" / "main"
    forbidden_source_packages = (
        "com.alibaba.compileflow.engine.tbbpm.model",
        "com.alibaba.compileflow.engine.tbbpm.parser",
        "com.alibaba.compileflow.engine.tbbpm.validation",
        "com.alibaba.compileflow.engine.tbbpm.semantic",
        "com.alibaba.compileflow.engine.bpmn.model",
        "com.alibaba.compileflow.engine.bpmn.parser",
        "com.alibaba.compileflow.engine.bpmn.validation",
        "com.alibaba.compileflow.engine.bpmn.semantic",
    )
    for path in sorted(durable_main.rglob("*.java")):
        text = read_text(path)
        for package in forbidden_source_packages:
            if package in text:
                errors.append(
                    f"{relative(path)} must consume ProcessSemanticPlan instead of source package {package}"
                )

    runtime_pom = DURABLE_RUNTIME / "pom.xml"
    runtime_dependencies = {
        artifact_id
        for group_id, artifact_id, _ in dependency_coordinates(runtime_pom)
        if group_id == "com.alibaba.compileflow"
    }
    for artifact_id in ("compileflow-tbbpm", "compileflow-bpmn"):
        if artifact_id in runtime_dependencies:
            errors.append(
                f"{relative(runtime_pom)} must not compile-depend on source-format module {artifact_id}"
            )

    durable_sources = sorted((durable_main / "java").rglob("*.java"))
    for path in durable_sources:
        text = read_text(path)
        for marker in ("stored.namespace()", "stored.processVersion()"):
            if marker in text:
                errors.append(
                    f"{relative(path)} must not recover admission attribution from stored Process semantics: {marker}"
                )

    process_engine_source = (
        ROOT / "compileflow-core" / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "engine" / "core" / "DefaultProcessEngine.java"
    )
    process_engine_text = read_text(process_engine_source) if process_engine_source.is_file() else ""
    for marker in ("preparePublishedCall",):
        if marker in process_engine_text:
            errors.append(
                f"{relative(process_engine_source)} contains a removed Process-call resolution marker: {marker}"
            )
    semantic_compiler_users = [
        path for path in durable_sources if "ProcessSemanticCompiler.discover(" in read_text(path)
    ]
    if not semantic_compiler_users:
        errors.append(
            f"{relative(durable_main)} must discover the shared semantic compiler for persisted model types"
        )
    if not any("stored.modelType()" in read_text(path) for path in durable_sources):
        errors.append(
            f"{relative(durable_main)} must use the persisted model type when compiling stored definitions"
        )

    compiler_sources = sorted(
        (ROOT / "compileflow-core" / "src" / "main" / "java").rglob("ProcessSemanticCompiler*.java")
    )
    if len(compiler_sources) != 2:
        errors.append(
            "compileflow-core must own exactly the semantic compiler and its provider contract; "
            f"found {[relative(path) for path in compiler_sources]}"
        )
    for path in compiler_sources:
        if java_package(path) != "com.alibaba.compileflow.engine.core.semantic":
            errors.append(f"{relative(path)} must be owned by the target-neutral core.semantic package")

    provider_service = "com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompilerProvider"
    for module in (ROOT / "compileflow-tbbpm", ROOT / "compileflow-bpmn"):
        service_root = module / "src" / "main" / "resources" / "META-INF" / "services"
        service_files = [
            path for path in sorted(service_root.glob("*ProcessSemanticCompilerProvider"))
            if path.is_file()
        ]
        expected = service_root / provider_service
        if service_files != [expected]:
            errors.append(
                f"{relative(service_root)} must declare exactly the target-neutral semantic compiler provider "
                f"service; found {[path.name for path in service_files]}"
            )
    return errors


def check_default_metric_dimensions() -> list[str]:
    """Reject business identifiers in default Micrometer binder tag declarations."""
    errors: list[str] = []
    binder_root = AUTOCONFIGURE / "src" / "main" / "java"
    binders = sorted(binder_root.rglob("*MetricsBinder.java"))
    if not binders:
        return [f"{relative(binder_root)} must contain metrics binders"]
    for path in binders:
        text = read_text(path)
        for tag in FORBIDDEN_DEFAULT_METRIC_TAGS:
            if f'"{tag}"' in text:
                errors.append(
                    f"{relative(path)} contains forbidden default metric dimension: {tag}"
                )
    core_sources = ROOT / "compileflow-core" / "src" / "main" / "java"
    dropped_counter_owners = [
        path for path in sorted(core_sources.rglob("*.java"))
        if "droppedCount()" in read_text(path)
    ]
    if not dropped_counter_owners:
        errors.append(f"{relative(core_sources)} must expose an internal dropped-event counter")
    if not any('"events.dropped"' in read_text(path) for path in binders):
        errors.append(f"{relative(binder_root)} must bind the dropped-event counter")
    return errors


def check_execution_surface_vocabulary() -> list[str]:
    """Freeze the current execution surfaces, package roots, and Definition format binding."""
    errors: list[str] = []
    module_map = ROOT / "docs" / "architecture" / "03-MODULE_MAP.en.md"
    module_map_text = read_text(module_map) if module_map.is_file() else ""
    for marker in (
        "`ProcessEngine` and `DurableProcessEngine` are sibling",
        "`LocalRoutingState`",
    ):
        if marker not in module_map_text:
            errors.append(
                f"{relative(module_map)} must retain execution-surface marker: {marker}"
            )

    process_spec = ROOT / "docs" / "specs" / "tbbpm-specification.en.md"
    process_spec_text = read_text(process_spec) if process_spec.is_file() else ""
    for marker in (
        "Exactly one of `classpath` or `version` is required",
        "it is not a URI",
        "caller-relative resolution",
        "The complete transitive graph is",
        "before the first business action",
    ):
        if marker not in process_spec_text:
            errors.append(
                f"{relative(process_spec)} must retain Process-call binding marker: {marker}"
            )

    java_sources = tuple(
        path for path in iter_repository_text_files()
        if path.suffix == ".java" and "/src/main/java/" in path.as_posix()
    )
    retired_java_terms = (
        "DurableProcessService",
        "DefaultDurableProcessService",
        "OrdinaryProcessEngine",
        "NonDurableProcessEngine",
        "Invocation.ordinary(",
        "ExecutionMode.DURABLE",
        "RoutedProcessEngine",
        "VersioningSnapshots",
        "AliasRouteSnapshot",
        "InstalledVersionSnapshot",
        "RoutingSnapshots",
        "AliasRouteMissHandler",
        "LocalReadyRoutingStateApplier",
        "RoutingStateReconciliationTask",
        "ProcessFamilyKey",
        "RuntimeTaskExecutors",
        "ConditionExecutor",
        "LoopExecutor",
        "TriggerExecutor",
        "StructuredProcessPlan",
        "StructuredProcessPlanAnalyzer",
        "ProcessVariableModel",
        "DurableProcessProgram",
        "MultiInstanceController",
        "WorkerDurationPolicy",
        "DurableRuntimeIdentity",
        "ProcessActor",
        "EffectCapabilityCode",
        "ProcessArtifactPublisher",
        "ArtifactPublishCoordinator",
        "ProcessDemandDecision",
        "ProcessDefinitionOrigin",
        "ProcessResourceBase",
        "ProcessResourceReference",
        "ResolvedProcessDefinition",
        "ProcessEntity",
        "ProcessRepository",
        "ProcessSummaryProjection",
        "DeploymentCreateCommand",
        "CanaryUpdateRequest",
        "CanaryAbortRequest",
        "CanaryEvaluationRequest",
        "CanaryRevisionRequest",
        "DeploymentRollbackRequest",
        "ActionConcurrency",
        "ProcessCallResourceResolver",
        "ProcessCallTarget.Resource",
        "getCalledProcessResource",
        "CF_ATTRIBUTE_RESOURCE",
        "ATTRIBUTE_RESOURCE",
    )
    retired_package_roots = (
        "com.alibaba.compileflow.engine.core.executor",
        "com.alibaba.compileflow.engine.core.runtime.executor",
        "com.alibaba.compileflow.engine.core.analysis",
        "com.alibaba.compileflow.durable.spi.execution",
    )
    for path in java_sources:
        text = read_text(path)
        for term in retired_java_terms:
            if term == "ActionConcurrency":
                found = re.search(
                    r"(?<![A-Za-z0-9_$])ActionConcurrency(?![A-Za-z0-9_$])",
                    text,
                )
            else:
                found = term in text
            if found:
                errors.append(f"{relative(path)} uses retired execution vocabulary: {term}")
        package = java_package(path)
        if package is not None and package.startswith("com.alibaba.compileflow.engine.deploy"):
            errors.append(f"{relative(path)} must use the top-level com.alibaba.compileflow.deploy package")
        if package is not None and package.startswith(retired_package_roots):
            errors.append(f"{relative(path)} uses retired package vocabulary: {package}")
    process_format_files = tuple(
        path for path in iter_repository_text_files()
        if path.suffix in {".bpm", ".bpmn", ".md", ".xml"}
    )
    retired_call_attribute = re.compile(r"cf:resource\s*=|<bpmCall\b[^>]*\bresource\s*=", re.DOTALL)
    for path in process_format_files:
        if retired_call_attribute.search(read_text(path)):
            errors.append(f"{relative(path)} uses retired Process-call resource attribute")

    retired_call_message = "exactly one of " + "resource or version is required"
    for path in iter_repository_text_files():
        if retired_call_message in read_text(path):
            errors.append(f"{relative(path)} uses retired Process-call validation vocabulary")

    retired_ontology = re.compile(
        r"(?i)\bordinary\s+(?:execution|runtime|engine|invocation|ProcessEngine|ProcessRuntime)\b"
        r"|\bnon[- ]durable\b"
    )
    for path in iter_repository_text_files():
        text = read_text(path)
        if retired_ontology.search(text):
            errors.append(f"{relative(path)} reintroduces the retired execution-mode ontology")
        if "com.alibaba.compileflow.engine.deploy" in text or "com/alibaba/compileflow/engine/deploy" in text:
            errors.append(f"{relative(path)} references the retired Deploy package root")

    engine = (
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "DurableProcessEngine.java"
    )
    engine_text = read_text(engine) if engine.is_file() else ""
    if re.search(r"interface\s+DurableProcessEngine\s+extends\s+ProcessEngine", engine_text):
        errors.append(f"{relative(engine)} must remain a sibling of ProcessEngine")

    implementation = (
        DURABLE_RUNTIME / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "runtime" / "DefaultDurableProcessEngine.java"
    )
    if not implementation.is_file() or "package com.alibaba.compileflow.durable.runtime;" not in read_text(implementation):
        errors.append(f"{relative(implementation)} must own the default DurableProcessEngine implementation")

    effect_policy_plan = (
        ROOT / "compileflow-core" / "src" / "main" / "java" / "com" / "alibaba"
        / "compileflow" / "engine" / "core" / "semantic" / "plan" / "EffectPolicyPlan.java"
    )
    effect_policy_text = read_text(effect_policy_plan) if effect_policy_plan.is_file() else ""
    if "ReconcilePlan reconcileAction" not in effect_policy_text:
        errors.append(f"{relative(effect_policy_plan)} must keep reconcile as a narrow ReconcilePlan")

    runtime_manager = (
        DURABLE_RUNTIME / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "runtime" / "process" / "DurableProcessRuntimeManager.java"
    )
    runtime_manager_text = read_text(runtime_manager) if runtime_manager.is_file() else ""
    for marker in (
        "this.definitionModelType = config.getModelType();",
        "register(ProcessDefinition definition)",
        "register(ProcessRef.Version version)",
    ):
        if marker not in runtime_manager_text:
            errors.append(
                f"{relative(runtime_manager)} must retain Direct Definition execution-context binding: {marker}"
            )
    return errors


def check_durable_domain_model_vocabulary() -> list[str]:
    """Keep Durable application models in domain vocabulary, not transport vocabulary."""
    errors: list[str] = []
    model_root = (
        DURABLE_API / "src" / "main" / "java" / "com" / "alibaba" / "compileflow"
        / "durable" / "api" / "model"
    )
    required = (
        "ProcessRun.java",
        "ProcessRunControl.java",
        "ProcessRunRetryState.java",
        "ActiveWork.java",
        "ActiveWait.java",
        "ActiveTimer.java",
        "ActiveEffect.java",
        "ProcessTimelineEvent.java",
        "OutboxEvent.java",
    )
    for name in required:
        if not (model_root / name).is_file():
            errors.append(f"{relative(model_root / name)} must remain a Durable domain model")

    retired = (
        "ProcessRunView",
        "ProcessRunControlView",
        "ProcessRunRetryView",
        "ActiveWorkView",
        "ActiveWaitView",
        "ActiveEffectView",
        "ProcessTimelineEventView",
        "OutboxEventView",
        "WaitKind",
    )
    checker = Path(__file__).resolve()
    for path in iter_repository_text_files():
        if path == checker:
            continue
        text = read_text(path)
        for term in retired:
            if re.search(rf"\b{re.escape(term)}\b", text):
                errors.append(f"{relative(path)} retains retired Durable domain type: {term}")

    active_work = model_root / "ActiveWork.java"
    active_work_text = read_text(active_work) if active_work.is_file() else ""
    if "permits ActiveWait, ActiveTimer, ActiveEffect" not in active_work_text:
        errors.append(f"{relative(active_work)} must expose Wait, Timer, and Effect variants")
    summary = model_root / "ActiveWorkSummary.java"
    summary_text = read_text(summary) if summary.is_file() else ""
    if "record ActiveWorkSummary(int waits," not in summary_text:
        errors.append(f"{relative(summary)} must use the domain field name 'waits'")
    return errors


def check_api_artifact_public_classification() -> list[str]:
    """Require every public type in API artifacts to be supported API or SPI."""
    errors: list[str] = []
    artifacts = (
        (
            ROOT / "compileflow-api" / "src" / "main" / "java",
            ("com.alibaba.compileflow.engine",),
            ("com.alibaba.compileflow.engine.spi",),
        ),
        (
            DEPLOY_API / "src" / "main" / "java",
            ("com.alibaba.compileflow.deploy.api",),
            ("com.alibaba.compileflow.deploy.api.spi", "com.alibaba.compileflow.deploy.api.sync"),
        ),
        (
            DURABLE_API / "src" / "main" / "java",
            ("com.alibaba.compileflow.durable.api",),
            (),
        ),
        (
            DURABLE_SPI / "src" / "main" / "java",
            (),
            ("com.alibaba.compileflow.durable.spi",),
        ),
    )
    for source_root, api_prefixes, spi_prefixes in artifacts:
        for path in sorted(source_root.rglob("*.java")):
            if PUBLIC_TOP_LEVEL_TYPE_RE.search(read_text(path)) is None:
                continue
            package = java_package(path)
            classified = any(
                package == prefix or package is not None and package.startswith(prefix + ".")
                for prefix in api_prefixes + spi_prefixes
            )
            if not classified:
                errors.append(f"{relative(path)} exposes an unclassified JVM-public type")
    return errors


def check_public_default_constants() -> list[str]:
    """Keep implementation tuning defaults out of compile-time public API constants."""
    errors: list[str] = []
    allowed = {
        "compileflow-deploy/compileflow-deploy-api/src/main/java/com/alibaba/compileflow/deploy/api/"
        "protocol/artifact/ProcessArtifactKeys.java": {"DEFAULT_PREFIX"},
        "compileflow-deploy/compileflow-deploy-api/src/main/java/com/alibaba/compileflow/deploy/api/"
        "protocol/routing/RoutingStateKeys.java": {"DEFAULT_PREFIX"},
    }
    source_roots = (
        ROOT / "compileflow-api" / "src" / "main" / "java",
        DEPLOY_API / "src" / "main" / "java",
        DURABLE_API / "src" / "main" / "java",
        DURABLE_SPI / "src" / "main" / "java",
    )
    pattern = re.compile(
        r"\bpublic\s+static\s+final\s+[A-Za-z0-9_.$<>?, ]+\s+(DEFAULT_[A-Z0-9_]+)\s*="
    )
    for source_root in source_roots:
        for path in sorted(source_root.rglob("*.java")):
            path_name = relative(path)
            for name in pattern.findall(read_text(path)):
                if name not in allowed.get(path_name, set()):
                    errors.append(
                        f"{path_name} exposes tuning default {name}; use defaults()/builder state instead"
                    )
    return errors


def check_supported_spring_bean_seams() -> list[str]:
    """Freeze the small API/SPI bean seam allowlist without freezing all auto-config details."""
    errors: list[str] = []
    seams = (
        "ProcessDataMapper",
        "ProcessEngineConfig",
        "ProcessEngine",
        "ProcessEventListener",
        "TraceIdProvider",
        "ProcessComponentResolver",
        "ProcessContextPropagator",
        "ProcessAliasRouteSource",
        "ProcessAliasTargetingPolicy",
        "ScriptExecutor",
        "RetryPolicy",
        "FailureHandler",
        "ProcessEnginePlugin",
        "ProcessDeploymentService",
        "DeploymentSyncChannel",
        "ProcessArtifactSource",
        "DurableProcessEngine",
        "DurableOperatorService",
        "DurableStore",
        "DurableWaitDescriptionProvider",
        "DurableVersionDefinitionSource",
        "DurableAliasStateSource",
        "DurableOutboxSink",
    )
    auto_configuration_roots = (
        ROOT / "compileflow-spring-boot-autoconfigure" / "src" / "main" / "java",
        DURABLE_AUTOCONFIGURE / "src" / "main" / "java",
    )
    auto_configuration = "\n".join(
        read_text(path)
        for root in auto_configuration_roots
        for path in sorted(root.rglob("*.java"))
    )
    for suffix in ("en", "zh"):
        path = ROOT / "docs" / suffix / "configuration.md"
        text = read_text(path)
        for seam in seams:
            if f"`{seam}`" not in text:
                errors.append(f"{relative(path)} must list Supported Spring bean seam {seam}")
    for seam in seams:
        if re.search(rf"\b{re.escape(seam)}\b", auto_configuration) is None:
            errors.append(f"Supported Spring bean seam {seam} is not consumed by auto-configuration")
    return errors


def check_configuration_ownership() -> list[str]:
    """Keep owner packages and retired public configuration keys exact."""
    errors: list[str] = []
    retired_keys = (
        "compileflow.durable.worker.idle-poll-interval",
        "compileflow.deploy.outbox.batch-size",
        "compileflow.workbench.server.async-invocation.lease-renewal-interval",
    )
    for path in iter_repository_text_files():
        if "test" in path.parts and "src" in path.parts:
            continue
        text = read_text(path)
        for key in retired_keys:
            if key in text:
                errors.append(f"{relative(path)} uses retired configuration key {key}")

    deploy_root = (
        AUTOCONFIGURE
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "deploy"
        / "spring"
        / "boot"
        / "autoconfigure"
    )
    for path in sorted(deploy_root.rglob("*.java")):
        match = PACKAGE_RE.search(read_text(path))
        if match is None or not match.group(1).startswith(
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure"
        ):
            errors.append(f"{relative(path)} must remain in the Deploy-owned Spring package")

    engine_root = (
        AUTOCONFIGURE
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "engine"
        / "spring"
        / "boot"
        / "autoconfigure"
    )
    for path in sorted(engine_root.rglob("*.java")):
        if re.search(r"\b(?:Deploy|Deployment|RoutingOutbox|Reconciliation)", path.stem):
            errors.append(f"{relative(path)} places Deploy configuration under the Engine owner")
    return errors


def check_role_accurate_names() -> list[str]:
    """Prevent removed phase-oriented and context-free names from returning."""
    errors: list[str] = []
    obsolete_names = (
        "PreparedProcessGraph",
        "ProcessInvocationGraph",
        "ProcessInvocationExecutor",
        "PreparedProcessExecutor",
        "ProcessCallExecutor",
        "PreparedExpressionEvaluator",
        "PreparedDurableProgram",
        "PreparedProgramCache",
        "InMemoryPreparedProgramCache",
        "DurablePreparationWorker",
        "PreparationDemand",
        "ManagedProcessExecutionService",
        "ProcessCallSupport",
        "VarSupport",
        "JavaExpressionParserSupport",
        "AbstractFlowWriterSupport",
        "OrdinaryTargetEligibilityChecker",
        "DurableTargetEligibilityChecker",
        "prepareAliasExecution",
        "ManagedLifecycle",
        "ManagedProcessKey",
        "PreparedInstallation",
        "PreparedScript",
        "ScriptPreparationRequest",
        "PreparedScriptCatalog",
        "ManagedExecutionRouting",
        "CompileOption",
        "RuntimeSpec",
        "ResolvedProcessRuntime",
        "ResolvedProcessDefinition",
        "ResolvedInvocationPolicy",
        "ResolvedEffectPolicy",
        "ResolvedOccurrence",
        "ResolvedDefinition",
        "resolvedProcess",
        "VariableManager",
        "BucketSpec",
        "ExpressionSpec",
        "DurablePreparationManager",
    )
    java_source_files = sorted(
        path for path in ROOT.rglob("*.java")
        if "/src/main/java/" in path.as_posix()
    )
    role_name_files = java_source_files + sorted(
        path for suffix in ("*.ts", "*.tsx")
        for path in (ROOT / "compileflow-workbench" / "apps" / "web" / "src").rglob(suffix)
    )
    for path in role_name_files:
        text = read_text(path)
        for name in obsolete_names:
            if re.search(rf"\b{re.escape(name)}\b", text):
                errors.append(f"{relative(path)} uses removed ambiguous role name: {name}")
    for path in java_source_files:
        text = read_text(path)
        if "runtime/preparation" in path.as_posix():
            errors.append(
                f"{relative(path)} must use responsibility-based runtime packages, not preparation"
            )
        generic_role_match = re.search(
            r"\b[A-Z][A-Za-z0-9]*(?:Spec|Request|Result|Report|Descriptor|Plan|Graph|Snapshot|Policy|Route|"
            r"Options|Config|Context|Scope|Query|Command)\s+(?:value|data|info)\b",
            text,
        )
        if generic_role_match:
            errors.append(
                f"{relative(path)} erases a domain role with a generic identifier: "
                f"{generic_role_match.group(0)}"
            )
        script_request_match = re.search(r"\bScriptProgramSpec\s+request\b", text)
        if script_request_match:
            errors.append(
                f"{relative(path)} must name ScriptProgramSpec values 'spec' or by a more specific role"
            )
    stale_process_target_terms = ("exact member", "target member", "member identities", "精确 member")
    documentation_files = sorted((ROOT / "docs").rglob("*.md"))
    for path in documentation_files:
        text = read_text(path)
        for term in stale_process_target_terms:
            if term in text:
                errors.append(f"{relative(path)} uses obsolete Process-call target term: {term}")
    return errors


def check_library_logging_configuration() -> list[str]:
    """Keep logging backend policy in executable applications, not embedded libraries."""
    errors: list[str] = []
    for module in LIBRARY_MODULES:
        resource_root = module / "src" / "main" / "resources"
        if not resource_root.is_dir():
            continue
        for path in sorted(resource_root.rglob("*")):
            if path.is_file() and path.name.lower() in LOGGING_CONFIGURATION_NAMES:
                errors.append(
                    f"{relative(path)} must not configure the host application's logging backend"
                )
    return errors


def main() -> int:
    """Run every architecture boundary check."""
    checks = (
        check_engine_api_dependencies,
        check_java_script_provider_boundary,
        check_deploy_api_dependencies,
        check_optional_deploy_configuration_binding,
        check_module_dependency_directions,
        check_durable_product_boundary,
        check_no_internal_java_packages,
        check_java_package_boundaries,
        check_java_source_layout,
        check_library_split_packages,
        check_workbench_server_package_graph,
        check_migration_discovery_boundaries,
        check_deploy_migration_owner,
        check_project_storage_boundaries,
        check_workbench_schema_admission,
        check_legacy_deploy_names,
        check_documented_semantic_contracts,
        check_release_compatibility_contracts,
        check_removed_rollout_automation_contract,
        check_legacy_routing_key_name,
        check_routing_key_persistence_boundary,
        check_workbench_product_boundary,
        check_workbench_web_dependency_directions,
        check_workbench_public_terminology,
        check_api_freeze_contracts,
        check_durable_evolution_contract,
        check_execution_surface_vocabulary,
        check_durable_domain_model_vocabulary,
        check_api_artifact_public_classification,
        check_public_default_constants,
        check_supported_spring_bean_seams,
        check_configuration_ownership,
        check_role_accurate_names,
        check_default_metric_dimensions,
        check_library_logging_configuration,
    )
    errors = [error for check in checks for error in check()]
    if errors:
        print("Architecture boundary check failed:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print("Architecture boundary check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
