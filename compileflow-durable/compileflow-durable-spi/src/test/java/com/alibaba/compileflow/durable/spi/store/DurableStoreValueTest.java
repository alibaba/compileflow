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
package com.alibaba.compileflow.durable.spi.store;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableStoreValueTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void programLoadDemandPageRejectsInvalidKeysetState() {
        assertThatThrownBy(() -> new DurableStore.ProcessRuntimeDemandPage(List.of(FIRST, FIRST), FIRST))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicates");
        assertThatThrownBy(() -> new DurableStore.ProcessRuntimeDemandPage(List.of(FIRST, SECOND), FIRST))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("last Process");
        assertThatThrownBy(() -> new DurableStore.ProcessRuntimeDemandPage(List.of(), FIRST))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-empty page");
    }

    @Test
    void timerResultRequiresMonotonicTimes() {
        Instant scheduled = Instant.parse("2026-01-01T00:00:00Z");
        Instant due = scheduled.plusSeconds(1);
        Instant resolved = due.plusSeconds(1);

        assertThatThrownBy(() -> timerResult(due, scheduled, resolved))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dueAt");
        assertThatThrownBy(() -> timerResult(scheduled, resolved, due))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resolvedAt");
    }

    @Test
    void turnCannotConsumeAndIssueTheSameOccurrence() {
        DurableStore.OccurrenceKey occurrence =
                new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT, FIRST);
        DurableStore.EffectCommit effect = new DurableStore.EffectCommit(occurrence, 1, FIRST, 0, "frontier", "effect",
                EffectRecoveryPlan.manual(), new DurableStore.Envelope(new byte[] {1}));

        assertThatThrownBy(() -> new DurableStore.TurnCommit(List.of(occurrence), List.of(effect),
                new DurableStore.WaitingTurn(new DurableStore.Envelope(new byte[] {2}))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("consumed and issued");
    }

    private static DurableStore.TimerResult timerResult(Instant scheduledAt, Instant dueAt, Instant resolvedAt) {
        return new DurableStore.TimerResult(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER, FIRST), 1,
                FIRST, 0, "frontier", "timer", new DurableStore.Envelope(new byte[] {1}), scheduledAt, dueAt, resolvedAt);
    }
}
