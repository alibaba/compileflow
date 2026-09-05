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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic fork/progress/join algebra for serialized logical frontiers.
 *
 * @author yusu
 */
public final class ConcurrentFrontierOperations {
    private ConcurrentFrontierOperations() {
    }

    public static List<FrontierSnapshot> fork(FrontierSnapshot parent, String splitId, String joinId,
            List<BranchActivation> selectedActivations) {
        FrontierSnapshot source = Objects.requireNonNull(parent, "parent");
        String split = requireText(splitId, "splitId");
        String join = requireText(joinId, "joinId");
        List<BranchActivation> branches =
                BranchActivation.immutableSorted(selectedActivations, "selectedActivations", 1, 256);
        List<FrontierSnapshot> children = new ArrayList<>(branches.size());
        for (BranchActivation branch : branches) {
            ConcurrentBranchFrame frame = new ConcurrentBranchFrame(source.frontierId(), split, join, branch,
                    source.variables(), new LinkedHashSet<>(branches));
            List<BranchFrame> ancestry = new ArrayList<>(source.branchFrames());
            ancestry.add(frame);
            children.add(
                    new FrontierSnapshot(frame.frontierId(), ResumePoint.beforeElement(branch.branchStartId()),
                            source.variables(), source.scopeFrames(), ancestry));
        }
        return List.copyOf(children);
    }

    /**
     * Records exact Process-variable writes while moving one frontier to its next coordinate.
     */
    public static FrontierSnapshot progress(FrontierSnapshot frontier, ResumePoint resumePoint,
            Map<String, Object> variables, List<ScopeFrame> scopeFrames, Set<String> writtenVariables) {
        FrontierSnapshot source = Objects.requireNonNull(frontier, "frontier");
        Map<String, Object> state = Objects.requireNonNull(variables, "variables");
        Set<String> writes = Set.copyOf(Objects.requireNonNull(writtenVariables, "writtenVariables"));
        if (!state.keySet().containsAll(writes)) {
            throw new IllegalArgumentException("A frontier cannot record writes absent from its Process state");
        }
        List<BranchFrame> ancestry = new ArrayList<>(source.branchFrames());
        if (!ancestry.isEmpty() && !writes.isEmpty()) {
            int top = ancestry.size() - 1;
            if (ancestry.get(top) instanceof ConcurrentBranchFrame concurrent) {
                ancestry.set(top, concurrent.recordWrites(writes));
            }
        }
        return new FrontierSnapshot(source.frontierId(), resumePoint, state, scopeFrames, ancestry);
    }

    public static FrontierSnapshot parkAtJoin(FrontierSnapshot frontier, String joinId, Map<String, Object> variables,
            List<ScopeFrame> scopeFrames, Set<String> writtenVariables) {
        if (frontier.branchFrames().isEmpty()) {
            throw new IllegalArgumentException("Only a structured concurrent branch may park at a join");
        }
        ConcurrentBranchFrame branch = top(frontier);
        if (!branch.joinId().equals(requireText(joinId, "joinId"))) {
            throw new IllegalArgumentException("Branch arrived at a join other than its declared convergence");
        }
        return progress(frontier, ResumePoint.atJoin(joinId), variables, scopeFrames, writtenVariables);
    }

    /**
     * Merges one complete sibling group in stable branch order.
     *
     * <p>Each branch reads the split snapshot in isolation. Exact write sets, including external
     * Wait result keys, are persisted in branch ancestry. Disjoint writes merge deterministically;
     * two branches writing the same Process variable fail closed.</p>
     */
    public static FrontierSnapshot join(List<FrontierSnapshot> siblings, String joinId, String nextElementId) {
        String convergence = requireText(joinId, "joinId");
        String next = requireText(nextElementId, "nextElementId");
        List<FrontierSnapshot> ordered = Objects
            .requireNonNull(siblings, "siblings")
            .stream()
            .map(sibling -> Objects.requireNonNull(sibling, "sibling"))
            .sorted(Comparator.comparing(FrontierSnapshot::frontierId))
            .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("A join requires at least one selected branch");
        }
        ConcurrentBranchFrame owner = top(ordered.get(0));
        List<BranchFrame> parentAncestry = prefix(ordered.get(0));
        List<ScopeFrame> parentScopes = ordered.get(0).scopeFrames();
        Map<String, Object> merged = new LinkedHashMap<>(owner.baselineVariables());
        Map<String, FrontierId> writers = new LinkedHashMap<>();
        Set<String> mergedWrites = new LinkedHashSet<>();
        Set<BranchActivation> arrivedBranches = new LinkedHashSet<>();
        for (FrontierSnapshot sibling : ordered) {
            ConcurrentBranchFrame branch = top(sibling);
            validateSibling(sibling, branch, owner, parentAncestry, parentScopes, convergence);
            if (!arrivedBranches.add(branch.activation())) {
                throw new IllegalArgumentException("A structured join received the same branch more than once");
            }
            for (String variable : branch.writtenVariables()) {
                FrontierId previous = writers.putIfAbsent(variable, sibling.frontierId());
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Concurrent branches wrote the same Process variable '" + variable + "'");
                }
                if (!sibling.variables().containsKey(variable) || !merged.containsKey(variable)) {
                    throw new IllegalArgumentException("Concurrent branch write is outside the Process state schema");
                }
                merged.put(variable, sibling.variables().get(variable));
                mergedWrites.add(variable);
            }
        }
        if (!arrivedBranches.equals(owner.selectedActivations())) {
            throw new IllegalArgumentException("Structured join is missing one or more selected branches");
        }
        if (!parentAncestry.isEmpty() && !mergedWrites.isEmpty()) {
            int parentTop = parentAncestry.size() - 1;
            if (parentAncestry.get(parentTop) instanceof ConcurrentBranchFrame concurrent) {
                parentAncestry.set(parentTop, concurrent.recordWrites(mergedWrites));
            }
        }
        return new FrontierSnapshot(owner.parentFrontierId(), ResumePoint.beforeElement(next), merged, parentScopes,
                parentAncestry);
    }

    /**
     * Returns a new frontier set with this complete sibling group merged, or unchanged while siblings remain.
     */
    public static List<FrontierSnapshot> mergeReadyGroup(List<FrontierSnapshot> frontiers, FrontierSnapshot member,
            String nextElementId) {
        List<FrontierSnapshot> current = List.copyOf(Objects.requireNonNull(frontiers, "frontiers"));
        FrontierSnapshot arrival = Objects.requireNonNull(member, "member");
        if (!arrival.resumePoint().isAtJoin()) {
            throw new IllegalArgumentException("Join group member must be parked at a join");
        }
        ConcurrentBranchFrame owner = top(arrival);
        List<FrontierSnapshot> siblings = current
            .stream()
            .filter(frontier -> belongsTo(frontier, owner))
            .toList();
        Set<BranchActivation> directBranches = new LinkedHashSet<>();
        for (FrontierSnapshot sibling : siblings) {
            directBranches.add(top(sibling).activation());
        }
        if (!directBranches.equals(owner.selectedActivations())
                || siblings.stream().anyMatch(sibling -> !sibling.resumePoint().equals(arrival.resumePoint()))) {
            return current;
        }
        FrontierSnapshot merged = join(siblings, arrival.resumePoint().elementId(), nextElementId);
        List<FrontierSnapshot> result = new ArrayList<>(current.size() - siblings.size() + 1);
        result.addAll(current);
        result.removeAll(siblings);
        result.add(merged);
        return List.copyOf(result);
    }

    private static boolean belongsTo(FrontierSnapshot candidate, ConcurrentBranchFrame owner) {
        if (candidate.branchFrames().isEmpty()) {
            return false;
        }
        ConcurrentBranchFrame branch = top(candidate);
        return branch.parentFrontierId().equals(owner.parentFrontierId()) && branch.splitId().equals(owner.splitId())
                && branch.joinId().equals(owner.joinId())
                && branch.selectedActivations().equals(owner.selectedActivations());
    }

    private static void validateSibling(FrontierSnapshot sibling, ConcurrentBranchFrame branch,
            ConcurrentBranchFrame owner, List<BranchFrame> expectedParentAncestry, List<ScopeFrame> expectedScopes,
            String joinId) {
        if (!sibling.resumePoint().equals(ResumePoint.atJoin(joinId))) {
            throw new IllegalArgumentException("Every sibling must be parked at the same structured join");
        }
        if (!branch.parentFrontierId().equals(owner.parentFrontierId()) || !branch.splitId().equals(owner.splitId())
                || !branch.joinId().equals(owner.joinId())
                || !branch.selectedActivations().equals(owner.selectedActivations())) {
            throw new IllegalArgumentException("Join siblings must belong to one split occurrence");
        }
        if (!prefix(sibling).equals(expectedParentAncestry)) {
            throw new IllegalArgumentException("Join siblings must have identical parent ancestry");
        }
        if (!sameScopeCoordinates(sibling.scopeFrames(), expectedScopes)) {
            throw new IllegalArgumentException("Join siblings must restore the same parent scope coordinates");
        }
        if (!sibling.variables().keySet().equals(owner.baselineVariables().keySet())
                || !branch.baselineVariables().keySet().equals(owner.baselineVariables().keySet())) {
            throw new IllegalArgumentException("Join sibling state does not match its split baseline schema");
        }
    }

    private static boolean sameScopeCoordinates(List<ScopeFrame> actual, List<ScopeFrame> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < actual.size(); index++) {
            ScopeFrame left = actual.get(index);
            ScopeFrame right = expected.get(index);
            if (!left.getClass().equals(right.getClass()) || !left.loopId().equals(right.loopId())
                    || left.position() != right.position()) {
                return false;
            }
        }
        return true;
    }

    private static ConcurrentBranchFrame top(FrontierSnapshot frontier) {
        if (frontier.branchFrames().isEmpty()) {
            throw new IllegalArgumentException("Join frontier has no concurrent branch ancestry");
        }
        BranchFrame top = frontier.branchFrames().get(frontier.branchFrames().size() - 1);
        if (!(top instanceof ConcurrentBranchFrame concurrent)) {
            throw new IllegalArgumentException("Frontier is not in a structured concurrent branch");
        }
        return concurrent;
    }

    private static List<BranchFrame> prefix(FrontierSnapshot frontier) {
        return new ArrayList<>(frontier.branchFrames().subList(0, frontier.branchFrames().size() - 1));
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }
}
