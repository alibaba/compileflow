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

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.util.List;
import java.util.Objects;

/**
 * One immutable page from a frozen retained Run Timeline.
 *
 * @param runId            exact retained Run
 * @param snapshotSequence inclusive frozen Journal upper bound
 * @param items            ordered redaction-safe events
 * @param nextCursor       typed continuation cursor, or {@code null}
 * @author yusu
 */
public record ProcessTimelinePage(ProcessRunId runId, long snapshotSequence, List<ProcessTimelineEvent> items,
        ProcessTimelineCursor nextCursor) {
    public ProcessTimelinePage {
        runId = Objects.requireNonNull(runId, "runId");
        long upperBound = DurableNumbers.requirePositive(snapshotSequence, "snapshotSequence");
        snapshotSequence = upperBound;
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items
            .stream()
            .anyMatch(event -> event.sequence() > upperBound)) {
            throw new IllegalArgumentException("items must not exceed snapshotSequence");
        }
    }
}
