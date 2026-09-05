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
 * Canary health decision and the exact facts used to derive it.
 *
 * @param deployment evaluated rollout
 * @param metricsScope scope covered by the evidence
 * @param metricsSource source of the evidence
 * @param decision   insufficient data, healthy, or unhealthy
 * @param reason     stable human-readable decision explanation
 * @param canary     candidate-version statistics
 * @param baseline   stable-version statistics
 * @param thresholds effective evaluation thresholds
 * @author yusu
 */
public record CanaryHealthEvaluationResponse(@JsonProperty(required = true) DeploymentResponse deployment,
        @JsonProperty(required = true) String metricsScope, @JsonProperty(required = true) String metricsSource,
        @JsonProperty(required = true) String decision, @JsonProperty(required = true) String reason,
        @JsonProperty(required = true) CanaryVersionStatsResponse canary,
        @JsonProperty(required = true) CanaryVersionStatsResponse baseline,
        @JsonProperty(required = true) CanaryHealthThresholdsResponse thresholds) {
    public CanaryHealthEvaluationResponse {
        Objects.requireNonNull(deployment, "deployment");
        Objects.requireNonNull(metricsScope, "metricsScope");
        Objects.requireNonNull(metricsSource, "metricsSource");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(canary, "canary");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(thresholds, "thresholds");
    }
}
