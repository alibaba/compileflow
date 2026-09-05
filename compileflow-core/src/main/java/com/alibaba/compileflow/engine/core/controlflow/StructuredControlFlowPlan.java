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
package com.alibaba.compileflow.engine.core.controlflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structured process plan used by code generators.
 *
 * @author yusu
 */
public final class StructuredControlFlowPlan {
    private final String entryNodeId;
    private final Map<String, GatewayPlan> gatewayPlans;

    StructuredControlFlowPlan(String entryNodeId, Map<String, GatewayPlan> gatewayPlans) {
        this.entryNodeId = Objects.requireNonNull(entryNodeId, "entryNodeId");
        this.gatewayPlans = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(gatewayPlans, "gatewayPlans")));
    }

    /**
     * Returns the validated entry node of the root Process scope.
     */
    public String getEntryNodeId() {
        return entryNodeId;
    }

    public Map<String, GatewayPlan> getGatewayPlans() {
        return gatewayPlans;
    }

    public GatewayPlan getGatewayPlan(String gatewayId) {
        return gatewayPlans.get(Objects.requireNonNull(gatewayId, "gatewayId"));
    }

    public GatewayPlan requireGatewayPlan(String gatewayId) {
        GatewayPlan plan = getGatewayPlan(gatewayId);
        if (plan == null) {
            throw new IllegalArgumentException("Missing structured gateway plan for node '" + gatewayId + "'");
        }
        return plan;
    }
}
