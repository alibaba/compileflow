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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The base exception for all runtime errors within the CompileFlow framework.
 * <p>
 * This class provides a structured exception with an {@link ErrorCode},
 * a descriptive message, an optional cause, and a context map for carrying diagnostic information.
 *
 * @author yusu
 * @see ErrorCode
 */
public class CompileFlowException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final int MAX_CONTEXT_ENTRIES = 32;
    private static final int MAX_CONTEXT_KEY_LENGTH = 128;
    private static final int MAX_CONTEXT_TEXT_LENGTH = 2_048;
    private static final int MAX_CONTEXT_COLLECTION_ENTRIES = 32;
    private static final String OMITTED_VALUE = "<omitted>";
    private static final String TRUNCATED_VALUE = "<truncated>";
    /**
     * Stable failure classification.
     *
     * @serial stable error code
     */
    private final ErrorCode errorCode;
    /**
     * Process-local, bounded diagnostic metadata; never part of the serialized exception form.
     */
    private transient Map<String, Object> context;

    /**
     * Creates a structured CompileFlow failure without an underlying cause.
     *
     * @param errorCode stable failure classification
     * @param message   diagnostic message
     */
    public CompileFlowException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Creates a structured CompileFlow failure.
     *
     * @param errorCode stable failure classification
     * @param message   diagnostic message
     * @param cause     underlying failure, or {@code null}
     */
    public CompileFlowException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.context = new LinkedHashMap<>();
    }

    /**
     * Adds a key-value pair to the exception's diagnostic context.
     *
     * <p>Context is for small, bounded diagnostic metadata such as a node ID, attempt number, or
     * digest. Callers must not attach process-variable maps, raw payloads or source, credentials,
     * bearer tokens, secrets, or other sensitive or potentially large application objects. Error
     * reporters and observability integrations may inspect this map. The exception retains at
     * most {@value #MAX_CONTEXT_ENTRIES} entries; keys are limited to {@value
     * #MAX_CONTEXT_KEY_LENGTH} characters and textual values to {@value
     * #MAX_CONTEXT_TEXT_LENGTH} characters. Unsafe or oversized diagnostic values are replaced
     * with a fixed marker so diagnostic enrichment can never change process execution outcome.
     *
     * @param key   The context key.
     * @param value The context value.
     * @return This exception instance for method chaining.
     */
    public final CompileFlowException withContext(String key, Object value) {
        putBounded(context, key, value);
        return this;
    }

    /**
     * Adds all entries from the given map to the exception's diagnostic context.
     *
     * <p>Every entry is subject to the same bounded, non-sensitive metadata contract as {@link
     * #withContext(String, Object)}. Entries are normalized into an immutable bounded snapshot;
     * unsafe entries beyond the context budget are ignored.
     *
     * @param contextMap A map containing context entries to add.
     * @return This exception instance for method chaining.
     */
    public final CompileFlowException withContext(Map<String, Object> contextMap) {
        if (contextMap != null) {
            Map<String, Object> candidate = new LinkedHashMap<>(context);
            try {
                for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
                    putBounded(candidate, entry.getKey(), entry.getValue());
                    if (candidate.size() >= MAX_CONTEXT_ENTRIES) {
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                return this;
            }
            context.clear();
            context.putAll(candidate);
        }
        return this;
    }

    private static void putBounded(Map<String, Object> target, String key, Object value) {
        String normalizedKey = normalizeContextKey(key);
        if (normalizedKey == null || value == null) {
            return;
        }
        if (!target.containsKey(normalizedKey) && target.size() >= MAX_CONTEXT_ENTRIES) {
            return;
        }
        target.put(normalizedKey, normalizeContextValue(value, true));
    }

    private static String normalizeContextKey(String key) {
        if (key == null) {
            return null;
        }
        String candidate = key.trim();
        if (candidate.isEmpty() || candidate.length() > MAX_CONTEXT_KEY_LENGTH) {
            return null;
        }
        return candidate;
    }

    private static Object normalizeContextValue(Object value, boolean allowCollection) {
        try {
            if (value instanceof CharSequence text) {
                return boundedText(text.toString());
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double || value instanceof Boolean
                    || value instanceof Character) {
                return value;
            }
            if (value instanceof Enum<?> enumeration) {
                return boundedText(enumeration.name());
            }
            if (value instanceof Class<?> type) {
                return boundedText(type.getName());
            }
            if (value instanceof UUID || value instanceof TemporalAccessor || value instanceof TemporalAmount) {
                return boundedText(value.toString());
            }
            if (allowCollection && value instanceof Collection<?> collection) {
                return boundedCollection(collection);
            }
        } catch (RuntimeException ignored) {
            return OMITTED_VALUE;
        }
        return OMITTED_VALUE;
    }

    private static List<Object> boundedCollection(Collection<?> collection) {
        List<Object> result = new ArrayList<>(Math.min(collection.size(), MAX_CONTEXT_COLLECTION_ENTRIES));
        int index = 0;
        for (Object item : collection) {
            if (index == MAX_CONTEXT_COLLECTION_ENTRIES - 1 && collection.size() > MAX_CONTEXT_COLLECTION_ENTRIES) {
                result.add(TRUNCATED_VALUE);
                break;
            }
            result.add(item == null ? OMITTED_VALUE : normalizeContextValue(item, false));
            index++;
            if (index >= MAX_CONTEXT_COLLECTION_ENTRIES) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static String boundedText(String value) {
        return value.length() <= MAX_CONTEXT_TEXT_LENGTH ? value : OMITTED_VALUE;
    }

    /**
     * Returns the stable error classification for this failure.
     *
     * @return The standardized {@link ErrorCode} associated with this exception.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns immutable diagnostic context accumulated for this failure.
     *
     * <p>The returned map is an immutable snapshot. Context is process-local and is deliberately
     * discarded by Java serialization.
     *
     * @return An unmodifiable view of the diagnostic context map.
     */
    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * Formats the stable error code together with the exception message.
     *
     * @return A detailed message including the error code and the exception message.
     */
    public String getDetailedMessage() {
        return String.format(Locale.ROOT, "[%s] %s", errorCode.getCode(), getMessage());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append(": [").append(errorCode.getCode()).append("] ").append(
                getMessage());

        if (!context.isEmpty()) {
            sb.append(" (contextKeys: ").append(context.keySet()).append(")");
        }

        return sb.toString();
    }

    /**
     * Restores the exception while intentionally replacing process-local diagnostic context.
     *
     * @param input serialized exception input
     * @throws IOException            when the serialized state cannot be read
     * @throws ClassNotFoundException when a serialized field type cannot be resolved
     */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        context = new LinkedHashMap<>();
    }

    /**
     * Represents an error that occurred during the configuration or startup phase of the engine.
     */
    public static class ConfigurationException extends CompileFlowException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates a configuration failure without an underlying cause.
         *
         * @param errorCode stable configuration failure classification
         * @param message   diagnostic message
         */
        public ConfigurationException(ErrorCode errorCode, String message) {
            super(errorCode, message, null);
        }

        /**
         * Creates a configuration failure with an underlying cause.
         *
         * @param errorCode stable configuration failure classification
         * @param message   diagnostic message
         * @param cause     underlying failure
         */
        public ConfigurationException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents a failure to access or load an external resource, such as a classpath process definition.
     */
    public static class ResourceException extends CompileFlowException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates a resource access failure.
         *
         * @param errorCode stable resource error classification
         * @param message   diagnostic message
         * @param cause     underlying resource failure
         */
        public ResourceException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Represents the failure of an operation due to exceeding a defined timeout.
     */
    public static class TimeoutException extends CompileFlowException {
        private static final long serialVersionUID = 1L;
        /**
         * Configured operation timeout in milliseconds.
         *
         * @serial configured timeout
         */
        private final long timeoutMs;

        /**
         * Creates a timeout failure and records the configured timeout in diagnostic context.
         *
         * @param errorCode stable timeout error classification
         * @param message   diagnostic message
         * @param timeoutMs configured timeout in milliseconds
         */
        public TimeoutException(ErrorCode errorCode, String message, long timeoutMs) {
            super(errorCode, message, null);
            this.timeoutMs = timeoutMs;
            withContext("timeoutMs", timeoutMs);
        }

        /**
         * Returns configured timeout in milliseconds.
         *
         * @return configured timeout in milliseconds
         */
        public long getTimeoutMs() {
            return timeoutMs;
        }
    }

    /**
     * Represents a parameter or input validation failure.
     * This exception typically includes the name of the field that failed validation.
     */
    public static class ValidationException extends CompileFlowException {
        private static final long serialVersionUID = 1L;
        /**
         * Input field that failed validation.
         *
         * @serial validated field name
         */
        private final String fieldName;

        /**
         * Creates a validation failure for one named input field.
         *
         * @param fieldName field that failed validation
         * @param message   diagnostic validation message
         */
        public ValidationException(String fieldName, String message) {
            super(ErrorCode.CF_VALIDATION_001, message, null);
            this.fieldName = fieldName;
            withContext("field", fieldName);
        }

        /**
         * Creates a validation failure for one named input field with an underlying cause.
         *
         * @param fieldName field that failed validation
         * @param message   diagnostic validation message
         * @param cause     underlying conversion or validation failure
         */
        public ValidationException(String fieldName, String message, Throwable cause) {
            super(ErrorCode.CF_VALIDATION_001, message, cause);
            this.fieldName = fieldName;
            withContext("field", fieldName);
        }

        /**
         * Returns input field that failed validation.
         *
         * @return input field that failed validation
         */
        public String getFieldName() {
            return fieldName;
        }
    }
}
