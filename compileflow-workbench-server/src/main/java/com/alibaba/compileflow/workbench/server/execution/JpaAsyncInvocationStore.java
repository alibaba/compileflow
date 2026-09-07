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

import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database authority for asynchronous invocation state transitions and attempt history.
 */
@Repository
class JpaAsyncInvocationStore implements AsyncInvocationStore {
    private final String authorityTimeMillis;
    private static final long MAX_EPOCH_MILLIS = Long.MAX_VALUE;
    private final AsyncInvocationRepository invocationRepository;
    private final AsyncInvocationAttemptRepository attemptRepository;
    private final EntityManager entityManager;

    JpaAsyncInvocationStore(AsyncInvocationRepository invocationRepository,
            AsyncInvocationAttemptRepository attemptRepository, EntityManager entityManager,
            CompileFlowWorkbenchServerProperties properties) {
        this.invocationRepository = Objects.requireNonNull(invocationRepository, "invocationRepository");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.authorityTimeMillis = switch (properties.getDatabase().getProvider()) {
            case POSTGRESQL -> "CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT)";
            case MYSQL -> "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public long currentTimeMillis() {
        return ((Number) entityManager.createNativeQuery("SELECT " + authorityTimeMillis).getSingleResult())
            .longValue();
    }

    @Override
    @Transactional
    public void insert(AsyncInvocationEntity invocation) {
        // Assigned invocation IDs must never turn a concurrent submission into a merge.
        entityManager.persist(invocation);
        entityManager.flush();
    }

    @Override
    @Transactional
    public Optional<Claim> claim(String invocationId, String workerId, long leaseDurationMs) {
        long now = currentTimeMillis();
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
        AsyncInvocationAttemptEntity attempt = newAttempt(invocation, attemptId, leaseToken, workerId, now);
        attemptRepository.saveAndFlush(attempt);
        return Optional.of(new Claim(invocation, attemptId, leaseToken));
    }

    @Override
    @Transactional
    public Set<String> renewLeases(Map<String, String> authorities, String runningStatus, long extensionMillis) {
        Map<String, String> snapshot = Map.copyOf(Objects.requireNonNull(authorities, "authorities"));
        if (snapshot.isEmpty()) {
            return Set.of();
        }
        if (extensionMillis <= 0L) {
            throw new IllegalArgumentException("extensionMillis must be positive");
        }
        String status = Objects.requireNonNull(runningStatus, "runningStatus");
        List<Map.Entry<String, String>> ordered =
                snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList();
        String predicate = authorityPredicate(ordered.size());
        String renewedUntil = "CASE WHEN " + authorityTimeMillis + " > " + MAX_EPOCH_MILLIS
                + " - :extensionMillis THEN " + MAX_EPOCH_MILLIS + " ELSE " + authorityTimeMillis
                + " + :extensionMillis END";
        Query update = entityManager.createNativeQuery(
                "UPDATE cf_async_invocation SET lease_until = " + renewedUntil + ", updated_at = " + authorityTimeMillis
                + " WHERE status = :runningStatus AND lease_until >= " + authorityTimeMillis + " AND (" + predicate
                + ")");
        update.setParameter("extensionMillis", extensionMillis);
        update.setParameter("runningStatus", status);
        bindAuthorities(update, ordered);
        update.executeUpdate();

        Query select = entityManager.createNativeQuery(
                "SELECT invocation_id FROM cf_async_invocation WHERE status = :runningStatus AND lease_until >= "
                + authorityTimeMillis + " AND (" + predicate + ")");
        select.setParameter("runningStatus", status);
        bindAuthorities(select, ordered);
        LinkedHashSet<String> renewed = new LinkedHashSet<>();
        for (Object value : select.getResultList()) {
            renewed.add((String) value);
        }
        return Set.copyOf(renewed);
    }

    @Override
    @Transactional
    public int pinRouting(String invocationId, String runningStatus, String leaseToken, String routingJson) {
        long now = currentTimeMillis();
        return invocationRepository.pinOwnedRouting(invocationId, runningStatus, leaseToken, routingJson, now);
    }

    @Override
    @Transactional
    public int complete(Claim claim, String routingJson, String resultJson, String traceId, long durationMs) {
        long now = currentTimeMillis();
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

    @Override
    @Transactional
    public int fail(Claim claim, String routingJson, String resultJson, String errorCode, String errorMessage,
            String traceId, Long durationMs, boolean permanent) {
        AsyncInvocationEntity invocation = claim.invocation();
        long now = currentTimeMillis();
        boolean deadLetter = permanent || invocation.getAttempts() >= invocation.getMaxAttempts();
        Long nextAttemptAt = deadLetter ? null : saturatingAdd(now, invocation.getRetryDelayMs());
        int transitioned = deadLetter
                ? invocationRepository.deadLetterOwned(invocation.getInvocationId(),
                        AsyncInvocationService.STATUS_RUNNING, AsyncInvocationService.STATUS_DEAD_LETTER,
                        claim.leaseToken(), routingJson, resultJson, errorCode, errorMessage, traceId, durationMs, now)
                : invocationRepository.retryOwned(invocation.getInvocationId(), AsyncInvocationService.STATUS_RUNNING,
                        AsyncInvocationService.STATUS_QUEUED, claim.leaseToken(), routingJson, resultJson, errorCode,
                        errorMessage, traceId, durationMs, nextAttemptAt.longValue(), now);
        if (transitioned == 0) {
            return 0;
        }
        requireAttemptTransition(attemptRepository.finishOwned(claim.attemptId(), OUTCOME_RUNNING, OUTCOME_FAILED,
                        deadLetter ? DISPOSITION_DEAD_LETTERED : DISPOSITION_RETRY_SCHEDULED, traceId, errorCode,
                        errorMessage, durationMs, now, nextAttemptAt), claim);
        return transitioned;
    }

    @Override
    @Transactional
    public int recoverExpired(AsyncInvocationEntity candidate, String errorCode, String errorMessage) {
        String leaseToken = candidate.getLeaseToken();
        if (leaseToken == null || candidate.getStartedAt() == null) {
            return 0;
        }
        long now = currentTimeMillis();
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

    @Override
    @Transactional
    public int requeueDeadLetter(String invocationId) {
        return invocationRepository.requeueDeadLetter(invocationId, AsyncInvocationService.STATUS_DEAD_LETTER,
                AsyncInvocationService.STATUS_QUEUED, currentTimeMillis());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptPage> listAttempts(String invocationId, long afterSequence, int limit) {
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

    private static AsyncInvocationAttemptEntity newAttempt(AsyncInvocationEntity invocation, String attemptId,
            String leaseToken, String workerId, long now) {
        AsyncInvocationAttemptEntity attempt = new AsyncInvocationAttemptEntity();
        attempt.setAttemptId(attemptId);
        attempt.setInvocationId(invocation.getInvocationId());
        attempt.setSequence(invocation.getTotalAttempts());
        attempt.setRedriveCount(invocation.getRedriveCount());
        attempt.setAttemptNumber(invocation.getAttempts());
        attempt.setWorkerId(workerId);
        attempt.setLeaseToken(leaseToken);
        attempt.setOutcome(OUTCOME_RUNNING);
        attempt.setStartedAt(now);
        return attempt;
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

    private static String authorityPredicate(int size) {
        List<String> predicates = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            predicates.add("(invocation_id = :invocation" + index + " AND lease_token = :token" + index + ")");
        }
        return String.join(" OR ", predicates);
    }

    private static void bindAuthorities(Query query, List<Map.Entry<String, String>> authorities) {
        for (int index = 0; index < authorities.size(); index++) {
            Map.Entry<String, String> authority = authorities.get(index);
            query.setParameter("invocation" + index, authority.getKey());
            query.setParameter("token" + index, authority.getValue());
        }
    }
}
