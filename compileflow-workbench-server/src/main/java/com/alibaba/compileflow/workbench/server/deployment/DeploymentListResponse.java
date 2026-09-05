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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/**
 * One page of deployment rollouts.
 *
 * @param deployments immutable rollout page
 * @param nextCursor opaque continuation cursor, or {@code null}
 * @param hasMore    whether another page exists
 * @author yusu
 */
public record DeploymentListResponse(@JsonProperty(required = true) List<DeploymentResponse> deployments,
        @JsonProperty(required = true) @Schema(nullable = true) String nextCursor,
        @JsonProperty(required = true) boolean hasMore) {
    public DeploymentListResponse {
        deployments = List.copyOf(Objects.requireNonNull(deployments, "deployments"));
        if (hasMore != (nextCursor != null)) {
            throw new IllegalArgumentException("hasMore must match nextCursor presence");
        }
    }
}
