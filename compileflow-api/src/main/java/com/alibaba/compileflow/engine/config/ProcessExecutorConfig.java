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

/**
 * Configuration for process engine executors, defining thread pool sizes for compilation and execution tasks.
 * <p>
 * This class is immutable and should be created using the static factory methods {@link #defaults()},
 * {@link #of(int, int)}, or via the {@link Builder}.
 *
 * @author yusu
 */
public class ProcessExecutorConfig implements Validatable {

    private final int compilationThreads;
    private final int executionThreads;

    private ProcessExecutorConfig(int compilationThreads, int executionThreads) {
        this.compilationThreads = compilationThreads;
        this.executionThreads = executionThreads;
    }

    /**
     * Returns a default executor configuration with a fixed number of compilation threads.
     */
    public static int defaultCompilationThreads() {
        return Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 8));
    }

    /**
     * Returns a default executor configuration with a fixed number of execution threads.
     */
    public static int defaultExecutionThreads() {
        return Math.max(4, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates an executor configuration with the specified thread counts.
     *
     * @param compilationThreads The number of threads for the compilation thread pool.
     * @param executionThreads   The number of threads for the execution thread pool.
     * @return A new {@link ProcessExecutorConfig} instance.
     */
    public static ProcessExecutorConfig of(int compilationThreads, int executionThreads) {
        return new ProcessExecutorConfig(compilationThreads, executionThreads);
    }

    /**
     * Returns a new {@link Builder} for creating a {@link ProcessExecutorConfig} instance.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    public int getCompilationThreads() {
        return compilationThreads;
    }

    public int getExecutionThreads() {
        return executionThreads;
    }

    /**
     * Gets the fixed number of event threads from the central property configuration.
     *
     * @return The number of event handler threads.
     */
    public int getEventThreads() {
        return ProcessPropertyProvider.Executor.getEventThreads();
    }

    /**
     * Gets the fixed number of scheduled threads from the central property configuration.
     *
     * @return The number of scheduler threads.
     */
    public int getScheduleThreads() {
        return ProcessPropertyProvider.Executor.getScheduleThreads();
    }

    @Override
    public ValidationResult validate() {
        ValidationResult result = ValidationResult.success();
        result = result.merge(validateBasic());
        result = result.merge(validateSemantic());
        result = result.merge(validateRuntime());
        return result;
    }

    private ValidationResult validateBasic() {
        return ProcessConfigValidator.combine(
                ProcessConfigValidator.validatePositive(compilationThreads, "compilationThreads"),
                ProcessConfigValidator.validatePositive(executionThreads, "executionThreads")
        );
    }

    private ValidationResult validateSemantic() {
        ValidationResult result = ValidationResult.success();

        if (compilationThreads > executionThreads) {
            result = result.addWarning(
                    "Compilation threads (" + compilationThreads + ") > execution threads (" + executionThreads +
                            ") may cause resource imbalance");
        }

        return result;
    }

    private ValidationResult validateRuntime() {
        ValidationResult result = ValidationResult.success();
        result = result.merge(ProcessConfigValidator.validateCpuRelated(compilationThreads, "compilationThreads"));
        result = result.merge(ProcessConfigValidator.validateCpuRelated(executionThreads, "executionThreads"));
        result = result.merge(ProcessConfigValidator.validateResourceBalance(this));
        return result;
    }

    @Override
    public String toString() {
        return String.format("ExecutorConfig{compilationThreads=%d, executionThreads=%d, eventThreads=%d(fixed), scheduleThreads=%d(fixed)}",
                compilationThreads, executionThreads, getEventThreads(), getScheduleThreads());
    }

    /**
     * A builder for creating {@link ProcessExecutorConfig} instances.
     * If a value is not set, it will fall back to the default calculated by {@link #defaults()}.
     */
    public static final class Builder {
        private int compilationThreads = -1;
        private int executionThreads = -1;

        /**
         * Sets the number of threads for the compilation thread pool.
         *
         * @param threads The number of compilation threads.
         * @return This builder instance for chaining.
         */
        public Builder compilationThreads(int threads) {
            this.compilationThreads = threads;
            return this;
        }

        /**
         * Sets the number of threads for the execution thread pool.
         *
         * @param threads The number of execution threads.
         * @return This builder instance for chaining.
         */
        public Builder executionThreads(int threads) {
            this.executionThreads = threads;
            return this;
        }

        /**
         * Builds and validates the {@link ProcessExecutorConfig}.
         *
         * @return A new, validated {@link ProcessExecutorConfig} instance.
         */
        public ProcessExecutorConfig build() {
            ProcessExecutorConfig config = new ProcessExecutorConfig(
                    compilationThreads > 0 ? compilationThreads : defaultCompilationThreads(),
                    executionThreads > 0 ? executionThreads : defaultExecutionThreads());

            config.validateAndThrow();
            return config;
        }
    }

}
