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

/**
 * Canary health evaluation thresholds.
 *
 * @param lookbackMs         optional positive lookback window
 * @param minCanarySamples   optional positive minimum sample count
 * @param maxCanaryErrorRate optional maximum error rate from 0 to 1
 * @param maxCanaryP95Ms     optional non-negative p95 latency limit
 * @author yusu
 */
public record EvaluateCanaryRequest(Long lookbackMs, Integer minCanarySamples, Double maxCanaryErrorRate,
        Long maxCanaryP95Ms) {
    /**
     * Creates a request using all service defaults.
     *
     * @return empty threshold override
     */
    public static EvaluateCanaryRequest empty() {
        return new EvaluateCanaryRequest(null, null, null, null);
    }

    /**
     * Converts the HTTP DTO into validated service thresholds.
     *
     * @return immutable canary health request
     */
    public CanaryHealthService.CanaryHealthRequest toServiceRequest() {
        return CanaryHealthService.CanaryHealthRequest.of(lookbackMs, minCanarySamples, maxCanaryErrorRate,
                maxCanaryP95Ms);
    }
}
