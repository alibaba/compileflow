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

/**
 * Base interface for all CompileFlow events.
 * Provides the foundation for all process-related events in the system.
 *
 * @author yusu
 */
public interface ProcessEvent {

    /**
     * Gets the event type for quick categorization and filtering.
     *
     * @return The event type enum value
     */
    EventType getEventType();

    /**
     * Gets the timestamp when the event occurred.
     *
     * @return The event timestamp in milliseconds since epoch
     */
    long getTimestamp();

    /**
     * Gets the process code associated with this event, if applicable.
     *
     * @return The process code, or null if not applicable
     */
    String getProcessCode();

    /**
     * Gets the event context containing additional data (optional).
     *
     * @return The event context, or empty context if none provided
     */
    ProcessEventContext getContext();

    /**
     * Enumeration of all supported event types.
     */
    enum EventType {
        // Execution-related events
        EXECUTION_STARTED,
        EXECUTION_COMPLETED,
        EXECUTION_FAILED,

        // Trigger-related events
        TRIGGER_STARTED,
        TRIGGER_COMPLETED,
        TRIGGER_FAILED,

        // Compilation-related events
        COMPILATION_COMPLETED,
        COMPILATION_FAILED
    }

    /**
     * Abstract base class providing common implementation for all events.
     * Implements the ProcessEvent interface with standard behavior.
     */
    abstract class AbstractProcessEvent implements ProcessEvent {
        protected final EventType eventType;
        protected final long timestamp;
        protected final String processCode;
        protected final ProcessEventContext context;

        protected AbstractProcessEvent(EventType eventType, String processCode, ProcessEventContext context) {
            this.eventType = eventType;
            this.timestamp = System.currentTimeMillis();
            this.processCode = processCode;
            this.context = context != null ? context : ProcessEventContext.empty();
        }

        @Override
        public EventType getEventType() {
            return eventType;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String getProcessCode() {
            return processCode;
        }

        @Override
        public ProcessEventContext getContext() {
            return context;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                    "eventType=" + eventType +
                    ", timestamp=" + timestamp +
                    ", processCode='" + processCode + '\'' +
                    ", context=" + context +
                    '}';
        }
    }
}
