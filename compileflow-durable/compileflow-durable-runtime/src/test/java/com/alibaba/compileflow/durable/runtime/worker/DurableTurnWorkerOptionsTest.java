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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurableTurnWorkerOptionsTest {
    private static DurableTurnWorkerOptions options(Duration turnFaultBackoff) {
        return new DurableTurnWorkerOptions("worker-1", turnFaultBackoff, 10_000, 32);
    }

    @Test
    void rejectsMissingZeroOrExcessiveFaultBackoff() {
        assertThatThrownBy(() -> options(null)).isInstanceOf(NullPointerException.class).hasMessage("turnFaultBackoff");
        assertThatThrownBy(() -> options(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("turnFaultBackoff must be positive");
        assertThatThrownBy(() -> options(Duration.ofNanos(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> options(Duration.ofMillis(1).plusNanos(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> options(Duration.ofHours(1).plusMillis(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("turnFaultBackoff must be in [PT0S, PT1H]");
    }
}
