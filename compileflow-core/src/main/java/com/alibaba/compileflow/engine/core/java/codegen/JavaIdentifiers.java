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

import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Helpers for validating and sanitizing Java identifiers used in generated code.
 *
 * @author yusu
 */
public final class JavaIdentifiers {
    private JavaIdentifiers() {
    }

    public static boolean isJavaIdentifier(String value) {
        return JavaNames.isIdentifier(value);
    }

    public static boolean isJavaKeyword(String value) {
        return ProcessNames.isJavaKeyword(value);
    }

    /**
     * Validates and returns a Java binary class name.
     *
     * @param className candidate simple or package-qualified binary name
     * @return the validated input value
     * @throws IllegalArgumentException when the value is blank, too long, path-like, or contains an invalid segment
     */
    public static String requireValidClassName(String className) {
        return JavaNames.requireValidClassName(className);
    }

    public static String toJavaIdentifier(String raw) {
        if (isBlank(raw)) {
            return "_";
        }

        StringBuilder identifier = new StringBuilder(raw.length());
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);
            identifier.append(
                    Character.isJavaIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint)
                    ? new String(Character.toChars(codePoint))
                    : "_");
        }

        String normalized = squashUnderscores(identifier.toString());
        if (normalized.isEmpty()) {
            normalized = "_";
        }

        int first = normalized.codePointAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            normalized = "_" + normalized;
        }

        if (!isJavaIdentifier(normalized)) {
            normalized = "_" + normalized;
        }
        return normalized;
    }

    public static String toClassName(String raw) {
        String name = toUpperCamel(tokenize(raw));
        if (!isJavaIdentifier(name)) {
            name = "_" + name;
        }
        return name;
    }

    public static String toMethodSuffix(String raw) {
        if (isAllDigits(raw)) {
            return raw;
        }
        return toClassName(raw);
    }

    /**
     * Builds a bounded, deterministic method-name suffix without lossy identifier collisions.
     *
     * <p>Human-readable normalization is not injective: values such as {@code a-b} and
     * {@code a_b} both normalize to the same Java identifier. Generated methods use the exact
     * UTF-8 value's SHA-256 digest so distinct model identifiers do not silently share code.
     *
     * @param raw exact model identifier
     * @return Java identifier suffix derived from the complete SHA-256 digest
     */
    public static String toStableMethodSuffix(String raw) {
        String value = Objects.requireNonNull(raw, "raw");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "N" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    public static String toPackageName(String raw) {
        if (isBlank(raw)) {
            return "x";
        }
        String[] parts = raw.split("\\.");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String identifier = toJavaIdentifier(part);
            if ("_".equals(identifier)) {
                identifier = "x";
            }
            identifier = identifier.toLowerCase(Locale.ROOT);
            if (isJavaKeyword(identifier) || !Character.isJavaIdentifierStart(identifier.codePointAt(0))) {
                identifier = "_" + identifier;
            }
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(identifier);
        }
        return out.length() == 0 ? "x" : out.toString();
    }

    private static String[] tokenize(String raw) {
        String identifier = toJavaIdentifier(raw);
        String[] candidates = identifier.split("_+");
        int tokenCount = 0;
        for (String candidate : candidates) {
            if (!candidate.isEmpty()) {
                tokenCount++;
            }
        }
        String[] tokens = new String[tokenCount];
        int tokenIndex = 0;
        for (String candidate : candidates) {
            if (!candidate.isEmpty()) {
                tokens[tokenIndex++] = candidate;
            }
        }
        if (tokens.length == 0) {
            return new String[] {"X"};
        }
        return tokens;
    }

    private static String toUpperCamel(String[] tokens) {
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            out.append(capitalize(token));
        }
        String className = out.toString();
        if (className.isEmpty() || !Character.isJavaIdentifierStart(className.codePointAt(0))) {
            className = "_" + className;
        }
        return className;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int firstCodePoint = value.codePointAt(0);
        int titleCaseCodePoint = Character.toTitleCase(firstCodePoint);
        if (firstCodePoint == titleCaseCodePoint) {
            return value;
        }
        return new StringBuilder()
            .appendCodePoint(titleCaseCodePoint)
            .append(value.substring(Character.charCount(firstCodePoint)))
            .toString();
    }

    private static String squashUnderscores(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean lastUnderscore = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '_') {
                if (!lastUnderscore) {
                    out.append('_');
                }
                lastUnderscore = true;
            } else {
                out.append(ch);
                lastUnderscore = false;
            }
        }

        int start = 0, end = out.length();
        if (start < end && out.charAt(start) == '_') {
            start++;
        }
        if (start < end && out.charAt(end - 1) == '_') {
            end--;
        }
        return (start < end) ? out.substring(start, end) : "";
    }

    private static boolean isAllDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
