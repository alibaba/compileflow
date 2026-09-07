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
package com.alibaba.compileflow.engine.core.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataTypeConversionTest {
    private static void assertConversionFails(Object value, Class<?> targetType) {
        assertThatThrownBy(() -> DataTypes.transfer(value, targetType))
            .isInstanceOf(DataTypeException.ConversionException.class);
    }

    private static String defaultExpression(Class<?> type, String value) {
        return DataTypes.generateDefaultValueCode(type, value).expression();
    }

    @Test
    void performsOnlyExactIntegralConversions() {
        assertThat(DataTypes.transfer("42.0", Integer.class)).isEqualTo(42);
        assertThat(DataTypes.transfer(BigDecimal.valueOf(127), Byte.class)).isEqualTo((byte) 127);
        assertThat(DataTypes.transfer("9223372036854775807", Long.class)).isEqualTo(Long.MAX_VALUE);
        assertThat(DataTypes.transfer("12345678901234567890", BigInteger.class))
            .isEqualTo(new BigInteger("12345678901234567890"));

        assertConversionFails("1.9", Integer.class);
        assertConversionFails("128", Byte.class);
        assertConversionFails(Double.NaN, Long.class);
        assertConversionFails("1,000", Integer.class);
        assertConversionFails("1 000", Integer.class);
    }

    @Test
    void rejectsAmbiguousBooleanAndCharacterCoercions() {
        assertThat(DataTypes.transfer("TRUE", Boolean.class)).isEqualTo(Boolean.TRUE);
        assertThat(DataTypes.transfer("x", Character.class)).isEqualTo('x');

        assertConversionFails("yes", Boolean.class);
        assertConversionFails(1, Boolean.class);
        assertConversionFails("ab", Character.class);
        assertConversionFails("", Character.class);
    }

    @Test
    void usesStrictIsoTemporalSemanticsWithoutAnImplicitTimeZone() {
        assertThat(DataTypes.transfer("2026-07-14", LocalDate.class)).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(DataTypes.transfer("12:34:56.123", LocalTime.class)).isEqualTo(LocalTime.of(12, 34, 56, 123_000_000));
        assertThat(DataTypes.transfer("2026-07-14T12:34:56", LocalDateTime.class))
            .isEqualTo(LocalDateTime.of(2026, 7, 14, 12, 34, 56));
        assertThat(DataTypes.transfer("2026-07-14T12:34:56Z", Instant.class)).isEqualTo(Instant.parse(
                "2026-07-14T12:34:56Z"));
        assertThat(DataTypes.transfer("2026-07-14T12:34:56Z", Timestamp.class))
            .isEqualTo(Timestamp.from(Instant.parse("2026-07-14T12:34:56Z")));
        assertThat(DataTypes.transfer("2026-07-14T12:34:56.123Z", java.util.Date.class).toInstant())
            .isEqualTo(Instant.parse("2026-07-14T12:34:56.123Z"));

        assertConversionFails("2026-02-30", LocalDate.class);
        assertConversionFails("2026-07-14 12:34:56", LocalDateTime.class);
        assertConversionFails("2026-07-14T12:34:56", java.util.Date.class);
        assertConversionFails(Instant.parse("2026-07-14T12:34:56.123456789Z"), java.util.Date.class);
        assertConversionFails("12:34:56.123", java.sql.Time.class);
        assertThatThrownBy(() -> DataTypes.transfer(new Object(), LocalDate.class))
            .isInstanceOf(DataTypeException.ConversionException.class)
            .hasMessageContaining(LocalDate.class.getName());
        assertThatThrownBy(() -> DataTypes.transfer("not-a-date", java.sql.Date.class))
            .isInstanceOf(DataTypeException.ConversionException.class)
            .hasMessageContaining(java.sql.Date.class.getName())
            .hasMessageNotContaining(LocalDate.class.getName());
    }

    @Test
    void generatedTemporalDefaultsAreValidatedAndCanonical() {
        assertThat(defaultExpression(LocalDate.class, "2026-07-14")).isEqualTo(
                "java.time.LocalDate.parse(\"2026-07-14\")");
        assertThat(defaultExpression(Instant.class, "2026-07-14T12:34:56Z"))
            .isEqualTo("java.time.Instant.parse(\"2026-07-14T12:34:56Z\")");
        DataTypes.DefaultValueCode timestamp =
                DataTypes.generateDefaultValueCode(Timestamp.class, "2026-07-14T12:34:56.123456789Z");
        assertThat(timestamp.expression())
            .isEqualTo("java.sql.Timestamp.from(java.time.Instant.parse(\"2026-07-14T12:34:56.123456789Z\"))");
        assertThat(timestamp.referencedTypes()).containsExactly(Timestamp.class, Instant.class);

        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(java.util.Date.class, "$now"))
            .isInstanceOf(DataTypeException.ConversionException.class);
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(java.util.Date.class,
                "2026-07-14T12:34:56.123456789Z"))
            .isInstanceOf(DataTypeException.ConversionException.class)
            .hasMessageContaining(java.util.Date.class.getName());
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(int.class, "@ "))
            .isInstanceOf(DataTypeException.ConversionException.class);
    }

    @Test
    void assignmentCompatibilityRetainsStrictConversionBoundaries() {
        assertThat(DataTypes.isJavaAssignmentCompatible(ArrayList.class, List.class)).isTrue();
        assertThat(DataTypes.isJavaAssignmentCompatible(Integer.class, long.class)).isTrue();
        assertThat(DataTypes.isJavaAssignmentCompatible(Float.class, double.class)).isFalse();
        assertThat(DataTypes.isJavaAssignmentCompatible(Object.class, Integer.class)).isFalse();
        assertThat(DataTypes.isJavaAssignmentCompatible(Object.class, Map.Entry.class)).isFalse();
        assertThat(DataTypes.isJavaAssignmentCompatible(Object.class, Map.Entry[].class)).isFalse();
    }

    @Test
    void generatedCharacterDefaultsEscapeTheCharacterDelimiter() {
        assertThat(defaultExpression(char.class, "'")).isEqualTo("'\\''");
        assertThat(defaultExpression(Character.class, "'")).isEqualTo("java.lang.Character.valueOf('\\'')");
    }

    @Test
    void conversionErrorsDoNotEchoRuntimeValues() {
        String secret = "api-key-super-secret";

        assertThatThrownBy(() -> DataTypes.transfer(secret, Integer.class))
            .isInstanceOf(DataTypeException.ConversionException.class)
            .hasMessageNotContaining(secret)
            .hasMessageContaining("java.lang.String")
            .hasMessageContaining("java.lang.Integer");
    }
}
