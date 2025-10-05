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
package com.alibaba.compileflow.engine.core.runtime.context;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.core.executor.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.executor.DynamicClassExecutor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Per-execution context: metadata, process context and engine resources.
 * Immutable after build; provides convenient accessors to engine executors.
 *
 * @author yusu
 */
public final class EngineExecutionContext {

    // Execution metadata
    private final String traceId;
    private final String processCode;
    private final long startTime;
    private final Map<String, Object> processContext;

    // Engine resources
    private final ProcessEngine processEngine;
    private final ProcessEngineExecutors executors;
    private final DynamicClassExecutor dynamicClassExecutor;

    private EngineExecutionContext(Builder builder) {
        this.traceId = builder.traceId;
        this.processCode = builder.processCode;
        this.startTime = builder.startTime;
        this.processContext = Collections.unmodifiableMap(new HashMap<>(builder.processContext));
        this.processEngine = builder.processEngine;
        this.executors = builder.executors;
        this.dynamicClassExecutor = builder.dynamicClassExecutor;
    }

    /**
     * Returns a new builder for creating execution contexts.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Convenient thread pool access methods

    /**
     * Returns the compilation executor for flow compilation tasks.
     */
    public ExecutorService compilation() {
        return executors.compilation();
    }

    /**
     * Returns the execution executor for flow execution tasks.
     */
    public ExecutorService execution() {
        return executors.execution();
    }

    /**
     * Returns the event executor for asynchronous event publishing.
     */
    public ExecutorService event() {
        return executors.event();
    }

    /**
     * Returns the schedule executor for scheduled tasks and timeouts.
     */
    public ScheduledExecutorService schedule() {
        return executors.schedule();
    }

    /**
     * Returns the engine-scoped dynamic class executor for Java source compilation.
     */
    public DynamicClassExecutor dynamicClassExecutor() {
        return dynamicClassExecutor;
    }

    // Execution metadata access

    /**
     * Returns the trace ID for this execution.
     */
    public String traceId() {
        return traceId;
    }

    /**
     * Returns the process code being executed.
     */
    public String processCode() {
        return processCode;
    }

    /**
     * Returns the execution start time in milliseconds.
     */
    public long startTime() {
        return startTime;
    }

    /**
     * Returns the execution duration in milliseconds.
     */
    public long duration() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Returns the process context (execution parameters).
     */
    public Map<String, Object> processContext() {
        return processContext;
    }

    /**
     * Returns the process engine instance.
     */
    public ProcessEngine processEngine() {
        return processEngine;
    }

    /**
     * Returns the engine executors.
     */
    public ProcessEngineExecutors executors() {
        return executors;
    }

    @Override
    public String toString() {
        return String.format("EngineExecutionContext{traceId='%s', processCode='%s', duration=%dms}",
                traceId, processCode, duration());
    }

    /**
     * Builder for creating immutable EngineExecutionContext instances.
     */
    public static final class Builder {
        private String traceId;
        private String processCode;
        private long startTime = System.currentTimeMillis();
        private Map<String, Object> processContext = new HashMap<>();
        private ProcessEngine processEngine;
        private ProcessEngineExecutors executors;
        private DynamicClassExecutor dynamicClassExecutor;

        private Builder() {
        }

        /**
         * Sets the trace ID for this execution.
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Sets the process code being executed.
         */
        public Builder processCode(String processCode) {
            this.processCode = Objects.requireNonNull(processCode, "processCode cannot be null");
            return this;
        }

        /**
         * Sets the execution start time. If not called, uses current time.
         */
        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * Sets the process context (execution parameters).
         */
        public Builder processContext(Map<String, Object> processContext) {
            this.processContext = processContext != null ? new HashMap<>(processContext) : new HashMap<>();
            return this;
        }

        /**
         * Sets the process engine instance. This automatically sets the executors.
         */
        public Builder processEngine(ProcessEngine processEngine) {
            this.processEngine = Objects.requireNonNull(processEngine, "processEngine cannot be null");
            return this;
        }

        /**
         * Sets the executors directly. This is preferred over processEngine() when available.
         */
        public Builder executors(ProcessEngineExecutors executors) {
            this.executors = Objects.requireNonNull(executors, "executors cannot be null");
            return this;
        }

        /**
         * Sets the engine-scoped dynamic class executor.
         */
        public Builder dynamicClassExecutor(DynamicClassExecutor dynamicClassExecutor) {
            this.dynamicClassExecutor = Objects.requireNonNull(dynamicClassExecutor, "dynamicClassExecutor cannot be null");
            return this;
        }

        /**
         * Builds the immutable EngineExecutionContext.
         *
         * @throws IllegalStateException if required fields are not set
         */
        public EngineExecutionContext build() {
            if (processCode == null) {
                throw new IllegalStateException("processCode is required");
            }
            if (executors == null) {
                throw new IllegalStateException("executors is required - set via executors() or processEngine()");
            }
            return new EngineExecutionContext(this);
        }
    }
}
