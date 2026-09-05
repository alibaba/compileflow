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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structured code-generation plan for one gateway.
 *
 * <p>A split owns only branch-exclusive paths and one shared continuation.
 * A join is a control boundary and therefore owns no executable path. Paths
 * contain stable node IDs rather than mutable definition objects.
 *
 * @author yusu
 */
public final class GatewayPlan {
    private final String gatewayId;
    private final Role role;
    private final String convergenceNodeId;
    private final Map<GatewayBranchKey, GatewayBranchPlan> branches;
    private final List<String> continuationNodeIds;
    private final boolean concurrent;
    private final boolean suspending;

    private GatewayPlan(String gatewayId, Role role, String convergenceNodeId,
            Map<GatewayBranchKey, GatewayBranchPlan> branches, List<String> continuationNodeIds, boolean concurrent,
            boolean suspending) {
        this.gatewayId = Objects.requireNonNull(gatewayId, "gatewayId");
        this.role = Objects.requireNonNull(role, "role");
        this.convergenceNodeId = convergenceNodeId;
        this.branches = immutableBranches(branches);
        this.continuationNodeIds = List.copyOf(continuationNodeIds);
        this.concurrent = concurrent;
        this.suspending = suspending;
    }

    public static GatewayPlan split(String gatewayId, String convergenceNodeId,
            Map<GatewayBranchKey, GatewayBranchPlan> branches, List<String> continuationNodeIds, boolean concurrent,
            boolean suspending) {
        return new GatewayPlan(gatewayId, Role.SPLIT, Objects.requireNonNull(convergenceNodeId, "convergenceNodeId"),
                branches, continuationNodeIds, concurrent, suspending);
    }

    public static GatewayPlan join(String gatewayId) {
        return new GatewayPlan(gatewayId, Role.JOIN, null, Map.of(), List.of(), false, false);
    }

    private static Map<GatewayBranchKey, GatewayBranchPlan> immutableBranches(
            Map<GatewayBranchKey, GatewayBranchPlan> branches) {
        Map<GatewayBranchKey, GatewayBranchPlan> snapshot = new LinkedHashMap<>();
        snapshot.putAll(Objects.requireNonNull(branches, "branches"));
        return Collections.unmodifiableMap(snapshot);
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isSplit() {
        return role == Role.SPLIT;
    }

    public boolean isJoin() {
        return role == Role.JOIN;
    }

    public String getConvergenceNodeId() {
        return convergenceNodeId;
    }

    public Map<GatewayBranchKey, GatewayBranchPlan> getBranches() {
        return branches;
    }

    public GatewayBranchPlan requireBranch(GatewayBranchKey key) {
        GatewayBranchKey requiredKey = Objects.requireNonNull(key, "key");
        GatewayBranchPlan branch = branches.get(requiredKey);
        if (branch == null) {
            throw new IllegalArgumentException(
                    "Missing branch plan '" + requiredKey + "' for gateway '" + gatewayId + "'");
        }
        return branch;
    }

    public List<String> getContinuationNodeIds() {
        return continuationNodeIds;
    }

    public boolean isConcurrent() {
        return concurrent;
    }

    public boolean isSuspending() {
        return suspending;
    }

    public enum Role {
        SPLIT,
        JOIN
    }
}
