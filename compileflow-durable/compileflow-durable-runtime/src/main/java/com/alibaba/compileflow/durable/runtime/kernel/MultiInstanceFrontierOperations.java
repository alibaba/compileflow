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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Deterministic bounded issue/complete algebra for parallel collection iterations.
 *
 * @author yusu
 */
public final class MultiInstanceFrontierOperations {
    private MultiInstanceFrontierOperations() {
    }

    public static List<FrontierSnapshot> start(List<FrontierSnapshot> otherFrontiers, FrontierSnapshot parent,
            String loopId, String bodyStartId, String collectionVariable, String collectionOwnerIterationId,
            String outputSourceVariable, Supplier<?> outputSourceInitializer, List<?> snapshot, int maxActive) {
        FrontierSnapshot source = requireNonController(parent);
        List<?> frozen = collection(source, collectionVariable, collectionOwnerIterationId);
        if (!frozen.equals(Objects.requireNonNull(snapshot, "snapshot"))) {
            throw new IllegalArgumentException("Parallel foreach input differs from its frozen Process state");
        }
        MultiInstanceState controller = new MultiInstanceState(loopId, frozen.size(), outputSourceVariable != null);
        FrontierSnapshot parked = new FrontierSnapshot(source.frontierId(), ResumePoint.beforeElement(loopId),
                source.variables(), source.scopeFrames(), source.branchFrames(), controller);
        List<FrontierSnapshot> current = new ArrayList<>(Objects.requireNonNull(otherFrontiers, "otherFrontiers"));
        current.add(parked);
        return issueAvailable(current, parked, bodyStartId, collectionVariable, collectionOwnerIterationId,
                outputSourceVariable, outputSourceInitializer, maxActive);
    }

    public static boolean canIssue(ContinuationSnapshot continuation, FrontierSnapshot candidate, int maxActive) {
        Objects.requireNonNull(continuation, "continuation");
        MultiInstanceState controller = Objects.requireNonNull(candidate, "candidate").multiInstanceController();
        return controller != null && controller.canIssue(maxActive)
                && continuation.frontiers().size() < ContinuationSnapshot.MAX_FRONTIERS;
    }

    public static List<FrontierSnapshot> issueAvailable(List<FrontierSnapshot> frontiers,
            FrontierSnapshot controllerFrontier, String bodyStartId, String collectionVariable,
            String collectionOwnerIterationId, String outputSourceVariable, Supplier<?> outputSourceInitializer,
            int maxActive) {
        List<FrontierSnapshot> result = new ArrayList<>(Objects.requireNonNull(frontiers, "frontiers"));
        FrontierSnapshot parked = requireController(controllerFrontier);
        int controllerPosition = result.indexOf(parked);
        if (controllerPosition < 0) {
            throw new IllegalArgumentException("Parallel foreach controller is absent from the continuation");
        }
        MultiInstanceState controller = parked.multiInstanceController();
        List<?> frozen = collection(parked, collectionVariable, collectionOwnerIterationId);
        if (frozen.size() != controller.totalIterations()) {
            throw new IllegalArgumentException("Parallel foreach input size changed after scope admission");
        }
        while (controller.canIssue(maxActive) && result.size() < ContinuationSnapshot.MAX_FRONTIERS) {
            int index = controller.nextIndex();
            controller = controller.issueNext(maxActive);
            result.add(
                    iteration(parked, controller, index, frozen.get(index), bodyStartId, outputSourceVariable,
                            outputSourceInitializer));
        }
        result.set(controllerPosition,
                new FrontierSnapshot(parked.frontierId(), parked.resumePoint(), parked.variables(), parked.scopeFrames(),
                        parked.branchFrames(), controller));
        return List.copyOf(result);
    }

    public static List<FrontierSnapshot> completeIteration(List<FrontierSnapshot> remainingFrontiers,
            FrontierSnapshot iteration, String loopId, String bodyStartId, String collectionVariable,
            String collectionOwnerIterationId, String outputSourceVariable, String outputTargetVariable,
            Supplier<?> outputSourceInitializer, Map<String, Object> iterationState, int maxActive,
            CompletionTarget completionTarget) {
        FrontierSnapshot child = Objects.requireNonNull(iteration, "iteration");
        String loop = requireText(loopId, "loopId");
        MultiInstanceBranchFrame branch = iterationBranch(child, loop);
        ParallelForEachFrame itemFrame = iterationFrame(child, loop);
        List<FrontierSnapshot> result =
                new ArrayList<>(Objects.requireNonNull(remainingFrontiers, "remainingFrontiers"));
        int controllerPosition = -1;
        FrontierSnapshot parked = null;
        for (int index = 0; index < result.size(); index++) {
            FrontierSnapshot candidate = result.get(index);
            MultiInstanceState controller = candidate.multiInstanceController();
            if (candidate.frontierId().equals(branch.parentFrontierId()) && controller != null
                    && controller.loopId().equals(loop)) {
                if (parked != null) {
                    throw new IllegalArgumentException("Parallel foreach has duplicate controllers");
                }
                parked = candidate;
                controllerPosition = index;
            }
        }
        if (parked == null) {
            throw new IllegalArgumentException("Parallel foreach controller is missing");
        }
        Map<String, Object> completedState = Objects.requireNonNull(iterationState, "iterationState");
        MultiInstanceState updated = parked
            .multiInstanceController()
            .recordResult(itemFrame.position(),
                    outputSourceVariable == null ? null : completedState.get(outputSourceVariable));
        FrontierSnapshot updatedController = new FrontierSnapshot(parked.frontierId(), parked.resumePoint(),
                parked.variables(), parked.scopeFrames(), parked.branchFrames(), updated);
        result.set(controllerPosition, updatedController);
        if (updated.complete()) {
            result.remove(controllerPosition);
            Map<String, Object> merged = new LinkedHashMap<>(parked.variables());
            if (outputTargetVariable != null) {
                merged.put(outputTargetVariable, updated.orderedResults());
            }
            Set<String> writes = new LinkedHashSet<>();
            if (outputTargetVariable != null) {
                writes.add(outputTargetVariable);
            }
            List<ScopeFrame> resumedScopes = new ArrayList<>(parked.scopeFrames());
            String nextElementId = requireText(Objects
                        .requireNonNull(completionTarget, "completionTarget")
                        .apply(merged, resumedScopes, writes), "nextElementId");
            FrontierSnapshot resumed = ConcurrentFrontierOperations.progress(parked,
                    ResumePoint.beforeElement(nextElementId), merged, resumedScopes, writes);
            result.add(resumed);
            return List.copyOf(result);
        }
        return issueAvailable(result, updatedController, bodyStartId, collectionVariable, collectionOwnerIterationId,
                outputSourceVariable, outputSourceInitializer, maxActive);
    }

    private static FrontierSnapshot iteration(FrontierSnapshot parent, MultiInstanceState controller, int index,
            Object item, String bodyStartId, String outputSourceVariable, Supplier<?> outputSourceInitializer) {
        Map<String, Object> state = new LinkedHashMap<>(parent.variables());
        if (outputSourceVariable != null) {
            state.put(outputSourceVariable,
                    Objects.requireNonNull(outputSourceInitializer, "outputSourceInitializer").get());
        }
        MultiInstanceBranchFrame branch = new MultiInstanceBranchFrame(parent.frontierId(), controller.loopId(), index);
        List<BranchFrame> ancestry = new ArrayList<>(parent.branchFrames());
        ancestry.add(branch);
        List<ScopeFrame> scopes = new ArrayList<>(parent.scopeFrames());
        scopes.add(new ParallelForEachFrame(controller.loopId(), index, item));
        String body = requireText(bodyStartId, "bodyStartId");
        ResumePoint resumePoint =
                body.equals(controller.loopId())
                ? ResumePoint.beforeIterationBody(body)
                : ResumePoint.beforeElement(body);
        return new FrontierSnapshot(branch.frontierId(), resumePoint, state, scopes, ancestry);
    }

    private static MultiInstanceBranchFrame iterationBranch(FrontierSnapshot iteration, String loopId) {
        if (iteration.branchFrames().isEmpty()) {
            throw new IllegalArgumentException("Parallel iteration has no branch lineage");
        }
        BranchFrame top = iteration.branchFrames().get(iteration.branchFrames().size() - 1);
        if (!(top instanceof MultiInstanceBranchFrame branch) || !branch.loopId().equals(loopId)) {
            throw new IllegalArgumentException("Parallel iteration branch does not match its loop");
        }
        return branch;
    }

    private static ParallelForEachFrame iterationFrame(FrontierSnapshot iteration, String loopId) {
        if (iteration.scopeFrames().isEmpty()
                || !(iteration.scopeFrames().get(iteration.scopeFrames().size() - 1) instanceof ParallelForEachFrame frame)
                || !frame.loopId().equals(loopId)) {
            throw new IllegalArgumentException("Parallel iteration scope does not match its loop");
        }
        return frame;
    }

    private static FrontierSnapshot requireNonController(FrontierSnapshot frontier) {
        Objects.requireNonNull(frontier, "frontier");
        if (frontier.multiInstanceController() != null) {
            throw new IllegalArgumentException("A parallel controller cannot enter another scope directly");
        }
        return frontier;
    }

    private static FrontierSnapshot requireController(FrontierSnapshot frontier) {
        Objects.requireNonNull(frontier, "frontier");
        if (frontier.multiInstanceController() == null) {
            throw new IllegalArgumentException("Frontier is not a parallel foreach controller");
        }
        return frontier;
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }

    private static List<?> collection(FrontierSnapshot controller, String variableName,
            String collectionOwnerIterationId) {
        String name = requireText(variableName, "collectionVariable");
        Object value = collectionOwnerIterationId == null
                ? controller.variables().get(name)
                : ownerCollection(controller, collectionOwnerIterationId);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Parallel foreach collection must be a non-empty visible List: " + name);
        }
        return list;
    }

    private static Object ownerCollection(FrontierSnapshot controller, String collectionOwnerIterationId) {
        String owner = requireText(collectionOwnerIterationId, "collectionOwnerIterationId");
        ScopeFrame ownerFrame = controller
            .scopeFrames()
            .stream()
            .filter(frame -> frame.loopId().equals(owner))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                    "Parallel foreach collection owner is not active: " + collectionOwnerIterationId));
        if (ownerFrame instanceof ForEachFrame frame) {
            return frame.currentValue();
        }
        if (ownerFrame instanceof ParallelForEachFrame frame) {
            return frame.currentValue();
        }
        throw new IllegalArgumentException(
                "Parallel foreach collection owner is not active: " + collectionOwnerIterationId);
    }

    @FunctionalInterface
    public interface CompletionTarget {
        String apply(Map<String, Object> state, List<ScopeFrame> frames, Set<String> writes);
    }
}
