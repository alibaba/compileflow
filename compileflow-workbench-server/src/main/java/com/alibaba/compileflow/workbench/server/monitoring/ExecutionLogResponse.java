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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Stable HTTP representation of one persisted execution log.
 *
 * @param id                 persistent log identifier
 * @param processCode           process code
 * @param invocationId       engine invocation identifier
 * @param parentInvocationId direct synchronous caller invocation
 * @param callDepth          zero-based synchronous process-call depth
 * @param traceId            correlated trace identifier
 * @param modelType          process definition format
 * @param sourceDigest       exact source SHA-256 digest
 * @param status             success or failed
 * @param startTime          execution start timestamp
 * @param endTime            execution completion timestamp
 * @param duration           execution duration in milliseconds
 * @param namespace          effective process namespace
 * @param requestedVersion   explicitly requested version
 * @param effectiveVersion   version actually executed
 * @param routingSource      routing source
 * @param routeAlias         alias used for routing
 * @param routeRevision      authoritative alias revision
 * @param errorCode          stable error code
 * @param errorMessage       redacted failure message
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionLogResponse(@JsonProperty(required = true) String id,
        @JsonProperty(required = true) String processCode, @JsonProperty(required = true) String invocationId,
        String parentInvocationId, @JsonProperty(required = true) int callDepth,
        @JsonProperty(required = true) String traceId, @JsonProperty(required = true) ProcessModelType modelType,
        String sourceDigest, @JsonProperty(required = true) String status,
        @JsonProperty(required = true) String startTime, @JsonProperty(required = true) String endTime,
        @JsonProperty(required = true) long duration, @JsonProperty(required = true) String namespace,
        String requestedVersion, String effectiveVersion, String routingSource, String routeAlias, Long routeRevision,
        String errorCode, String errorMessage) {
    public ExecutionLogResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(invocationId, "invocationId");
        if (callDepth < 0) {
            throw new IllegalArgumentException("callDepth must not be negative");
        }
        if ((callDepth == 0) != (parentInvocationId == null)) {
            throw new IllegalArgumentException("parentInvocationId must be null exactly when callDepth is 0");
        }
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(modelType, "modelType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(namespace, "namespace");
    }
}
