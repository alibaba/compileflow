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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence entity for one asynchronous invocation.
 *
 * @author yusu
 */
@Entity
@Table(name = "cf_async_invocation")
public class AsyncInvocationEntity {
    @Id
    @Column(name = "invocation_id", length = 128, nullable = false)
    private String invocationId;
    @Column(name = "process_code", nullable = false, length = 128)
    private String processCode;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "total_attempts", nullable = false)
    private long totalAttempts;
    @Column(name = "redrive_count", nullable = false)
    private int redriveCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "retry_delay_ms", nullable = false)
    private long retryDelayMs;
    @Column(name = "available_at", nullable = false)
    private long availableAt;
    @Column(name = "params_json", nullable = false)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String paramsJson;
    @Column(name = "routing_json", nullable = false)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String routingJson;
    @Column(name = "result_json")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String resultJson;
    @Column(name = "error_code", length = 128)
    private String errorCode;
    @Column(name = "error_message", length = 4096)
    private String errorMessage;
    @Column(name = "trace_id", length = 128)
    private String traceId;
    @Column(name = "lease_token", length = 128)
    private String leaseToken;
    @Column(name = "lease_until")
    private Long leaseUntil;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
    @Column(name = "started_at")
    private Long startedAt;
    @Column(name = "completed_at")
    private Long completedAt;

    public String getInvocationId() {
        return invocationId;
    }

    public void setInvocationId(String invocationId) {
        this.invocationId = invocationId;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public long getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(long totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public int getRedriveCount() {
        return redriveCount;
    }

    public void setRedriveCount(int redriveCount) {
        this.redriveCount = redriveCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public long getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(long availableAt) {
        this.availableAt = availableAt;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getRoutingJson() {
        return routingJson;
    }

    public void setRoutingJson(String routingJson) {
        this.routingJson = routingJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String leaseToken) {
        this.leaseToken = leaseToken;
    }

    public Long getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Long leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
}
