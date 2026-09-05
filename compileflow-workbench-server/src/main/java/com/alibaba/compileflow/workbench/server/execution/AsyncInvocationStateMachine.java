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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional state machine for an invocation aggregate and its physical attempt ledger.
 *
 * @author yusu
 */
@Component
class AsyncInvocationStateMachine {
    static final String OUTCOME_RUNNING = "running";
    static final String OUTCOME_SUCCEEDED = "succeeded";
    static final String OUTCOME_FAILED = "failed";
    static final String OUTCOME_LEASE_EXPIRED = "lease_expired";
    static final String DISPOSITION_SUCCEEDED = "succeeded";
    static final String DISPOSITION_RETRY_SCHEDULED = "retry_scheduled";
    static final String DISPOSITION_DEAD_LETTERED = "dead_lettered";
    private final AsyncInvocationRepository invocationRepository;
    private final AsyncInvocationAttemptRepository attemptRepository;

    AsyncInvocationStateMachine(AsyncInvocationRepository invocationRepository,
            AsyncInvocationAttemptRepository attemptRepository) {
        this.invocationRepository = Objects.requireNonNull(invocationRepository, "invocationRepository");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository");
    }

    private static void requireAttemptTransition(int transitioned, Claim claim) {
        if (transitioned != 1) {
            throw new IllegalStateException(
                    "Async invocation transition has no matching running attempt: " + claim
                        .invocation()
                        .getInvocationId());
        }
    }

    private static long elapsed(long startedAt, long finishedAt) {
        return finishedAt <= startedAt ? 0L : finishedAt - startedAt;
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    @Transactional
    Optional<Claim> claim(String invocationId, String workerId, long leaseDurationMs) {
        long now = invocationRepository.currentTimeMillis();
        String attemptId = "att-" + UUID.randomUUID();
        String leaseToken = "lease-" + UUID.randomUUID();
        int claimed = invocationRepository.claimQueued(invocationId, AsyncInvocationService.STATUS_QUEUED,
                AsyncInvocationService.STATUS_RUNNING, now, leaseToken, saturatingAdd(now, leaseDurationMs));
        if (claimed == 0) {
            return Optional.empty();
        }

        AsyncInvocationEntity invocation = invocationRepository
            .findById(invocationId)
            .orElseThrow(() -> new IllegalStateException("Claimed async invocation disappeared: " + invocationId));
        AsyncInvocationAttemptEntity attempt = new AsyncInvocationAttemptEntity();
        attempt.setAttemptId(attemptId);
        attempt.setInvocationId(invocationId);
        attempt.setSequence(invocation.getTotalAttempts());
        attempt.setRedriveCount(invocation.getRedriveCount());
        attempt.setAttemptNumber(invocation.getAttempts());
        attempt.setWorkerId(workerId);
        attempt.setLeaseToken(leaseToken);
        attempt.setOutcome(OUTCOME_RUNNING);
        attempt.setStartedAt(now);
        attemptRepository.saveAndFlush(attempt);
        return Optional.of(new Claim(invocation, attemptId, leaseToken));
    }

    @Transactional
    int complete(Claim claim, String routingJson, String resultJson, String traceId, long durationMs) {
        long now = invocationRepository.currentTimeMillis();
        int transitioned = invocationRepository.completeOwned(claim.invocation().getInvocationId(),
                AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_SUCCEEDED, claim.leaseToken(),
                routingJson, resultJson, traceId, durationMs, now);
        if (transitioned == 0) {
            return 0;
        }
        requireAttemptTransition(attemptRepository.finishOwned(claim.attemptId(), OUTCOME_RUNNING, OUTCOME_SUCCEEDED,
                        DISPOSITION_SUCCEEDED, traceId, null, null, durationMs, now, null), claim);
        return transitioned;
    }

    @Transactional
    int fail(Claim claim, String routingJson, String resultJson, String errorCode, String errorMessage, String traceId,
            Long durationMs, boolean permanent) {
        AsyncInvocationEntity invocation = claim.invocation();
        long now = invocationRepository.currentTimeMillis();
        boolean deadLetter = permanent || invocation.getAttempts() >= invocation.getMaxAttempts();
        Long nextAttemptAt = deadLetter ? null : saturatingAdd(now, invocation.getRetryDelayMs());
        int transitioned;
        if (deadLetter) {
            transitioned = invocationRepository.deadLetterOwned(invocation.getInvocationId(),
                    AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_DEAD_LETTER, claim.leaseToken(),
                    routingJson, resultJson, errorCode, errorMessage, traceId, durationMs, now);
        } else {
            transitioned = invocationRepository.retryOwned(invocation.getInvocationId(),
                    AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_QUEUED, claim.leaseToken(),
                    routingJson, resultJson, errorCode, errorMessage, traceId, durationMs, nextAttemptAt.longValue(),
                    now);
        }
        if (transitioned == 0) {
            return 0;
        }
        requireAttemptTransition(attemptRepository.finishOwned(claim.attemptId(), OUTCOME_RUNNING, OUTCOME_FAILED,
                        deadLetter ? DISPOSITION_DEAD_LETTERED : DISPOSITION_RETRY_SCHEDULED, traceId, errorCode,
                        errorMessage, durationMs, now, nextAttemptAt), claim);
        return transitioned;
    }

    @Transactional
    int recoverExpired(AsyncInvocationEntity candidate, String errorCode, String errorMessage) {
        String leaseToken = candidate.getLeaseToken();
        if (leaseToken == null || candidate.getStartedAt() == null) {
            return 0;
        }
        long now = invocationRepository.currentTimeMillis();
        long nextAttemptAt = saturatingAdd(now, candidate.getRetryDelayMs());
        int transitioned = invocationRepository.recoverExpiredForRetry(candidate.getInvocationId(),
                AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_QUEUED, leaseToken, errorCode,
                errorMessage, nextAttemptAt, now);
        String disposition = DISPOSITION_RETRY_SCHEDULED;
        Long persistedNextAttemptAt = nextAttemptAt;
        if (transitioned == 0) {
            transitioned = invocationRepository.recoverExpiredToDeadLetter(candidate.getInvocationId(),
                    AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_DEAD_LETTER, leaseToken,
                    errorCode, errorMessage, now);
            disposition = DISPOSITION_DEAD_LETTERED;
            persistedNextAttemptAt = null;
        }
        if (transitioned == 0) {
            return 0;
        }
        int attemptTransitioned = attemptRepository.finishExpired(candidate.getInvocationId(), leaseToken,
                OUTCOME_RUNNING, OUTCOME_LEASE_EXPIRED, disposition, errorCode, errorMessage,
                elapsed(candidate.getStartedAt(), now), now, persistedNextAttemptAt);
        if (attemptTransitioned != 1) {
            throw new IllegalStateException(
                    "Async invocation recovery has no matching running attempt: " + candidate.getInvocationId());
        }
        return transitioned;
    }

    @Transactional
    int requeueDeadLetter(String invocationId, long now) {
        return invocationRepository.requeueDeadLetter(invocationId, AsyncInvocationService.STATUS_DEAD_LETTER,
                AsyncInvocationService.STATUS_QUEUED, now);
    }

    @Transactional(readOnly = true)
    Optional<AttemptPage> listAttempts(String invocationId, long afterSequence, int limit) {
        if (!invocationRepository.existsById(invocationId)) {
            return Optional.empty();
        }
        List<AsyncInvocationAttemptEntity> rows = attemptRepository.findByInvocationIdAndSequenceGreaterThanOrderBySequenceAsc(invocationId,
                afterSequence, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<AsyncInvocationAttemptEntity> data = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        Long nextAfterSequence = hasMore && !data.isEmpty() ? data.get(data.size() - 1).getSequence() : null;
        return Optional.of(new AttemptPage(data, hasMore, nextAfterSequence));
    }

    record Claim(AsyncInvocationEntity invocation, String attemptId, String leaseToken) {
        Claim {
            Objects.requireNonNull(invocation, "invocation");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(leaseToken, "leaseToken");
        }
    }

    record AttemptPage(List<AsyncInvocationAttemptEntity> data, boolean hasMore, Long nextAfterSequence) {}
}
