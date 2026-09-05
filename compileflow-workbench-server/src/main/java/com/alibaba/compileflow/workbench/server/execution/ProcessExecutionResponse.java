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

import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable HTTP representation of a synchronous process execution.
 *
 * <p>The envelope is closed and typed. The process result remains an open JSON object because its
 * variables are defined by the individual process rather than the platform contract.
 *
 * @param success      whether execution completed successfully
 * @param message      human-readable outcome summary
 * @param traceId      trace correlation identifier
 * @param invocationId stable invocation identifier
 * @param processCode  executed process code
 * @param durationMs   server-observed execution duration in milliseconds
 * @param routing      controlled routing attribution
 * @param result       process-defined output variables on success
 * @param errorCode    stable engine error code on failure
 * @param error        human-readable failure detail
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessExecutionResponse(@JsonProperty(required = true) boolean success,
        @JsonProperty(required = true) String message, @JsonProperty(required = true) String traceId,
        @JsonProperty(required = true) String invocationId, @JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) long durationMs, @JsonProperty(required = true) ExecutionRoutingResponse routing,
        Map<String, Object> result, String errorCode, String error) {
    public ProcessExecutionResponse {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(routing, "routing");
        result = immutableCopy(result);
        if (success) {
            if (errorCode != null || error != null) {
                throw new IllegalArgumentException("Successful execution must not contain failure fields");
            }
        } else {
            Objects.requireNonNull(errorCode, "errorCode");
            Objects.requireNonNull(error, "error");
            if (result != null) {
                throw new IllegalArgumentException("Failed execution must not contain a result");
            }
        }
    }

    /**
     * Creates a transport response from one engine result.
     *
     * @param result     completed engine result
     * @param durationMs server-observed duration in milliseconds
     * @return typed success or failure response
     */
    public static ProcessExecutionResponse from(ProcessResult<Map<String, Object>> result, long durationMs) {
        Objects.requireNonNull(result, "result");
        ProcessExecution execution = result.getExecution();
        if (result.isSuccess()) {
            return new ProcessExecutionResponse(true, "Process executed successfully", execution.getTraceId(),
                    execution.getInvocationId(), execution.getProcessCode(), durationMs,
                    ExecutionRoutingResponse.from(execution), result.getOutput(), null, null);
        }
        return new ProcessExecutionResponse(false, "Process execution failed", execution.getTraceId(),
                execution.getInvocationId(), execution.getProcessCode(), durationMs,
                ExecutionRoutingResponse.from(execution), null, result.getError().getCode(),
                result.getError().getMessage());
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        if (values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Returns the same execution response with sanitized routing attribution.
     *
     * @param value controlled routing attribution
     * @return response containing the supplied routing facts
     */
    public ProcessExecutionResponse withRouting(ExecutionRoutingResponse value) {
        return new ProcessExecutionResponse(success, message, traceId, invocationId, processCode, durationMs, value,
                result, errorCode, error);
    }
}
