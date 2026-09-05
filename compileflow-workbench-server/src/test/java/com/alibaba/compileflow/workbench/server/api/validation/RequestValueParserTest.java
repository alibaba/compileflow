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
package com.alibaba.compileflow.workbench.server.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestValueParserTest {
    @Test
    void validatesLogSafeInvocationIdentifiersWithoutNormalization() {
        assertThat(RequestValueParser.optionalInvocationId("inv:order_42@example.com")).isEqualTo(
                "inv:order_42@example.com");
        assertThatThrownBy(() -> RequestValueParser.optionalInvocationId(" inv:order_42@example.com "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> RequestValueParser.optionalInvocationId("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void rejectsInvocationIdentifiersThatCannotSafelyCrossServerBoundaries() {
        assertThatThrownBy(() -> RequestValueParser.optionalInvocationId("inv-1\nforged"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invocationId must not contain control characters or Unicode format characters");
        assertThatThrownBy(() -> RequestValueParser.optionalInvocationId("a".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invocationId must not exceed 128 characters");
    }

    @Test
    void requiresInvocationIdentifierForPathOperations() {
        assertThat(RequestValueParser.requiredInvocationId("inv-1")).isEqualTo("inv-1");
        assertThatThrownBy(() -> RequestValueParser.requiredInvocationId(" inv-1 "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> RequestValueParser.requiredInvocationId("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invocationId must not be blank");
    }

    @Test
    void optionalIntegerAcceptsIntegralJsonNumbers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", 50);

        assertThat(RequestValueParser.optionalInteger(body, "limit")).isEqualTo(50);
    }

    @Test
    void optionalIntegerRejectsFloatingPointNumbers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", 50.5d);

        assertThatThrownBy(() -> RequestValueParser.optionalInteger(body, "limit"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be an integer");
    }

    @Test
    void optionalIntegerRejectsDecimalNumbersWithFractionalScale() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", new BigDecimal("50.5"));

        assertThatThrownBy(() -> RequestValueParser.optionalInteger(body, "limit"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be an integer");
    }

    @Test
    void optionalIntegerRejectsValuesOutsideIntRange() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE));

        assertThatThrownBy(() -> RequestValueParser.optionalInteger(body, "limit"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must fit in a 32-bit integer");
    }

    @Test
    void requireOneBasedPageRejectsZeroPage() {
        assertThatThrownBy(() -> RequestValueParser.requireOneBasedPage(0, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be greater than or equal to 1");
    }

    @Test
    void requireLimitRejectsValuesOutsideConfiguredBounds() {
        assertThatThrownBy(() -> RequestValueParser.requireLimit(0, "limit", 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> RequestValueParser.requireLimit(101, "limit", 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void optionalDoubleRejectsNonNumericValues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("maxCanaryErrorRate", "0.05");

        assertThatThrownBy(() -> RequestValueParser.optionalDouble(body, "maxCanaryErrorRate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxCanaryErrorRate must be numeric");
    }

    @Test
    void optionalBooleanRejectsNonBooleanValues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("abortOnFailure", "true");

        assertThatThrownBy(() -> RequestValueParser.optionalBoolean(body, "abortOnFailure", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("abortOnFailure must be a boolean");
    }
}
