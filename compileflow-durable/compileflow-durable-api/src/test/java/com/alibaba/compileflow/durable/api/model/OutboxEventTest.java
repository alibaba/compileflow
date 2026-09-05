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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:01Z");

    @Test
    void rejectsCompletionBeforeCreation() {
        assertThatThrownBy(() -> event(CREATED_AT.minusSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("completedAt must not be before createdAt");
    }

    @Test
    void acceptsCompletionAtCreation() {
        event(CREATED_AT);
    }

    private static OutboxEvent event(Instant completedAt) {
        ProcessRef.Version process = new ProcessRef.Version("default", "order", "v1");
        return new OutboxEvent(new ProcessRunId("00000000-0000-0000-0000-000000000001"), process.namespace(),
                process.code(), process, "event-1", "RUN_SUCCEEDED", null, OutboxEventStatus.DELIVERED, 1, 0, CREATED_AT,
                CREATED_AT, completedAt);
    }
}
