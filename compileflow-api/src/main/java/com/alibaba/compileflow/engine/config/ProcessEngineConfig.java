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

import com.alibaba.compileflow.engine.common.FlowModelType;

/**
 * Configuration for the ProcessEngine, constructed via a layered builder API.
 *
 * @author yusu
 */
public class ProcessEngineConfig implements Validatable {

    private final FlowModelType modelType;
    private final boolean parallelCompilationEnabled;
    private final ProcessExecutorConfig executorConfig;
    private final ClassLoader classLoader;

    private ProcessEngineConfig(Builder builder) {
        this.modelType = builder.modelType;
        this.parallelCompilationEnabled = builder.parallelCompilationEnabled;
        this.executorConfig = builder.executorConfig;
        this.classLoader = builder.classLoader;
    }

    /**
     * Creates a default configuration for TBBPM.
     */
    public static ProcessEngineConfig tbbpm() {
        return new Builder(FlowModelType.TBBPM).build();
    }

    /**
     * Creates a default configuration for BPMN.
     */
    public static ProcessEngineConfig bpmn() {
        return new Builder(FlowModelType.BPMN).build();
    }

    /**
     * Returns a new builder for creating a TBBPM configuration.
     */
    public static Builder tbbpmBuilder() {
        return new Builder(FlowModelType.TBBPM);
    }

    /**
     * Returns a new builder for creating a BPMN configuration.
     */
    public static Builder bpmnBuilder() {
        return new Builder(FlowModelType.BPMN);
    }

    /**
     * Returns a new builder for the specified model type.
     */
    public static Builder builder(FlowModelType modelType) {
        return new Builder(modelType);
    }

    /**
     * Creates a TBBPM configuration with a custom number of execution threads.
     */
    public static ProcessEngineConfig tbbpm(int executionThreads) {
        return tbbpmBuilder()
                .executionThreads(executionThreads)
                .build();
    }

    /**
     * Creates a TBBPM configuration with a custom executor configuration.
     */
    public static ProcessEngineConfig tbbpm(ProcessExecutorConfig executors) {
        return tbbpmBuilder()
                .executors(executors)
                .build();
    }

    /**
     * Creates a BPMN configuration with a custom number of execution threads.
     */
    public static ProcessEngineConfig bpmn(int executionThreads) {
        return bpmnBuilder()
                .executionThreads(executionThreads)
                .build();
    }

    /**
     * Creates a BPMN configuration with a custom executor configuration.
     */
    public static ProcessEngineConfig bpmn(ProcessExecutorConfig executors) {
        return bpmnBuilder()
                .executors(executors)
                .build();
    }

    public FlowModelType getModelType() {
        return modelType;
    }

    public boolean isParallelCompilationEnabled() {
        return parallelCompilationEnabled;
    }

    public ProcessExecutorConfig getExecutorConfig() {
        return executorConfig;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public ValidationResult validate() {
        ValidationResult result = ValidationResult.success();
        result = result.merge(validateBasic());
        result = result.merge(validateSemantic());
        if (executorConfig != null) {
            result = result.merge(executorConfig.validate());
        } else {
            result = result.merge(ValidationResult.failure("executorConfig cannot be null"));
        }
        return result;
    }

    private ValidationResult validateBasic() {
        return ProcessConfigValidator.combine(
                ProcessConfigValidator.validateNotNull(modelType, "flowModelType"),
                ProcessConfigValidator.validateNotNull(executorConfig, "executorConfig"),
                ProcessConfigValidator.validateClassLoader(classLoader)
        );
    }

    private ValidationResult validateSemantic() {
        ValidationResult result = ValidationResult.success();

        // Check parallel compilation configuration consistency
        if (parallelCompilationEnabled && executorConfig.getCompilationThreads() == 1) {
            result = result.addWarning(
                    "Parallel compilation enabled but only 1 compilation thread configured. " +
                            "Consider increasing compilation threads or disabling parallel compilation.");
        }

        return result;
    }

    @Override
    public String toString() {
        return String.format("ProcessEngineConfig{type=%s, parallel=%s, executors=%s}",
                modelType, parallelCompilationEnabled, executorConfig);
    }

    public static final class Builder {
        private final FlowModelType modelType;
        private boolean parallelCompilationEnabled = ProcessPropertyProvider.Compilation.isParallelCompilationEnabled();
        private ProcessExecutorConfig executorConfig = ProcessExecutorConfig.of(ProcessExecutorConfig.defaultCompilationThreads(),
                ProcessExecutorConfig.defaultExecutionThreads());
        private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        private Builder(FlowModelType modelType) {
            this.modelType = modelType;
        }

        public Builder parallelCompilation(boolean enabled) {
            this.parallelCompilationEnabled = enabled;
            return this;
        }

        public Builder executors(ProcessExecutorConfig config) {
            this.executorConfig = config;
            return this;
        }

        public Builder executionThreads(int executionThreads) {
            this.executorConfig = ProcessExecutorConfig.of(ProcessExecutorConfig.defaultCompilationThreads(), executionThreads);
            return this;
        }

        public Builder threads(int compilationThreads, int executionThreads) {
            this.executorConfig = ProcessExecutorConfig.of(compilationThreads, executionThreads);
            return this;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Builds and validates the configuration.
         *
         * @return A validated, immutable ProcessEngineConfig instance.
         */
        public ProcessEngineConfig build() {
            ProcessEngineConfig config = new ProcessEngineConfig(this);
            config.validateAndThrow();
            return config;
        }
    }

}
