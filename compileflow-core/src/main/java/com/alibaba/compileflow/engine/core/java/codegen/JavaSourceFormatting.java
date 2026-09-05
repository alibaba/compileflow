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
package com.alibaba.compileflow.engine.core.java.codegen;

import com.alibaba.compileflow.engine.core.type.JavaSourceLiteral;
import java.util.ArrayList;
import java.util.List;

/**
 * Small source-formatting primitives shared by Java code generators.
 *
 * @author yusu
 */
final class JavaSourceFormatting {
    private JavaSourceFormatting() {
    }

    static String literal(String value) {
        return JavaSourceLiteral.stringExpression(value);
    }

    static String readableStringExpression(String value, int continuationIndent) {
        final int readableChunkSize = 96;
        if (value.length() <= readableChunkSize) {
            return JavaSourceLiteral.stringExpression(value);
        }
        List<String> chunks = new ArrayList<>();
        for (int from = 0; from < value.length(); ) {
            int to = Math.min(from + readableChunkSize, value.length());
            if (to < value.length() && Character.isHighSurrogate(value.charAt(to - 1))) {
                to--;
            }
            chunks.add(JavaSourceLiteral.stringExpression(value.substring(from, to)));
            from = to;
        }
        String padding = "    ".repeat(continuationIndent);
        if (value.length() <= 16_000) {
            return String.join("\n" + padding + "+ ", chunks);
        }
        StringBuilder expression = new StringBuilder("new StringBuilder(" + value.length() + ")");
        chunks.forEach(chunk -> expression
            .append('\n')
            .append(padding)
            .append(".append(")
            .append(chunk)
            .append(')'));
        return expression.append('\n').append(padding).append(".toString()").toString();
    }

    static List<String> logicalExpressionLines(String expression) {
        List<String> lines = new ArrayList<>();
        int segmentStart = 0;
        char quote = 0;
        boolean escaped = false;
        String operator = null;
        for (int index = 0; index + 1 < expression.length(); index++) {
            char current = expression.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }
            char next = expression.charAt(index + 1);
            if (!((current == '&' && next == '&') || (current == '|' && next == '|'))) {
                continue;
            }
            String segment = expression.substring(segmentStart, index).trim();
            lines.add(operator == null ? segment : operator + " " + segment);
            operator = expression.substring(index, index + 2);
            segmentStart = index + 2;
            index++;
        }
        String tail = expression.substring(segmentStart).trim();
        lines.add(operator == null ? tail : operator + " " + tail);
        return List.copyOf(lines);
    }

    static String safeComment(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\\') {
                safe.append("\\\\");
            } else if (codePoint == '\r') {
                safe.append("\\r");
            } else if (codePoint == '\n') {
                safe.append("\\n");
            } else if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT
                    || Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR) {
                safe.append("\\x{").append(Integer.toHexString(codePoint)).append('}');
            } else {
                safe.appendCodePoint(codePoint);
            }
        }
        return safe.toString().replace("*/", "* /");
    }

    static void line(StringBuilder code, int indent, String line) {
        if (!line.isEmpty()) {
            code.append("    ".repeat(indent)).append(line);
        }
        code.append('\n');
    }
}
