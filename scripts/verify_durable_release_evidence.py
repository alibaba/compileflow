#!/usr/bin/env python3
"""Aggregate a complete, same-commit Durable CI evidence matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable

try:
    from scripts.evidence_io import write_json as write_evidence
    from scripts.verify_durable_test_evidence import (
        EvidenceError,
        parse_report,
    )
except ModuleNotFoundError:  # Direct script execution from scripts/.
    from evidence_io import write_json as write_evidence
    from verify_durable_test_evidence import EvidenceError, parse_report


TEST_EVIDENCE_SCHEMA = "compileflow-durable-test-evidence/v2"
RELEASE_EVIDENCE_SCHEMA = "compileflow-durable-release-evidence/v2"
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
POSTGRES_VERSION_PATTERN = re.compile(r"[0-9]+\.[0-9]+")
COUNT_FIELDS = (
    "tests", "failures", "errors", "skipped", "flakes", "passed"
)
KERNEL_REQUIRED_TEST_CASES = frozenset(
    {
        "com.alibaba.compileflow.durable.api.DurableApiShapeTest#keepsSupportedTypesExplicitlyAllowlisted",
        "com.alibaba.compileflow.durable.api.GreenfieldDurableApiTest#aliasRoutingOptionsContainOnlyAdmissionInputs",
        "com.alibaba.compileflow.durable.api.GreenfieldDurableApiTest#applicationFacadeUsesDirectSemanticArguments",
        "com.alibaba.compileflow.durable.api.GreenfieldDurableApiTest#runLifecycleAndControlAreIndependentAxes",
        "com.alibaba.compileflow.durable.api.GreenfieldDurableApiTest#waitTokenStringRepresentationNeverExposesTheBearerCapability",
        "com.alibaba.compileflow.durable.api.model.ProcessRunResultTest#succeededResultDeeplyOwnsAndRedactsOutput",
        "com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProviderTest#defaultsProvideNoApplicationWaitAttributes",
        "com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSinkTest#outboundEventOwnsAnImmutableLogicalPayloadAndRedactsIt",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#roundTripsDeclaredVariablesAndDefinitionTypedScopeState",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#startPreservesOmittedFieldsAndExplicitNullAsDifferentInputs",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#startRejectsInternalAndReturnVariablesOnEncodeAndRecovery",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#rejectsAmbiguousOrHostileJson",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#effectOutputRejectsIllTypedProcessStateBeforeCommitAndOnRecovery",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#processCallDefaultsUseTheExactChildParameterType",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#waitPayloadRoundTripsOnlyTypedDeclaredPartialState",
        "com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializerTest#waitPayloadRejectsUndeclaredOrIllTypedValuesBeforeCommit",
        "com.alibaba.compileflow.durable.runtime.kernel.FrontierExecutionOperationsTest#runtimeSnapshotsBoundEntireGraphsBeforeApplyingUpdates",
        "com.alibaba.compileflow.durable.runtime.kernel.FrontierExecutionOperationsTest#effectRequestOwnsTheDurableInputSnapshot",
        "com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperationsTest#forkAndJoinUseStableIdentitySnapshotIsolationAndDisjointWriteMerge",
        "com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperationsTest#branchCannotParkAtAJoinOutsideItsDeclaredConvergence",
        "com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperationsTest#joinFailsClosedWhenTwoBranchesWriteTheSameProcessVariable",
        "com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperationsTest#nestedJoinRestoresItsParentFrontierAndPropagatesExactWritesToTheOuterJoin",
        "com.alibaba.compileflow.durable.runtime.machine.DurableEvolutionContractTest#semanticPlanAndResumeCoordinatesAreStableWithoutProgramIdentity",
        "com.alibaba.compileflow.durable.runtime.machine.DurableEvolutionContractTest#expressionAllowlistRejectsCallsAndMutation",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#decodesTheFrozenStartContinuation",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#resumesTheFrozenWaitSnapshotAndCommittedResult",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#resumesTheFrozenTimerSnapshot",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#resumesTheFrozenEffectSnapshotAndCommittedResult",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#resumesTheFrozenLoopSnapshotWithoutImplementationVersionRouting",
        "com.alibaba.compileflow.durable.runtime.program.DurableCompatibilityCorpusTest#decodesTheFrozenKernelFactWithoutTreatingFormatAsIdentity",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#parallelWaitsResolveIndependentlyAndMergeDisjointStateInArrivalOrder",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#inclusiveForksEveryMatchingBranchAndUsesDefaultOnlyWhenNoneMatch",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#concurrentWaitResultsWritingTheSameVariableFailClosedAtJoin",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#persistedFrontierQueuePreventsALongBranchFromStarvingItsSibling",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#nestedConcurrentJoinPropagatesWritesToItsOuterBranch",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableConcurrentGatewayTest#concurrentBranchesMayConvergeDirectlyAtTheProcessEnd",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#sequenceProducesTheSameProcessOutput",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#forEachUsesTheSameSnapshotBindingsAndIterationOrder",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#processDefaultsApplyOnlyWhenTheCallerOmittedTheVariable",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#parallelMergesTheSameDisjointBranchWrites",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#inclusiveUsesTheSameSelectedSetAndDefaultLaw",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmProcessDurableDifferentialTest#nestedParallelScopesProduceTheSameDeterministicMerge",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableEffectActionCompilerTest#businessTaskActionBecomesAnEffectBoundaryWithoutASecondTargetIdentity",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableEffectActionCompilerTest#mutableProcessValuesAreDetachedBeforeApplicationCodeRuns",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableEffectActionCompilerTest#waitDescriptionReceivesDetachedState",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableScriptTaskTest#arbitraryScriptLanguageUsesTheSameProgramPath",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableScriptTaskTest#providerIdentityIsRuntimeOnly",
        "com.alibaba.compileflow.durable.runtime.program.TbbpmDurableSemanticCheckpointTest#loopWaitRecoversOnlyFromProcessSemanticCoordinates",
        "com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCacheTest#evictsLeastRecentlyUsedDisposableProgramAtTheBound",
        "com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorkerTest#rebuildsAnEmptyNodeLocalCacheFromCommittedRunDemand",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#heartbeatsAllThreeKindsOfApplicationWork",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#slowRunRenewalCannotStarveEffectOrOutboxRenewal",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#retriesAfterTransientStoreFailure",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#oneFailedChunkCannotStarveLaterAuthorities",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#persistentRenewalFaultsDegradeAndSuccessfulCycleRecovers",
        "com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewerTest#stalledRenewalCycleBecomesDegradedBeforeLeaseExpiry",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#completedTurnCommitsResultAndReportsStaleCompletionAsLeaseLoss",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#waitCommitContainsOnlyDigestInAuthorityAndRawTokenInOutboxPayload",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#committedWaitFactResumesFromExactSemanticCheckpoint",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#processCallPushesAnExactChildFrameInTheSameRun",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#processCallAndChildShareOneTurnBudget",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#preservesAChildReturnWhenItExhaustsTheTurnBudget",
        "com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerTest#cancellationCacheEvictionAndFaultUseDistinctFencedTransitions",
        "com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerTest#anyPostInvocationFailureBecomesUnknown",
        "com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerTest#recoveryDeadlineAndAttemptLimitFailClosedToReview",
        "com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerTest#staleCompletionIsObservedAsLeaseLossInsteadOfSuccess",
        "com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherTest#deliveryFailureRetriesBeforeLimitAndAbandonsAtLimit",
        "com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherTest#acceptedDeliveryWithLostCompletionReplaysTheSameLogicalEvent",
        "com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherTest#staleCompletionIsObservedAsLeaseLossInsteadOfSuccess",
        "com.alibaba.compileflow.durable.runtime.AliasAdmissionTest#admitsCommittedAliasOnceToAnAuthorizedExactTarget",
        "com.alibaba.compileflow.durable.runtime.service.DefaultDurableOperatorServiceTest#effectResolutionRejectsMismatchedTargetBeforeMutation",
        "com.alibaba.compileflow.durable.runtime.service.DefaultDurableOperatorServiceTest#timelineContinuationRejectsStoreSnapshotDrift",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#exactVersionStartBypassesAliasAdmission",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#aliasStartResolvesOnceAndPersistsOnlyTheSelectedVersion",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#laterAliasChangeCannotChangeAnExistingRunVersion",
        "com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManagerTest#recoveryUsesStoredSemanticsWithoutConsultingTheAdmissionSource",
        "com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManagerTest#admissionPersistsDistinctCallSitesWhilePreparingSharedMembersOnce",
        "com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManagerTest#admissionRejectsMappingsOutsideTheExactChildContract",
        "com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManagerTest#definitionSourcedCallSelectsAnExactChildVersion",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#startRejectsReturnInnerAndUndeclaredVariablesBeforeStoreMutation",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#startRejectsMismatchedStoreIdentity",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#completeWaitValidatesTypedProcessStateBeforeStoreMutation",
        "com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngineTest#getRunResultDistinguishesMissingActiveAndSucceededRuns",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.DeployDurableAdaptersTest#aliasStateIsMappedOnlyForNewRunAdmission",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.DeployDurableAdaptersTest#exactTbbpmArtifactIsVerifiedBeforeCrossingTheBoundary",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.DeployDurableAdaptersTest#modelTypeIsPreservedAndDigestMismatchFailsClosed",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.DeployDurableAdaptersTest#mismatchedSourceIdentitiesFailClosed",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.CompileFlowDurableOptionalObservabilityAutoConfigurationTest#composesDeployThroughTheTwoNarrowAdmissionAdapters",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.CompileFlowDurableOptionalObservabilityAutoConfigurationTest#startsWithoutOptionalDeployLibrary",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.postgres.DurablePostgresSchemaInitializerTest#rejectsPendingMigrationWhenDdlIsExternallyManaged",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableHealthIndicatorTest#persistentLeaseRenewalFaultIsDegradedWithoutLeakingFailureDetails",
        "com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime.DurableWorkerCoordinatorTest#gracefulStopDrainsInFlightWorkWithoutInterruptingIt",
        "com.alibaba.compileflow.durable.testkit.DurableTestCompilationTest#keepsDebugArtifactExportDisabledByDefault",
    }
)
POSTGRES_REQUIRED_TEST_CASES = frozenset(
    {
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#storedProcessIsImmutableAndIdempotent",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#everyStartCreatesANewExactVersionRun",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#waitCompletionResumeAndTerminalOutboxFormOneFencedChain",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#runReferencesProtectEveryProcessInTheRecoverySet",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#purgingATerminalRunReleasesItsRecoveryProcessReferences",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#pauseAndCancelWaitForEveryPossibleEffectDispatch",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#cancelAndDueTimerRaceCompletesWithoutDeadlock",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#pauseAndEffectClaimRaceHasOneSafeOutcomeWithoutDeadlock",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#timelinePaginationFreezesOneCommittedFactSnapshot",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#pauseResumeAndCancellationRemainOrthogonalToRunLifecycle",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#timerAndEffectBoundariesResumeFromCommittedFacts",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#outboxRetryUsesStableEventIdentityAndTokenFencing",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#effectReviewAndOutboxResolutionUseOccurrenceRevisions",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#committedRunDemandIsDiscoverableAfterRestart",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#terminalRunRetentionWaitsForRequiredOutboxAndPurgesTheWholeRun",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#consumedOccurrenceRetentionIsBoundedAndPreservesTheActiveRun",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#unusedProcessRetentionIsBoundedAndRunReferencesPreventDeletion",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#leasesCanBeRenewedAcrossLongApplicationWork",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#batchRenewalReturnsOnlyAuthoritiesThatRemainCurrent",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#batchRenewalRequiresWholeMillisecondLeaseDuration",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#expiredClaimsRecoverAfterWorkerCrashAndFenceEveryLateOwner",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#waitCommittedOutboxCannotBeAbandoned",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#waitAuthorityFulfillmentOrRevocationSettlesItsOutboxAndFencesLatePublishers",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#pauseAndEffectClaimRaceHasOneSafeOutcomeWithoutDeadlock",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#cancelAndDueTimerRaceCompletesWithoutDeadlock",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#cancelLeaseRenewalAndOutboxCompletionShareRunFirstLockOrder",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#oneTurnIssuesAndConsumesMultipleExactOccurrences",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#oneFrontierOwnsAtMostOneOutstandingOccurrenceAndAdvancesAfterExactConsumption",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#activeWorkIsSummarizedPagedAndMayCoexistWithRunnableFrontier",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableStoreContractTest#pauseAndCancelWaitForEveryPossibleEffectDispatch",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableOutboxAckLossTest#sigkillAfterDurableAcceptanceReplaysTheSameCommittedEvent",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#effectResponseLossBecomesUnknownThenReconcilesToOneContinuation",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#messageCatchSurvivesRuntimeLossAndResumesExactlyOnce",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#timerCatchSurvivesRuntimeLossAndResolvesOnceWhenDue",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#callActivityChildAndParentContinuationRecoverAsOneChain",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#parallelBranchesResumeFromPartialProgressAndConvergeExactly",
        "com.alibaba.compileflow.durable.postgres.LocalPostgresDurableBpmnCrashMatrixTest#parallelMultiInstanceRecoversPartialIterationsWithStableOrderedMerge",
    }
)
EXAMPLE_REQUIRED_TEST_CASES = frozenset(
    {
        "com.alibaba.compileflow.examples.durable.DurableSampleApplicationTest#persistsTimerEffectWaitCompletionAndRunCompletion",
        "com.alibaba.compileflow.examples.durable.DurableSampleApplicationTest#validatesExternallyMigratedSchemaWithoutDdl",
    }
)
REQUIRED_TEST_CASES = {
    "durable-kernel": KERNEL_REQUIRED_TEST_CASES,
    "postgres-contract": POSTGRES_REQUIRED_TEST_CASES,
    "durable-postgres-example": EXAMPLE_REQUIRED_TEST_CASES,
}
SUITE_POLICIES = {
    "durable-kernel": (None, 250, 165, False),
    "postgres-contract": (3, 50, 50, True),
    "durable-postgres-example": (1, 2, 2, True),
}
SUITE_METADATA_FIELDS = {
    "durable-kernel": frozenset({"commit", "java"}),
    "postgres-contract": frozenset(
        {"commit", "java", "postgres", "declared-image"}
    ),
    "durable-postgres-example": frozenset({"commit", "java"}),
}
MAXIMUM_MANIFEST_BYTES = 1_000_000
MAXIMUM_REPORT_BYTES = 50_000_000


class ReleaseEvidenceError(ValueError):
    """The matrix is incomplete, inconsistent, or not clean."""


def parse_args() -> argparse.Namespace:
    """Parse the required matrix and output path."""
    parser = argparse.ArgumentParser(
        description=(
            "Verify all Durable test evidence belongs to one commit and "
            "covers the exact required JDK, PostgreSQL, and example matrix."
        )
    )
    parser.add_argument("--evidence-root", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument(
        "--expected-java",
        action="append",
        required=True,
        help="Required JDK feature version; repeat for the complete matrix",
    )
    parser.add_argument(
        "--expected-postgres",
        action="append",
        required=True,
        help="Required PostgreSQL major.minor; repeat for the complete matrix",
    )
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def require_object(
    value: object,
    field: str,
    path: Path,
) -> dict[str, object]:
    """Require a JSON object."""
    if not isinstance(value, dict):
        raise ReleaseEvidenceError(
            f"{path}: {field} must be an object"
        )
    return value


def require_exact_fields(
    value: dict[str, object],
    fields: Iterable[str],
    name: str,
    path: Path,
) -> None:
    """Reject missing and undeclared evidence fields."""
    expected = set(fields)
    if set(value) != expected:
        raise ReleaseEvidenceError(
            f"{path}: {name} fields must be exactly {sorted(expected)}"
        )


def reject_duplicate_json_pairs(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    """Reject ambiguous JSON objects instead of taking the last key."""
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ReleaseEvidenceError(
                f"JSON object contains duplicate key {key!r}"
            )
        result[key] = value
    return result


def require_count(
    value: object,
    field: str,
    path: Path,
) -> int:
    """Require a non-negative integer count."""
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value < 0
    ):
        raise ReleaseEvidenceError(
            f"{path}: {field} must be a non-negative integer"
        )
    return value


def require_text(
    value: object,
    field: str,
    path: Path,
    maximum: int = 1_024,
) -> str:
    """Require bounded non-blank text."""
    if (
        not isinstance(value, str)
        or not value
        or len(value) > maximum
    ):
        raise ReleaseEvidenceError(
            f"{path}: {field} must contain 1..{maximum} characters"
        )
    return value


def require_list(value: object, field: str, path: Path) -> list[object]:
    """Require a JSON array."""
    if not isinstance(value, list):
        raise ReleaseEvidenceError(f"{path}: {field} must be an array")
    return value


def load_document(path: Path) -> tuple[dict[str, object], str]:
    """Load one JSON manifest and return its content digest."""
    if not path.is_file() or path.is_symlink():
        raise ReleaseEvidenceError(
            f"{path}: evidence manifest must be a real file"
        )
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAXIMUM_MANIFEST_BYTES:
            raise ReleaseEvidenceError(
                f"{path}: evidence manifest must contain "
                "1..1000000 bytes"
            )
        content = path.read_bytes()
        if len(content) != size:
            raise ReleaseEvidenceError(
                f"{path}: evidence manifest size changed while reading"
            )
        document = json.loads(
            content,
            object_pairs_hook=reject_duplicate_json_pairs,
        )
    except (OSError, json.JSONDecodeError) as failure:
        raise ReleaseEvidenceError(
            f"{path}: cannot read evidence JSON: {failure}"
        ) from failure
    return (
        require_object(document, "root", path),
        hashlib.sha256(content).hexdigest(),
    )


def recompute_raw_report(
    manifest_path: Path,
    filename: str,
    expected_digest: str,
) -> dict[str, object]:
    """Find, authenticate, and independently parse one raw JUnit report."""
    if (
        filename != Path(filename).name
        or not filename.startswith("TEST-")
        or not filename.endswith(".xml")
    ):
        raise ReleaseEvidenceError(
            f"{manifest_path}: raw report filename {filename!r} is invalid"
        )
    root = manifest_path.parent.resolve()
    candidates = []
    for candidate in manifest_path.parent.rglob(filename):
        resolved = candidate.resolve()
        if (
            not candidate.is_file()
            or candidate.is_symlink()
            or not resolved.is_relative_to(root)
        ):
            raise ReleaseEvidenceError(
                f"{manifest_path}: raw XML report {candidate} is unsafe"
            )
        candidates.append(resolved)
    matches = []
    for candidate in candidates:
        try:
            size = candidate.stat().st_size
            if size <= 0 or size > MAXIMUM_REPORT_BYTES:
                raise ReleaseEvidenceError(
                    f"{manifest_path}: raw XML report {candidate} must "
                    "contain 1..50000000 bytes"
                )
            content = candidate.read_bytes()
            if len(content) != size:
                raise ReleaseEvidenceError(
                    f"{manifest_path}: raw XML report {candidate} size "
                    "changed while reading"
                )
            digest = hashlib.sha256(content).hexdigest()
        except OSError as failure:
            raise ReleaseEvidenceError(
                f"{manifest_path}: cannot read raw report {candidate}: "
                f"{failure}"
            ) from failure
        if digest == expected_digest:
            matches.append(candidate)
    if len(matches) != 1:
        raise ReleaseEvidenceError(
            f"{manifest_path}: raw XML report {filename!r} must have "
            "exactly one digest match"
        )
    try:
        return parse_report(matches[0])
    except EvidenceError as failure:
        raise ReleaseEvidenceError(
            f"{manifest_path}: raw XML report {filename!r} is invalid: "
            f"{failure}"
        ) from failure


def validate_test_manifest(
    path: Path,
    document: dict[str, object],
    digest: str,
    expected_commit: str,
) -> dict[str, object]:
    """Validate one test-evidence subject without trusting its summary."""
    require_exact_fields(
        document,
        ("schema", "suite", "policy", "metadata", "summary", "reports"),
        "root",
        path,
    )
    if document.get("schema") != TEST_EVIDENCE_SCHEMA:
        raise ReleaseEvidenceError(
            f"{path}: unsupported test evidence schema "
            f"{document.get('schema')!r}"
        )
    suite = require_text(document.get("suite"), "suite", path, 128)
    metadata = require_object(document.get("metadata"), "metadata", path)
    metadata_fields = SUITE_METADATA_FIELDS.get(suite)
    if metadata_fields is None:
        raise ReleaseEvidenceError(f"{path}: unexpected evidence suite {suite!r}")
    require_exact_fields(metadata, metadata_fields, "metadata", path)
    commit = require_text(metadata.get("commit"), "metadata.commit", path)
    if commit != expected_commit:
        raise ReleaseEvidenceError(
            f"{path}: commit {commit!r} does not match "
            f"{expected_commit!r}"
        )

    reports_value = document.get("reports")
    if not isinstance(reports_value, list) or not reports_value:
        raise ReleaseEvidenceError(
            f"{path}: reports must be a non-empty array"
        )
    reports: list[dict[str, object]] = []
    names: list[str] = []
    testcase_statuses: dict[str, str] = {}
    for index, value in enumerate(reports_value):
        report = require_object(value, f"reports[{index}]", path)
        name = require_text(
            report.get("name"),
            f"reports[{index}].name",
            path,
            512,
        )
        filename = require_text(
            report.get("file"),
            f"reports[{index}].file",
            path,
            512,
        )
        xml_sha256 = require_text(
            report.get("xmlSha256"),
            f"reports[{index}].xmlSha256",
            path,
            64,
        )
        if not re.fullmatch(r"[0-9a-f]{64}", xml_sha256):
            raise ReleaseEvidenceError(
                f"{path}: reports[{index}].xmlSha256 is invalid"
            )
        recomputed = recompute_raw_report(path, filename, xml_sha256)
        if set(report) != set(recomputed):
            raise ReleaseEvidenceError(
                f"{path}: reports[{index}] fields do not match raw XML "
                "evidence"
            )
        for field, raw_value in recomputed.items():
            if report.get(field) != raw_value:
                raise ReleaseEvidenceError(
                    f"{path}: reports[{index}].{field} does not match "
                    "the independently parsed raw XML"
                )
        report = recomputed
        counts = {
            field: require_count(
                report.get(field),
                f"reports[{index}].{field}",
                path,
            )
            for field in COUNT_FIELDS
        }
        if (
            counts["passed"]
            + counts["failures"]
            + counts["errors"]
            + counts["skipped"]
            != counts["tests"]
        ):
            raise ReleaseEvidenceError(
                f"{path}: reports[{index}] counts do not add up"
            )
        if counts["flakes"]:
            raise ReleaseEvidenceError(
                f"{path}: reports[{index}] contains flaky tests"
            )
        testcase_values = require_list(
            report.get("testCases"),
            f"reports[{index}].testCases",
            path,
        )
        testcase_counts = {
            "PASSED": 0,
            "FAILURE": 0,
            "ERROR": 0,
            "SKIPPED": 0,
        }
        for case_index, case_value in enumerate(testcase_values):
            testcase = require_object(
                case_value,
                f"reports[{index}].testCases[{case_index}]",
                path,
            )
            identity = require_text(
                testcase.get("id"),
                f"reports[{index}].testCases[{case_index}].id",
                path,
            )
            status = require_text(
                testcase.get("status"),
                f"reports[{index}].testCases[{case_index}].status",
                path,
                16,
            )
            if status not in testcase_counts:
                raise ReleaseEvidenceError(
                    f"{path}: testcase {identity!r} has invalid status"
                )
            if identity in testcase_statuses:
                raise ReleaseEvidenceError(
                    f"{path}: duplicate testcase identity {identity!r}"
                )
            testcase_statuses[identity] = status
            testcase_counts[status] += 1
        expected_case_counts = {
            "PASSED": counts["passed"],
            "FAILURE": counts["failures"],
            "ERROR": counts["errors"],
            "SKIPPED": counts["skipped"],
        }
        if testcase_counts != expected_case_counts:
            raise ReleaseEvidenceError(
                f"{path}: reports[{index}] testcase outcomes do not "
                "match counts"
            )
        reports.append(report)
        names.append(name)

    duplicate_names = sorted(
        name for name, count in Counter(names).items()
        if count > 1
    )
    if duplicate_names:
        raise ReleaseEvidenceError(
            f"{path}: duplicate testsuite names: "
            + ", ".join(duplicate_names)
        )

    calculated = {
        field: sum(int(report[field]) for report in reports)
        for field in COUNT_FIELDS
    }
    calculated["reports"] = len(reports)
    summary = require_object(document.get("summary"), "summary", path)
    require_exact_fields(
        summary,
        (*COUNT_FIELDS, "reports"),
        "summary",
        path,
    )
    for field, value in calculated.items():
        if require_count(summary.get(field), f"summary.{field}", path) != value:
            raise ReleaseEvidenceError(
                f"{path}: summary.{field} does not match report totals"
            )
    if calculated["failures"] or calculated["errors"] or calculated["flakes"]:
        raise ReleaseEvidenceError(
            f"{path}: evidence contains failures or errors: {calculated}"
        )
    if calculated["tests"] == 0:
        raise ReleaseEvidenceError(f"{path}: evidence contains no tests")

    policy = require_object(document.get("policy"), "policy", path)
    require_exact_fields(
        policy,
        (
            "expectedReports",
            "minimumTests",
            "minimumPassed",
            "rejectSkips",
            "requiredTestCases",
        ),
        "policy",
        path,
    )
    expected_reports = policy.get("expectedReports")
    if expected_reports is not None:
        expected_report_count = require_count(
            expected_reports,
            "policy.expectedReports",
            path,
        )
        if expected_report_count <= 0 or expected_report_count != len(reports):
            raise ReleaseEvidenceError(
                f"{path}: expected-reports policy is not satisfied"
            )
    minimum_tests = require_count(
        policy.get("minimumTests"),
        "policy.minimumTests",
        path,
    )
    if minimum_tests <= 0 or calculated["tests"] < minimum_tests:
        raise ReleaseEvidenceError(
            f"{path}: minimum-tests policy is not satisfied"
        )
    minimum_passed = require_count(
        policy.get("minimumPassed"),
        "policy.minimumPassed",
        path,
    )
    if minimum_passed <= 0 or calculated["passed"] < minimum_passed:
        raise ReleaseEvidenceError(
            f"{path}: minimum-passed policy is not satisfied"
        )
    reject_skips = policy.get("rejectSkips")
    if not isinstance(reject_skips, bool):
        raise ReleaseEvidenceError(
            f"{path}: policy.rejectSkips must be boolean"
        )
    if reject_skips and calculated["skipped"]:
        raise ReleaseEvidenceError(
            f"{path}: non-skippable evidence contains skips"
        )
    required_values = require_list(
        policy.get("requiredTestCases"),
        "policy.requiredTestCases",
        path,
    )
    required_testcases = {
        require_text(value, "policy.requiredTestCases[]", path)
        for value in required_values
    }
    if len(required_testcases) != len(required_values):
        raise ReleaseEvidenceError(
            f"{path}: policy.requiredTestCases contains duplicates"
        )
    expected_required = REQUIRED_TEST_CASES.get(suite)
    if expected_required is not None and required_testcases != expected_required:
        raise ReleaseEvidenceError(
            f"{path}: {suite} critical testcase policy mismatch"
        )
    not_passed = sorted(
        identity
        for identity in required_testcases
        if testcase_statuses.get(identity) != "PASSED"
    )
    if not_passed:
        raise ReleaseEvidenceError(
            f"{path}: critical testcases are missing or not passed: "
            + ", ".join(not_passed)
        )
    expected_policy = SUITE_POLICIES.get(suite)
    if expected_policy is not None:
        (
            required_reports,
            required_minimum,
            required_passed,
            required_reject_skips,
        ) = expected_policy
        if (
            expected_reports != required_reports
            or minimum_tests < required_minimum
            or minimum_passed < required_passed
            or reject_skips is not required_reject_skips
        ):
            raise ReleaseEvidenceError(
                f"{path}: {suite} release policy is weaker than required"
            )

    return {
        "suite": suite,
        "metadata": metadata,
        "summary": calculated,
        "criticalTestCases": sorted(required_testcases),
        "testCaseCount": len(testcase_statuses),
        "inputSha256": digest,
    }


def normalize_unique(
    values: Iterable[str],
    field: str,
    pattern: re.Pattern[str],
) -> tuple[str, ...]:
    """Validate a required matrix dimension without duplicates."""
    result = tuple(values)
    if not result:
        raise ReleaseEvidenceError(f"{field} must not be empty")
    invalid = [value for value in result if not pattern.fullmatch(value)]
    if invalid:
        raise ReleaseEvidenceError(
            f"{field} contains invalid values: {invalid}"
        )
    duplicates = sorted(
        value for value, count in Counter(result).items()
        if count > 1
    )
    if duplicates:
        raise ReleaseEvidenceError(
            f"{field} contains duplicates: {duplicates}"
        )
    return result


def postgres_image(
    metadata: dict[str, object],
    version: str,
    path: Path,
) -> str:
    """Require a version-matching immutable PostgreSQL image digest."""
    image = require_text(
        metadata.get("declared-image"),
        "metadata.declared-image",
        path,
    )
    expected = re.compile(
        rf"postgres:{re.escape(version)}-[^@]+@sha256:[0-9a-f]{{64}}"
    )
    if not expected.fullmatch(image):
        raise ReleaseEvidenceError(
            f"{path}: PostgreSQL {version} image is not an immutable, "
            "version-matching digest"
        )
    return image


def postgres_environment(
    manifest_path: Path,
    version: str,
    declared_image: str,
    expected_commit: str,
) -> dict[str, str]:
    """Bind declared PostgreSQL metadata to the resolved image digest."""
    expected_fields = {
        "commit",
        "declared_postgres",
        "declared_image",
        "resolved_image",
    }
    values, content = load_environment(
        manifest_path,
        "postgres-environment.txt",
        "PostgreSQL",
        expected_fields,
    )
    if values["commit"] != expected_commit:
        raise ReleaseEvidenceError(
            f"{manifest_path}: PostgreSQL environment commit does not match"
        )
    if values["declared_postgres"] != version:
        raise ReleaseEvidenceError(
            f"{manifest_path}: PostgreSQL environment version does not match"
        )
    if values["declared_image"] != declared_image:
        raise ReleaseEvidenceError(
            f"{manifest_path}: PostgreSQL environment declared image does "
            "not match the test manifest"
        )
    resolved = re.fullmatch(
        r"[a-z0-9][a-z0-9._/-]*@sha256:([0-9a-f]{64})",
        values["resolved_image"],
    )
    declared_digest = declared_image.rsplit("@sha256:", 1)[1]
    if resolved is None or resolved.group(1) != declared_digest:
        raise ReleaseEvidenceError(
            f"{manifest_path}: resolved PostgreSQL image digest does not "
            "match the declared image"
        )
    return {
        "resolvedImage": values["resolved_image"],
        "environmentSha256": hashlib.sha256(content).hexdigest(),
    }


def load_environment(
    manifest_path: Path,
    filename: str,
    label: str,
    expected_fields: set[str],
) -> tuple[dict[str, str], bytes]:
    """Load one bounded, exact-field environment evidence file."""
    environment_path = manifest_path.parent / filename
    if not environment_path.is_file() or environment_path.is_symlink():
        raise ReleaseEvidenceError(
            f"{manifest_path}: {label} environment evidence is missing or unsafe"
        )
    try:
        content = environment_path.read_bytes()
    except OSError as failure:
        raise ReleaseEvidenceError(
            f"{manifest_path}: cannot read {label} environment evidence: {failure}"
        ) from failure
    if not content or len(content) > 4_096:
        raise ReleaseEvidenceError(
            f"{manifest_path}: {label} environment evidence must contain 1..4096 bytes"
        )
    try:
        lines = content.decode("utf-8").splitlines()
    except UnicodeDecodeError as failure:
        raise ReleaseEvidenceError(
            f"{manifest_path}: {label} environment evidence must be UTF-8"
        ) from failure
    values: dict[str, str] = {}
    for line in lines:
        key, separator, value = line.partition("=")
        if (
            not separator
            or re.fullmatch(r"[a-z][a-z0-9_]*", key) is None
            or not value
            or len(value) > 2_048
            or key in values
            or any(character in value for character in "\r\n\0")
        ):
            raise ReleaseEvidenceError(
                f"{manifest_path}: malformed {label} environment evidence"
            )
        values[key] = value
    if set(values) != expected_fields:
        raise ReleaseEvidenceError(
            f"{manifest_path}: {label} environment fields must be exactly "
            f"{sorted(expected_fields)}"
        )
    return values, content


def java_environment(
    manifest_path: Path,
    declared_java: str,
    expected_commit: str,
) -> dict[str, str]:
    """Bind a subject to its exact Java vendor, VM, runtime, OS, and arch."""
    expected_fields = {
        "commit",
        "declared_java",
        "java_runtime_version",
        "java_specification_version",
        "java_vendor",
        "java_vm_name",
        "java_vm_version",
        "os_arch",
        "os_name",
    }
    values, content = load_environment(
        manifest_path,
        "jdk-environment.txt",
        "Java",
        expected_fields,
    )
    if values["commit"] != expected_commit:
        raise ReleaseEvidenceError(
            f"{manifest_path}: Java environment commit does not match"
        )
    if values["declared_java"] != declared_java:
        raise ReleaseEvidenceError(
            f"{manifest_path}: Java environment feature does not match"
        )
    if values["java_specification_version"] != declared_java:
        raise ReleaseEvidenceError(
            f"{manifest_path}: Java specification version does not match"
        )
    if re.fullmatch(
        rf"{re.escape(declared_java)}(?:[.+_-].*)?",
        values["java_runtime_version"],
    ) is None:
        raise ReleaseEvidenceError(
            f"{manifest_path}: Java runtime version does not match"
        )
    return {
        "declaredJava": declared_java,
        "runtimeVersion": values["java_runtime_version"],
        "specificationVersion": values["java_specification_version"],
        "vendor": values["java_vendor"],
        "vmName": values["java_vm_name"],
        "vmVersion": values["java_vm_version"],
        "osName": values["os_name"],
        "osArch": values["os_arch"],
        "environmentSha256": hashlib.sha256(content).hexdigest(),
    }


def build_release_evidence(
    *,
    paths: Iterable[Path],
    commit: str,
    expected_java: Iterable[str],
    expected_postgres: Iterable[str],
) -> dict[str, object]:
    """Build a complete same-commit release-evidence manifest."""
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ReleaseEvidenceError(
            "commit must be a lowercase 40-character Git SHA"
        )
    java_matrix = normalize_unique(
        expected_java,
        "expected_java",
        re.compile(r"[0-9]+"),
    )
    postgres_matrix = normalize_unique(
        expected_postgres,
        "expected_postgres",
        POSTGRES_VERSION_PATTERN,
    )

    input_paths = tuple(sorted({path.resolve() for path in paths}))
    expected_subjects = len(java_matrix) + len(postgres_matrix) + 1
    if len(input_paths) != expected_subjects:
        raise ReleaseEvidenceError(
            f"expected {expected_subjects} evidence manifests, "
            f"found {len(input_paths)}"
        )

    subjects: dict[str, dict[str, object]] = {}
    for path in input_paths:
        document, digest = load_document(path)
        subject = validate_test_manifest(
            path,
            document,
            digest,
            commit,
        )
        suite = str(subject["suite"])
        metadata = require_object(subject["metadata"], "metadata", path)
        java = require_text(
            metadata.get("java"),
            "metadata.java",
            path,
            16,
        )
        subject["javaEnvironment"] = java_environment(
            path,
            java,
            commit,
        )

        if suite == "durable-kernel":
            subject_id = f"kernel-java-{java}"
            if java not in java_matrix:
                raise ReleaseEvidenceError(
                    f"{path}: unexpected kernel JDK {java}"
                )
        elif suite == "postgres-contract":
            version = require_text(
                metadata.get("postgres"),
                "metadata.postgres",
                path,
                32,
            )
            if version not in postgres_matrix:
                raise ReleaseEvidenceError(
                    f"{path}: unexpected PostgreSQL version {version}"
                )
            if java != "17":
                raise ReleaseEvidenceError(
                    f"{path}: PostgreSQL contract must use JDK 17"
                )
            subject_id = f"postgres-{version}"
            declared_image = postgres_image(
                metadata,
                version,
                path,
            )
            subject["declaredImage"] = declared_image
            subject.update(
                postgres_environment(
                    path,
                    version,
                    declared_image,
                    commit,
                )
            )
            if int(subject["summary"]["skipped"]) != 0:
                raise ReleaseEvidenceError(
                    f"{path}: PostgreSQL contract must not skip tests"
                )
        elif suite == "durable-postgres-example":
            if java != "17":
                raise ReleaseEvidenceError(
                    f"{path}: executable example must use JDK 17"
                )
            subject_id = "postgres-example-java-17"
            if int(subject["summary"]["skipped"]) != 0:
                raise ReleaseEvidenceError(
                    f"{path}: executable example must not skip tests"
                )
        else:
            raise ReleaseEvidenceError(
                f"{path}: unexpected evidence suite {suite!r}"
            )

        if subject_id in subjects:
            raise ReleaseEvidenceError(
                f"duplicate evidence subject {subject_id!r}"
            )
        subject.pop("metadata")
        subject["subjectId"] = subject_id
        subjects[subject_id] = subject

    required_ids = {
        *(f"kernel-java-{java}" for java in java_matrix),
        *(f"postgres-{version}" for version in postgres_matrix),
        "postgres-example-java-17",
    }
    actual_ids = set(subjects)
    if actual_ids != required_ids:
        raise ReleaseEvidenceError(
            "evidence matrix mismatch; missing="
            f"{sorted(required_ids - actual_ids)}, unexpected="
            f"{sorted(actual_ids - required_ids)}"
        )

    ordered_subjects = [subjects[key] for key in sorted(subjects)]
    totals = {
        field: sum(
            int(subject["summary"][field])
            for subject in ordered_subjects
        )
        for field in COUNT_FIELDS
    }
    totals["subjects"] = len(ordered_subjects)
    return {
        "schema": RELEASE_EVIDENCE_SCHEMA,
        "commit": commit,
        "matrix": {
            "java": list(java_matrix),
            "postgres": list(postgres_matrix),
            "executableExample": True,
        },
        "summary": totals,
        "subjects": ordered_subjects,
    }


def discover_manifests(root: Path) -> tuple[Path, ...]:
    """Discover test-evidence inputs below a downloaded-artifact root."""
    if not root.is_dir() or root.is_symlink():
        raise ReleaseEvidenceError(
            f"{root}: evidence root must be a real directory"
        )
    resolved_root = root.resolve()
    result: list[Path] = []
    for path in root.glob("**/*test-evidence.json"):
        resolved = path.resolve()
        if (
            not path.is_file()
            or path.is_symlink()
            or not resolved.is_relative_to(resolved_root)
        ):
            raise ReleaseEvidenceError(
                f"{path}: evidence manifest is missing or unsafe"
            )
        result.append(resolved)
    return tuple(sorted(result))


def main() -> int:
    """Validate downloaded inputs and emit one release Gate."""
    arguments = parse_args()
    try:
        evidence = build_release_evidence(
            paths=discover_manifests(arguments.evidence_root),
            commit=arguments.commit,
            expected_java=arguments.expected_java,
            expected_postgres=arguments.expected_postgres,
        )
        write_evidence(arguments.output, evidence)
    except ReleaseEvidenceError as failure:
        print(
            f"Durable release evidence failed: {failure}",
            file=sys.stderr,
        )
        return 1

    print(
        "Validated Durable release evidence: "
        f"{evidence['summary']}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
