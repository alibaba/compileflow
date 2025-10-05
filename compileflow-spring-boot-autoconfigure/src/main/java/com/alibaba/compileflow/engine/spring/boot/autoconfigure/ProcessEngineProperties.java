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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Streamlined configuration properties for CompileFlow engine.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow")
@Validated
public class ProcessEngineProperties {

    /**
     * Process model type: TBBPM or BPMN.
     * TBBPM is optimized for enterprise workflows, BPMN follows international standards.
     */
    @NotNull(message = "Model type cannot be null")
    private FlowModelType modelType = FlowModelType.TBBPM;

    /**
     * Enable parallel compilation of multiple processes.
     * Improves startup time but consumes more CPU resources.
     */
    private boolean parallelCompilationEnabled = true;

    /**
     * Thread pool configuration - the most critical performance settings.
     */
    @NotNull(message = "Executor configuration cannot be null")
    private ExecutorProperties executor = new ExecutorProperties();

    /**
     * Cache configuration for performance optimization.
     */
    @NotNull(message = "Cache configuration cannot be null")
    private CacheProperties cache = new CacheProperties();

    /**
     * Compilation behavior settings.
     */
    @NotNull(message = "Compilation configuration cannot be null")
    private CompilationProperties compilation = new CompilationProperties();

    /**
     * ClassLoader lifecycle management.
     */
    @NotNull(message = "ClassLoader configuration cannot be null")
    private ClassLoaderProperties classLoader = new ClassLoaderProperties();

    /**
     * Monitoring and observability settings.
     */
    @NotNull(message = "Observability configuration cannot be null")
    private ObservabilityProperties observability = new ObservabilityProperties();

    /**
     * Converts these Spring Boot properties into the core {@link ProcessEngineConfig} object.
     * This method acts as an adapter between the Spring Boot configuration world and
     * the CompileFlow engine's internal configuration model.
     *
     * @return A {@link ProcessEngineConfig} instance configured from these properties.
     */
    public ProcessEngineConfig toProcessEngineConfig() {
        ProcessExecutorConfig executorConfig = ProcessExecutorConfig.builder()
                .compilationThreads(executor.getCompilationThreads())
                .executionThreads(executor.getExecutionThreads())
                .build();

        return ProcessEngineConfig.builder(modelType)
                .parallelCompilation(parallelCompilationEnabled)
                .executors(executorConfig)
                .classLoader(Thread.currentThread().getContextClassLoader())
                .build();
    }

    // Getters and Setters
    public FlowModelType getModelType() {
        return modelType;
    }

    public void setModelType(FlowModelType modelType) {
        this.modelType = modelType;
    }

    public boolean isParallelCompilationEnabled() {
        return parallelCompilationEnabled;
    }

    public void setParallelCompilationEnabled(boolean parallelCompilationEnabled) {
        this.parallelCompilationEnabled = parallelCompilationEnabled;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorProperties executor) {
        this.executor = executor;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public CompilationProperties getCompilation() {
        return compilation;
    }

    public void setCompilation(CompilationProperties compilation) {
        this.compilation = compilation;
    }

    public ClassLoaderProperties getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoaderProperties classLoader) {
        this.classLoader = classLoader;
    }

    public ObservabilityProperties getObservability() {
        return observability;
    }

    public void setObservability(ObservabilityProperties observability) {
        this.observability = observability;
    }

    @Override
    public String toString() {
        return "ProcessEngineProperties{" +
                "modelType=" + modelType +
                ", parallelCompilationEnabled=" + parallelCompilationEnabled +
                ", executor=" + executor +
                '}';
    }

    /**
     * Simplified thread pool configuration.
     * Focus on the two critical pools: compilation and execution.
     */
    public static class ExecutorProperties {

        /**
         * Number of threads for process compilation.
         * Recommended: 1-4 threads (CPU/4 to CPU/2).
         */
        @Min(1)
        @Max(16)
        private Integer compilationThreads;

        /**
         * Number of threads for process execution.
         * Recommended: CPU cores to CPU*2.
         */
        @Min(1)
        @Max(64)
        private Integer executionThreads;

        public int getCompilationThreads() {
            return compilationThreads != null ? compilationThreads : ProcessExecutorConfig.defaultCompilationThreads();
        }

        public void setCompilationThreads(Integer compilationThreads) {
            this.compilationThreads = compilationThreads;
        }

        public int getExecutionThreads() {
            return executionThreads != null ? executionThreads : ProcessExecutorConfig.defaultExecutionThreads();
        }

        public void setExecutionThreads(Integer executionThreads) {
            this.executionThreads = executionThreads;
        }

        @Override
        public String toString() {
            return "ExecutorProperties{compilationThreads=" + getCompilationThreads() +
                    ", executionThreads=" + getExecutionThreads() + '}';
        }
    }

    /**
     * Essential cache settings for performance tuning.
     * Most users only need to configure runtime-max-size.
     */
    public static class CacheProperties {

        /**
         * Maximum entries in runtime cache (most important setting).
         * Higher values improve performance but use more memory.
         * Recommended: 100-500 (dev), 2000-10000 (prod).
         */
        @Min(100)
        @Max(100000)
        private Integer runtimeMaxSize;

        /**
         * Advanced cache settings (rarely need adjustment).
         */
        private Integer compileFuturesMaxSize;
        private Integer dynamicClassMaxSize;
        @Min(1)
        @Max(1440)
        private Integer dynamicClassExpireMinutes;

        // Getters and Setters
        public Integer getRuntimeMaxSize() {
            return runtimeMaxSize;
        }

        public void setRuntimeMaxSize(Integer runtimeMaxSize) {
            this.runtimeMaxSize = runtimeMaxSize;
        }

        public Integer getCompileFuturesMaxSize() {
            return compileFuturesMaxSize;
        }

        public void setCompileFuturesMaxSize(Integer compileFuturesMaxSize) {
            this.compileFuturesMaxSize = compileFuturesMaxSize;
        }

        public Integer getDynamicClassMaxSize() {
            return dynamicClassMaxSize;
        }

        public void setDynamicClassMaxSize(Integer dynamicClassMaxSize) {
            this.dynamicClassMaxSize = dynamicClassMaxSize;
        }

        public Integer getDynamicClassExpireMinutes() {
            return dynamicClassExpireMinutes;
        }

        public void setDynamicClassExpireMinutes(Integer dynamicClassExpireMinutes) {
            this.dynamicClassExpireMinutes = dynamicClassExpireMinutes;
        }
    }

    /**
     * Compilation behavior settings.
     * Default values work well for most use cases.
     */
    public static class CompilationProperties {

        /**
         * Compilation timeout in seconds.
         */
        @Min(1)
        @Max(60)
        private Integer timeoutSeconds;

        /**
         * Maximum retry attempts on compilation failure.
         */
        @Min(1)
        @Max(5)
        private Integer maxAttempts;

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    /**
     * ClassLoader lifecycle management.
     */
    public static class ClassLoaderProperties {

        /**
         * Maintenance interval in minutes.
         * How often to clean up unused dynamic classes.
         */
        @Min(1)
        @Max(1440)
        private Integer maintenanceIntervalMinutes;

        public Integer getMaintenanceIntervalMinutes() {
            return maintenanceIntervalMinutes;
        }

        public void setMaintenanceIntervalMinutes(Integer maintenanceIntervalMinutes) {
            this.maintenanceIntervalMinutes = maintenanceIntervalMinutes;
        }
    }

    /**
     * Monitoring and observability configuration.
     * Essential for production environments.
     */
    public static class ObservabilityProperties {

        /**
         * Enable all monitoring features.
         * Master switch for observability.
         */
        private boolean enabled = false;

        /**
         * Enable metrics collection.
         * Collects performance and throughput metrics.
         */
        private boolean metricsEnabled = true;

        /**
         * Process events asynchronously.
         * True: better performance, False: immediate processing.
         */
        private boolean eventsAsync = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }

        public boolean isEventsAsync() {
            return eventsAsync;
        }

        public void setEventsAsync(boolean eventsAsync) {
            this.eventsAsync = eventsAsync;
        }
    }
}
