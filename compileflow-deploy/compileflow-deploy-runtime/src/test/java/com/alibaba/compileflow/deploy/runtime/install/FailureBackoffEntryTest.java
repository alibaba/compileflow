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
package com.alibaba.compileflow.deploy.runtime.install;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FailureBackoffEntryTest {
    @Test
    void usesMonotonicDeadlineForEligibilityAndRemainingDuration() {
        long nowNanos = 10_000L;
        long deadlineNanos = nowNanos + TimeUnit.MILLISECONDS.toNanos(250L);
        FailureBackoffEntry entry = new FailureBackoffEntry("resolution failed", 1_800_000_000_000L, deadlineNanos);

        assertThat(entry.isBlocked(nowNanos)).isTrue();
        assertThat(entry.remainingMillis(nowNanos)).isEqualTo(250L);
        assertThat(entry.isBlocked(deadlineNanos)).isFalse();
        assertThat(entry.remainingMillis(deadlineNanos)).isZero();
    }

    @Test
    void deadlineComparisonRemainsCorrectAcrossNanoTimeWraparound() {
        long nowNanos = Long.MAX_VALUE - 10L;
        long deadlineNanos = nowNanos + 20L;
        FailureBackoffEntry entry = new FailureBackoffEntry("resolution failed", 1_800_000_000_000L, deadlineNanos);

        assertThat(entry.isBlocked(nowNanos)).isTrue();
        assertThat(entry.remainingMillis(nowNanos)).isEqualTo(1L);
        assertThat(entry.isBlocked(nowNanos + 21L)).isFalse();
    }
}
