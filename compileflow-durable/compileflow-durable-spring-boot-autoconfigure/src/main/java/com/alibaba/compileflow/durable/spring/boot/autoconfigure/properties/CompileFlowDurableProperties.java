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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Immutable, owner-oriented configuration for one Durable runtime.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow.durable", ignoreUnknownFields = false)
@Validated
public final class CompileFlowDurableProperties {
    /**
     * Whether to assemble the Durable product.
     */
    private final boolean enabled;
    /**
     * Disposable runtime realization; persisted Durable behavior is mode-independent.
     */
    @NotNull
    private final ProcessRuntimeMode runtimeMode;
    /**
     * ProcessCall admission limits.
     */
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Call call;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Shutdown shutdown;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final DurableDefinitionProperties definition;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final DurableJavaDiagnosticsProperties javaDiagnostics;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Database database;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Worker worker;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Outbox outbox;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Maintenance maintenance;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Retention retention;
    @Valid
    @NotNull
    @NestedConfigurationProperty
    private final Cache cache;

    public CompileFlowDurableProperties(@DefaultValue("false") boolean enabled,
            @DefaultValue("COMPILED") ProcessRuntimeMode runtimeMode, @DefaultValue Call call,
            @DefaultValue Shutdown shutdown, @DefaultValue DurableDefinitionProperties definition,
            @DefaultValue DurableJavaDiagnosticsProperties javaDiagnostics, @DefaultValue Database database,
            @DefaultValue Worker worker, @DefaultValue Outbox outbox, @DefaultValue Maintenance maintenance,
            @DefaultValue Retention retention, @DefaultValue Cache cache) {
        this.enabled = enabled;
        this.runtimeMode = runtimeMode;
        this.call = call;
        this.shutdown = shutdown;
        this.definition = definition;
        this.javaDiagnostics = javaDiagnostics;
        this.database = database;
        this.worker = worker;
        this.outbox = outbox;
        this.maintenance = maintenance;
        this.retention = retention;
        this.cache = cache;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ProcessRuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public Call getCall() {
        return call;
    }

    public Shutdown getShutdown() {
        return shutdown;
    }

    public DurableDefinitionProperties getDefinition() {
        return definition;
    }

    public DurableJavaDiagnosticsProperties getJavaDiagnostics() {
        return javaDiagnostics;
    }

    public Database getDatabase() {
        return database;
    }

    public Worker getWorker() {
        return worker;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Maintenance getMaintenance() {
        return maintenance;
    }

    public Retention getRetention() {
        return retention;
    }

    public Cache getCache() {
        return cache;
    }

    /**
     * Optional first-party persistence Provider selection for this Durable runtime.
     */
    public static final class Database {
        private final Provider provider;
        private final boolean migrate;

        public Database(Provider provider, @DefaultValue("false") boolean migrate) {
            this.provider = provider;
            this.migrate = migrate;
        }

        public Provider getProvider() {
            return provider;
        }

        public boolean isMigrate() {
            return migrate;
        }

        /**
         * First-party Durable persistence Providers.
         */
        public enum Provider {
            POSTGRESQL,
            MYSQL
        }
    }

    /**
     * Worker drain budget used by stop and close; excludes admitted application operations
     * and subsequent resource cleanup.
     */
    public static final class Shutdown {
        /**
         * Maximum worker drain wait before requesting interruption and reporting unfinished work.
         */
        @NotNull
        private final Duration timeout;

        public Shutdown(@DefaultValue("15s") Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getTimeout() {
            return timeout;
        }

        @AssertTrue(message = "compileflow.durable.shutdown.timeout must be a positive whole-millisecond duration"
                + " at most 1d")
        public boolean isTimeoutValid() {
            return isPositiveWholeMillisecondsAtMost(timeout, Duration.ofDays(1));
        }
    }

    /**
     * Synchronous ProcessCall admission limits.
     */
    public static final class Call {
        /**
         * Maximum root-inclusive ProcessCall depth admitted into a Run.
         */
        @Min(value = 1, message = "compileflow.durable.call.max-depth must be between 1 and 256")
        @Max(value = 256, message = "compileflow.durable.call.max-depth must be between 1 and 256")
        private final int maxDepth;

        public Call(@DefaultValue("32") int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxDepth() {
            return maxDepth;
        }
    }

    /**
     * Execution capacity and lease policy for this runtime.
     */
    public static final class Worker {
        /**
         * Whether this process hosts Durable execution and maintenance workers.
         */
        private final boolean enabled;
        /**
         * Optional base identity shared by every worker kind in this process.
         */
        @Size(max = 96, message = "compileflow.durable.worker.id must contain at most 96 characters")
        private final String id;
        /**
         * Token-fenced authority duration for one work attempt.
         */
        @NotNull
        private final Duration leaseDuration;
        /**
         * Initial delay after an empty acquisition before adaptive backoff.
         */
        @NotNull
        private final Duration idlePollDelay;
        /**
         * Suppression delay after one unexpected replayable Turn fault.
         */
        @NotNull
        private final Duration turnFaultBackoff;
        /**
         * Maximum Process-node advances committed by one bounded Machine Turn.
         */
        @Min(value = 1, message = "compileflow.durable.worker.turn-max-steps must be between 1 and 1000000")
        @Max(value = 1_000_000, message = "compileflow.durable.worker.turn-max-steps must be between 1 and 1000000")
        private final int turnMaxSteps;
        /**
         * Maximum active iterations retained by one parallel collection scope.
         */
        @Min(value = 1, message = "compileflow.durable.worker.max-active-iterations must be between 1 and 64")
        @Max(value = 64, message = "compileflow.durable.worker.max-active-iterations must be between 1 and 64")
        private final int maxActiveIterations;
        /**
         * Maximum concurrent replayable Machine Turn executions in this runtime.
         */
        @Min(value = 1, message = "compileflow.durable.worker.turn-concurrency must be between 1 and 256")
        @Max(value = 256, message = "compileflow.durable.worker.turn-concurrency must be between 1 and 256")
        private final int turnConcurrency;
        /**
         * Maximum concurrent Effect executions in this runtime.
         */
        @Min(value = 1, message = "compileflow.durable.worker.effect-concurrency must be between 1 and 256")
        @Max(value = 256, message = "compileflow.durable.worker.effect-concurrency must be between 1 and 256")
        private final int effectConcurrency;

        public Worker(@DefaultValue("true") boolean enabled, String id, @DefaultValue("30s") Duration leaseDuration,
                @DefaultValue("100ms") Duration idlePollDelay, @DefaultValue("1s") Duration turnFaultBackoff,
                @DefaultValue("10000") int turnMaxSteps, @DefaultValue("32") int maxActiveIterations,
                @DefaultValue("2") int turnConcurrency, @DefaultValue("8") int effectConcurrency) {
            this.enabled = enabled;
            this.id = blankToNull(id);
            this.leaseDuration = leaseDuration;
            this.idlePollDelay = idlePollDelay;
            this.turnFaultBackoff = turnFaultBackoff;
            this.turnMaxSteps = turnMaxSteps;
            this.maxActiveIterations = maxActiveIterations;
            this.turnConcurrency = turnConcurrency;
            this.effectConcurrency = effectConcurrency;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getId() {
            return id;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public Duration getIdlePollDelay() {
            return idlePollDelay;
        }

        public Duration getTurnFaultBackoff() {
            return turnFaultBackoff;
        }

        public int getTurnMaxSteps() {
            return turnMaxSteps;
        }

        public int getMaxActiveIterations() {
            return maxActiveIterations;
        }

        public int getTurnConcurrency() {
            return turnConcurrency;
        }

        public int getEffectConcurrency() {
            return effectConcurrency;
        }

        @AssertTrue(message = "compileflow.durable.worker.id must be trimmed, well-formed text")
        public boolean isIdValid() {
            return isOptionalText(id, 96);
        }

        @AssertTrue(message = "compileflow.durable.worker.lease-duration must be a whole-millisecond"
                + " duration between 2ms and 1h to allow renewal before expiry")
        public boolean isLeaseDurationValid() {
            return isPositiveWholeMillisecondsAtMost(leaseDuration, Duration.ofHours(1))
                    && leaseDuration.toMillis() >= 2L;
        }

        @AssertTrue(message = "compileflow.durable.worker.idle-poll-delay must be a positive whole-millisecond"
                + " duration at most 1m")
        public boolean isIdlePollIntervalValid() {
            return isPositiveWholeMillisecondsAtMost(idlePollDelay, Duration.ofMinutes(1));
        }

        @AssertTrue(message = "compileflow.durable.worker.turn-fault-backoff must be a positive whole-millisecond"
                + " duration at most 1h")
        public boolean isTurnFaultBackoffValid() {
            return isPositiveWholeMillisecondsAtMost(turnFaultBackoff, Duration.ofHours(1));
        }
    }

    /**
     * Reliable Integration Event delivery capacity and retry policy.
     */
    public static final class Outbox {
        /**
         * Maximum concurrent Integration Event deliveries in this runtime.
         */
        @Min(value = 1, message = "compileflow.durable.outbox.concurrency must be between 1 and 256")
        @Max(value = 256, message = "compileflow.durable.outbox.concurrency must be between 1 and 256")
        private final int concurrency;
        @Valid
        @NotNull
        @NestedConfigurationProperty
        private final Retry retry;

        public Outbox(@DefaultValue("2") int concurrency, @DefaultValue Retry retry) {
            this.concurrency = concurrency;
            this.retry = retry;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public Retry getRetry() {
            return retry;
        }

        /**
         * Capped exponential retry bounds; jitter is owned by the runtime.
         */
        public static final class Retry {
            /**
             * First capped-exponential retry-window ceiling.
             */
            @NotNull
            private final Duration initialDelay;
            /**
             * Maximum capped-exponential retry-window ceiling.
             */
            @NotNull
            private final Duration maxDelay;
            /**
             * Attempts before an dispatch event is abandoned.
             */
            @Min(value = 1, message = "compileflow.durable.outbox.retry.max-attempts must be between 1 and 10000")
            @Max(value = 10_000, message = "compileflow.durable.outbox.retry.max-attempts must be between 1 and 10000")
            private final int maxAttempts;

            public Retry(@DefaultValue("1s") Duration initialDelay, @DefaultValue("1m") Duration maxDelay,
                    @DefaultValue("100") int maxAttempts) {
                this.initialDelay = initialDelay;
                this.maxDelay = maxDelay;
                this.maxAttempts = maxAttempts;
            }

            public Duration getInitialDelay() {
                return initialDelay;
            }

            public Duration getMaxDelay() {
                return maxDelay;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            @AssertTrue(message = "compileflow.durable.outbox.retry.initial-delay must be a positive"
                    + " whole-millisecond duration at most 1h")
            public boolean isInitialDelayValid() {
                return isPositiveWholeMillisecondsAtMost(initialDelay, Duration.ofHours(1));
            }

            @AssertTrue(message = "compileflow.durable.outbox.retry.max-delay must be a positive whole-millisecond"
                    + " duration at most 1h and at least compileflow.durable.outbox.retry.initial-delay")
            public boolean isMaxDelayValid() {
                return isPositiveWholeMillisecondsAtMost(maxDelay, Duration.ofHours(1)) && initialDelay != null
                        && maxDelay.compareTo(initialDelay) >= 0;
            }
        }
    }

    /**
     * Bounded repair, scheduling, and readiness sweep policy.
     */
    public static final class Maintenance {
        /**
         * Delay between bounded readiness, lease-reclaim, and timer sweeps.
         */
        @NotNull
        private final Duration interval;
        /**
         * Maximum records processed by one maintenance operation (1..1000).
         */
        @Min(value = 1, message = "compileflow.durable.maintenance.batch-size must be between 1 and 1000")
        @Max(value = 1_000, message = "compileflow.durable.maintenance.batch-size must be between 1 and 1000")
        private final int batchSize;

        public Maintenance(@DefaultValue("1s") Duration interval, @DefaultValue("100") int batchSize) {
            this.interval = interval;
            this.batchSize = batchSize;
        }

        public Duration getInterval() {
            return interval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        @AssertTrue(message = "compileflow.durable.maintenance.interval must be a positive whole-millisecond"
                + " duration at most 1h")
        public boolean isIntervalValid() {
            return isPositiveWholeMillisecondsAtMost(interval, Duration.ofHours(1));
        }
    }

    /**
     * Optional bounded cleanup policies.
     */
    public static final class Retention {
        /**
         * Optional age after which eligible terminal Runs may be deleted.
         */
        private final Duration terminalRun;
        /**
         * Optional age after which unreferenced exact Process Definitions may be deleted.
         */
        private final Duration unusedProcess;
        /**
         * Optional age after which consumed Wait/Effect occurrences may be deleted.
         */
        private final Duration consumedOccurrence;
        /**
         * Delay between bounded retention sweeps.
         */
        @NotNull
        private final Duration interval;

        public Retention(Duration terminalRun, Duration unusedProcess, Duration consumedOccurrence,
                @DefaultValue("1h") Duration interval) {
            this.terminalRun = terminalRun;
            this.unusedProcess = unusedProcess;
            this.consumedOccurrence = consumedOccurrence;
            this.interval = interval;
        }

        public Duration getTerminalRun() {
            return terminalRun;
        }

        public Duration getUnusedProcess() {
            return unusedProcess;
        }

        public Duration getConsumedOccurrence() {
            return consumedOccurrence;
        }

        public Duration getInterval() {
            return interval;
        }

        @AssertTrue(message = "compileflow.durable.retention.terminal-run must be a positive whole-millisecond"
                + " duration at most 3650d when configured")
        public boolean isTerminalRunValid() {
            return isOptionalPositiveWholeMillisecondsAtMost(terminalRun, Duration.ofDays(3650));
        }

        @AssertTrue(message = "compileflow.durable.retention.unused-process must be a positive whole-millisecond"
                + " duration at most 3650d when configured")
        public boolean isUnusedProcessValid() {
            return isOptionalPositiveWholeMillisecondsAtMost(unusedProcess, Duration.ofDays(3650));
        }

        @AssertTrue(message = "compileflow.durable.retention.consumed-occurrence must be a positive"
                + " whole-millisecond duration at most 3650d when configured")
        public boolean isConsumedOccurrenceValid() {
            return isOptionalPositiveWholeMillisecondsAtMost(consumedOccurrence, Duration.ofDays(3650));
        }

        @AssertTrue(message = "compileflow.durable.retention.interval must be a positive whole-millisecond"
                + " duration at most 1d")
        public boolean isIntervalValid() {
            return isPositiveWholeMillisecondsAtMost(interval, Duration.ofDays(1));
        }
    }

    /**
     * Disposable Durable runtime cache bound.
     */
    public static final class Cache {
        /**
         * Maximum node-local disposable Durable runtimes.
         */
        @Min(value = 1, message = "compileflow.durable.cache.runtime-max-size must be between 1 and 10000")
        @Max(value = 10_000, message = "compileflow.durable.cache.runtime-max-size must be between 1 and 10000")
        private final int runtimeMaxSize;

        public Cache(@DefaultValue("256") int runtimeMaxSize) {
            this.runtimeMaxSize = runtimeMaxSize;
        }

        public int getRuntimeMaxSize() {
            return runtimeMaxSize;
        }
    }

    private static boolean isPositiveWholeMillisecondsAtMost(Duration value, Duration maximum) {
        return value != null && !value.isNegative() && !value.isZero() && value.toNanosPart() % 1_000_000 == 0
                && value.compareTo(maximum) <= 0;
    }

    private static boolean isOptionalPositiveWholeMillisecondsAtMost(Duration value, Duration maximum) {
        return value == null || isPositiveWholeMillisecondsAtMost(value, maximum);
    }

    private static boolean isOptionalText(String value, int maximumCharacters) {
        if (value == null) {
            return true;
        }
        try {
            DurableIdentifiers.requireIdentity(value, "value", maximumCharacters);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
