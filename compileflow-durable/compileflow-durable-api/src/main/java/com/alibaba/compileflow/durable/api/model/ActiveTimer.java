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
package com.alibaba.compileflow.durable.api.model;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.time.Instant;
import java.util.Objects;

/**
 * Operator-visible metadata for one active Timer occurrence.
 *
 * @param frontierId stable logical continuation frontier
 * @param elementId stable process element identity
 * @param occurrenceSequence monotonic occurrence
 * @param createdAt creation time
 * @param dueAt scheduled firing time
 * @author yusu
 */
public record ActiveTimer(String frontierId, String elementId, long occurrenceSequence, Instant createdAt,
        Instant dueAt) implements ActiveWork {
    public ActiveTimer {
        frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
        elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        occurrenceSequence = DurableNumbers.requirePositive(occurrenceSequence, "occurrenceSequence");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (dueAt == null) {
            throw new IllegalArgumentException("dueAt is required for an active Timer");
        }
    }
}
