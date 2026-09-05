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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence operations for physical async invocation attempts.
 *
 * @author yusu
 */
interface AsyncInvocationAttemptRepository extends JpaRepository<AsyncInvocationAttemptEntity, String> {
    List<AsyncInvocationAttemptEntity> findByInvocationIdAndSequenceGreaterThanOrderBySequenceAsc(String invocationId,
            long sequence, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationAttemptEntity a set a.outcome = :outcome, a.disposition = :"
            + "disposition, a.traceId = :traceId, a.errorCode = :errorCode, a.errorMessage = :"
            + "errorMessage, a.durationMs = :durationMs, a.finishedAt = :finishedAt, a.nextAttemptAt = :"
            + "nextAttemptAt where a.attemptId = :attemptId and a.outcome = :running")
    int finishOwned(@Param("attemptId") String attemptId, @Param("running") String running,
            @Param("outcome") String outcome, @Param("disposition") String disposition, @Param("traceId") String traceId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
            @Param("durationMs") Long durationMs, @Param("finishedAt") long finishedAt,
            @Param("nextAttemptAt") Long nextAttemptAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AsyncInvocationAttemptEntity a set a.outcome = :outcome, a.disposition = :"
            + "disposition, a.errorCode = :errorCode, a.errorMessage = :errorMessage, a.durationMs = :"
            + "durationMs, a.finishedAt = :finishedAt, a.nextAttemptAt = :nextAttemptAt where a."
            + "invocationId = :invocationId and a.leaseToken = :leaseToken and a.outcome = :running")
    int finishExpired(@Param("invocationId") String invocationId, @Param("leaseToken") String leaseToken,
            @Param("running") String running, @Param("outcome") String outcome, @Param("disposition") String disposition,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
            @Param("durationMs") long durationMs, @Param("finishedAt") long finishedAt,
            @Param("nextAttemptAt") Long nextAttemptAt);
}
