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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable Spring binding DTO for engine-owned executors.
 *
 * @author yusu
 */
public final class EngineExecutorProperties {
    /**
     * Local runtime materialization capacity.
     */
    @Valid
    @NotNull
    private final RuntimeLoad runtimeLoad;
    /**
     * Bounded executor used for actions that require timeout enforcement.
     */
    @Valid
    @NotNull
    private final ActionTimeout actionTimeout;
    /**
     * Parallel branch cancellation behavior.
     */
    @Valid
    @NotNull
    private final ParallelExecution parallel;

    /**
     * Creates immutable executor settings.
     *
     * @param runtimeLoad local runtime-load settings
     * @param actionTimeout action timeout enforcement settings
     * @param parallel    parallel orchestration cancellation settings
     */
    public EngineExecutorProperties(@DefaultValue RuntimeLoad runtimeLoad, @DefaultValue ActionTimeout actionTimeout,
            @DefaultValue ParallelExecution parallel) {
        this.runtimeLoad = runtimeLoad;
        this.actionTimeout = actionTimeout;
        this.parallel = parallel;
    }

    /**
     * Converts bound values into the immutable executor configuration.
     *
     * @return validated executor configuration
     */
    public ProcessExecutorConfig toConfig() {
        return ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(runtimeLoad.getMaxConcurrency())
            .runtimeLoadMaxPending(runtimeLoad.getMaxPending())
            .actionTimeoutMaxConcurrency(actionTimeout.getMaxConcurrency())
            .actionTimeoutMaxPending(actionTimeout.getMaxPending())
            .actionTimeoutCancellationGracePeriod(actionTimeout.getCancellationGracePeriod())
            .parallelCancellationGracePeriod(parallel.getCancellationGracePeriod())
            .build();
    }

    public RuntimeLoad getRuntimeLoad() {
        return runtimeLoad;
    }

    public ActionTimeout getActionTimeout() {
        return actionTimeout;
    }

    public ParallelExecution getParallel() {
        return parallel;
    }

    /**
     * Local runtime-load execution and pending-admission limits.
     */
    public static final class RuntimeLoad {
        /**
         * Maximum concurrent runtime loads; derived from available processors when absent.
         */
        @Min(1)
        private final int maxConcurrency;
        /**
         * Maximum number of waiting runtime-load tasks before rejection.
         */
        @Min(0)
        private final int maxPending;

        public RuntimeLoad(Integer maxConcurrency, @DefaultValue("4") int maxPending) {
            this.maxConcurrency = maxConcurrency == null
                    ? ProcessExecutorConfig.defaultRuntimeLoadMaxConcurrency()
                    : maxConcurrency;
            this.maxPending = maxPending;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public int getMaxPending() {
            return maxPending;
        }
    }

    /**
     * Bounded pool for actions that require timeout enforcement.
     */
    public static final class ActionTimeout {
        /**
         * Maximum concurrent timeout-enforced actions; derived from available
         * processors when absent.
         */
        @Min(1)
        private final int maxConcurrency;
        /**
         * Maximum number of waiting actions before rejection.
         */
        @Min(0)
        private final int maxPending;
        /**
         * Time allowed for a cancelled action worker to stop cooperatively.
         */
        @NotNull
        private final Duration cancellationGracePeriod;

        public ActionTimeout(Integer maxConcurrency, @DefaultValue("0") int maxPending,
                @DefaultValue("2s") Duration cancellationGracePeriod) {
            this.maxConcurrency = maxConcurrency == null
                    ? ProcessExecutorConfig.defaultActionTimeoutMaxConcurrency()
                    : maxConcurrency;
            this.maxPending = maxPending;
            this.cancellationGracePeriod = cancellationGracePeriod;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public int getMaxPending() {
            return maxPending;
        }

        public Duration getCancellationGracePeriod() {
            return cancellationGracePeriod;
        }

        @AssertTrue(message = "action-timeout cancellation-grace-period must be a non-negative whole-millisecond "
                + "duration")
        public boolean isValid() {
            return DurationPropertyConstraints.isNonNegativeWholeMilliseconds(cancellationGracePeriod);
        }
    }

    /**
     * Parallel branch cancellation settings.
     */
    public static final class ParallelExecution {
        /**
         * Time allowed for interrupted sibling branches to stop cooperatively.
         */
        @NotNull
        private final Duration cancellationGracePeriod;

        public ParallelExecution(@DefaultValue("2s") Duration cancellationGracePeriod) {
            this.cancellationGracePeriod = cancellationGracePeriod;
        }

        public Duration getCancellationGracePeriod() {
            return cancellationGracePeriod;
        }

        @AssertTrue(message = "parallel cancellation-grace-period must be a non-negative whole-millisecond duration")
        public boolean isValid() {
            return DurationPropertyConstraints.isNonNegativeWholeMilliseconds(cancellationGracePeriod);
        }
    }
}
