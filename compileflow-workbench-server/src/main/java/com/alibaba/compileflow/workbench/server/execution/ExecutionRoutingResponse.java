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
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Public routing attribution for one process execution.
 *
 * @param namespace        runtime namespace
 * @param requestedVersion exact version requested by the caller, when applicable
 * @param requestedAlias   alias requested by the caller, when applicable
 * @param effectiveVersion version selected for execution
 * @param alias            effective alias used for routing
 * @param routeRevision    authoritative alias revision
 * @param target           selected stable or candidate side
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionRoutingResponse(@JsonProperty(required = true) String namespace, String requestedVersion,
        String requestedAlias, String effectiveVersion, String alias, Long routeRevision, ProcessAliasTarget target) {
    public ExecutionRoutingResponse {
        Objects.requireNonNull(namespace, "namespace");
    }

    /**
     * Creates transport attribution from the engine-owned execution facts.
     *
     * @param execution completed engine execution
     * @return routing attribution safe for the public response
     */
    public static ExecutionRoutingResponse from(ProcessExecution execution) {
        Objects.requireNonNull(execution, "execution");
        var version = execution.getProcessVersion();
        return new ExecutionRoutingResponse(execution.getNamespace(), null, null,
                version == null ? null : version.version(), null, null, null);
    }
}
