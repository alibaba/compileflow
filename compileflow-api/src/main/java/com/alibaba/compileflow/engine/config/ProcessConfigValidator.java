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
package com.alibaba.compileflow.engine.config;

import java.time.Duration;

/**
 * Shared configuration field validation helpers.
 *
 * @author yusu
 */
final class ProcessConfigValidator {
    private ProcessConfigValidator() {
    }

    static ValidationResult validatePositive(int value, String fieldName) {
        return value > 0
                ? ValidationResult.success()
                : ValidationResult.failure(fieldName + " must be positive (> 0), got: " + value);
    }

    static ValidationResult validateNonNegative(int value, String fieldName) {
        return value >= 0
                ? ValidationResult.success()
                : ValidationResult.failure(fieldName + " must be non-negative (>= 0), got: " + value);
    }

    static ValidationResult validatePositiveDurationMillis(Duration value, String fieldName) {
        return validateDurationMillis(value, fieldName, true);
    }

    static ValidationResult validateNonNegativeDurationMillis(Duration value, String fieldName) {
        return validateDurationMillis(value, fieldName, false);
    }

    private static ValidationResult validateDurationMillis(Duration value, String fieldName, boolean positive) {
        String requirement = positive ? "positive" : "non-negative";
        if (value == null) {
            return ValidationResult.failure(fieldName + " must be a " + requirement + " whole-millisecond duration");
        }
        if (value.isNegative() || positive && value.isZero()) {
            return ValidationResult.failure(
                    fieldName + " must be a " + requirement + " whole-millisecond duration, got: " + value);
        }
        Duration maximum = Duration.ofMillis(Long.MAX_VALUE);
        if (value.compareTo(maximum) > 0 || !value.equals(Duration.ofMillis(value.toMillis()))) {
            String message = "%s must be a %s whole-millisecond duration representable as a long, got: %s"
                .formatted(fieldName, requirement, value);
            return ValidationResult.failure(message);
        }
        return ValidationResult.success();
    }

    static ValidationResult combine(ValidationResult... results) {
        ValidationResult combined = ValidationResult.success();
        for (ValidationResult result : results) {
            combined = combined.merge(result);
        }
        return combined;
    }
}
