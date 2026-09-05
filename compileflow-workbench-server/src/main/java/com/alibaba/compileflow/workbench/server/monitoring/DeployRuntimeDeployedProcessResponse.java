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
import java.util.List;
import java.util.Objects;

/**
 * Locally deployed versions grouped by process identity.
 *
 * @param namespace process namespace
 * @param code      process code
 * @param versions  immutable deployed versions
 * @author yusu
 */
public record DeployRuntimeDeployedProcessResponse(@JsonProperty(required = true) String namespace,
        @JsonProperty(required = true) String code, @JsonProperty(required = true) List<String> versions) {
    public DeployRuntimeDeployedProcessResponse {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(code, "code");
        versions = List.copyOf(Objects.requireNonNull(versions, "versions"));
    }
}
