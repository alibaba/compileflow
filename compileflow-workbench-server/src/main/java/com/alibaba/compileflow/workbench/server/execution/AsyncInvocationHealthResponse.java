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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Persisted asynchronous invocation queue health snapshot.
 *
 * @param status              healthy or degraded
 * @param queuedCount         queued invocation count
 * @param readyQueuedCount    queued invocations ready for dispatch
 * @param oldestReadyAgeMs    milliseconds since the oldest ready invocation became dispatchable, or
 *                            zero when none are ready
 * @param delayedQueuedCount  queued invocations waiting for their retry time
 * @param runningCount        running invocation count
 * @param succeededCount      retained successful invocation count
 * @param deadLetterCount     dead-letter invocation count
 * @param expiredRunningCount expired running leases
 * @param localRunningCount   invocations running in this server process
 * @param dispatchedCount     invocations guarded as dispatched in this server process
 * @param workerId            reporting worker identifier
 * @param leaseDurationMs     configured worker lease duration
 * @param dispatchBatchSize   configured dispatch batch size
 * @param checkedAt           snapshot timestamp
 * @author yusu
 */
public record AsyncInvocationHealthResponse(@JsonProperty(required = true) String status,
        @JsonProperty(required = true) long queuedCount, @JsonProperty(required = true) long readyQueuedCount,
        @JsonProperty(required = true) long oldestReadyAgeMs, @JsonProperty(required = true) long delayedQueuedCount,
        @JsonProperty(required = true) long runningCount, @JsonProperty(required = true) long succeededCount,
        @JsonProperty(required = true) long deadLetterCount, @JsonProperty(required = true) long expiredRunningCount,
        @JsonProperty(required = true) int localRunningCount, @JsonProperty(required = true) int dispatchedCount,
        @JsonProperty(required = true) String workerId, @JsonProperty(required = true) long leaseDurationMs,
        @JsonProperty(required = true) int dispatchBatchSize, @JsonProperty(required = true) String checkedAt) {
    public AsyncInvocationHealthResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
