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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConcurrentFrontierOperationsTest {
    @Test
    void forkAndJoinUseStableIdentitySnapshotIsolationAndDisjointWriteMerge() {
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("fork"),
                Map.of("left", 0, "right", 0, "unchanged", 7), List.of());

        List<FrontierSnapshot> branches =
                ConcurrentFrontierOperations.fork(root, "fork", "join", List.of(branch(1, "right"), branch(0, "left")));

        assertThat(branches)
            .extracting(frontier -> frontier.resumePoint().elementId())
            .containsExactly("left", "right");
        FrontierSnapshot left = ConcurrentFrontierOperations.parkAtJoin(branches.get(0), "join",
                Map.of("left", 1, "right", 0, "unchanged", 7), List.of(), Set.of("left"));
        FrontierSnapshot right = ConcurrentFrontierOperations.parkAtJoin(branches.get(1), "join",
                Map.of("left", 0, "right", 2, "unchanged", 7), List.of(), Set.of("right"));

        FrontierSnapshot joined = ConcurrentFrontierOperations.join(List.of(right, left), "join", "after");

        assertThat(joined.frontierId()).isEqualTo(FrontierId.ROOT);
        assertThat(joined.resumePoint()).isEqualTo(ResumePoint.beforeElement("after"));
        assertThat(joined.variables()).containsExactlyInAnyOrderEntriesOf(Map.of("left", 1, "right", 2, "unchanged", 7));
        assertThat(joined.branchFrames()).isEmpty();
    }

    @Test
    void joinFailsClosedWhenTwoBranchesWriteTheSameProcessVariable() {
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("fork"), Map.of("value", 0), List.of());
        List<FrontierSnapshot> branches =
                ConcurrentFrontierOperations.fork(root, "fork", "join", List.of(branch(0, "a"), branch(1, "b")));
        FrontierSnapshot first = ConcurrentFrontierOperations.parkAtJoin(branches.get(0), "join", Map.of("value", 1),
                List.of(), Set.of("value"));
        FrontierSnapshot second = ConcurrentFrontierOperations.parkAtJoin(branches.get(1), "join", Map.of("value", 2),
                List.of(), Set.of("value"));

        assertThatThrownBy(() -> ConcurrentFrontierOperations.join(List.of(first, second), "join", "after"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same Process variable 'value'");
    }

    @Test
    void branchCannotParkAtAJoinOutsideItsDeclaredConvergence() {
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("fork"), Map.of("value", 0), List.of());
        FrontierSnapshot branch =
                ConcurrentFrontierOperations.fork(root, "fork", "declaredJoin", List.of(branch(0, "branch"))).get(0);

        assertThatThrownBy(() -> ConcurrentFrontierOperations.parkAtJoin(branch, "differentJoin", branch.variables(),
                branch.scopeFrames(), Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared convergence");
    }

    @Test
    void nestedJoinRestoresItsParentFrontierAndPropagatesExactWritesToTheOuterJoin() {
        Map<String, Object> baseline = Map.of("a", 0, "b", 0, "c", 0, "d", 0);
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("outer"), baseline, List.of());
        List<FrontierSnapshot> outer = ConcurrentFrontierOperations.fork(root, "outer", "outer-join",
                List.of(branch(0, "left"), branch(1, "right")));
        FrontierSnapshot leftBeforeNested = ConcurrentFrontierOperations.progress(outer.get(0),
                ResumePoint.beforeElement("inner"), Map.of("a", 1, "b", 0, "c", 0, "d", 0), List.of(), Set.of("a"));
        List<FrontierSnapshot> inner = ConcurrentFrontierOperations.fork(leftBeforeNested, "inner", "inner-join",
                List.of(branch(0, "inner-a"), branch(1, "inner-b")));
        FrontierSnapshot innerA = ConcurrentFrontierOperations.parkAtJoin(inner.get(0), "inner-join",
                Map.of("a", 1, "b", 2, "c", 0, "d", 0), List.of(), Set.of("b"));
        FrontierSnapshot innerB = ConcurrentFrontierOperations.parkAtJoin(inner.get(1), "inner-join",
                Map.of("a", 1, "b", 0, "c", 3, "d", 0), List.of(), Set.of("c"));
        FrontierSnapshot restoredLeft =
                ConcurrentFrontierOperations.join(List.of(innerB, innerA), "inner-join", "outer-join");
        restoredLeft = ConcurrentFrontierOperations.parkAtJoin(restoredLeft, "outer-join", restoredLeft.variables(),
                List.of(), Set.of());
        FrontierSnapshot right = ConcurrentFrontierOperations.parkAtJoin(outer.get(1), "outer-join",
                Map.of("a", 0, "b", 0, "c", 0, "d", 4), List.of(), Set.of("d"));

        FrontierSnapshot joined =
                ConcurrentFrontierOperations.join(new ArrayList<>(List.of(right, restoredLeft)), "outer-join", "after");

        assertThat(joined.variables()).containsExactlyInAnyOrderEntriesOf(Map.of("a", 1, "b", 2, "c", 3, "d", 4));
        assertThat(joined.frontierId()).isEqualTo(FrontierId.ROOT);
    }

    @Test
    void duplicateBodyTargetsRemainDistinctActivations() {
        FrontierSnapshot root = FrontierSnapshot.root(ResumePoint.beforeElement("fork"), Map.of(), List.of());

        List<FrontierSnapshot> branches =
                ConcurrentFrontierOperations.fork(root, "fork", "join",
                        List.of(branch(0, "shared"), branch(1, "shared")));

        assertThat(branches).hasSize(2).extracting(FrontierSnapshot::frontierId).doesNotHaveDuplicates();
        assertThat(branches)
            .extracting(value -> value.resumePoint().elementId())
            .containsExactly("shared", "shared");
    }

    private static BranchActivation branch(int ordinal, String target) {
        return new BranchActivation(ordinal, target);
    }
}
