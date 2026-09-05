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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable code-generation plan for one reusable gateway branch body.
 *
 * <p>Concurrent branch writes are committed to the parent process only after
 * every active branch completes successfully. Nodes are stored by stable ID so
 * the analyzed plan does not retain mutable definition objects.
 *
 * @author yusu
 */
public final class GatewayBranchPlan {
    private final List<String> nodeIds;
    private final Set<String> reads;
    private final Set<String> writes;

    public GatewayBranchPlan(List<String> nodeIds, Set<String> reads, Set<String> writes) {
        this.nodeIds = List.copyOf(Objects.requireNonNull(nodeIds, "nodeIds"));
        this.reads = immutableOrderedSet(reads, "reads");
        this.writes = immutableOrderedSet(writes, "writes");
    }

    private static Set<String> immutableOrderedSet(Set<String> values, String name) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(values, name)));
    }

    public List<String> getNodeIds() {
        return nodeIds;
    }

    public Set<String> getReads() {
        return reads;
    }

    public Set<String> getWrites() {
        return writes;
    }
}
