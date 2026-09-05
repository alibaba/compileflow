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
 * Execution distribution for one effective process version.
 *
 * @param processCode         process code
 * @param namespace        effective namespace
 * @param effectiveVersion version actually executed
 * @param routingSource    routing source
 * @param routeAlias       Alias route or unattributed marker
 * @param executions       total executions
 * @param success          successful executions
 * @param failed           failed executions
 * @param avgDuration      average duration in milliseconds
 * @param successRate      success percentage from zero through one hundred
 * @author yusu
 */
public record VersionDistributionResponse(@JsonProperty(required = true) String processCode,
        @JsonProperty(required = true) String namespace, @JsonProperty(required = true) String effectiveVersion,
        @JsonProperty(required = true) String routingSource, @JsonProperty(required = true) String routeAlias,
        @JsonProperty(required = true) long executions, @JsonProperty(required = true) long success,
        @JsonProperty(required = true) long failed, @JsonProperty(required = true) long avgDuration,
        @JsonProperty(required = true) double successRate) {
    public VersionDistributionResponse {
        Objects.requireNonNull(processCode, "processCode");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(effectiveVersion, "effectiveVersion");
        Objects.requireNonNull(routingSource, "routingSource");
        Objects.requireNonNull(routeAlias, "routeAlias");
    }
}
