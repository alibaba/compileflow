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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiInstanceFrontierOperationsTest {
    private static final MultiInstanceFrontierOperations.CompletionTarget END = (state, frames, writes) -> "end";

    @Test
    void operationLevelIterationsUseAnUnambiguousBodyCoordinate() {
        List<String> items = List.of("A");
        FrontierSnapshot parent = FrontierSnapshot.root(ResumePoint.beforeElement("loop"),
                Map.of("slot", "", "results", List.of(), "items", items), List.of());

        List<FrontierSnapshot> frontiers = MultiInstanceFrontierOperations.start(List.of(), parent, "loop", "loop",
                "items", null, "slot", () -> "", items, 1);

        assertThat(frontiers)
            .filteredOn(frontier -> frontier.multiInstanceController() == null)
            .singleElement()
            .satisfies(iteration -> {
                assertThat(iteration.resumePoint()).isEqualTo(ResumePoint.beforeIterationBody("loop"));
                assertThat(iteration.runnable()).isTrue();
                assertThat(iteration.variables()).containsEntry("slot", "");
            });
    }

    @Test
    void orphanIterationCannotBecomeAnAuthoritativeContinuation() {
        MultiInstanceBranchFrame branch = new MultiInstanceBranchFrame(FrontierId.ROOT, "loop", 0);
        FrontierSnapshot orphan = new FrontierSnapshot(branch.frontierId(), ResumePoint.beforeElement("body"),
                Map.of("items", List.of("A"), "slot", "A", "results", List.of()),
                List.of(new ParallelForEachFrame("loop", 0, "A")), List.of(branch));

        assertThatThrownBy(() -> new ContinuationSnapshot(List.of(orphan)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active controller");
    }

    @Test
    void currentRuntimeBudgetControlsFutureIssuanceAfterRecovery() {
        List<Integer> items = List.of(0, 1, 2, 3);
        FrontierSnapshot parent = FrontierSnapshot.root(ResumePoint.beforeElement("loop"),
                Map.of("slot", -1, "results", List.of(), "items", items), List.of());
        List<FrontierSnapshot> frontiers = MultiInstanceFrontierOperations.start(List.of(), parent, "loop", "body",
                "items", null, "slot", () -> -1, items, 3);

        assertThat(controller(frontiers).activeIndices()).containsExactly(0, 1, 2);
        assertThat(controller(frontiers).canIssue(1)).isFalse();

        frontiers = completeFirst(frontiers, items, 1);
        assertThat(controller(frontiers).activeIndices()).containsExactly(1, 2);
        frontiers = completeFirst(frontiers, items, 1);
        assertThat(controller(frontiers).activeIndices()).containsExactly(2);
        frontiers = completeFirst(frontiers, items, 1);

        assertThat(controller(frontiers).activeIndices()).containsExactly(3);
        assertThat(frontiers).hasSize(2);
    }

    @Test
    void thousandItemsRemainWindowBoundedAndCollectByInputIndex() {
        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            items.add(index);
        }
        FrontierSnapshot parent = FrontierSnapshot.root(ResumePoint.beforeElement("loop"),
                Map.of("slot", -1, "results", List.of(), "items", items), List.of());
        List<FrontierSnapshot> frontiers = MultiInstanceFrontierOperations.start(List.of(), parent, "loop", "body",
                "items", null, "slot", () -> -1, items, 8);

        while (frontiers
            .stream()
            .anyMatch(frontier -> frontier.multiInstanceController() != null)) {
            assertThat(frontiers).hasSizeLessThanOrEqualTo(9);
            FrontierSnapshot iteration =
                    frontiers
                .stream()
                .filter(frontier -> frontier.multiInstanceController() == null)
                .findFirst()
                .orElseThrow();
            ParallelForEachFrame frame = (ParallelForEachFrame) iteration.scopeFrames().get(0);
            List<FrontierSnapshot> remaining = new ArrayList<>(frontiers);
            remaining.remove(iteration);
            frontiers = MultiInstanceFrontierOperations.completeIteration(remaining, iteration, "loop", "body", "items",
                    null, "slot", "results", () -> -1,
                    Map.of("slot", frame.position() * 2, "results", List.of(), "items", items), 8, END);
        }

        assertThat(frontiers)
            .singleElement()
            .satisfies(frontier -> {
                @SuppressWarnings("unchecked")
                List<Integer> results = (List<Integer>) frontier.variables().get("results");
                assertThat(results).hasSize(1_000);
                assertThat(results.get(0)).isZero();
                assertThat(results.get(999)).isEqualTo(1_998);
            });
    }

    private static MultiInstanceState controller(List<FrontierSnapshot> frontiers) {
        return frontiers
            .stream()
            .map(FrontierSnapshot::multiInstanceController)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElseThrow();
    }

    private static List<FrontierSnapshot> completeFirst(List<FrontierSnapshot> frontiers, List<Integer> items,
            int maxActive) {
        FrontierSnapshot iteration =
                frontiers
            .stream()
            .filter(frontier -> frontier.multiInstanceController() == null)
            .findFirst()
            .orElseThrow();
        ParallelForEachFrame frame = (ParallelForEachFrame) iteration.scopeFrames().get(0);
        List<FrontierSnapshot> remaining = new ArrayList<>(frontiers);
        remaining.remove(iteration);
        return MultiInstanceFrontierOperations.completeIteration(remaining, iteration, "loop", "body", "items", null,
                "slot", "results", () -> -1, Map.of("slot", frame.position(), "results", List.of(), "items", items),
                maxActive, END);
    }
}
