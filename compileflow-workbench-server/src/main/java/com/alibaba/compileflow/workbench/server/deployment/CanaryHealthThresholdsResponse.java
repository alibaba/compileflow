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

/**
 * Effective thresholds used for one canary health evaluation.
 *
 * @param lookbackMs         evaluation lookback in milliseconds
 * @param minCanarySamples   minimum required candidate sample count
 * @param maxCanaryErrorRate maximum accepted candidate error rate
 * @param maxCanaryP95Ms     maximum accepted candidate p95 duration, or zero when disabled
 * @author yusu
 */
public record CanaryHealthThresholdsResponse(@JsonProperty(required = true) long lookbackMs,
        @JsonProperty(required = true) int minCanarySamples, @JsonProperty(required = true) double maxCanaryErrorRate,
        @JsonProperty(required = true) long maxCanaryP95Ms) {}
