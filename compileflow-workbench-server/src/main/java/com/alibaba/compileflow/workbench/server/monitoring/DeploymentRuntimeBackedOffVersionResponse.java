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
 * Exact version temporarily blocked after an installation failure.
 *
 * @param namespace    process namespace
 * @param code         process code
 * @param version      immutable version
 * @param id           display identity
 * @param reason       bounded failure reason
 * @param blockedUntil retry eligibility timestamp
 * @param remainingMs  remaining backoff duration in milliseconds
 * @author yusu
 */
public record DeploymentRuntimeBackedOffVersionResponse(@JsonProperty(required = true) String namespace,
        @JsonProperty(required = true) String code, @JsonProperty(required = true) String version,
        @JsonProperty(required = true) String id, @JsonProperty(required = true) String reason,
        @JsonProperty(required = true) String blockedUntil, @JsonProperty(required = true) long remainingMs) {
    public DeploymentRuntimeBackedOffVersionResponse {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(blockedUntil, "blockedUntil");
    }
}
