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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for persisted async invocations.
 *
 * @author yusu
 */
public interface AsyncInvocationRepository extends JpaRepository<AsyncInvocationEntity, String> {
    @Query(value = "SELECT CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT)", nativeQuery = true)
    long currentTimeMillis();

    Page<AsyncInvocationEntity> findByStatusAndAvailableAtLessThanEqual(String status, long availableAt,
            Pageable pageable);

    Page<AsyncInvocationEntity> findByStatusAndLeaseUntilLessThan(String status, long leaseUntil, Pageable pageable);

    Page<AsyncInvocationEntity> findByStatus(String status, Pageable pageable);

    Page<AsyncInvocationEntity> findByProcessCode(String processCode, Pageable pageable);

    Page<AsyncInvocationEntity> findByProcessCodeAndStatus(String processCode, String status, Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndLeaseUntilLessThan(String status, long leaseUntil);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :running, e.attempts = e.attempts + 1, e."
            + "totalAttempts = e.totalAttempts + 1, e.startedAt = :now, e.updatedAt = :now, e.leaseToken "
            + "= :leaseToken, e.leaseUntil = :leaseUntil where e.invocationId = :invocationId and e."
            + "status = :queued and e.availableAt <= :now")
    int claimQueued(@Param("invocationId") String invocationId, @Param("queued") String queued,
            @Param("running") String running, @Param("now") long now, @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") long leaseUntil);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.leaseUntil = :leaseUntil, e.updatedAt = :now where e."
            + "invocationId = :invocationId and e.status = :running and e.leaseToken = :leaseToken and e."
            + "leaseUntil >= :now")
    int extendLease(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("leaseToken") String leaseToken, @Param("leaseUntil") long leaseUntil, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.routingJson = :routingJson, e.updatedAt = :now where "
            + "e.invocationId = :invocationId and e.status = :running and e.leaseToken = :leaseToken and "
            + "e.leaseUntil >= :now")
    int pinOwnedRouting(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("leaseToken") String leaseToken, @Param("routingJson") String routingJson, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :succeeded, e.routingJson = :routingJson, e."
            + "resultJson = :resultJson, e.errorCode = null, e.errorMessage = null, e.traceId = :traceId,"
            + " e.durationMs = :durationMs, e.leaseToken = null, e.leaseUntil = null, e.availableAt = :"
            + "now, e.updatedAt = :now, e.completedAt = :now where e.invocationId = :invocationId and e."
            + "status = :running and e.leaseToken = :leaseToken and e.leaseUntil >= :now")
    int completeOwned(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("succeeded") String succeeded, @Param("leaseToken") String leaseToken,
            @Param("routingJson") String routingJson, @Param("resultJson") String resultJson,
            @Param("traceId") String traceId, @Param("durationMs") Long durationMs, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :queued, e.routingJson = :routingJson, e."
            + "resultJson = :resultJson, e.errorCode = :errorCode, e.errorMessage = :errorMessage, e."
            + "traceId = :traceId, e.durationMs = :durationMs, e.leaseToken = null, e.leaseUntil = null, "
            + "e.availableAt = :availableAt, e.updatedAt = :now, e.completedAt = null where e."
            + "invocationId = :invocationId and e.status = :running and e.leaseToken = :leaseToken and e."
            + "leaseUntil >= :now and e.attempts < e.maxAttempts")
    int retryOwned(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("queued") String queued, @Param("leaseToken") String leaseToken,
            @Param("routingJson") String routingJson, @Param("resultJson") String resultJson,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
            @Param("traceId") String traceId, @Param("durationMs") Long durationMs,
            @Param("availableAt") long availableAt, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :deadLetter, e.routingJson = :routingJson, "
            + "e.resultJson = :resultJson, e.errorCode = :errorCode, e.errorMessage = :errorMessage, e."
            + "traceId = :traceId, e.durationMs = :durationMs, e.leaseToken = null, e.leaseUntil = null, "
            + "e.availableAt = :now, e.updatedAt = :now, e.completedAt = :now where e.invocationId = :"
            + "invocationId and e.status = :running and e.leaseToken = :leaseToken and e.leaseUntil >= :" + "now")
    int deadLetterOwned(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("deadLetter") String deadLetter, @Param("leaseToken") String leaseToken,
            @Param("routingJson") String routingJson, @Param("resultJson") String resultJson,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
            @Param("traceId") String traceId, @Param("durationMs") Long durationMs, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :queued, e.errorCode = :errorCode, e."
            + "errorMessage = :errorMessage, e.leaseToken = null, e.leaseUntil = null, e.availableAt = :"
            + "availableAt, e.updatedAt = :now, e.completedAt = null where e.invocationId = :"
            + "invocationId and e.status = :running and e.leaseToken = :leaseToken and e.leaseUntil < :"
            + "now and e.attempts < e.maxAttempts")
    int recoverExpiredForRetry(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("queued") String queued, @Param("leaseToken") String leaseToken, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("availableAt") long availableAt,
            @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :deadLetter, e.errorCode = :errorCode, e."
            + "errorMessage = :errorMessage, e.leaseToken = null, e.leaseUntil = null, e.availableAt = :"
            + "now, e.updatedAt = :now, e.completedAt = :now where e.invocationId = :invocationId and e."
            + "status = :running and e.leaseToken = :leaseToken and e.leaseUntil < :now and e.attempts "
            + ">= e.maxAttempts")
    int recoverExpiredToDeadLetter(@Param("invocationId") String invocationId, @Param("running") String running,
            @Param("deadLetter") String deadLetter, @Param("leaseToken") String leaseToken,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("now") long now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationEntity e set e.status = :queued, e.attempts = 0, e.redriveCount = e."
            + "redriveCount + 1, e.errorCode = null, e.errorMessage = null, e.resultJson = null, e."
            + "traceId = null, e.leaseToken = null, e.leaseUntil = null, e.durationMs = null, e."
            + "startedAt = null, e.completedAt = null, e.availableAt = :now, e.updatedAt = :now where e."
            + "invocationId = :invocationId and e.status = :deadLetter")
    int requeueDeadLetter(@Param("invocationId") String invocationId, @Param("deadLetter") String deadLetter,
            @Param("queued") String queued, @Param("now") long now);
}
