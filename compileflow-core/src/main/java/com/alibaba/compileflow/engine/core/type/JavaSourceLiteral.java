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

import java.util.Objects;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Produces Java source expressions for string values without exceeding the
 * class-file UTF-8 limit for one constant.
 *
 * @author yusu
 */
public final class JavaSourceLiteral {
    private static final int MAX_CHARS_PER_LITERAL = 16_000;

    private JavaSourceLiteral() {
    }

    /**
     * Returns a Java expression that reconstructs the supplied string exactly.
     *
     * <p>Short values use one readable literal. Long values use non-constant
     * {@link StringBuilder} appends so {@code javac} cannot fold the chunks back
     * into one oversized constant-pool entry.</p>
     *
     * @param value source string
     * @return Java source expression
     */
    public static String stringExpression(String value) {
        String source = Objects.requireNonNull(value, "value");
        if (source.length() <= MAX_CHARS_PER_LITERAL) {
            return literal(source);
        }
        return builderExpression(source);
    }

    private static String builderExpression(String source) {
        StringBuilder expression = new StringBuilder(source.length() + source.length() / MAX_CHARS_PER_LITERAL * 24);
        expression.append("new StringBuilder(").append(source.length()).append(')');
        for (int from = 0; from < source.length(); ) {
            int to = Math.min(from + MAX_CHARS_PER_LITERAL, source.length());
            expression.append(".append(").append(literal(source.substring(from, to))).append(')');
            from = to;
        }
        return expression.append(".toString()").toString();
    }

    /**
     * Returns one valid Java character literal.
     *
     * <p>A Java string literal and a Java character literal do not have the
     * same delimiter rules: in particular, an apostrophe must be escaped only
     * in the latter. Keeping that rule here prevents callers from accidentally
     * generating an invalid {@code '''} token.</p>
     *
     * @param value source UTF-16 code unit
     * @return Java source character literal
     */
    public static String characterLiteral(char value) {
        String escaped = StringEscapeUtils.escapeJava(String.valueOf(value)).replace("'", "\\'");
        return "'" + escaped + "'";
    }

    private static String literal(String value) {
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }
}
