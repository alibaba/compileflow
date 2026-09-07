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
package com.alibaba.compileflow.durable.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurableNumbersTest {
    @ParameterizedTest
    @ValueSource(strings = {"PT0.001S", "PT1H"})
    void acceptsInclusivePositiveMillisecondBounds(String value) {
        Duration duration = Duration.parse(value);
        assertThat(DurableNumbers.requirePositiveDurationMillis(duration, Duration.ofHours(1), "delay")).isSameAs(
                duration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-0.001S", "PT0.000001S", "PT1.000001S", "PT1H0.001S"})
    void rejectsNonPositiveLossyAndOutOfRangeDurations(String value) {
        assertThatThrownBy(() -> DurableNumbers.requirePositiveDurationMillis(Duration.parse(value), Duration.ofHours(1),
                "delay"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delay");
    }

    @Test
    void nonNegativeDurationContractStillAcceptsZero() {
        assertThat(DurableNumbers.requireDurationMillis(Duration.ZERO, Duration.ofHours(1), "retention"))
            .isEqualTo(Duration.ZERO);
    }
}
