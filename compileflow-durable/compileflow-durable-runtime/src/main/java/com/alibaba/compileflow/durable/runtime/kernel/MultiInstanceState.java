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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable coordinator state for one bounded parallel foreach scope.
 *
 * @author yusu
 */
public final class MultiInstanceState {
    public static final int ABSOLUTE_MAX_ACTIVE = 64;
    private final String loopId;
    private final int totalIterations;
    private final List<Object> results;
    private final Set<Integer> activeIndices;
    private final Set<Integer> completedIndices;
    private final int nextIndex;

    public MultiInstanceState(String loopId, int totalIterations) {
        this(loopId, totalIterations, emptyResults(totalIterations), Set.of(), Set.of(), 0);
    }

    public MultiInstanceState(String loopId, int totalIterations, boolean collectResults) {
        this(loopId, totalIterations, collectResults ? emptyResults(totalIterations) : List.of(), Set.of(), Set.of(), 0);
    }

    public MultiInstanceState(String loopId, int totalIterations, List<?> results, Set<Integer> activeIndices,
            Set<Integer> completedIndices, int nextIndex) {
        this.loopId = requireText(loopId);
        this.totalIterations = totalIterations;
        this.results = DurableValueSnapshots.immutableList(Objects.requireNonNull(results, "results"));
        this.activeIndices = orderedIndices(activeIndices, "activeIndices");
        this.completedIndices = orderedIndices(completedIndices, "completedIndices");
        this.nextIndex = nextIndex;
        validate();
    }

    private void validate() {
        if (totalIterations <= 0) {
            throw new IllegalArgumentException("Parallel foreach state requires at least one iteration");
        }
        if (!results.isEmpty() && results.size() != totalIterations) {
            throw new IllegalArgumentException("Parallel foreach result slots must match totalIterations");
        }
        if (nextIndex < 0 || nextIndex > totalIterations) {
            throw new IllegalArgumentException("nextIndex is outside totalIterations");
        }
        if (activeIndices.size() > ABSOLUTE_MAX_ACTIVE || !Collections.disjoint(activeIndices, completedIndices)) {
            throw new IllegalArgumentException("Parallel foreach active/completed iteration sets are invalid");
        }
        LinkedHashSet<Integer> issued = new LinkedHashSet<>(activeIndices);
        issued.addAll(completedIndices);
        if (issued.size() != nextIndex) {
            throw new IllegalArgumentException("Issued parallel iterations must partition active and completed sets");
        }
        for (int index = 0; index < nextIndex; index++) {
            if (!issued.contains(index)) {
                throw new IllegalArgumentException("Parallel iteration issuance must be a contiguous input prefix");
            }
        }
        for (Integer index : issued) {
            if (index < 0 || index >= nextIndex) {
                throw new IllegalArgumentException("Parallel iteration index is outside the issued prefix");
            }
        }
    }

    public String loopId() {
        return loopId;
    }

    public int totalIterations() {
        return totalIterations;
    }

    public int nextIndex() {
        return nextIndex;
    }

    public Set<Integer> activeIndices() {
        return activeIndices;
    }

    public Set<Integer> completedIndices() {
        return completedIndices;
    }

    public boolean canIssue(int maxActive) {
        requireMaxActive(maxActive);
        return nextIndex < totalIterations && activeIndices.size() < maxActive;
    }

    public boolean complete() {
        return nextIndex == totalIterations && activeIndices.isEmpty() && completedIndices.size() == totalIterations;
    }

    public List<Object> orderedResults() {
        if (!complete()) {
            throw new IllegalStateException("Parallel foreach results are incomplete");
        }
        if (results.isEmpty()) {
            throw new IllegalStateException("Parallel foreach does not collect results");
        }
        return DurableValueSnapshots.immutableList(results);
    }

    public MultiInstanceState issueNext(int maxActive) {
        if (!canIssue(maxActive)) {
            throw new IllegalStateException("Parallel foreach cannot issue another iteration");
        }
        LinkedHashSet<Integer> active = new LinkedHashSet<>(activeIndices);
        active.add(nextIndex);
        return new MultiInstanceState(loopId, totalIterations, results, active, completedIndices, nextIndex + 1);
    }

    public MultiInstanceState recordResult(int index, Object result) {
        if (!activeIndices.contains(index)) {
            throw new IllegalArgumentException("Parallel iteration is not active: " + index);
        }
        List<Object> updatedResults = results;
        if (!results.isEmpty()) {
            updatedResults = new ArrayList<>(results);
            updatedResults.set(index, DurableValueSnapshots.detachedValue(result));
        }
        LinkedHashSet<Integer> active = new LinkedHashSet<>(activeIndices);
        active.remove(index);
        LinkedHashSet<Integer> completed = new LinkedHashSet<>(completedIndices);
        completed.add(index);
        return new MultiInstanceState(loopId, totalIterations, updatedResults, active, completed, nextIndex);
    }

    /**
     * Returns immutable result slots for the runtime-owned persistence codec.
     */
    public List<Object> resultsForEncoding() {
        return results;
    }

    private static List<Object> emptyResults(int totalIterations) {
        if (totalIterations <= 0) {
            throw new IllegalArgumentException("totalIterations must be positive");
        }
        return new ArrayList<>(Collections.nCopies(totalIterations, null));
    }

    private static Set<Integer> orderedIndices(Set<Integer> source, String name) {
        TreeSet<Integer> ordered = new TreeSet<>();
        for (Integer index : Objects.requireNonNull(source, name)) {
            ordered.add(Objects.requireNonNull(index, name + " entry"));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "loopId", 128);
    }

    private static void requireMaxActive(int maxActive) {
        if (maxActive <= 0 || maxActive > ABSOLUTE_MAX_ACTIVE) {
            throw new IllegalArgumentException("maxActive must be in [1, " + ABSOLUTE_MAX_ACTIVE + ']');
        }
    }

    @Override
    public String toString() {
        return "MultiInstanceState[loopId=" + loopId + ", size=" + totalIterations + ", nextIndex=" + nextIndex
                + ", active=" + activeIndices.size() + ", completed=" + completedIndices.size() + ']';
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof MultiInstanceState other)) {
            return false;
        }
        return nextIndex == other.nextIndex && loopId.equals(other.loopId) && totalIterations == other.totalIterations
                && results.equals(other.results) && activeIndices.equals(other.activeIndices)
                && completedIndices.equals(other.completedIndices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loopId, totalIterations, results, activeIndices, completedIndices, nextIndex);
    }
}
