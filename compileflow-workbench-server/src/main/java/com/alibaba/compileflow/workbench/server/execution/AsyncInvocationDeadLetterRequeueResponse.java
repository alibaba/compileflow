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
import java.util.List;
import java.util.Objects;

/**
 * Result of an operator dead-letter requeue command.
 *
 * @param requeued      number of invocations requeued
 * @param requeuedAt    command timestamp
 * @param processCode      optional process-code filter
 * @param limit         effective selection limit
 * @param invocationIds selected invocation identifiers
 * @param health        queue health after the command
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncInvocationDeadLetterRequeueResponse(@JsonProperty(required = true) int requeued,
        @JsonProperty(required = true) String requeuedAt, String processCode, @JsonProperty(required = true) int limit,
        @JsonProperty(required = true) List<String> invocationIds,
        @JsonProperty(required = true) AsyncInvocationHealthResponse health) {
    public AsyncInvocationDeadLetterRequeueResponse {
        Objects.requireNonNull(requeuedAt, "requeuedAt");
        invocationIds = List.copyOf(Objects.requireNonNull(invocationIds, "invocationIds"));
        Objects.requireNonNull(health, "health");
    }
}
