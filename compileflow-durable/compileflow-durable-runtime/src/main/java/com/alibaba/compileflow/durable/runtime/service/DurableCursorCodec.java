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

import com.alibaba.compileflow.durable.api.model.ActiveWorkCursor;
import com.alibaba.compileflow.durable.api.model.OutboxEventCursor;
import com.alibaba.compileflow.durable.api.model.ProcessRunCursor;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineCursor;
import com.alibaba.compileflow.engine.ProcessText;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned encoding for public opaque cursors.
 *
 * @author yusu
 */
public final class DurableCursorCodec {
    private static final String RUN = "cf-cursor/v1/run";
    private static final String TIMELINE = "cf-cursor/v1/timeline";
    private static final String OUTBOX = "cf-cursor/v1/outbox";
    private static final String ACTIVE_WORK = "cf-cursor/v1/active-work";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private DurableCursorCodec() {
    }

    public static ProcessRunCursor run(Instant createdAt, ProcessRunId runId) {
        Instant time = Objects.requireNonNull(createdAt, "createdAt");
        ProcessRunId id = Objects.requireNonNull(runId, "runId");
        return new ProcessRunCursor(encode(RUN + '|' + time.getEpochSecond() + '|' + time.getNano() + '|' + id.value()));
    }

    public static RunKey run(ProcessRunCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String[] fields = decode(cursor.value(), RUN, 4);
        return new RunKey(instant(fields[1], fields[2], "Run"), new ProcessRunId(fields[3]));
    }

    static ProcessTimelineCursor timeline(long snapshotSequence, long lastSequence) {
        if (snapshotSequence <= 0 || lastSequence <= 0 || lastSequence > snapshotSequence) {
            throw new IllegalArgumentException("Invalid Timeline cursor state");
        }
        return new ProcessTimelineCursor(encode(TIMELINE + '|' + snapshotSequence + '|' + lastSequence));
    }

    static TimelineKey timeline(ProcessTimelineCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String[] fields = decode(cursor.value(), TIMELINE, 3);
        long snapshot = positiveLong(fields[1], "Timeline snapshot sequence");
        long last = positiveLong(fields[2], "Timeline last sequence");
        if (last > snapshot) {
            throw new IllegalArgumentException("Invalid Timeline cursor");
        }
        return new TimelineKey(snapshot, last);
    }

    static OutboxEventCursor outbox(Instant createdAt, UUID eventId) {
        Instant time = Objects.requireNonNull(createdAt, "createdAt");
        UUID id = Objects.requireNonNull(eventId, "eventId");
        return new OutboxEventCursor(encode(OUTBOX + '|' + time.getEpochSecond() + '|' + time.getNano() + '|' + id));
    }

    static OutboxKey outbox(OutboxEventCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String[] fields = decode(cursor.value(), OUTBOX, 4);
        try {
            UUID eventId = UUID.fromString(fields[3]);
            if (!eventId.toString().equals(fields[3])) {
                throw new IllegalArgumentException("Invalid Outbox cursor");
            }
            return new OutboxKey(instant(fields[1], fields[2], "Outbox"), eventId);
        } catch (IllegalArgumentException failure) {
            throw invalid("Outbox", failure);
        }
    }

    static ActiveWorkCursor activeWork(long occurrenceSequence) {
        if (occurrenceSequence <= 0) {
            throw new IllegalArgumentException("Invalid active-work cursor state");
        }
        return new ActiveWorkCursor(encode(ACTIVE_WORK + '|' + occurrenceSequence));
    }

    static long activeWork(ActiveWorkCursor cursor) {
        if (cursor == null) {
            return 0;
        }
        String[] fields = decode(cursor.token(), ACTIVE_WORK, 2);
        return positiveLong(fields[1], "active-work occurrence sequence");
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decode(String value, String type, int count) {
        try {
            byte[] bytes = DECODER.decode(value);
            if (!ENCODER.encodeToString(bytes).equals(value)) {
                throw new IllegalArgumentException("Non-canonical encoding");
            }
            String[] fields = ProcessText.decodeUtf8(bytes, "Cursor").split("\\|", -1);
            if (fields.length != count || !type.equals(fields[0])) {
                throw new IllegalArgumentException("Unexpected cursor type or shape");
            }
            return fields;
        } catch (IllegalArgumentException failure) {
            throw invalid(type.substring(type.lastIndexOf('/') + 1), failure);
        }
    }

    private static Instant instant(String seconds, String nanos, String name) {
        try {
            long epochSecond = Long.parseLong(seconds);
            int nano = Integer.parseInt(nanos);
            return Instant.ofEpochSecond(epochSecond, nano);
        } catch (NumberFormatException | DateTimeException failure) {
            throw invalid(name, failure);
        }
    }

    private static long positiveLong(String source, String name) {
        try {
            long value = Long.parseLong(source);
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid " + name, failure);
        }
    }

    private static IllegalArgumentException invalid(String type, RuntimeException failure) {
        return new IllegalArgumentException("Invalid " + type + " cursor", failure);
    }

    public record RunKey(Instant createdAt, ProcessRunId runId) {}

    record TimelineKey(long snapshotSequence, long lastSequence) {}

    record OutboxKey(Instant createdAt, UUID eventId) {}
}
