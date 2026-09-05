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
package com.alibaba.compileflow.engine.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable execution capacity, backpressure, and cancellation-safety settings
 * owned by one process engine.
 *
 * @author yusu
 */
public final class ProcessExecutorConfig {
    private static final int DEFAULT_RUNTIME_LOAD_MAX_PENDING = 4;
    private static final int DEFAULT_ACTION_TIMEOUT_MAX_PENDING = 32;
    private static final Duration DEFAULT_ACTION_TIMEOUT_CANCELLATION_GRACE_PERIOD = Duration.ofSeconds(2);
    private static final Duration DEFAULT_PARALLEL_CANCELLATION_GRACE_PERIOD = Duration.ofSeconds(2);
    private final int runtimeLoadMaxConcurrency;
    private final int runtimeLoadMaxPending;
    private final int actionTimeoutMaxConcurrency;
    private final int actionTimeoutMaxPending;
    private final Duration actionTimeoutCancellationGracePeriod;
    private final Duration parallelCancellationGracePeriod;

    private ProcessExecutorConfig(Builder builder) {
        this.runtimeLoadMaxConcurrency = builder.runtimeLoadMaxConcurrency;
        this.runtimeLoadMaxPending = builder.runtimeLoadMaxPending;
        this.actionTimeoutMaxConcurrency = builder.actionTimeoutMaxConcurrency;
        this.actionTimeoutMaxPending = builder.actionTimeoutMaxPending;
        this.actionTimeoutCancellationGracePeriod = builder.actionTimeoutCancellationGracePeriod;
        this.parallelCancellationGracePeriod = builder.parallelCancellationGracePeriod;
    }

    /**
     * Returns the CPU-aware default runtime-load concurrency limit.
     *
     * @return one or two concurrent runtime loads depending on available processors
     */
    public static int defaultRuntimeLoadMaxConcurrency() {
        return Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 8));
    }

    /**
     * Returns the CPU-aware default concurrency limit for timeout-enforced actions.
     *
     * @return at least four concurrent actions
     */
    public static int defaultActionTimeoutMaxConcurrency() {
        return Math.max(4, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a builder initialized with production defaults.
     *
     * @return executor configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates the default executor configuration.
     *
     * @return validated CPU-aware defaults
     */
    public static ProcessExecutorConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a builder initialized from this immutable snapshot.
     *
     * @return mutable builder carrying all current values
     */
    public Builder toBuilder() {
        return new Builder()
            .runtimeLoadMaxConcurrency(runtimeLoadMaxConcurrency)
            .runtimeLoadMaxPending(runtimeLoadMaxPending)
            .actionTimeoutMaxConcurrency(actionTimeoutMaxConcurrency)
            .actionTimeoutMaxPending(actionTimeoutMaxPending)
            .actionTimeoutCancellationGracePeriod(actionTimeoutCancellationGracePeriod)
            .parallelCancellationGracePeriod(parallelCancellationGracePeriod);
    }

    /**
     * Returns the maximum number of unique runtime loads that may run concurrently.
     *
     * @return maximum concurrent runtime loads
     */
    public int getRuntimeLoadMaxConcurrency() {
        return runtimeLoadMaxConcurrency;
    }

    /**
     * Returns the maximum number of unique runtime loads that may wait when all
     * execution slots are occupied.
     *
     * @return maximum pending runtime loads, or zero for fail-fast admission
     */
    public int getRuntimeLoadMaxPending() {
        return runtimeLoadMaxPending;
    }

    /**
     * Returns the concurrency limit for actions that require timeout enforcement.
     *
     * @return concurrent action limit
     */
    public int getActionTimeoutMaxConcurrency() {
        return actionTimeoutMaxConcurrency;
    }

    /**
     * Returns the maximum number of timeout-enforced action attempts that may
     * wait when all execution slots are occupied.
     *
     * @return maximum pending action attempts, or zero for fail-fast admission
     */
    public int getActionTimeoutMaxPending() {
        return actionTimeoutMaxPending;
    }

    /**
     * Returns how long a timed-out or cancelled action waits for the worker to
     * stop before the execution fails closed.
     *
     * @return non-negative cancellation drain budget
     */
    public Duration getActionTimeoutCancellationGracePeriod() {
        return actionTimeoutCancellationGracePeriod;
    }

    /**
     * Returns how long a failed parallel gateway waits for interrupted sibling
     * branches to stop cooperatively.
     *
     * @return non-negative cancellation drain budget
     */
    public Duration getParallelCancellationGracePeriod() {
        return parallelCancellationGracePeriod;
    }

    ValidationResult validate() {
        ValidationResult runtimeConcurrency =
                ProcessConfigValidator.validatePositive(runtimeLoadMaxConcurrency, "executor.runtimeLoadMaxConcurrency");
        ValidationResult result = ProcessConfigValidator.combine(runtimeConcurrency,
                ProcessConfigValidator.validateNonNegative(runtimeLoadMaxPending, "executor.runtimeLoadMaxPending"),
                ProcessConfigValidator.validatePositive(actionTimeoutMaxConcurrency,
                        "executor.actionTimeoutMaxConcurrency"),
                ProcessConfigValidator.validateNonNegative(actionTimeoutMaxPending, "executor.actionTimeoutMaxPending"));
        if ((long) runtimeLoadMaxConcurrency + runtimeLoadMaxPending > Integer.MAX_VALUE) {
            result = result.addError(
                    "executor.runtimeLoadMaxConcurrency + runtimeLoadMaxPending must not exceed " + Integer.MAX_VALUE);
        }
        result = result.merge(ProcessConfigValidator.validateNonNegativeDurationMillis(actionTimeoutCancellationGracePeriod,
                "executor.actionTimeoutCancellationGracePeriod"));
        result = result.merge(ProcessConfigValidator.validateNonNegativeDurationMillis(parallelCancellationGracePeriod,
                "executor.parallelCancellationGracePeriod"));
        return result;
    }

    @Override
    public String toString() {
        return "ProcessExecutorConfig{runtimeLoad=" + runtimeLoadMaxConcurrency + "/pending=" + runtimeLoadMaxPending
                + ", actionTimeout=" + actionTimeoutMaxConcurrency + "/pending=" + actionTimeoutMaxPending
                + "/cancellation=" + actionTimeoutCancellationGracePeriod + ", parallelCancellation="
                + parallelCancellationGracePeriod + '}';
    }

    /**
     * Builder for {@link ProcessExecutorConfig}.
     */
    public static final class Builder {
        private int runtimeLoadMaxConcurrency = defaultRuntimeLoadMaxConcurrency();
        private int runtimeLoadMaxPending = DEFAULT_RUNTIME_LOAD_MAX_PENDING;
        private int actionTimeoutMaxConcurrency = defaultActionTimeoutMaxConcurrency();
        private int actionTimeoutMaxPending = DEFAULT_ACTION_TIMEOUT_MAX_PENDING;
        private Duration actionTimeoutCancellationGracePeriod = DEFAULT_ACTION_TIMEOUT_CANCELLATION_GRACE_PERIOD;
        private Duration parallelCancellationGracePeriod = DEFAULT_PARALLEL_CANCELLATION_GRACE_PERIOD;

        private Builder() {
        }

        /**
         * Sets the maximum number of unique runtime loads that may run concurrently.
         *
         * @param value positive runtime-load concurrency limit
         * @return this builder
         */
        public Builder runtimeLoadMaxConcurrency(int value) {
            this.runtimeLoadMaxConcurrency = value;
            return this;
        }

        /**
         * Sets the maximum number of unique runtime loads that may wait for an
         * execution slot.
         *
         * @param value non-negative pending-load limit; zero rejects instead of waiting
         * @return this builder
         */
        public Builder runtimeLoadMaxPending(int value) {
            this.runtimeLoadMaxPending = value;
            return this;
        }

        /**
         * Sets the concurrency limit for actions that require timeout enforcement.
         *
         * @param value positive concurrent action limit
         * @return this builder
         */
        public Builder actionTimeoutMaxConcurrency(int value) {
            this.actionTimeoutMaxConcurrency = value;
            return this;
        }

        /**
         * Sets the maximum number of timeout-enforced action attempts that may
         * wait for an execution slot.
         *
         * @param value non-negative pending-attempt limit; zero rejects instead of waiting
         * @return this builder
         */
        public Builder actionTimeoutMaxPending(int value) {
            this.actionTimeoutMaxPending = value;
            return this;
        }

        /**
         * Sets the cooperative cancellation drain budget for timed-out or
         * cancelled actions.
         *
         * @param value non-negative whole-millisecond duration; zero disables extra drain time
         * @return this builder
         */
        public Builder actionTimeoutCancellationGracePeriod(Duration value) {
            this.actionTimeoutCancellationGracePeriod = Objects.requireNonNull(value,
                    "actionTimeoutCancellationGracePeriod must not be null");
            return this;
        }

        /**
         * Sets the cooperative cancellation drain budget for failed parallel
         * gateways.
         *
         * @param value non-negative whole-millisecond duration; zero disables extra drain time
         * @return this builder
         */
        public Builder parallelCancellationGracePeriod(Duration value) {
            this.parallelCancellationGracePeriod = Objects.requireNonNull(value,
                    "parallelCancellationGracePeriod must not be null");
            return this;
        }

        /**
         * Builds and validates an immutable executor configuration.
         *
         * @return validated executor configuration
         */
        public ProcessExecutorConfig build() {
            ProcessExecutorConfig config = new ProcessExecutorConfig(this);
            config.validate().throwIfInvalid();
            return config;
        }
    }
}
