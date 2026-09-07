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
package com.alibaba.compileflow.deploy.runtime.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class VersionRuntimeManagerTimeArithmeticTest {
    @Test
    void saturatesEpochDeadlinesInsteadOfWrappingIntoThePast() {
        assertThat(VersionRuntimeManager.addSaturated(1_000L, 250L)).isEqualTo(1_250L);
        assertThat(VersionRuntimeManager.addSaturated(1_000L, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void acceptsOnlyPositiveWholeMillisecondMonotonicDurations() {
        assertThat(VersionRuntimeManager.requirePositiveMonotonicMillis(Duration.ofSeconds(5), "failureBackoff"))
            .isEqualTo(5_000L);
        assertThatThrownBy(() -> VersionRuntimeManager.requirePositiveMonotonicMillis(Duration.ZERO, "failureBackoff"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionRuntimeManager.requirePositiveMonotonicMillis(Duration.ofNanos(1),
                "failureBackoff"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionRuntimeManager.requirePositiveMonotonicMillis(Duration.ofMillis(Long.MAX_VALUE),
                "failureBackoff"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("representable as nanoseconds");
        assertThatThrownBy(() -> VersionRuntimeManager.requirePositiveMonotonicMillis(Duration.ofSeconds(Long.MAX_VALUE),
                "failureBackoff"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
