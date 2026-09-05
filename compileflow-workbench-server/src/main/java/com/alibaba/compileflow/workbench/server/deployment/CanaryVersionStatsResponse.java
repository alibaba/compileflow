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
 * Execution statistics for one version in a canary evaluation window.
 *
 * @param version       immutable process version
 * @param samples       observed execution count
 * @param failures      failed execution count
 * @param errorRate     failure ratio from zero to one
 * @param p95DurationMs observed p95 duration in milliseconds
 * @author yusu
 */
public record CanaryVersionStatsResponse(@JsonProperty(required = true) String version,
        @JsonProperty(required = true) int samples, @JsonProperty(required = true) int failures,
        @JsonProperty(required = true) double errorRate, @JsonProperty(required = true) long p95DurationMs) {
    public CanaryVersionStatsResponse {
        Objects.requireNonNull(version, "version");
    }
}
