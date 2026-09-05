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
package com.alibaba.compileflow.durable.runtime.service;

import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ActiveWorkCursor;
import com.alibaba.compileflow.durable.api.model.ActiveWorkPage;
import com.alibaba.compileflow.durable.api.model.ActiveWorkQuery;
import com.alibaba.compileflow.durable.api.model.ActiveWork;
import com.alibaba.compileflow.durable.api.model.OutboxEventCursor;
import com.alibaba.compileflow.durable.api.model.OutboxEventPage;
import com.alibaba.compileflow.durable.api.model.OutboxEventQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.ProcessRunCursor;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunPage;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineCursor;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineEventCode;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineEvent;
import com.alibaba.compileflow.durable.api.model.ProcessTimelinePage;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineQuery;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fail-closed validation and mapping of first-party Store results.
 *
 * @author yusu
 */
public final class DurableStoreResultMapper {
    private DurableStoreResultMapper() {
    }

    public static ProcessRun requireRun(ProcessRun result, ProcessRunId runId) {
        ProcessRun value = Objects.requireNonNull(result, "Store returned null Run");
        if (!runId.equals(value.runId())) {
            throw mismatch("Store returned a different Run");
        }
        return value;
    }

    public static ProcessRun requireRun(ProcessRun result, ProcessRunId runId, DurableStore.RunProcess process) {
        ProcessRun value = requireRun(result, runId);
        if (!process.namespace().equals(value.namespace()) || !process.processCode().equals(value.processCode())
                || !Objects.equals(process.processVersion(), value.processVersion())) {
            throw mismatch("Store returned a different Run Process identity");
        }
        return value;
    }

    public static Optional<ProcessRun> requireRunLookup(Optional<ProcessRun> result, ProcessRunId runId) {
        return Objects
            .requireNonNull(result, "Store returned null Run lookup")
            .map(value -> requireRun(value, runId));
    }

    public static Optional<DurableStore.RunResultProjection> requireRunResultLookup(
            Optional<DurableStore.RunResultProjection> result, ProcessRunId runId) {
        return Objects
            .requireNonNull(result, "Store returned null Run result lookup")
            .map(value -> {
                if (!runId.equals(value.runId())) {
                    throw mismatch("Store returned a result for a different Run");
                }
                return value;
            });
    }

    public static DurableStore.WaitTarget requireWaitTarget(Optional<DurableStore.WaitTarget> result,
            String tokenDigest) {
        DurableStore.WaitTarget value = Objects
            .requireNonNull(result, "Store returned null Wait target lookup")
            .orElseThrow(() -> DurableProcessException.of(DurableErrorCode.INVALID_WAIT_TOKEN,
                    "Wait authority token does not exist"));
        if (!tokenDigest.equals(value.tokenDigest())) {
            throw mismatch("Store returned a different Wait authority token");
        }
        return value;
    }

    static DurableStore.EffectTarget requireEffectTarget(DurableStore.EffectTarget result, ProcessRunId runId,
            UUID effectId) {
        DurableStore.EffectTarget value = Objects.requireNonNull(result, "Store returned null Effect target");
        if (!runId.equals(value.runId()) || !effectId.equals(value.effectId())) {
            throw mismatch("Store returned a different Effect occurrence");
        }
        return value;
    }

    static Optional<OutboxEvent> requireOutboxLookup(Optional<OutboxEvent> result, ProcessRunId runId, UUID eventId) {
        return Objects
            .requireNonNull(result, "Store returned null Outbox lookup")
            .map(value -> requireOutbox(value, runId, eventId));
    }

    static OutboxEvent requireOutbox(OutboxEvent result, UUID eventId) {
        OutboxEvent value = Objects.requireNonNull(result, "Store returned null Outbox event");
        if (!eventId.equals(eventId(value))) {
            throw mismatch("Store returned a different Outbox event");
        }
        return value;
    }

    static OutboxEvent requireOutbox(OutboxEvent result, ProcessRunId runId, UUID eventId) {
        OutboxEvent value = requireOutbox(result, eventId);
        if (!runId.equals(value.runId())) {
            throw mismatch("Store returned an Outbox event owned by a different Run");
        }
        return value;
    }

    public static ProcessRunPage runPage(ProcessRunQuery query, DurableStore.RunPage result) {
        ProcessRunQuery request = Objects.requireNonNull(query, "query");
        DurableStore.RunPage page = Objects.requireNonNull(result, "Store returned null Run page");
        List<ProcessRun> items = page.items();
        if (items.size() > request.limit()) {
            throw mismatch("Store exceeded the requested Run page limit");
        }
        DurableCursorCodec.RunKey requestedCursor = DurableCursorCodec.run(request.cursor());
        ProcessRun previous = null;
        for (ProcessRun item : items) {
            ProcessRun value = Objects.requireNonNull(item, "Store returned null Run page item");
            requireRunFilters(request, value);
            requireBeforeRunCursor(requestedCursor, value);
            if (previous != null && compareRunKey(previous, value) <= 0) {
                throw mismatch("Store returned Runs outside descending keyset order");
            }
            previous = value;
        }
        ProcessRunCursor next =
                page.nextRunId() == null ? null : DurableCursorCodec.run(page.nextCreatedAt(), page.nextRunId());
        DurableCursorCodec.RunKey nextKey = DurableCursorCodec.run(next);
        if (next != null
                && (previous == null || !nextKey.createdAt().equals(previous.createdAt())
                || !nextKey.runId().equals(previous.runId()))) {
            throw mismatch("Store returned a Run cursor that does not match the last item");
        }
        return new ProcessRunPage(items, next);
    }

    static OutboxEventPage outboxPage(OutboxEventQuery query, DurableStore.OutboxPage result) {
        OutboxEventQuery request = Objects.requireNonNull(query, "query");
        DurableStore.OutboxPage page = Objects.requireNonNull(result, "Store returned null Outbox page");
        List<OutboxEvent> items = page.items();
        if (items.size() > request.limit()) {
            throw mismatch("Store exceeded the requested Outbox page limit");
        }
        DurableCursorCodec.OutboxKey requestedCursor = DurableCursorCodec.outbox(request.cursor());
        OutboxEvent previous = null;
        for (OutboxEvent item : items) {
            OutboxEvent value = Objects.requireNonNull(item, "Store returned null Outbox page item");
            UUID eventId = eventId(value);
            if (!request.statuses().isEmpty() && !request.statuses().contains(value.status())) {
                throw mismatch("Store returned an Outbox event outside the requested status filter");
            }
            if (request.namespace() != null && !request.namespace().equals(value.namespace())) {
                throw mismatch("Store returned an Outbox event outside the requested namespace filter");
            }
            if (request.code() != null && !request.code().equals(value.processCode())) {
                throw mismatch("Store returned an Outbox event outside the requested code filter");
            }
            requireBeforeOutboxCursor(requestedCursor, value, eventId);
            if (previous != null && compareOutboxKey(previous, value) <= 0) {
                throw mismatch("Store returned Outbox events outside descending keyset order");
            }
            previous = value;
        }
        OutboxEventCursor next =
                page.nextEventId() == null ? null : DurableCursorCodec.outbox(page.nextCreatedAt(), page.nextEventId());
        DurableCursorCodec.OutboxKey nextKey = DurableCursorCodec.outbox(next);
        if (next != null
                && (previous == null || !nextKey.createdAt().equals(previous.createdAt())
                || !nextKey.eventId().equals(eventId(previous)))) {
            throw mismatch("Store returned an Outbox cursor that does not match the last item");
        }
        return new OutboxEventPage(items, next);
    }

    static ProcessTimelinePage timelinePage(ProcessTimelineQuery query, DurableStore.TimelinePage result) {
        ProcessTimelineQuery request = Objects.requireNonNull(query, "query");
        DurableStore.TimelinePage page = Objects.requireNonNull(result, "Store returned null Timeline page");
        DurableCursorCodec.TimelineKey requestedCursor = DurableCursorCodec.timeline(request.cursor());
        long expectedSnapshot = requestedCursor == null ? page.snapshotSequence() : requestedCursor.snapshotSequence();
        if (page.snapshotSequence() <= 0 || page.snapshotSequence() != expectedSnapshot) {
            throw mismatch("Store changed the frozen Timeline snapshot");
        }
        if (page.items().size() > request.limit()) {
            throw mismatch("Store exceeded the requested Timeline page limit");
        }
        long after = requestedCursor == null ? 0 : requestedCursor.lastSequence();
        long previous = after;
        List<ProcessTimelineEvent> items = new ArrayList<>(page.items().size());
        for (DurableStore.JournalFact fact : page.items()) {
            DurableStore.JournalFact value = Objects.requireNonNull(fact, "Store returned null Timeline item");
            if (value.sequence() <= previous) {
                throw mismatch("Store returned Timeline facts outside ascending sequence order");
            }
            if (value.sequence() > page.snapshotSequence()) {
                throw mismatch("Store returned a Timeline fact beyond the frozen snapshot");
            }
            previous = value.sequence();
            items.add(
                    new ProcessTimelineEvent(value.sequence(), new ProcessTimelineEventCode(value.type()),
                            value.occurredAt()));
        }
        Long nextSequence = page.nextSequence();
        if (nextSequence != null
                && (items.isEmpty() || nextSequence.longValue() != items.get(items.size() - 1).sequence())) {
            throw mismatch("Store returned a Timeline cursor that does not match the last item");
        }
        if (nextSequence != null && nextSequence.longValue() >= page.snapshotSequence()) {
            throw mismatch("Store returned a Timeline cursor at the end of the frozen snapshot");
        }
        ProcessTimelineCursor next =
                nextSequence == null ? null : DurableCursorCodec.timeline(page.snapshotSequence(), nextSequence);
        return new ProcessTimelinePage(request.runId(), page.snapshotSequence(), items, next);
    }

    static ActiveWorkPage activeWorkPage(ActiveWorkQuery query, DurableStore.ActiveWorkPage result) {
        ActiveWorkQuery request = Objects.requireNonNull(query, "query");
        DurableStore.ActiveWorkPage page = Objects.requireNonNull(result, "Store returned null active-work page");
        if (page.items().size() > request.limit()) {
            throw mismatch("Store exceeded the requested active-work page limit");
        }
        long previous = DurableCursorCodec.activeWork(request.cursor());
        for (ActiveWork item : page.items()) {
            ActiveWork value = Objects.requireNonNull(item, "Store returned null active-work item");
            if (value.occurrenceSequence() <= previous) {
                throw mismatch("Store returned active work outside ascending occurrence order");
            }
            previous = value.occurrenceSequence();
        }
        Long nextSequence = page.nextSequence();
        if (nextSequence != null
                && (page.items().isEmpty()
                || nextSequence.longValue() != page.items().get(page.items().size() - 1).occurrenceSequence())) {
            throw mismatch("Store returned an active-work cursor that does not match the last item");
        }
        ActiveWorkCursor next = nextSequence == null ? null : DurableCursorCodec.activeWork(nextSequence);
        return new ActiveWorkPage(page.items(), next);
    }

    private static void requireRunFilters(ProcessRunQuery query, ProcessRun value) {
        if (query.namespace() != null && !query.namespace().equals(value.namespace())) {
            throw mismatch("Store returned a Run outside the requested namespace filter");
        }
        if (query.code() != null && !query.code().equals(value.processCode())) {
            throw mismatch("Store returned a Run outside the requested code filter");
        }
        if (!query.statuses().isEmpty() && !query.statuses().contains(value.status())) {
            throw mismatch("Store returned a Run outside the requested status filter");
        }
    }

    private static void requireBeforeRunCursor(DurableCursorCodec.RunKey cursor, ProcessRun value) {
        if (cursor != null && compareRunKey(value.createdAt(), value.runId(), cursor.createdAt(), cursor.runId()) >= 0) {
            throw mismatch("Store returned a Run outside the requested keyset window");
        }
    }

    private static void requireBeforeOutboxCursor(DurableCursorCodec.OutboxKey cursor, OutboxEvent value, UUID eventId) {
        if (cursor != null && compareOutboxKey(value.createdAt(), eventId, cursor.createdAt(), cursor.eventId()) >= 0) {
            throw mismatch("Store returned an Outbox event outside the requested keyset window");
        }
    }

    private static int compareRunKey(ProcessRun left, ProcessRun right) {
        return compareRunKey(left.createdAt(), left.runId(), right.createdAt(), right.runId());
    }

    private static int compareRunKey(Instant leftTime, ProcessRunId leftId, Instant rightTime, ProcessRunId rightId) {
        int time = leftTime.compareTo(rightTime);
        return time != 0 ? time : leftId.value().compareTo(rightId.value());
    }

    private static int compareOutboxKey(OutboxEvent left, OutboxEvent right) {
        return compareOutboxKey(left.createdAt(), eventId(left), right.createdAt(), eventId(right));
    }

    private static int compareOutboxKey(Instant leftTime, UUID leftId, Instant rightTime, UUID rightId) {
        int time = leftTime.compareTo(rightTime);
        return time != 0 ? time : leftId.toString().compareTo(rightId.toString());
    }

    private static UUID eventId(OutboxEvent value) {
        String source = value.eventId();
        UUID parsed;
        try {
            parsed = UUID.fromString(source);
        } catch (IllegalArgumentException failure) {
            throw mismatch("Store returned a non-UUID Outbox event identity");
        }
        if (!parsed.toString().equals(source)) {
            throw mismatch("Store returned a non-canonical Outbox event identity");
        }
        return parsed;
    }

    private static IllegalStateException mismatch(String message) {
        return new IllegalStateException(message);
    }
}
