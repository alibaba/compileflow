/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.durable.testkit;

import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.bytes;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.consumeClaimedResults;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.effectTurn;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.envelope;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.PROCESS_ID;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.ROOT_INVOCATION_ID;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.runnableTurn;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.succeededTurn;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.timerTurn;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.waitDeadlineTurn;
import static com.alibaba.compileflow.durable.testkit.DurableStoreContractFixtures.waitTurn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.command.EffectResolutionDecision;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ActiveEffect;
import com.alibaba.compileflow.durable.api.model.ActiveWait;
import com.alibaba.compileflow.durable.api.model.ActiveWork;
import com.alibaba.compileflow.durable.api.model.EffectReadinessCode;
import com.alibaba.compileflow.durable.api.model.EffectExecutionStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunControlState;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineEventCode;
import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executable conformance contract for the atomic Durable Store protocol.
 *
 * <p>Implementations supply a newly migrated, empty Store for every inherited
 * test. The contract verifies durable state transitions and token fencing; it
 * deliberately does not prescribe SQL, table layout, or application runtime
 * compatibility.</p>
 *
 * @author yusu
 */
public abstract class DurableStoreContract {
    protected static final ProcessRef.Version PROCESS_VERSION = ProcessRef.version("contract", "approval", "v1");
    private static final byte[] PROCESS_DEFINITION = bytes("contract-process-definition-v1");
    private static final DurableStore.RunProcess PROCESS = new DurableStore.RunProcess(PROCESS_ID,
            PROCESS_VERSION.namespace(), PROCESS_VERSION.code(), PROCESS_VERSION);
    private static final AuditPrincipal ACTOR = new AuditPrincipal("contract-test");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private DurableStore store;

    /**
     * Returns a newly migrated Store backed by an empty database.
     */
    protected abstract DurableStore createEmptyStore() throws Exception;

    /**
     * Returns the Store created for the current test.
     */
    protected final DurableStore store() {
        return store;
    }

    /**
     * Verifies through provider-owned storage inspection that no Wait delivery secret remains.
     */
    protected abstract void assertWaitCommittedAuthoritySecretDisposed(ProcessRunId runId) throws Exception;

    @BeforeEach
    final void initializeContractStore() throws Exception {
        store = createEmptyStore();
        registerProcess(PROCESS_VERSION);
    }

    @Test
    void storedProcessIsImmutableAndIdempotent() {
        DurableStore.StoredProcess first = store.findProcess(PROCESS.processId()).orElseThrow();
        DurableStore.StoredProcess replay = registerProcess(PROCESS_VERSION);

        assertThat(replay.processId()).isEqualTo(first.processId());
        assertThat(replay.processCode()).isEqualTo(PROCESS_VERSION.code());
        assertThat(replay.definitionBytes()).containsExactly(first.definitionBytes());
        assertThat(replay.definitionDigest()).isEqualTo(first.definitionDigest());
        assertThat(replay.registeredAt()).isEqualTo(first.registeredAt());
    }

    @Test
    void everyStartCreatesANewExactVersionRun() {
        ProcessRun first = store.start(
                newRun(ProcessRunId.random(), PROCESS, envelope("start-state"), envelope("alias-admission-fact")));
        ProcessRun second = store.start(newRun(ProcessRunId.random(), PROCESS, envelope("start-state")));

        assertThat(second.runId()).isNotEqualTo(first.runId());
        assertThat(first.processCode()).isEqualTo(PROCESS.processCode());
        assertThat(first.processVersion()).isEqualTo(PROCESS.processVersion());
        assertThat(first.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(store.findRunResult(first.runId())).hasValueSatisfying(result -> {
            assertThat(result.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
            assertThat(result.result()).isNull();
            assertThat(result.completedAt()).isNull();
        });
        assertThat(store.findRunResult(ProcessRunId.random())).isEmpty();
        assertThat(store.listTimeline(new DurableStore.TimelineQuery(first.runId(), 0, 0, 10)).items())
            .singleElement()
            .satisfies(fact -> assertThat(fact.fact().payload()).isEqualTo(bytes("alias-admission-fact")));
    }

    @Test
    void callerAllocatedRunIdentityRejectsDuplicateOccurrenceCreation() {
        ProcessRunId runId = ProcessRunId.random();
        store.start(newRun(runId, PROCESS, envelope("initial-state")));

        assertThatThrownBy(() -> store.start(newRun(runId, PROCESS, envelope("different-state"))))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.RUN_ALREADY_EXISTS));
        assertThat(store.findRun(runId)).hasValueSatisfying(run -> {
            assertThat(run.processCode()).isEqualTo(PROCESS.processCode());
            assertThat(run.processVersion()).isEqualTo(PROCESS.processVersion());
        });
    }

    @Test
    void waitCompletionResumeAndTerminalOutboxFormOneFencedChain() {
        ProcessRun started = startRun();
        DurableStore.RunClaim claim = claimRun();
        UUID waitId = UUID.randomUUID();
        String tokenDigest = DurableDigests.sha256("wait-token");

        assertThat(store.commitTurn(claim.lease(),
                waitTurn(waitId, 1, "approval", "approved", tokenDigest, envelope("waiting-state"),
                        envelope("wait-event"))))
            .isTrue();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("stale")))).isFalse();

        ProcessRun waiting = store.findRun(started.runId()).orElseThrow();
        assertThat(waiting.status()).isEqualTo(ProcessRunStatus.WAITING);
        assertThat(activeWait(waiting.runId()).elementId()).isEqualTo("approval");
        assertThat(store.findWaitTarget(tokenDigest))
            .contains(new DurableStore.WaitTarget(started.runId(), PROCESS, PROCESS.processId(), tokenDigest));
        assertThat(store.findWaitTarget(DurableDigests.sha256("unknown-token"))).isEmpty();
        DurableStore.OutboxClaim waitEvent =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", LEASE)).orElseThrow();
        assertThat(waitEvent.eventType()).isEqualTo("WAIT_COMMITTED");
        assertThat(waitEvent.occurrenceId()).isEqualTo(waitId);
        assertThat(store.retryOutbox(waitEvent.lease(), Duration.ZERO)).isTrue();

        DurableStore.WaitCompletion completion =
                new DurableStore.WaitCompletion(started.runId(), tokenDigest, envelope("approved-result"));
        ProcessRun completedWait = store.completeWait(completion);
        ProcessRun replay = store.completeWait(completion);
        assertThat(completedWait.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(replay.runId()).isEqualTo(started.runId());
        assertThatThrownBy(() -> store.completeWait(
                new DurableStore.WaitCompletion(started.runId(), tokenDigest, envelope("different-result"))))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.WAIT_COMPLETION_MISMATCH));

        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.occurrenceSequence()).isEqualTo(1);
        assertThat(resumed.occurrenceResults().get(0)).isInstanceOfSatisfying(DurableStore.WaitResult.class, result -> {
            assertThat(result.elementId()).isEqualTo("approval");
            assertThat(result.event()).isEqualTo("approved");
        });
        assertThat(store.commitTurn(resumed.lease(), consumeClaimedResults(resumed, succeededTurn(envelope("done"))))).isTrue();

        ProcessRun completed = store.findRun(started.runId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(ProcessRunStatus.SUCCEEDED);
        assertThat(completed.completedAt()).isAfterOrEqualTo(completed.updatedAt());
        assertThat(store.findRunResult(started.runId())).hasValueSatisfying(result -> {
            assertThat(result.rootProcess()).isEqualTo(PROCESS);
            assertThat(result.status()).isEqualTo(ProcessRunStatus.SUCCEEDED);
            assertThat(result.result().payload()).isEqualTo(bytes("done"));
            assertThat(result.completedAt()).isNotNull();
        });
        assertThat(store.listOutbox(new DurableStore.OutboxQuery(null, null, Set.of(), null, null, 20)).items())
            .extracting(OutboxEvent::eventType)
            .containsExactlyInAnyOrder("WAIT_COMMITTED", "RUN_SUCCEEDED");
    }

    @Test
    void oneTurnIssuesAndConsumesMultipleExactOccurrences() {
        ProcessRun run = startRun();
        DurableStore.RunClaim first = claimRun();
        UUID firstWaitId = UUID.randomUUID();
        UUID secondWaitId = UUID.randomUUID();
        String firstToken = DurableDigests.sha256("multi-wait-1");
        String secondToken = DurableDigests.sha256("multi-wait-2");
        List<DurableStore.OccurrenceCommit> issued = List.of(new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                firstWaitId), 1, PROCESS_ID, ROOT_INVOCATION_ID, "wait-a", "approval-a", "approved-a",
                        firstToken, null, envelope("wait-a")),
                new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                secondWaitId), 2, PROCESS_ID, ROOT_INVOCATION_ID, "wait-b", "approval-b", "approved-b",
                        secondToken, null, envelope("wait-b")));

        assertThat(store.commitTurn(first.lease(),
                new DurableStore.TurnCommit(List.of(), issued, new DurableStore.WaitingTurn(envelope("both-waiting")))))
            .isTrue();
        assertThat(store.findRun(run.runId()).orElseThrow().activeWork().waits()).isEqualTo(2);

        store.completeWait(new DurableStore.WaitCompletion(run.runId(), firstToken, envelope("result-a")));
        store.completeWait(new DurableStore.WaitCompletion(run.runId(), secondToken, envelope("result-b")));
        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.occurrenceResults())
            .extracting(DurableStore.OccurrenceResult::occurrenceSequence)
            .containsExactly(1L, 2L);
        assertThat(resumed.occurrenceResults())
            .extracting(DurableStore.OccurrenceResult::frontierId)
            .containsExactly("wait-a", "wait-b");
        assertThat(store.commitTurn(resumed.lease(),
                new DurableStore.TurnCommit(resumed
                            .occurrenceResults()
                            .stream()
                            .map(DurableStore.OccurrenceResult::occurrence)
                            .toList(), List.of(), new DurableStore.SucceededTurn(envelope("done")))))
            .isTrue();
        assertThat(store.findRun(run.runId()).orElseThrow().status()).isEqualTo(ProcessRunStatus.SUCCEEDED);
    }

    @Test
    void consumingOneResolvedFrontierKeepsRunRunnableWhileAnotherResultRemains() {
        ProcessRun run = startRun();
        DurableStore.RunClaim first = claimRun();
        UUID firstWaitId = UUID.randomUUID();
        UUID secondWaitId = UUID.randomUUID();
        String firstToken = DurableDigests.sha256("partial-multi-wait-1");
        String secondToken = DurableDigests.sha256("partial-multi-wait-2");
        List<DurableStore.OccurrenceCommit> issued = List.of(new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                firstWaitId), 1, PROCESS_ID, ROOT_INVOCATION_ID, "frontier-a", "approval-a",
                        "approved-a", firstToken, null, envelope("wait-a")),
                new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                secondWaitId), 2, PROCESS_ID, ROOT_INVOCATION_ID, "frontier-b", "approval-b",
                        "approved-b", secondToken, null, envelope("wait-b")));
        assertThat(store.commitTurn(first.lease(),
                new DurableStore.TurnCommit(List.of(), issued, new DurableStore.WaitingTurn(envelope("both-waiting")))))
            .isTrue();

        store.completeWait(new DurableStore.WaitCompletion(run.runId(), firstToken, envelope("result-a")));
        store.completeWait(new DurableStore.WaitCompletion(run.runId(), secondToken, envelope("result-b")));
        DurableStore.RunClaim bothResolved = claimRun();
        assertThat(store.commitTurn(bothResolved.lease(),
                new DurableStore.TurnCommit(List.of(bothResolved.occurrenceResults().get(0).occurrence()), List.of(),
                        new DurableStore.WaitingTurn(envelope("one-result-remains")))))
            .isTrue();
        assertThat(store.findRun(run.runId()).orElseThrow().status()).isEqualTo(ProcessRunStatus.RUNNABLE);

        DurableStore.RunClaim remaining = claimRun();
        assertThat(remaining.occurrenceResults())
            .singleElement()
            .satisfies(result -> {
                assertThat(result.occurrence().id()).isEqualTo(secondWaitId);
                assertThat(result.frontierId()).isEqualTo("frontier-b");
            });
        assertThat(store.commitTurn(remaining.lease(),
                new DurableStore.TurnCommit(List.of(remaining.occurrenceResults().get(0).occurrence()), List.of(),
                        new DurableStore.SucceededTurn(envelope("done")))))
            .isTrue();
    }

    @Test
    void oneFrontierOwnsAtMostOneOutstandingOccurrenceAndAdvancesAfterExactConsumption() {
        ProcessRun run = startRun();
        DurableStore.RunClaim first = claimRun();
        UUID firstWaitId = UUID.randomUUID();
        String firstToken = DurableDigests.sha256("frontier-first");
        assertThat(store.commitTurn(first.lease(),
                waitTurn(firstWaitId, 1, "first", "continued", firstToken, envelope("first-waiting"),
                        envelope("first-outbox"))))
            .isTrue();

        store.completeWait(new DurableStore.WaitCompletion(run.runId(), firstToken, envelope("first-result")));
        DurableStore.RunClaim resumed = claimRun();
        UUID secondWaitId = UUID.randomUUID();
        String secondToken = DurableDigests.sha256("frontier-second");
        DurableStore.TurnCommit missingConsumption = waitTurn(secondWaitId, 2, "second", "continued", secondToken,
                envelope("second-waiting"), envelope("second-outbox"));
        assertThatThrownBy(() -> store.commitTurn(resumed.lease(), missingConsumption))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.CONCURRENT_MODIFICATION));

        DurableStore.WaitCommit next = (DurableStore.WaitCommit) missingConsumption.issuedOccurrences().get(0);
        DurableStore.TurnCommit advancing = new DurableStore.TurnCommit(List.of(resumed
                    .occurrenceResults()
                    .get(0)
                    .occurrence()), List.of(next), new DurableStore.WaitingTurn(envelope("second-waiting")));
        assertThat(store.commitTurn(resumed.lease(), advancing)).isTrue();
        store.completeWait(new DurableStore.WaitCompletion(run.runId(), secondToken, envelope("second-result")));
        DurableStore.RunClaim finalClaim = claimRun();
        assertThat(finalClaim.occurrenceResults())
            .singleElement()
            .satisfies(result -> {
                assertThat(result.frontierId()).isEqualTo("root");
                assertThat(result.occurrence().id()).isEqualTo(secondWaitId);
            });
        assertThat(store.commitTurn(finalClaim.lease(),
                new DurableStore.TurnCommit(List.of(finalClaim.occurrenceResults().get(0).occurrence()), List.of(),
                        new DurableStore.SucceededTurn(envelope("done")))))
            .isTrue();
    }

    @Test
    void activeWorkIsSummarizedPagedAndMayCoexistWithRunnableFrontier() {
        ProcessRun run = startRun();
        DurableStore.RunClaim claim = claimRun();
        UUID eventId = UUID.randomUUID();
        UUID timerId = UUID.randomUUID();
        UUID firstEffectId = UUID.randomUUID();
        UUID secondEffectId = UUID.randomUUID();
        List<DurableStore.OccurrenceCommit> issued = List.of(new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                eventId), 1, PROCESS_ID, ROOT_INVOCATION_ID, "event-frontier", "event", "continue",
                        DurableDigests.sha256("multi-active-event"), null, envelope("event-outbox")),
                new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER, timerId),
                        2, PROCESS_ID, ROOT_INVOCATION_ID, "timer-frontier", "timer", Duration.ofHours(1), null),
                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                firstEffectId), 3, PROCESS_ID, ROOT_INVOCATION_ID, "effect-frontier-a", "charge-a",
                        EffectRecoveryPlan.manual(), envelope("effect-a")),
                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                secondEffectId), 4, PROCESS_ID, ROOT_INVOCATION_ID, "effect-frontier-b", "charge-b",
                        EffectRecoveryPlan.manual(), envelope("effect-b")));

        assertThat(store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(List.of(), issued,
                        new DurableStore.RunnableTurn(envelope("frontier-runnable")))))
            .isTrue();
        ProcessRun projected = store.findRun(run.runId()).orElseThrow();
        assertThat(projected.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(projected.activeWork().waits()).isOne();
        assertThat(projected.activeWork().timers()).isOne();
        assertThat(projected.activeWork().effects()).isEqualTo(2);

        DurableStore.ActiveWorkPage firstPage =
                store.listActiveWork(new DurableStore.ActiveWorkQuery(run.runId(), 0, 2));
        assertThat(firstPage.items()).extracting(ActiveWork::occurrenceSequence).containsExactly(1L, 2L);
        assertThat(firstPage.items()).extracting(ActiveWork::frontierId).containsExactly("event-frontier",
                "timer-frontier");
        assertThat(firstPage.nextSequence()).isEqualTo(2L);
        DurableStore.ActiveWorkPage secondPage =
                store.listActiveWork(new DurableStore.ActiveWorkQuery(run.runId(), firstPage.nextSequence(), 2));
        assertThat(secondPage.items()).extracting(ActiveWork::occurrenceSequence).containsExactly(3L, 4L);
        assertThat(secondPage.items())
            .extracting(ActiveWork::frontierId)
            .containsExactly("effect-frontier-a", "effect-frontier-b");
        assertThat(secondPage.nextSequence()).isNull();
    }

    @Test
    void effectRecoveryPlanIsFrozenWithItsOccurrence() {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        EffectRecoveryPlan expected = EffectRecoveryPlan.reconcile(3, 5, Duration.ofSeconds(2), Duration.ofMinutes(30));
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(turn.lease(),
                new DurableStore.TurnCommit(List.of(),
                        List.of(
                                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                                effectId), 1, PROCESS_ID, ROOT_INVOCATION_ID, "root", "charge", expected,
                                        envelope("effect-input"))),
                        new DurableStore.WaitingTurn(envelope("effect-state")))))
            .isTrue();

        DurableStore.EffectClaim claim = claimEffect();
        assertThat(claim.runId()).isEqualTo(run.runId());
        assertThat(claim.recoveryPlan()).isEqualTo(expected);
    }

    @Test
    void pauseAndCancelWaitForEveryPossibleEffectDispatch() {
        ProcessRun pausedRun = startRun();
        issueTwoEffects(pausedRun.runId());
        DurableStore.EffectClaim first = claimEffect();
        DurableStore.EffectClaim second = claimEffect();

        ProcessRun pauseRequested = store.control(
                new DurableStore.ControlCommand(pausedRun.runId(), 0, DurableStore.ControlOperation.PAUSE, ACTOR,
                        "maintenance", null));
        assertThat(pauseRequested.control().state()).isEqualTo(ProcessRunControlState.PAUSE_REQUESTED);
        assertThat(pauseRequested.activeWork().runningEffects()).isEqualTo(2);
        assertThat(store.releaseEffectBeforeInvocation(first.lease(), Duration.ZERO,
                EffectReadinessCode.ACTION_NOT_READY))
            .isTrue();
        assertThat(store.findRun(pausedRun.runId()).orElseThrow().control().state())
            .isEqualTo(ProcessRunControlState.PAUSE_REQUESTED);
        assertThat(store.releaseEffectBeforeInvocation(second.lease(), Duration.ZERO,
                EffectReadinessCode.ACTION_NOT_READY))
            .isTrue();
        assertThat(store.findRun(pausedRun.runId()).orElseThrow().control().state()).isEqualTo(
                ProcessRunControlState.PAUSED);

        ProcessRun cancelRun = startRun();
        issueTwoEffects(cancelRun.runId());
        DurableStore.EffectClaim cancelFirst = claimEffect();
        DurableStore.EffectClaim cancelSecond = claimEffect();
        ProcessRun cancelling = store.requestCancel(new DurableStore.CancelCommand(cancelRun.runId()));
        assertThat(cancelling.status()).isEqualTo(ProcessRunStatus.WAITING);
        assertThat(cancelling.activeWork().runningEffects()).isEqualTo(2);
        assertThat(store.completeEffect(cancelFirst.lease(), envelope("first-known"))).isTrue();
        ProcessRun stillCancelling = store.findRun(cancelRun.runId()).orElseThrow();
        assertThat(stillCancelling.status()).isEqualTo(ProcessRunStatus.WAITING);
        assertThat(stillCancelling.activeWork().runningEffects()).isOne();
        assertThat(store.completeEffect(cancelSecond.lease(), envelope("second-known"))).isTrue();
        ProcessRun cancelled = store.findRun(cancelRun.runId()).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(ProcessRunStatus.CANCELLED);
        assertThat(cancelled.activeWork().isEmpty()).isTrue();
    }

    @Test
    void timelinePaginationFreezesOneCommittedFactSnapshot() {
        ProcessRun run = startRun();
        DurableStore.RunClaim claim = claimRun();
        String tokenDigest = DurableDigests.sha256("timeline-token");
        assertThat(store.commitTurn(claim.lease(),
                waitTurn(UUID.randomUUID(), 1, "approval", "approved", tokenDigest, envelope("waiting-state"),
                        envelope("wait-event"))))
            .isTrue();

        DurableStore.TimelinePage first = store.listTimeline(new DurableStore.TimelineQuery(run.runId(), 0, 0, 1));
        assertThat(first.snapshotSequence()).isEqualTo(2);
        assertThat(first.items())
            .extracting(DurableStore.JournalFact::type)
            .containsExactly(ProcessTimelineEventCode.RUN_CREATED.value());
        assertThat(first.nextSequence()).isEqualTo(1);

        store.completeWait(new DurableStore.WaitCompletion(run.runId(), tokenDigest, envelope("approved-result")));

        DurableStore.TimelinePage second = store.listTimeline(
                new DurableStore.TimelineQuery(run.runId(), first.snapshotSequence(), first.nextSequence(), 1));
        assertThat(second.snapshotSequence()).isEqualTo(first.snapshotSequence());
        assertThat(second.items())
            .extracting(DurableStore.JournalFact::type)
            .containsExactly(ProcessTimelineEventCode.WAIT_COMMITTED.value());
        assertThat(second.nextSequence()).isNull();

        DurableStore.TimelinePage current = store.listTimeline(new DurableStore.TimelineQuery(run.runId(), 0, 0, 10));
        assertThat(current.items())
            .extracting(DurableStore.JournalFact::type)
            .containsExactly(ProcessTimelineEventCode.RUN_CREATED.value(),
                    ProcessTimelineEventCode.WAIT_COMMITTED.value(), ProcessTimelineEventCode.WAIT_COMPLETED.value());
    }

    @Test
    void resolvedOccurrenceIsConsumedExactlyOnceByTheFencedTurn() {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID waitId = UUID.randomUUID();
        String tokenDigest = DurableDigests.sha256("consume-token");
        assertThat(store.commitTurn(turn.lease(),
                waitTurn(waitId, 1, "approval", "approved", tokenDigest, envelope("waiting-state"),
                        envelope("wait-event"))))
            .isTrue();
        store.completeWait(new DurableStore.WaitCompletion(run.runId(), tokenDigest, envelope("result")));

        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.occurrenceResults())
            .singleElement()
            .satisfies(result -> {
                assertThat(result.occurrence()).isEqualTo(
                        new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT, waitId));
                assertThat(result.occurrenceSequence()).isEqualTo(1);
            });
        assertThatThrownBy(() -> store.commitTurn(resumed.lease(), succeededTurn(envelope("missing-consume"))))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.CONCURRENT_MODIFICATION));
        DurableStore.TurnCommit wrongConsumption = new DurableStore.TurnCommit(List.of(
                        new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT, UUID.randomUUID())), List.of(),
                new DurableStore.RunnableTurn(envelope("wrong-consume")));
        assertThatThrownBy(() -> store.commitTurn(resumed.lease(), wrongConsumption))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.CONCURRENT_MODIFICATION));

        assertThat(store.commitTurn(resumed.lease(),
                consumeClaimedResults(resumed, runnableTurn(envelope("after-consume")))))
            .isTrue();
        assertThat(store.commitTurn(resumed.lease(),
                consumeClaimedResults(resumed, runnableTurn(envelope("duplicate-consume")))))
            .isFalse();

        DurableStore.RunClaim next = claimRun();
        assertThat(next.continuation().payload()).isEqualTo(bytes("after-consume"));
        assertThat(next.occurrenceResults()).isEmpty();
    }

    @Test
    void pauseResumeAndCancellationRemainOrthogonalToRunLifecycle() {
        ProcessRun started = startRun();
        ProcessRun paused = store.control(
                new DurableStore.ControlCommand(started.runId(), 0, DurableStore.ControlOperation.PAUSE, ACTOR, "pause",
                        null));
        assertThat(paused.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(paused.control().state()).isEqualTo(ProcessRunControlState.PAUSED);
        ProcessRun pauseReplay = store.control(
                new DurableStore.ControlCommand(started.runId(), 0, DurableStore.ControlOperation.PAUSE, ACTOR, "pause",
                        null));
        assertThat(pauseReplay.control().revision()).isEqualTo(paused.control().revision());
        assertThat(store.claimRun(claimRequest())).isEmpty();

        ProcessRun resumed = store.control(
                new DurableStore.ControlCommand(started.runId(), paused.control().revision(),
                        DurableStore.ControlOperation.RESUME, ACTOR, "resume", null));
        assertThat(resumed.control().state()).isEqualTo(ProcessRunControlState.ACTIVE);

        DurableStore.RunClaim claim = claimRun();
        ProcessRun cancelling = store.requestCancel(new DurableStore.CancelCommand(started.runId()));
        assertThat(cancelling.status()).isEqualTo(ProcessRunStatus.RUNNING);
        assertThat(cancelling.cancelRequestedAt()).isNotNull();
        ProcessRun cancelReplay = store.requestCancel(new DurableStore.CancelCommand(started.runId()));
        assertThat(cancelReplay.cancelRequestedAt()).isEqualTo(cancelling.cancelRequestedAt());
        assertThat(cancelReplay.updatedAt()).isEqualTo(cancelling.updatedAt());

        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("ignored")))).isTrue();
        assertThat(store.findRun(started.runId()).orElseThrow().status()).isEqualTo(ProcessRunStatus.CANCELLED);
        assertThat(store.commitRunCancelled(claim.lease())).isFalse();
    }

    @Test
    void runnableTurnPersistsContinuationAndFencesTheCompletedLease() {
        ProcessRun started = startRun();
        DurableStore.RunClaim first = claimRun();

        assertThat(store.commitTurn(first.lease(), runnableTurn(envelope("yielded-state")))).isTrue();
        assertThat(store.commitTurn(first.lease(), succeededTurn(envelope("stale")))).isFalse();
        assertThat(store.findRun(started.runId())).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
            assertThat(run.control().state()).isEqualTo(ProcessRunControlState.ACTIVE);
        });

        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.lease().runId()).isEqualTo(started.runId());
        assertThat(resumed.continuation().payload()).isEqualTo(bytes("yielded-state"));
        assertThat(resumed.occurrenceSequence()).isZero();
        assertThat(resumed.occurrenceResults()).isEmpty();
        assertThat(store.commitTurn(resumed.lease(), succeededTurn(envelope("done")))).isTrue();
    }

    @Test
    void waitingTurnWithoutOutstandingAuthorityIsRejectedAtomically() {
        ProcessRun started = startRun();
        DurableStore.RunClaim claim = claimRun();
        DurableStore.TurnCommit invalid =
                new DurableStore.TurnCommit(List.of(), List.of(), new DurableStore.WaitingTurn(envelope("orphaned")));

        assertThatThrownBy(() -> store.commitTurn(claim.lease(), invalid))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.INVALID_ARGUMENT));
        assertThat(store.findRun(started.runId())).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(ProcessRunStatus.RUNNING);
        });
        assertThat(store.commitTurn(claim.lease(), runnableTurn(envelope("recovered")))).isTrue();
    }

    @Test
    void pauseRequestedDuringRunningBecomesPausedWhenRunnableTurnCommits() {
        ProcessRun started = startRun();
        DurableStore.RunClaim claim = claimRun();

        ProcessRun pauseRequested = store.control(
                new DurableStore.ControlCommand(started.runId(), 0, DurableStore.ControlOperation.PAUSE, ACTOR,
                        "pause-yield", null));
        assertThat(pauseRequested.status()).isEqualTo(ProcessRunStatus.RUNNING);
        assertThat(pauseRequested.control().state()).isEqualTo(ProcessRunControlState.PAUSE_REQUESTED);

        assertThat(store.commitTurn(claim.lease(), runnableTurn(envelope("paused-state")))).isTrue();
        ProcessRun paused = store.findRun(started.runId()).orElseThrow();
        assertThat(paused.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(paused.control().state()).isEqualTo(ProcessRunControlState.PAUSED);
        assertThat(store.claimRun(claimRequest())).isEmpty();

        store.control(
                new DurableStore.ControlCommand(started.runId(), paused.control().revision(),
                        DurableStore.ControlOperation.RESUME, ACTOR, "resume-yield", null));
        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.continuation().payload()).isEqualTo(bytes("paused-state"));
    }

    @Test
    void timerAndEffectBoundariesResumeFromCommittedFacts() {
        ProcessRun timerRun = startRun();
        DurableStore.RunClaim timerClaim = claimRun();
        assertThat(store.commitTurn(timerClaim.lease(),
                timerTurn(UUID.randomUUID(), 1, "deadline", Duration.ZERO, null, envelope("timer-state"))))
            .isTrue();
        assertThat(store.resolveDueWaits(10)).isOne();
        DurableStore.RunClaim fired = claimRun();
        assertThat(fired.lease().runId()).isEqualTo(timerRun.runId());
        assertThat(fired.occurrenceResults().get(0)).isInstanceOf(DurableStore.TimerResult.class);
        assertThat(store.commitTurn(fired.lease(), consumeClaimedResults(fired, succeededTurn(envelope("timer-done")))))
            .isTrue();

        ProcessRun effectRun = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(effectId, 1, "charge", envelope("effect-input"), envelope("effect-state"))))
            .isTrue();
        DurableStore.EffectClaim effect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(effect.runId()).isEqualTo(effectRun.runId());
        assertThat(effect.dispatchAttempts()).isOne();
        assertThat(store.completeEffect(effect.lease(), envelope("effect-result"))).isTrue();

        DurableStore.RunClaim afterEffect = claimRun();
        assertThat(afterEffect.lease().runId()).isEqualTo(effectRun.runId());
        assertThat(afterEffect.occurrenceResults().get(0)).isInstanceOf(DurableStore.EffectResult.class);
        assertThat(store.commitTurn(afterEffect.lease(),
                consumeClaimedResults(afterEffect, succeededTurn(envelope("effect-done")))))
            .isTrue();
    }

    @Test
    void eventDeadlineExpiresTheSameWaitOccurrenceAndRevokesItsTokenDelivery() throws Exception {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID waitId = UUID.randomUUID();
        String token = DurableDigests.sha256("expiring-wait");
        assertThat(store.commitTurn(turn.lease(),
                waitDeadlineTurn(waitId, 1, "approval", "approved", token, Duration.ZERO, envelope("state"),
                        envelope("wait-token"))))
            .isTrue();

        assertThat(activeWait(run.runId())).satisfies(wait -> {
            assertThat(wait.event()).isEqualTo("approved");
            assertThat(wait.dueAt()).isNotNull();
        });

        assertThat(store.resolveDueWaits(10)).isOne();
        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.lease().runId()).isEqualTo(run.runId());
        assertThat(resumed.occurrenceResults())
            .singleElement()
            .isInstanceOfSatisfying(DurableStore.WaitResult.class, result -> {
                assertThat(result.resolution()).isEqualTo(DurableStore.WaitResolution.EXPIRED);
                assertThat(result.deadline()).isNotNull();
                assertThat(result.resolvedAt()).isAfterOrEqualTo(result.deadline());
            });
        assertThatThrownBy(() -> store.completeWait(
                new DurableStore.WaitCompletion(run.runId(), token, envelope("late-result"))))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.INVALID_WAIT_TOKEN));
        assertThat(store.listOutbox(new DurableStore.OutboxQuery(null, null, Set.of(), null, null, 20)).items())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.eventType()).isEqualTo("WAIT_COMMITTED");
                assertThat(event.status()).isEqualTo(OutboxEventStatus.ABANDONED);
            });
        assertWaitCommittedAuthoritySecretDisposed(run.runId());
    }

    @Test
    void eventCompletionBeforeDeadlineWinsAndDeadlineCannotResolveItAgain() {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID waitId = UUID.randomUUID();
        String token = DurableDigests.sha256("completed-before-deadline");
        assertThat(store.commitTurn(turn.lease(),
                waitDeadlineTurn(waitId, 1, "approval", "approved", token, Duration.ofDays(1), envelope("state"),
                        envelope("wait-token"))))
            .isTrue();

        store.completeWait(new DurableStore.WaitCompletion(run.runId(), token, envelope("approved-result")));
        assertThat(store.resolveDueWaits(10)).isZero();
        DurableStore.RunClaim resumed = claimRun();
        assertThat(resumed.occurrenceResults())
            .singleElement()
            .isInstanceOfSatisfying(DurableStore.WaitResult.class, result -> {
                assertThat(result.resolution()).isEqualTo(DurableStore.WaitResolution.COMPLETED);
                assertThat(result.deadline()).isNull();
                assertThat(result.result().payload()).isEqualTo(bytes("approved-result"));
            });
    }

    @Test
    void outboxRetryUsesStableEventIdentityAndTokenFencing() {
        ProcessRun run = startRun();
        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();

        DurableStore.OutboxClaim first =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", LEASE)).orElseThrow();
        assertThat(first.runId()).isEqualTo(run.runId());
        assertThat(first.occurrenceId()).isNull();
        assertThat(first.attempt()).isOne();
        assertThat(store.retryOutbox(first.lease(), Duration.ZERO)).isTrue();
        assertThat(store.completeOutbox(first.lease())).isFalse();

        DurableStore.OutboxClaim second =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", LEASE)).orElseThrow();
        assertThat(second.lease().eventId()).isEqualTo(first.lease().eventId());
        assertThat(second.rootProcess()).isEqualTo(first.rootProcess());
        assertThat(second.eventType()).isEqualTo(first.eventType());
        assertThat(second.payload().bytes()).containsExactly(first.payload().bytes());
        assertThat(second.lease().token()).isNotEqualTo(first.lease().token());
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(store.completeOutbox(second.lease())).isTrue();

        OutboxEvent delivered = store.findOutbox(run.runId(), second.lease().eventId()).orElseThrow();
        assertThat(delivered.status()).isEqualTo(OutboxEventStatus.DELIVERED);
        assertThat(delivered.attemptCount()).isEqualTo(2);
    }

    @Test
    void effectReviewAndOutboxResolutionUseOccurrenceRevisions() {
        ProcessRun effectRun = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(effectId, 1, "charge", envelope("effect-input"), envelope("effect-state"))))
            .isTrue();
        DurableStore.EffectClaim effect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(store.requireEffectReview(effect.lease(), "outcome unknown")).isTrue();
        ActiveEffect review = activeEffect(effectRun.runId());
        assertThat(review.reviewRequired()).isTrue();
        assertThat(review.reviewRevision()).isPositive();

        DurableStore.EffectResolution retry = new DurableStore.EffectResolution(effectRun.runId(), effectId,
                review.reviewRevision(), EffectResolutionDecision.CONFIRM_NOT_EXECUTED_RETRY, null,
                "provider proved not executed", ACTOR, "incident-1");
        ProcessRun resolved = store.resolveEffect(retry);
        ProcessRun replay = store.resolveEffect(retry);
        assertThat(activeEffect(resolved.runId()).status()).isEqualTo(EffectExecutionStatus.PENDING);
        assertThat(replay.updatedAt()).isEqualTo(resolved.updatedAt());
        assertThat(store.requestCancel(new DurableStore.CancelCommand(effectRun.runId())).status())
            .isEqualTo(ProcessRunStatus.CANCELLED);
        assertThatThrownBy(() -> store.resolveEffect(
                new DurableStore.EffectResolution(effectRun.runId(), effectId, review.reviewRevision(),
                        EffectResolutionDecision.FAIL_RUN, null, "stale failure after cancellation", ACTOR, "incident-1")))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.CONCURRENT_MODIFICATION));

        ProcessRun failedEffectRun = startRun();
        DurableStore.RunClaim failedTurn = claimRun();
        UUID failedEffectId = UUID.randomUUID();
        assertThat(store.commitTurn(failedTurn.lease(),
                effectTurn(failedEffectId, 1, "refund", envelope("effect-input"), envelope("effect-state"))))
            .isTrue();
        DurableStore.EffectClaim failedEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(store.requireEffectReview(failedEffect.lease(), "outcome unknown")).isTrue();
        long failedReviewRevision = activeEffect(failedEffectRun.runId()).reviewRevision();
        DurableStore.EffectResolution fail = new DurableStore.EffectResolution(failedEffectRun.runId(), failedEffectId,
                failedReviewRevision, EffectResolutionDecision.FAIL_RUN, null, "provider proved business failure", ACTOR,
                "incident-2");
        ProcessRun failed = store.resolveEffect(fail);
        ProcessRun failedReplay = store.resolveEffect(fail);
        assertThat(failed.status()).isEqualTo(ProcessRunStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("EFFECT_RESOLVED_FAILED");
        assertThat(failedReplay.updatedAt()).isEqualTo(failed.updatedAt());

        ProcessRun completedEffectRun = startRun();
        DurableStore.RunClaim completedTurn = claimRun();
        UUID completedEffectId = UUID.randomUUID();
        assertThat(store.commitTurn(completedTurn.lease(),
                effectTurn(completedEffectId, 1, "capture", envelope("effect-input"), envelope("effect-state"))))
            .isTrue();
        DurableStore.EffectClaim completedEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(store.requireEffectReview(completedEffect.lease(), "outcome unknown")).isTrue();
        long completedReviewRevision = activeEffect(completedEffectRun.runId()).reviewRevision();
        DurableStore.EffectResolution success = new DurableStore.EffectResolution(completedEffectRun.runId(),
                completedEffectId, completedReviewRevision, EffectResolutionDecision.CONFIRM_SUCCEEDED,
                envelope("charged"), "provider proved success", ACTOR, "incident-3");
        ProcessRun completed = store.resolveEffect(success);
        ProcessRun successReplay = store.resolveEffect(success);
        assertThat(completed.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(successReplay.updatedAt()).isEqualTo(completed.updatedAt());
        assertThatThrownBy(() -> store.resolveEffect(
                new DurableStore.EffectResolution(completedEffectRun.runId(), completedEffectId, completedReviewRevision,
                        EffectResolutionDecision.CONFIRM_SUCCEEDED, envelope("different-result"),
                        "conflicting operator result", ACTOR, "incident-3")))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.CONCURRENT_MODIFICATION));
        DurableStore.RunClaim completedEffectTurn = claimRun();
        assertThat(completedEffectTurn.lease().runId()).isEqualTo(completedEffectRun.runId());
        assertThat(store.commitTurn(completedEffectTurn.lease(),
                consumeClaimedResults(completedEffectTurn, succeededTurn(envelope("done")))))
            .isTrue();

        ProcessRun outboxRun = startRun();
        DurableStore.RunClaim run = claimRun();
        assertThat(run.lease().runId()).isEqualTo(outboxRun.runId());
        assertThat(store.commitTurn(run.lease(), succeededTurn(envelope("done")))).isTrue();
        OutboxEvent event = store
            .listOutbox(new DurableStore.OutboxQuery(null, null, Set.of(OutboxEventStatus.PENDING), null, null, 20))
            .items()
            .stream()
            .filter(item -> item.runId().equals(outboxRun.runId()))
            .findFirst()
            .orElseThrow();
        DurableStore.OutboxResolution abandon = new DurableStore.OutboxResolution(UUID.fromString(event.eventId()),
                event.revision(), DurableStore.OutboxResolutionDecision.ABANDON, ACTOR,
                "delivery intentionally suppressed", "incident-4");
        OutboxEvent abandoned = store.resolveOutbox(abandon);
        OutboxEvent abandonReplay = store.resolveOutbox(abandon);
        assertThat(abandoned.status()).isEqualTo(OutboxEventStatus.ABANDONED);
        assertThat(abandoned.revision()).isEqualTo(event.revision() + 1);
        assertThat(abandonReplay.revision()).isEqualTo(abandoned.revision());
    }

    @Test
    void committedRunDemandIsDiscoverableAfterRestart() {
        store.start(newRun(ProcessRunId.random(), PROCESS, envelope("initial-state")));

        assertThat(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 100)).processIds())
            .containsExactly(PROCESS.processId());

        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();
        assertThat(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 100)).processIds())
            .isEmpty();
    }

    @Test
    void programLoadDemandExcludesPassiveWaitsButIncludesExecutableEffects() {
        startRun();
        DurableStore.RunClaim timerRun = claimRun();
        assertThat(store.commitTurn(timerRun.lease(),
                timerTurn(UUID.randomUUID(), 1, "long-timer", Duration.ofDays(30), null, envelope("timer-state"))))
            .isTrue();
        assertThat(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 100)).processIds())
            .isEmpty();

        startRun();
        DurableStore.RunClaim effectRun = claimRun();
        assertThat(store.commitTurn(effectRun.lease(),
                effectTurn(UUID.randomUUID(), 1, "charge", envelope("input"), envelope("effect-state"))))
            .isTrue();
        assertThat(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 100)).processIds())
            .containsExactly(PROCESS.processId());
    }

    @Test
    void terminalRunRetentionWaitsForRequiredOutboxAndPurgesTheWholeRun() {
        ProcessRun terminal = startRun();
        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();
        ProcessRun active = startRun();

        assertThat(store.purgeTerminalRuns(Duration.ZERO, 10)).isZero();

        DurableStore.OutboxClaim outbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("retention-outbox", LEASE)).orElseThrow();
        assertThat(outbox.runId()).isEqualTo(terminal.runId());
        assertThat(store.completeOutbox(outbox.lease())).isTrue();

        assertThat(store.purgeTerminalRuns(Duration.ZERO, 10)).isOne();
        assertThat(store.findRun(terminal.runId())).isEmpty();
        assertThat(store.findOutbox(terminal.runId(), outbox.lease().eventId())).isEmpty();
        assertThat(store.findRun(active.runId())).isPresent();
        assertThat(store.findProcess(PROCESS.processId())).isPresent();
    }

    @Test
    void consumedOccurrenceRetentionIsBoundedAndPreservesTheActiveRun() {
        ProcessRun run = startRun();
        DurableStore.RunClaim initial = claimRun();
        UUID waitId = UUID.randomUUID();
        String token = DurableDigests.sha256("consumed-retention-wait");
        assertThat(store.commitTurn(initial.lease(),
                waitTurn(waitId, 1, "approval", "approved", token, envelope("waiting"), envelope("wait-outbox"))))
            .isTrue();
        store.completeWait(new DurableStore.WaitCompletion(run.runId(), token, envelope("approved")));
        DurableStore.RunClaim afterWait = claimRun();
        assertThat(store.commitTurn(afterWait.lease(),
                consumeClaimedResults(afterWait, runnableTurn(envelope("after-wait")))))
            .isTrue();

        assertThat(store.purgeConsumedOccurrences(Duration.ZERO, 1)).isOne();
        assertThat(store.purgeConsumedOccurrences(Duration.ZERO, 1)).isZero();

        DurableStore.RunClaim next = claimRun();
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(next.lease(),
                effectTurn(effectId, 2, "charge", envelope("effect-input"), envelope("effect-waiting"))))
            .isTrue();
        DurableStore.EffectClaim effect = claimEffect();
        assertThat(store.completeEffect(effect.lease(), envelope("effect-result"))).isTrue();
        DurableStore.RunClaim afterEffect = claimRun();
        assertThat(store.commitTurn(afterEffect.lease(),
                consumeClaimedResults(afterEffect, runnableTurn(envelope("after-effect")))))
            .isTrue();

        assertThat(store.purgeConsumedOccurrences(Duration.ZERO, 1)).isOne();
        assertThat(store.purgeConsumedOccurrences(Duration.ZERO, 1)).isZero();
        assertThat(store.findRun(run.runId())).isPresent();
    }

    @Test
    void unusedProcessRetentionIsBoundedAndRunReferencesPreventDeletion() {
        startRun();
        ProcessRef.Version unusedVersion = ProcessRef.version("contract", "unused", "v1");
        DurableStore.RunProcess unused = runProcess(unusedVersion, registerProcess(unusedVersion));

        assertThat(store.purgeUnusedProcesses(Duration.ZERO, 1)).isOne();
        assertThat(store.findProcess(unused.processId())).isEmpty();
        assertThat(store.findProcess(PROCESS.processId())).isPresent();
    }

    @Test
    void runReferencesProtectEveryProcessInTheRecoverySet() {
        ProcessRef.Version childVersion = ProcessRef.version("contract", "recovery-child", "v1");
        DurableStore.RunProcess child = runProcess(childVersion, registerProcess(childVersion));
        store.start(
                new DurableStore.NewRun(ProcessRunId.random(), PROCESS, Set.of(PROCESS.processId(), child.processId()),
                        envelope("initial-state"), null));

        assertThat(store.purgeUnusedProcesses(Duration.ZERO, 10)).isZero();
        assertThat(store.findProcess(PROCESS.processId())).isPresent();
        assertThat(store.findProcess(child.processId())).isPresent();
    }

    @Test
    void purgingATerminalRunReleasesItsRecoveryProcessReferences() {
        ProcessRef.Version childVersion = ProcessRef.version("contract", "retained-child", "v1");
        DurableStore.RunProcess child = runProcess(childVersion, registerProcess(childVersion));
        ProcessRun run = store.start(
                new DurableStore.NewRun(ProcessRunId.random(), PROCESS, Set.of(PROCESS.processId(), child.processId()),
                        envelope("initial-state"), null));
        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();

        DurableStore.OutboxClaim outbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("retention-outbox", LEASE)).orElseThrow();
        assertThat(outbox.runId()).isEqualTo(run.runId());
        assertThat(store.completeOutbox(outbox.lease())).isTrue();
        assertThat(store.purgeTerminalRuns(Duration.ZERO, 10)).isOne();

        assertThat(store.purgeUnusedProcesses(Duration.ZERO, 10)).isEqualTo(2);
        assertThat(store.findProcess(PROCESS.processId())).isEmpty();
        assertThat(store.findProcess(child.processId())).isEmpty();
    }

    @Test
    void aSharedRecoveryProcessRemainsUntilItsLastRunReferenceIsPurged() {
        ProcessRef.Version childVersion = ProcessRef.version("contract", "shared-child", "v1");
        DurableStore.RunProcess child = runProcess(childVersion, registerProcess(childVersion));
        ProcessRun terminal = store.start(
                new DurableStore.NewRun(ProcessRunId.random(), PROCESS, Set.of(PROCESS.processId(), child.processId()),
                        envelope("terminal-state"), null));
        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();
        store.start(
                new DurableStore.NewRun(ProcessRunId.random(), PROCESS, Set.of(PROCESS.processId(), child.processId()),
                        envelope("active-state"), null));

        DurableStore.OutboxClaim outbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("retention-outbox", LEASE)).orElseThrow();
        assertThat(outbox.runId()).isEqualTo(terminal.runId());
        assertThat(store.completeOutbox(outbox.lease())).isTrue();
        assertThat(store.purgeTerminalRuns(Duration.ZERO, 10)).isOne();

        assertThat(store.purgeUnusedProcesses(Duration.ZERO, 10)).isZero();
        assertThat(store.findProcess(child.processId())).isPresent();
    }

    @Test
    void startRejectsAMissingRecoveryProcessWithoutCreatingTheRun() {
        ProcessRunId runId = ProcessRunId.random();
        UUID missingProcessId = UUID.randomUUID();

        assertThatThrownBy(() -> store.start(
                new DurableStore.NewRun(runId, PROCESS, Set.of(PROCESS.processId(), missingProcessId),
                        envelope("initial-state"), null)))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.PROCESS_NOT_FOUND));
        assertThat(store.findRun(runId)).isEmpty();
        assertThat(store.findProcess(PROCESS.processId())).isPresent();
    }

    @Test
    void turnRejectsAnOccurrenceOutsideTheRunRecoverySetAndRollsBack() {
        ProcessRun run = startRun();
        DurableStore.RunClaim claim = claimRun();
        UUID unrelatedProcessId = UUID.randomUUID();
        UUID waitId = UUID.randomUUID();
        DurableStore.TurnCommit invalid = new DurableStore.TurnCommit(List.of(),
                List.of(
                        new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                        waitId), 1, unrelatedProcessId, ROOT_INVOCATION_ID, "root", "approval",
                                "approved", DurableDigests.sha256("unrelated-process"), null, envelope("wait-event"))),
                new DurableStore.WaitingTurn(envelope("waiting-state")));

        assertThatThrownBy(() -> store.commitTurn(claim.lease(), invalid))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.INVALID_ARGUMENT));
        assertThat(store.findRun(run.runId()).orElseThrow().status()).isEqualTo(ProcessRunStatus.RUNNING);
        assertThat(store.findRun(run.runId()).orElseThrow().activeWork().waits()).isZero();

        assertThat(store.commitTurn(claim.lease(), succeededTurn(envelope("done")))).isTrue();
    }

    @Test
    void concurrentStartAndUnusedProcessGcHaveOneReferentiallySafeOutcome() throws Exception {
        ProcessRef.Version candidateVersion = ProcessRef.version("contract", "gc-race", "v1");
        DurableStore.RunProcess candidate = runProcess(candidateVersion, registerProcess(candidateVersion));
        ProcessRef.Version childVersion = ProcessRef.version("contract", "gc-race-child", "v1");
        DurableStore.RunProcess child = runProcess(childVersion, registerProcess(childVersion));
        ProcessRunId runId = ProcessRunId.random();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = concurrentExecutor();
        try {
            Future<ProcessRun> admission = executor.submit(() -> {
                start.await();
                try {
                    return store.start(
                            new DurableStore.NewRun(runId, candidate, Set.of(candidate.processId(), child.processId()),
                                    envelope("initial-state"), null));
                } catch (DurableProcessException failure) {
                    if (failure.getErrorCode() == DurableErrorCode.PROCESS_NOT_FOUND) {
                        return null;
                    }
                    throw failure;
                }
            });
            Future<Integer> gc = executor.submit(() -> {
                start.await();
                return store.purgeUnusedProcesses(Duration.ZERO, 10);
            });
            start.countDown();

            ProcessRun admitted = admission.get(10, TimeUnit.SECONDS);
            gc.get(10, TimeUnit.SECONDS);
            if (admitted == null) {
                assertThat(store.findRun(runId)).isEmpty();
                store.purgeUnusedProcesses(Duration.ZERO, 10);
                assertThat(store.findProcess(candidate.processId())).isEmpty();
                assertThat(store.findProcess(child.processId())).isEmpty();
            } else {
                assertThat(store.findRun(admitted.runId())).isPresent();
                assertThat(store.findProcess(candidate.processId())).isPresent();
                assertThat(store.findProcess(child.processId())).isPresent();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void leasesCanBeRenewedAcrossLongApplicationWork() throws Exception {
        Duration shortLease = Duration.ofMillis(100);
        Duration renewedLease = Duration.ofSeconds(2);

        startRun();
        DurableStore.RunClaim run = store
            .claimRun(new DurableStore.RunClaimRequest("turn-worker", Set.of(PROCESS.processId()), shortLease))
            .orElseThrow();
        assertThat(store.renewRunLeases(Set.of(run.lease()), renewedLease)).containsExactly(run.lease());
        Thread.sleep(200);
        assertThat(store.commitTurn(run.lease(), succeededTurn(envelope("done")))).isTrue();

        startRun();
        DurableStore.RunClaim turn = claimRun();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(UUID.randomUUID(), 1, "charge", envelope("input"), envelope("state"))))
            .isTrue();
        DurableStore.EffectClaim effect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH,
                            shortLease))
            .orElseThrow();
        assertThat(store.renewEffectLeases(Set.of(effect.lease()), renewedLease)).containsExactly(effect.lease());
        Thread.sleep(200);
        assertThat(store.completeEffect(effect.lease(), envelope("result"))).isTrue();

        DurableStore.OutboxClaim outbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", shortLease)).orElseThrow();
        assertThat(store.renewOutboxLeases(Set.of(outbox.lease()), renewedLease)).containsExactly(outbox.lease());
        Thread.sleep(200);
        assertThat(store.completeOutbox(outbox.lease())).isTrue();
    }

    @Test
    void batchRenewalReturnsOnlyAuthoritiesThatRemainCurrent() {
        ProcessRun firstRun = startRun();
        DurableStore.RunClaim currentRun = claimRun();
        ProcessRun secondRun = startRun();
        DurableStore.RunClaim completedRun = claimRun();
        assertThat(currentRun.lease().runId()).isEqualTo(firstRun.runId());
        assertThat(completedRun.lease().runId()).isEqualTo(secondRun.runId());
        assertThat(store.commitTurn(completedRun.lease(), succeededTurn(envelope("done")))).isTrue();
        assertThat(store.renewRunLeases(Set.of(currentRun.lease(), completedRun.lease()), LEASE))
            .containsExactly(currentRun.lease());

        startRun();
        DurableStore.RunClaim firstEffectRun = claimRun();
        assertThat(store.commitTurn(firstEffectRun.lease(),
                effectTurn(UUID.randomUUID(), 1, "first-effect", envelope("input"), envelope("state"))))
            .isTrue();
        startRun();
        DurableStore.RunClaim secondEffectRun = claimRun();
        assertThat(store.commitTurn(secondEffectRun.lease(),
                effectTurn(UUID.randomUUID(), 1, "second-effect", envelope("input"), envelope("state"))))
            .isTrue();
        DurableStore.EffectClaim currentEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker-1", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        DurableStore.EffectClaim completedEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker-2", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(store.completeEffect(completedEffect.lease(), envelope("result"))).isTrue();
        assertThat(store.renewEffectLeases(Set.of(currentEffect.lease(), completedEffect.lease()), LEASE))
            .containsExactly(currentEffect.lease());

        startRun();
        DurableStore.RunClaim anotherCompletedRun = claimRun();
        assertThat(store.commitTurn(anotherCompletedRun.lease(),
                consumeClaimedResults(anotherCompletedRun, succeededTurn(envelope("done")))))
            .isTrue();
        DurableStore.OutboxClaim currentOutbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker-1", LEASE)).orElseThrow();
        DurableStore.OutboxClaim completedOutbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker-2", LEASE)).orElseThrow();
        assertThat(store.completeOutbox(completedOutbox.lease())).isTrue();
        assertThat(store.renewOutboxLeases(Set.of(currentOutbox.lease(), completedOutbox.lease()), LEASE))
            .containsExactly(currentOutbox.lease());
    }

    @Test
    void batchRenewalRequiresWholeMillisecondLeaseDuration() {
        startRun();
        DurableStore.RunClaim run = claimRun();

        assertThatThrownBy(() -> store.renewRunLeases(Set.of(run.lease()), Duration.ofNanos(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> store.renewRunLeases(Set.of(run.lease()), Duration.ofMillis(1).plusNanos(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
    }

    @Test
    void turnRetryEvidenceSurvivesClaimAndClearsAtDurableBoundary() {
        ProcessRun started = startRun();
        DurableStore.RunClaim first = claimRun();

        assertThat(store.releaseRunFault(first.lease(), Duration.ZERO, RunRetryCode.TURN_EXECUTION_FAULT)).isTrue();
        ProcessRun backedOff = store.findRun(started.runId()).orElseThrow();
        assertThat(backedOff.status()).isEqualTo(ProcessRunStatus.RUNNABLE);
        assertThat(backedOff.retry()).isNotNull();
        assertThat(backedOff.retry().code()).isEqualTo(RunRetryCode.TURN_EXECUTION_FAULT);
        assertThat(backedOff.retry().consecutiveTurnFaults()).isOne();
        assertThat(backedOff.availableAt()).isNotNull();

        DurableStore.RunClaim second = claimRun();
        assertThat(second.lease().runId()).isEqualTo(started.runId());
        assertThat(store.findRun(started.runId()).orElseThrow().retry()).isEqualTo(backedOff.retry());
        assertThat(store.commitTurn(second.lease(),
                timerTurn(UUID.randomUUID(), 1, "retry-cleared", Duration.ofHours(1), null, envelope("after-retry"))))
            .isTrue();
        ProcessRun waiting = store.findRun(started.runId()).orElseThrow();
        assertThat(waiting.retry()).isNull();
        assertThat(waiting.status()).isEqualTo(ProcessRunStatus.WAITING);
    }

    @Test
    void effectUncertaintyEpisodeSurvivesReconcileClaims() {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(UUID.randomUUID(), 1, "charge", envelope("input"), envelope("state"),
                        EffectRecoveryPlan.reconcile(2, 2, Duration.ofMillis(1), null))))
            .isTrue();
        DurableStore.EffectClaim dispatch = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
        assertThat(dispatch.unknownSince()).isNull();
        assertThat(store.markEffectUnknown(dispatch.lease(), Duration.ZERO, "dispatch uncertain")).isTrue();

        DurableStore.EffectClaim firstReconcile = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("reconcile-worker", DurableStore.EffectOperation.RECONCILE,
                            LEASE))
            .orElseThrow();
        assertThat(firstReconcile.unknownSince()).isNotNull();
        assertThat(store.releaseEffectBeforeInvocation(firstReconcile.lease(), Duration.ZERO,
                EffectReadinessCode.RECONCILE_ACTION_NOT_READY))
            .isTrue();
        assertThat(activeEffect(run.runId()).readinessCode()).isEqualTo(EffectReadinessCode.RECONCILE_ACTION_NOT_READY);
        DurableStore.EffectClaim secondReconcile = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("reconcile-worker", DurableStore.EffectOperation.RECONCILE,
                            LEASE))
            .orElseThrow();
        assertThat(secondReconcile.reconcileAttempts()).isEqualTo(firstReconcile.reconcileAttempts());
        assertThat(secondReconcile.unknownSince()).isEqualTo(firstReconcile.unknownSince());
        assertThat(store.requireEffectReview(secondReconcile.lease(), "EFFECT_DEADLINE_EXCEEDED")).isTrue();

        ActiveEffect review = activeEffect(run.runId());
        assertThat(review.reviewRequiredAt()).isNotNull();
        assertThat(review.reviewReason()).isEqualTo("EFFECT_DEADLINE_EXCEEDED");
        assertThat(review.nextAttemptAt()).isNull();
    }

    @Test
    void reconciliationAttemptsNeverExceedTheFrozenLimit() {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        EffectRecoveryPlan policy = EffectRecoveryPlan.reconcile(2, 1, Duration.ofMillis(1), null);
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(turn.lease(),
                new DurableStore.TurnCommit(List.of(),
                        List.of(
                                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                                effectId), 1, PROCESS_ID, ROOT_INVOCATION_ID, "charge", "charge", policy,
                                        envelope("input"))), new DurableStore.WaitingTurn(envelope("state")))))
            .isTrue();

        DurableStore.EffectClaim dispatch = claimEffect();
        assertThat(store.markEffectUnknown(dispatch.lease(), Duration.ZERO, "dispatch uncertain")).isTrue();
        DurableStore.EffectClaim reconcile = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("reconcile-worker", DurableStore.EffectOperation.RECONCILE,
                            LEASE))
            .orElseThrow();
        assertThat(reconcile.reconcileAttempts()).isOne();
        assertThat(store.markEffectUnknown(reconcile.lease(), Duration.ZERO, "still unknown")).isTrue();

        assertThat(store.claimEffect(
                new DurableStore.EffectClaimRequest("reconcile-worker", DurableStore.EffectOperation.RECONCILE, LEASE)))
            .isEmpty();
        ActiveEffect review = activeEffect(run.runId());
        assertThat(review.reconcileAttempts()).isOne();
        assertThat(review.reviewRequired()).isTrue();
        assertThat(review.reviewReason()).isEqualTo("EFFECT_RECONCILE_ATTEMPTS_EXHAUSTED");
    }

    @Test
    void expiredClaimsRecoverAfterWorkerCrashAndFenceEveryLateOwner() throws Exception {
        Duration crashLease = Duration.ofMillis(100);

        ProcessRun run = startRun();
        DurableStore.RunClaim abandonedRun = store
            .claimRun(new DurableStore.RunClaimRequest("crashed-turn", Set.of(PROCESS.processId()), crashLease))
            .orElseThrow();
        Thread.sleep(200);
        assertThat(store.reclaimExpiredRuns(10)).isOne();
        assertThat(store.commitTurn(abandonedRun.lease(), succeededTurn(envelope("late-turn-result")))).isFalse();
        DurableStore.RunClaim recoveredRun = claimRun();
        assertThat(recoveredRun.lease().runId()).isEqualTo(run.runId());
        assertThat(recoveredRun.lease().token()).isNotEqualTo(abandonedRun.lease().token());
        assertThat(store.commitTurn(recoveredRun.lease(), succeededTurn(envelope("recovered")))).isTrue();

        ProcessRun effectRun = startRun();
        DurableStore.RunClaim turn = claimRun();
        UUID effectId = UUID.randomUUID();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(effectId, 1, "charge", envelope("input"), envelope("state"),
                        EffectRecoveryPlan.reconcile(2, 1, Duration.ofMillis(1), null))))
            .isTrue();
        DurableStore.EffectClaim abandonedEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("crashed-effect", DurableStore.EffectOperation.DISPATCH,
                            crashLease))
            .orElseThrow();
        Thread.sleep(200);
        assertThat(store.reclaimExpiredEffects(10)).isOne();
        assertThat(store.completeEffect(abandonedEffect.lease(), envelope("late-effect-result"))).isFalse();
        DurableStore.EffectClaim recoveredEffect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("recovery-effect", DurableStore.EffectOperation.RECONCILE, LEASE))
            .orElseThrow();
        assertThat(recoveredEffect.runId()).isEqualTo(effectRun.runId());
        assertThat(recoveredEffect.lease().effectId()).isEqualTo(effectId);
        assertThat(recoveredEffect.lease().token()).isNotEqualTo(abandonedEffect.lease().token());
        assertThat(store.requireEffectReview(recoveredEffect.lease(), "crash left outcome unknown")).isTrue();

        DurableStore.OutboxClaim abandonedOutbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("crashed-outbox", crashLease)).orElseThrow();
        assertThat(abandonedOutbox.runId()).isEqualTo(run.runId());
        Thread.sleep(200);
        assertThat(store.reclaimExpiredOutbox(10)).isOne();
        assertThat(store.completeOutbox(abandonedOutbox.lease())).isFalse();
        Optional<DurableStore.OutboxClaim> recovered = Optional.empty();
        for (int index = 0; index < 10 && recovered.isEmpty(); index++) {
            DurableStore.OutboxClaim candidate =
                    store.claimOutbox(new DurableStore.OutboxClaimRequest("recovery-outbox", LEASE)).orElseThrow();
            if (candidate.lease().eventId().equals(abandonedOutbox.lease().eventId())) {
                recovered = Optional.of(candidate);
            } else {
                assertThat(store.completeOutbox(candidate.lease())).isTrue();
            }
        }
        DurableStore.OutboxClaim recoveredOutbox = recovered.orElseThrow();
        assertThat(recoveredOutbox.lease().eventId()).isEqualTo(abandonedOutbox.lease().eventId());
        assertThat(recoveredOutbox.lease().token()).isNotEqualTo(abandonedOutbox.lease().token());
        assertThat(recoveredOutbox.attempt()).isEqualTo(2);
        assertThat(store.completeOutbox(recoveredOutbox.lease())).isTrue();
    }

    @Test
    void waitCommittedOutboxCannotBeAbandoned() {
        startRun();
        DurableStore.RunClaim claim = claimRun();
        assertThat(store.commitTurn(claim.lease(),
                waitTurn(UUID.randomUUID(), 1, "approval", "approved", DurableDigests.sha256("non-abandonable-token"),
                        envelope("state"), envelope("wait-event"))))
            .isTrue();

        DurableStore.OutboxClaim event =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", LEASE)).orElseThrow();
        assertThat(event.eventType()).isEqualTo("WAIT_COMMITTED");
        assertThat(store.abandonOutbox(event.lease())).isFalse();
        assertThat(store.findOutbox(event.runId(), event.lease().eventId()).orElseThrow().status())
            .isEqualTo(OutboxEventStatus.DELIVERING);
    }

    @Test
    void waitAuthorityFulfillmentOrRevocationSettlesItsOutboxAndFencesLatePublishers() throws Exception {
        ProcessRun completedRun = startRun();
        DurableStore.RunClaim completedClaim = claimRun();
        String completedToken = DurableDigests.sha256("completion-settles-delivery");
        assertThat(store.commitTurn(completedClaim.lease(),
                waitTurn(UUID.randomUUID(), 1, "approval", "approved", completedToken, envelope("state"),
                        envelope("wait-event"))))
            .isTrue();
        DurableStore.OutboxClaim delivering =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("ambiguous-publisher", LEASE)).orElseThrow();

        store.completeWait(new DurableStore.WaitCompletion(completedRun.runId(), completedToken, envelope("approved")));

        assertThat(store.completeOutbox(delivering.lease())).isFalse();
        assertThat(store.findOutbox(completedRun.runId(), delivering.lease().eventId()).orElseThrow().status())
            .isEqualTo(OutboxEventStatus.DELIVERED);
        assertWaitCommittedAuthoritySecretDisposed(completedRun.runId());
        assertThat(store.requestCancel(new DurableStore.CancelCommand(completedRun.runId())).status())
            .isEqualTo(ProcessRunStatus.CANCELLED);
        DurableStore.OutboxClaim terminalEvent =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("terminal-publisher", LEASE)).orElseThrow();
        assertThat(terminalEvent.runId()).isEqualTo(completedRun.runId());
        assertThat(terminalEvent.eventType()).isEqualTo("RUN_CANCELLED");
        assertThat(store.completeOutbox(terminalEvent.lease())).isTrue();

        ProcessRun cancelledRun = startRun();
        DurableStore.RunClaim cancelledClaim = claimRun();
        assertThat(store.commitTurn(cancelledClaim.lease(),
                waitTurn(UUID.randomUUID(), 1, "approval", "approved", DurableDigests.sha256("cancel-settles-delivery"),
                        envelope("state"), envelope("wait-event"))))
            .isTrue();
        DurableStore.OutboxClaim revoked =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("revoked-publisher", LEASE)).orElseThrow();
        assertThat(revoked.runId()).isEqualTo(cancelledRun.runId());

        assertThat(store.requestCancel(new DurableStore.CancelCommand(cancelledRun.runId())).status())
            .isEqualTo(ProcessRunStatus.CANCELLED);
        assertThat(store.completeOutbox(revoked.lease())).isFalse();
        OutboxEvent abandoned = store.findOutbox(cancelledRun.runId(), revoked.lease().eventId()).orElseThrow();
        assertThat(abandoned.status()).isEqualTo(OutboxEventStatus.ABANDONED);
        assertWaitCommittedAuthoritySecretDisposed(cancelledRun.runId());
        assertThatThrownBy(() -> store.resolveOutbox(
                new DurableStore.OutboxResolution(revoked.lease().eventId(), abandoned.revision(),
                        DurableStore.OutboxResolutionDecision.RETRY, ACTOR, "obsolete authority must stay revoked",
                        "wait-revocation")))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void pauseAndEffectClaimRaceHasOneSafeOutcomeWithoutDeadlock() throws Exception {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(UUID.randomUUID(), 1, "charge", envelope("input"), envelope("state"))))
            .isTrue();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = concurrentExecutor();
        try {
            Future<ProcessRun> pause = executor.submit(() -> {
                startGate.await();
                return store.control(
                        new DurableStore.ControlCommand(run.runId(), 0, DurableStore.ControlOperation.PAUSE, ACTOR,
                                "pause-race", null));
            });
            Future<Optional<DurableStore.EffectClaim>> effect = executor.submit(() -> {
                startGate.await();
                return store.claimEffect(
                        new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH,
                                LEASE));
            });
            startGate.countDown();
            ProcessRun paused = pause.get(10, TimeUnit.SECONDS);
            Optional<DurableStore.EffectClaim> claimed = effect.get(10, TimeUnit.SECONDS);
            if (claimed.isPresent()) {
                assertThat(paused.control().state()).isEqualTo(ProcessRunControlState.PAUSE_REQUESTED);
                assertThat(store.completeEffect(claimed.orElseThrow().lease(), envelope("result"))).isTrue();
            } else {
                assertThat(paused.control().state()).isEqualTo(ProcessRunControlState.PAUSED);
            }
            assertThat(store.findRun(run.runId()).orElseThrow().control().state()).isEqualTo(
                    ProcessRunControlState.PAUSED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelAndDueTimerRaceCompletesWithoutDeadlock() throws Exception {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        assertThat(store.commitTurn(turn.lease(),
                timerTurn(UUID.randomUUID(), 1, "deadline", Duration.ZERO, null, envelope("state"))))
            .isTrue();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = concurrentExecutor();
        try {
            Future<Integer> timer = executor.submit(() -> {
                startGate.await();
                return store.resolveDueWaits(1);
            });
            Future<ProcessRun> cancel = executor.submit(() -> {
                startGate.await();
                return store.requestCancel(new DurableStore.CancelCommand(run.runId()));
            });
            startGate.countDown();
            assertThat(timer.get(10, TimeUnit.SECONDS)).isBetween(0, 1);
            assertThat(cancel.get(10, TimeUnit.SECONDS).status()).isEqualTo(ProcessRunStatus.CANCELLED);
            assertThat(store.findRun(run.runId()).orElseThrow().status()).isEqualTo(ProcessRunStatus.CANCELLED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void eventCompletionAndDeadlineRaceHaveOneCommittedWinner() throws Exception {
        ProcessRun run = startRun();
        DurableStore.RunClaim turn = claimRun();
        String token = DurableDigests.sha256("wait-deadline-race");
        assertThat(store.commitTurn(turn.lease(),
                waitDeadlineTurn(UUID.randomUUID(), 1, "approval", "approved", token, Duration.ZERO, envelope("state"),
                        envelope("wait-token"))))
            .isTrue();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = concurrentExecutor();
        try {
            Future<String> completion = executor.submit(() -> {
                startGate.await();
                try {
                    store.completeWait(new DurableStore.WaitCompletion(run.runId(), token, envelope("approved-result")));
                    return "COMPLETED";
                } catch (DurableProcessException failure) {
                    return failure.getErrorCode().name();
                }
            });
            Future<Integer> expiry = executor.submit(() -> {
                startGate.await();
                return store.resolveDueWaits(1);
            });
            startGate.countDown();
            String completionOutcome = completion.get(10, TimeUnit.SECONDS);
            int expired = expiry.get(10, TimeUnit.SECONDS);
            if (expired == 1) {
                assertThat(completionOutcome).isEqualTo(DurableErrorCode.INVALID_WAIT_TOKEN.name());
            } else {
                assertThat(expired).isZero();
                assertThat(completionOutcome).isEqualTo("COMPLETED");
            }

            DurableStore.WaitResult result = (DurableStore.WaitResult) claimRun().occurrenceResults().get(0);
            assertThat(result.resolution())
                .isEqualTo(expired == 1 ? DurableStore.WaitResolution.EXPIRED : DurableStore.WaitResolution.COMPLETED);
            assertWaitCommittedAuthoritySecretDisposed(run.runId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelLeaseRenewalAndOutboxCompletionShareRunFirstLockOrder() throws Exception {
        ProcessRun effectRun = startRun();
        DurableStore.RunClaim turn = claimRun();
        assertThat(store.commitTurn(turn.lease(),
                effectTurn(UUID.randomUUID(), 1, "charge", envelope("input"), envelope("state"))))
            .isTrue();
        DurableStore.EffectClaim effect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();

        CountDownLatch effectGate = new CountDownLatch(1);
        ExecutorService effectRace = concurrentExecutor();
        try {
            Future<ProcessRun> cancel = effectRace.submit(() -> {
                effectGate.await();
                return store.requestCancel(new DurableStore.CancelCommand(effectRun.runId()));
            });
            Future<Boolean> renew = effectRace.submit(() -> {
                effectGate.await();
                return store.renewEffectLeases(Set.of(effect.lease()), Duration.ofSeconds(2)).contains(effect.lease());
            });
            effectGate.countDown();
            assertThat(cancel.get(10, TimeUnit.SECONDS).cancelRequestedAt()).isNotNull();
            assertThat(renew.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            effectRace.shutdownNow();
        }

        ProcessRun outboxRun = startRun();
        DurableStore.RunClaim run = claimRun();
        assertThat(run.lease().runId()).isEqualTo(outboxRun.runId());
        assertThat(store.commitTurn(run.lease(), succeededTurn(envelope("done")))).isTrue();
        DurableStore.OutboxClaim outbox =
                store.claimOutbox(new DurableStore.OutboxClaimRequest("outbox-worker", LEASE)).orElseThrow();

        CountDownLatch outboxGate = new CountDownLatch(1);
        ExecutorService outboxRace = concurrentExecutor();
        try {
            Future<ProcessRun> cancel = outboxRace.submit(() -> {
                outboxGate.await();
                return store.requestCancel(new DurableStore.CancelCommand(outboxRun.runId()));
            });
            Future<Boolean> complete = outboxRace.submit(() -> {
                outboxGate.await();
                return store.completeOutbox(outbox.lease());
            });
            outboxGate.countDown();
            assertThat(cancel.get(10, TimeUnit.SECONDS).status()).isEqualTo(ProcessRunStatus.SUCCEEDED);
            assertThat(complete.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            outboxRace.shutdownNow();
        }
    }

    private DurableStore.StoredProcess registerProcess(ProcessRef.Version process) {
        byte[] definition = process.equals(PROCESS_VERSION)
                ? PROCESS_DEFINITION
                : bytes("process-definition:" + process.namespace() + ':' + process.code() + ':' + process.version());
        UUID processId = process.equals(PROCESS_VERSION)
                ? PROCESS_ID
                : UUID.nameUUIDFromBytes(bytes(process.namespace() + ':' + process.code() + ':' + process.version()));
        String digest = ProcessDefinitionDigest.compute(ProcessModelType.BPMN, process.code(), definition);
        return store.registerProcess(
                new DurableStore.ProcessRegistration(processId, process.code(), ProcessModelType.BPMN, definition,
                        digest));
    }

    private static DurableStore.RunProcess runProcess(ProcessRef.Version version, DurableStore.StoredProcess stored) {
        return new DurableStore.RunProcess(stored.processId(), version.namespace(), stored.processCode(), version);
    }

    private ProcessRun startRun() {
        return store.start(newRun(ProcessRunId.random(), PROCESS, envelope("initial-state")));
    }

    private static DurableStore.NewRun newRun(ProcessRunId runId, DurableStore.RunProcess process,
            DurableStore.Envelope continuation) {
        return newRun(runId, process, continuation, null);
    }

    private static DurableStore.NewRun newRun(ProcessRunId runId, DurableStore.RunProcess process,
            DurableStore.Envelope continuation, DurableStore.Envelope admissionFact) {
        return new DurableStore.NewRun(runId, process, Set.of(process.processId()), continuation, admissionFact);
    }

    private DurableStore.RunClaim claimRun() {
        return store.claimRun(claimRequest()).orElseThrow();
    }

    private DurableStore.RunClaimRequest claimRequest() {
        return new DurableStore.RunClaimRequest("turn-worker", Set.of(PROCESS.processId()), LEASE);
    }

    private DurableStore.EffectClaim claimEffect() {
        return store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH, LEASE))
            .orElseThrow();
    }

    private void issueTwoEffects(ProcessRunId expectedRunId) {
        DurableStore.RunClaim claim = claimRun();
        assertThat(claim.lease().runId()).isEqualTo(expectedRunId);
        List<DurableStore.OccurrenceCommit> effects = List.of(new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                UUID.randomUUID()), 1, PROCESS_ID, ROOT_INVOCATION_ID, "effect-a", "effect-a",
                        EffectRecoveryPlan.manual(), envelope("input-a")),
                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                UUID.randomUUID()), 2, PROCESS_ID, ROOT_INVOCATION_ID, "effect-b", "effect-b",
                        EffectRecoveryPlan.manual(), envelope("input-b")));
        assertThat(store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(List.of(), effects,
                        new DurableStore.WaitingTurn(envelope("effects-waiting")))))
            .isTrue();
    }

    private ActiveWait activeWait(ProcessRunId runId) {
        return store
            .listActiveWork(new DurableStore.ActiveWorkQuery(runId, 0, 200))
            .items()
            .stream()
            .filter(ActiveWait.class::isInstance)
            .map(ActiveWait.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private ActiveEffect activeEffect(ProcessRunId runId) {
        return store
            .listActiveWork(new DurableStore.ActiveWorkQuery(runId, 0, 200))
            .items()
            .stream()
            .filter(ActiveEffect.class::isInstance)
            .map(ActiveEffect.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private static ExecutorService concurrentExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "durable-store-contract-race");
            thread.setDaemon(true);
            return thread;
        });
    }
}
