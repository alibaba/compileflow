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

import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * HTTP request for creating a deployment rollout.
 *
 * @param processCode              published process code
 * @param version               published process version
 * @param alias                  target process alias
 * @param strategy              rollout strategy
 * @param notes                 optional operator notes
 * @param canaryWeightBps      optional canary traffic weight in basis points
 * @param targetingPolicy       optional named Alias targeting policy
 * @param targetingParameters   bounded targeting policy configuration
 * @param expectedRouteRevision expected current route revision
 * @author yusu
 */
public record CreateDeploymentRequest(@NotBlank String processCode, @NotBlank String version, @NotBlank String alias,
        String strategy, String notes, @Min(1) @Max(9_999) Integer canaryWeightBps, String targetingPolicy,
        Map<String, String> targetingParameters, @NotNull Long expectedRouteRevision) {
    public CreateDeploymentRequest {
        processCode = StringUtils.trimToNull(processCode);
        version = StringUtils.trimToNull(version);
        alias = StringUtils.trimToNull(alias);
        strategy = StringUtils.trimToNull(strategy);
        notes = StringUtils.trimToNull(notes);
        targetingPolicy = StringUtils.trimToNull(targetingPolicy);
        targetingParameters = RequestValueParser.immutableMap(targetingParameters);
    }

    /**
     * Validates the HTTP fields and creates the application command.
     *
     * @param idempotencyKey request retry identity
     * @return immutable deployment command
     */
    public CreateDeploymentCommand toCommand(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || processCode == null || version == null) {
            throw new IllegalArgumentException("Idempotency-Key, processCode, and version are required");
        }
        String validatedKey = RolloutConstraints.requireIdempotencyKey(idempotencyKey);
        String normalizedAlias = DeploymentRequestValidation.requireAlias(alias);
        if (expectedRouteRevision == null || expectedRouteRevision < 0) {
            throw new IllegalArgumentException("expectedRouteRevision must be zero or greater");
        }
        if ("canary".equalsIgnoreCase(strategy)
                && (canaryWeightBps == null || canaryWeightBps <= 0 || canaryWeightBps >= 10_000)) {
            throw new IllegalArgumentException("canaryWeightBps must be between 1 and 9999");
        }
        AliasTargeting targeting =
                targetingPolicy == null ? null : new AliasTargeting(targetingPolicy, targetingParameters);
        if (targeting == null && !targetingParameters.isEmpty()) {
            throw new IllegalArgumentException("targetingParameters require targetingPolicy");
        }
        return new CreateDeploymentCommand(validatedKey, processCode, version, normalizedAlias, expectedRouteRevision,
                strategy, notes, canaryWeightBps, targeting);
    }
}
