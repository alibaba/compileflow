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

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Fixed HTTP routing envelope kept separate from dynamic process variables.
 *
 * @param version    exact published version
 * @param alias      published process Alias
 * @param routingKey opaque deterministic routing key
 * @param attributes bounded request-scoped targeting attributes
 * @author yusu
 */
public record ExecutionRoutingRequest(String version, String alias, String routingKey, Map<String, String> attributes) {
    public ExecutionRoutingRequest {
        version = StringUtils.trimToNull(version);
        alias = StringUtils.trimToNull(alias);
        routingKey = routingKey == null || routingKey.isEmpty() ? null : routingKey;
        AliasRoutingOptions validated = new AliasRoutingOptions(routingKey, attributes);
        attributes = validated.attributes();
    }

    /**
     * Requires exactly one version or alias route.
     */
    public void validateRouteSelection() {
        if ((version == null) == (alias == null)) {
            throw new IllegalArgumentException("routing must contain exactly one of version or alias");
        }
    }

    /**
     * Validates routing for an invocation that is admitted before persistence.
     */
    public void validatePersistedInvocation() {
        validateRouteSelection();
        validateAliasRouting();
    }

    /**
     * Rejects Alias-only routing inputs when an exact version was selected.
     */
    public void validateAliasRouting() {
        if (version != null && (routingKey != null || !attributes.isEmpty())) {
            throw new IllegalArgumentException("routingKey and attributes require an alias route");
        }
    }
}
