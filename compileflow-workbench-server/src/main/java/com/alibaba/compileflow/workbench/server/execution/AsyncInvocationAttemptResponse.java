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
import java.util.Objects;

/**
 * Observable lifecycle of one physical async invocation attempt.
 *
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncInvocationAttemptResponse(@JsonProperty(required = true) String attemptId,
        @JsonProperty(required = true) String invocationId, @JsonProperty(required = true) long sequence,
        @JsonProperty(required = true) int redriveCount, @JsonProperty(required = true) int attemptNumber,
        @JsonProperty(required = true) String workerId,
        @JsonProperty(required = true) AsyncInvocationAttemptOutcome outcome,
        AsyncInvocationAttemptDisposition disposition, @JsonProperty(required = true) String startedAt,
        String finishedAt, String nextAttemptAt, String traceId, String errorCode, String error, Long durationMs) {
    public AsyncInvocationAttemptResponse {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
