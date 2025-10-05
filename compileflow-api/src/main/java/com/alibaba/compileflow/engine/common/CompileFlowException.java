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
package com.alibaba.compileflow.engine.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The base exception for all runtime errors within the CompileFlow framework.
 * <p>
 * This abstract class provides a structured exception with an {@link ErrorCode},
 * a descriptive message, an optional cause, and a context map for carrying diagnostic information.
 * It also includes a timestamp for when the exception occurred.
 * <p>
 * Subclasses should be used to represent specific categories of errors, such as system-level
 * issues or business validation failures.
 *
 * @author yusu
 * @see ErrorCode
 */
public abstract class CompileFlowException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> context;
    private final long timestamp;

    protected CompileFlowException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Adds a key-value pair to the exception's diagnostic context.
     * This allows for attaching relevant data (e.g., process variables, node IDs) to the exception
     * for improved debugging and logging.
     *
     * @param key   The context key.
     * @param value The context value.
     * @return This exception instance for method chaining.
     */
    public CompileFlowException withContext(String key, Object value) {
        if (key != null && value != null) {
            this.context.put(key, value);
        }
        return this;
    }

    /**
     * Adds all entries from the given map to the exception's diagnostic context.
     *
     * @param contextMap A map containing context entries to add.
     * @return This exception instance for method chaining.
     */
    public CompileFlowException withContext(Map<String, Object> contextMap) {
        if (contextMap != null) {
            this.context.putAll(contextMap);
        }
        return this;
    }

    /**
     * @return The standardized {@link ErrorCode} associated with this exception.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return An unmodifiable view of the diagnostic context map.
     */
    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }

    /**
     * @return The timestamp (in milliseconds since the epoch) when this exception was created.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return A detailed message including the error code and the exception message.
     */
    public String getDetailedMessage() {
        return String.format("[%s] %s", errorCode.getCode(), getMessage());
    }

    /**
     * @return {@code true} if this exception is an instance of {@link BusinessException}, {@code false} otherwise.
     */
    public boolean isBusinessException() {
        return this instanceof BusinessException;
    }

    /**
     * @return {@code true} if this exception is an instance of {@link SystemException}, {@code false} otherwise.
     */
    public boolean isSystemException() {
        return this instanceof SystemException;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName())
                .append(": [").append(errorCode.getCode()).append("] ")
                .append(getMessage());

        if (!context.isEmpty()) {
            sb.append(" (context: ").append(context).append(")");
        }

        return sb.toString();
    }

    /**
     * Represents a business-layer exception that is generally predictable and potentially recoverable.
     * These exceptions typically relate to invalid inputs or business rule violations.
     */
    public static class BusinessException extends CompileFlowException {

        public BusinessException(ErrorCode errorCode, String message) {
            super(errorCode, message, null);
        }

        public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents an unexpected system-level failure.
     * These exceptions often indicate bugs, misconfigurations, or environmental issues.
     */
    public static class SystemException extends CompileFlowException {

        public SystemException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents an error that occurred during the configuration or startup phase of the engine.
     */
    public static class ConfigurationException extends CompileFlowException {

        public ConfigurationException(ErrorCode errorCode, String message) {
            super(errorCode, message, null);
        }

        public ConfigurationException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents a failure to access or load an external resource, such as a process definition file.
     */
    public static class ResourceException extends CompileFlowException {

        public ResourceException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents the failure of an operation due to exceeding a defined timeout.
     */
    public static class TimeoutException extends CompileFlowException {

        private final long timeoutMs;

        public TimeoutException(ErrorCode errorCode, String message, long timeoutMs) {
            super(errorCode, message, null);
            this.timeoutMs = timeoutMs;
            withContext("timeoutMs", timeoutMs);
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }
    }

    /**
     * Represents a parameter or input validation failure.
     * This exception typically includes the name of the field that failed validation.
     */
    public static class ValidationException extends CompileFlowException {

        private final String fieldName;

        public ValidationException(String fieldName, String message) {
            super(ErrorCode.CF_VALIDATION_001, message, null);
            this.fieldName = fieldName;
            withContext("field", fieldName);
        }

        public String getFieldName() {
            return fieldName;
        }
    }


}
