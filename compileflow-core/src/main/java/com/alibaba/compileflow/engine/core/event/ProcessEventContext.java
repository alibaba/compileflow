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
package com.alibaba.compileflow.engine.core.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Process event context that encapsulates event-related data.
 * Minimal core keys are provided; additional data can be carried via properties.
 *
 * @author yusu
 */
public class ProcessEventContext {

    public static final String DURATION_MS = "durationMs";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String THREAD_NAME = "threadName";
    public static final String TRACE_ID = "traceId";

    private final Map<String, Object> properties;

    private ProcessEventContext(Map<String, Object> properties) {
        this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
    }

    /**
     * Creates an empty event context.
     */
    public static ProcessEventContext empty() {
        return new ProcessEventContext(Collections.emptyMap());
    }

    /**
     * Returns a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) properties.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        return properties.containsKey(key) ? (T) properties.get(key) : defaultValue;
    }

    public boolean contains(String key) {
        return properties.containsKey(key);
    }

    public Map<String, Object> getAllProperties() {
        return properties;
    }

    public Long getDurationMs() {
        return get(DURATION_MS);
    }

    public String getErrorMessage() {
        return get(ERROR_MESSAGE);
    }

    public String getThreadName() {
        return get(THREAD_NAME);
    }

    @Override
    public String toString() {
        return "ProcessEventContext{" +
                "properties=" + properties.size() + " items" +
                '}';
    }

    public static class Builder {
        private final Map<String, Object> properties = new HashMap<>();

        private Builder() {
        }

        public Builder put(String key, Object value) {
            properties.put(key, value);
            return this;
        }

        public Builder durationMs(Long durationMs) {
            if (durationMs != null) {
                properties.put(DURATION_MS, durationMs);
            }
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            if (errorMessage != null) {
                properties.put(ERROR_MESSAGE, errorMessage);
            }
            return this;
        }

        public Builder threadName(String threadName) {
            if (threadName != null) {
                properties.put(THREAD_NAME, threadName);
            }
            return this;
        }

        public Builder traceId(String traceId) {
            if (traceId != null) {
                properties.put(TRACE_ID, traceId);
            }
            return this;
        }

        public ProcessEventContext build() {
            return new ProcessEventContext(properties);
        }
    }

}
