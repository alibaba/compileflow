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
package com.alibaba.compileflow.engine.test.support.helpers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AwaiterTest {
    @Test
    void rejectsNonPositiveTimingConfiguration() {
        assertThatThrownBy(() -> Awaiter.await("invalid timeout", Duration.ZERO, Duration.ofMillis(1), () -> false, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeout must be positive");

        assertThatThrownBy(() -> Awaiter.await("invalid interval", Duration.ofMillis(1), Duration.ZERO, () -> false,
                null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("interval must be positive");
    }

    @Test
    void doesNotSuppressSeriousConditionFailures() {
        assertThatThrownBy(() -> Awaiter.await("fatal condition", Duration.ofSeconds(1), Duration.ofMillis(1),
                () -> {
                    throw new AssertionError("fatal");
                }, null))
            .isInstanceOf(AssertionError.class)
            .hasMessage("fatal");
    }
}
