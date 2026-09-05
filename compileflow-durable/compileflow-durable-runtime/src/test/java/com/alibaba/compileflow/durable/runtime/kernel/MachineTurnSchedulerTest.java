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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachineTurnSchedulerTest {
    @Test
    void selectsInPersistedOrderAndPreservesTheRemainingOrder() {
        FrontierSnapshot first = branch("first", ResumePoint.beforeElement("left"));
        FrontierSnapshot second = branch("second", ResumePoint.beforeElement("right"));

        MachineTurnScheduler.Selection selection =
                MachineTurnScheduler.select(new ContinuationSnapshot(List.of(first, second)), List.of(), 8);

        assertThat(selection.frontier()).isEqualTo(first);
        assertThat(selection.occurrenceResult()).isNull();
        assertThat(selection.remainingFrontiers()).containsExactly(second);
    }

    @Test
    void selectsTheWaitingFrontierWhoseCommittedResultWasOffered() {
        FrontierSnapshot waiting = branch("waiting", ResumePoint.afterElement("approval"));
        FrontierSnapshot parked = branch("parked", ResumePoint.atJoin("join"));
        OccurrenceResult result = resolved(waiting.frontierId(), "approval");

        MachineTurnScheduler.Selection selection =
                MachineTurnScheduler.select(new ContinuationSnapshot(List.of(parked, waiting)), List.of(result), 8);

        assertThat(selection.frontier()).isEqualTo(waiting);
        assertThat(selection.occurrenceResult()).isSameAs(result);
        assertThat(selection.remainingFrontiers()).containsExactly(parked);
    }

    @Test
    void rejectsAmbiguousOrUnmatchedOccurrenceResults() {
        FrontierSnapshot waiting = branch("waiting", ResumePoint.afterElement("approval"));
        OccurrenceResult result = resolved(waiting.frontierId(), "approval");
        ContinuationSnapshot continuation = new ContinuationSnapshot(List.of(waiting));

        assertThatThrownBy(() -> MachineTurnScheduler.select(continuation, List.of(result, result), 8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multiple occurrence results");
        assertThatThrownBy(() -> MachineTurnScheduler.select(continuation,
                List.of(resolved(FrontierId.branch(FrontierId.ROOT, "split", "other"), "approval")), 8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown frontier");
    }

    private static FrontierSnapshot branch(String branchId, ResumePoint resumePoint) {
        Set<BranchActivation> selected = Set.of(new BranchActivation(0, "first"), new BranchActivation(1, "second"),
                new BranchActivation(2, "waiting"), new BranchActivation(3, "parked"));
        BranchActivation activation =
                selected
            .stream()
            .filter(value -> value.branchStartId().equals(branchId))
            .findFirst()
            .orElseThrow();
        ConcurrentBranchFrame frame =
                new ConcurrentBranchFrame(FrontierId.ROOT, "split", "join", activation, Map.of(), selected);
        return new FrontierSnapshot(frame.frontierId(), resumePoint, Map.of(), List.of(), List.of(frame));
    }

    private static OccurrenceResult resolved(FrontierId frontierId, String boundaryId) {
        BoundaryCompletion completion = new BoundaryCompletion.WaitCompleted(1, boundaryId, "approved", Map.of());
        return new OccurrenceResult(new OccurrenceKey(BoundaryKind.WAIT, UUID.randomUUID()), frontierId, completion);
    }
}
