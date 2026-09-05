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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistence entity for one physical attempt of a logical async invocation.
 *
 * @author yusu
 */
@Entity
@Table(name = "cf_async_invocation_attempt")
class AsyncInvocationAttemptEntity {
    @Id
    @Column(name = "attempt_id", nullable = false, length = 128)
    private String attemptId;
    @Column(name = "invocation_id", nullable = false, length = 128)
    private String invocationId;
    @Column(name = "sequence_no", nullable = false)
    private long sequence;
    @Column(name = "redrive_count", nullable = false)
    private int redriveCount;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;
    @Column(name = "lease_token", nullable = false, length = 128)
    private String leaseToken;
    @Column(nullable = false, length = 32)
    private String outcome;
    @Column(length = 32)
    private String disposition;
    @Column(name = "trace_id", length = 128)
    private String traceId;
    @Column(name = "error_code", length = 128)
    private String errorCode;
    @Column(name = "error_message", length = 4096)
    private String errorMessage;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "started_at", nullable = false)
    private long startedAt;
    @Column(name = "finished_at")
    private Long finishedAt;
    @Column(name = "next_attempt_at")
    private Long nextAttemptAt;

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getInvocationId() {
        return invocationId;
    }

    public void setInvocationId(String invocationId) {
        this.invocationId = invocationId;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public int getRedriveCount() {
        return redriveCount;
    }

    public void setRedriveCount(int redriveCount) {
        this.redriveCount = redriveCount;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String leaseToken) {
        this.leaseToken = leaseToken;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Long nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }
}
