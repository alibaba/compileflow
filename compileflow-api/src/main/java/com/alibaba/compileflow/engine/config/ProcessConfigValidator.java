/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.function.Predicate;

/**
 * A utility class providing static methods for configuration validation.
 * This class cannot be instantiated.
 *
 * @author yusu
 */
public final class ProcessConfigValidator {

    private ProcessConfigValidator() {
    }

    /**
     * Validates that the given object is not null.
     *
     * @param obj       The object to validate.
     * @param fieldName The name of the field being validated, for use in the error message.
     * @param <T>       The type of the object.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static <T> ValidationResult validateNotNull(T obj, String fieldName) {
        return obj != null ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " cannot be null");
    }

    /**
     * Validates that the given string is not null, empty, or whitespace.
     *
     * @param str       The string to validate.
     * @param fieldName The name of the field being validated.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validateNotBlank(String str, String fieldName) {
        return (str != null && !str.trim().isEmpty()) ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " cannot be null or blank, got: " + str);
    }

    /**
     * Validates that the given integer is positive (> 0).
     *
     * @param value     The integer to validate.
     * @param fieldName The name of the field being validated.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validatePositive(int value, String fieldName) {
        return value > 0 ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " must be positive (> 0), got: " + value);
    }

    /**
     * Validates that the given long is non-negative (>= 0).
     *
     * @param value     The long to validate.
     * @param fieldName The name of the field being validated.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validateNonNegative(long value, String fieldName) {
        return value >= 0 ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " cannot be negative, got: " + value);
    }

    /**
     * Validates that the given integer is within a specified range (inclusive).
     *
     * @param value     The integer to validate.
     * @param min       The minimum allowed value.
     * @param max       The maximum allowed value.
     * @param fieldName The name of the field being validated.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validateRange(int value, int min, int max, String fieldName) {
        return (value >= min && value <= max) ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " must be between " + min + " and " + max + ", got: " + value);
    }

    /**
     * Validates that one integer is less than or equal to another.
     *
     * @param smaller     The integer that should be smaller or equal.
     * @param larger      The integer that should be larger or equal.
     * @param smallerName The name of the smaller value's field.
     * @param largerName  The name of the larger value's field.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validateLessOrEqual(int smaller, int larger, String smallerName, String largerName) {
        return smaller <= larger ?
                ValidationResult.success() :
                ValidationResult.failure(smallerName + " cannot be greater than " + largerName +
                        ": " + smallerName + "=" + smaller + ", " + largerName + "=" + larger);
    }

    /**
     * Validates that the given enum is not null.
     *
     * @param enumValue The enum to validate.
     * @param fieldName The name of the field being validated.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static ValidationResult validateEnum(Enum<?> enumValue, String fieldName) {
        return enumValue != null ?
                ValidationResult.success() :
                ValidationResult.failure(fieldName + " must be explicitly configured");
    }

    /**
     * Validates a value against a given predicate.
     *
     * @param value        The value to validate.
     * @param condition    The predicate to test the value against.
     * @param errorMessage The error message to use on failure.
     * @param <T>          The type of the value.
     * @return A {@link ValidationResult} indicating success or failure.
     */
    public static <T> ValidationResult validateCondition(T value, Predicate<T> condition, String errorMessage) {
        return condition.test(value) ?
                ValidationResult.success() :
                ValidationResult.failure(errorMessage);
    }

    /**
     * Combines multiple validation results into a single result.
     *
     * @param results The validation results to combine.
     * @return A new {@link ValidationResult} containing all errors and warnings from the input results.
     */
    public static ValidationResult combine(ValidationResult... results) {
        ValidationResult combined = ValidationResult.success();
        for (ValidationResult result : results) {
            combined = combined.merge(result);
        }
        return combined;
    }

    /**
     * Validates thread pool sizes in relation to available CPU cores, adding warnings for potentially problematic configurations.
     *
     * @param threads The number of threads to validate.
     * @param name    The name of the thread pool being validated (e.g., "compilationThreads").
     * @return A {@link ValidationResult}, possibly with warnings.
     */
    public static ValidationResult validateCpuRelated(int threads, String name) {
        ValidationResult result = validatePositive(threads, name);
        if (!result.isValid()) {
            return result;
        }

        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        int recommended;
        if ("compilationThreads".equals(name)) {
            if (threads > Math.max(2, cpu / 2)) {
                result = result.addWarning(String.format(
                        "%s (%d) is high for %d cores; consider ≤ %d to avoid GC/memory pressure",
                        name, threads, cpu, Math.max(2, cpu / 8)));
            }
        } else if ("executionThreads".equals(name)) {
            recommended = Math.max(4, cpu);
            if (threads > recommended * 1.5) {
                result = result.addWarning(String.format(
                        "%s (%d) exceeds typical baseline (%d) for %d cores; ensure workload is I/O-heavy or add backpressure",
                        name, threads, recommended, cpu));
            }
            if (threads > recommended * 2) {
                result = result.addWarning(String.format(
                        "%s (%d) significantly exceeds baseline (%d); may cause context-switch or memory overhead",
                        name, threads, recommended));
            }
        }
        return result;
    }

    /**
     * Validates the balance of thread resources in the executor configuration.
     * It warns if the total number of threads is excessively high for the available cores
     * or if compilation threads outnumber execution threads.
     *
     * @param config The executor configuration to validate.
     * @return A {@link ValidationResult}, possibly with warnings.
     */
    public static ValidationResult validateResourceBalance(ProcessExecutorConfig config) {
        if (config == null) {
            return ValidationResult.failure("ExecutorConfig cannot be null");
        }

        ValidationResult result = ValidationResult.success();
        int total = config.getCompilationThreads() + config.getExecutionThreads() + config.getEventThreads() + config.getScheduleThreads();
        int cpu = Runtime.getRuntime().availableProcessors();

        if (total > cpu * 4) {
            result = result.addWarning(String.format(
                    "Total threads (%d) may cause resource contention on %d CPU cores. Consider reducing thread counts.",
                    total, cpu));
        }

        if (config.getCompilationThreads() > config.getExecutionThreads()) {
            result = result.addWarning(
                    "Compilation threads > execution threads may cause resource imbalance");
        }

        return result;
    }

    /**
     * Validates the provided ClassLoader, warning if it is the system ClassLoader.
     * Using the system ClassLoader can lead to classpath pollution and security issues in multi-tenant environments.
     *
     * @param classLoader The ClassLoader to validate.
     * @return A {@link ValidationResult}, possibly with warnings.
     */
    public static ValidationResult validateClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return ValidationResult.failure("ClassLoader cannot be null");
        }

        ValidationResult result = ValidationResult.success();

        // Check if it's the system class loader (potential security issue)
        if (classLoader == ClassLoader.getSystemClassLoader()) {
            result = result.addWarning(
                    "Using system ClassLoader may cause security and isolation issues in some environments");
        }

        return result;
    }


    /**
     * Validates a process code for format and length constraints.
     * A valid code must start with a letter and contain only alphanumeric characters, dots, underscores, or hyphens.
     * A warning is added if the code is excessively long.
     *
     * @param code The process code to validate.
     * @return A {@link ValidationResult}, possibly with warnings or failures.
     */
    public static ValidationResult validateProcessCode(String code) {
        ValidationResult basicValidation = validateNotBlank(code, "processCode");
        if (!basicValidation.isValid()) {
            return basicValidation;
        }

        if (!code.matches("^[a-zA-Z][a-zA-Z0-9._-]*$")) {
            return ValidationResult.failure("Process code '" + code +
                    "' contains invalid characters. Use only letters, numbers, dots, underscores, and hyphens. Must start with a letter");
        }

        ValidationResult lengthCheck = ValidationResult.success();
        if (code.length() > 100) {
            lengthCheck = lengthCheck.addWarning("Process code '" + code +
                    "' is very long (" + code.length() + " chars). Consider shorter, more concise codes");
        }

        return lengthCheck;
    }

    /**
     * Requires the object to be non-null, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param obj       The object to check.
     * @param fieldName The name of the field for the error message.
     */
    public static void requireNonNull(Object obj, String fieldName) {
        validateNotNull(obj, fieldName).throwIfInvalid();
    }

    /**
     * Requires the string to be non-blank, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param str       The string to check.
     * @param fieldName The name of the field for the error message.
     */
    public static void requireNonBlank(String str, String fieldName) {
        validateNotBlank(str, fieldName).throwIfInvalid();
    }

    /**
     * Requires the value to be positive, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param value     The value to check.
     * @param fieldName The name of the field for the error message.
     */
    public static void requirePositive(int value, String fieldName) {
        validatePositive(value, fieldName).throwIfInvalid();
    }

    /**
     * Requires the value to be non-negative, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param value     The value to check.
     * @param fieldName The name of the field for the error message.
     */
    public static void requireNonNegative(long value, String fieldName) {
        validateNonNegative(value, fieldName).throwIfInvalid();
    }

    /**
     * Requires one value to be less than or equal to another, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param smaller     The smaller value.
     * @param larger      The larger value.
     * @param smallerName The name of the smaller value's field.
     * @param largerName  The name of the larger value's field.
     */
    public static void requireLessOrEqual(int smaller, int larger, String smallerName, String largerName) {
        validateLessOrEqual(smaller, larger, smallerName, largerName).throwIfInvalid();
    }

    /**
     * Requires the enum to be non-null, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param enumValue The enum to check.
     * @param fieldName The name of the field for the error message.
     */
    public static void requireValidEnum(Enum<?> enumValue, String fieldName) {
        validateEnum(enumValue, fieldName).throwIfInvalid();
    }

}
