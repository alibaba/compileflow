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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One committed continuation: the Process-semantic resume position and every value needed to
 * continue from it.
 *
 * <p>The complete value is encoded into one opaque continuation envelope. The Store owns envelope
 * durability and fencing but deliberately does not interpret Engine resume coordinates, variables,
 * scopes, or any future multi-frontier representation. Frontier order is Process-semantic
 * round-robin scheduler state: a completed turn removes its active frontier and appends its
 * continuation, so encoding and decoding must preserve this order exactly.
 *
 * @author yusu
 */
public record ContinuationSnapshot(List<FrontierSnapshot> frontiers) {
    public static final int MAX_FRONTIERS = 256;

    public ContinuationSnapshot {
        List<FrontierSnapshot> ordered = Objects
            .requireNonNull(frontiers, "frontiers")
            .stream()
            .map(frontier -> Objects.requireNonNull(frontier, "frontier"))
            .toList();
        if (ordered.isEmpty() || ordered.size() > MAX_FRONTIERS) {
            throw new IllegalArgumentException("Continuation must contain 1.." + MAX_FRONTIERS + " logical frontiers");
        }
        LinkedHashSet<FrontierId> identities = new LinkedHashSet<>();
        for (FrontierSnapshot frontier : ordered) {
            if (!identities.add(frontier.frontierId())) {
                throw new IllegalArgumentException("Continuation frontier identities must be unique");
            }
        }
        validateMultiInstances(ordered);
        frontiers = ordered;
    }

    private static void validateMultiInstances(List<FrontierSnapshot> frontiers) {
        Map<FrontierId, FrontierSnapshot> byId =
                frontiers
            .stream()
            .collect(Collectors.toUnmodifiableMap(FrontierSnapshot::frontierId, frontier -> frontier));
        for (FrontierSnapshot candidate : frontiers) {
            if (candidate.branchFrames().isEmpty()) {
                continue;
            }
            BranchFrame top = candidate.branchFrames().get(candidate.branchFrames().size() - 1);
            if (!(top instanceof MultiInstanceBranchFrame branch)) {
                continue;
            }
            FrontierSnapshot parent = byId.get(branch.parentFrontierId());
            MultiInstanceState controller = parent == null ? null : parent.multiInstanceController();
            if (controller == null || !controller.loopId().equals(branch.loopId())
                    || !controller.activeIndices().contains(branch.index())) {
                throw new IllegalArgumentException(
                        "Parallel iteration must reference an active controller in the same continuation");
            }
        }
        for (FrontierSnapshot controllerFrontier : frontiers) {
            MultiInstanceState controller = controllerFrontier.multiInstanceController();
            if (controller == null) {
                continue;
            }
            LinkedHashSet<Integer> actual = new LinkedHashSet<>();
            for (FrontierSnapshot candidate : frontiers) {
                if (candidate.branchFrames().isEmpty()) {
                    continue;
                }
                BranchFrame top = candidate.branchFrames().get(candidate.branchFrames().size() - 1);
                if (!(top instanceof MultiInstanceBranchFrame branch)
                        || !branch.parentFrontierId().equals(controllerFrontier.frontierId())
                        || !branch.loopId().equals(controller.loopId())) {
                    continue;
                }
                int index = branch.index();
                if (candidate.scopeFrames().isEmpty()
                        || !(candidate.scopeFrames().get(candidate.scopeFrames().size() - 1) instanceof ParallelForEachFrame frame)
                        || !frame.loopId().equals(controller.loopId()) || frame.position() != index
                        || !actual.add(index)) {
                    throw new IllegalArgumentException("Parallel iteration frontier does not match its controller");
                }
            }
            if (!actual.equals(controller.activeIndices())) {
                throw new IllegalArgumentException("Parallel foreach active indices do not match its frontiers");
            }
        }
    }

    public ContinuationSnapshot(ResumePoint resumePoint, Map<String, Object> variables, List<ScopeFrame> scopeFrames) {
        this(List.of(FrontierSnapshot.root(resumePoint, variables, scopeFrames)));
    }

    public static ContinuationSnapshot start(Map<String, Object> variables) {
        return new ContinuationSnapshot(ResumePoint.start(), variables, List.of());
    }

    public static ContinuationSnapshot afterElement(String elementId, Map<String, Object> variables,
            List<ScopeFrame> scopeFrames) {
        return new ContinuationSnapshot(ResumePoint.afterElement(elementId), variables, scopeFrames);
    }

    public static ContinuationSnapshot beforeElement(String elementId, Map<String, Object> variables,
            List<ScopeFrame> scopeFrames) {
        return new ContinuationSnapshot(ResumePoint.beforeElement(elementId), variables, scopeFrames);
    }

    /**
     * Convenience for the single-frontier Process shape.
     */
    public FrontierSnapshot onlyFrontier() {
        if (frontiers.size() != 1) {
            throw new IllegalStateException("Continuation contains multiple logical frontiers");
        }
        return frontiers.get(0);
    }

    public ResumePoint resumePoint() {
        return onlyFrontier().resumePoint();
    }

    public Map<String, Object> variables() {
        return onlyFrontier().variables();
    }

    public List<ScopeFrame> scopeFrames() {
        return onlyFrontier().scopeFrames();
    }

    public boolean hasRunnableFrontier(int maxActiveIterations) {
        if (frontiers.stream().anyMatch(FrontierSnapshot::runnable)) {
            return true;
        }
        if (frontiers.size() >= MAX_FRONTIERS) {
            return false;
        }
        return frontiers
            .stream()
            .map(FrontierSnapshot::multiInstanceController)
            .filter(Objects::nonNull)
            .anyMatch(controller -> controller.canIssue(maxActiveIterations));
    }
}
