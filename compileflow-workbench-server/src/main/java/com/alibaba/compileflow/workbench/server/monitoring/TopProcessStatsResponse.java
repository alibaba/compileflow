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
 * Aggregate execution statistics for one process.
 *
 * @param processCode       process code
 * @param executionCount execution count
 * @param avgDuration    average duration in milliseconds
 * @param successRate    success percentage from zero through one hundred
 * @author yusu
 */
public record TopProcessStatsResponse(@JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) long executionCount, @JsonProperty(required = true) long avgDuration,
        @JsonProperty(required = true) double successRate) {
    public TopProcessStatsResponse {
        Objects.requireNonNull(processCode, "processCode");
    }
}
