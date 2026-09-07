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
package com.alibaba.compileflow.durable.runtime;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceState;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable construction settings for one node-local Durable engine.
 *
 * <p>Every supplied collaborator is borrowed and remains application-owned. The engine closes only
 * resources created by its factory. Definitions own their semantic model type.
 *
 * @author yusu
 */
public final class DurableProcessEngineConfig {
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);
    private final DurableStore store;
    private final ClassLoader classLoader;
    private final ProcessDefinitionConfig definitionConfig;
    private final JavaDiagnosticsConfig javaDiagnostics;
    private final ProcessRuntimeMode runtimeMode;
    private final ProcessComponentResolver componentResolver;
    private final List<ScriptExecutor> scriptExecutors;
    private final Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies;
    private final DurableVersionDefinitionSource versionDefinitionSource;
    private final DurableAliasStateSource aliasStateSource;
    private final DurableWaitDescriptionProvider waitDescriptionProvider;
    private final DurableOutboxSink outboxSink;
    private final int maxCallDepth;
    private final int cacheMaxSize;
    private final Duration shutdownTimeout;
    private final Worker worker;
    private final Outbox outbox;
    private final Maintenance maintenance;
    private final Retention retention;

    private DurableProcessEngineConfig(Builder builder) {
        store = builder.store;
        classLoader = Objects.requireNonNull(builder.classLoader, "classLoader");
        definitionConfig = builder.definitionConfig;
        DurableNumbers.requireRange(definitionConfig.getMaxBytes(), 1, DurableStore.MAX_DEFINITION_BYTES,
                "definition.maxBytes");
        javaDiagnostics = builder.javaDiagnostics;
        runtimeMode = builder.runtimeMode;
        componentResolver = builder.componentResolver;
        scriptExecutors = List.copyOf(builder.scriptExecutors.values());
        aliasTargetingPolicies = Map.copyOf(builder.aliasTargetingPolicies);
        versionDefinitionSource = builder.versionDefinitionSource;
        aliasStateSource = builder.aliasStateSource;
        waitDescriptionProvider = builder.waitDescriptionProvider;
        outboxSink = builder.outboxSink;
        maxCallDepth = DurableNumbers.requireRange(builder.maxCallDepth, 1, 256, "maxCallDepth");
        cacheMaxSize = DurableNumbers.requireRange(builder.cacheMaxSize, 1, 10000, "cacheMaxSize");
        shutdownTimeout = DurableNumbers.requirePositiveDurationMillis(builder.shutdownTimeout, Duration.ofDays(1),
                "shutdownTimeout");
        worker = builder.worker;
        outbox = builder.outbox;
        maintenance = builder.maintenance;
        retention = builder.retention;
    }

    public static Builder builder(DurableStore store) {
        return new Builder(store);
    }

    public DurableStore getStore() {
        return store;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Definition loading limit, from 1 through {@link DurableStore#MAX_DEFINITION_BYTES}
     * bytes (4 MiB), inclusive. Applications may choose a smaller limit.
     */
    public ProcessDefinitionConfig getDefinitionConfig() {
        return definitionConfig;
    }

    public JavaDiagnosticsConfig getJavaDiagnostics() {
        return javaDiagnostics;
    }

    public ProcessRuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public ProcessComponentResolver getComponentResolver() {
        return componentResolver;
    }

    public List<ScriptExecutor> getScriptExecutors() {
        return scriptExecutors;
    }

    public Map<String, ProcessAliasTargetingPolicy> getAliasTargetingPolicies() {
        return aliasTargetingPolicies;
    }

    public DurableVersionDefinitionSource getVersionDefinitionSource() {
        return versionDefinitionSource;
    }

    public DurableAliasStateSource getAliasStateSource() {
        return aliasStateSource;
    }

    public DurableWaitDescriptionProvider getWaitDescriptionProvider() {
        return waitDescriptionProvider;
    }

    public DurableOutboxSink getOutboxSink() {
        return outboxSink;
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    /**
     * Worker drain budget used by both stop and close, after which interruption is requested.
     *
     * <p>This is not an end-to-end close deadline: admitted application operations drain before
     * worker shutdown, and resource cleanup follows it. Store and other borrowed collaborators
     * must bound their own blocking calls.
     */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
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

    /**
     * Scheduling capacity, independent of definition semantics.
     */
    public record Worker(boolean enabled, String id, Duration leaseDuration, Duration idlePollDelay,
            Duration turnFaultBackoff, int turnMaxSteps, int maxActiveIterations, int turnConcurrency,
            int effectConcurrency) {
        public Worker {
            if (id != null) {
                id = DurableIdentifiers.requireIdentity(id, "workerId", 96);
            }
            leaseDuration = DurableNumbers.requirePositiveDurationMillis(leaseDuration, Duration.ofHours(1),
                    "leaseDuration");
            if (leaseDuration.toMillis() < 2L) {
                throw new IllegalArgumentException("leaseDuration must be at least 2ms to allow renewal before expiry");
            }
            idlePollDelay = DurableNumbers.requirePositiveDurationMillis(idlePollDelay, Duration.ofMinutes(1),
                    "idlePollDelay");
            turnFaultBackoff = DurableNumbers.requirePositiveDurationMillis(turnFaultBackoff, Duration.ofHours(1),
                    "turnFaultBackoff");
            DurableNumbers.requireRange(turnMaxSteps, 1, TurnBudget.ABSOLUTE_MAX_STEPS, "turnMaxSteps");
            DurableNumbers.requireRange(maxActiveIterations, 1, MultiInstanceState.ABSOLUTE_MAX_ACTIVE,
                    "maxActiveIterations");
            DurableNumbers.requireRange(turnConcurrency, 1, 256, "turnConcurrency");
            DurableNumbers.requireRange(effectConcurrency, 1, 256, "effectConcurrency");
        }

        public static Worker defaults() {
            TurnBudget budget = TurnBudget.defaults();
            return new Worker(true, null, Duration.ofSeconds(30), Duration.ofMillis(100), Duration.ofSeconds(1),
                    budget.maxSteps(), budget.maxActiveIterations(), 2, 8);
        }
    }

    /**
     * Outbox execution and retry bounds.
     */
    public record Outbox(int concurrency, Duration initialDelay, Duration maxDelay, int maxAttempts) {
        public Outbox {
            DurableNumbers.requireRange(concurrency, 1, 256, "outboxConcurrency");
            initialDelay = DurableNumbers.requirePositiveDurationMillis(initialDelay, Duration.ofHours(1),
                    "initialDelay");
            maxDelay = DurableNumbers.requirePositiveDurationMillis(maxDelay, Duration.ofHours(1), "maxDelay");
            if (maxDelay.compareTo(initialDelay) < 0) {
                throw new IllegalArgumentException("maxDelay must be greater than or equal to initialDelay");
            }
            DurableNumbers.requireRange(maxAttempts, 1, 10000, "maxAttempts");
        }

        public static Outbox defaults() {
            return new Outbox(2, Duration.ofSeconds(1), Duration.ofMinutes(1), 100);
        }
    }

    /**
     * Bounded recovery and expired-authority maintenance.
     * Batch size is limited to 1..1000 by runtime-demand queries.
     */
    public record Maintenance(Duration interval, int batchSize) {
        public Maintenance {
            interval = DurableNumbers.requirePositiveDurationMillis(interval, Duration.ofHours(1), "maintenanceInterval");
            DurableNumbers.requireRange(batchSize, 1, 1000, "maintenanceBatchSize");
        }

        public static Maintenance defaults() {
            return new Maintenance(Duration.ofSeconds(1), 100);
        }
    }

    /**
     * Optional retention; null horizons retain that category indefinitely.
     */
    public record Retention(Duration terminalRun, Duration unusedProcess, Duration consumedOccurrence,
            Duration interval) {
        public Retention {
            if (terminalRun != null) {
                terminalRun = DurableNumbers.requirePositiveDurationMillis(terminalRun, Duration.ofDays(3650),
                        "terminalRun");
            }
            if (unusedProcess != null) {
                unusedProcess = DurableNumbers.requirePositiveDurationMillis(unusedProcess, Duration.ofDays(3650),
                        "unusedProcess");
            }
            if (consumedOccurrence != null) {
                consumedOccurrence = DurableNumbers.requirePositiveDurationMillis(consumedOccurrence,
                        Duration.ofDays(3650), "consumedOccurrence");
            }
            interval = DurableNumbers.requirePositiveDurationMillis(interval, Duration.ofDays(1), "retentionInterval");
        }

        public boolean enabled() {
            return terminalRun != null || unusedProcess != null || consumedOccurrence != null;
        }

        public static Retention defaults() {
            return new Retention(null, null, null, Duration.ofHours(1));
        }
    }

    /**
     * Mutable builder; every build captures immutable settings and collaborator identities.
     */
    public static final class Builder {
        private final DurableStore store;
        private ClassLoader classLoader = defaultClassLoader();
        private ProcessDefinitionConfig definitionConfig = ProcessDefinitionConfig.defaults();
        private JavaDiagnosticsConfig javaDiagnostics = JavaDiagnosticsConfig.defaults();
        private ProcessRuntimeMode runtimeMode = ProcessRuntimeMode.COMPILED;
        private ProcessComponentResolver componentResolver = ProcessComponentResolver.disabled();
        private final Map<String, ScriptExecutor> scriptExecutors = new LinkedHashMap<>();
        private final Map<String, ProcessAliasTargetingPolicy> aliasTargetingPolicies = new LinkedHashMap<>();
        private DurableVersionDefinitionSource versionDefinitionSource = DurableVersionDefinitionSource.empty();
        private DurableAliasStateSource aliasStateSource;
        private DurableWaitDescriptionProvider waitDescriptionProvider = DurableWaitDescriptionProvider.defaults();
        private DurableOutboxSink outboxSink;
        private int maxCallDepth = 32;
        private int cacheMaxSize = 256;
        private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private Worker worker = Worker.defaults();
        private Outbox outbox = Outbox.defaults();
        private Maintenance maintenance = Maintenance.defaults();
        private Retention retention = Retention.defaults();

        private Builder(DurableStore store) {
            this.store = Objects.requireNonNull(store, "store");
        }

        private static ClassLoader defaultClassLoader() {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            return context == null ? DurableProcessEngineConfig.class.getClassLoader() : context;
        }

        public Builder classLoader(ClassLoader value) {
            classLoader = Objects.requireNonNull(value, "classLoader");
            return this;
        }

        /**
         * Sets the definition loading limit. {@link #build()} rejects {@code maxBytes}
         * outside 1 through {@link DurableStore#MAX_DEFINITION_BYTES} (4 MiB), inclusive,
         * even though the general Process API permits larger definitions.
         *
         * @param definitions definition settings, retaining any smaller application limit
         * @return this builder
         */
        public Builder definitions(ProcessDefinitionConfig definitions) {
            definitionConfig = Objects.requireNonNull(definitions, "definitions");
            return this;
        }

        public Builder javaDiagnostics(JavaDiagnosticsConfig diagnostics) {
            javaDiagnostics = Objects.requireNonNull(diagnostics, "javaDiagnostics");
            return this;
        }

        public Builder runtimeMode(ProcessRuntimeMode value) {
            runtimeMode = Objects.requireNonNull(value, "runtimeMode");
            return this;
        }

        public Builder componentResolver(ProcessComponentResolver value) {
            componentResolver = Objects.requireNonNull(value, "componentResolver");
            return this;
        }

        public Builder versionDefinitionSource(DurableVersionDefinitionSource value) {
            versionDefinitionSource = Objects.requireNonNull(value, "versionDefinitionSource");
            return this;
        }

        public Builder aliasStateSource(DurableAliasStateSource value) {
            aliasStateSource = value;
            return this;
        }

        public Builder waitDescriptionProvider(DurableWaitDescriptionProvider value) {
            waitDescriptionProvider = Objects.requireNonNull(value, "waitDescriptionProvider");
            return this;
        }

        public Builder outboxSink(DurableOutboxSink value) {
            outboxSink = value;
            return this;
        }

        public Builder maxCallDepth(int value) {
            maxCallDepth = value;
            return this;
        }

        public Builder cacheMaxSize(int value) {
            cacheMaxSize = value;
            return this;
        }

        /**
         * Sets the worker drain budget used by stop and close.
         *
         * @param value positive whole-millisecond shutdown budget, at most one day
         * @return this builder
         */
        public Builder shutdownTimeout(Duration value) {
            shutdownTimeout = Objects.requireNonNull(value, "shutdownTimeout");
            return this;
        }

        public Builder worker(Worker value) {
            worker = Objects.requireNonNull(value, "worker");
            return this;
        }

        public Builder outbox(Outbox value) {
            outbox = Objects.requireNonNull(value, "outbox");
            return this;
        }

        public Builder maintenance(Maintenance value) {
            maintenance = Objects.requireNonNull(value, "maintenance");
            return this;
        }

        public Builder retention(Retention value) {
            retention = Objects.requireNonNull(value, "retention");
            return this;
        }

        public Builder scriptExecutor(ScriptExecutor value) {
            Objects.requireNonNull(value, "scriptExecutor");
            String name = ScriptExecutor.requireCanonicalName(value.name());
            if (scriptExecutors.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate script executor: " + name);
            }
            return this;
        }

        public Builder aliasTargetingPolicy(ProcessAliasTargetingPolicy policy) {
            Objects.requireNonNull(policy, "aliasTargetingPolicy");
            String name = ProcessAliasTargetingPolicy.requireCanonicalName(policy.name());
            if (aliasTargetingPolicies.putIfAbsent(name, policy) != null) {
                throw new IllegalArgumentException("Duplicate Alias targeting policy: " + name);
            }
            return this;
        }

        public DurableProcessEngineConfig build() {
            return new DurableProcessEngineConfig(this);
        }
    }
}
