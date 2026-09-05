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
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.Objects;

/**
 * Operator-visible Outbox event metadata and delivery state.
 *
 * <p>Event payload and delivery lease authority are deliberately not exposed.</p>
 *
 * @param runId           owning Run
 * @param namespace       root Process namespace
 * @param processCode     root Process code
 * @param processVersion  exact Version selected at admission, or {@code null} when started from a definition
 * @param eventId         stable downstream deduplication identity
 * @param eventType       event category from the closed Durable Outbox vocabulary
 * @param occurrenceId    related Wait/Effect occurrence identity, or {@code null} for Run events
 * @param status          current delivery lifecycle
 * @param attemptCount    persisted delivery attempts
 * @param revision        current operator authority revision
 * @param availableAt     next delivery eligibility time
 * @param createdAt       event creation time
 * @param completedAt     delivery or abandonment time
 * @author yusu
 */
public record OutboxEvent(ProcessRunId runId, String namespace, String processCode, ProcessRef.Version processVersion,
        String eventId, String eventType, String occurrenceId, OutboxEventStatus status, int attemptCount, long revision,
        Instant availableAt, Instant createdAt, Instant completedAt) {
    public OutboxEvent {
        runId = Objects.requireNonNull(runId, "runId");
        namespace = ProcessIdentifiers.requireNamespace(namespace);
        processCode = ProcessIdentifiers.requireCode(processCode);
        if (processVersion != null
                && (!namespace.equals(processVersion.namespace()) || !processCode.equals(processVersion.code()))) {
            throw new IllegalArgumentException("processVersion must identify the same process");
        }
        eventId = DurableIdentifiers.requireIdentity(eventId, "eventId", 160);
        eventType = DurableIdentifiers.requireOutboxEventType(eventType);
        occurrenceId = DurableIdentifiers.optionalIdentity(occurrenceId, "occurrenceId", 160);
        boolean occurrenceEvent = "WAIT_COMMITTED".equals(eventType) || "EFFECT_REVIEW_REQUIRED".equals(eventType);
        if (occurrenceEvent != (occurrenceId != null)) {
            throw new IllegalArgumentException("occurrenceId is present exactly for occurrence events");
        }
        status = Objects.requireNonNull(status, "status");
        attemptCount = DurableNumbers.requireRange(attemptCount, 0, Integer.MAX_VALUE, "attemptCount");
        revision = DurableNumbers.requireNonNegative(revision, "revision");
        availableAt = Objects.requireNonNull(availableAt, "availableAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if ((status == OutboxEventStatus.DELIVERED || status == OutboxEventStatus.ABANDONED) != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must be present exactly for terminal Outbox state");
        }
        if (completedAt != null && completedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("completedAt must not be before createdAt");
        }
    }
}
