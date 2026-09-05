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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Aggregate execution metrics for one bounded time window.
 *
 * @param scope             execution provenance represented by these metrics
 * @param totalExecutions   total retained executions
 * @param successExecutions successful executions
 * @param failedExecutions  failed executions
 * @param avgExecutionTime  average execution duration in milliseconds
 * @param processesWithAliases       processes with an authoritative alias
 * @param timeRange         requested named time range
 * @param windowStart       inclusive window start timestamp
 * @param windowEnd         inclusive window end timestamp
 * @param timestamp         snapshot timestamp
 * @author yusu
 */
public record MonitoringMetricsResponse(@JsonProperty(required = true) String scope,
        @JsonProperty(required = true) long totalExecutions, @JsonProperty(required = true) long successExecutions,
        @JsonProperty(required = true) long failedExecutions, @JsonProperty(required = true) long avgExecutionTime,
        @JsonProperty(required = true) long processesWithAliases, @JsonProperty(required = true) String timeRange,
        @JsonProperty(required = true) String windowStart, @JsonProperty(required = true) String windowEnd,
        @JsonProperty(required = true) String timestamp) {
    public MonitoringMetricsResponse {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(timeRange, "timeRange");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
