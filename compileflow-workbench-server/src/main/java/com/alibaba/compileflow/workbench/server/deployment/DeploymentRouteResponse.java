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
 * Current authoritative state for one process alias.
 *
 * @param processCode            process code
 * @param alias              target process alias
 * @param stableVersion       stable version
 * @param candidateVersion    candidate version, when a canary is active
 * @param candidateWeightBps candidate traffic weight in basis points
 * @param targetingPolicy     named Alias targeting policy, when configured
 * @param targetingParameters targeting policy configuration
 * @param revision            alias revision
 * @param updatedBy           last mutation actor
 * @param updatedAt           authority timestamp in epoch milliseconds
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentRouteResponse(@JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) String alias, @JsonProperty(required = true) String stableVersion,
        String candidateVersion, Integer candidateWeightBps, String targetingPolicy,
        Map<String, String> targetingParameters, @JsonProperty(required = true) long revision,
        @JsonProperty(required = true) String updatedBy, @JsonProperty(required = true) long updatedAt) {
    public DeploymentRouteResponse {
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(stableVersion, "stableVersion");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}
