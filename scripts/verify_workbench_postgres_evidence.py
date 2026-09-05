#!/usr/bin/env python3
"""Verify Workbench, Deploy, and runtime-chain PostgreSQL JUnit evidence."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Mapping
from pathlib import Path

if __package__:
    from .junit_evidence import parse_junit_report, status_counts
else:
    from junit_evidence import parse_junit_report, status_counts


REQUIRED_METHODS: dict[str, frozenset[str]] = {
    "ProcessDraftServicePersistenceTest": frozenset(
        {"roundTripsStructuredTagsAndEnforcesDraftRevision"}
    ),
    "ProcessOptimisticLockPersistenceTest": frozenset(
        {"exactlyOneConcurrentWriterCanCommitTheSameRevision"}
    ),
    "ExecutionLogAggregationPersistenceTest": frozenset(
        {
            "aggregatesTheSharedExecutionLogInsideOneBoundedWindow",
            "searchTreatsLikeMetacharactersLiterallyAndUsesStableOrdering",
            "retentionDeletionIsBoundedAndPreservesRowsAtTheCutoff",
        }
    ),
    "AsyncInvocationRepositoryStateMachineTest": frozenset(
        {
            "readyQueueUsesStableDurableAvailabilityOrder",
            "queuedInvocationCannotBeClaimedBeforeItsDurableAvailabilityTime",
            "concurrentClaimsProduceExactlyOneOwner",
            "expiredOwnerCannotRenewOrCommitEvenBeforeRecoveryRuns",
            "onlyTheCurrentUnexpiredOwnerCanPinInvocationRouting",
            "recoveredAndReclaimedInvocationRejectsLateResultFromPreviousOwner",
            "expiredInvocationCanBeRecoveredOnlyOnce",
            "concurrentExpiredRecoveryProducesExactlyOneWinner",
            "deadLetterCanBeRequeuedOnlyOnce",
            "concurrentDeadLetterRequeueProducesExactlyOneWinner",
            "attemptLedgerRemainsMonotonicAcrossDeadLetterRedrive",
            "attemptInsertFailureRollsBackInvocationClaim",
            "missingAttemptRollsBackInvocationCompletion",
        }
    ),
    "WorkbenchExternalSchemaAdmissionTest": frozenset(
        {"startsAfterExternalMigrationWithoutApplicationDdl"}
    ),
    "PostgreSqlDeployRepositoryContractTest": frozenset(
        {
            "concurrentImmutableVersionWritesPreserveOneExactIdentity",
            "concurrentRolloutsApplyCasAndKeepIdempotentReplayStable",
            "competingCanaryMutationAndCreateDoNotDeadlock",
            "concurrentOutboxClaimsSkipLockedRowsAndMakeParallelProgress",
            "expiredOutboxClaimIsReclaimedAndFencesThePreviousWorker",
            "successfulDeliveryBeforeCrashIsReplayedIdempotentlyAfterLeaseExpiry",
            "deliveredOutboxRetentionIsBoundedAndUsesOrderedIndex",
            "concurrentReconciliationEnsuresCoalesceToOneDelivery",
        }
    ),
    "DeploymentRuntimeChainIntegrationTest": frozenset(
        {
            "transactionalOutboxConvergesTwoNodesAcrossCanaryPromotionAndRollback",
            "reconcilerRepublishesTheAuthoritativeAliasForLateNodes",
            "processCallRemainsBoundToThePublishedChildVersionOnBothNodes",
        }
    ),
}


def verify_reports(
    root: Path, requirements: Mapping[str, frozenset[str]] = REQUIRED_METHODS
) -> list[str]:
    """Verify one clean, complete JUnit report for every required contract class."""
    errors: list[str] = []
    for test_class, required_methods in sorted(requirements.items()):
        matches = sorted(
            root.glob(f"**/target/surefire-reports/TEST-*{test_class}.xml")
        )
        if len(matches) != 1:
            errors.append(
                f"expected one JUnit report for {test_class}, found {len(matches)}"
            )
            continue

        report = matches[0]
        cases, report_errors = parse_junit_report(report)
        errors.extend(report_errors)
        counts = status_counts(cases)
        if counts["tests"] < 1 or any(
            counts[name] for name in ("failures", "errors", "skipped")
        ):
            errors.append(
                f"{report}: PostgreSQL evidence is not clean: "
                f"tests={counts['tests']}, failures={counts['failures']}, "
                f"errors={counts['errors']}, skipped={counts['skipped']}"
            )

        executed_methods = {
            case.name
            for case in cases
            if case.classname.rsplit(".", 1)[-1] == test_class
        }
        for method in sorted(required_methods - executed_methods):
            errors.append(f"{report}: missing required testcase: {test_class} :: {method}")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "root",
        nargs="?",
        type=Path,
        default=Path("."),
        help="repository root containing module target directories",
    )
    args = parser.parse_args(argv)

    errors = verify_reports(args.root)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    required_count = sum(len(methods) for methods in REQUIRED_METHODS.values())
    print(
        f"Verified {len(REQUIRED_METHODS)} PostgreSQL reports "
        f"and {required_count} required testcases"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
