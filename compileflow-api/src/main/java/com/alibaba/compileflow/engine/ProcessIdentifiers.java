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
package com.alibaba.compileflow.engine;

import java.util.regex.Pattern;

/**
 * Supported canonical validation rules for public process and invocation identities.
 *
 * <p>These methods validate without trimming or otherwise normalizing identity values. Their
 * accepted alphabets, bounds, and exact-preservation behavior are part of the 2.x API contract.
 *
 * @author yusu
 */
public final class ProcessIdentifiers {
    static final int MAX_NAMESPACE_LENGTH = 128;
    static final int MAX_CODE_LENGTH = 128;
    static final int MAX_VERSION_LENGTH = 64;
    static final int MAX_ALIAS_LENGTH = 64;
    /**
     * Maximum length of an invocation identifier accepted by the public API.
     */
    public static final int MAX_INVOCATION_ID_LENGTH = 128;
    /**
     * Maximum number of characters accepted for a process node identifier.
     */
    public static final int MAX_NODE_ID_LENGTH = 512;
    /**
     * Maximum number of characters accepted for an event selector.
     */
    public static final int MAX_EVENT_LENGTH = 512;
    /**
     * Maximum number of characters accepted for a trace identifier.
     */
    public static final int MAX_TRACE_ID_LENGTH = 128;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern INVOCATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@-]*");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private ProcessIdentifiers() {
    }

    public static String requireNamespace(String value) {
        return require(value, "namespace", MAX_NAMESPACE_LENGTH);
    }

    public static String requireCode(String value) {
        return require(value, "code", MAX_CODE_LENGTH);
    }

    public static String requireVersion(String value) {
        return require(value, "version", MAX_VERSION_LENGTH);
    }

    public static String requireAlias(String value) {
        return require(value, "alias", MAX_ALIAS_LENGTH);
    }

    /**
     * Validates an optional invocation identifier without normalizing it.
     *
     * @param value invocation identifier, or {@code null}
     * @return {@code null}, or the validated identifier unchanged
     */
    public static String optionalInvocationId(String value) {
        if (value == null) {
            return null;
        }
        return requireInvocationId(value);
    }

    /**
     * Validates a required invocation identifier without normalizing it.
     *
     * @param value invocation identifier
     * @return the validated identifier unchanged
     */
    public static String requireInvocationId(String value) {
        String candidate = requireExactIdentity(value, "invocationId", MAX_INVOCATION_ID_LENGTH);
        if (!INVOCATION_ID_PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException(
                    "invocationId must start with an ASCII letter or digit and contain only "
                    + "ASCII letters, digits, '.', '_', ':', '@', or '-'");
        }
        return candidate;
    }

    /**
     * Validates a required process node identifier without normalizing it.
     *
     * @param value node identifier
     * @return the validated identifier unchanged
     */
    public static String requireNodeId(String value) {
        return requireExactIdentity(value, "nodeId", MAX_NODE_ID_LENGTH);
    }

    /**
     * Validates an optional event selector without normalizing it.
     *
     * @param value event selector, or {@code null}
     * @return {@code null}, or the validated selector unchanged
     */
    public static String optionalEvent(String value) {
        return value == null ? null : requireExactIdentity(value, "event", MAX_EVENT_LENGTH);
    }

    /**
     * Validates a required trace identifier without normalizing it.
     *
     * @param value trace identifier
     * @return the validated identifier unchanged
     */
    public static String requireTraceId(String value) {
        return requireExactIdentity(value, "traceId", MAX_TRACE_ID_LENGTH);
    }

    /**
     * Validates an optional trace identifier without normalizing it.
     *
     * @param value trace identifier, or {@code null}
     * @return {@code null}, or the validated identifier unchanged
     */
    public static String optionalTraceId(String value) {
        return value == null ? null : requireTraceId(value);
    }

    /**
     * Validates an optional lowercase SHA-256 digest.
     *
     * @param value digest, or {@code null}
     * @param name field name used in failures
     * @return {@code null}, or the validated digest unchanged
     */
    public static String optionalSha256(String value, String name) {
        return value == null ? null : requireSha256(value, name);
    }

    /**
     * Validates a required lowercase SHA-256 digest.
     *
     * @param value digest
     * @param name field name used in failures
     * @return the validated digest unchanged
     */
    public static String requireSha256(String value, String name) {
        String digest = requireExactIdentity(value, name, 64);
        if (!SHA_256_PATTERN.matcher(digest).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return digest;
    }

    private static String require(String value, String name, int maxLength) {
        String candidate = requireExactIdentity(value, name, maxLength);
        if (!IDENTIFIER_PATTERN.matcher(candidate).matches()) {
            String message = name + " must start with an ASCII letter or digit and contain only "
                    + "ASCII letters, digits, '.', '_', or '-'";
            throw new IllegalArgumentException(message);
        }
        return candidate;
    }

    /**
     * Validates an exact bounded identity without imposing a domain-specific alphabet.
     *
     * @param value identity value
     * @param name field name used in failures
     * @param maxLength maximum number of Unicode code points
     * @return the validated identity unchanged
     */
    public static String requireExactIdentity(String value, String name, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        String candidate = ProcessText.requireNonBlank(value, name);
        if (!candidate.equals(ProcessText.strip(candidate))) {
            throw new IllegalArgumentException(name + " must not contain surrounding whitespace");
        }
        if (candidate.codePoints().anyMatch(ProcessIdentifiers::isProhibitedIdentityCharacter)) {
            throw new IllegalArgumentException(
                    name + " must not contain control characters or Unicode format characters");
        }
        if (candidate.codePointCount(0, candidate.length()) > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
        return candidate;
    }

    private static boolean isProhibitedIdentityCharacter(int codePoint) {
        return Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT;
    }
}
