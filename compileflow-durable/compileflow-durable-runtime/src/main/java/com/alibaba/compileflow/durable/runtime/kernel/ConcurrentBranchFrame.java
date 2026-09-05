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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Process-owned ancestry and split-state baseline for one structured concurrent branch.
 *
 * @author yusu
 */
public record ConcurrentBranchFrame(FrontierId parentFrontierId, String splitId, String joinId,
        BranchActivation activation, Map<String, Object> baselineVariables, Set<BranchActivation> selectedActivations,
        Set<String> writtenVariables) implements BranchFrame {
    public ConcurrentBranchFrame(FrontierId parentFrontierId, String splitId, String joinId, BranchActivation activation,
            Map<String, Object> baselineVariables, Set<BranchActivation> selectedActivations) {
        this(parentFrontierId, splitId, joinId, activation, baselineVariables, selectedActivations, Set.of());
    }

    public ConcurrentBranchFrame {
        parentFrontierId = Objects.requireNonNull(parentFrontierId, "parentFrontierId");
        splitId = requireText(splitId, "splitId");
        joinId = requireText(joinId, "joinId");
        activation = Objects.requireNonNull(activation, "activation");
        baselineVariables = DurableValueSnapshots.immutableMap(Objects.requireNonNull(baselineVariables,
                "baselineVariables"));
        List<BranchActivation> selected =
                BranchActivation.immutableSorted(selectedActivations, "selectedActivations", 1, 256);
        if (!selected.contains(activation)) {
            throw new IllegalArgumentException("Selected branches must contain this branch within the 1..256 bound");
        }
        selectedActivations = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        TreeSet<String> writes = new TreeSet<>();
        for (String variable : Objects.requireNonNull(writtenVariables, "writtenVariables")) {
            writes.add(requireText(variable, "written variable"));
        }
        if (writes.size() > 256) {
            throw new IllegalArgumentException("One branch may track at most 256 written variables");
        }
        writtenVariables = Collections.unmodifiableSet(new LinkedHashSet<>(writes));
    }

    /**
     * Stable identity of the child frontier represented by this ancestry frame.
     */
    public FrontierId frontierId() {
        return FrontierId.branch(parentFrontierId, splitId, activation.stableIdentity());
    }

    public ConcurrentBranchFrame recordWrites(Set<String> variables) {
        TreeSet<String> merged = new TreeSet<>(writtenVariables);
        merged.addAll(Objects.requireNonNull(variables, "variables"));
        return new ConcurrentBranchFrame(parentFrontierId, splitId, joinId, activation, baselineVariables,
                selectedActivations, merged);
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }
}
