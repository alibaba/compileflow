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

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Declarative timer schedule produced by a replayable frontier step.
 *
 * <p>An {@link ScheduleKind#AFTER} duration is converted to an absolute
 * {@code wakeAt} by the Store using database time in the boundary commit
 * transaction. Generated programs never read a machine clock.</p>
 *
 * @author yusu
 */
public record TimerRequest(String boundaryId, ScheduleKind scheduleKind, Duration duration, Instant wakeAt) {
    private static final Duration MAX_DELAY = Duration.ofDays(36500);

    public TimerRequest {
        boundaryId = requireText(boundaryId);
        scheduleKind = Objects.requireNonNull(scheduleKind, "scheduleKind");
        if (scheduleKind == ScheduleKind.AFTER) {
            duration = Objects.requireNonNull(duration, "duration");
            if (wakeAt != null) {
                throw new IllegalArgumentException("AFTER Timer requires a duration only");
            }
            duration = DurableNumbers.requireDurationMillis(duration, MAX_DELAY, "AFTER Timer duration");
        } else if (wakeAt == null || duration != null) {
            throw new IllegalArgumentException("AT Timer requires an absolute wakeAt only");
        }
    }

    public static TimerRequest after(String boundaryId, Duration duration) {
        return new TimerRequest(boundaryId, ScheduleKind.AFTER, duration, null);
    }

    public static TimerRequest at(String boundaryId, Instant wakeAt) {
        return new TimerRequest(boundaryId, ScheduleKind.AT, null, wakeAt);
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "boundaryId", 128);
    }

    public enum ScheduleKind {
        AFTER,
        AT
    }
}
