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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted asynchronous invocation representation.
 *
 * @param invocationId     stable invocation identifier and idempotency key
 * @param processCode         process code
 * @param status           persisted invocation lifecycle state
 * @param currentAttemptCount attempts consumed in the current delivery cycle
 * @param totalAttemptCount   physical attempts consumed across all redrives
 * @param redriveCount     operator redrives already accepted
 * @param maxAttempts      configured attempt limit
 * @param retryDelayMs     delay between attempts in milliseconds
 * @param nextAttemptAt    earliest dispatch time while queued
 * @param createdAt        creation timestamp
 * @param updatedAt        last update timestamp
 * @param startedAt        last attempt start timestamp
 * @param completedAt      terminal completion timestamp
 * @param traceId          latest engine trace identifier
 * @param leaseUntil       current worker lease expiration
 * @param error            latest failure detail
 * @param errorCode        latest stable engine error code
 * @param durationMs       latest attempt duration
 * @param routing          latest controlled routing attribution
 * @param response         latest synchronous execution response
 * @param payloadErrors    bounded persisted-payload decoding diagnostics
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncInvocationResponse(@JsonProperty(required = true) String invocationId,
        @JsonProperty(required = true) String processCode, @JsonProperty(required = true) AsyncInvocationStatus status,
        @JsonProperty(required = true) int currentAttemptCount, @JsonProperty(required = true) long totalAttemptCount,
        @JsonProperty(required = true) int redriveCount, @JsonProperty(required = true) int maxAttempts,
        @JsonProperty(required = true) long retryDelayMs, String nextAttemptAt,
        @JsonProperty(required = true) String createdAt, @JsonProperty(required = true) String updatedAt,
        String startedAt, String completedAt, String traceId, String leaseUntil, String error, String errorCode,
        Long durationMs, ExecutionRoutingResponse routing, ProcessExecutionResponse response,
        Map<String, String> payloadErrors) {
    public AsyncInvocationResponse {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        payloadErrors = immutableCopy(payloadErrors);
    }

    private static Map<String, String> immutableCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
