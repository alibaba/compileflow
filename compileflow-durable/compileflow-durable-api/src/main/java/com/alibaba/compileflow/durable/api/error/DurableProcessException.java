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
package com.alibaba.compileflow.durable.api.error;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured Durable Process failure with redacted bounded context.
 *
 * @author yusu
 */
public final class DurableProcessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final DurableErrorCode errorCode;
    /**
     * Private serializable snapshot; the mutable representation is never exposed.
     */
    private final LinkedHashMap<String, String> context;

    private DurableProcessException(DurableErrorCode errorCode, String message, Throwable cause,
            Map<String, String> context) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.context = immutableContext(context);
    }

    public static DurableProcessException of(DurableErrorCode errorCode, String message) {
        return new DurableProcessException(errorCode, message, null, Map.of());
    }

    public static DurableProcessException of(DurableErrorCode errorCode, String message, Throwable cause) {
        return new DurableProcessException(errorCode, message, cause, Map.of());
    }

    public static Builder builder(DurableErrorCode errorCode, String message) {
        return new Builder(errorCode, message, null);
    }

    public static Builder builder(DurableErrorCode errorCode, String message, Throwable cause) {
        return new Builder(errorCode, message, cause);
    }

    private static LinkedHashMap<String, String> immutableContext(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (source.size() > 16) {
            throw new IllegalArgumentException("context must not contain more than 16 entries");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(DurableIdentifiers.requireIdentity(key, "context key", 64),
                DurableIdentifiers.requireHumanText(value, "context value", 256)));
        return copy;
    }

    public DurableErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getContext() {
        return Collections.unmodifiableMap(context);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{errorCode=" + errorCode + ", message='" + getMessage() + "', contextKeys="
                + context.keySet() + '}';
    }

    /**
     * Fluent builder for a bounded, redacted Durable failure.
     */
    public static final class Builder {
        private final DurableErrorCode errorCode;
        private final String message;
        private final Throwable cause;
        private final Map<String, String> context = new LinkedHashMap<>();

        private Builder(DurableErrorCode errorCode, String message, Throwable cause) {
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
            this.message = Objects.requireNonNull(message, "message");
            this.cause = cause;
        }

        public Builder context(String key, String value) {
            context.put(key, value);
            return this;
        }

        public DurableProcessException build() {
            return new DurableProcessException(errorCode, message, cause, context);
        }
    }
}
