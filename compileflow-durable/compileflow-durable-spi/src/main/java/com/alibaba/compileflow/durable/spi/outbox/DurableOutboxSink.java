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
package com.alibaba.compileflow.durable.spi.outbox;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic external-delivery adapter for authenticated, at-least-once Durable events.
 *
 * <p>This SPI is the lowest common denominator for delivery to an independent external authority;
 * it is not a universal Durable message bus or an internal Run-to-Run protocol. A destination may
 * strengthen the guarantee when it durably deduplicates by {@link OutboundEvent#eventId()}, but the
 * Kernel does not promise exactly-once external delivery or side effects.
 *
 * <p>Delivery order is not guaranteed, including between events of the same Run. Consumers that
 * need current state must read the authoritative Run. A Wait token is a bearer capability and is
 * present only for WAIT_COMMITTED; sinks must never log it, use it as a metric label, place it in an
 * browser-visible URL or third-party metadata, or send it over an unauthenticated channel. Long-lived
 * integration storage must protect it as a credential.
 *
 * <p>Implementations must be thread-safe because deliveries may overlap. External I/O must use a
 * bounded deadline and preserve interruption. The application or dependency-injection container
 * owns the sink lifecycle; Durable workers never close it.
 *
 * @author yusu
 */
public interface DurableOutboxSink {
    /**
     * Delivers one immutable logical event to an authenticated external delivery boundary.
     *
     * <p>The same event ID, type, occurrence, and logically equivalent payload may be presented more than once.
     * A normal return asserts that the destination boundary has <em>durably accepted</em> the event;
     * merely placing it in a process-local buffer or scheduling an incomplete asynchronous send is
     * not success. The implementation must either durably deduplicate acceptance by {@link
     * OutboundEvent#eventId()} or propagate that ID unchanged so a downstream consumer can
     * deduplicate it.
     *
     * <p>A timeout or interruption cannot prove that the destination did not accept the event. The
     * publisher may therefore retry the same committed fact, including after a sink returned
     * successfully but the process died before the Kernel committed DELIVERED. Implementations
     * should honor thread interruption and must make that replay safe. Exactly-once external side
     * effects are outside this contract.
     *
     * @param event immutable event and optional bearer capability to deliver
     * @throws Exception when durable acceptance was not confirmed
     */
    void deliver(OutboundEvent event) throws Exception;

    /**
     * One immutable outbound event whose string representation redacts its payload.
     *
     * @param eventId          stable downstream deduplication identity across every retry
     * @param runId            owning Durable Run
     * @param namespace        root Process namespace
     * @param processCode      root Process code
     * @param processVersion   exact Version selected at admission, or {@code null} when started from a definition
     * @param eventType        event category from the closed Durable Outbox vocabulary
     * @param occurrenceId     related Wait/Effect occurrence identity, or {@code null} for Run events
     * @param payload          detached logical Kernel event payload, stable across every retry
     */
    record OutboundEvent(UUID eventId, ProcessRunId runId, String namespace, String processCode,
            ProcessRef.Version processVersion, String eventType, UUID occurrenceId, Map<String, Object> payload) {
        public OutboundEvent {
            eventId = Objects.requireNonNull(eventId, "eventId");
            runId = Objects.requireNonNull(runId, "runId");
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            processCode = ProcessIdentifiers.requireCode(processCode);
            if (processVersion != null
                    && (!namespace.equals(processVersion.namespace()) || !processCode.equals(processVersion.code()))) {
                throw new IllegalArgumentException("processVersion must identify the same process");
            }
            eventType = DurableIdentifiers.requireOutboxEventType(eventType);
            boolean occurrenceEvent = "WAIT_COMMITTED".equals(eventType) || "EFFECT_REVIEW_REQUIRED".equals(eventType);
            if (occurrenceEvent == (occurrenceId == null)) {
                throw new IllegalArgumentException("occurrenceId is present exactly for occurrence events");
            }
            payload = DurablePayload.immutablePayload(Objects.requireNonNull(payload, "payload"), "payload");
        }

        @Override
        public String toString() {
            return "OutboundEvent{eventId='" + eventId + "', runId=" + runId + ", namespace=" + namespace
                    + ", processCode=" + processCode + ", eventType='" + eventType + "', occurrenceId=" + occurrenceId
                    + ", payload=<redacted>}";
        }
    }
}
