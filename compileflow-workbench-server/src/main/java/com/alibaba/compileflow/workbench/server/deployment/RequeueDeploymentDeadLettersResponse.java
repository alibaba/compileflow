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
 * Result of requeueing routing outbox dead letters.
 *
 * @param requeued   number of records returned to pending
 * @param requeuedAt operation completion timestamp
 * @param health     pipeline health after the operation
 * @author yusu
 */
public record RequeueDeploymentDeadLettersResponse(@JsonProperty(required = true) int requeued,
        @JsonProperty(required = true) String requeuedAt,
        @JsonProperty(required = true) DeploymentControlHealthResponse health) {
    public RequeueDeploymentDeadLettersResponse {
        Objects.requireNonNull(requeuedAt, "requeuedAt");
        Objects.requireNonNull(health, "health");
    }
}
