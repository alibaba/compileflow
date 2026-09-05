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
package com.alibaba.compileflow.spring.boot.autoconfigure.properties;

import java.time.Duration;

/**
 * Shared representation constraints for Spring-bound durations.
 *
 * @author yusu
 */
public final class DurationPropertyConstraints {
    private static final Duration MAXIMUM_MILLIS = Duration.ofMillis(Long.MAX_VALUE);

    private DurationPropertyConstraints() {
    }

    public static boolean isPositiveWholeMilliseconds(Duration value) {
        return value != null && !value.isNegative() && !value.isZero() && isWholeMilliseconds(value);
    }

    public static boolean isPositiveWholeMillisecondsRepresentableAsNanos(Duration value) {
        if (!isPositiveWholeMilliseconds(value)) {
            return false;
        }
        try {
            return value.toNanos() > 0L;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    public static boolean isNonNegativeWholeMilliseconds(Duration value) {
        return value != null && !value.isNegative() && isWholeMilliseconds(value);
    }

    private static boolean isWholeMilliseconds(Duration value) {
        return value.compareTo(MAXIMUM_MILLIS) <= 0 && value.equals(Duration.ofMillis(value.toMillis()));
    }
}
