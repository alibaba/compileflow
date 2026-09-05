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
package com.alibaba.compileflow.engine.core.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolDurationTest {
    @ParameterizedTest
    @CsvSource({"PT0S, 0", "PT1S, 1000", "PT60S, 60000", "PT1.001S, 1001", "PT1.000S, 1000",
            "P0DT1S, 1000", "P1D, 86400000"})
    void acceptsTheSharedProtocolDurationLanguage(String value, long expectedMillis) {
        assertThat(ProtocolDuration.parseNonNegativeMillis(value, "duration")).isEqualTo(expectedMillis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P", "PT", "PT+1S", "P1Y", "P1M", "P1W", "PT1.0000000000S"})
    void rejectsValuesOutsideTheSharedProtocolDurationLanguage(String value) {
        assertThatThrownBy(() -> ProtocolDuration.parseNonNegativeMillis(value, "duration"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative ISO-8601 duration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT-1S", "-PT1S"})
    void rejectsNegativeDurationsPrecisely(String value) {
        assertThatThrownBy(() -> ProtocolDuration.parseNonNegativeMillis(value, "duration"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duration must not be negative");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pt1s", "Pt1S"})
    void rejectsNonCanonicalCase(String value) {
        assertThatThrownBy(() -> ProtocolDuration.parseNonNegativeMillis(value, "duration"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duration must use canonical uppercase ISO-8601 notation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0.000000001S", "PT1.0001S"})
    void rejectsSubMillisecondPrecision(String value) {
        assertThatThrownBy(() -> ProtocolDuration.parseNonNegativeMillis(value, "duration"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("duration must use whole-millisecond precision");
    }

    @ParameterizedTest
    @ValueSource(strings = {" PT1S", "PT1S ", "\u00a0PT1S"})
    void rejectsSurroundingUnicodeWhitespace(String value) {
        assertThatThrownBy(() -> ProtocolDuration.parseNonNegativeMillis(value, "duration"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
    }
}
