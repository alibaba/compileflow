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
package com.alibaba.compileflow.engine.core.java.expression;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Performs conservative lexical inspection of generated Java expressions.
 *
 * <p>This class deliberately does not claim to prove that invoked methods are
 * pure. Every Process expression must be observational and must not mutate any
 * object reachable from Process state. It does reject direct Java mutation operators and exposes
 * process-variable references without being confused by literals, comments, member names, or
 * method names.
 *
 * @author yusu
 */
public final class JavaExpressionInspector {
    private JavaExpressionInspector() {
    }

    /**
     * Returns candidate identifiers read by a Java expression.
     *
     * <p>Java Unicode escapes are translated before tokenization. Expressions
     * containing one are therefore treated as opaque and conservatively read
     * every candidate.
     *
     * @param expression Java expression
     * @param candidates identifiers that represent process state
     * @return referenced candidates in encounter order
     */
    public static Set<String> referencedIdentifiers(String expression, Set<String> candidates) {
        Set<String> references = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) {
            return references;
        }
        if (expression.contains("\\u")) {
            references.addAll(candidates);
            return references;
        }
        String code = maskNonCode(expression);

        int index = 0;
        while (index < code.length()) {
            int current = code.codePointAt(index);
            if (!Character.isJavaIdentifierStart(current)) {
                index += Character.charCount(current);
                continue;
            }

            int end = index + Character.charCount(current);
            while (end < code.length()) {
                int codePoint = code.codePointAt(end);
                if (!Character.isJavaIdentifierPart(codePoint)) {
                    break;
                }
                end += Character.charCount(codePoint);
            }
            String identifier = code.substring(index, end);
            if (candidates.contains(identifier) && !isNonProcessMemberReference(code, index)
                    && !isMethodInvocation(code, end)) {
                references.add(identifier);
            }
            index = end;
        }
        return references;
    }

    /**
     * Finds a direct mutation operator in a condition expression.
     *
     * @param expression Java condition expression
     * @return offending operator, {@code unicode escape}, or {@code null}
     */
    public static String findDirectMutation(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        if (expression.contains("\\u")) {
            return "unicode escape";
        }
        String code = maskNonCode(expression);

        int index = 0;
        while (index < code.length()) {
            String operator = mutationOperatorAt(code, index);
            if (operator != null) {
                return operator;
            }
            index++;
        }
        return null;
    }

    private static String maskNonCode(String expression) {
        StringBuilder code = new StringBuilder(expression);
        int index = 0;
        while (index < expression.length()) {
            int skipped = skipNonCode(expression, index);
            if (skipped == index) {
                index++;
                continue;
            }
            for (int masked = index; masked < skipped; masked++) {
                code.setCharAt(masked, ' ');
            }
            index = skipped;
        }
        return code.toString();
    }

    private static String mutationOperatorAt(String expression, int index) {
        String[] multiCharacterOperators =
                {">>>=", "<<=", ">>=", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="};
        for (String operator : multiCharacterOperators) {
            if (expression.startsWith(operator, index)) {
                return operator;
            }
        }
        if (expression.charAt(index) != '=') {
            return null;
        }
        char previous = index == 0 ? '\0' : expression.charAt(index - 1);
        char next = index + 1 < expression.length() ? expression.charAt(index + 1) : '\0';
        return previous == '=' || previous == '!' || previous == '<' || previous == '>' || next == '=' ? null : "=";
    }

    private static int skipNonCode(String expression, int index) {
        char current = expression.charAt(index);
        if (current == '/' && index + 1 < expression.length()) {
            char next = expression.charAt(index + 1);
            if (next == '/') {
                return skipLineComment(expression, index + 2);
            }
            if (next == '*') {
                return skipBlockComment(expression, index + 2);
            }
        }
        if (current == '"' || current == '\'') {
            return skipJavaLiteral(expression, index, current);
        }
        return index;
    }

    private static int skipLineComment(String expression, int index) {
        while (index < expression.length() && expression.charAt(index) != '\n' && expression.charAt(index) != '\r') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String expression, int index) {
        int end = expression.indexOf("*/", index);
        return end < 0 ? expression.length() : end + 2;
    }

    private static int skipJavaLiteral(String expression, int start, char delimiter) {
        boolean textBlock = delimiter == '"' && start + 2 < expression.length() && expression.charAt(start + 1) == '"'
                && expression.charAt(start + 2) == '"';
        int index = start + (textBlock ? 3 : 1);
        while (index < expression.length()) {
            if (textBlock && index + 2 < expression.length() && expression.charAt(index) == '"'
                    && expression.charAt(index + 1) == '"' && expression.charAt(index + 2) == '"'
                    && !isEscaped(expression, index)) {
                return index + 3;
            }
            if (!textBlock && expression.charAt(index) == delimiter && !isEscaped(expression, index)) {
                return index + 1;
            }
            index++;
        }
        return expression.length();
    }

    private static boolean isEscaped(String expression, int index) {
        int slashCount = 0;
        for (int cursor = index - 1; cursor >= 0 && expression.charAt(cursor) == '\\'; cursor--) {
            slashCount++;
        }
        return slashCount % 2 != 0;
    }

    private static boolean isNonProcessMemberReference(String expression, int identifierStart) {
        int dot = previousNonWhitespace(expression, identifierStart - 1);
        if (dot < 0 || expression.charAt(dot) != '.') {
            return false;
        }
        return !isExplicitThisReceiver(expression, dot);
    }

    private static boolean isExplicitThisReceiver(String expression, int dot) {
        int selectorEnd = previousNonWhitespace(expression, dot - 1);
        if (selectorEnd < 0 || expression.charAt(selectorEnd) == ')') {
            return selectorEnd >= 0 && isParenthesizedThisReceiver(expression, selectorEnd);
        }
        int selectorEndCodePoint = expression.codePointBefore(selectorEnd + 1);
        if (!Character.isJavaIdentifierPart(selectorEndCodePoint)) {
            return false;
        }
        int selectorStart = selectorEnd;
        while (selectorStart > 0) {
            int previous = expression.codePointBefore(selectorStart);
            if (!Character.isJavaIdentifierPart(previous)) {
                break;
            }
            selectorStart -= Character.charCount(previous);
        }
        return "this".equals(expression.substring(selectorStart, selectorEnd + 1));
    }

    private static boolean isParenthesizedThisReceiver(String expression, int closingParenthesis) {
        int openingParenthesis = matchingOpeningParenthesis(expression, closingParenthesis);
        if (openingParenthesis < 0 || isInvocationParenthesis(expression, openingParenthesis)) {
            return false;
        }
        String compact = compactCode(expression, openingParenthesis + 1, closingParenthesis);
        while (compact.length() >= 2 && compact.charAt(0) == '('
                && matchingClosingParenthesis(compact, 0) == compact.length() - 1) {
            compact = compact.substring(1, compact.length() - 1);
        }
        return "this".equals(compact);
    }

    private static int matchingOpeningParenthesis(String expression, int expectedClosing) {
        int[] stack = new int[expression.length()];
        int depth = 0;
        int index = 0;
        while (index <= expectedClosing) {
            int skipped = skipNonCode(expression, index);
            if (skipped != index) {
                index = skipped;
                continue;
            }
            char current = expression.charAt(index);
            if (current == '(') {
                stack[depth++] = index;
            } else if (current == ')') {
                if (depth == 0) {
                    return -1;
                }
                int opening = stack[--depth];
                if (index == expectedClosing) {
                    return opening;
                }
            }
            index++;
        }
        return -1;
    }

    private static int matchingClosingParenthesis(String expression, int opening) {
        int depth = 0;
        for (int index = opening; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isInvocationParenthesis(String expression, int openingParenthesis) {
        int previous = previousNonWhitespace(expression, openingParenthesis - 1);
        if (previous < 0) {
            return false;
        }
        int codePoint = expression.codePointBefore(previous + 1);
        return Character.isJavaIdentifierPart(codePoint) || expression.charAt(previous) == ')'
                || expression.charAt(previous) == ']';
    }

    private static String compactCode(String expression, int start, int end) {
        StringBuilder result = new StringBuilder(end - start);
        int index = start;
        while (index < end) {
            char current = expression.charAt(index);
            int skipped = skipNonCode(expression, index);
            if (skipped != index) {
                if (current == '"' || current == '\'') {
                    result.append('#');
                }
                index = skipped;
                continue;
            }
            if (!Character.isWhitespace(current)) {
                result.append(current);
            }
            index++;
        }
        return result.toString();
    }

    private static boolean isMethodInvocation(String expression, int identifierEnd) {
        int next = nextNonWhitespace(expression, identifierEnd);
        return next < expression.length() && expression.charAt(next) == '(';
    }

    private static int previousNonWhitespace(String expression, int index) {
        while (index >= 0 && Character.isWhitespace(expression.charAt(index))) {
            index--;
        }
        return index;
    }

    private static int nextNonWhitespace(String expression, int index) {
        while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
            index++;
        }
        return index;
    }
}
