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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActiveWaitTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant DUE_AT = Instant.parse("2026-09-02T00:05:00Z");

    @Test
    void externalWaitMayExposeItsAutomaticExpiryTime() {
        ActiveWait wait = new ActiveWait("frontier", "approval", "approved", 1, CREATED_AT, DUE_AT);

        assertThat(wait.dueAt()).isEqualTo(DUE_AT);
    }

    @Test
    void externalWaitMayOmitItsEventSelector() {
        ActiveWait wait = new ActiveWait("frontier", "approval", null, 1, CREATED_AT, null);

        assertThat(wait.event()).isNull();
        assertThat(wait.dueAt()).isNull();
    }

    @Test
    void activeTimerRequiresDueTime() {
        assertThatThrownBy(() -> new ActiveTimer("frontier", "timer", 1, CREATED_AT, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("dueAt is required for an active Timer");
    }

    @Test
    void activeTimerHasNoWaitEventSelector() {
        ActiveTimer timer = new ActiveTimer("frontier", "timer", 2, CREATED_AT, DUE_AT);

        assertThat(timer.frontierId()).isEqualTo("frontier");
        assertThat(timer.dueAt()).isEqualTo(DUE_AT);
    }
}
