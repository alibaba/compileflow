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
package com.alibaba.compileflow.durable.runtime.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurableOutboxPublisherOptionsTest {
    private static DurableOutboxPublisherOptions options(Duration initialDelay, Duration maxDelay) {
        return new DurableOutboxPublisherOptions("worker-1", initialDelay, maxDelay, 3);
    }

    @Test
    void computesABoundedExponentialRetryWindow() {
        DurableOutboxPublisherOptions options =
                new DurableOutboxPublisherOptions("worker-1", Duration.ofSeconds(1), Duration.ofMinutes(1), 100);

        assertThat(options.initialRetryDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.maxRetryDelay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(options.maxAttempts()).isEqualTo(100);
        assertThat(options.retryCeilingMillis(1)).isEqualTo(1_000L);
        assertThat(options.retryCeilingMillis(2)).isEqualTo(2_000L);
        assertThat(options.retryCeilingMillis(20)).isEqualTo(60_000L);
    }

    @Test
    void rejectsMissingZeroOrExcessiveRetryDelay() {
        assertThatThrownBy(() -> options(null, Duration.ofSeconds(2)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("initialRetryDelay");
        assertThatThrownBy(() -> options(Duration.ZERO, Duration.ofSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("initialRetryDelay must be positive");
        assertThatThrownBy(() -> options(Duration.ofNanos(1), Duration.ofSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> options(Duration.ofMillis(1).plusNanos(1), Duration.ofSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> options(Duration.ofSeconds(1), Duration.ofHours(1).plusMillis(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxRetryDelay must be in [PT0S, PT1H]");
        assertThatThrownBy(() -> options(Duration.ofSeconds(2), Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("greater than or equal");
    }
}
