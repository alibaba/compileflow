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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Stable HTTP representation of one deployment rollout.
 *
 * @param id                rollout identifier
 * @param processCode          process code
 * @param version           target version
 * @param baselineVersion   version captured before the rollout, when present
 * @param alias             target process alias
 * @param operation         deploy or rollback operation
 * @param status            rollout status exposed to Workbench
 * @param strategy          rollout strategy
 * @param canaryWeightBps  candidate traffic weight in basis points for canary rollouts
 * @param targetingPolicy named Alias targeting policy, when configured
 * @param targetingParameters targeting policy configuration
 * @param revision          rollout optimistic-lock revision
 * @param baseRouteRevision alias revision observed when the rollout was created
 * @param routeRevision     last alias revision committed by the rollout
 * @param createdAt         rollout creation timestamp
 * @param deployedAt        route commit timestamp, when complete
 * @param createdBy         authenticated actor
 * @param notes             optional operator note
 * @param duration          rollout duration in milliseconds, when complete
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentResponse(@JsonProperty(required = true) String id,
        @JsonProperty(required = true) String processCode, @JsonProperty(required = true) String version,
        String baselineVersion, @JsonProperty(required = true) String alias,
        @JsonProperty(required = true) String operation, @JsonProperty(required = true) String status,
        @JsonProperty(required = true) String strategy, Integer canaryWeightBps, String targetingPolicy,
        Map<String, String> targetingParameters, @JsonProperty(required = true) long revision,
        @JsonProperty(required = true) long baseRouteRevision, @JsonProperty(required = true) long routeRevision,
        @JsonProperty(required = true) String createdAt, String deployedAt,
        @JsonProperty(required = true) String createdBy, String notes, Long duration) {
    public DeploymentResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
    }
}
