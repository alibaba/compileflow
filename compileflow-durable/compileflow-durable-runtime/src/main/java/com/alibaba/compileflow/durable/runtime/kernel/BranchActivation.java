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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Identity and reusable body target of one outgoing-flow activation.
 *
 * @author yusu
 */
public record BranchActivation(int ordinal, String branchStartId) implements Comparable<BranchActivation> {
    public BranchActivation {
        if (ordinal < 0) {
            throw new IllegalArgumentException("branch activation ordinal must be non-negative");
        }
        branchStartId = DurableIdentifiers.requireIdentity(branchStartId, "branchStartId", 128);
    }

    @Override
    public int compareTo(BranchActivation other) {
        int order = Integer.compare(ordinal, other.ordinal);
        return order != 0 ? order : branchStartId.compareTo(other.branchStartId);
    }

    /**
     * Returns the split-local identity used to derive a child frontier.
     */
    public String stableIdentity() {
        return Integer.toString(ordinal);
    }

    /**
     * Validates and orders a bounded activation set by its split-local ordinal.
     */
    public static List<BranchActivation> immutableSorted(Collection<BranchActivation> source, String name,
            int minimumSize, int maximumSize) {
        Collection<BranchActivation> values = Objects.requireNonNull(source, name);
        if (values.size() < minimumSize || values.size() > maximumSize) {
            throw new IllegalArgumentException(
                    name + " must contain " + minimumSize + ".." + maximumSize + " flow activations");
        }
        TreeMap<Integer, BranchActivation> ordered = new TreeMap<>();
        for (BranchActivation activation : values) {
            BranchActivation exact = Objects.requireNonNull(activation, name + " entry");
            if (ordered.putIfAbsent(exact.ordinal(), exact) != null) {
                throw new IllegalArgumentException(name + " must contain distinct activation ordinals");
            }
        }
        return List.copyOf(ordered.values());
    }
}
