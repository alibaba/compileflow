#!/usr/bin/env python3
"""Verify the greenfield CompileFlow Durable kernel delivery boundary."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DURABLE = ROOT / "compileflow-durable"
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}

DURABLE_MODULES = (
    "compileflow-durable-api",
    "compileflow-durable-spi",
    "compileflow-durable-testkit",
    "compileflow-durable-runtime",
    "compileflow-durable-postgresql",
    "compileflow-durable-mysql",
    "compileflow-durable-spring-boot-autoconfigure",
    "compileflow-durable-spring-boot-autoconfigure-postgresql",
    "compileflow-durable-spring-boot-autoconfigure-mysql",
    "compileflow-durable-spring-boot-starter",
    "compileflow-durable-spring-boot-starter-postgresql",
    "compileflow-durable-spring-boot-starter-mysql",
)

KERNEL_TABLES = frozenset(
    {
        "cf_durable_process",
        "cf_durable_run",
        "cf_durable_run_recovery_process",
        "cf_durable_wait",
        "cf_durable_effect",
        "cf_durable_journal",
        "cf_durable_outbox",
    }
)

INTEGRATION_EVENTS = frozenset(
    {
        "WAIT_COMMITTED",
        "EFFECT_REVIEW_REQUIRED",
        "RUN_SUCCEEDED",
        "RUN_FAILED",
        "RUN_CANCELLED",
    }
)

STORE_CONTRACT_TESTS = (
    "storedProcessIsImmutableAndIdempotent",
    "everyStartCreatesANewExactVersionRun",
    "waitCompletionResumeAndTerminalOutboxFormOneFencedChain",
    "timelinePaginationFreezesOneCommittedFactSnapshot",
    "pauseResumeAndCancellationRemainOrthogonalToRunLifecycle",
    "timerAndEffectBoundariesResumeFromCommittedFacts",
    "outboxRetryUsesStableEventIdentityAndTokenFencing",
    "committedRunDemandIsDiscoverableAfterRestart",
    "terminalRunRetentionWaitsForRequiredOutboxAndPurgesTheWholeRun",
    "runReferencesProtectEveryProcessInTheRecoverySet",
    "purgingATerminalRunReleasesItsRecoveryProcessReferences",
    "aSharedRecoveryProcessRemainsUntilItsLastRunReferenceIsPurged",
    "startRejectsAMissingRecoveryProcessWithoutCreatingTheRun",
    "concurrentStartAndUnusedProcessGcHaveOneReferentiallySafeOutcome",
    "leasesCanBeRenewedAcrossLongApplicationWork",
    "waitCommittedOutboxCannotBeAbandoned",
    "waitAuthorityFulfillmentOrRevocationSettlesItsOutboxAndFencesLatePublishers",
    "pauseAndEffectClaimRaceHasOneSafeOutcomeWithoutDeadlock",
    "cancelAndDueTimerRaceCompletesWithoutDeadlock",
    "effectReviewAndOutboxResolutionUseOccurrenceRevisions",
    "cancelLeaseRenewalAndOutboxCompletionShareRunFirstLockOrder",
    "expiredClaimsRecoverAfterWorkerCrashAndFenceEveryLateOwner",
    "resolvedOccurrenceIsConsumedExactlyOnceByTheFencedTurn",
    "waitingTurnWithoutOutstandingAuthorityIsRejectedAtomically",
    "oneTurnIssuesAndConsumesMultipleExactOccurrences",
    "oneFrontierOwnsAtMostOneOutstandingOccurrenceAndAdvancesAfterExactConsumption",
    "activeWorkIsSummarizedPagedAndMayCoexistWithRunnableFrontier",
    "pauseAndCancelWaitForEveryPossibleEffectDispatch",
)

BPMN_CRASH_MATRIX_TESTS = (
    "effectResponseLossBecomesUnknownThenReconcilesToOneContinuation",
    "messageCatchSurvivesRuntimeLossAndResumesExactlyOnce",
    "timerCatchSurvivesRuntimeLossAndResolvesOnceWhenDue",
    "callActivityChildAndParentContinuationRecoverAsOneChain",
    "parallelBranchesResumeFromPartialProgressAndConvergeExactly",
    "parallelMultiInstanceRecoversPartialIterationsWithStableOrderedMerge",
)

AUTHORITATIVE_DOCUMENTS = (
    "compileflow-durable/README.md",
    "docs/en/architecture/durable-architecture.md",
    "docs/zh/architecture/durable-architecture.md",
    "docs/en/durable-process.md",
    "docs/zh/durable-process.md",
)


class DeliveryError(ValueError):
    """A greenfield Durable delivery invariant is unsatisfied."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise DeliveryError(message)


def read(path: str | Path) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def strip_sql_comments(source: str) -> str:
    return re.sub(r"--[^\n]*", "", source)


def extract_sql_tables(source: str) -> frozenset[str]:
    return frozenset(
        re.findall(
            r"\bCREATE\s+TABLE\s+(?:public\.)?([a-z][a-z0-9_]*)",
            strip_sql_comments(source),
            flags=re.IGNORECASE,
        )
    )


def extract_outbox_event_types(source: str) -> frozenset[str]:
    sql = strip_sql_comments(source)
    match = re.search(
        r"ck_cf_durable_outbox_type\s+CHECK\s*\(\s*event_type\s+IN\s*\((.*?)\)\s*\)",
        sql,
        flags=re.IGNORECASE | re.DOTALL,
    )
    require(match is not None, "Durable schema misses the closed Outbox event check")
    return frozenset(re.findall(r"'([A-Z][A-Z0-9_]*)'", match.group(1)))


def check_maven_surface() -> None:
    durable_pom = ET.parse(DURABLE / "pom.xml").getroot()
    modules = tuple(
        node.text.strip()
        for node in durable_pom.findall("m:modules/m:module", MAVEN_NAMESPACE)
        if node.text
    )
    require(modules == DURABLE_MODULES, f"Durable module surface drifted: {modules}")

    root_pom = ET.parse(ROOT / "pom.xml").getroot()
    root_modules = {
        node.text.strip()
        for node in root_pom.findall("m:modules/m:module", MAVEN_NAMESPACE)
        if node.text
    }
    require("compileflow-durable" in root_modules, "Root reactor misses compileflow-durable")

    for pom in DURABLE.rglob("pom.xml"):
        if pom.parent.name == "compileflow-durable-spring-boot-autoconfigure":
            continue
        source = pom.read_text(encoding="utf-8")
        require(
            "<artifactId>compileflow-deploy" not in source,
            f"Durable must not depend on Deploy routing: {pom.relative_to(ROOT)}",
        )

    composition_pom = read(
        "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/pom.xml"
    )
    require(
        re.search(
            r"<artifactId>compileflow-deploy-api</artifactId>\s*<optional>true</optional>",
            composition_pom,
        )
        is not None,
        "Only the Spring composition root may carry Deploy API, and it must be optional",
    )
    for source in DURABLE.rglob("src/main/**/*.java"):
        if "compileflow-durable-spring-boot-autoconfigure" in source.parts:
            continue
        require(
            "com.alibaba.compileflow.deploy" not in source.read_text(encoding="utf-8"),
            f"Durable Kernel source must not import Deploy: {source.relative_to(ROOT)}",
        )


def check_database_baseline() -> None:
    providers = {
        "PostgreSQL": (
            DURABLE / "compileflow-durable-postgresql/src/main/resources/db/compileflow-durable/postgres/migration",
            "public.",
            ("process_id            uuid primary key", "lease_token               uuid", "default clock_timestamp()"),
        ),
        "MySQL": (
            DURABLE / "compileflow-durable-mysql/src/main/resources/db/compileflow-durable/mysql/migration",
            "",
            ("process_id            char(36) primary key", "lease_token               char(36)",
             "default current_timestamp(3)"),
        ),
    }
    for provider, (migration_dir, schema_prefix, provider_markers) in providers.items():
        migrations = tuple(sorted(path.name for path in migration_dir.glob("*.sql")))
        require(migrations == ("V1__durable_kernel.sql",),
                f"{provider} Durable migration baseline drifted: {migrations}")
        schema = read(migration_dir.relative_to(ROOT) / "V1__durable_kernel.sql")
        require(extract_sql_tables(schema) == KERNEL_TABLES,
                f"{provider} V1 layout must contain the expected seven tables: {sorted(extract_sql_tables(schema))}")
        sql = strip_sql_comments(schema).lower()
        for retired in (
            "process_alias", "application_build", "program_abi", "machine_semantics_version",
            "compiler_version", "generator_version", "runtime_version", "execution_semantics_version",
            "stored_program", "codec_id", "fence_token", "fencing_token", "cf_durable_command_receipt",
            "cf_durable_idempotency_config", "artifact_digest", "effect_token",
        ):
            require(retired not in sql, f"Retired identity leaked into {provider} Durable schema: {retired}")
        for marker in (
            "process_code", "definition_digest", "occurrence_sequence", "frontier_id",
            "ck_cf_durable_wait_frontier", "ck_cf_durable_effect_frontier", "consumed_at",
            f"references {schema_prefix}cf_durable_process(process_id)", *provider_markers,
        ):
            require(marker in sql, f"{provider} Durable schema misses {marker!r}")
        process_table = sql.split(f"create table {schema_prefix}cf_durable_process", 1)[1].split("create ", 1)[0]
        for attribution in ("namespace", "process_version"):
            require(attribution not in process_table,
                    f"Stored Process semantics must not contain admission attribution {attribution!r}")
        run_table = sql.split(f"create table {schema_prefix}cf_durable_run", 1)[1].split("create ", 1)[0]
        for attribution in ("namespace", "process_version"):
            require(attribution in run_table, f"Run must retain optional admission attribution {attribution!r}")
        require(extract_outbox_event_types(schema) == INTEGRATION_EVENTS,
                f"{provider} Outbox must expose exactly the closed five Integration Events")
        outbox = sql.split(f"create table {schema_prefix}cf_durable_outbox", 1)[1]
        require("journal" not in outbox, f"{provider} Outbox must not be coupled 1:1 to Journal")


def check_admission_boundary() -> None:
    engine = read(
        "compileflow-durable/compileflow-durable-api/src/main/java/"
        "com/alibaba/compileflow/durable/api/DurableProcessEngine.java"
    )
    version_start = re.search(
        r"\bstart\s*\(\s*ProcessRunId\s+\w+\s*,\s*ProcessRef\.Version\s+\w+\s*,"
        r"\s*Map<String,\s*\?>\s+\w+\s*\)",
        engine,
    )
    alias_start = re.search(
        r"\bstart\s*\(\s*ProcessRunId\s+\w+\s*,\s*ProcessRef\.Alias\s+\w+\s*,"
        r"\s*Map<String,\s*\?>\s+\w+\s*\)",
        engine,
    )
    routed_alias_start = re.search(
        r"\bstart\s*\(\s*ProcessRunId\s+\w+\s*,\s*ProcessRef\.Alias\s+\w+\s*,"
        r"\s*Map<String,\s*\?>\s+\w+\s*,"
        r"\s*AliasRoutingOptions\s+\w+\s*\)",
        engine,
    )
    direct_definition_start = re.search(
        r"\bstart\s*\(\s*ProcessRunId\s+\w+\s*,\s*ProcessDefinition\s+\w+\s*,"
        r"\s*Map<String,\s*\?>\s+\w+\s*\)",
        engine,
    )
    require(
        direct_definition_start is not None
        and version_start is not None
        and alias_start is not None
        and routed_alias_start is not None,
        "Durable Start must require RunId and expose Definition, Version and Alias with Alias-only routing Options",
    )
    require(
        "start(ProcessRef process" not in engine,
        "Durable Start must reject open ProcessRef arguments at compile time",
    )
    require(
        "ProcessModelType" not in engine,
        "Durable Start must derive model type from its typed Definition, not a parallel argument",
    )
    for method in (
        r"\bcompleteWait\s*\(\s*WaitToken\s+\w+\s*,\s*Map<String,\s*\?>\s+\w+\s*\)",
        r"\bcancel\s*\(\s*ProcessRunId\s+\w+\s*\)",
        r"\bgetRun\s*\(\s*ProcessRunId\s+\w+\s*\)",
        r"\blistRuns\s*\(\s*ProcessRunQuery\s+\w+\s*\)",
        r"\bgetRunResult\s*\(\s*ProcessRunId\s+\w+\s*\)",
    ):
        require(re.search(method, engine) is not None, "DurableProcessEngine drifted from direct semantic arguments")
    for command in ("StartProcessRunCommand", "CompleteWaitCommand", "CancelProcessRunCommand"):
        require(command not in engine, f"High-frequency Durable Application API must not expose {command}")

    factory = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/DurableProcessEngineFactory.java"
    )
    for compiler in ("DurableJavaProgramCompiler", "DurableInterpretedProgramCompiler"):
        require(
            compiler in factory,
            f"Durable runtime must retain the {compiler} realization",
        )

    main_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in DURABLE.rglob("src/main/**/*.java")
    )
    for retired in (
        "ProcessAliasResolver",
        "ProcessAliasStateSource",
        "DurableAliasAdmissionResolver",
        "DefaultDurableAliasAdmissionResolver",
        "ResolvedAliasAdmission",
        "ApplicationBuild",
        "ProgramAbi",
        "StoredProgram",
        "DurablePayloadProtector",
        "DurableKeyEncryptionProvider",
        "DurableRequestSecurityKeys",
        "OperatorAuthorizationEvidence",
        "idempotencyKey",
        "DurableCompatibilityAnalysis",
        "DurableCompatibilityException",
        "TbbpmDurableCompatibilityAnalyzer",
    ):
        require(retired not in main_sources, f"Retired Durable protocol remains: {retired}")

    runtime_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (
            DURABLE / "compileflow-durable-runtime/src/main/java"
        ).rglob("*.java")
    )
    require(
        re.search(r"\bprocessVersion\s*(?:==|!=)\s*null\b", runtime_sources) is None,
        "Durable Runtime behavior must not branch on optional Version attribution",
    )


def check_admission_behavior_contract() -> None:
    run_id = read(
        "compileflow-durable/compileflow-durable-api/src/main/java/"
        "com/alibaba/compileflow/durable/api/model/ProcessRunId.java"
    )
    engine = read(
        "compileflow-durable/compileflow-durable-api/src/main/java/"
        "com/alibaba/compileflow/durable/api/DurableProcessEngine.java"
    )
    durable_guide = read("docs/en/durable-process.md")
    require(
        "must never be reused" in " ".join(run_id.split())
        and "must never be reused" in " ".join(engine.split()),
        "Public Durable API must forbid ProcessRunId reuse, including after retention",
    )
    require(
        "must never be reused" in " ".join(durable_guide.split())
        and "even after retention" in durable_guide,
        "Durable guide must freeze permanent ProcessRunId occurrence identity",
    )

    engine_test = read(
        "compileflow-durable/compileflow-durable-runtime/src/test/java/"
        "com/alibaba/compileflow/durable/runtime/DefaultDurableProcessEngineTest.java"
    )
    for test in (
        "exactVersionStartBypassesAliasAdmission",
        "aliasStartResolvesOnceAndPersistsOnlyTheSelectedVersion",
        "laterAliasChangeCannotChangeAnExistingRunVersion",
        "bpmnModelTypeIsPreservedByTheSharedBackend",
    ):
        require(f"void {test}()" in engine_test, f"Durable admission behavior misses {test}")

    manager_test = read(
        "compileflow-durable/compileflow-durable-runtime/src/test/java/"
        "com/alibaba/compileflow/durable/runtime/process/DurableProcessRuntimeManagerTest.java"
    )
    require(
        "void recoveryUsesStoredSemanticsWithoutConsultingTheAdmissionSource()" in manager_test,
        "Durable recovery must prove independence from the admission source",
    )

    adapter_test = read(
        "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/src/test/java/"
        "com/alibaba/compileflow/durable/spring/boot/autoconfigure/DeployDurableAdaptersTest.java"
    )
    for test in (
        "aliasStateIsMappedOnlyForNewRunAdmission",
        "exactTbbpmArtifactIsVerifiedBeforeCrossingTheBoundary",
        "modelTypeIsPreservedAndDigestMismatchFailsClosed",
        "mismatchedSourceIdentitiesFailClosed",
    ):
        require(f"void {test}()" in adapter_test, f"Deploy/Durable composition behavior misses {test}")


def check_wait_token_contract() -> None:
    token = read(
        "compileflow-durable/compileflow-durable-api/src/main/java/"
        "com/alibaba/compileflow/durable/api/model/WaitToken.java"
    )
    require("WaitToken[<redacted>]" in token, "WaitToken string representation must redact the bearer capability")
    require(
        "ProcessTrigger" not in token and "ProcessRunId" not in token,
        "WaitToken must be a scalar capability without caller-supplied ownership or recovery coordinates",
    )

    store = read(
        "compileflow-durable/compileflow-durable-spi/src/main/java/"
        "com/alibaba/compileflow/durable/spi/store/DurableStore.java"
    )
    for marker in (
        "Optional<WaitTarget> findWaitTarget(String tokenDigest)",
        "record WaitTarget(ProcessRunId runId, RunProcess rootProcess, UUID processId, String tokenDigest)",
    ):
        require(marker in store, f"WaitToken owner discovery contract misses {marker!r}")

    engine = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/DefaultDurableProcessEngine.java"
    )
    require(
        "store.findWaitTarget(tokenDigest)" in engine,
        "Wait completion must discover Store-owned authority from the scalar capability",
    )

    api_test = read(
        "compileflow-durable/compileflow-durable-api/src/test/java/"
        "com/alibaba/compileflow/durable/api/DurableApiContractTest.java"
    )
    require(
        "waitTokenStringRepresentationNeverExposesTheBearerCapability" in api_test,
        "WaitToken redaction must remain release-tested",
    )

    sink = read(
        "compileflow-durable/compileflow-durable-spi/src/main/java/"
        "com/alibaba/compileflow/durable/spi/outbox/DurableOutboxSink.java"
    )
    for marker in ("must never log it", "metric label", "third-party metadata"):
        require(marker in sink, f"WaitToken sink contract misses {marker!r}")


def check_atomic_store_protocol() -> None:
    store = read(
        "compileflow-durable/compileflow-durable-spi/src/main/java/"
        "com/alibaba/compileflow/durable/spi/store/DurableStore.java"
    )
    require(
        "public interface DurableStore" in store,
        "DurableStore must remain the first-party atomic Provider transaction protocol",
    )
    protocol = store + "\n".join(
        read(
            "compileflow-durable/compileflow-durable-spi/src/main/java/"
            f"com/alibaba/compileflow/durable/spi/store/{name}.java"
        )
        for name in (
            "DurableCatalogStore",
            "DurableOperatorStore",
            "DurableTurnStore",
            "DurableEffectStore",
            "DurableOutboxDeliveryStore",
            "DurableLeaseStore",
            "DurableMaintenanceStore",
        )
    )
    for method in (
        "registerProcess(",
        "listProcessRuntimeDemand(",
        "start(",
        "completeWait(",
        "claimRun(",
        "renewRunLeases(",
        "commitTurn(",
        "listActiveWork(",
        "record TurnCommit(",
        "List<OccurrenceKey> consumedOccurrences",
        "List<OccurrenceCommit> issuedOccurrences",
        "releaseRunAfterCapabilityLoss(",
        "claimEffect(",
        "renewEffectLeases(",
        "claimOutbox(",
        "renewOutboxLeases(",
        "purgeTerminalRuns(",
        "purgeConsumedOccurrences(",
        "purgeUnusedProcesses(",
    ):
        require(method in protocol, f"Atomic DurableStore protocol misses {method!r}")

    for retired in ("commitWait(", "commitTimer(", "commitEffect("):
        require(
            retired not in protocol,
            f"Boundary-specific Store transaction must remain unified behind commitTurn: {retired}",
        )

    retired_store = (
        DURABLE
        / "compileflow-durable-runtime/src/main/java/com/alibaba/compileflow/durable/runtime/store/"
        "DurableStore.java"
    )
    require(
        not retired_store.exists(),
        "DurableStore must not remain physically owned by Runtime",
    )

    repository_types = tuple(
        path
        for path in DURABLE.rglob("src/main/**/*.java")
        if path.name.endswith("Repository.java")
    )
    require(not repository_types, "Durable persistence must not fragment into Repository ports")


def check_random_lease_fencing() -> None:
    store = read(
        "compileflow-durable/compileflow-durable-spi/src/main/java/"
        "com/alibaba/compileflow/durable/spi/store/DurableStore.java"
    )
    for lease in (
        "record EffectLease(ProcessRunId runId, UUID effectId, UUID token)",
        "record OutboxLease(ProcessRunId runId, UUID eventId, UUID token)",
    ):
        require(lease in store, f"Run-owned lease must carry RunId: {lease}")

    implementations = {
        "PostgreSQL": read(
            "compileflow-durable/compileflow-durable-postgresql/src/main/java/"
            "com/alibaba/compileflow/durable/postgres/PostgresDurableStore.java"
        ),
        "MySQL": read(
            "compileflow-durable/compileflow-durable-mysql/src/main/java/"
            "com/alibaba/compileflow/durable/mysql/MySqlDurableStore.java"
        ),
    }
    for provider, implementation in implementations.items():
        require(implementation.count("UUID.randomUUID()") >= 3,
                f"{provider} Run, Effect and Outbox claims need random lease tokens")
        for predicate in (
            "status = 'RUNNING' AND lease_token = ?", "e.lease_token = ?",
            "status = 'DELIVERING'", "lease_token = ?",
        ):
            require(predicate in implementation,
                    f"{provider} token-fenced completion path misses {predicate!r}")
        require("numericFence" not in implementation and "fencingToken" not in implementation,
                f"Numeric fencing must not enter the {provider} implementation")
        for marker in ("lockEffectLeaseRunFirst(connection,", "lockOutboxLeaseRunFirst(connection,"):
            require(marker in implementation, f"{provider} Run-first occurrence protocol misses {marker!r}")
    require("FOR UPDATE OF r SKIP LOCKED LIMIT 1" in implementations["PostgreSQL"],
            "PostgreSQL run acquisition must preserve its run-first SKIP LOCKED protocol")


def check_store_contract() -> None:
    contract = read(
        "compileflow-durable/compileflow-durable-testkit/src/main/java/"
        "com/alibaba/compileflow/durable/testkit/DurableStoreContract.java"
    )
    require(
        "abstract class DurableStoreContract" in contract,
        "Durable Store contract must be an inheritable executable test class",
    )
    for test in STORE_CONTRACT_TESTS:
        require(f"void {test}()" in contract, f"Durable Store contract misses {test}")

    for implementation in (
        "PostgresDurableStoreContractTest.java",
        "LocalPostgresDurableStoreContractTest.java",
    ):
        source = read(
            "compileflow-durable/compileflow-durable-postgresql/src/test/java/"
            f"com/alibaba/compileflow/durable/postgres/{implementation}"
        )
        require(
            "extends DurableStoreContract" in source,
            f"{implementation} must inherit the kernel Store contract",
        )

    mysql_contract = read(
        "compileflow-durable/compileflow-durable-mysql/src/test/java/"
        "com/alibaba/compileflow/durable/mysql/MySqlDurableStoreContractTest.java"
    )
    require("extends DurableStoreContract" in mysql_contract,
            "MySQL Durable implementation must inherit the complete kernel Store contract")
    require("db/compileflow-durable/mysql/migration" in mysql_contract,
            "MySQL Durable contract must execute the Provider-owned migration")

    schema_test = read(
        "compileflow-durable/compileflow-durable-postgresql/src/test/java/"
        "com/alibaba/compileflow/durable/postgres/DurableKernelSchemaContractTest.java"
    )
    require(
        "greenfieldV1LayoutContainsOnlyItsDeclaredKernelTables" in schema_test,
        "PostgreSQL implementation must lock its declared V1 table layout",
    )

    crash_matrix = read(
        "compileflow-durable/compileflow-durable-postgresql/src/test/java/"
        "com/alibaba/compileflow/durable/postgres/LocalPostgresDurableBpmnCrashMatrixTest.java"
    )
    require(
        '@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_POSTGRES_URL"' in crash_matrix,
        "BPMN crash matrix must run only against an explicitly supplied real PostgreSQL authority",
    )
    for test in BPMN_CRASH_MATRIX_TESTS:
        require(f"void {test}()" in crash_matrix, f"BPMN PostgreSQL crash matrix misses {test}")

    postgres_pom = read("compileflow-durable/compileflow-durable-postgresql/pom.xml")
    require(
        re.search(
            r"<artifactId>compileflow-bpmn</artifactId>\s*<scope>test</scope>",
            postgres_pom,
        )
        is not None,
        "PostgreSQL recovery proof must compile the real BPMN frontend as a test-only dependency",
    )

    workflow = read(".github/workflows/durable-ci.yml")
    for marker in (
        "--expected-reports 4",
        "--minimum-tests 50",
        "--minimum-passed 50",
        "LocalPostgresDurableBpmnCrashMatrixTest",
    ):
        require(marker in workflow, f"PostgreSQL 16/17/18 evidence gate misses {marker!r}")
    for marker in (
        "mysql-contract:",
        "--minimum-tests 45",
        "--minimum-passed 45",
        "MySqlDurableStoreContractTest",
        "mysql:8.4.7@sha256:",
        "--expected-mysql 8.4.7",
    ):
        require(marker in workflow, f"MySQL 8.4 evidence gate misses {marker!r}")


def check_action_semantics() -> None:
    execution = read(
        "compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/model/action/"
        "ActionExecution.java"
    )
    require(
        'REPLAYABLE("replayable")' in execution and 'EFFECT("effect")' in execution,
        "Action execution must be the closed replayable/effect semantic",
    )
    for retired in ("PURE", "IDEMPOTENT", "NON_IDEMPOTENT"):
        require(retired not in execution, f"Retired Action side-effect semantic remains: {retired}")

    xsd = read("compileflow-tbbpm/src/main/resources/TBBPM.xsd")
    require(
        '<xs:attribute name="execution" type="ActionExecution"/>' in xsd,
        "TBBPM Action misses execution semantics",
    )
    require('<xs:element name="effectTask"' not in xsd, "Effect must not become a dedicated node")
    require('<xs:element name="inAction"' not in xsd, "inAction must not re-enter TBBPM")
    require('<xs:element name="outAction"' not in xsd, "outAction must not re-enter TBBPM")

    context = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/program/DurableExecutionContext.java"
    )
    require(
        context.count("serializer.detachActionInput(elementId, input)") == 2,
        "Replayable and Effect Action inputs must cross the exact typed detach boundary",
    )
    require(
        "serializer.detachSnapshot(" in context,
        "Wait-description callbacks must receive detached typed state and scope values",
    )

    effect_metadata_path = ROOT / (
        "compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/model/action/"
        "EffectMetadata.java"
    )
    effect_metadata = effect_metadata_path.read_text(encoding="utf-8")
    require(
        'ID = "__cf_effect_id"' in effect_metadata
        and "ATTEMPT" not in effect_metadata,
        "Effect ID must be the only authoring-level Kernel metadata binding",
    )
    definitions = [
        path
        for path in ROOT.rglob("*.java")
        if "src" in path.parts
        and "main" in path.parts
        and "__cf_effect_id" in path.read_text(encoding="utf-8")
    ]
    require(
        definitions == [effect_metadata_path],
        "Kernel Effect ID source must have one production definition",
    )
    require(
        not any(
            "__cf_effect_attempt" in path.read_text(encoding="utf-8")
            for path in ROOT.rglob("*.java")
            if "src" in path.parts and "main" in path.parts
        ),
        "Effect attempt must not be an Action input source",
    )

    durable_action_invoker = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/action/DurableActionInvoker.java"
    )
    durable_eligibility = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/machine/"
        "DurableProcessEligibilityChecker.java"
    )
    durable_serializer = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/codec/DurableValueSerializer.java"
    )
    action_plan = read(
        "compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/semantic/plan/ActionPlan.java"
    )
    require(
        "record EffectId() implements InputSource" in action_plan,
        "Semantic Action inputs must represent Effect ID as a typed source",
    )
    require(
        "ActionPlan.InputSource.EffectId" in durable_action_invoker
        and "ActionPlan.InputSource.EffectId" in durable_serializer
        and "EffectMetadata" not in durable_action_invoker
        and "EffectMetadata" not in durable_eligibility
        and "EffectMetadata" not in durable_serializer,
        "Effect metadata sentinel must not survive the semantic frontend",
    )

    sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in DURABLE.rglob("src/main/**/*.java")
    )
    for retired in ("__cf_effect_token", "effectToken", "EffectToken"):
        require(retired not in sources, f"Retired Effect token terminology remains: {retired}")

    target_checker = DURABLE / (
        "compileflow-durable-runtime/src/main/java/com/alibaba/compileflow/durable/runtime/machine/"
        "DurableProcessEligibilityChecker.java"
    )
    require(target_checker.is_file(), "Source-neutral Durable target checker is missing")
    for retired in (
        "TbbpmDurableExecutionProfile.java",
        "TbbpmDurableModelEligibilityChecker.java",
        "TbbpmDurablePlanCompiler.java",
    ):
        require(not any(DURABLE.rglob(retired)), f"Retired TBBPM-specific Durable backend remains: {retired}")


def check_schema_startup_validation() -> None:
    initializers = {
        "PostgreSQL": read(
            "compileflow-durable/compileflow-durable-spring-boot-autoconfigure-postgresql/src/main/java/"
            "com/alibaba/compileflow/durable/spring/boot/autoconfigure/postgres/"
            "DurablePostgresSchemaInitializer.java"
        ),
        "MySQL": read(
            "compileflow-durable/compileflow-durable-spring-boot-autoconfigure-mysql/src/main/java/"
            "com/alibaba/compileflow/durable/spring/boot/autoconfigure/mysql/"
            "DurableMySqlSchemaInitializer.java"
        ),
    }
    for provider, initializer in initializers.items():
        for marker in (
            '.table("cf_durable_schema_history")', '.baselineVersion("0")',
            "flyway.validate();", "flyway.info().pending()",
        ):
            require(marker in initializer,
                    f"Fail-closed {provider} Durable schema initialization misses {marker!r}")
    for marker in ('.defaultSchema("public")', '.schemas("public")'):
        require(marker not in initializers["PostgreSQL"],
                f"PostgreSQL Durable schema initialization hard-codes the public schema via {marker!r}")


def check_optional_observability() -> None:
    runtime_pom = read("compileflow-durable/compileflow-durable-runtime/pom.xml")
    require("micrometer" not in runtime_pom, "Durable Runtime must not depend on Micrometer")
    require("spring-boot-health" not in runtime_pom, "Durable Runtime must not depend on Spring Health")

    metrics = read(
        "compileflow-durable/compileflow-durable-runtime/src/main/java/"
        "com/alibaba/compileflow/durable/runtime/observability/DurableRuntimeMetrics.java"
    )
    require("enum Operation" in metrics and "enum Outcome" in metrics, "Durable metrics must use bounded enums")
    require("Map<" not in metrics, "Durable metrics must not create dynamic high-cardinality dimensions")

    autoconfigure = read(
        "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/src/main/java/"
        "com/alibaba/compileflow/durable/spring/boot/autoconfigure/CompileFlowDurableAutoConfiguration.java"
    ) + read(
        "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/src/main/java/"
        "com/alibaba/compileflow/durable/spring/boot/autoconfigure/CompileFlowDurableObservabilityAutoConfiguration.java"
    )
    for marker in (
        "@ConditionalOnBean(DurableStore.class)",
        "@ConditionalOnClass(MeterRegistry.class)",
        "compileFlowDurableMetricsBinder",
        'name = "org.springframework.boot.health.contributor.HealthIndicator"',
        "compileFlowDurableHealthIndicator",
    ):
        require(marker in autoconfigure, f"Optional Durable observability misses {marker!r}")
    for provider_leak in ("PostgresDurableStore", "DataSource", "Flyway"):
        require(
            provider_leak not in autoconfigure,
            f"Provider-neutral Durable Runtime composition leaks {provider_leak}",
        )

    provider_compositions = {
        "PostgreSQL": (
            read("compileflow-durable/compileflow-durable-spring-boot-autoconfigure-postgresql/src/main/java/"
                 "com/alibaba/compileflow/durable/spring/boot/autoconfigure/postgres/"
                 "CompileFlowDurablePostgresAutoConfiguration.java"),
            ("PostgresDurableStore", "DurablePostgresSchemaInitializer"),
        ),
        "MySQL": (
            read("compileflow-durable/compileflow-durable-spring-boot-autoconfigure-mysql/src/main/java/"
                 "com/alibaba/compileflow/durable/spring/boot/autoconfigure/mysql/"
                 "CompileFlowDurableMySqlAutoConfiguration.java"),
            ("MySqlDurableStore", "DurableMySqlSchemaInitializer"),
        ),
    }
    for provider, (configuration, markers) in provider_compositions.items():
        for marker in (*markers, "compileFlowDurableDataSource", "@ConditionalOnMissingBean(DurableStore.class)"):
            require(marker in configuration, f"{provider} Durable composition misses {marker!r}")

    neutral_starter = read(
        "compileflow-durable/compileflow-durable-spring-boot-starter/pom.xml"
    )
    for provider_dependency in ("compileflow-durable-postgresql", "compileflow-durable-mysql", "flyway-core",
                                "postgresql", "mysql-connector-j"):
        require(
            provider_dependency not in neutral_starter,
            f"Provider-neutral Durable starter leaks {provider_dependency}",
        )
    postgres_starter = read(
        "compileflow-durable/compileflow-durable-spring-boot-starter-postgresql/pom.xml"
    )
    for provider_dependency in (
        "compileflow-durable-spring-boot-starter",
        "compileflow-durable-spring-boot-autoconfigure-postgresql",
        "flyway-database-postgresql",
        "postgresql",
    ):
        require(
            provider_dependency in postgres_starter,
            f"PostgreSQL Durable starter misses {provider_dependency}",
        )
    mysql_starter = read(
        "compileflow-durable/compileflow-durable-spring-boot-starter-mysql/pom.xml"
    )
    for provider_dependency in (
        "compileflow-durable-spring-boot-starter", "compileflow-durable-spring-boot-autoconfigure-mysql",
        "flyway-mysql", "mysql-connector-j",
    ):
        require(provider_dependency in mysql_starter,
                f"MySQL Durable starter misses {provider_dependency}")

    health = read(
        "compileflow-durable/compileflow-durable-spring-boot-autoconfigure/src/main/java/"
        "com/alibaba/compileflow/durable/spring/boot/autoconfigure/runtime/DurableHealthIndicator.java"
    )
    require("listProcessRuntimeDemand" in health, "Durable health must probe the configured Store authority")
    for provider_leak in ("DataSource", "clock_timestamp", "executeQuery"):
        require(provider_leak not in health, f"Provider-neutral Durable health leaks {provider_leak}")
    require(
        '"effectCapability", "process_scoped"' in health,
        "Process-scoped Effect capability must not become global Engine readiness",
    )


def check_documented_boundary() -> None:
    for path in AUTHORITATIVE_DOCUMENTS:
        require((ROOT / path).is_file(), f"Missing authoritative Durable document: {path}")

    authoritative_text = "\n".join(read(path) for path in AUTHORITATIVE_DOCUMENTS)
    for stale_contract in (
        "Code | Version | Alias",
        "Code / Version / Alias",
        "effectiveProcessVersion",
        "getEffectiveVersion",
    ):
        require(
            stale_contract not in authoritative_text,
            f"Authoritative Durable documentation retains stale API contract {stale_contract!r}",
        )

    architecture = read("docs/en/architecture/durable-architecture.md")
    normalized_architecture = " ".join(architecture.lower().split())
    for marker in (
        "admission materializes one exact immutable stored process",
        "binds the run to its `processid`",
        "alias is deploy control-plane state",
        "before resolving alias once",
        "kernel does not persist generated java source, classes, bytecode, live object instances",
        "application/runtime capability problem",
        "opaque, one-shot bearer capability",
        "does not require an application token table",
        "does not provide fuzzy",
    ):
        require(
            marker in normalized_architecture,
            f"Durable architecture misses boundary statement {marker!r}",
        )

    normalized_architecture = " ".join(architecture.split())
    for marker in (
        "content-addressed `processId`",
        "Namespace and optional Version belong to Run admission attribution, not stored semantic identity",
        "Every Run stores one exact root `processId`",
    ):
        require(
            marker in normalized_architecture,
            f"Durable architecture misses recovery identity boundary {marker!r}",
        )


def main() -> int:
    checks = (
        check_maven_surface,
        check_database_baseline,
        check_admission_boundary,
        check_admission_behavior_contract,
        check_wait_token_contract,
        check_atomic_store_protocol,
        check_random_lease_fencing,
        check_store_contract,
        check_action_semantics,
        check_schema_startup_validation,
        check_optional_observability,
        check_documented_boundary,
    )
    try:
        for check in checks:
            check()
    except (DeliveryError, ET.ParseError, OSError, re.error) as failure:
        print(f"Durable kernel delivery check failed: {failure}", file=sys.stderr)
        return 1
    print("Durable kernel delivery check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
