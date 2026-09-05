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
package com.alibaba.compileflow.deploy.api.rollout;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.regex.Pattern;

/**
 * Stable scalar constraints shared by rollout commands, state, and persistence adapters.
 *
 * @author yusu
 */
public final class RolloutConstraints {
    public static final int FULL_WEIGHT_BPS = 10_000;
    public static final int MAX_ID_CHARACTERS = 64;
    public static final int MAX_IDEMPOTENCY_KEY_CHARACTERS = 128;
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private RolloutConstraints() {
    }

    /**
     * Validates a bounded rollout identifier.
     *
     * @param value identifier value
     * @param name field name used in validation failures
     * @return validated identifier
     */
    public static String requireId(String value, String name) {
        return requireText(value, name, MAX_ID_CHARACTERS);
    }

    /**
     * Validates a stable uppercase rollout event type.
     *
     * @param value event type
     * @return validated event type
     */
    public static String requireEventType(String value) {
        String type = requireText(value, "type", 64);
        if (!EVENT_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("type must contain only uppercase ASCII letters, digits, or '_'");
        }
        return type;
    }

    /**
     * Validates a lowercase SHA-256 digest.
     *
     * @param value digest value
     * @param name field name used in validation failures
     * @return validated digest
     */
    public static String requireSha256(String value, String name) {
        String digest = requireText(value, name, 64);
        if (!SHA_256.matcher(digest).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 hexadecimal value");
        }
        return digest;
    }

    /**
     * Validates a bounded visible-ASCII idempotency key.
     *
     * @param value idempotency key
     * @return validated idempotency key
     */
    public static String requireIdempotencyKey(String value) {
        String key = requireText(value, "idempotencyKey", MAX_IDEMPOTENCY_KEY_CHARACTERS);
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("idempotencyKey must contain only visible ASCII characters");
            }
        }
        return key;
    }

    /**
     * Validates a positive revision.
     *
     * @param revision revision value
     * @param name field name used in validation failures
     * @return validated revision
     */
    public static long requirePositiveRevision(long revision, String name) {
        if (revision <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return revision;
    }

    /**
     * Validates a non-negative revision.
     *
     * @param revision revision value
     * @param name field name used in validation failures
     * @return validated revision
     */
    public static long requireNonNegativeRevision(long revision, String name) {
        if (revision < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return revision;
    }

    /**
     * Validates a non-terminal active rollout weight.
     *
     * @param weightBps active weight in basis points
     * @param name field name used in validation failures
     * @return validated active weight
     */
    public static int requireActiveWeightBps(int weightBps, String name) {
        if (weightBps <= 0 || weightBps >= FULL_WEIGHT_BPS) {
            throw new IllegalArgumentException(name + " must be between 1 and 9999 basis points");
        }
        return weightBps;
    }

    private static String requireText(String value, String name, int maximumCharacters) {
        return ProcessIdentifiers.requireExactIdentity(value, name, maximumCharacters);
    }
}
