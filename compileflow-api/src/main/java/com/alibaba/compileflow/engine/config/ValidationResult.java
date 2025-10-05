/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a validation operation, encapsulating success or failure status,
 * along with lists of error and warning messages.
 * <p>
 * This class is immutable. Operations like {@link #merge(ValidationResult)}, {@link #addError(String)},
 * or {@link #addWarning(String)} return a new {@code ValidationResult} instance.
 *
 * @author yusu
 */
public final class ValidationResult {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationResult.class);

    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;

    private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = errors == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = warnings == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    private ValidationResult(boolean valid, List<String> errors) {
        this(valid, errors, Collections.emptyList());
    }

    /**
     * Creates a successful validation result with no messages.
     *
     * @return A new, successful {@code ValidationResult}.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with a single error message.
     *
     * @param error The error message.
     * @return A new, failed {@code ValidationResult}.
     */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, Collections.singletonList(error));
    }

    /**
     * Creates a failed validation result with a list of error messages.
     *
     * @param errors The list of error messages.
     * @return A new, failed {@code ValidationResult}.
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    /**
     * Creates a successful validation result with a single warning message.
     *
     * @param warning The warning message.
     * @return A new, successful {@code ValidationResult} with a warning.
     */
    public static ValidationResult warning(String warning) {
        return new ValidationResult(true, Collections.emptyList(), Collections.singletonList(warning));
    }

    /**
     * Creates a successful validation result with a list of warning messages.
     *
     * @param warnings The list of warning messages.
     * @return A new, successful {@code ValidationResult} with warnings.
     */
    public static ValidationResult warning(List<String> warnings) {
        return new ValidationResult(true, Collections.emptyList(), warnings);
    }

    /**
     * Merges this validation result with another. The merged result is considered valid only if both
     * original results were valid. Errors and warnings from both are combined.
     *
     * @param other The other {@code ValidationResult} to merge with.
     * @return A new {@code ValidationResult} representing the combination of both.
     */
    public ValidationResult merge(ValidationResult other) {
        if (other == null) {
            return this;
        }
        boolean ok = this.valid && other.valid;
        List<String> es = new ArrayList<>(this.errors);
        es.addAll(other.errors);
        List<String> ws = new ArrayList<>(this.warnings);
        ws.addAll(other.warnings);
        return new ValidationResult(ok, es, ws);
    }

    /**
     * Adds an error message, invalidating the result.
     *
     * @param error The error message to add.
     * @return A new, failed {@code ValidationResult} with the added error.
     */
    public ValidationResult addError(String error) {
        List<String> es = new ArrayList<>(this.errors);
        es.add(error);
        return new ValidationResult(false, es, this.warnings);
    }

    /**
     * Adds a warning message. This does not change the validity of the result.
     *
     * @param warning The warning message to add.
     * @return A new {@code ValidationResult} with the added warning.
     */
    public ValidationResult addWarning(String warning) {
        List<String> ws = new ArrayList<>(this.warnings);
        ws.add(warning);
        return new ValidationResult(this.valid, this.errors, ws);
    }

    /**
     * Logs any warnings and throws an {@link IllegalArgumentException} if the validation has failed.
     * If valid, this method does nothing.
     *
     * @throws IllegalArgumentException if the result is not valid.
     */
    public void throwIfInvalid() {
        if (!warnings.isEmpty()) {
            LOGGER.warn("Configuration warnings: {}", String.join("; ", warnings));
        }
        if (!valid) {
            throw new IllegalArgumentException("Validation failed: " + String.join("; ", errors));
        }
    }

    /**
     * Checks if the validation was successful (i.e., no errors).
     *
     * @return {@code true} if there are no errors, {@code false} otherwise.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns an unmodifiable list of error messages.
     *
     * @return The list of errors; will be empty if validation was successful.
     */
    public List<String> getErrorMessages() {
        return errors;
    }

    /**
     * Returns an unmodifiable list of warning messages.
     *
     * @return The list of warnings; may contain messages even for a successful validation.
     */
    public List<String> getWarningMessages() {
        return warnings;
    }

}
