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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Deployment runtime availability and local convergence diagnostics.
 *
 * @param timestamp                snapshot timestamp
 * @param available                whether deployment runtime diagnostics are available
 * @param started                  whether the deployment runtime has started
 * @param message                  unavailable-state explanation
 * @param topology                 embedded or distributed topology
 * @param desiredAliasCount        desired alias count
 * @param localReadyAliasCount     locally executable alias count
 * @param pendingAliasCount        pending convergence count
 * @param failedAliasCount         failed convergence count
 * @param aliases                  immutable alias diagnostics
 * @param inflightCount            inflight installation count
 * @param inflightCapacity         installation concurrency capacity
 * @param inflightAvailablePermits available installation permits
 * @param failureBackoffMs         configured failure backoff
 * @param retainedRuntimeCount     retained runtime count
 * @param inflightVersions         immutable inflight versions
 * @param demandedVersions         immutable demanded versions
 * @param pendingReleaseVersions   immutable versions pending release
 * @param backedOffVersions        immutable backed-off versions
 * @param deployedVersions         immutable locally deployed process versions
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentRuntimeDiagnosticsResponse(@JsonProperty(required = true) String timestamp,
        @JsonProperty(required = true) boolean available, @JsonProperty(required = true) boolean started, String message,
        String topology, Integer desiredAliasCount, Integer localReadyAliasCount, Integer pendingAliasCount,
        Integer failedAliasCount, List<DeploymentRuntimeAliasResponse> aliases, Integer inflightCount,
        Integer inflightCapacity, Integer inflightAvailablePermits, Long failureBackoffMs, Integer retainedRuntimeCount,
        List<DeploymentRuntimeVersionResponse> inflightVersions, List<DeploymentRuntimeVersionResponse> demandedVersions,
        List<DeploymentRuntimeVersionResponse> pendingReleaseVersions,
        List<DeploymentRuntimeBackedOffVersionResponse> backedOffVersions,
        List<DeploymentRuntimeDeployedProcessResponse> deployedVersions) {
    public DeploymentRuntimeDiagnosticsResponse {
        Objects.requireNonNull(timestamp, "timestamp");
        aliases = immutableCopy(aliases);
        inflightVersions = immutableCopy(inflightVersions);
        demandedVersions = immutableCopy(demandedVersions);
        pendingReleaseVersions = immutableCopy(pendingReleaseVersions);
        backedOffVersions = immutableCopy(backedOffVersions);
        deployedVersions = immutableCopy(deployedVersions);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
