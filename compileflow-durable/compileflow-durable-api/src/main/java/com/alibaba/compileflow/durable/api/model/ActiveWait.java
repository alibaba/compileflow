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
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.time.Instant;
import java.util.Objects;

/**
 * Operator-visible metadata for one active external Wait. The authority token is never exposed.
 *
 * @param frontierId       stable logical continuation frontier
 * @param elementId        stable process element identity
 * @param event            event selector
 * @param occurrenceSequence monotonic occurrence
 * @param createdAt        creation time
 * @param dueAt            automatic resolution time, or {@code null} when no deadline exists
 * @author yusu
 */
public record ActiveWait(String frontierId, String elementId, String event, long occurrenceSequence, Instant createdAt,
        Instant dueAt) implements ActiveWork {
    public ActiveWait {
        frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
        elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        event = ProcessIdentifiers.optionalEvent(event);
        occurrenceSequence = DurableNumbers.requirePositive(occurrenceSequence, "occurrenceSequence");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
