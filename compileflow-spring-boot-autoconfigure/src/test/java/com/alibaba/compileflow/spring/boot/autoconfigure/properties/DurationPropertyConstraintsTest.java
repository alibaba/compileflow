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
package com.alibaba.compileflow.spring.boot.autoconfigure.properties;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationPropertyConstraintsTest {
    @Test
    void acceptsOnlyPositiveWholeMillisecondValuesWithinTheLongRange() {
        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(Duration.ofMillis(1))).isTrue();
        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(Duration.ofMillis(Long.MAX_VALUE))).isTrue();

        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(null)).isFalse();
        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(Duration.ZERO)).isFalse();
        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(Duration.ofNanos(1_000_001))).isFalse();
        assertThat(DurationPropertyConstraints.isPositiveWholeMilliseconds(Duration
            .ofMillis(Long.MAX_VALUE)
            .plusMillis(1)))
            .isFalse();
    }

    @Test
    void allowsZeroOnlyForNonNegativeWholeMillisecondValues() {
        assertThat(DurationPropertyConstraints.isNonNegativeWholeMilliseconds(Duration.ZERO)).isTrue();
        assertThat(DurationPropertyConstraints.isNonNegativeWholeMilliseconds(Duration.ofNanos(1))).isFalse();
        assertThat(DurationPropertyConstraints.isNonNegativeWholeMilliseconds(Duration.ofMillis(-1))).isFalse();
        assertThat(DurationPropertyConstraints.isNonNegativeWholeMilliseconds(Duration
            .ofMillis(Long.MAX_VALUE)
            .plusMillis(1)))
            .isFalse();
    }
}
