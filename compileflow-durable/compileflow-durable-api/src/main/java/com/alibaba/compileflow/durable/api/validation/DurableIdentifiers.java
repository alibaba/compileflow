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
package com.alibaba.compileflow.durable.api.validation;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Exact validation for Durable identities and human-readable text.
 *
 * @author yusu
 */
public final class DurableIdentifiers {
    public static final int MAX_ACTOR_CHARACTERS = 128;
    public static final int MAX_AUDIT_CONTEXT_ID_CHARACTERS = 128;
    public static final int MAX_OUTBOX_EVENT_TYPE_CHARACTERS = 32;
    public static final int MAX_REASON_CHARACTERS = 2_048;
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private DurableIdentifiers() {
    }

    /**
     * Validates a required bounded identity without normalizing it.
     *
     * @param value text value
     * @param name field name used in failures
     * @param maximumCharacters maximum Unicode code points
     * @return validated text
     */
    public static String requireIdentity(String value, String name, int maximumCharacters) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be non-blank and contain no surrounding whitespace");
        }
        return ProcessIdentifiers.requireExactIdentity(value, name, maximumCharacters);
    }

    /**
     * Validates a lowercase, canonical UUID string without accepting alternate textual forms.
     *
     * @param value UUID text
     * @param name field name used in failures
     * @return the canonical UUID text unchanged
     */
    public static String requireCanonicalUuid(String value, String name) {
        String candidate = requireIdentity(value, name, 36);
        try {
            if (!UUID.fromString(candidate).toString().equals(candidate)) {
                throw new IllegalArgumentException();
            }
            return candidate;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(name + " must be a lowercase canonical UUID", failure);
        }
    }

    /**
     * Validates an optional bounded identity without normalizing it.
     *
     * @param value text value
     * @param name field name used in failures
     * @param maximumCharacters maximum Unicode code points
     * @return validated identity unchanged, or {@code null} when absent
     */
    public static String optionalIdentity(String value, String name, int maximumCharacters) {
        if (value == null) {
            return null;
        }
        return requireIdentity(value, name, maximumCharacters);
    }

    /**
     * Validates one event from the Durable Outbox integration vocabulary.
     *
     * @param value event type
     * @return validated event type
     */
    public static String requireOutboxEventType(String value) {
        String eventType = requireIdentity(value, "eventType", MAX_OUTBOX_EVENT_TYPE_CHARACTERS);
        return switch (eventType) {
            case "WAIT_COMMITTED", "EFFECT_REVIEW_REQUIRED", "RUN_SUCCEEDED", "RUN_FAILED", "RUN_CANCELLED" -> eventType;
            default -> throw new IllegalArgumentException("eventType is not a supported Durable Outbox event");
        };
    }

    /**
     * Validates required bounded human-readable text while preserving it exactly.
     *
     * @param value text value
     * @param name field name used in failures
     * @param maximumCharacters maximum Unicode code points
     * @return validated text
     */
    public static String requireHumanText(String value, String name, int maximumCharacters) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String text = ProcessText.requireNonBlank(value, name);
        if (text.codePointCount(0, text.length()) > maximumCharacters) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumCharacters + " characters");
        }
        return text;
    }

    /**
     * Validates optional bounded human-readable text while preserving it exactly.
     *
     * @param value text value
     * @param name field name used in failures
     * @param maximumCharacters maximum Unicode code points
     * @return validated text unchanged, or {@code null} when absent
     */
    public static String optionalHumanText(String value, String name, int maximumCharacters) {
        return value == null ? null : requireHumanText(value, name, maximumCharacters);
    }

    /**
     * Validates a lowercase SHA-256 digest.
     *
     * @param value digest value
     * @param name field name used in failures
     * @return validated digest
     */
    public static String requireSha256(String value, String name) {
        String digest = requireIdentity(value, name, 64);
        if (!SHA_256_PATTERN.matcher(digest).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return digest;
    }

    /**
     * Validates an opaque visible-ASCII Wait token.
     *
     * @param value token value
     * @return validated token
     */
    public static String requireOpaqueToken(String value) {
        String token = requireIdentity(value, "waitToken", 512);
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("waitToken must contain only visible ASCII");
            }
        }
        return token;
    }

    /**
     * Validates a bounded actor identity.
     *
     * @param value actor value
     * @return validated actor
     */
    public static String requireActor(String value) {
        return requireIdentity(value, "actor", MAX_ACTOR_CHARACTERS);
    }
}
