#!/usr/bin/env python3
"""Check repository-wide documentation, delivery, and hygiene invariants."""

from __future__ import annotations

import fnmatch
import hashlib
import json
import os
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
INTERNAL_DECLARATION_RE = re.compile(
    r"^\s*(?:export\s+)?(?:declare\s+)?(?:async\s+)?"
    r"(?:function|class|interface|type|enum|const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)",
    re.MULTILINE,
)
UPPERCASE_ACRONYM_RE = re.compile(r"[A-Z]{2,}")
MICROMETER_METRIC_LITERAL_RE = re.compile(r'"(compileflow\.[a-z0-9_.]+)"')
MICROMETER_PREFIX_DECLARATION_RE = re.compile(
    r'METRIC_PREFIX\s*=\s*"(compileflow\.[a-z0-9_.]+)"'
)
MICROMETER_PREFIX_SUFFIX_RE = re.compile(
    r'METRIC_PREFIX\s*\+\s*"([a-z0-9_.]+)"'
)
DOCUMENTED_MICROMETER_METRIC_RE = re.compile(
    r"`(compileflow\.[a-z0-9_.]+)`"
)
SKIP_DIRS = {
    ".git",
    ".idea",
    ".mvn",
    "__pycache__",
    "coverage",
    "dist",
    "node_modules",
    "playwright-report",
    "target",
    "test-results",
}
ALLOWED_TRACKED_ROOT_HIDDEN_PATHS = (
    Path(".dockerignore"),
    Path(".editorconfig"),
    Path(".gitattributes"),
    Path(".github"),
    Path(".gitignore"),
    Path(".mvn"),
)
TRACKED_GENERATED_DIR_NAMES = {
    "__pycache__",
    "coverage",
    "dist",
    "node_modules",
    "playwright-report",
    "target",
    "test-results",
}
TRACKED_GENERATED_FILE_SUFFIXES = {
    ".class",
    ".jar",
    ".log",
    ".pyc",
    ".war",
}


def extract_micrometer_metric_names(java_text: str) -> set[str]:
    """Extract complete CompileFlow meter names from one Micrometer binder."""
    names = set(MICROMETER_METRIC_LITERAL_RE.findall(java_text))
    prefix_match = MICROMETER_PREFIX_DECLARATION_RE.search(java_text)
    if prefix_match:
        prefix = prefix_match.group(1)
        names.update(
            prefix + suffix
            for suffix in MICROMETER_PREFIX_SUFFIX_RE.findall(java_text)
        )
    return {name for name in names if not name.endswith(".")}


def find_missing_workflow_tests(root: Path) -> list[str]:
    """Check each explicit Surefire class selector, including quoted globs."""
    test_classes = {
        path.stem
        for path in root.glob("**/src/test/java/**/*.java")
        if not any(part in SKIP_DIRS for part in path.relative_to(root).parts)
    }
    errors: list[str] = []
    for workflow in sorted((root / ".github/workflows").glob("*.yml")):
        for match in re.finditer(r'''-Dtest=("[^"]*"|'[^']*'|[^\s\\]+)''', workflow.read_text(encoding="utf-8")):
            for selector in shlex.split(match.group(1))[0].split(","):
                if selector.startswith("!"):
                    continue
                pattern = selector.split("#", 1)[0].removesuffix(".java").rsplit(".", 1)[-1]
                if not any(fnmatch.fnmatchcase(name, pattern) for name in test_classes):
                    errors.append(f"{workflow.relative_to(root)}: CI test selector matches no source: {selector}")
    return errors


def check_workflow_tests() -> list[str]:
    return find_missing_workflow_tests(ROOT)


def find_internal_identifier_casing_errors(root: Path) -> list[str]:
    """Find owned source identifiers that use all-uppercase acronym segments."""
    errors: list[str] = []
    for path in root.rglob("*.java"):
        if "src" not in path.parts or "java" not in path.parts:
            continue
        if path.name in {"package-info.java", "module-info.java"}:
            continue
        if UPPERCASE_ACRONYM_RE.search(path.stem):
            errors.append(
                f"{path.relative_to(root)} uses an uppercase acronym in an owned Java type name"
            )

    apps_root = root / "compileflow-workbench" / "apps"
    if apps_root.exists():
        for path in apps_root.glob("*/src/**/*"):
            if path.suffix not in {".ts", ".tsx"}:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INTERNAL_DECLARATION_RE.finditer(text):
                identifier = match.group(1)
                if identifier.isupper() or not UPPERCASE_ACRONYM_RE.search(identifier):
                    continue
                line = text.count("\n", 0, match.start(1)) + 1
                errors.append(
                    f"{path.relative_to(root)}:{line} uses an uppercase acronym in owned identifier "
                    f"{identifier}"
                )
    return errors


def find_maven_project_identity_errors(root: Path) -> list[str]:
    """Require each Maven project directory to match its artifact identity."""
    errors: list[str] = []
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    for pom in sorted(root.rglob("pom.xml")):
        relative_path = pom.relative_to(root)
        if relative_path == Path("pom.xml") or any(
            part in SKIP_DIRS for part in relative_path.parts
        ):
            continue
        try:
            project = ET.parse(pom).getroot()
        except ET.ParseError as error:
            errors.append(f"{relative_path} is not valid XML: {error}")
            continue
        artifact_id = project.findtext("m:artifactId", default="", namespaces=namespace).strip()
        if not artifact_id:
            errors.append(f"{relative_path} must declare a project artifactId")
        elif artifact_id != pom.parent.name:
            errors.append(
                f"{relative_path} directory {pom.parent.name!r} must match "
                f"artifactId {artifact_id!r}"
            )
    return errors


def check_internal_identifier_casing() -> list[str]:
    return find_internal_identifier_casing_errors(ROOT)


def check_maven_project_identities() -> list[str]:
    return find_maven_project_identity_errors(ROOT)


TRACKED_GENERATED_FILE_NAMES = {
    ".DS_Store",
}
ALLOWED_TRACKED_BINARY_PATHS = {
    Path(".mvn/wrapper/maven-wrapper.jar"),
}
INTERNAL_ONLY_PATTERNS = [
    "compileflow-examples",
]
INSECURE_SECRET_DEFAULT_PATTERNS = [
    "changeme-",
    "change-me-",
]
PROCESS_ARTIFACT_PATTERNS = [
    "Recent Refactoring History",
    "Completed in recent sessions",
    "generated based on current codebase analysis",
    "基于当前代码库分析生成",
]
TRANSLATION_PLACEHOLDER_RE = re.compile(
    r"英文(?:文档|原文).*为准|以英文文档为准|补齐完整中文版|补齐完整中文翻译"
)
PROCESS_STAGE_PATTERNS = [
    re.compile(r"\bPhase\s+\d+(?:-\d+)?\b", re.IGNORECASE),
    re.compile(r"initial baseline", re.IGNORECASE),
]
UNSUPPORTED_ARCHITECTURE_CLAIM_RE = re.compile(
    r"10-100\s*倍(?:提升)?|性能高\s*10-100\s*倍|性能提升\s*10-100\s*倍|减少\s*90%\s*网络传输"
)
STALE_ARCHITECTURE_EXAMPLE_RE = re.compile(
    r"(?<!VITE_)\bCOMPILEFLOW_DEBUG\b|router\.route\(|new\s+WeightedAliasVersionRouter\(\)"
)
STALE_EXECUTION_FLOW_DOC_RE = re.compile(
    r"\bFlowParser\b|UnsupportedFormatException|TbbpmFlowParser|"
    r"source\.getPath\(\)|source\.getLocator\(\)\.load\(\)|"
    r"com\.alibaba\.compileflow\.engine\.runtime\.ProcessRuntime|"
    r"耗时:\s*5-10|100-500ms"
)
MARKDOWN_LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
MARKDOWN_FENCE_RE = re.compile(r"^[ ]{0,3}(`{3,}|~{3,})(.*)$")
MARKDOWN_HEADING_RE = re.compile(r"^#{1,6}\s+(.+?)\s*$")
PNPM_COMMAND_RE = re.compile(
    r"^\s*(?:[A-Z][A-Z0-9_]*=\S+\s+)*pnpm\s+([^\s\\]+)"
)
PNPM_BUILTIN_COMMANDS = {
    "add",
    "audit",
    "config",
    "deploy",
    "dlx",
    "env",
    "exec",
    "help",
    "install",
    "list",
    "pack",
    "publish",
    "remove",
    "run",
    "store",
    "update",
    "why",
}
KFILE_RE = re.compile(r"<kfile\b")
LOCKFILE_DELETION_RE = re.compile(r"rm\s+-rf[^\n]*(?:pnpm-lock\.yaml|package-lock\.json|yarn\.lock)")
STALE_TYPESCRIPT_DOC_RE = re.compile(r"TypeScript\s+5\.(?:5|6)|应该是\s+5\.5\.x")
STALE_JAVA_BASELINE_RE = re.compile(
    r"Build baseline: Java 8|JDK8|JDK 8\+|Java 8\+|project is Java 8|"
    r"currently project is Java 8|<release>8</release>|"
    r'"-source",\s*"1\.8"|"-target",\s*"1\.8"|'
    r"<maven\.compiler\.source>1\.8</maven\.compiler\.source>|"
    r"<maven\.compiler\.target>1\.8</maven\.compiler\.target>|"
    r"<java\.docSource>8</java\.docSource>|\[1\.8,\)|"
    r"falling back to 1\.8|fallback to 1\.8|ECJ compiler \(4\.6\.x\)|"
    r"temurin-8|eclipse-temurin:8|8-jre"
)
STALE_INVOCATION_POLICY_RE = re.compile(
    r'\bretry\s*=\s*["\']R(?:0|[1-9]\d*)/P|'
    r'\bcf:(?:retry|timeout|retryOn|onFailure)\s*=|'
    r'\bonFailure\s*=\s*["\']ignore["\']'
)
PHANTOM_CONFIG_RE = re.compile(
    r"compileflow\.compilation\.compiler-type|compileflow\.compilation\.java-version|"
    r"compileflow\.compilation\.source-retention|compileflow\.compilation\.source-output-dir|"
    r"compileflow\.cache\.runtime\.|compileflow\.cache\.dynamic\.|"
    r"compileflow\.executor\.core-pool-size|compileflow\.executor\.max-pool-size|"
    r"compileflow\.executor\.queue-capacity|compileflow\.metrics-enabled|"
    r"\bcompiler-type:|\bjava-version:|\bsource-retention:|\bsource-output-dir:|"
    r"\bcore-pool-size:|\bmax-pool-size:|"
    r"compileflow\.deploy\.sync\.nacos\.[a-z0-9.-]+|"
    r"compileflow\.deploy\.(?:preflight-mode|preflight-timeout-ms|runtime\.mode)|"
    r"compileflow\.deploy\.(?:control|runtime)\.enabled|"
    r"\b(?:publisher-enabled|subscriber-enabled|artifact-prefix|blacklist-ttl-ms):|"
    r"\bCOMPILEFLOW_BRIDGE_[A-Z0-9_]+\b|\bCOMPILEFLOW_DATABASE_[A-Z0-9_]+\b|"
    r"\bCOMPILEFLOW_WORKBENCH_SERVER_UPSTREAM_API_KEY\b|\bVITE_COMPILEFLOW_CLIENT_KEY\b|"
    r"\bVITE_COMPILEFLOW_VERSION\b|\bVITE_COMPILEFLOW_BUILD_TIME\b|"
    r"\bVITE_COMPILEFLOW_API_BASE_URL\b|\bVITE_COMPILEFLOW_BRIDGE_URL\b"
)
STALE_API_DOC_RE = re.compile(
    r"ProcessSource\.fromCode\([^)]+\)\s*\n\s*\.(?:namespace|version)\(|"
    r"\bProcessRef\.code\s*\(|"
    r"\bCompileFlowAutoConfiguration\b|"
    r"ProcessResult[^\n]{0,120}\bon(?:Success|Failure)\b|"
    r"\bresult\.getMessage\(\)|\bresult\.getException\(\)|\bgetResultData\(\)|"
    r"\bProcessEngine<[^>]+>\s+extends\s+Closeable|"
    r"\bgetAdminService\(\)|\bgetConfig\(\)|"
    r"ProcessEngineConfig\.(?:tbbpm|bpmn)(?:Builder)?\(|"
    r"ProcessEngineFactory\.create(?:Tbbpm|Bpmn)\(|"
    r"\.dumpGeneratedCode\(|\.dumpDirectory\(|COMPILEFLOW_DUMP_CODE|COMPILEFLOW_DUMP_DIR|"
    r"\.generateJavaSource\(|\.getCacheStats\(\)|\bCacheStats\b|"
    r"selectVersion\(String namespace|selectVersion\(namespace|source\.getAlias\(\)|"
    r"ProcessPreflightOptions\.defaults\(\)|\.isValid\(\)|\.getErrors\(\)|"
    r"\.getFlowVersion\(|"
    r"updateActiveSnapshot|updateAliasSnapshot|updateDeployedSnapshot|\bDeployedVersionSnapshot\b|"
    r"RuntimeDeploymentPipeline|publishRoutingState|subscribeRoutingState|getPreviousActiveVersion|"
    r"VersionRouterHolder|VersionRouteContext\.readOnly|ProcessVersionRepositoryHolder|"
    r"ProcessRuntime compileSync|CompletableFuture<ProcessRuntime>|compileSync\([^)]*digest|"
    r"does not manage versions out-of-the-box|不直接管理版本|"
    r"loading-sources-max-size`?\s*(?:\(|（)default 1024|"
    r"loading-sources-max-size`?\s*(?:\(|（)默认 1024|"
    r"compileflow:\s*\n(?:.*\n){0,8}\s+execution:\s*\n\s+default-timeout-seconds:|"
    r"\bFlowStorage\b|the current `SpanContext`"
    r"|Provider implementations are shipped by the format|semantic frontends?, and providers?"
    r"|(?:C|c)ore consumes their provider boundary|matching format provider"
    r"|provider 实现位于格式模块|semantic frontend 和 provider"
    r"|Core 只依赖 provider 边界|没有匹配格式 provider"
)
ACTIVE_DOC_RETIRED_SEMANTIC_PATTERNS = (
    (
        re.compile(
            r"(?:`ProcessDefinition`|Process definition)[^\n]{0,160}"
            r"(?:`?Inline`?|inline)[^\n]{0,80}(?:`?File`?|\bfile\b)[^\n]{0,80}"
            r"(?:`?Classpath`?|\bclasspath\b)",
            re.IGNORECASE,
        ),
        "removed ProcessDefinition.File source",
    ),
    (
        re.compile(
            r"(?:file input is allowed only under configured real-path roots|"
            r"file 只允许位于配置 real-path root 下)",
            re.IGNORECASE,
        ),
        "removed filesystem definition loading",
    ),
    (
        re.compile(
            r"`ProcessExecution`[^.。]{0,160}"
            r"(?:includes|containing|exposes only controlled attribution|"
            r"reports the effective identity|包含|只暴露受控归因)"
            r"[^.。]{0,400}(?:parent invocation|call depth|model type|source digest|"
            r"requested reference|Alias revision|父 invocation|调用深度|请求引用)",
            re.IGNORECASE,
        ),
        "operational attribution embedded in ProcessExecution",
    ),
    (
        re.compile(
            r"`ProcessExecution`[^.。]{0,200}(?:typed `ProcessRef` values|"
            r"类型化 `ProcessRef`)",
            re.IGNORECASE,
        ),
        "obsolete ProcessExecution ProcessRef attribution",
    ),
    (
        re.compile(
            r"(?:Persisted Run state|持久化 Run 状态)[^|\n]{0,240}child Runs?",
            re.IGNORECASE,
        ),
        "persisted Child Run state",
    ),
    (
        re.compile(
            r"(?:\b(?:database|Store)\b[^.。\n]{0,120}(?:stores?|persists?)[^.。\n]{0,40}"
            r"\bonly\b[^.。\n]{0,40}(?:SHA-256\s+)?digest|"
            r"Wait tokens?[^.。\n]{0,120}\bonly their digests are persisted|"
            r"(?:数据库|Store)[^.。\n]{0,80}只(?:存|持久化)[^.。\n]{0,40}"
            r"(?:SHA-256\s+)?(?:digest|Digest|摘要)|"
            r"Wait token[^.。\n]{0,80}只持久化其摘要)",
            re.IGNORECASE,
        ),
        "overbroad Wait-token persistence claim",
    ),
)
REMOVED_2_0_SURFACE_RE = re.compile(
    r"\bProcessSource\b|\bResourceLocator\b|\bRouteContextKeys\b|"
    r"\bVersionRouter\b|\bVersionRouteContext\b|\bVersionSelection\b|"
    r"\bProcessPropertyProvider\b|\bCompositePropertyResolver\b|\bMapPropertyResolver\b|"
    r"\bSystemPropertyResolver\b|\bProcessPropertyDefaults\b|\bProcessPropertyKeys\b|"
    r"\bcompileflow-benchmark\b|"
    r"META-INF/extensions(?!/)|"
    r"com\.alibaba\.compileflow\.engine\.FlowModel\b|"
    r"com\.alibaba\.compileflow\.engine\.ProcessEngineProvider\b"
)
INTERNAL_PACKAGE_DOC_RE = re.compile(
    r"com\.alibaba\.compileflow\.engine\.core\.[A-Za-z0-9_.]+"
)
STALE_DEPLOY_DOC_RE = re.compile(
    r"\bDeployResult\b|\.registerVersion\(|\bdeployAdmin\.getActiveVersion\(|"
    r"\bPublishFlowVersionCommand\b|\bRolloutRecord\b|\bFlowArtifact\b|"
    r"\bFlowArtifactKeys\b|\bFlowArtifactParser\b|\bFlowArtifactPayloads\b|"
    r"\bFlowArtifactSource\b|\bFlowDeploymentMetrics\b|\bFlowDeploymentOpsMetrics\b|"
    r"\.invalidateCache\(|\.invalidateAllCaches\(|"
    r"\bDeploymentRuntime\.bootstrap(?:Db|Channel)\(|"
    r"\bRoutingStatePublisher\b|\"processSource\"\s*:\s*\{|"
    r"\b(?:PublishProcessVersionCommand|ActivateFlowVersionCommand|UpsertAliasWeightsCommand|"
    r"FinalizeRolloutCommand|RollbackCommand)\.builder\("
)
STALE_DURABLE_RECOVERY_DOC_RE = re.compile(
    r"Durable Run binds one exact Process Version|"
    r"Durable Runs continue on their exact Version|"
    r"Process Version referenced by live or recoverable Kernel state|"
    r"Run permanently stores only the\s+resulting exact Version|"
    r"DurableVersionDefinitionSource` restores a missing|"
    r"`DurableVersionDefinitionSource` 恢复缺失|"
    r"Monotonic claim token that rejects stale workers|"
    r"22-scenario\s+HA campaign",
    re.IGNORECASE,
)
UNSUPPORTED_ROLLOUT_DOC_RE = re.compile(
    r"TenantVersionRouter|PercentageTrafficVersionRouter|规划中|计划中"
)
STALE_RELEASE_METADATA_RE = re.compile(
    r"git@github\.com|scm:git:git@|<url>git@|"
    r"oss\.sonatype\.org|nexus-staging-maven-plugin|"
    r"<name>Apache 2</name>|Business-friendly OSS license|"
    r"\(AT\)"
)
STALE_SECURITY_POLICY_RE = re.compile(
    r"\|\s*`?\d+\.x`?\s*\|\s*:white_check_mark:|"
    r"\|\s*`?\d+\.\d+\.\d+`?\s*\|\s*:white_check_mark:"
)
PROJECT_VERSION_RE = re.compile(r"<version>(\d+\.\d+\.\d+(?:-SNAPSHOT)?)</version>")
MODULE_RE = re.compile(r"<module>([^<]+)</module>")
COMPILEFLOW_DEPENDENCY_RE = re.compile(
    r"<groupId>com\.alibaba\.compileflow</groupId>\s*"
    r"<artifactId>[^<]+</artifactId>\s*"
    r"<version>([^<]+)</version>",
    re.DOTALL,
)
MAVEN_CENTRAL_BADGE_RE = re.compile(r"shields\.io/maven-central|search\.maven\.org/artifact/com\.alibaba\.compileflow")
UNPINNED_ACTION_RE = re.compile(
    r"(?m)^\s*uses:\s+([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)@"
    r"(?![a-fA-F0-9]{40}(?:\s|$))([^\s#]+)"
)
STALE_JAVA_CI_BASELINE_RE = re.compile(
    r"java-version:\s*['\"](?:8|11)['\"]|JDK (?:8|11)|"
    r"CI runs on (?:Java )?(?:8|11)|CI 使用 Java (?:8|11)"
)
STALE_MAVEN_BASELINE_RE = re.compile(
    r"Maven\s+3\.6\.0\s+or\s+higher|Maven\s+3\.6\+|Maven\s+3\.6\.x\s+or\s+higher|Maven\s+3\.6\.x\s+或更高|Maven\s+3\.6\+"
)
MAVEN_WRAPPER_REQUIRED_PROPERTIES = {
    "wrapperVersion": "3.3.4",
    "distributionType": "bin",
    "distributionUrl": "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip",
    "distributionSha256Sum": "5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce",
    "wrapperUrl": "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar",
    "wrapperSha256Sum": "4e2fbf6554bc8a4702cdfdd3bef464f423393d784ddbb037216320ce55d5e4e1",
}
EDITORCONFIG_REQUIRED_FRAGMENTS = [
    "root = true",
    "end_of_line = lf",
    "[*.{bat,cmd}]",
    "end_of_line = crlf",
    "trim_trailing_whitespace = true",
    "trim_trailing_whitespace = false",
]
GITATTRIBUTES_REQUIRED_FRAGMENTS = [
    "* text=auto eol=lf",
    "*.cmd text eol=crlf",
    "*.bat text eol=crlf",
    "mvnw text eol=lf",
    "*.jar binary",
    "*.png binary",
    "*.zip binary",
]
JAVA_CORE_CI_REQUIRED_GATES = [
    ("java: ['17', '21', '25']", "Java Core CI must build all supported LTS runtimes"),
    ("if: matrix.java == '17'", "Java Core CI must keep the full suite on the Java 17 baseline"),
    ("Run dynamic-code runtime compatibility smoke", "Java Core CI must smoke dynamic code on Java 25"),
    (
        "-Dcheckstyle.config.location=checkstyle-javadoc.xml",
        "Java Core CI must run the dedicated Javadoc Checkstyle gate",
    ),
    (
        "-pl compileflow-api,compileflow-deploy/compileflow-deploy-api",
        "Java Core CI Javadoc gates must target both supported API artifacts",
    ),
    ("spotbugs:check", "Java Core CI must run SpotBugs"),
    ("verify_spotbugs_reports.py", "Java Core CI must reject incomplete SpotBugs analysis"),
    ("javadoc:javadoc", "Java Core CI must generate Javadocs with doclint enabled"),
]
SERVER_CI_REQUIRED_GATES = [
    ("workflow_call:", "CompileFlow Workbench Server CI must expose a tag release gate"),
    ("check_architecture_boundaries.py", "CompileFlow Workbench Server CI must enforce architecture boundaries"),
    ("checkstyle:check", "CompileFlow Workbench Server CI must run Checkstyle"),
    (
        "-Dcheckstyle.config.location=checkstyle-javadoc.xml",
        "CompileFlow Workbench Server CI must run the dedicated Javadoc Checkstyle gate",
    ),
    (
        "-pl compileflow-api,compileflow-deploy/compileflow-deploy-api",
        "CompileFlow Workbench Server CI Javadoc gates must target both supported API artifacts",
    ),
    ("spotbugs:check", "CompileFlow Workbench Server CI must run SpotBugs"),
    ("verify_spotbugs_reports.py", "CompileFlow Workbench Server CI must reject incomplete SpotBugs analysis"),
    ("javadoc:javadoc", "CompileFlow Workbench Server CI must generate Javadocs with doclint enabled"),
    ("services:", "CompileFlow Workbench Server CI must provision its production database dependency"),
    ("jdbc:postgresql://", "CompileFlow Workbench Server CI must validate migrations on PostgreSQL"),
    (
        "postgres:18.6-alpine3.24@sha256:",
        "CompileFlow Workbench Server CI must pin the PostgreSQL 18 service image",
    ),
    ("postgres: '16.15'", "Workbench and Deploy CI must cover PostgreSQL 16"),
    ("postgres: '17.11'", "Workbench and Deploy CI must cover PostgreSQL 17"),
    ("postgres: '18.6'", "Workbench and Deploy CI must cover PostgreSQL 18"),
    (
        "EmbeddedDeploymentExecutionIntegrationTest",
        "Workbench and Deploy CI must exercise the embedded deployment execution path",
    ),
    (
        "Reject missing, failed, or skipped PostgreSQL evidence",
        "Workbench and Deploy CI must reject incomplete PostgreSQL evidence",
    ),
    (
        "verify_workbench_postgres_evidence.py",
        "Workbench and Deploy CI must run the reusable PostgreSQL evidence verifier",
    ),
    (
        "test_verify_workbench_postgres_evidence.py",
        "Workbench and Deploy CI must test its PostgreSQL evidence verifier",
    ),
    (
        "compileflow-workbench-postgres-${{ matrix.postgres }}",
        "Workbench and Deploy CI artifacts must identify the PostgreSQL version",
    ),
    (
        "Execute the production release lifecycle example",
        "Workbench and Deploy CI must execute the production release lifecycle example against PostgreSQL",
    ),
    (
        "DeploymentExampleApplicationTest",
        "Workbench and Deploy CI must reject a skipped production release lifecycle example",
    ),
    (
        "mysql:8.4.7@sha256:",
        "Workbench and Deploy CI must pin the supported MySQL service image",
    ),
    (
        "MySqlDeployRepositoryContractTest,AsyncInvocationStoreContractTest,WorkbenchExternalSchemaAdmissionTest",
        "Workbench and Deploy CI must execute all MySQL persistence contracts together",
    ),
    (
        "verify_database_contract_evidence.py",
        "Workbench and Deploy CI must reject skipped or failed MySQL evidence",
    ),
    (
        "compileflow-workbench-mysql-8.4.7",
        "Workbench and Deploy CI artifacts must identify the MySQL version",
    ),
    ("java-version: '17'", "CompileFlow Workbench Server CI must own quality gates on Java 17"),
    ("java: ['21', '25']", "CompileFlow Workbench Server CI must smoke newer supported runtimes"),
    ("java-version: ${{ matrix.java }}", "CompileFlow Workbench Server CI must select the compatibility JDK"),
]
INTEGRATION_CI_REQUIRED_GATES = [
    ("workflow_call:", "Integration CI must expose a tag release gate"),
    (
        "examples/spring-boot-basic/**",
        "Integration CI must run when the executable basic example changes",
    ),
    (
        "Run storage-free Spring Boot example",
        "Integration CI must prove that the base starter needs no Deploy runtime",
    ),
    (
        "-pl examples/spring-boot-basic",
        "Integration CI must execute the basic Spring Boot example",
    ),
    ("java: ['17', '21', '25']", "Integration CI must build all supported LTS runtimes"),
    ("Run Java 25 runtime compatibility smoke", "Integration CI must smoke Java 25 process execution"),
    ("java-version: ${{ matrix.java }}", "Integration CI must select the matrix JDK"),
    ("jacoco-aggregate/jacoco.xml", "Integration CI must publish aggregate production-code coverage"),
    ("contains no production line coverage", "Integration CI must reject empty aggregate coverage"),
    (
        "integration-test-results-jdk-${{ matrix.java }}",
        "Integration CI artifacts must identify their JDK runtime",
    ),
]
JAVA_PLATFORM_SUPPORT_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/compatibility-policy.md"),
        [
            ("Java 17 is the minimum", "Java 17 source and bytecode baseline"),
            ("maven.compiler.release=17", "compiler release contract"),
            ("jdk.compiler", "dynamic-preparation compiler module requirement"),
            ("Java 21, and Java 25 LTS", "supported LTS runtime matrix"),
            ("one set of Java artifacts", "single-artifact publishing policy"),
            ("does not publish JDK-specific classifiers", "rejection of parallel JDK artifacts"),
            ("thread strategies remain internal", "runtime thread-strategy policy"),
        ],
    ),
]
INVOCATION_POLICY_PROTOCOL_REQUIRED_FRAGMENTS = [
    ("integer, `1..100`", "bounded invocation count"),
    ("finite double, `>= 1.0`", "bounded retry backoff multiplier"),
    ("whole-millisecond ISO-8601 duration", "whole-millisecond duration precision"),
    ("at most one policy element", "unique policy declaration"),
    ("Timeout cancellation is cooperative", "cooperative timeout semantics"),
    ("JVM fatal errors (`Error`) are never retried", "fatal error propagation"),
]
WORKBENCH_DELIVERY_REQUIRED_GATES = [
    (
        "check_architecture_boundaries.py",
        "Workbench delivery verification must enforce architecture boundaries",
    ),
    (
        "-Dcheckstyle.config.location=checkstyle-javadoc.xml",
        "Workbench delivery verification must run the server Javadoc Checkstyle gate",
    ),
    (
        "-pl compileflow-api,compileflow-deploy/compileflow-deploy-api",
        "Workbench delivery Javadoc gates must target both supported API artifacts",
    ),
    ("spotbugs:check", "Workbench delivery verification must run server SpotBugs"),
    (
        "verify_spotbugs_reports.py",
        "Workbench delivery verification must reject incomplete SpotBugs analysis",
    ),
    ("javadoc:javadoc", "Workbench delivery verification must generate server Javadocs"),
]
WORKBENCH_CI_REQUIRED_GATES = [
    ("workflow_call:", "Workbench CI must expose a tag release gate"),
    ("pnpm audit --audit-level high", "Workbench CI must audit the complete pnpm lockfile"),
    (
        "pnpm --filter @compileflow/workbench-web test:coverage",
        "Workbench CI must run Web coverage tests against the current package name",
    ),
    (
        "pnpm --filter @compileflow/workbench-dev-gateway test",
        "Workbench CI must run development-gateway tests against the current package name",
    ),
    (
        "pnpm --filter @compileflow/workbench-web test:e2e:smoke",
        "Workbench CI must run Playwright smoke tests against the current package name",
    ),
    (
        "bash scripts/verify-delivery.sh --assembly-only",
        "Workbench CI must run the cross-stack assembly verification gate",
    ),
    ("actions/setup-java@", "Workbench CI delivery verification must provision Java"),
]
RETIRED_WORKBENCH_PACKAGE_FILTERS = (
    "@workbench/web",
    "@workbench/dev-gateway",
)
WORKBENCH_CI_CONTRACT_PATH_FILTERS = [
    "compileflow-workbench/**",
    "compileflow-workbench-server/**",
    "compileflow-deploy/**",
    "compileflow-spring-boot-autoconfigure/**",
    "compileflow-api/**",
    "compileflow-core/**",
    "compileflow-tbbpm/**",
    "compileflow-bpmn/**",
    ".mvn/**",
    "mvnw",
    "mvnw.cmd",
    "checkstyle.xml",
    "checkstyle-javadoc.xml",
    "scripts/check_architecture_boundaries.py",
    "scripts/verify_spotbugs_reports.py",
    "pom.xml",
]
DEPENDABOT_REQUIRED_FRAGMENTS = [
    ('package-ecosystem: "maven"', "Maven dependencies"),
    ('directory: "/"', "root Maven manifests"),
    ('package-ecosystem: "npm"', "pnpm workspace dependencies"),
    ('directory: "/compileflow-workbench"', "Workbench pnpm workspace"),
    ('package-ecosystem: "github-actions"', "GitHub Actions dependencies"),
    ('package-ecosystem: "docker"', "Docker base images"),
    ('directory: "/compileflow-workbench/docker"', "Workbench Dockerfiles"),
    ('package-ecosystem: "docker-compose"', "Docker Compose images"),
    ('directory: "/compileflow-workbench"', "Workbench Compose manifest"),
    ('update-types:', "minor and patch update grouping"),
    ("groups:", "grouped dependency update PRs"),
]
JAVA_SHARED_BUILD_PATH_FILTERS = [
    ".mvn/**",
    "mvnw",
    "mvnw.cmd",
    "checkstyle.xml",
    "checkstyle-javadoc.xml",
]
SPOTBUGS_GATE_PATH_FILTERS = [
    "scripts/verify_spotbugs_reports.py",
]
DOCUMENT_INDEX_REQUIRED_LINKS = [
    (
        Path("docs/en/README.md"),
        [
            "quick-start.md",
            "resource-management.md",
            "api-reference.md",
            "security.md",
            "threat-model.md",
            "../../SUPPORT.md",
            "../../CONTRIBUTING.md",
            "architecture/supported-surfaces.md",
        ],
    ),
    (
        Path("docs/zh/README.md"),
        [
            "../en/README.md",
            "quick-start.md",
            "resource-management.md",
            "api-reference.md",
            "security.md",
            "threat-model.md",
            "../../SUPPORT.md",
            "../../CONTRIBUTING.md",
            "architecture/supported-surfaces.md",
        ],
    ),
    (
        Path("docs/README.md"),
        [
            "../CONTRIBUTING.md",
            "../SUPPORT.md",
            "../compileflow-bom/README.md",
            "en/architecture/supported-surfaces.md",
            "zh/architecture/supported-surfaces.md",
        ],
    ),
    (
        Path("docs/en/architecture/README.md"),
        [
            "supported-surfaces.md",
        ],
    ),
]
SUPPORTED_SURFACES_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/architecture/supported-surfaces.md"),
        [
            ("ProcessEngine", "engine entry"),
            ("ProcessRef", "existing-process identity"),
            ("ProcessDefinition", "explicit process definition"),
            ("compileflow-deploy-api", "supported deployment API artifact"),
            ("compileflow-deploy-protocol", "supported deployment protocol artifact"),
            ("DeploymentProjectionStore", "deployment transport SPI"),
            ("deploy admin/runtime/integration", "unsupported deployment implementation packages"),
            ("compileflow.engine.*", "engine configuration prefix"),
            ("compileflow.deploy.*", "deploy configuration prefix"),
            ("OpenAPI description", "Operate wire contract authority"),
            ("Internal", "internal tier"),
        ],
    ),
    (
        Path("docs/zh/architecture/supported-surfaces.md"),
        [
            ("ProcessEngine", "engine entry"),
            ("ProcessRef", "existing-process identity"),
            ("ProcessDefinition", "explicit process definition"),
            ("compileflow-deploy-api", "supported deployment API artifact"),
            ("compileflow-deploy-protocol", "supported deployment protocol artifact"),
            ("DeploymentProjectionStore", "deployment transport SPI"),
            ("部署协调器", "unsupported deployment implementation packages"),
            ("compileflow.engine.*", "engine configuration prefix"),
            ("compileflow.deploy.*", "deploy configuration prefix"),
            ("OpenAPI 描述", "Operate wire contract authority"),
            ("Internal", "internal tier"),
        ],
    ),
]
API_REFERENCE_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/api-reference.md"),
        [
            ("ProcessRef", "existing-process identity"),
            ("ProcessDefinition", "explicit process definitions"),
            ("boolean isFailure();", "ProcessResult failure predicate"),
            ("ProcessError getError();", "typed ProcessResult error"),
            ("ProcessExecution getExecution();", "controlled execution attribution"),
            ("void warmUp(ProcessDefinition... definitions);", "exact runtime warm-up API"),
            ("void load(ProcessRef.Version ref, ProcessDefinition definition);", "immutable local version load API"),
            ("void unload(ProcessRef.Version... refs);", "runtime ownership release API"),
            ("ProcessPreflightReport preflight(", "tooling preflight API"),
            ("String generateJavaCode(ProcessDefinition definition);", "tooling definition API"),
            ("static ProcessPreflightOptions strict();", "preflight strict factory"),
            ("ProcessPreflightReport.OverallStatus getOverallStatus();", "preflight report status API"),
            ("ProcessEngineConfig.Builder builder();", "engine configuration builder"),
        ],
    ),
    (
        Path("docs/zh/api-reference.md"),
        [
            ("ProcessRef", "existing-process identity"),
            ("ProcessDefinition", "explicit process definitions"),
            ("boolean isFailure();", "ProcessResult failure predicate"),
            ("ProcessError getError();", "typed ProcessResult error"),
            ("ProcessExecution getExecution();", "controlled execution attribution"),
            ("void warmUp(ProcessDefinition... definitions);", "exact runtime warm-up API"),
            ("void load(ProcessRef.Version ref, ProcessDefinition definition);", "immutable local version load API"),
            ("void unload(ProcessRef.Version... refs);", "runtime ownership release API"),
            ("ProcessPreflightReport preflight(", "tooling preflight API"),
            ("String generateJavaCode(ProcessDefinition definition);", "tooling definition API"),
            ("static ProcessPreflightOptions strict();", "preflight strict factory"),
            ("ProcessPreflightReport.OverallStatus getOverallStatus();", "preflight report status API"),
            ("ProcessEngineConfig.Builder builder();", "engine configuration builder"),
        ],
    ),
]
CONFIGURATION_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/configuration.md"),
        [
            (
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL",
                "Workbench Server service principal",
            ),
            ("compileflow.engine.java-diagnostics.debug.symbols", "Java diagnostics symbols"),
            ("compileflow.deploy.artifact.mode", "deployment artifact mode"),
            ("compileflow.deploy.routing.key-prefix", "transport-independent routing key prefix"),
            ("COMPILEFLOW_DEV_GATEWAY_PORT", "development gateway port"),
            ("COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL", "development gateway log level"),
            ("VITE_COMPILEFLOW_DEBUG", "web debug build input"),
            ("public **build-time** inputs", "public Vite build-time semantics"),
        ],
    ),
    (
        Path("docs/zh/configuration.md"),
        [
            (
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL",
                "Workbench Server service principal",
            ),
            ("compileflow.engine.java-diagnostics.debug.symbols", "Java diagnostics symbols"),
            ("compileflow.deploy.artifact.mode", "deployment artifact mode"),
            ("compileflow.deploy.routing.key-prefix", "transport-independent routing key prefix"),
            ("COMPILEFLOW_DEV_GATEWAY_PORT", "development gateway port"),
            ("COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL", "development gateway log level"),
            ("VITE_COMPILEFLOW_DEBUG", "web debug build input"),
            ("公开 **构建时输入**", "public Vite build-time semantics"),
        ],
    ),
]
MODULE_MAP_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/architecture/module-map.md"),
        [
            ("compileflow-bom", "consumer dependency alignment"),
            ("compileflow-workbench-server", "server module"),
            ("compileflow-deploy-runtime", "deploy runtime module"),
            ("compileflow-deploy-jdbc", "shared Deploy JDBC implementation module"),
            ("ProcessRef", "existing-process identity"),
            ("ProcessDefinition", "explicit process definition"),
            ("CompileFlowEngineAutoConfiguration", "current Spring Boot core auto-configuration"),
            ("DeploymentRuntime", "deploy data-plane runtime"),
            ("ProcessRuntimeResolver", "core runtime provider"),
            ("AliasAdmission", "published Alias router"),
            ("DeterministicAliasSelector", "deterministic alias target selector"),
            ("ProcessRuntimeManager", "current runtime management entrypoint"),
        ],
    ),
]
EXECUTION_FLOW_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/architecture/execution-flow.md"),
        [
            ("EngineExecutionContext", "execution context lifecycle"),
            ("ProcessExecutionOptions", "isolated execution request metadata"),
            ("ProcessExecution", "controlled execution attribution"),
            ("CF_EXEC_010", "pre-execution input mapping failure"),
            ("CF_EXEC_009", "post-execution output mapping failure"),
            ("code#version", "versioned runtime cache key"),
            ("ProcessRuntimeIdentity", "runtime digest contract"),
            ("InflightRuntimeLoadRegistry", "single-flight compilation registry"),
            ("runtimeCache.install", "conditional runtime cache installation"),
            ("DefaultProcessDefinitionLoader", "explicit source-loading boundary"),
            ("runtimeCheckSync", "preflight dry-run compilation"),
            ("TriggerableProcess", "trigger-entry execution boundary"),
        ],
    ),
]
VERSION_ROUTING_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/architecture/version-routing.md"),
        [
            ("one stable version", "stable/candidate route shape"),
            ("candidateWeightBps", "basis-point candidate weight"),
            ("SHA-256", "deterministic routing hash"),
            ("present in `InstalledVersionState`", "dedicated local-installation boundary"),
            ("tombstone retains", "alias deletion high-watermark semantics"),
            ("expected Alias revision", "Alias compare-and-set precondition"),
            ("Alias revision", "execution Alias attribution"),
            ("`routingKey` is opaque", "routing-key confidentiality"),
            ("ProcessRef.alias", "explicit published-alias reference"),
        ],
    ),
]
NODE_SUPPORT_REQUIRED_FRAGMENTS = [
    (
        Path("docs/en/node-support.md"),
        [
            ("Message definition metadata", "BPMN message metadata boundary"),
            ("not a standalone", "non-executable BPMN message semantics"),
            ("standardLoopCharacteristics", "standard loop wrapper support"),
            ("multiInstanceLoopCharacteristics", "multi-instance loop wrapper support"),
            ("TbbpmElementParserRegistry", "TBBPM parser registry source of truth"),
            ("TbbpmSemanticFrontend", "TBBPM semantic frontend source of truth"),
            ("BpmnElementParserRegistry", "BPMN parser registry source of truth"),
            ("BpmnSemanticFrontend", "BPMN semantic frontend source of truth"),
            ("JavaProcessCodeGenerator", "shared compiled realization source of truth"),
            ("DurableMachineLowerer", "Durable lowering boundary source of truth"),
        ],
    ),
    (
        Path("docs/zh/node-support.md"),
        [
            ("消息定义元数据", "BPMN message metadata boundary"),
            ("不是独立可执行节点", "non-executable BPMN message semantics"),
            ("standardLoopCharacteristics", "standard loop wrapper support"),
            ("multiInstanceLoopCharacteristics", "multi-instance loop wrapper support"),
            ("TbbpmElementParserRegistry", "TBBPM parser registry source of truth"),
            ("TbbpmSemanticFrontend", "TBBPM semantic frontend source of truth"),
            ("BpmnElementParserRegistry", "BPMN parser registry source of truth"),
            ("BpmnSemanticFrontend", "BPMN semantic frontend source of truth"),
            ("JavaProcessCodeGenerator", "shared compiled realization source of truth"),
            ("DurableMachineLowerer", "Durable lowering boundary source of truth"),
        ],
    ),
]
SECURITY_SCORECARD_REQUIRED_GATES = [
    ("ossf/scorecard-action@", "Security Scorecard workflow must run OpenSSF Scorecard"),
    ("results_format: sarif", "Security Scorecard workflow must emit SARIF"),
    ("publish_results: true", "Security Scorecard workflow must publish OpenSSF results"),
    ("security-events: write", "Security Scorecard workflow must be able to upload code scanning results"),
    ("id-token: write", "Security Scorecard workflow must be able to publish Scorecard results"),
]
CODEQL_REQUIRED_GATES = [
    ("actions", "CodeQL must analyze GitHub Actions workflows"),
    ("java-kotlin", "CodeQL must analyze Java"),
    ("javascript-typescript", "CodeQL must analyze JavaScript and TypeScript"),
    ("python", "CodeQL must analyze Python release tooling"),
    ("build-mode: none", "CodeQL must avoid a duplicate Java build"),
    ("queries: security-and-quality", "CodeQL must run security and quality queries"),
    ("security-events: write", "CodeQL analysis must be able to publish code-scanning results"),
]
SUPPLY_CHAIN_REQUIRED_GATES = [
    ("./mvnw install -DskipTests", "Supply Chain inventory must install the current default reactor before BOM resolution"),
    ("cyclonedx-maven-plugin:makeAggregateBom", "Supply Chain workflow must generate a Maven aggregate SBOM"),
    ("scripts/verify_maven_sbom.py", "Supply Chain workflow must structurally verify the Maven SBOM"),
    ("target/compileflow-bom.json", "Supply Chain workflow must verify the Maven SBOM artifact"),
    ("target/compileflow-bom.sha256", "Supply Chain workflow must publish a checksum subject for the SBOM"),
    (
        "actions/attest-build-provenance@96278af6caaf10aea03fd8d33a09a777ca52d62f",
        "Supply Chain workflow must use the reviewed GitHub build-provenance action",
    ),
    ("subject-checksums: target/compileflow-bom.sha256", "Supply Chain workflow must attest the SBOM checksum subject"),
    ("attestations: write", "Supply Chain workflow must publish artifact attestations"),
    ("id-token: write", "Supply Chain workflow must grant OIDC to the attesting SBOM job"),
    ("actions/upload-artifact@", "Supply Chain workflow must upload the generated SBOM"),
]
DEV_GATEWAY_QUALITY_RULES = [
    (
        Path("compileflow-workbench/apps/dev-gateway/package.json"),
        re.compile(r'"lint"\s*:\s*"tsc --noEmit"'),
        "Development gateway lint must run ESLint, not only TypeScript",
    ),
    (
        Path("compileflow-workbench/apps/dev-gateway/package.json"),
        re.compile(r'"type-check"\s*:\s*"tsc --noEmit"'),
        "Development gateway type-check must include integration tests outside src",
    ),
]
WORKBENCH_TOOLCHAIN_REQUIRED_FRAGMENTS = [
    (
        Path("compileflow-workbench/package.json"),
        [
            ('"packageManager": "pnpm@11.11.0"', "exact pnpm release"),
            ('"node": ">=24.0.0 <25.0.0"', "Node 24 LTS engine range"),
            ('"pnpm": ">=11.11.0 <12.0.0"', "pnpm 11 engine range"),
        ],
    ),
    (
        Path("compileflow-workbench/apps/web/vite.config.ts"),
        [
            ("require('./package.json')", "Web package version source"),
            ("__COMPILEFLOW_APP_VERSION__", "build-time Web artifact version injection"),
        ],
    ),
    (
        Path("compileflow-workbench/pnpm-workspace.yaml"),
        [
            ("engineStrict: true", "strict runtime engine enforcement"),
            ("autoInstallPeers: false", "explicit peer dependency ownership"),
            ("strictPeerDependencies: true", "fail-fast peer dependency validation"),
            ("minimumReleaseAge: 1440", "dependency publication cooling period"),
            (
                "minimumReleaseAgeIgnoreMissingTime: false",
                "fail-closed dependency publication-time validation",
            ),
            ("allowBuilds:", "reviewed dependency build-script policy"),
            ("esbuild: true", "required esbuild binary verification script"),
        ],
    ),
    (
        Path(".github/workflows/workbench-ci.yml"),
        [
            ("node-version-file: compileflow-workbench/.node-version", "shared Node version source"),
            # Quote style is not semantic in YAML; accept '17', "17", and 17.
            (re.compile(r"java-version:\s*['\"]?17['\"]?"), "Java 17 delivery toolchain"),
        ],
    ),
]
DOCKER_DELIVERY_RULES = [
    (
        Path(".dockerignore"),
        [
            ("**/node_modules", "Root Docker context must exclude dependency directories"),
            ("**/target", "Root Docker context must exclude Maven build output"),
            ("**/.env*", "Root Docker context must exclude environment files"),
            (".*", "Root Docker context must exclude unapproved hidden directories"),
            ("!.mvn/**", "Root Docker context must retain the Maven Wrapper support files"),
        ],
    ),
    (
        Path("compileflow-workbench/.dockerignore"),
        [
            ("**/node_modules", "Workbench Docker context must exclude dependency directories"),
            ("**/dist", "Workbench Docker context must exclude build output"),
            ("**/.env*", "Workbench Docker context must exclude environment files"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/Dockerfile.all-in-one"),
        [
            (
                re.compile(r"FROM node:\d+\.\d+\.\d+-alpine3\.24@sha256:[0-9a-f]{64} AS web-build"),
                "Bundled image must pin its Node 24 LTS build image",
            ),
            (
                "COPY --from=web-build /workbench/apps/web/dist "
                "compileflow-workbench/apps/web/dist",
                "Bundled image must stage the compiled SPA for Maven packaging",
            ),
            ("-Pworkbench-bundled", "Bundled image must use the release packaging profile"),
            (
                "compileflow-workbench-all-in-one-*.jar",
                "Bundled image must run the all-in-one artifact",
            ),
            ("USER compileflow", "Bundled runtime image must not run as root"),
            ('ENTRYPOINT ["java", "-jar", "app.jar"]', "Bundled image must run one Java process"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/Dockerfile.workbench-server"),
        [
            ("USER compileflow", "Server runtime image must not run as root"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/Dockerfile.web"),
        [
            (
                re.compile(r"FROM node:\d+\.\d+\.\d+-alpine3\.24@sha256:[0-9a-f]{64} AS build"),
                "Web image must pin its Node 24 LTS manifest digest",
            ),
            (
                "FROM nginx:1.30.4-alpine3.24@sha256:",
                "Web runtime image must pin its nginx manifest digest",
            ),
            ("RUN corepack enable && corepack install", "Web image must use the repository-pinned pnpm version"),
            ("RUN chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx", "Web image must prepare nginx writable paths for a non-root user"),
            (
                "COPY docker/nginx.web.conf /etc/nginx/conf.d/default.conf",
                "Web image must use the static-only nginx configuration",
            ),
            ("USER nginx", "Web runtime image must not run as root"),
            ("EXPOSE 8080", "Web runtime image must expose the non-privileged nginx port"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/nginx.web.conf"),
        [
            ("listen 8080;", "Web nginx must listen on a non-privileged port"),
            ("connect-src 'self'", "Web nginx CSP must restrict browser connections to the same origin"),
            ("location = /health", "Web nginx must expose static-container health"),
            ("try_files $uri $uri/ /index.html;", "Web nginx must support SPA routes"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/Dockerfile.local-gateway"),
        [
            (
                "FROM nginx:1.30.4-alpine3.24@sha256:",
                "Local gateway image must pin its nginx manifest digest",
            ),
            (
                "/etc/nginx/conf.d",
                "Local gateway must make generated configuration writable by nginx",
            ),
            ("USER nginx", "Local gateway must not run as root"),
        ],
    ),
    (
        Path("compileflow-workbench/docker/nginx.local-gateway.conf.template"),
        [
            (
                "client_max_body_size 100m;",
                "Local gateway must not undercut the Server request-size contract",
            ),
            ("location /api/", "Local gateway must route the same-origin API"),
            (
                'proxy_set_header X-API-Key "${COMPILEFLOW_WORKBENCH_LOCAL_GATEWAY_UPSTREAM_API_KEY}";',
                "Local gateway must inject its private evaluation credential",
            ),
            (
                'proxy_set_header Authorization "";',
                "Local gateway must strip browser authorization headers",
            ),
        ],
    ),
    (
        Path("compileflow-workbench/docker-compose.yml"),
        [
            (
                "image: postgres:18.6-alpine3.24@sha256:",
                "Compose must pin the verified PostgreSQL 18 manifest digest",
            ),
            ("local-gateway:", "Split Compose must isolate its loopback credential injector"),
            (
                "COMPILEFLOW_WORKBENCH_DATABASE_PASSWORD",
                "Split Compose database secret must use the Workbench-owned namespace",
            ),
            ("stop_grace_period: 75s", "Split Compose must honor application shutdown budgets"),
        ],
    ),
    (
        Path("compileflow-workbench/docker-compose.all-in-one.yml"),
        [
            (
                "image: postgres:18.6-alpine3.24@sha256:",
                "Bundled Compose must pin the verified PostgreSQL 18 manifest digest",
            ),
            (
                "COMPILEFLOW_WORKBENCH_DATABASE_PASSWORD",
                "Bundled Compose database secret must use the Workbench-owned namespace",
            ),
            (
                "stop_grace_period: 75s",
                "Bundled Compose must honor application shutdown budgets",
            ),
        ],
    ),
]
SOFT_QUALITY_GATE_RULES = [
    (
        re.compile(
            r"<artifactId>maven-checkstyle-plugin</artifactId>(?:(?!</plugin>)[\s\S])*?"
            r"(?:<failsOnError>false</failsOnError>|<failOnViolation>false</failOnViolation>|"
            r"<violationSeverity>warning</violationSeverity>)"
        ),
        "Checkstyle must fail the build on violations",
    ),
    (
        re.compile(
            r"<artifactId>spotbugs-maven-plugin</artifactId>(?:(?!</plugin>)[\s\S])*?"
            r"<failOnError>false</failOnError>"
        ),
        "SpotBugs must fail the build on findings",
    ),
    (
        re.compile(
            r"<artifactId>maven-javadoc-plugin</artifactId>(?:(?!</plugin>)[\s\S])*?"
            r"(?:<failOnError>false</failOnError>|<failOnWarnings>false</failOnWarnings>|"
            r"-Xdoclint:none)"
        ),
        "Javadoc must fail the build on errors and warnings",
    ),
]
CHECKSTYLE_NON_ERROR_SEVERITY_RE = re.compile(
    r'<property name="severity" value="(?:info|warning)"/>'
)
DEPENDENCY_CHECK_SUPPRESSION_ANTIPATTERN_RE = re.compile(
    r"pkg:maven/org\\.junit|pkg:maven/org\\.mockito|"
    r"Uncomment and customize as needed|False positive: This vulnerability does not affect our usage|"
    r"Test dependencies are not shipped in production"
)
SPOTBUGS_EXCLUDE_ANTIPATTERN_RE = re.compile(
    r"<Bug pattern=\"(?:SE_NO_SERIALVERSIONID|SLF4J_FORMAT_SHOULD_BE_CONST|PATH_TRAVERSAL_IN)\"/>|"
    r"<Package name=\"~com\\.alibaba\\.compileflow\\.examples\\..\\*\"/>"
)
DEFAULT_BUILD_RELEASE_EXTENSION_RE = re.compile(
    r"<artifactId>central-publishing-maven-plugin</artifactId>(?:(?!</plugin>)[\s\S])*?"
    r"<extensions>true</extensions>"
)
CENTRALIZED_PLUGIN_ARTIFACTS = {
    "maven-clean-plugin",
    "maven-resources-plugin",
    "maven-jar-plugin",
    "maven-source-plugin",
    "maven-surefire-plugin",
    "maven-compiler-plugin",
    "maven-assembly-plugin",
    "maven-site-plugin",
    "maven-gpg-plugin",
    "maven-install-plugin",
    "maven-deploy-plugin",
    "maven-enforcer-plugin",
    "maven-javadoc-plugin",
    "maven-checkstyle-plugin",
    "jacoco-maven-plugin",
    "spotbugs-maven-plugin",
    "dependency-check-maven",
    "cyclonedx-maven-plugin",
}
OPERATE_CONTRACT_DRIFT_RULES = [
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/processContract.ts"),
        re.compile(r"export interface ProcessDuplicateRequest \{[^}]*\bcopyTags\?:", re.DOTALL),
        "ProcessDuplicateRequest must not expose unsupported copyTags semantics",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/processContract.ts"),
        re.compile(r"export interface ProcessListParams \{[^}]*\btags\?:", re.DOTALL),
        "ProcessListParams must not expose unsupported tag filtering",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/deployContract.ts"),
        re.compile(r"export interface RollbackDeploymentRequest\b"),
        "RollbackDeploymentRequest must not expose an unsupported rollback body",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/deployContract.ts"),
        re.compile(r"export interface DeploymentListParams \{[^}]*(?:\bsortBy\?:|\bsortOrder\?:)", re.DOTALL),
        "DeploymentListParams must not expose unsupported sorting",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/runtimeContract.ts"),
        re.compile(
            r"export interface LogFilterParams \{[^}]*"
            r"(?:\bflowType\?:|\benvironment\?:|\bsortBy\?:|\bsortOrder\?:)",
            re.DOTALL,
        ),
        "LogFilterParams must match ExecutionLogController filters",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/runtimeContract.ts"),
        re.compile(r"export interface LogSearchRequest \{[^}]*\btimeRange\?:", re.DOTALL),
        "LogSearchRequest must not expose unsupported timeRange",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/runtimeContract.ts"),
        re.compile(
            r"export interface LogExportParams \{[^}]*(?:\bformat\?:|\bincludeData\?:)",
            re.DOTALL,
        ),
        "LogExportParams must not expose unsupported export options",
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/runtimeContract.ts"),
        re.compile(r"export interface LogPurgeParams"),
        "LogPurgeParams must come from the generated server schema",
    ),
    (
        Path("compileflow-workbench/apps/web/src/operate/API_SPEC.md"),
        re.compile(r"Log filters accept[^.\n]*(?:flowType|environment|sortBy|sortOrder)"),
        "Operate API spec documents unsupported log filters",
    ),
]
OPERATE_CONTRACT_REQUIRED_FRAGMENTS = [
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/processContract.ts"),
        [
            ("sortBy?: 'name' | 'createdAt' | 'updatedAt'", "process list sort field contract"),
            ("sortOrder?: 'asc' | 'desc'", "process list sort direction contract"),
            ("keyword?: string", "process list keyword filter contract"),
        ],
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/deployContract.ts"),
        [
            ("RequeueDeploymentDeadLettersResponse", "deployment dead-letter requeue response"),
        ],
    ),
    (
        Path("compileflow-workbench/apps/web/src/shared/contracts/runtimeContract.ts"),
        [
            ("invocationId?: string", "execution/log invocation correlation filter"),
            ("requestedVersion?: string", "requested-version routing filter"),
            ("effectiveVersion?: string", "effective-version routing filter"),
            ("routingSource?: string", "routing source filter"),
            (
                "export type AsyncInvocationListParams = ServerOperationQuery<'listAsyncInvocations'>",
                "async invocation list contract",
            ),
            ("One-based page number.", "async invocation one-based pagination"),
            (
                "AsyncInvocationDeadLetterRequeueRequest",
                "async invocation dead-letter requeue request",
            ),
            ("DeploymentRuntimeDiagnostics", "deploy runtime diagnostics contract"),
        ],
    ),
    (
        Path("compileflow-workbench/apps/web/src/operate/API_SPEC.md"),
        [
            ("Allowed `sortBy` values: `name`, `createdAt`, `updatedAt`.", "flow list sort values"),
            (
                "Async invocation list pagination defaults to `page=1` and `pageSize=20`.",
                "async invocation pagination base",
            ),
            ("Persisted asynchronous routing excludes `routingKey`", "async routing key persistence boundary"),
            ("Deploy runtime diagnostics returns `available=false`", "runtime diagnostics unavailable response"),
            ("Log list and export filters accept", "log filter contract"),
        ],
    ),
]
ASSERTJ_ENFORCED_TEST_DIRS = [
    Path("compileflow-bpmn/src/test/java"),
    Path("compileflow-core/src/test/java"),
    Path("compileflow-deploy/compileflow-deploy-control-plane/src/test/java"),
    Path("compileflow-deploy/compileflow-deploy-api/src/test/java"),
    Path("compileflow-deploy/compileflow-deploy-protocol/src/test/java"),
    Path("compileflow-deploy/compileflow-deploy-runtime/src/test/java"),
    Path("compileflow-deploy/compileflow-deploy-spring-boot-autoconfigure/src/test/java"),
    Path("compileflow-integration-tests/src/test/java"),
    Path("compileflow-workbench-server/src/test/java"),
    Path("compileflow-spring-boot-autoconfigure/src/test/java"),
]
JUNIT_ASSERTION_RE = re.compile(
    r"import\s+static\s+org\.junit\.jupiter\.api\.Assertions(?:\.\*|\.[A-Za-z0-9_]+)\s*;|"
    r"\bassert(?:Equals|True|False|NotNull|Null|NotEquals|Throws|ArrayEquals|DoesNotThrow|Same|InstanceOf)\s*\("
)
TEMPORARY_MARKER_RE = re.compile(r"\b(?:TODO|FIXME|XXX|HACK)\b")
PUBLIC_DOC_ANTIPATTERN_RE = re.compile(r"\bprintStackTrace\s*\(|System\.(?:out|err)\.")
PUBLIC_DOC_BROKEN_JAVA_SNIPPET_RE = re.compile(r"}\s*else\s*\{\s*}\s*else\s*\{")
PUBLIC_DOC_JUNIT_ASSERTION_RE = re.compile(
    r"\bassert(?:Equals|True|False|NotNull|Null|NotEquals|Throws|ArrayEquals|DoesNotThrow|Same|InstanceOf)\s*\("
)
JAVA_COMMENT_RE = re.compile(r"^\s*(?://|/\*|\*).*[\u4e00-\u9fff]")
JAVADOC_BLOCK_RE = re.compile(r"/\*\*(.*?)\*/", re.DOTALL)
IN_MEMORY_DEPLOY_REPOSITORIES = [
    "InMemoryProcessAliasRepository",
    "InMemoryProcessReleaseRepository",
    "InMemoryProcessVersionRepository",
]
IN_MEMORY_DEPLOY_PRODUCTION_TYPES = [
    *IN_MEMORY_DEPLOY_REPOSITORIES,
    "InMemoryDeploymentProjectionStore",
]
BROKEN_HELPER_SCRIPT_RE = re.compile(
    r"bash-buddy/|(?<!\S)-Plint(?!\S)|spotbugs\.failOnError=false"
)


def iter_files(*suffixes: str) -> list[Path]:
    files: list[Path] = []
    for current, dirs, names in os.walk(ROOT):
        rel_parts = Path(current).relative_to(ROOT).parts
        if any(part in SKIP_DIRS for part in rel_parts):
            dirs[:] = []
            continue
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in names:
            path = Path(current) / name
            if not suffixes or path.name.endswith(suffixes):
                files.append(path)
    return files


def find_active_documentation_retired_semantics(root: Path) -> list[str]:
    """Find removed semantics presented as current outside historical records."""
    errors: list[str] = []
    for path in sorted(root.rglob("*.md")):
        relative_path = path.relative_to(root)
        if any(part in SKIP_DIRS for part in relative_path.parts):
            continue
        if path.name == "CHANGELOG.md":
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern, description in ACTIVE_DOC_RETIRED_SEMANTIC_PATTERNS:
            match = pattern.search(text)
            if match:
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{relative_path}:{line} documents retired semantics: {description}"
                )
    return errors


def markdown_heading_anchors(text: str) -> set[str]:
    """Return GitHub-style anchors for headings outside fenced code blocks."""
    anchors: set[str] = set()
    slug_counts: dict[str, int] = {}
    fence_character: str | None = None
    fence_length = 0
    for line in text.splitlines():
        fence = MARKDOWN_FENCE_RE.match(line)
        if fence_character is None:
            if fence:
                marker = fence.group(1)
                fence_character = marker[0]
                fence_length = len(marker)
                continue
        elif fence:
            marker = fence.group(1)
            if (
                marker[0] == fence_character
                and len(marker) >= fence_length
                and not fence.group(2).strip()
            ):
                fence_character = None
                fence_length = 0
            continue
        else:
            continue

        heading = MARKDOWN_HEADING_RE.match(line)
        if not heading:
            continue
        title = re.sub(r"\s+#+\s*$", "", heading.group(1))
        title = re.sub(r"<[^>]+>", "", title)
        title = re.sub(r"[`*_~]", "", title).strip().lower()
        base_slug = re.sub(r"[^\w\- ]", "", title)
        base_slug = re.sub(r"\s+", "-", base_slug)
        duplicate = slug_counts.get(base_slug, 0)
        slug_counts[base_slug] = duplicate + 1
        anchors.add(base_slug if duplicate == 0 else f"{base_slug}-{duplicate}")
    return anchors


def markdown_structure_errors(text: str) -> list[str]:
    """Return structural Markdown errors outside fenced code blocks."""
    errors: list[str] = []
    h1_lines: list[int] = []
    previous_heading_level = 0
    fence_character: str | None = None
    fence_length = 0
    fence_line = 0

    for line_number, line in enumerate(text.splitlines(), start=1):
        fence = MARKDOWN_FENCE_RE.match(line)
        if fence_character is not None:
            if fence and fence.group(1)[0] == fence_character:
                marker = fence.group(1)
                if len(marker) >= fence_length and not fence.group(2).strip():
                    fence_character = None
                    fence_length = 0
                    fence_line = 0
            continue

        if fence:
            marker = fence.group(1)
            fence_character = marker[0]
            fence_length = len(marker)
            fence_line = line_number
            continue

        heading = MARKDOWN_HEADING_RE.match(line)
        if not heading:
            continue
        heading_level = len(line) - len(line.lstrip("#"))
        if heading_level == 1:
            h1_lines.append(line_number)
        if previous_heading_level and heading_level > previous_heading_level + 1:
            errors.append(
                f"line {line_number} jumps from H{previous_heading_level} to H{heading_level}"
            )
        previous_heading_level = heading_level

    if fence_character is not None:
        errors.append(f"line {fence_line} opens an unclosed fenced code block")
    if len(h1_lines) != 1:
        locations = ", ".join(str(line) for line in h1_lines) or "none"
        errors.append(f"expected exactly one H1 outside code blocks; found {locations}")
    if text and not text.endswith("\n"):
        errors.append("file must end with a newline")
    return errors


def check_markdown_structure() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for error in markdown_structure_errors(text):
            errors.append(f"{path.relative_to(ROOT)}: {error}")
    return errors


def find_unknown_documented_pnpm_scripts(root: Path) -> list[str]:
    """Reject documented root pnpm commands that are not declared scripts or pnpm built-ins."""
    package_json = root / "compileflow-workbench" / "package.json"
    if not package_json.exists():
        return []
    scripts = set(json.loads(package_json.read_text(encoding="utf-8")).get("scripts", {}))
    errors: list[str] = []
    for current, directories, names in os.walk(root):
        directories[:] = sorted(
            directory for directory in directories if directory not in SKIP_DIRS
        )
        for name in sorted(names):
            if not name.endswith(".md"):
                continue
            path = Path(current) / name
            relative_path = path.relative_to(root)
            for line_number, line in enumerate(
                path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1
            ):
                match = PNPM_COMMAND_RE.match(line)
                if not match:
                    continue
                command = match.group(1)
                if (
                    command.startswith("-")
                    or command in PNPM_BUILTIN_COMMANDS
                    or command in scripts
                ):
                    continue
                errors.append(
                    f"{relative_path}:{line_number} documents unknown root pnpm script {command!r}"
                )
    return errors


def check_documented_pnpm_scripts() -> list[str]:
    return find_unknown_documented_pnpm_scripts(ROOT)


def check_markdown_links() -> list[str]:
    errors: list[str] = []
    anchor_cache: dict[Path, set[str]] = {}
    for md_file in iter_files(".md"):
        text = md_file.read_text(encoding="utf-8", errors="replace")
        for target in MARKDOWN_LINK_RE.findall(text):
            if (
                "://" in target
                or target.startswith("mailto:")
                or target.startswith("tel:")
            ):
                continue
            link_path, separator, fragment = target.partition("#")
            link_path = link_path.strip()
            resolved = md_file.resolve() if not link_path else (md_file.parent / link_path).resolve()
            try:
                resolved.relative_to(ROOT)
            except ValueError:
                errors.append(f"{md_file.relative_to(ROOT)} links outside repository: {target}")
                continue
            if not resolved.exists():
                errors.append(f"{md_file.relative_to(ROOT)} has missing link target: {target}")
                continue
            anchor_target = resolved / "README.md" if resolved.is_dir() else resolved
            if not separator or not fragment or anchor_target.suffix.lower() != ".md":
                continue
            anchors = anchor_cache.get(anchor_target)
            if anchors is None:
                anchors = markdown_heading_anchors(
                    anchor_target.read_text(encoding="utf-8", errors="replace")
                )
                anchor_cache[anchor_target] = anchors
            normalized_fragment = unquote(fragment).strip().lower()
            if normalized_fragment not in anchors:
                errors.append(
                    f"{md_file.relative_to(ROOT)} has missing Markdown anchor: {target}"
                )
    return errors


def check_issue_templates() -> list[str]:
    errors: list[str] = []
    template_dir = ROOT / ".github" / "ISSUE_TEMPLATE"
    if not template_dir.exists():
        return errors
    markdown_templates = sorted(template_dir.glob("*.md"))
    if markdown_templates:
        names = ", ".join(str(p.relative_to(ROOT)) for p in markdown_templates)
        errors.append(f"Use YAML issue forms only; remove Markdown issue templates: {names}")
    config_path = template_dir / "config.yml"
    if config_path.exists():
        config_text = config_path.read_text(encoding="utf-8", errors="replace")
        if "github.com/alibaba/compileflow/discussions" in config_text:
            errors.append("Issue contacts must not link to the unavailable GitHub Discussions channel")
        if "github.com/alibaba/compileflow/tree/master/docs/examples" in config_text:
            errors.append("Issue contacts must link to the runnable root examples, not docs/examples")
        if "github.com/alibaba/compileflow/tree/master/examples" not in config_text:
            errors.append("Issue contacts must link to the runnable root examples")
    return errors


def check_generated_artifacts() -> list[str]:
    return [
        f"Generated Java build output must not be present: {rel_path}"
        for rel_path in find_java_build_output_bins(ROOT)
    ]


def find_java_build_output_bins(root: Path) -> list[Path]:
    """Find IDE compiler output copied into a Maven module tree."""
    matches: list[Path] = []
    for directory, child_names, _ in os.walk(root):
        child_names[:] = [name for name in child_names if name not in SKIP_DIRS]
        path = Path(directory)
        if path.name != "bin":
            continue
        has_java_tree = (path / "src" / "main" / "java").is_dir() or (
            path / "src" / "test" / "java"
        ).is_dir()
        if (path / "pom.xml").is_file() or has_java_tree or next(path.rglob("*.class"), None):
            matches.append(path.relative_to(root))
            child_names.clear()
    return sorted(matches)


def check_tracked_local_tooling_files() -> list[str]:
    errors: list[str] = []
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return errors
    for line in result.stdout.splitlines():
        rel = Path(line)
        if not (ROOT / rel).exists():
            continue
        if not rel.parts or not rel.parts[0].startswith("."):
            continue
        if any(rel == allowed or allowed in rel.parents for allowed in ALLOWED_TRACKED_ROOT_HIDDEN_PATHS):
            continue
        errors.append(f"Unapproved root-level hidden path must not be tracked: {rel.as_posix()}")
    return errors


def is_generated_repository_path(path: Path) -> bool:
    return (
        any(part in TRACKED_GENERATED_DIR_NAMES for part in path.parts)
        or path.suffix in TRACKED_GENERATED_FILE_SUFFIXES
        or path.name in TRACKED_GENERATED_FILE_NAMES
    )


def check_tracked_generated_artifacts() -> list[str]:
    errors: list[str] = []
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        return errors
    for raw_path in result.stdout.split(b"\0"):
        if not raw_path:
            continue
        rel = Path(os.fsdecode(raw_path))
        if not (ROOT / rel).exists():
            continue
        if (
            rel not in ALLOWED_TRACKED_BINARY_PATHS
            and is_generated_repository_path(rel)
        ):
            errors.append(f"Generated artifact must not be tracked by Git: {rel.as_posix()}")
    return errors


def check_submodule_metadata() -> list[str]:
    errors: list[str] = []
    gitmodules = ROOT / ".gitmodules"
    if not gitmodules.exists():
        return errors
    paths = re.findall(r"(?m)^\s*path\s*=\s*(.+?)\s*$", gitmodules.read_text(encoding="utf-8", errors="replace"))
    result = subprocess.run(
        ["git", "ls-files", "-s"],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return errors
    gitlink_paths = set()
    for line in result.stdout.splitlines():
        parts = line.split(None, 3)
        if len(parts) == 4 and parts[0] == "160000":
            gitlink_paths.add(parts[3])
    for path in paths:
        if path not in gitlink_paths:
            errors.append(f".gitmodules declares {path}, but no matching git submodule is tracked")
    return errors


def check_helper_scripts_are_current() -> list[str]:
    errors: list[str] = []
    scripts_dir = ROOT / "scripts"
    if not scripts_dir.exists():
        return errors
    for path in sorted(scripts_dir.iterdir()):
        if not path.is_file() or path.name == "check_internal_links.py":
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = BROKEN_HELPER_SCRIPT_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} references obsolete helper wiring: {match.group(0)}")
    return errors


def check_internal_only_references() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md", ".yml", ".yaml"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern in INTERNAL_ONLY_PATTERNS:
            if pattern in text:
                errors.append(f"{path.relative_to(ROOT)} contains internal or missing reference: {pattern}")
    return errors


def check_insecure_secret_defaults() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md", ".yml", ".yaml", ".env.example", "Dockerfile"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern in INSECURE_SECRET_DEFAULT_PATTERNS:
            if pattern in text:
                errors.append(f"{path.relative_to(ROOT)} contains insecure example secret default: {pattern}")
    return errors


def check_process_artifacts() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md", ".yml", ".yaml"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern in PROCESS_ARTIFACT_PATTERNS:
            if pattern in text:
                errors.append(f"{path.relative_to(ROOT)} contains process-artifact attribution: {pattern}")
    return errors


def check_translation_placeholders() -> list[str]:
    errors: list[str] = []
    docs_dir = ROOT / "docs"
    if not docs_dir.exists():
        return errors
    for path in sorted(docs_dir.rglob("*.md")):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = TRANSLATION_PLACEHOLDER_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} contains incomplete translation placeholder: {match.group(0)}")
    return errors


def check_process_stage_language() -> list[str]:
    errors: list[str] = []
    guarded_paths = [
        ROOT / "checkstyle.xml",
        ROOT / "checkstyle-javadoc.xml",
    ]
    for path in iter_files(".md", ".xml"):
        if not any(path == guarded or guarded in path.parents for guarded in guarded_paths):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for pattern in PROCESS_STAGE_PATTERNS:
            match = pattern.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains process-stage language: {match.group(0)}")
    return errors


def check_unsupported_architecture_claims() -> list[str]:
    errors: list[str] = []
    architecture_dirs = (
        ROOT / "docs" / "en" / "architecture",
        ROOT / "docs" / "zh" / "architecture",
    )
    for architecture_dir in architecture_dirs:
        if not architecture_dir.exists():
            continue
        for path in sorted(architecture_dir.rglob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            match = UNSUPPORTED_ARCHITECTURE_CLAIM_RE.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains unsupported architecture claim: {match.group(0)}")
            match = STALE_ARCHITECTURE_EXAMPLE_RE.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains stale architecture example: {match.group(0)}")
    return errors


def check_nonstandard_markdown_links() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if KFILE_RE.search(text):
            errors.append(f"{path.relative_to(ROOT)} uses nonstandard <kfile> links")
    return errors


def check_reproducible_install_documentation() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = LOCKFILE_DELETION_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} tells users to delete a lockfile: {match.group(0)}")
        match = STALE_TYPESCRIPT_DOC_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} documents a stale TypeScript minor version: {match.group(0)}")
    return errors


def check_workflow_path_filters() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "\n    paths:" not in text:
            continue
        rel_path = str(path.relative_to(ROOT))
        if f"'{rel_path}'" not in text and f'"{rel_path}"' not in text and f"- {rel_path}" not in text:
            errors.append(f"{path.relative_to(ROOT)} has path filters but does not include itself")
    return errors


def check_workflow_permissions() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if not re.search(r"(?m)^permissions:\n  contents: read(?:\n|$)", text):
            errors.append(f"{path.relative_to(ROOT)} must declare top-level permissions: contents: read")
    return errors


def check_workflow_actions_are_pinned() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in UNPINNED_ACTION_RE.finditer(text):
            errors.append(
                f"{path.relative_to(ROOT)} uses unpinned GitHub Action: "
                f"{match.group(1)}@{match.group(2)}"
            )
    return errors


def check_workflow_checkout_credentials_not_persisted() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        for index, line in enumerate(lines):
            if "uses: actions/checkout@" not in line:
                continue
            window = "\n".join(lines[index + 1:index + 8])
            if "persist-credentials: false" not in window:
                errors.append(
                    f"{path.relative_to(ROOT)}:{index + 1} uses actions/checkout without persist-credentials: false"
                )
    return errors


def check_required_workflows() -> list[str]:
    errors: list[str] = []
    required = [
        ROOT / ".github" / "workflows" / "java-core-ci.yml",
        ROOT / ".github" / "workflows" / "workbench-server-ci.yml",
        ROOT / ".github" / "workflows" / "workbench-ci.yml",
        ROOT / ".github" / "workflows" / "check-doc-links.yml",
        ROOT / ".github" / "workflows" / "security-scorecard.yml",
        ROOT / ".github" / "workflows" / "codeql.yml",
        ROOT / ".github" / "workflows" / "java-security.yml",
        ROOT / ".github" / "workflows" / "release-build.yml",
        ROOT / ".github" / "workflows" / "supply-chain.yml",
        ROOT / ".github" / "workflows" / "dependency-review.yml",
    ]
    for path in required:
        if not path.exists():
            errors.append(f"Required workflow is missing: {path.relative_to(ROOT)}")
    hygiene = ROOT / ".github" / "workflows" / "check-doc-links.yml"
    if hygiene.exists():
        text = hygiene.read_text(encoding="utf-8", errors="replace")
        for fragment, description in (
            ("actionlint", "semantic GitHub Actions validation"),
            ("ACTIONLINT_SHA256", "checksum verification for the actionlint binary"),
            ("sha256sum --check", "enforcement of the actionlint checksum"),
            ("check_release_baselines.py", "pull-request release baseline consistency validation"),
            ("scripts/check_dco.py", "per-commit DCO sign-off enforcement"),
            ("DCO_BASE_SHA", "trusted pull-request base identity for the DCO range"),
            ("DCO_HEAD_SHA", "trusted pull-request head identity for the DCO range"),
        ):
            if fragment not in text:
                errors.append(
                    f"{hygiene.relative_to(ROOT)} must include {description}"
                )
    return errors


def check_java_ci_baseline() -> list[str]:
    errors: list[str] = []
    guarded_paths = sorted((ROOT / ".github" / "workflows").glob("*.yml")) + [
        ROOT / "CONTRIBUTING.md",
        ROOT / "docs" / "zh" / "quick-start.md",
    ]
    for path in guarded_paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_JAVA_CI_BASELINE_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} does not validate the Java 17 baseline: {match.group(0)}")
    return errors


def check_maven_toolchain_baseline() -> list[str]:
    errors: list[str] = []
    guarded_paths = [
        ROOT / "pom.xml",
        ROOT / "CONTRIBUTING.md",
        ROOT / "docs" / "zh" / "quick-start.md",
    ]
    for path in guarded_paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_MAVEN_BASELINE_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} documents a stale Maven baseline: {match.group(0)}")

    pom = ROOT / "pom.xml"
    if pom.exists():
        text = pom.read_text(encoding="utf-8", errors="replace")
        required_fragments = [
            "<maven.compiler.release>17</maven.compiler.release>",
            "<source>${maven.compiler.release}</source>",
            "<requireMavenVersion>",
            "<version>[3.9.16,4.0.0)</version>",
            "<requireJavaVersion>",
            "<version>[17,)</version>",
            "<dependencyConvergence/>",
            "<bannedDependencies>",
        ]
        for fragment in required_fragments:
            if fragment not in text:
                errors.append(f"pom.xml Maven Enforcer configuration must include {fragment}")
        plugin_match = re.search(
            r"<artifactId>maven-enforcer-plugin</artifactId>(?P<body>[\s\S]*?)</plugin>",
            text,
        )
        if plugin_match:
            body = plugin_match.group("body")
            if body.find("<configuration>") == -1 or body.find("<executions>") == -1:
                errors.append("pom.xml Maven Enforcer plugin must configure rules and bind an execution")
            elif body.find("<configuration>") > body.find("<executions>"):
                errors.append("pom.xml Maven Enforcer rules must live at plugin level, not only under an execution")
    return errors


def check_maven_wrapper_integrity() -> list[str]:
    errors: list[str] = []
    properties_path = ROOT / ".mvn" / "wrapper" / "maven-wrapper.properties"
    jar_path = ROOT / ".mvn" / "wrapper" / "maven-wrapper.jar"
    legacy_downloader = ROOT / ".mvn" / "wrapper" / "MavenWrapperDownloader.java"

    if not properties_path.exists():
        errors.append(".mvn/wrapper/maven-wrapper.properties is required")
        return errors
    if not jar_path.exists():
        errors.append(".mvn/wrapper/maven-wrapper.jar is required")
        return errors
    if legacy_downloader.exists():
        errors.append(".mvn/wrapper/MavenWrapperDownloader.java is obsolete with Maven Wrapper 3.3.4")

    properties: dict[str, str] = {}
    for line in properties_path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        properties[key.strip()] = value.strip()

    for key, expected in MAVEN_WRAPPER_REQUIRED_PROPERTIES.items():
        actual = properties.get(key)
        if actual != expected:
            errors.append(f".mvn/wrapper/maven-wrapper.properties must set {key}={expected}")

    for key in ("distributionUrl", "wrapperUrl"):
        value = properties.get(key, "")
        if value.startswith("http://") or "nexus" in value or "corp" in value:
            errors.append(f".mvn/wrapper/maven-wrapper.properties uses a non-public wrapper URL for {key}: {value}")

    actual_hash = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    expected_hash = properties.get("wrapperSha256Sum")
    if expected_hash and actual_hash != expected_hash:
        errors.append(
            ".mvn/wrapper/maven-wrapper.jar SHA-256 does not match wrapperSha256Sum: "
            f"{actual_hash}"
        )
    return errors


def check_text_normalization_policy() -> list[str]:
    errors: list[str] = []
    files = [
        (ROOT / ".editorconfig", EDITORCONFIG_REQUIRED_FRAGMENTS),
        (ROOT / ".gitattributes", GITATTRIBUTES_REQUIRED_FRAGMENTS),
    ]
    for path, required_fragments in files:
        rel_path = path.relative_to(ROOT)
        if not path.exists():
            errors.append(f"{rel_path} is required")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must contain {fragment}")
    return errors


def workflow_fragment_present(text: str, fragment: str | re.Pattern[str]) -> bool:
    """Match workflow contracts without coupling them to YAML presentation."""
    if isinstance(fragment, re.Pattern):
        return fragment.search(text) is not None
    if fragment in text:
        return True
    compact = lambda value: re.sub(r"[\s'\"]+", "", value)
    return compact(fragment) in compact(text)


def check_java_core_ci_quality_gates() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "java-core-ci.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in JAVA_CORE_CI_REQUIRED_GATES:
        if not workflow_fragment_present(text, required):
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    return errors


def check_server_ci_quality_gates() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "workbench-server-ci.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in SERVER_CI_REQUIRED_GATES:
        if not workflow_fragment_present(text, required):
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    return errors


def check_integration_ci_runtime_matrix() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "integration-tests.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in INTEGRATION_CI_REQUIRED_GATES:
        if not workflow_fragment_present(text, required):
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    if text.count("- 'examples/spring-boot-basic/**'") != 2:
        errors.append(
            f"{path.relative_to(ROOT)}: push and pull-request filters must "
            "both include examples/spring-boot-basic/**"
        )
    if text.count("- 'examples/spring-boot-order-fulfillment/**'") != 2:
        errors.append(
            f"{path.relative_to(ROOT)}: push and pull-request filters must "
            "both include examples/spring-boot-order-fulfillment/**"
        )
    return errors


def check_java_platform_support_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in JAVA_PLATFORM_SUPPORT_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required Java platform support documentation is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    pom = ROOT / "pom.xml"
    if not pom.exists():
        errors.append("pom.xml is required for the Java platform contract")
    else:
        pom_text = pom.read_text(encoding="utf-8", errors="replace")
        if "<checkstyle.version>12.3.1</checkstyle.version>" not in pom_text:
            errors.append("pom.xml must use Java 17-compatible Checkstyle 12.3.1")
    return errors


def check_workbench_delivery_quality_gates() -> list[str]:
    errors: list[str] = []
    path = ROOT / "compileflow-workbench" / "scripts" / "verify-delivery.sh"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    normalized = re.sub(r"\\\r?\n\s*", " ", text)
    maven_commands: list[list[str]] = []
    for line in normalized.splitlines():
        marker = "./mvnw "
        if marker not in line:
            continue
        command = line.split(marker, 1)[1].rsplit(")", 1)[0]
        try:
            maven_commands.append(shlex.split(command))
        except ValueError:
            continue

    has_clean_server_build = any(
        "clean" in command
        and "install" in command
        and "-pl" in command
        and command[command.index("-pl") + 1] == "compileflow-workbench-server"
        and "-am" in command
        and "-DskipTests" in command
        and "-Djacoco.skip=true" in command
        for command in maven_commands
        if "-pl" in command and command.index("-pl") + 1 < len(command)
    )
    if not has_clean_server_build:
        errors.append(
            f"{path.relative_to(ROOT)}: Workbench delivery verification must "
            "clean-build current server reactor artifacts without stale coverage data"
        )
    for required, message in WORKBENCH_DELIVERY_REQUIRED_GATES:
        if required not in text:
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    return errors


def check_workbench_ci_delivery_gate() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "workbench-ci.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in WORKBENCH_CI_REQUIRED_GATES:
        if required not in text:
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    for required in WORKBENCH_CI_CONTRACT_PATH_FILTERS:
        if f"'{required}'" not in text and f'"{required}"' not in text and f"- {required}" not in text:
            errors.append(f"{path.relative_to(ROOT)} path filters must include {required}")
    return errors


def check_retired_workbench_package_filters() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for retired in RETIRED_WORKBENCH_PACKAGE_FILTERS:
            if retired in text:
                errors.append(
                    f"{path.relative_to(ROOT)} uses retired pnpm package filter {retired}"
                )
    return errors


def check_java_workflow_shared_build_path_filters() -> list[str]:
    errors: list[str] = []
    workflow_paths = [
        ROOT / ".github" / "workflows" / "java-core-ci.yml",
        ROOT / ".github" / "workflows" / "workbench-server-ci.yml",
        ROOT / ".github" / "workflows" / "integration-tests.yml",
    ]
    for path in workflow_paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for required in JAVA_SHARED_BUILD_PATH_FILTERS:
            if f"'{required}'" not in text and f'"{required}"' not in text and f"- {required}" not in text:
                errors.append(f"{path.relative_to(ROOT)} path filters must include {required}")
    spotbugs_workflow_paths = [
        ROOT / ".github" / "workflows" / "java-core-ci.yml",
        ROOT / ".github" / "workflows" / "workbench-server-ci.yml",
        ROOT / ".github" / "workflows" / "workbench-ci.yml",
    ]
    for path in spotbugs_workflow_paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for required in SPOTBUGS_GATE_PATH_FILTERS:
            if f"'{required}'" not in text and f'"{required}"' not in text and f"- {required}" not in text:
                errors.append(f"{path.relative_to(ROOT)} path filters must include {required}")
    return errors


def check_security_scorecard_workflow() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "security-scorecard.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in SECURITY_SCORECARD_REQUIRED_GATES:
        if required not in text:
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    return errors


def check_codeql_workflow() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "codeql.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in CODEQL_REQUIRED_GATES:
        if required not in text:
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    return errors


def check_supply_chain_workflow() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "workflows" / "supply-chain.yml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for required, message in SUPPLY_CHAIN_REQUIRED_GATES:
        if required not in text:
            errors.append(f"{path.relative_to(ROOT)}: {message}")
    for workflow_input in (
        "scripts/verify_maven_sbom.py",
        "scripts/tests/test_verify_maven_sbom.py",
        ".github/workflows/java-security.yml",
    ):
        if text.count(f"- '{workflow_input}'") != 2:
            errors.append(
                "Supply Chain push and pull-request filters must include "
                f"{workflow_input}"
            )
    pom = ROOT / "pom.xml"
    if pom.exists():
        pom_text = pom.read_text(encoding="utf-8", errors="replace")
        required_pom_fragments = [
            "<cyclonedx-maven-plugin.version>",
            "<artifactId>cyclonedx-maven-plugin</artifactId>",
            "<outputName>compileflow-bom</outputName>",
            "<includeTestScope>false</includeTestScope>",
        ]
        for fragment in required_pom_fragments:
            if fragment not in pom_text:
                errors.append(f"pom.xml must configure CycloneDX SBOM generation: {fragment}")
    return errors


def find_java_security_release_gate_errors(root: Path) -> list[str]:
    """Require one reliable, fail-closed Java dependency scan for scheduled and release evidence."""
    errors: list[str] = []
    workflow_dir = root / ".github" / "workflows"
    security = workflow_dir / "java-security.yml"
    supply_chain = workflow_dir / "supply-chain.yml"
    release = workflow_dir / "release.yml"
    required_files = (security, supply_chain, release)
    for path in required_files:
        if not path.exists():
            errors.append(f"Required Java security gate input is missing: {path.relative_to(root)}")
    if errors:
        return errors

    security_text = security.read_text(encoding="utf-8", errors="replace")
    for fragment, description in (
        ("workflow_call:", "reusable workflow entrypoint"),
        ("NVD_API_KEY:", "NVD credential contract"),
        ("required: false", "optional NVD credential"),
        ("-Psecurity-scan", "OWASP Dependency-Check Maven profile"),
        ("concurrency:", "serialized NVD access"),
        ("actions/cache/restore@", "vulnerability database cache restore"),
        ("actions/cache/save@", "vulnerability database cache save"),
        ("-DnvdApiDelay=", "anonymous NVD rate-limit protection"),
        ("actions/upload-artifact@", "dependency report evidence"),
    ):
        if fragment not in security_text:
            errors.append(f"{security.relative_to(root)} must include {description}")

    reusable_call = "uses: ./.github/workflows/java-security.yml"
    secret_mapping = "NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"
    supply_chain_text = supply_chain.read_text(encoding="utf-8", errors="replace")
    for fragment, description in (
        (reusable_call, "the reusable Java security workflow"),
        (secret_mapping, "the NVD credential mapping"),
    ):
        if fragment not in supply_chain_text:
            errors.append(f"{supply_chain.relative_to(root)} must include {description}")

    release_text = release.read_text(encoding="utf-8", errors="replace")
    for fragment, description in (
        ("java-security-candidate-evidence:", "same-commit Java security evidence job"),
        (reusable_call, "the reusable Java security workflow"),
        (secret_mapping, "the NVD credential mapping"),
        ("- java-security-candidate-evidence", "a blocking build dependency on Java security evidence"),
    ):
        if fragment not in release_text:
            errors.append(f"{release.relative_to(root)} must include {description}")
    return errors


def check_java_security_release_gate() -> list[str]:
    return find_java_security_release_gate_errors(ROOT)


def check_codecov_token_guard() -> list[str]:
    errors: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return errors
    for path in sorted(workflow_dir.glob("*.yml")):
        text = path.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if "uses: codecov/codecov-action@" in line:
                previous = "\n".join(lines[max(0, index - 3):index])
                following = "\n".join(lines[index + 1:index + 12])
                if "if:" not in previous or "env.CODECOV_TOKEN != ''" not in previous:
                    errors.append(
                        f"{path.relative_to(ROOT)}:{index + 1} uses Codecov "
                        "without an env.CODECOV_TOKEN guard"
                    )
                if "CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}" not in text:
                    errors.append(
                        f"{path.relative_to(ROOT)} must map the Codecov secret "
                        "to job-level CODECOV_TOKEN"
                    )
                if "token: ${{ env.CODECOV_TOKEN }}" not in following:
                    errors.append(
                        f"{path.relative_to(ROOT)}:{index + 1} must pass the "
                        "job-level CODECOV_TOKEN to the Codecov action"
                    )
    return errors


def check_dependabot_coverage() -> list[str]:
    errors: list[str] = []
    path = ROOT / ".github" / "dependabot.yml"
    if not path.exists():
        errors.append(".github/dependabot.yml is required")
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for fragment, description in DEPENDABOT_REQUIRED_FRAGMENTS:
        if fragment not in text:
            errors.append(f"{path.relative_to(ROOT)} must cover {description}")
    return errors


def find_release_attestation_permission_errors(root: Path) -> list[str]:
    """Require both sides of the reusable release workflow to grant attestation authority."""
    errors: list[str] = []
    workflow_dir = root / ".github" / "workflows"
    for name in ("release.yml", "release-build.yml"):
        path = workflow_dir / name
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for permission in ("attestations: write", "id-token: write"):
            if permission not in text:
                errors.append(
                    f"{path.relative_to(root)} must grant {permission} for release attestations"
                )
    return errors


def check_release_document_set() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        rel_path = path.relative_to(ROOT)
        if path.name.upper() == "CHANGELOG.MD" and rel_path != Path("CHANGELOG.md"):
            errors.append(f"Use the root CHANGELOG.md only; remove duplicate changelog: {rel_path}")
    workflow = ROOT / ".github" / "workflows" / "release.yml"
    build_workflow = ROOT / ".github" / "workflows" / "release-build.yml"
    if not workflow.exists():
        errors.append(".github/workflows/release.yml is required")
    else:
        text = workflow.read_text(encoding="utf-8", errors="replace")
        if not build_workflow.exists():
            errors.append(".github/workflows/release-build.yml is required")
        else:
            text += "\n" + build_workflow.read_text(encoding="utf-8", errors="replace")
        required = [
            (
                "uses: ./.github/workflows/release-build.yml",
                "release workflow must delegate build and attestation to the trusted reusable workflow",
            ),
            ("workflow_call:", "release build workflow must be reusable"),
            ("Verify tag and project version", "release tags must be checked against the Maven version"),
            (
                "scripts/check_release_baselines.py --online",
                "release workflow must confirm current database and container baselines",
            ),
            (
                "uses: ./.github/workflows/workbench-ci.yml",
                "release workflow must require same-commit Workbench delivery evidence",
            ),
            (
                "scripts/check_project_versions.py",
                "release workflow must validate Maven and Workbench version alignment",
            ),
            ("--release", "release workflow must reject snapshot versions"),
            (
                'release_version="${RELEASE_TAG#v}"',
                "release workflow must derive the project version from the release tag",
            ),
            ("git merge-base --is-ancestor", "release tags must originate from the protected main branch"),
            (
                "scripts/resolve_api_compatibility_baseline.py",
                "release workflow must resolve the reachable same-MAJOR API baseline",
            ),
            (
                "steps.api-compat.outputs.baseline",
                "release workflow must gate compatibility checks on the resolved baseline",
            ),
            (
                "-Papi-compat",
                "release workflow must enforce supported API binary compatibility",
            ),
            (
                "uses: ./.github/workflows/integration-tests.yml",
                "release workflow must rerun same-commit Engine integration evidence",
            ),
            (
                "uses: ./.github/workflows/durable-ci.yml",
                "release workflow must rerun same-commit Durable evidence",
            ),
            (
                "uses: ./.github/workflows/workbench-server-ci.yml",
                "release workflow must rerun same-commit Workbench Server evidence",
            ),
            (
                "needs:\n"
                "      - engine-candidate-evidence\n"
                "      - durable-candidate-evidence\n"
                "      - workbench-server-candidate-evidence\n"
                "      - workbench-delivery-candidate-evidence",
                "release build must wait for all same-commit product evidence",
            ),
            ("./mvnw clean install", "release artifacts must pass verify and be installed locally for SBOM resolution"),
            (
                "-pl examples/spring-boot-basic",
                "release workflow must execute the basic Spring Boot example",
            ),
            (
                "examples/spring-boot-order-fulfillment",
                "release workflow must execute the realistic order-fulfillment example",
            ),
            ("-Prelease-artifacts", "release workflow must use the artifact attachment profile"),
            ("Missing sources for", "release workflow must require source JARs"),
            ("Missing Javadoc for", "release workflow must require Javadoc JARs"),
            ("Missing META-INF/LICENSE", "release workflow must require license text in binary JARs"),
            ("Missing META-INF/NOTICE", "release workflow must require notice text in binary JARs"),
            ("Incorrect Implementation-Version", "release workflow must verify JAR implementation versions"),
            ("expected_artifacts=(", "release workflow must enumerate the complete artifact set"),
            ("compileflow-deploy-jdbc", "release workflow must include the shared Deploy JDBC artifact"),
            ("compileflow-workbench-server", "release workflow must include the executable Server"),
            (
                "compileflow-workbench-all-in-one-",
                "release workflow must include the bundled Workbench application",
            ),
            (
                "-Pworkbench-bundled",
                "release workflow must build the bundled Workbench packaging profile",
            ),
            ("Unexpected release JAR set", "release workflow must reject extra release artifacts"),
            ("SHA256SUMS", "release workflow must publish a checksum manifest"),
            ("compileflow-bom.json", "release workflow must publish the aggregate SBOM"),
            (
                "cyclonedx-maven-plugin:makeBom@workbench-application",
                "release workflow must generate the separate Workbench application SBOM",
            ),
            (
                "dependency:copy@workbench-sbom-loader",
                "release workflow must resolve the exact Spring Boot loader for application verification",
            ),
            (
                "python3 scripts/verify_workbench_sbom.py",
                "release workflow must verify application dependencies against the distributed JAR",
            ),
            (
                "cp compileflow-workbench-server/target/compileflow-workbench-server-bom.json staging/",
                "release workflow must publish the Workbench application SBOM",
            ),
            (
                "compileflow-workbench-web-bom.json",
                "release workflow must publish the Workbench Web SBOM",
            ),
            ("verify_source_archive.py", "release workflow must validate a deterministic source archive"),
            ("*-source.tar.gz", "release provenance must cover the complete source archive"),
            ("Verify release source archive bootstrap", "release workflow must test the distributed source archive"),
            ("./mvnw -version", "release source archive must bootstrap its Maven Wrapper"),
            (
                "actions/attest-build-provenance@96278af6caaf10aea03fd8d33a09a777ca52d62f",
                "release build must use the reviewed GitHub build-provenance action",
            ),
            ("subject-checksums: staging/SHA256SUMS", "release provenance must cover every checksum subject"),
            ("gh attestation verify", "release instructions must use GitHub attestation verification"),
            ("--signer-workflow", "release verification must bind provenance to the reusable build workflow"),
            ("Publish complete GitHub Release", "the final job must publish the complete release"),
        ]
        for fragment, description in required:
            if not workflow_fragment_present(text, fragment):
                errors.append(f"release workflows: {description}")
        verify_skips_tests = re.search(
            r"\./mvnw clean (?:verify|install)[^\n]*\\\n"
            r"(?:[^\n]*\\\n)*"
            r"[^\n]*-DskipTests",
            text,
        )
        if "-DperformRelease" in text or verify_skips_tests:
            errors.append(
                "release workflows must not use the implicit Maven release "
                "profile or skip release verification tests"
            )
        errors.extend(find_release_attestation_permission_errors(ROOT))
    freshness = ROOT / ".github" / "workflows" / "release-baseline-freshness.yml"
    if not freshness.exists():
        errors.append(".github/workflows/release-baseline-freshness.yml is required")
    else:
        freshness_text = freshness.read_text(encoding="utf-8", errors="replace")
        for fragment in ("schedule:", "scripts/check_release_baselines.py --online"):
            if fragment not in freshness_text:
                errors.append(
                    ".github/workflows/release-baseline-freshness.yml must schedule the official upstream check"
                )
    pom = ROOT / "pom.xml"
    if pom.exists():
        pom_text = pom.read_text(encoding="utf-8", errors="replace")
        if "<project.build.outputTimestamp>" not in pom_text:
            errors.append("pom.xml must define project.build.outputTimestamp for reproducible archives")
        if "<targetPath>META-INF</targetPath>" not in pom_text:
            errors.append("pom.xml must package root LICENSE and NOTICE files under META-INF")
        if "<addDefaultImplementationEntries>true</addDefaultImplementationEntries>" not in pom_text:
            errors.append("pom.xml must add implementation identity to JAR manifests")
        for profile in ["release-artifacts", "sign-artifacts", "publish-central"]:
            if f"<id>{profile}</id>" not in pom_text:
                errors.append(f"pom.xml must define the explicit {profile} profile")
        if "<autoPublish>false</autoPublish>" not in pom_text:
            errors.append("pom.xml must require manual Central publication after validation")
        if "<waitUntil>validated</waitUntil>" not in pom_text:
            errors.append("pom.xml must wait for Central validation before succeeding")
        if "<name>performRelease</name>" in pom_text:
            errors.append("pom.xml must not activate release behavior through performRelease")
    integration_pom = ROOT / "compileflow-integration-tests" / "pom.xml"
    if integration_pom.exists():
        integration_text = integration_pom.read_text(encoding="utf-8", errors="replace")
        if "<maven.deploy.skip>true</maven.deploy.skip>" not in integration_text:
            errors.append("compileflow-integration-tests must be excluded from repository deployment")
        if "<maven.install.skip>true</maven.install.skip>" not in integration_text:
            errors.append("compileflow-integration-tests must not install an empty artifact")
        if "<goal>test-jar</goal>" in integration_text:
            errors.append("compileflow-integration-tests must not publish an unconsumed test JAR")
    non_publishable_poms = [
        ROOT / "compileflow-workbench-server" / "pom.xml",
        ROOT / "compileflow-benchmarks" / "pom.xml",
        ROOT / "examples" / "spring-boot-basic" / "pom.xml",
        ROOT / "examples" / "spring-boot-order-fulfillment" / "pom.xml",
        ROOT / "examples" / "spring-boot-deployment" / "pom.xml",
        ROOT / "examples" / "spring-boot-durable-postgresql" / "pom.xml",
    ]
    for non_publishable_pom in non_publishable_poms:
        if not non_publishable_pom.exists():
            errors.append(
                f"Required non-publishable Maven project is missing: "
                f"{non_publishable_pom.relative_to(ROOT)}"
            )
            continue
        non_publishable_text = non_publishable_pom.read_text(encoding="utf-8", errors="replace")
        if "<maven.deploy.skip>true</maven.deploy.skip>" not in non_publishable_text:
            errors.append(
                f"{non_publishable_pom.relative_to(ROOT)} must be excluded from repository deployment"
            )
    published_parent_poms = [
        ROOT / "pom.xml",
        ROOT / "compileflow-deploy" / "pom.xml",
        ROOT / "compileflow-durable" / "pom.xml",
    ]
    for published_parent_pom in published_parent_poms:
        if not published_parent_pom.exists():
            errors.append(
                f"Required published Maven parent is missing: "
                f"{published_parent_pom.relative_to(ROOT)}"
            )
            continue
        parent_text = published_parent_pom.read_text(encoding="utf-8", errors="replace")
        if "<maven.deploy.skip>true</maven.deploy.skip>" in parent_text:
            errors.append(
                f"{published_parent_pom.relative_to(ROOT)} is a consumed Maven parent "
                "and must be deployed with its children"
            )
    return errors


def check_governance_document_set() -> list[str]:
    errors: list[str] = []
    required_root_docs = {
        Path("CODE_OF_CONDUCT.md"): ["Contributor Covenant"],
        Path("CONTRIBUTING.md"): ["Pull Request", "Developer Certificate of Origin 1.1"],
        Path("MAINTAINERS.md"): ["@yusu1210", ".github/CODEOWNERS", "review", "Access Management"],
        Path("SECURITY.md"): ["GitHub Security Advisories", "CycloneDX VEX", "Repository Credential Policy"],
        Path("SUPPORT.md"): ["no dedicated Q&A channel", "GitHub Security Advisories", "Support Scope"],
        Path("docs/en/threat-model.md"): ["TM-01", "TM-11", "CycloneDX VEX"],
        Path("docs/zh/threat-model.md"): ["TM-01", "TM-11", "CycloneDX VEX"],
    }
    for rel_path, required_fragments in required_root_docs.items():
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required governance document is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document: {fragment}")
    for path in iter_files(".md"):
        rel_path = path.relative_to(ROOT)
        if path.name.upper() != "CODE_OF_CONDUCT.MD" or rel_path == Path("CODE_OF_CONDUCT.md"):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if "../CODE_OF_CONDUCT.md" not in text and "../../CODE_OF_CONDUCT.md" not in text:
            errors.append(f"{rel_path} must point to the root CODE_OF_CONDUCT.md")
    codeowners_path = ROOT / ".github" / "CODEOWNERS"
    if not codeowners_path.exists():
        errors.append(".github/CODEOWNERS is required")
    else:
        codeowners_text = codeowners_path.read_text(encoding="utf-8", errors="replace")
        for fragment in [
            "* @yusu1210",
            "/compileflow-api/",
            "/compileflow-core/",
            "/compileflow-deploy/",
            "/compileflow-workbench-server/",
            "/compileflow-workbench/",
            "/docs/",
        ]:
            if fragment not in codeowners_text:
                errors.append(f".github/CODEOWNERS must route ownership for {fragment}")
    return errors


def check_document_index_required_links() -> list[str]:
    errors: list[str] = []
    for rel_path, required_links in DOCUMENT_INDEX_REQUIRED_LINKS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required documentation index is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for link in required_links:
            if link not in text:
                errors.append(f"{rel_path} must link to {link}")
    return errors


def check_adopter_logos() -> list[str]:
    """Keep the root adoption section and its local vector assets complete and uniform."""
    errors: list[str] = []
    readme = ROOT / "README.md"
    text = readme.read_text(encoding="utf-8", errors="replace")
    if "## Adopters" not in text:
        errors.append("README.md must include the Adopters section")
    for name in (
        "alibaba.svg",
        "taobao.svg",
        "tmall.svg",
        "alipay.svg",
        "aliyun.svg",
        "aliexpress.svg",
        "lazada.svg",
        "fliggy.svg",
    ):
        relative_path = Path("docs/assets/images/adopters") / name
        logo_path = ROOT / relative_path
        if not logo_path.exists():
            errors.append(f"Adopter logo is missing: {relative_path}")
            continue
        if relative_path.as_posix() not in text:
            errors.append(f"README.md must display adopter logo: {relative_path}")
        image_tag = re.compile(
            rf'<img\s+src="{re.escape(relative_path.as_posix())}"[^>]*'
            r'width="64"\s+height="64"',
        )
        if not image_tag.search(text):
            errors.append(f"README.md must render {relative_path} at 64 by 64 pixels")
        try:
            root = ET.parse(logo_path).getroot()
        except ET.ParseError as exc:
            errors.append(f"Adopter logo is not valid XML: {relative_path}: {exc}")
            continue
        view_box = root.attrib.get("viewBox", "").split()
        try:
            view_box_numbers = [float(value) for value in view_box]
        except ValueError:
            view_box_numbers = []
        if len(view_box_numbers) != 4 or abs(view_box_numbers[2] - view_box_numbers[3]) > 0.01:
            errors.append(f"Adopter logo must use a square viewBox: {relative_path}")
        if root.attrib.get("role") != "img" or root.attrib.get("aria-labelledby") != "title":
            errors.append(f"Adopter logo must expose an accessible image title: {relative_path}")
        direct_titles = [
            child for child in root
            if child.tag.rsplit("}", 1)[-1] == "title" and child.attrib.get("id") == "title"
        ]
        if not direct_titles or not (direct_titles[0].text or "").strip():
            errors.append(f"Adopter logo must contain a non-empty title: {relative_path}")
        for element in root.iter():
            local_name = element.tag.rsplit("}", 1)[-1]
            if local_name in {"image", "text"}:
                errors.append(
                    f"Adopter logo must remain self-contained vector paths: {relative_path} contains {local_name}"
                )
                break
            for attribute, value in element.attrib.items():
                if attribute.rsplit("}", 1)[-1] == "href" and (
                    value.startswith(("http://", "https://", "data:"))
                ):
                    errors.append(f"Adopter logo must not load external or embedded raster data: {relative_path}")
                    break
    return errors


def check_api_reference_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in API_REFERENCE_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required API reference is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_configuration_reference_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in CONFIGURATION_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required configuration reference is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")

    source_contracts = [
        (
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
            / "config"
            / "CompileFlowWorkbenchServerEnvironmentPostProcessor.java",
            r"\bCOMPILEFLOW_WORKBENCH_SERVER_CONFIG_[A-Z0-9_]+\b",
            [ROOT / "docs" / "en" / "configuration.md", ROOT / "docs" / "zh" / "configuration.md"],
        ),
        (
            ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / "src" / "config.ts",
            r"\bCOMPILEFLOW_DEV_GATEWAY_[A-Z0-9_]+\b",
            [
                ROOT / "docs" / "en" / "configuration.md",
                ROOT / "docs" / "zh" / "configuration.md",
                ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / ".env.example",
            ],
        ),
        (
            ROOT / "compileflow-workbench" / "apps" / "web" / "src" / "shared" / "config"
            / "buildConfigSchema.ts",
            r"\bVITE_COMPILEFLOW_[A-Z0-9_]+\b",
            [
                ROOT / "docs" / "en" / "configuration.md",
                ROOT / "docs" / "zh" / "configuration.md",
                ROOT / "compileflow-workbench" / "apps" / "web" / ".env.example",
                ROOT / "compileflow-workbench" / "docker" / "Dockerfile.web",
            ],
        ),
    ]
    for source, pattern, consumers in source_contracts:
        if not source.exists():
            errors.append(f"Configuration source is missing: {source.relative_to(ROOT)}")
            continue
        names = sorted(set(re.findall(pattern, source.read_text(encoding="utf-8", errors="replace"))))
        for consumer in consumers:
            if not consumer.exists():
                errors.append(f"Configuration contract consumer is missing: {consumer.relative_to(ROOT)}")
                continue
            consumer_text = consumer.read_text(encoding="utf-8", errors="replace")
            for name in names:
                if name not in consumer_text:
                    errors.append(
                        f"{consumer.relative_to(ROOT)} does not document configuration input {name} "
                        f"from {source.relative_to(ROOT)}"
                    )
    return errors


def check_module_map_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in MODULE_MAP_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required architecture module map is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_execution_flow_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in EXECUTION_FLOW_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required architecture execution flow is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_EXECUTION_FLOW_DOC_RE.search(text)
        if match:
            errors.append(f"{rel_path} documents stale execution-flow internals: {match.group(0)}")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_version_routing_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in VERSION_ROUTING_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required architecture version routing guide is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_node_support_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in NODE_SUPPORT_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required node support guide is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_public_documentation_code_antipatterns() -> list[str]:
    errors: list[str] = []
    for rel_root in [
        Path("README.md"),
        Path("CONTRIBUTING.md"),
        Path("docs"),
        Path("compileflow-integration-tests/README.md"),
    ]:
        paths = [ROOT / rel_root] if rel_root.suffix else sorted((ROOT / rel_root).rglob("*.md"))
        for path in paths:
            if not path.exists():
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            match = PUBLIC_DOC_ANTIPATTERN_RE.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains copy-paste unsafe Java example: {match.group(0)}")
            match = PUBLIC_DOC_BROKEN_JAVA_SNIPPET_RE.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains a broken duplicate else Java snippet")
            match = PUBLIC_DOC_JUNIT_ASSERTION_RE.search(text)
            if match:
                errors.append(f"{path.relative_to(ROOT)} contains copy-paste unsafe JUnit assertion: {match.group(0)}")
    return errors


def check_stale_java_baseline_references() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md", ".yml", ".yaml", ".xml", "Dockerfile"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_JAVA_BASELINE_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} contains stale Java 8 build-baseline text: {match.group(0)}")
    return errors


def check_action_policy_protocol() -> list[str]:
    errors: list[str] = []
    process_model = ROOT / "docs" / "en" / "architecture" / "process-model.md"
    if not process_model.exists():
        errors.append("docs/en/architecture/process-model.md is required")
    else:
        protocol_text = process_model.read_text(encoding="utf-8", errors="replace")
        for fragment, description in INVOCATION_POLICY_PROTOCOL_REQUIRED_FRAGMENTS:
            if fragment not in protocol_text:
                errors.append(f"docs/en/architecture/process-model.md must document {description}")

    for path in iter_files(".md", ".bpm", ".bpmn", ".xml", ".ts", ".tsx", ".java"):
        rel_path = path.relative_to(ROOT)
        rel_text = rel_path.as_posix()
        if "/src/test/" in f"/{rel_text}" or "__tests__" in rel_path.parts:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_INVOCATION_POLICY_RE.search(text)
        if match:
            errors.append(f"{rel_path} uses a removed InvocationPolicy surface: {match.group(0)}")
    return errors


def check_phantom_configuration_references() -> list[str]:
    errors: list[str] = []
    config_docs = [
        ROOT / "docs" / "en" / "configuration.md",
        ROOT / "docs" / "zh" / "configuration.md",
        ROOT / "docs" / "en" / "troubleshooting.md",
        ROOT / "docs" / "zh" / "troubleshooting.md",
        ROOT / "docs" / "en" / "monitoring.md",
        ROOT / "docs" / "zh" / "monitoring.md",
        ROOT / "docs" / "en" / "hot-deploy.md",
        ROOT / "docs" / "zh" / "hot-deploy.md",
        ROOT / "docs" / "en" / "hot-deploy-integration.md",
        ROOT / "docs" / "zh" / "hot-deploy-integration.md",
        ROOT / "docs" / "zh" / "README.md",
        ROOT / "compileflow-deploy" / "README.md",
        ROOT / "compileflow-workbench" / ".env.example",
        ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / ".env.example",
        ROOT / "compileflow-workbench" / "apps" / "web" / ".env.example",
        ROOT / "compileflow-workbench" / "apps" / "web" / ".env.development",
    ]
    for path in config_docs:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = PHANTOM_CONFIG_RE.search(text)
        if match:
            errors.append(
                f"{path.relative_to(ROOT)} contains unsupported configuration: {match.group(0)}"
            )
    return errors


def check_micrometer_documentation_contract() -> list[str]:
    """Keep the bilingual monitoring tables aligned with built-in binders."""
    errors: list[str] = []
    source_paths = [
        Path(
            "compileflow-spring-boot-autoconfigure/src/main/java/com/alibaba/compileflow/engine/"
            "spring/boot/autoconfigure/observability/CompileFlowMetricsBinder.java"
        ),
        Path(
            "compileflow-deploy/compileflow-deploy-spring-boot-autoconfigure/src/main/java/com/alibaba/compileflow/deploy/"
            "spring/boot/autoconfigure/observability/CompileFlowDeploymentMetricsBinder.java"
        ),
        Path(
            "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/src/main/java/com/alibaba/"
            "compileflow/durable/spring/boot/autoconfigure/observability/DurableRuntimeMetricsBinder.java"
        ),
    ]
    implemented: set[str] = set()
    for rel_path in source_paths:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required Micrometer binder is missing: {rel_path}")
            continue
        implemented.update(
            extract_micrometer_metric_names(
                path.read_text(encoding="utf-8", errors="replace")
            )
        )

    for rel_path in [Path("docs/en/monitoring.md"), Path("docs/zh/monitoring.md")]:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required monitoring guide is missing: {rel_path}")
            continue
        documented = set(
            DOCUMENTED_MICROMETER_METRIC_RE.findall(
                path.read_text(encoding="utf-8", errors="replace")
            )
        )
        missing = sorted(implemented - documented)
        stale = sorted(documented - implemented)
        if missing:
            errors.append(
                f"{rel_path} is missing built-in Micrometer metrics: {', '.join(missing)}"
            )
        if stale:
            errors.append(
                f"{rel_path} documents unregistered Micrometer metrics: {', '.join(stale)}"
            )
    return errors


def check_stale_api_documentation() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md", ".yml", ".yaml"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_API_DOC_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} documents stale public API usage: {match.group(0).splitlines()[0]}")
    return errors


def check_active_documentation_retired_semantics() -> list[str]:
    return find_active_documentation_retired_semantics(ROOT)


def check_removed_2_0_surfaces_in_user_docs() -> list[str]:
    """User docs must not present deleted 2.0 surfaces as current APIs."""
    errors: list[str] = []
    allowed_mentions = {
        Path("docs/en/architecture/supported-surfaces.md"),
        Path("CHANGELOG.md"),
    }
    user_doc_roots = [ROOT / "README.md", ROOT / "docs" / "en", ROOT / "docs" / "zh", ROOT / "CONTRIBUTING.md"]
    for path in iter_files(".md"):
        rel = path.relative_to(ROOT)
        if rel in allowed_mentions:
            continue
        if not any(path == root or root in path.parents for root in user_doc_roots):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = REMOVED_2_0_SURFACE_RE.search(text)
        if match:
            errors.append(
                f"{rel} documents a removed 2.0 surface as current usage: {match.group(0)}"
            )
    return errors


def check_supported_surfaces_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in SUPPORTED_SURFACES_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required supported-surfaces document is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    empty_extension = (
        ROOT
        / "compileflow-core"
        / "src"
        / "main"
        / "java"
        / "com"
        / "alibaba"
        / "compileflow"
        / "engine"
        / "core"
        / "extension"
    )
    if empty_extension.exists():
        errors.append(
            "compileflow-core/.../extension must be removed; SPI lives in compileflow-api"
        )
    notice = ROOT / "NOTICE"
    if not notice.exists():
        errors.append("NOTICE is required for Apache-licensed redistribution hygiene")
    sample_readme = ROOT / "examples" / "spring-boot-basic" / "README.md"
    if not sample_readme.exists():
        errors.append("examples/spring-boot-basic/README.md is required as the runnable Quick Start sample")
    realistic_sample_readme = ROOT / "examples" / "spring-boot-order-fulfillment" / "README.md"
    if not realistic_sample_readme.exists():
        errors.append(
            "examples/spring-boot-order-fulfillment/README.md is required as the realistic runnable sample"
        )
    api_poms = [
        (Path("compileflow-api/pom.xml"), "jacoco-check-api"),
        (
            Path("compileflow-deploy/compileflow-deploy-api/pom.xml"),
            "jacoco-check-deploy-api",
        ),
        (
            Path("compileflow-deploy/compileflow-deploy-protocol/pom.xml"),
            "jacoco-check-deploy-protocol",
        ),
    ]
    for rel_path, coverage_execution in api_poms:
        api_pom = ROOT / rel_path
        if not api_pom.exists():
            errors.append(f"{rel_path} is required for the supported Java API")
            continue
        api_pom_text = api_pom.read_text(encoding="utf-8", errors="replace")
        if "japicmp-maven-plugin" not in api_pom_text:
            errors.append(
                f"{rel_path} must declare japicmp-maven-plugin for API compatibility gates"
            )
        if coverage_execution not in api_pom_text:
            errors.append(
                f"{rel_path} must enforce a JaCoCo coverage check for its API module"
            )
    return errors


def check_stale_deploy_documentation() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_DEPLOY_DOC_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} documents stale deploy API usage: {match.group(0)}")
    return errors


def check_stale_durable_recovery_documentation() -> list[str]:
    """Current docs must describe processId-based recovery and lease-token fencing."""
    errors: list[str] = []
    for path in iter_files(".md"):
        rel_path = path.relative_to(ROOT)
        if rel_path == Path("CHANGELOG.md"):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_DURABLE_RECOVERY_DOC_RE.search(text)
        if match:
            errors.append(
                f"{rel_path} documents stale Durable recovery semantics: "
                f"{match.group(0).splitlines()[0]}"
            )
    return errors


def check_unsupported_rollout_documentation() -> list[str]:
    errors: list[str] = []
    for path in iter_files(".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = UNSUPPORTED_ROLLOUT_DOC_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} documents unsupported rollout behavior: {match.group(0)}")
    return errors


def check_release_metadata() -> list[str]:
    errors: list[str] = []
    for path in [ROOT / "pom.xml"]:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = STALE_RELEASE_METADATA_RE.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} contains stale release metadata: {match.group(0)}")
    for path in iter_files(".java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "Licensed to the Apache Software Foundation" in text:
            errors.append(
                f"{path.relative_to(ROOT)} must not claim ASF project ownership"
            )
    return errors


def check_release_plugins_are_profile_scoped() -> list[str]:
    errors: list[str] = []
    path = ROOT / "pom.xml"
    if not path.exists():
        return errors
    default_model = path.read_text(encoding="utf-8", errors="replace").split("<profiles>", 1)[0]
    if DEFAULT_BUILD_RELEASE_EXTENSION_RE.search(default_model):
        errors.append(
            "pom.xml loads Maven Central publishing as a default build extension; "
            "scope release extensions to a release profile"
        )
    return errors


def check_dependency_check_configuration() -> list[str]:
    errors: list[str] = []
    pom = ROOT / "pom.xml"
    if pom.exists():
        text = pom.read_text(encoding="utf-8", errors="replace")
        if "<artifactId>dependency-check-maven</artifactId>" in text:
            required = [
                ("<failBuildOnCVSS>7.0</failBuildOnCVSS>", "security scan CVSS threshold"),
                ("<skipTestScope>true</skipTestScope>", "test-scope dependency exclusion"),
            ]
            for fragment, description in required:
                if fragment not in text:
                    errors.append(f"pom.xml dependency-check profile must configure {description}")
    suppression = ROOT / "dependency-check-suppression.xml"
    if suppression.exists():
        text = suppression.read_text(encoding="utf-8", errors="replace")
        match = DEPENDENCY_CHECK_SUPPRESSION_ANTIPATTERN_RE.search(text)
        if match:
            errors.append(
                "dependency-check-suppression.xml contains placeholder or blanket suppressions: "
                f"{match.group(0)}"
            )
    return errors


def check_spotbugs_exclusions_are_specific() -> list[str]:
    errors: list[str] = []
    path = ROOT / "spotbugs-exclude.xml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    match = SPOTBUGS_EXCLUDE_ANTIPATTERN_RE.search(text)
    if match:
        errors.append(f"spotbugs-exclude.xml contains a broad or stale exclusion: {match.group(0)}")
    return errors


def check_plugin_versions_are_centralized() -> list[str]:
    errors: list[str] = []
    path = ROOT / "pom.xml"
    if not path.exists():
        return errors
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    root = ET.parse(path).getroot()
    for plugin in root.findall(".//m:plugin", ns):
        artifact_id = plugin.findtext("m:artifactId", default="", namespaces=ns)
        if artifact_id not in CENTRALIZED_PLUGIN_ARTIFACTS:
            continue
        version = plugin.findtext("m:version", default="", namespaces=ns).strip()
        if version and not version.startswith("${"):
            errors.append(
                f"pom.xml hard-codes {artifact_id} version {version}; "
                "use the matching plugin version property"
            )
    return errors


def check_security_policy() -> list[str]:
    errors: list[str] = []
    path = ROOT / "SECURITY.md"
    if not path.exists():
        errors.append("SECURITY.md is required")
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    required = [
        "GitHub Security Advisories",
        "Security fixes are provided for the current `2.x` line",
        "Security Automation",
        "Finding And Exception Policy",
        "CycloneDX VEX",
        "Repository Credential Policy",
    ]
    for phrase in required:
        if phrase not in text:
            errors.append(f"SECURITY.md must document: {phrase}")
    match = STALE_SECURITY_POLICY_RE.search(text)
    if match:
        errors.append(f"SECURITY.md hard-codes a supported release line: {match.group(0)}")
    return errors


def read_project_version() -> str:
    text = (ROOT / "pom.xml").read_text(encoding="utf-8", errors="replace")
    match = PROJECT_VERSION_RE.search(text)
    return match.group(1) if match else ""


def check_top_level_maven_modules_are_declared() -> list[str]:
    errors: list[str] = []
    root_pom = ROOT / "pom.xml"
    if not root_pom.exists():
        return errors
    declared_modules = set(MODULE_RE.findall(root_pom.read_text(encoding="utf-8", errors="replace")))
    for pom in sorted(ROOT.glob("*/pom.xml")):
        module_dir = pom.parent.name
        if module_dir.startswith("."):
            continue
        if module_dir not in declared_modules:
            errors.append(f"Top-level Maven module is not declared in root pom.xml: {module_dir}")
    return errors


def check_bom_dependency_alignment() -> list[str]:
    """Require the consumer BOM to manage every published CompileFlow JAR exactly once."""
    errors: list[str] = []
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    bom_path = ROOT / "compileflow-bom" / "pom.xml"
    if not bom_path.exists():
        return ["compileflow-bom/pom.xml is required"]

    published_artifacts: set[str] = set()
    for pom in sorted(ROOT.rglob("pom.xml")):
        relative_path = pom.relative_to(ROOT)
        if relative_path == Path("pom.xml") or any(
            part in SKIP_DIRS for part in relative_path.parts
        ):
            continue
        project = ET.parse(pom).getroot()
        artifact_id = project.findtext(
            "m:artifactId", default="", namespaces=namespace
        ).strip()
        packaging = project.findtext(
            "m:packaging", default="jar", namespaces=namespace
        ).strip()
        deploy_skip = project.findtext(
            "m:properties/m:maven.deploy.skip", default="", namespaces=namespace
        ).strip()
        if packaging == "jar" and deploy_skip != "true":
            published_artifacts.add(artifact_id)

    bom = ET.parse(bom_path).getroot()
    managed_entries = bom.findall(
        "m:dependencyManagement/m:dependencies/m:dependency", namespace
    )
    managed_artifacts = [
        entry.findtext("m:artifactId", default="", namespaces=namespace).strip()
        for entry in managed_entries
        if entry.findtext("m:groupId", default="", namespaces=namespace).strip()
        == "com.alibaba.compileflow"
    ]
    duplicate_artifacts = sorted(
        artifact_id
        for artifact_id in set(managed_artifacts)
        if managed_artifacts.count(artifact_id) > 1
    )
    missing_artifacts = sorted(published_artifacts - set(managed_artifacts))
    extra_artifacts = sorted(set(managed_artifacts) - published_artifacts)
    if duplicate_artifacts:
        errors.append(
            "compileflow-bom duplicates managed artifacts: "
            + ", ".join(duplicate_artifacts)
        )
    if missing_artifacts:
        errors.append(
            "compileflow-bom does not manage published artifacts: "
            + ", ".join(missing_artifacts)
        )
    if extra_artifacts:
        errors.append(
            "compileflow-bom manages non-published JAR artifacts: "
            + ", ".join(extra_artifacts)
        )
    return errors


def check_user_docs_release_versions() -> list[str]:
    errors: list[str] = []
    project_version = read_project_version()
    project_is_snapshot = project_version.endswith("-SNAPSHOT")
    user_doc_roots = [
        ROOT / "README.md",
        ROOT / "docs",
    ]
    for path in iter_files(".md"):
        if not any(path == root or root in path.parents for root in user_doc_roots):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if project_is_snapshot and MAVEN_CENTRAL_BADGE_RE.search(text):
            errors.append(f"{path.relative_to(ROOT)} advertises Maven Central for an unreleased snapshot project")
        for match in COMPILEFLOW_DEPENDENCY_RE.finditer(text):
            version = match.group(1)
            if project_is_snapshot and version != project_version:
                errors.append(
                    f"{path.relative_to(ROOT)} documents CompileFlow dependency version {version}; "
                    f"use {project_version} until a stable release is tagged"
                )
            if not project_is_snapshot and version.endswith("-SNAPSHOT"):
                errors.append(f"{path.relative_to(ROOT)} documents a snapshot dependency version: {match.group(0)}")
        if project_is_snapshot and f"<version>{project_version}</version>" in text:
            if "./mvnw install -pl" not in text:
                errors.append(
                    f"{path.relative_to(ROOT)} documents snapshot dependencies without a scoped local install command"
                )
    return errors


def check_quality_gates() -> list[str]:
    errors: list[str] = []
    path = ROOT / "pom.xml"
    if not path.exists():
        return errors
    text = path.read_text(encoding="utf-8", errors="replace")
    for pattern, message in SOFT_QUALITY_GATE_RULES:
        match = pattern.search(text)
        if match:
            errors.append(f"{path.relative_to(ROOT)} has a soft quality gate: {message}")
    return errors


def check_operate_contract_drift() -> list[str]:
    errors: list[str] = []
    for rel_path, pattern, message in OPERATE_CONTRACT_DRIFT_RULES:
        path = ROOT / rel_path
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = pattern.search(text)
        if match:
            errors.append(f"{rel_path}: {message}: {match.group(0).splitlines()[0]}")
    return errors


def check_checkstyle_configs_are_enforcing() -> list[str]:
    errors: list[str] = []
    for rel_path in [Path("checkstyle.xml"), Path("checkstyle-javadoc.xml")]:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"{rel_path} is required")
            continue
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"{rel_path} is not well-formed XML: {exc}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if rel_path.name.startswith("checkstyle") and CHECKSTYLE_NON_ERROR_SEVERITY_RE.search(text):
            errors.append(f"{rel_path} uses non-error severity for an enforced Checkstyle rule")
    pom = ROOT / "pom.xml"
    if pom.exists():
        pom_text = pom.read_text(encoding="utf-8", errors="replace")
        required_fragments = [
            "<checkstyle.config.location>checkstyle.xml</checkstyle.config.location>",
            "<configLocation>${checkstyle.config.location}</configLocation>",
            "<show>public</show>",
        ]
        for fragment in required_fragments:
            if fragment not in pom_text:
                errors.append(f"pom.xml must make the Checkstyle ruleset selectable with {fragment}")
    return errors


def check_operate_contract_required_fragments() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in OPERATE_CONTRACT_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required Operate contract file is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            if fragment not in text:
                errors.append(f"{rel_path} must document {description}")
    return errors


def check_workbench_runtime_boundaries() -> list[str]:
    errors: list[str] = []
    web_src = ROOT / "compileflow-workbench" / "apps" / "web" / "src"
    if web_src.exists():
        build_config = web_src / "shared" / "config" / "buildConfig.ts"
        for path in sorted(web_src.rglob("*")):
            if not path.is_file() or path.suffix not in {".ts", ".tsx"}:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            if "import.meta.env" in text and path != build_config:
                errors.append(f"{path.relative_to(ROOT)} reads import.meta.env outside buildConfig.ts")
            forbidden_server_keys = (
                "COMPILEFLOW_BRIDGE_UPSTREAM_API_KEY",
                "COMPILEFLOW_WORKBENCH_SERVER_AUTHENTICATION_API_KEY",
                "COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY",
            )
            if any(key in text for key in forbidden_server_keys):
                errors.append(f"{path.relative_to(ROOT)} exposes a server-side API key name to browser code")
    dev_gateway_src = ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / "src"
    if dev_gateway_src.exists():
        config_path = dev_gateway_src / "config.ts"
        for path in sorted(dev_gateway_src.rglob("*")):
            if not path.is_file() or path.suffix != ".ts":
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            if path != config_path and "process.env" in text:
                errors.append(f"{path.relative_to(ROOT)} reads process environment directly; use config.ts")
    operate_api = web_src / "operate" / "api"
    if operate_api.exists():
        for path in sorted(operate_api.glob("*.ts")):
            if path.name.startswith("mock") or path.name.endswith(".test.ts"):
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            if "apiClient." in text and "isOperateMockMode()" not in text:
                errors.append(f"{path.relative_to(ROOT)} calls apiClient without an Operate mock-mode guard")
    return errors


def check_dev_gateway_quality_gates() -> list[str]:
    errors: list[str] = []
    test_tsconfig = ROOT / "compileflow-workbench" / "apps" / "dev-gateway" / "tsconfig.test.json"
    if not test_tsconfig.exists():
        errors.append("compileflow-workbench/apps/dev-gateway/tsconfig.test.json is required")
    for rel_path, pattern, message in DEV_GATEWAY_QUALITY_RULES:
        path = ROOT / rel_path
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = pattern.search(text)
        if match:
            errors.append(f"{rel_path}: {message}: {match.group(0)}")
    return errors


def check_workbench_toolchain_contract() -> list[str]:
    errors: list[str] = []
    for rel_path, required_fragments in WORKBENCH_TOOLCHAIN_REQUIRED_FRAGMENTS:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required Workbench toolchain file is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, description in required_fragments:
            found = (
                fragment.search(text) is not None
                if isinstance(fragment, re.Pattern)
                else fragment in text
            )
            if not found:
                errors.append(f"{rel_path} must define {description}")
    return errors


def check_deploy_inmemory_repositories_not_production_registered() -> list[str]:
    errors: list[str] = []
    source_roots = [
        ROOT / "compileflow-deploy" / "compileflow-deploy-api" / "src" / "main",
        ROOT / "compileflow-deploy" / "compileflow-deploy-control-plane" / "src" / "main",
        ROOT / "compileflow-deploy" / "compileflow-deploy-spring-boot-autoconfigure" / "src" / "main",
    ]
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*")):
            if not path.is_file():
                continue
            rel_path = path.relative_to(ROOT)
            text = path.read_text(encoding="utf-8", errors="replace")
            if path.suffix == ".java" and path.stem in IN_MEMORY_DEPLOY_PRODUCTION_TYPES:
                errors.append(
                    f"{rel_path} publishes an in-memory deploy authority or transport"
                )
            if "META-INF/services" in rel_path.as_posix():
                for repository in IN_MEMORY_DEPLOY_REPOSITORIES:
                    if repository in text:
                        errors.append(f"{rel_path} registers {repository} through SPI")
            if "spring" in rel_path.as_posix() and "autoconfigure" in rel_path.as_posix():
                for repository in IN_MEMORY_DEPLOY_REPOSITORIES:
                    if repository in text:
                        errors.append(f"{rel_path} wires {repository} in production auto-configuration")
    return errors


def check_docker_delivery_rules() -> list[str]:
    errors: list[str] = []
    pinned_image = re.compile(r"^[^\s@]+(?:[:][^\s@]+)?@sha256:[0-9a-f]{64}$")
    for rel_path, required_fragments in DOCKER_DELIVERY_RULES:
        path = ROOT / rel_path
        if not path.exists():
            errors.append(f"Required Dockerfile is missing: {rel_path}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for fragment, message in required_fragments:
            if not workflow_fragment_present(text, fragment):
                errors.append(f"{rel_path}: {message}")
        for line_number, line in enumerate(text.splitlines(), start=1):
            from_match = re.match(r"^\s*FROM\s+(\S+)", line, re.IGNORECASE)
            compose_match = re.match(r"^\s*image:\s*(\S+)", line)
            image = from_match.group(1) if from_match else compose_match.group(1) if compose_match else None
            if image is None or image.lower() == "scratch":
                continue
            if not pinned_image.fullmatch(image):
                errors.append(
                    f"{rel_path}:{line_number} container image must use tag@sha256:<64 hex>: {image}"
                )
    return errors


def check_assertion_policy() -> list[str]:
    errors: list[str] = []
    for rel_test_dir in ASSERTJ_ENFORCED_TEST_DIRS:
        test_dir = ROOT / rel_test_dir
        if not test_dir.exists():
            continue
        for path in sorted(test_dir.rglob("*.java")):
            text = path.read_text(encoding="utf-8", errors="replace")
            match = JUNIT_ASSERTION_RE.search(text)
            if match:
                errors.append(
                    f"{path.relative_to(ROOT)} uses JUnit assertions; use AssertJ instead: "
                    f"{match.group(0).splitlines()[0]}"
                )
    return errors


def check_production_temporary_markers() -> list[str]:
    errors: list[str] = []
    for path in sorted(iter_files(".java")):
        rel_path = path.relative_to(ROOT)
        if "src/main/java" not in rel_path.as_posix():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        match = TEMPORARY_MARKER_RE.search(text)
        if match:
            errors.append(f"{rel_path} contains unresolved production marker: {match.group(0)}")
    return errors


def check_java_comments_are_english() -> list[str]:
    errors: list[str] = []
    for path in sorted(iter_files(".java")):
        rel_path = path.relative_to(ROOT)
        for index, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1):
            if JAVA_COMMENT_RE.search(line):
                errors.append(f"{rel_path}:{index} contains non-English Java comment")
    return errors


def find_author_only_javadocs(root: Path) -> list[str]:
    """Find Javadocs that contain author tags but no useful documentation."""
    errors: list[str] = []
    for path in sorted(root.rglob("*.java")):
        if any(part in SKIP_DIRS for part in path.relative_to(root).parts):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in JAVADOC_BLOCK_RE.finditer(text):
            lines = [
                re.sub(r"^\s*\*?\s?", "", line).strip()
                for line in match.group(1).splitlines()
            ]
            content = [line for line in lines if line]
            if content and all(line.startswith("@author") for line in content):
                line_number = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{path.relative_to(root)}:{line_number} has an author-only Javadoc; "
                    "remove it or document the API contract"
                )
    return errors


def check_author_only_javadocs() -> list[str]:
    return find_author_only_javadocs(ROOT)


def check_public_package_documentation() -> list[str]:
    errors: list[str] = []
    source_roots = [
        ROOT / "compileflow-api" / "src" / "main" / "java",
        ROOT
        / "compileflow-deploy"
        / "compileflow-deploy-api"
        / "src"
        / "main"
        / "java",
        ROOT
        / "compileflow-deploy"
        / "compileflow-deploy-protocol"
        / "src"
        / "main"
        / "java",
    ]
    package_pattern = re.compile(r"^package\s+([A-Za-z0-9_.]+);", re.MULTILINE)
    for source_root in source_roots:
        packages: set[str] = set()
        for path in sorted(source_root.rglob("*.java")):
            match = package_pattern.search(path.read_text(encoding="utf-8", errors="replace"))
            if match:
                packages.add(match.group(1))
        for package_name in sorted(packages):
            package_info = source_root.joinpath(*package_name.split("."), "package-info.java")
            if not package_info.exists():
                errors.append(
                    f"{package_info.relative_to(ROOT)} is required for public package navigation"
                )
    return errors


def main() -> int:
    checks = [
        check_workflow_tests,
        check_markdown_links,
        check_markdown_structure,
        check_documented_pnpm_scripts,
        check_issue_templates,
        check_generated_artifacts,
        check_tracked_local_tooling_files,
        check_tracked_generated_artifacts,
        check_submodule_metadata,
        check_helper_scripts_are_current,
        check_internal_only_references,
        check_insecure_secret_defaults,
        check_process_artifacts,
        check_translation_placeholders,
        check_process_stage_language,
        check_unsupported_architecture_claims,
        check_nonstandard_markdown_links,
        check_reproducible_install_documentation,
        check_workflow_path_filters,
        check_workflow_permissions,
        check_workflow_actions_are_pinned,
        check_workflow_checkout_credentials_not_persisted,
        check_required_workflows,
        check_java_ci_baseline,
        check_maven_toolchain_baseline,
        check_maven_wrapper_integrity,
        check_text_normalization_policy,
        check_java_core_ci_quality_gates,
        check_server_ci_quality_gates,
        check_integration_ci_runtime_matrix,
        check_java_platform_support_contract,
        check_workbench_delivery_quality_gates,
        check_workbench_ci_delivery_gate,
        check_retired_workbench_package_filters,
        check_java_workflow_shared_build_path_filters,
        check_security_scorecard_workflow,
        check_codeql_workflow,
        check_supply_chain_workflow,
        check_java_security_release_gate,
        check_codecov_token_guard,
        check_dependabot_coverage,
        check_release_document_set,
        check_governance_document_set,
        check_document_index_required_links,
        check_adopter_logos,
        check_api_reference_contract,
        check_configuration_reference_contract,
        check_module_map_contract,
        check_execution_flow_contract,
        check_version_routing_contract,
        check_node_support_contract,
        check_public_documentation_code_antipatterns,
        check_stale_java_baseline_references,
        check_action_policy_protocol,
        check_phantom_configuration_references,
        check_micrometer_documentation_contract,
        check_stale_api_documentation,
        check_active_documentation_retired_semantics,
        check_removed_2_0_surfaces_in_user_docs,
        check_supported_surfaces_contract,
        check_stale_deploy_documentation,
        check_stale_durable_recovery_documentation,
        check_unsupported_rollout_documentation,
        check_release_metadata,
        check_release_plugins_are_profile_scoped,
        check_dependency_check_configuration,
        check_spotbugs_exclusions_are_specific,
        check_plugin_versions_are_centralized,
        check_security_policy,
        check_maven_project_identities,
        check_top_level_maven_modules_are_declared,
        check_bom_dependency_alignment,
        check_user_docs_release_versions,
        check_quality_gates,
        check_checkstyle_configs_are_enforcing,
        check_operate_contract_required_fragments,
        check_operate_contract_drift,
        check_workbench_runtime_boundaries,
        check_dev_gateway_quality_gates,
        check_workbench_toolchain_contract,
        check_deploy_inmemory_repositories_not_production_registered,
        check_docker_delivery_rules,
        check_assertion_policy,
        check_production_temporary_markers,
        check_internal_identifier_casing,
        check_java_comments_are_english,
        check_author_only_javadocs,
        check_public_package_documentation,
    ]
    errors: list[str] = []
    for check in checks:
        errors.extend(check())
    if errors:
        print("Repository hygiene check failed:")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Repository hygiene check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
