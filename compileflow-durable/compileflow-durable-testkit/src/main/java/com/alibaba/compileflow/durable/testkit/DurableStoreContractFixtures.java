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
package com.alibaba.compileflow.durable.testkit;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class DurableStoreContractFixtures {
    static final UUID PROCESS_ID = UUID.fromString("00000000-0000-8000-8000-000000000001");
    static final long ROOT_INVOCATION_ID = 0;

    private DurableStoreContractFixtures() {
    }

    static DurableStore.TurnCommit waitTurn(UUID waitId, long occurrenceSequence, String elementId, String event,
            String tokenDigest, DurableStore.Envelope continuation, DurableStore.Envelope outboxPayload) {
        return new DurableStore.TurnCommit(List.of(),
                List.of(
                        new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                        waitId), occurrenceSequence, PROCESS_ID, ROOT_INVOCATION_ID, "root", elementId,
                                event, tokenDigest, null, outboxPayload)), new DurableStore.WaitingTurn(continuation));
    }

    static DurableStore.TurnCommit waitDeadlineTurn(UUID waitId, long occurrenceSequence, String elementId, String event,
            String tokenDigest, Duration deadlineAfter, DurableStore.Envelope continuation,
            DurableStore.Envelope outboxPayload) {
        return new DurableStore.TurnCommit(List.of(),
                List.of(
                        new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                        waitId), occurrenceSequence, PROCESS_ID, ROOT_INVOCATION_ID, "root", elementId,
                                event, tokenDigest, deadlineAfter, outboxPayload)),
                new DurableStore.WaitingTurn(continuation));
    }

    static DurableStore.TurnCommit timerTurn(UUID waitId, long occurrenceSequence, String elementId, Duration delay,
            Instant dueAt, DurableStore.Envelope continuation) {
        return new DurableStore.TurnCommit(List.of(),
                List.of(
                        new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                                        waitId), occurrenceSequence, PROCESS_ID, ROOT_INVOCATION_ID, "root", elementId,
                                delay, dueAt)), new DurableStore.WaitingTurn(continuation));
    }

    static DurableStore.TurnCommit effectTurn(UUID effectId, long occurrenceSequence, String elementId,
            DurableStore.Envelope input, DurableStore.Envelope continuation) {
        return effectTurn(effectId, occurrenceSequence, elementId, input, continuation, EffectRecoveryPlan.manual());
    }

    static DurableStore.TurnCommit effectTurn(UUID effectId, long occurrenceSequence, String elementId,
            DurableStore.Envelope input, DurableStore.Envelope continuation, EffectRecoveryPlan recoveryPlan) {
        return new DurableStore.TurnCommit(List.of(),
                List.of(
                        new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                        effectId), occurrenceSequence, PROCESS_ID, ROOT_INVOCATION_ID, "root", elementId,
                                recoveryPlan, input)), new DurableStore.WaitingTurn(continuation));
    }

    static DurableStore.TurnCommit runnableTurn(DurableStore.Envelope continuation) {
        return new DurableStore.TurnCommit(List.of(), List.of(), new DurableStore.RunnableTurn(continuation));
    }

    static DurableStore.TurnCommit succeededTurn(DurableStore.Envelope result) {
        return new DurableStore.TurnCommit(List.of(), List.of(), new DurableStore.SucceededTurn(result));
    }

    static DurableStore.TurnCommit failedTurn(String code, String message) {
        return new DurableStore.TurnCommit(List.of(), List.of(), new DurableStore.FailedTurn(code, message));
    }

    static DurableStore.TurnCommit consumeClaimedResults(DurableStore.RunClaim claim, DurableStore.TurnCommit commit) {
        return new DurableStore.TurnCommit(claim
                    .occurrenceResults()
                    .stream()
                    .map(DurableStore.OccurrenceResult::occurrence)
                    .toList(), commit.issuedOccurrences(), commit.state());
    }

    static DurableStore.Envelope envelope(String value) {
        return new DurableStore.Envelope(bytes(value));
    }

    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
