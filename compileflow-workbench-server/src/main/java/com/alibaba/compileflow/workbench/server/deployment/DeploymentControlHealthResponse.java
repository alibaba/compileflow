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
package com.alibaba.compileflow.workbench.server.deployment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Routing outbox pipeline health snapshot.
 *
 * @param status               overall pipeline status
 * @param pendingCount         pending outbox records
 * @param processingCount      records currently claimed by dispatchers
 * @param expiredClaimCount    processing records with expired leases
 * @param failedCount          dead-letter records
 * @param dispatcherRunning    whether the local dispatcher is running
 * @param outboxStateAvailable whether outbox state storage is available
 * @param checkedAt            snapshot timestamp
 * @author yusu
 */
public record DeploymentControlHealthResponse(@JsonProperty(required = true) String status,
        @JsonProperty(required = true) long pendingCount, @JsonProperty(required = true) long processingCount,
        @JsonProperty(required = true) long expiredClaimCount, @JsonProperty(required = true) long failedCount,
        @JsonProperty(required = true) boolean dispatcherRunning,
        @JsonProperty(required = true) boolean outboxStateAvailable, @JsonProperty(required = true) String checkedAt) {
    public DeploymentControlHealthResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
