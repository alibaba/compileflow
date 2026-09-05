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
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Committed result used to resume one exact occurrence.
 *
 * @author yusu
 */
public sealed interface BoundaryCompletion
        permits BoundaryCompletion.WaitCompleted, BoundaryCompletion.WaitExpired, BoundaryCompletion.TimerFired,
        BoundaryCompletion.EffectSucceeded, BoundaryCompletion.ProcessReturned {
    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }

    record WaitExpired(long occurrenceSequence, String boundaryId, Instant deadline, Instant expiredAt)
            implements BoundaryCompletion {
        public WaitExpired {
            if (occurrenceSequence <= 0) {
                throw new IllegalArgumentException("occurrenceSequence must be positive");
            }
            boundaryId = requireText(boundaryId, "boundaryId");
            deadline = Objects.requireNonNull(deadline, "deadline");
            expiredAt = Objects.requireNonNull(expiredAt, "expiredAt");
            if (expiredAt.isBefore(deadline)) {
                throw new IllegalArgumentException("Wait cannot expire before its deadline");
            }
        }

        @Override
        public BoundaryKind kind() {
            return BoundaryKind.WAIT;
        }
    }

    long occurrenceSequence();

    String boundaryId();

    BoundaryKind kind();

    record WaitCompleted(long occurrenceSequence, String boundaryId, String event, Map<String, Object> payload)
            implements BoundaryCompletion {
        public WaitCompleted {
            if (occurrenceSequence <= 0) {
                throw new IllegalArgumentException("occurrenceSequence must be positive");
            }
            boundaryId = requireText(boundaryId, "boundaryId");
            event = ProcessIdentifiers.optionalEvent(event);
            payload = DurableValueSnapshots.immutableMap(Objects.requireNonNull(payload, "payload"));
        }

        @Override
        public BoundaryKind kind() {
            return BoundaryKind.WAIT;
        }

        @Override
        public String toString() {
            return "WaitCompleted{occurrenceSequence=" + occurrenceSequence + ", boundaryId=" + boundaryId + ", event="
                    + (event == null ? "none" : "<present>") + ", payload=<redacted>}";
        }
    }

    record TimerFired(long occurrenceSequence, String boundaryId, Instant scheduledAt, Instant wakeAt, Instant firedAt)
            implements BoundaryCompletion {
        public TimerFired {
            if (occurrenceSequence <= 0) {
                throw new IllegalArgumentException("occurrenceSequence must be positive");
            }
            boundaryId = requireText(boundaryId, "boundaryId");
            scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
            wakeAt = Objects.requireNonNull(wakeAt, "wakeAt");
            firedAt = Objects.requireNonNull(firedAt, "firedAt");
            if (firedAt.isBefore(wakeAt) || firedAt.isBefore(scheduledAt)) {
                throw new IllegalArgumentException("Timer cannot fire before scheduling or wakeAt");
            }
        }

        @Override
        public BoundaryKind kind() {
            return BoundaryKind.TIMER;
        }

        @Override
        public String toString() {
            return "TimerFired{occurrenceSequence=" + occurrenceSequence + ", boundaryId=" + boundaryId
                    + ", scheduledAt=" + scheduledAt + ", wakeAt=" + wakeAt + ", firedAt=" + firedAt + "}";
        }
    }

    record EffectSucceeded(long occurrenceSequence, String boundaryId, Map<String, Object> output)
            implements BoundaryCompletion {
        public EffectSucceeded {
            if (occurrenceSequence <= 0) {
                throw new IllegalArgumentException("occurrenceSequence must be positive");
            }
            boundaryId = requireText(boundaryId, "boundaryId");
            output = DurableValueSnapshots.immutableMap(Objects.requireNonNull(output, "output"));
        }

        @Override
        public BoundaryKind kind() {
            return BoundaryKind.EFFECT;
        }

        @Override
        public String toString() {
            return "EffectSucceeded{occurrenceSequence=" + occurrenceSequence + ", boundaryId=" + boundaryId
                    + ", output=<redacted>}";
        }
    }

    record ProcessReturned(long occurrenceSequence, String boundaryId, Map<String, Object> output)
            implements BoundaryCompletion {
        public ProcessReturned {
            requireOccurrenceSequence(occurrenceSequence);
            boundaryId = requireText(boundaryId, "boundaryId");
            output = DurableValueSnapshots.immutableMap(Objects.requireNonNull(output, "output"));
        }

        @Override
        public BoundaryKind kind() {
            return BoundaryKind.PROCESS_CALL;
        }

        @Override
        public String toString() {
            return "ProcessReturned{occurrenceSequence=" + occurrenceSequence + ", boundaryId=" + boundaryId
                    + ", output=<redacted>}";
        }
    }

    private static void requireOccurrenceSequence(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("occurrenceSequence must be positive");
        }
    }
}
