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
 * Stable REST representation of one append-only rollout audit event.
 *
 * @param id        persistent event identifier
 * @param sequence  one-based sequence within the rollout
 * @param type      stable uppercase event type
 * @param fromPhase previous lowercase phase, absent for the first event
 * @param toPhase   resulting lowercase phase
 * @param actor     authenticated mutation actor
 * @param reason    optional audit reason
 * @param timestamp authority timestamp in ISO-8601 format
 * @author yusu
 */
public record DeploymentEventView(@JsonProperty(required = true) long id, @JsonProperty(required = true) long sequence,
        @JsonProperty(required = true) String type, @JsonProperty(required = true) String fromPhase,
        @JsonProperty(required = true) String toPhase, @JsonProperty(required = true) String actor,
        @JsonProperty(required = true) String reason, @JsonProperty(required = true) String timestamp) {
    public DeploymentEventView {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(toPhase, "toPhase");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
