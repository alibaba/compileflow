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
import org.junit.jupiter.api.Test;

class JavaSourceLiteralTest {
    @Test
    void keepsShortValuesReadableAndEscaped() {
        assertThat(JavaSourceLiteral.stringExpression("a\n\"b")).isEqualTo("\"a\\n\\\"b\"");
    }

    @Test
    void appliesCharacterDelimiterEscaping() {
        assertThat(JavaSourceLiteral.characterLiteral('\'')).isEqualTo("'\\''");
        assertThat(JavaSourceLiteral.characterLiteral('\\')).isEqualTo("'\\\\'");
        assertThat(JavaSourceLiteral.characterLiteral('\n')).isEqualTo("'\\n'");
    }

    @Test
    void preventsLongValuesFromBecomingOneCompileTimeConstant() {
        String expression = JavaSourceLiteral.stringExpression("x".repeat(70_000));

        assertThat(expression).startsWith("new StringBuilder(70000)").contains(".append(").endsWith(".toString()");
        assertThat(longestQuotedSegment(expression)).isLessThanOrEqualTo(16_000);
    }

    private int longestQuotedSegment(String expression) {
        int longest = 0;
        int start = -1;
        boolean escaped = false;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (start >= 0) {
                if (current == '"' && !escaped) {
                    longest = Math.max(longest, index - start);
                    start = -1;
                }
                escaped = current == '\\' && !escaped;
                if (current != '\\') {
                    escaped = false;
                }
            } else if (current == '"') {
                start = index + 1;
            }
        }
        return longest;
    }
}
