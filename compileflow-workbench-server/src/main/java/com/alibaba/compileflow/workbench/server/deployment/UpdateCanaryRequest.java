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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Canary traffic update request.
 *
 * @param weightBps        new canary traffic weight in basis points
 * @param expectedRevision expected rollout revision
 * @author yusu
 */
public record UpdateCanaryRequest(@NotNull @Min(1) @Max(9_999) Integer weightBps, @NotNull Long expectedRevision) {
    /**
     * Returns a validated canary traffic weight.
     *
     * @return basis points between 1 and 9999
     */
    public int requireWeightBps() {
        if (weightBps == null) {
            throw new IllegalArgumentException("weightBps is required");
        }
        if (weightBps <= 0 || weightBps >= 10_000) {
            throw new IllegalArgumentException("weightBps must be between 1 and 9999");
        }
        return weightBps;
    }

    /**
     * Returns a validated positive rollout revision.
     *
     * @return expected rollout revision
     */
    public long requireExpectedRevision() {
        return DeploymentRequestValidation.requirePositiveRevision(expectedRevision);
    }
}
