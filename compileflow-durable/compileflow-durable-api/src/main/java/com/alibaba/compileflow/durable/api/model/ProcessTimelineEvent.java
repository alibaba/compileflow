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
import java.time.Instant;
import java.util.Objects;

/**
 * Payload-blind timeline event derived from one append-only Journal fact.
 *
 * @param sequence retained Journal sequence
 * @param code stable event code
 * @param recordedAt authority timestamp
 *
 * @author yusu
 */
public record ProcessTimelineEvent(long sequence, ProcessTimelineEventCode code, Instant recordedAt) {
    public ProcessTimelineEvent {
        sequence = DurableNumbers.requirePositive(sequence, "sequence");
        code = Objects.requireNonNull(code, "code");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
