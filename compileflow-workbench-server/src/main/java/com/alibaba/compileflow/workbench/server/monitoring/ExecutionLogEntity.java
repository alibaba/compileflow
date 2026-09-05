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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.engine.ProcessModelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persisted execution log persistence entity.
 *
 * @author yusu
 */
@Entity
@Table(name = "cf_execution_log")
public class ExecutionLogEntity {
    @Id
    @Column(length = 128, nullable = false)
    private String id;
    @Column(nullable = false, length = 128)
    private String processCode;
    @Column(nullable = false, length = 32)
    private String status;
    private long durationMs;
    @Column(length = 128)
    private String errorCode;
    @Column(length = 4096)
    private String errorMessage;
    @Column(nullable = false, length = 128)
    private String invocationId;
    @Column(length = 128)
    private String parentInvocationId;
    @Column(nullable = false)
    private int callDepth;
    @Column(nullable = false, length = 128)
    private String traceId;
    @Column(nullable = false, length = 128)
    private String namespace;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessModelType modelType;
    @Column(length = 64)
    private String sourceDigest;
    @Column(length = 64)
    private String requestedVersion;
    @Column(length = 64)
    private String effectiveVersion;
    @Column(length = 32)
    private String routingSource;
    @Column(length = 64)
    private String routeAlias;
    private Long routeRevision;
    @Column(nullable = false)
    private long loggedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
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

    public String getInvocationId() {
        return invocationId;
    }

    public void setInvocationId(String invocationId) {
        this.invocationId = invocationId;
    }

    public String getParentInvocationId() {
        return parentInvocationId;
    }

    public void setParentInvocationId(String parentInvocationId) {
        this.parentInvocationId = parentInvocationId;
    }

    public int getCallDepth() {
        return callDepth;
    }

    public void setCallDepth(int callDepth) {
        this.callDepth = callDepth;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public ProcessModelType getModelType() {
        return modelType;
    }

    public void setModelType(ProcessModelType modelType) {
        this.modelType = modelType;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public void setSourceDigest(String sourceDigest) {
        this.sourceDigest = sourceDigest;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }

    public void setRequestedVersion(String requestedVersion) {
        this.requestedVersion = requestedVersion;
    }

    public String getEffectiveVersion() {
        return effectiveVersion;
    }

    public void setEffectiveVersion(String effectiveVersion) {
        this.effectiveVersion = effectiveVersion;
    }

    public String getRoutingSource() {
        return routingSource;
    }

    public void setRoutingSource(String routingSource) {
        this.routingSource = routingSource;
    }

    public String getRouteAlias() {
        return routeAlias;
    }

    public void setRouteAlias(String routeAlias) {
        this.routeAlias = routeAlias;
    }

    public Long getRouteRevision() {
        return routeRevision;
    }

    public void setRouteRevision(Long routeRevision) {
        this.routeRevision = routeRevision;
    }

    public long getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(long loggedAt) {
        this.loggedAt = loggedAt;
    }
}
