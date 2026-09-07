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

import java.time.Duration;
import java.util.Objects;

/**
 * Scalar range rules shared by Durable API values and first-party adapters.
 *
 * @author yusu
 */
public final class DurableNumbers {
    private DurableNumbers() {
    }

    /**
     * Validates a positive scalar.
     *
     * @param value scalar value
     * @param name field name used in failures
     * @return validated value
     */
    public static long requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * Validates a non-negative scalar.
     *
     * @param value scalar value
     * @param name field name used in failures
     * @return validated value
     */
    public static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    /**
     * Validates an inclusive integer range.
     *
     * @param value scalar value
     * @param minimum inclusive minimum
     * @param maximum inclusive maximum
     * @param name field name used in failures
     * @return validated value
     */
    public static int requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    /**
     * Validates a positive whole-millisecond duration.
     *
     * @param value duration value
     * @param maximum inclusive maximum
     * @param name field name used in failures
     * @return validated value
     */
    public static Duration requirePositiveDurationMillis(Duration value, Duration maximum, String name) {
        Duration duration = requireDurationMillis(value, maximum, name);
        requirePositive(duration.toMillis(), name);
        return duration;
    }

    /**
     * Validates a non-negative whole-millisecond duration.
     *
     * @param value duration value
     * @param maximum inclusive maximum
     * @param name field name used in failures
     * @return validated value
     */
    public static Duration requireDurationMillis(Duration value, Duration maximum, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isNegative() || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be in [PT0S, " + maximum + "]");
        }
        final long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " must be representable as milliseconds", overflow);
        }
        if (!duration.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(name + " must use whole-millisecond precision");
        }
        return duration;
    }
}
