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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Desired and local-ready state for one deployment alias.
 *
 * @param namespace          process namespace
 * @param code               process code
 * @param alias              deployment alias
 * @param desiredRevision    desired control-plane revision
 * @param desiredDeleted     whether desired state is deletion
 * @param localReadyRevision locally executable revision
 * @param localReadyDeleted  whether local-ready state is deletion
 * @param state              local convergence state
 * @param failureReason      bounded convergence failure reason
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeployRuntimeAliasResponse(@JsonProperty(required = true) String namespace,
        @JsonProperty(required = true) String code, @JsonProperty(required = true) String alias,
        @JsonProperty(required = true) long desiredRevision, @JsonProperty(required = true) boolean desiredDeleted,
        @JsonProperty(required = true) long localReadyRevision, @JsonProperty(required = true) boolean localReadyDeleted,
        @JsonProperty(required = true) String state, String failureReason) {
    public DeployRuntimeAliasResponse {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(state, "state");
    }
}
