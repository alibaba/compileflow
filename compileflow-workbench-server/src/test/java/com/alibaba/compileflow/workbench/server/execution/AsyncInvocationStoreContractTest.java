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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"compileflow.workbench.server.async-invocation.dispatch-interval=1h",
        "compileflow.workbench.server.async-invocation.lease-duration=2h",
        "compileflow.workbench.server.async-invocation.lease-recovery-interval=1h"})
@ActiveProfiles("test")
class AsyncInvocationStoreContractTest {
    private static final String QUEUED = AsyncInvocationService.STATUS_QUEUED;
    private static final String RUNNING = AsyncInvocationService.STATUS_RUNNING;
    private static final String SUCCEEDED = AsyncInvocationService.STATUS_SUCCEEDED;
    private static final String DEAD_LETTER = AsyncInvocationService.STATUS_DEAD_LETTER;
    private static final String ROUTING_V1_JSON = """
        {"effectiveVersion":"v1"}
        """.strip();
    private static final String OLD_OWNER_JSON = """
        {"owner":"old"}
        """.strip();
    private static final String NEW_OWNER_JSON = """
        {"owner":"new"}
        """.strip();
    @Autowired
    private AsyncInvocationRepository repository;
    @Autowired
    private AsyncInvocationAttemptRepository attemptRepository;
    @Autowired
    private AsyncInvocationStore store;

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstResult = workers.submit(gated(first, ready, start));
            Future<T> secondResult = workers.submit(gated(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static <T> Callable<T> gated(Callable<T> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent repository race did not start");
            }
            return action.call();
        };
    }

    private static AsyncInvocationEntity invocation(String invocationId, String status, int attempts, int maxAttempts,
            long availableAt, String leaseToken, Long leaseUntil) {
        AsyncInvocationEntity entity = new AsyncInvocationEntity();
        entity.setInvocationId(invocationId);
        entity.setProcessCode("state.machine");
        entity.setStatus(status);
        entity.setAttempts(attempts);
        entity.setTotalAttempts(attempts);
        entity.setRedriveCount(0);
        entity.setMaxAttempts(maxAttempts);
        entity.setRetryDelayMs(50L);
        entity.setAvailableAt(availableAt);
        entity.setParamsJson("{}");
        entity.setRoutingJson("{}");
        entity.setLeaseToken(leaseToken);
        entity.setLeaseUntil(leaseUntil);
        entity.setCreatedAt(1L);
        entity.setUpdatedAt(1L);
        if (attempts > 0) {
            entity.setStartedAt(1L);
        }
        if (SUCCEEDED.equals(status) || DEAD_LETTER.equals(status)) {
            entity.setCompletedAt(1L);
        }
        if (DEAD_LETTER.equals(status)) {
            entity.setErrorCode("FAILED");
            entity.setErrorMessage("failed");
        }
        return entity;
    }

    private static AsyncInvocationAttemptEntity terminalAttempt(String attemptId, String invocationId, long sequence,
            int redriveCount, int attemptNumber) {
        AsyncInvocationAttemptEntity attempt = new AsyncInvocationAttemptEntity();
        attempt.setAttemptId(attemptId);
        attempt.setInvocationId(invocationId);
        attempt.setSequence(sequence);
        attempt.setRedriveCount(redriveCount);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId("worker-existing");
        attempt.setLeaseToken("lease-" + attemptId);
        attempt.setOutcome(AsyncInvocationStore.OUTCOME_FAILED);
        attempt.setDisposition(AsyncInvocationStore.DISPOSITION_DEAD_LETTERED);
        attempt.setErrorCode("FAILED");
        attempt.setErrorMessage("failed");
        attempt.setDurationMs(1L);
        attempt.setStartedAt(1L);
        attempt.setFinishedAt(2L);
        return attempt;
    }

    @BeforeEach
    @AfterEach
    void clearInvocations() {
        attemptRepository.deleteAllInBatch();
        repository.deleteAllInBatch();
    }

    @Test
    void duplicateSubmissionCannotOverwriteAnAlreadyClaimedInvocation() {
        String invocationId = "state-insert-existing";
        store.insert(invocation(invocationId, QUEUED, 0, 3, 1L, null, null));
        AsyncInvocationStore.Claim claim = store.claim(invocationId, "worker-first", 60_000L).orElseThrow();
        AsyncInvocationEntity duplicate = invocation(invocationId, QUEUED, 0, 1, 1L, null, null);
        duplicate.setParamsJson("{\"different\":true}");

        assertThatThrownBy(() -> store.insert(duplicate)).isInstanceOf(DataIntegrityViolationException.class);

        AsyncInvocationEntity retained = repository.findById(invocationId).orElseThrow();
        assertThat(retained.getStatus()).isEqualTo(RUNNING);
        assertThat(retained.getLeaseToken()).isEqualTo(claim.leaseToken());
        assertThat(retained.getMaxAttempts()).isEqualTo(3);
        assertThat(retained.getParamsJson()).isEqualTo("{}");
        assertThat(retained.getTotalAttempts()).isOne();
        assertThat(store.complete(claim, "{}", "{}", "trace-first", 1L)).isOne();
    }

    @Test
    void persistsLargeUtf8PayloadsAcrossSubmissionAndCompletion() {
        String payload = new AsyncInvocationPayloadCodec().write(Map.of("value", "\u4e2d".repeat(30_000)));
        String invocationId = "state-large-payload";
        AsyncInvocationEntity request = invocation(invocationId, QUEUED, 0, 3, 1L, null, null);
        request.setParamsJson(payload);
        request.setRoutingJson(payload);
        store.insert(request);
        AsyncInvocationStore.Claim claim = store.claim(invocationId, "worker-large", 60_000L).orElseThrow();
        assertThat(claim.invocation().getParamsJson()).isEqualTo(payload);
        assertThat(claim.invocation().getRoutingJson()).isEqualTo(payload);
        assertThat(store.complete(claim, payload, payload, "trace-large", 1L)).isOne();

        AsyncInvocationEntity completed = repository.findById(invocationId).orElseThrow();
        assertThat(completed.getParamsJson()).isEqualTo(payload);
        assertThat(completed.getRoutingJson()).isEqualTo(payload);
        assertThat(completed.getResultJson()).isEqualTo(payload);
    }

    @Test
    void concurrentSubmissionsInsertExactlyOneRequest() throws Exception {
        String invocationId = "state-insert-race";
        Callable<Boolean> insert =
                () -> {
            try {
                store.insert(invocation(invocationId, QUEUED, 0, 3, 1L, null, null));
                return true;
            } catch (DataIntegrityViolationException duplicate) {
                return false;
            }
        };

        assertThat(race(insert, insert)).containsExactlyInAnyOrder(true, false);
        assertThat(repository.count()).isOne();
        assertThat(repository.findById(invocationId).orElseThrow().getStatus()).isEqualTo(QUEUED);
    }

    @Test
    void readyQueueUsesStableDurableAvailabilityOrder() {
        AsyncInvocationEntity sameAvailabilityLaterCreation =
                invocation("state-ready-b", QUEUED, 0, 3, 100L, null, null);
        sameAvailabilityLaterCreation.setCreatedAt(30L);
        sameAvailabilityLaterCreation.setUpdatedAt(30L);
        AsyncInvocationEntity sameAvailabilityEarlierCreation =
                invocation("state-ready-a", QUEUED, 0, 3, 100L, null, null);
        sameAvailabilityEarlierCreation.setCreatedAt(20L);
        sameAvailabilityEarlierCreation.setUpdatedAt(20L);
        AsyncInvocationEntity earliestAvailability = invocation("state-ready-c", QUEUED, 0, 3, 50L, null, null);
        earliestAvailability.setCreatedAt(40L);
        earliestAvailability.setUpdatedAt(40L);
        AsyncInvocationEntity delayed = invocation("state-delayed", QUEUED, 0, 3, 201L, null, null);
        repository.saveAllAndFlush(List.of(sameAvailabilityLaterCreation, sameAvailabilityEarlierCreation,
                earliestAvailability, delayed));

        assertThat(repository
            .findByStatusAndAvailableAtLessThanEqual(QUEUED, 200L,
                    PageRequest.of(0, 3,
                            Sort.by(Sort.Order.asc("availableAt"), Sort.Order.asc("createdAt"),
                                    Sort.Order.asc("invocationId"))))
            .map(AsyncInvocationEntity::getInvocationId)
            .getContent())
            .containsExactly("state-ready-c", "state-ready-a", "state-ready-b");
    }

    @Test
    void queuedInvocationCannotBeClaimedBeforeItsDurableAvailabilityTime() {
        repository.saveAndFlush(invocation("state-future", QUEUED, 0, 3, 200L, null, null));

        assertThat(repository.findByStatusAndAvailableAtLessThanEqual(QUEUED, 199L, PageRequest.of(0, 10))).isEmpty();
        assertThat(repository.claimQueued("state-future", QUEUED, RUNNING, 199L, "owner-1", 300L)).isZero();
        assertThat(repository.claimQueued("state-future", QUEUED, RUNNING, 200L, "owner-1", 300L)).isOne();
    }

    @Test
    void concurrentClaimsProduceExactlyOneOwner() throws Exception {
        repository.saveAndFlush(invocation("state-claim-race", QUEUED, 0, 3, 100L, null, null));

        List<Integer> outcomes = race(() -> repository.claimQueued("state-claim-race", QUEUED, RUNNING, 100L, "owner-a",
                300L), () -> repository.claimQueued("state-claim-race", QUEUED, RUNNING, 100L, "owner-b", 300L));

        assertThat(outcomes).containsExactlyInAnyOrder(0, 1);
        AsyncInvocationEntity claimed = repository.findById("state-claim-race").orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(RUNNING);
        assertThat(claimed.getAttempts()).isOne();
        assertThat(claimed.getLeaseToken()).isIn("owner-a", "owner-b");
    }

    @Test
    void expiredOwnerCannotRenewOrCommitEvenBeforeRecoveryRuns() {
        repository.saveAndFlush(invocation("state-expired-owner", RUNNING, 1, 3, 100L, "owner-expired", 100L));

        assertThat(store.renewLeases(Map.of("state-expired-owner", "owner-expired"), RUNNING, 200L)).isEmpty();
        assertThat(repository.completeOwned("state-expired-owner", RUNNING, SUCCEEDED, "owner-expired", "{}",
                "{\"success\":true}", "trace-expired", 1L, 101L))
            .isZero();
        assertThat(repository.findById("state-expired-owner").orElseThrow().getStatus()).isEqualTo(RUNNING);
    }

    @Test
    void batchRenewalUsesDatabaseTimeAndReturnsOnlyCurrentAuthorities() {
        long authorityTime = store.currentTimeMillis();
        repository.saveAllAndFlush(List.of(invocation("state-batch-a", RUNNING, 1, 3, authorityTime, "owner-a",
                        authorityTime + 60_000L),
                invocation("state-batch-b", RUNNING, 1, 3, authorityTime, "owner-b", authorityTime + 60_000L),
                invocation("state-batch-expired", RUNNING, 1, 3, authorityTime, "owner-expired", authorityTime - 1L),
                invocation("state-batch-wrong-token", RUNNING, 1, 3, authorityTime, "owner-current",
                        authorityTime + 60_000L)));

        Set<String> renewed = store.renewLeases(Map.of("state-batch-a", "owner-a", "state-batch-b", "owner-b",
                        "state-batch-expired", "owner-expired", "state-batch-wrong-token", "owner-stale"), RUNNING,
                120_000L);

        assertThat(renewed).containsExactlyInAnyOrder("state-batch-a", "state-batch-b");
        assertThat(repository.findById("state-batch-a").orElseThrow().getLeaseUntil()).isGreaterThan(
                authorityTime + 60_000L);
        assertThat(repository.findById("state-batch-b").orElseThrow().getLeaseUntil()).isGreaterThan(
                authorityTime + 60_000L);
        assertThat(repository.findById("state-batch-expired").orElseThrow().getLeaseUntil()).isEqualTo(
                authorityTime - 1L);
        assertThat(repository.findById("state-batch-wrong-token").orElseThrow().getLeaseUntil())
            .isEqualTo(authorityTime + 60_000L);
    }

    @Test
    void onlyTheCurrentUnexpiredOwnerCanPinInvocationRouting() {
        repository.saveAndFlush(invocation("state-pin-route", RUNNING, 1, 3, 100L, "owner-current", 200L));

        assertThat(repository.pinOwnedRouting("state-pin-route", RUNNING, "owner-stale", ROUTING_V1_JSON, 100L)).isZero();
        assertThat(repository.pinOwnedRouting("state-pin-route", RUNNING, "owner-current",
                "{\"effectiveVersion\":\"v2\"}", 201L))
            .isZero();
        assertThat(repository.pinOwnedRouting("state-pin-route", RUNNING, "owner-current",
                "{\"effectiveVersion\":\"v2\"}", 100L))
            .isOne();
        assertThat(repository.findById("state-pin-route").orElseThrow().getRoutingJson())
            .isEqualTo("{\"effectiveVersion\":\"v2\"}");
    }

    @Test
    void recoveredAndReclaimedInvocationRejectsLateResultFromPreviousOwner() {
        repository.saveAndFlush(invocation("state-fenced", RUNNING, 1, 3, 100L, "owner-old", 100L));

        assertThat(repository.recoverExpiredForRetry("state-fenced", RUNNING, QUEUED, "owner-old", "LEASE_EXPIRED",
                "lease expired", 101L, 101L))
            .isOne();
        assertThat(repository.claimQueued("state-fenced", QUEUED, RUNNING, 101L, "owner-new", 300L)).isOne();
        assertThat(repository.completeOwned("state-fenced", RUNNING, SUCCEEDED, "owner-old", "{}", OLD_OWNER_JSON,
                "trace-old", 2L, 102L))
            .isZero();
        assertThat(repository.completeOwned("state-fenced", RUNNING, SUCCEEDED, "owner-new", "{}", NEW_OWNER_JSON,
                "trace-new", 3L, 102L))
            .isOne();

        AsyncInvocationEntity completed = repository.findById("state-fenced").orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(completed.getTraceId()).isEqualTo("trace-new");
        assertThat(completed.getResultJson()).contains("new");
    }

    @Test
    void expiredInvocationCanBeRecoveredOnlyOnce() {
        repository.saveAndFlush(invocation("state-recover-once", RUNNING, 1, 3, 100L, "owner-old", 100L));

        assertThat(repository.recoverExpiredForRetry("state-recover-once", RUNNING, QUEUED, "owner-old", "LEASE_EXPIRED",
                "lease expired", 150L, 101L))
            .isOne();
        assertThat(repository.recoverExpiredForRetry("state-recover-once", RUNNING, QUEUED, "owner-old", "LEASE_EXPIRED",
                "lease expired", 150L, 101L))
            .isZero();

        AsyncInvocationEntity recovered = repository.findById("state-recover-once").orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(QUEUED);
        assertThat(recovered.getAvailableAt()).isEqualTo(150L);
        assertThat(recovered.getLeaseToken()).isNull();
    }

    @Test
    void concurrentExpiredRecoveryProducesExactlyOneWinner() throws Exception {
        repository.saveAndFlush(invocation("state-recovery-race", RUNNING, 1, 3, 100L, "owner-old", 100L));

        List<Integer> outcomes = race(() -> repository.recoverExpiredForRetry("state-recovery-race", RUNNING, QUEUED,
                "owner-old", "LEASE_EXPIRED", "lease expired", 150L, 101L), () -> repository.recoverExpiredForRetry("s"
                + "tate-recovery-race", RUNNING, QUEUED, "owner-old", "LEASE_EXPIRED", "lease expired", 150L, 101L));

        assertThat(outcomes).containsExactlyInAnyOrder(0, 1);
        AsyncInvocationEntity recovered = repository.findById("state-recovery-race").orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(QUEUED);
        assertThat(recovered.getAttempts()).isOne();
        assertThat(recovered.getAvailableAt()).isEqualTo(150L);
        assertThat(recovered.getLeaseToken()).isNull();
    }

    @Test
    void deadLetterCanBeRequeuedOnlyOnce() {
        repository.saveAndFlush(invocation("state-requeue-once", DEAD_LETTER, 3, 3, 100L, null, null));

        assertThat(repository.requeueDeadLetter("state-requeue-once", DEAD_LETTER, QUEUED, 200L)).isOne();
        assertThat(repository.requeueDeadLetter("state-requeue-once", DEAD_LETTER, QUEUED, 200L)).isZero();

        AsyncInvocationEntity requeued = repository.findById("state-requeue-once").orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(QUEUED);
        assertThat(requeued.getAttempts()).isZero();
        assertThat(requeued.getRedriveCount()).isOne();
        assertThat(requeued.getAvailableAt()).isEqualTo(200L);
        assertThat(requeued.getCompletedAt()).isNull();
    }

    @Test
    void concurrentDeadLetterRequeueProducesExactlyOneWinner() throws Exception {
        String invocationId = "state-requeue-race";
        repository.saveAndFlush(invocation(invocationId, DEAD_LETTER, 3, 3, 100L, null, null));

        Callable<Integer> requeue = () -> repository.requeueDeadLetter(invocationId, DEAD_LETTER, QUEUED, 200L);
        List<Integer> outcomes = race(requeue, requeue);

        assertThat(outcomes).containsExactlyInAnyOrder(0, 1);
        AsyncInvocationEntity requeued = repository.findById(invocationId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(QUEUED);
        assertThat(requeued.getAttempts()).isZero();
        assertThat(requeued.getRedriveCount()).isOne();
        assertThat(requeued.getAvailableAt()).isEqualTo(200L);
        assertThat(requeued.getCompletedAt()).isNull();
    }

    @Test
    void attemptLedgerRemainsMonotonicAcrossDeadLetterRedrive() {
        repository.saveAndFlush(invocation("state-attempt-history", QUEUED, 0, 1, 1L, null, null));

        AsyncInvocationStore.Claim first = store.claim("state-attempt-history", "worker-a", 10_000L).orElseThrow();
        assertThat(store.fail(first, "{}", null, "FLOW_FAILED", "first attempt failed", "trace-failed", 5L, false)).isOne();
        assertThat(store.requeueDeadLetter("state-attempt-history")).isOne();
        AsyncInvocationStore.Claim second = store.claim("state-attempt-history", "worker-b", 10_000L).orElseThrow();
        assertThat(store.complete(second, "{}", "{}", "trace-success", 7L)).isOne();

        AsyncInvocationEntity invocation = repository.findById("state-attempt-history").orElseThrow();
        assertThat(invocation.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(invocation.getAttempts()).isOne();
        assertThat(invocation.getTotalAttempts()).isEqualTo(2L);
        assertThat(invocation.getRedriveCount()).isOne();
        List<AsyncInvocationAttemptEntity> attempts = attemptRepository.findByInvocationIdAndSequenceGreaterThanOrderBySequenceAsc("s"
                + "tate-attempt-history", 0L, PageRequest.of(0, 10));
        assertThat(attempts).extracting(AsyncInvocationAttemptEntity::getSequence).containsExactly(1L, 2L);
        assertThat(attempts).extracting(AsyncInvocationAttemptEntity::getRedriveCount).containsExactly(0, 1);
        assertThat(attempts).extracting(AsyncInvocationAttemptEntity::getAttemptNumber).containsExactly(1, 1);
        assertThat(attempts)
            .extracting(AsyncInvocationAttemptEntity::getOutcome)
            .containsExactly(AsyncInvocationStore.OUTCOME_FAILED, AsyncInvocationStore.OUTCOME_SUCCEEDED);
        assertThat(attempts)
            .extracting(AsyncInvocationAttemptEntity::getDisposition)
            .containsExactly(AsyncInvocationStore.DISPOSITION_DEAD_LETTERED, AsyncInvocationStore.DISPOSITION_SUCCEEDED);
    }

    @Test
    void attemptInsertFailureRollsBackInvocationClaim() {
        AsyncInvocationEntity invocation = invocation("state-claim-ledger-rollback", QUEUED, 0, 3, 1L, null, null);
        repository.saveAndFlush(invocation);
        attemptRepository.saveAndFlush(terminalAttempt("existing-attempt", invocation.getInvocationId(), 1L, 0, 1));

        assertThatThrownBy(() -> store.claim(invocation.getInvocationId(), "worker-a", 10_000L))
            .isInstanceOf(RuntimeException.class);

        AsyncInvocationEntity unchanged = repository.findById(invocation.getInvocationId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(QUEUED);
        assertThat(unchanged.getAttempts()).isZero();
        assertThat(unchanged.getTotalAttempts()).isZero();
    }

    @Test
    void missingAttemptRollsBackInvocationCompletion() {
        long now = store.currentTimeMillis();
        AsyncInvocationEntity invocation =
                invocation("state-complete-ledger-rollback", RUNNING, 1, 3, now, "lease-missing-attempt", now + 10_000L);
        invocation.setStartedAt(now);
        repository.saveAndFlush(invocation);
        AsyncInvocationStore.Claim claim =
                new AsyncInvocationStore.Claim(invocation, "missing-attempt", "lease-missing-attempt");

        assertThatThrownBy(() -> store.complete(claim, "{}", "{}", "trace-1", 1L))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no matching running attempt");

        AsyncInvocationEntity unchanged = repository.findById(invocation.getInvocationId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RUNNING);
        assertThat(unchanged.getLeaseToken()).isEqualTo("lease-missing-attempt");
        assertThat(unchanged.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void missingAttemptRollsBackFailureAndRetryDecision(int attempts) {
        long now = store.currentTimeMillis();
        AsyncInvocationEntity invocation =
                invocation("state-fail-ledger-rollback", RUNNING, attempts, 3, now, "lease-missing", now + 60_000L);
        invocation.setStartedAt(now);
        repository.saveAndFlush(invocation);
        AsyncInvocationStore.Claim claim =
                new AsyncInvocationStore.Claim(invocation, "missing-attempt", "lease-missing");

        assertThatThrownBy(() -> store.fail(claim, "{}", null, "FAILED", "failure", null, 1L, false))
            .hasMessageContaining("no matching running attempt");

        AsyncInvocationEntity unchanged = repository.findById(invocation.getInvocationId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RUNNING);
        assertThat(unchanged.getLeaseToken()).isEqualTo("lease-missing");
        assertThat(unchanged.getAvailableAt()).isEqualTo(now);
        assertThat(unchanged.getErrorCode()).isNull();
        assertThat(unchanged.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void missingAttemptRollsBackExpiredRecovery(int attempts) {
        long now = store.currentTimeMillis();
        AsyncInvocationEntity invocation =
                invocation("state-recover-ledger-rollback", RUNNING, attempts, 3, now, "lease-missing", now - 1L);
        repository.saveAndFlush(invocation);

        assertThatThrownBy(() -> store.recoverExpired(invocation, "LEASE_EXPIRED", "lease expired"))
            .hasMessageContaining("no matching running attempt");

        AsyncInvocationEntity unchanged = repository.findById(invocation.getInvocationId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RUNNING);
        assertThat(unchanged.getLeaseToken()).isEqualTo("lease-missing");
        assertThat(unchanged.getLeaseUntil()).isEqualTo(now - 1L);
        assertThat(unchanged.getErrorCode()).isNull();
        assertThat(unchanged.getCompletedAt()).isNull();
    }
}
