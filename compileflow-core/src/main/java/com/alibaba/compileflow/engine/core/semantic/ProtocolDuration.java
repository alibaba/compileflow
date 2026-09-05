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
package com.alibaba.compileflow.engine.core.semantic;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Exact duration rules shared by authoring formats and semantic plans.
 *
 * @author yusu
 */
public final class ProtocolDuration {
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)(?:\\.(\\d{1,9}))?S)?)?");

    private ProtocolDuration() {
    }

    /**
     * Parses a non-negative ISO-8601 duration with whole-millisecond precision.
     *
     * @param raw source duration
     * @param name field name used in failures
     * @return exact duration in milliseconds
     */
    public static long parseNonNegativeMillis(String raw, String name) {
        String value = SemanticText.requireIdentity(raw, name);
        if (value.indexOf('-') >= 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        if (!matchesDurationLanguage(value)) {
            String uppercase = value.toUpperCase(java.util.Locale.ROOT);
            if (!value.equals(uppercase) && matchesDurationLanguage(uppercase)) {
                throw new IllegalArgumentException(name + " must use canonical uppercase ISO-8601 notation");
            }
            throw new IllegalArgumentException(name + " must be a non-negative ISO-8601 duration");
        }
        final Duration duration;
        try {
            duration = Duration.parse(value);
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(name + " must be a non-negative ISO-8601 duration", failure);
        }
        return requireNonNegativeMillis(duration, name);
    }

    private static boolean matchesDurationLanguage(String value) {
        return value.length() > 1 && !value.endsWith("T") && DURATION_PATTERN.matcher(value).matches();
    }

    /**
     * Parses a positive ISO-8601 duration with whole-millisecond precision.
     */
    public static long parsePositiveMillis(String raw, String name) {
        long millis = parseNonNegativeMillis(raw, name);
        if (millis == 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return millis;
    }

    /**
     * Validates a non-negative duration and returns its exact millisecond value.
     *
     * @param duration duration to validate
     * @param name field name used in failures
     * @return exact duration in milliseconds
     */
    public static long requireNonNegativeMillis(Duration duration, String name) {
        Duration value = Objects.requireNonNull(duration, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        final long millis;
        try {
            millis = value.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " must be representable as milliseconds", overflow);
        }
        if (!value.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(name + " must use whole-millisecond precision");
        }
        return millis;
    }
}
