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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.AssembledProcessEngineFactory;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.lifecycle.BudgetedShutdown;
import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.core.routing.LocalReadyAliasRouteSource;
import com.alibaba.compileflow.engine.core.routing.AliasSelectionExecutor;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRouteSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable Spring composition of one long-lived process engine per supported model type.
 *
 * <p>This registry owns engine lifecycles but no mutable extension registration. Every engine
 * shares the same local deployment localRoutingState while retaining its own cache, executors, compiler,
 * and generated-class scope.
 *
 * @author yusu
 */
public final class ProcessEngineRegistry implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEngineRegistry.class);
    private static final int ALIAS_HANDOFF_RETRIES = 1;
    private final Map<ProcessModelType, ProcessEngine> engines;
    private final Map<ProcessModelType, ProcessEngineConfig> configurations;
    private final AliasAdmission aliasAdmission;
    private final OperationGate lifecycleGate = new OperationGate("ProcessEngineRegistry");
    private final Duration shutdownTimeout;

    /**
     * Creates a registry for the requested model formats.
     *
     * @param configurationFactory factory for format-bound immutable configurations
     * @param primaryConfiguration existing primary engine configuration
     * @param localRoutingState            deployment state shared by all engines
     * @param modelTypes           non-empty set of formats hosted by this process
     */
    public ProcessEngineRegistry(ProcessEngineConfigurationFactory configurationFactory,
            ProcessEngineConfig primaryConfiguration, LocalRoutingState localRoutingState,
            Collection<ProcessModelType> modelTypes) {
        this(configurationFactory, primaryConfiguration, localRoutingState,
                defaultAliasAdmission(primaryConfiguration, localRoutingState), modelTypes);
    }

    /**
     * Creates a registry around one shared local-ready Alias resolver.
     *
     * @param configurationFactory factory for format-bound immutable configurations
     * @param primaryConfiguration existing primary engine configuration
     * @param localRoutingState            deployment state shared by all engines
     * @param aliasAdmission   shared local-ready Alias admission
     * @param modelTypes           non-empty set of formats hosted by this process
     */
    public ProcessEngineRegistry(ProcessEngineConfigurationFactory configurationFactory,
            ProcessEngineConfig primaryConfiguration, LocalRoutingState localRoutingState, AliasAdmission aliasAdmission,
            Collection<ProcessModelType> modelTypes) {
        ProcessEngineConfigurationFactory factory = Objects.requireNonNull(configurationFactory, "configurationFactory");
        ProcessEngineConfig primary = Objects.requireNonNull(primaryConfiguration, "primaryConfiguration");
        LocalRoutingState sharedRoutingState = Objects.requireNonNull(localRoutingState, "localRoutingState");
        AliasAdmission sharedAliasAdmission = Objects.requireNonNull(aliasAdmission, "aliasAdmission");
        Collection<ProcessModelType> requested = Objects.requireNonNull(modelTypes, "modelTypes");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("modelTypes must not be empty");
        }
        EnumSet<ProcessModelType> requestedTypes = EnumSet.noneOf(ProcessModelType.class);
        for (ProcessModelType modelType : requested) {
            requestedTypes.add(Objects.requireNonNull(modelType, "modelTypes contains null"));
        }
        if (!requestedTypes.contains(primary.getModelType())) {
            throw new IllegalArgumentException(
                    "modelTypes must contain the primary model type " + primary.getModelType());
        }

        EnumMap<ProcessModelType, ProcessEngine> createdEngines = new EnumMap<>(ProcessModelType.class);
        EnumMap<ProcessModelType, ProcessEngineConfig> createdConfigurations = new EnumMap<>(ProcessModelType.class);
        try {
            for (ProcessModelType type : requestedTypes) {
                ProcessEngineConfig configuration = type == primary.getModelType() ? primary : factory.create(type);
                EngineDependencies dependencies =
                        EngineAssembly.assemble(configuration, sharedRoutingState, sharedAliasAdmission);
                ProcessEngine engine = AssembledProcessEngineFactory.create(configuration, dependencies);
                createdConfigurations.put(type, configuration);
                createdEngines.put(type, engine);
            }
            requireSharedAliasRouteSource(createdConfigurations, primary);
        } catch (RuntimeException | Error failure) {
            closeAfterFailedConstruction(createdEngines, failure);
            throw failure;
        }
        engines = Collections.unmodifiableMap(createdEngines);
        configurations = Collections.unmodifiableMap(createdConfigurations);
        this.aliasAdmission = sharedAliasAdmission;
        shutdownTimeout = createdConfigurations
            .values()
            .stream()
            .map(ProcessEngineConfig::getShutdownTimeout)
            .max(Duration::compareTo)
            .orElseThrow();
    }

    private static AliasSelectionExecutor requireAliasSelectionExecutor(ProcessEngine engine) {
        if (engine instanceof AliasSelectionExecutor selectionExecutor) {
            return selectionExecutor;
        }
        throw new IllegalStateException(
                "Multi-format alias routing requires a CompileFlow engine with preselected-route execution; engine="
                + Objects.requireNonNull(engine, "engine").getClass().getName());
    }

    private static ProcessModelType requireResolvedModelType(Function<ProcessRef.Version, ProcessModelType> resolver,
            ProcessRef.Version ref) {
        ProcessModelType modelType = resolver.apply(ref);
        if (modelType == null) {
            throw new IllegalStateException("No published model type found for version " + ref);
        }
        return modelType;
    }

    private static ProcessExecutionOptions prepareExecutionOptions(ProcessExecutionOptions options) {
        ProcessExecutionOptions requested = Objects.requireNonNull(options, "options");
        String invocationId =
                requested.getInvocationId() == null ? UUID.randomUUID().toString() : requested.getInvocationId();
        AliasRoutingOptions routing = requested.getAliasRouting();
        AliasRoutingOptions effectiveRouting =
                routing.routingKey() == null ? new AliasRoutingOptions(invocationId, routing.attributes()) : routing;
        if (invocationId.equals(requested.getInvocationId()) && effectiveRouting == routing) {
            return requested;
        }
        return ProcessExecutionOptions.builder().invocationId(invocationId).aliasRouting(effectiveRouting).build();
    }

    private static void requireSharedAliasRouteSource(Map<ProcessModelType, ProcessEngineConfig> configurations,
            ProcessEngineConfig primary) {
        ProcessAliasRouteSource shared = primary.getAliasRouteSource();
        for (Map.Entry<ProcessModelType, ProcessEngineConfig> entry : configurations.entrySet()) {
            if (entry.getValue().getAliasRouteSource() != shared) {
                throw new IllegalStateException(
                        "A multi-format registry requires one shared ProcessAliasRouteSource; modelType=" + entry.getKey());
            }
        }
    }

    private static AliasAdmission defaultAliasAdmission(ProcessEngineConfig primaryConfiguration,
            LocalRoutingState localRoutingState) {
        ProcessEngineConfig config = Objects.requireNonNull(primaryConfiguration, "primaryConfiguration");
        LocalRoutingState state = Objects.requireNonNull(localRoutingState, "localRoutingState");
        ProcessAliasRouteSource source = config.getAliasRouteSource();
        return new AliasAdmission(source == null ? LocalReadyAliasRouteSource.from(state.getAliasRouteState()) : source,
                config.getAliasTargetingPolicies());
    }

    private static void closeAfterFailedConstruction(Map<ProcessModelType, ProcessEngine> created,
            Throwable startupFailure) {
        List<ProcessEngine> values = new ArrayList<>(created.values());
        Collections.reverse(values);
        for (ProcessEngine engine : values) {
            try {
                engine.close();
            } catch (RuntimeException | Error closeFailure) {
                startupFailure.addSuppressed(closeFailure);
            }
        }
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error fatalFailure) {
            throw fatalFailure;
        }
    }

    private static Duration remaining(Duration budget, long startedAtNanos) {
        long budgetNanos;
        try {
            budgetNanos = budget.toNanos();
        } catch (ArithmeticException overflow) {
            budgetNanos = Long.MAX_VALUE;
        }
        long elapsed = System.nanoTime() - startedAtNanos;
        return Duration.ofNanos(elapsed >= budgetNanos ? 0L : budgetNanos - elapsed);
    }

    /**
     * Returns the engine for one model format.
     *
     * @param modelType required format
     * @return long-lived format-bound engine
     */
    public ProcessEngine get(ProcessModelType modelType) {
        lifecycleGate.ensureOpen();
        ProcessEngine engine = engines.get(Objects.requireNonNull(modelType, "modelType"));
        if (engine == null) {
            throw new IllegalArgumentException("No ProcessEngine is configured for model type " + modelType);
        }
        return engine;
    }

    /**
     * Returns the immutable configuration for one model format.
     *
     * @param modelType required format
     * @return engine configuration
     */
    public ProcessEngineConfig getConfiguration(ProcessModelType modelType) {
        lifecycleGate.ensureOpen();
        ProcessEngineConfig configuration = configurations.get(Objects.requireNonNull(modelType, "modelType"));
        if (configuration == null) {
            throw new IllegalArgumentException("No ProcessEngine is configured for model type " + modelType);
        }
        return configuration;
    }

    /**
     * Returns all format-bound engines.
     *
     * @return immutable model-type keyed map
     */
    public Map<ProcessModelType, ProcessEngine> getEngines() {
        lifecycleGate.ensureOpen();
        return engines;
    }

    /**
     * Returns all immutable format-bound engine configurations.
     *
     * @return immutable model-type keyed configuration map
     */
    public Map<ProcessModelType, ProcessEngineConfig> getConfigurations() {
        lifecycleGate.ensureOpen();
        return configurations;
    }

    /**
     * Executes an existing reference after resolving its exact runtime format.
     *
     * @param ref              requested Version or Alias
     * @param versionModelType resolves an immutable version to its published format
     * @param variables        process variables
     * @param options          request-scoped execution options
     * @return process outcome with controlled routing attribution
     */
    public ProcessResult<Map<String, Object>> execute(ProcessRef ref,
            Function<ProcessRef.Version, ProcessModelType> versionModelType, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        lifecycleGate.enter();
        try {
            ProcessRef requested = Objects.requireNonNull(ref, "ref");
            Function<ProcessRef.Version, ProcessModelType> resolver =
                    Objects.requireNonNull(versionModelType, "versionModelType");
            if (requested instanceof ProcessRef.Alias alias) {
                return executeAlias(alias, resolver, variables, options).result();
            }
            ProcessRef.Version version = (ProcessRef.Version) requested;
            return get(requireResolvedModelType(resolver, version)).execute(version, variables, options);
        } finally {
            lifecycleGate.exit();
        }
    }

    /**
     * Executes an Alias and returns the exact admission decision used by that invocation.
     *
     * <p>The selection is returned from the same admission-and-handoff loop that executes the
     * process. Callers can therefore expose authoritative routing attribution without re-reading a
     * route that may already have changed.
     *
     * @param alias            requested Alias
     * @param versionModelType resolves the selected immutable version to its published format
     * @param variables        process variables
     * @param options          request-scoped execution options
     * @return process outcome and the exact Alias selection used by the final attempt
     */
    public AliasExecution executeAliasWithSelection(ProcessRef.Alias alias,
            Function<ProcessRef.Version, ProcessModelType> versionModelType, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        lifecycleGate.enter();
        try {
            return executeAlias(Objects.requireNonNull(alias, "alias"),
                    Objects.requireNonNull(versionModelType, "versionModelType"), variables, options);
        } finally {
            lifecycleGate.exit();
        }
    }

    /**
     * Admits an Alias without starting execution.
     *
     * <p>This is the admission boundary for queues that persist the selected immutable version
     * before execution. The caller must provide its occurrence identifier as the routing key when
     * no explicit cohort key exists.
     *
     * @param alias requested Alias
     * @param routing bounded request-scoped routing inputs
     * @return exact authorized Alias selection
     */
    public AliasSelection admitAlias(ProcessRef.Alias alias, AliasRoutingOptions routing) {
        lifecycleGate.enter();
        try {
            return aliasAdmission.admit(Objects.requireNonNull(alias, "alias"),
                    Objects.requireNonNull(routing, "routing"));
        } finally {
            lifecycleGate.exit();
        }
    }

    /**
     * Executes a pinned Alias selection without consulting a newer route.
     *
     * <p>This is an admission boundary for queued ProcessEngine invocations. The selected root runtime
     * is acquired before process code starts; only route selection is skipped so the invocation
     * retains its admitted version, target, and Alias revision.
     *
     * @param alias            originally requested Alias
     * @param selection        pinned exact Alias selection
     * @param versionModelType resolves the selected immutable version to its published format
     * @param variables        process variables
     * @param options          request-scoped execution options
     * @return process outcome with the pinned Alias attribution
     */
    public ProcessResult<Map<String, Object>> executeAliasSelection(ProcessRef.Alias alias, AliasSelection selection,
            Function<ProcessRef.Version, ProcessModelType> versionModelType, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        lifecycleGate.enter();
        try {
            ProcessRef.Alias requestedAlias = Objects.requireNonNull(alias, "alias");
            AliasSelection selected = Objects.requireNonNull(selection, "selection");
            AliasSelectionExecutor engine =
                    selectionExecutor(selected, Objects.requireNonNull(versionModelType, "versionModelType"));
            return engine.execute(requestedAlias, selected, Objects.requireNonNull(variables, "variables"),
                    Objects.requireNonNull(options, "options"));
        } finally {
            lifecycleGate.exit();
        }
    }

    /**
     * Runs preflight against the engine that owns the definition format.
     *
     * @param modelType  definition format
     * @param definition explicit definition
     * @param options    preflight stages and limits
     * @return preflight report
     */
    public ProcessPreflightReport preflight(ProcessModelType modelType, ProcessDefinition definition,
            ProcessPreflightOptions options) {
        lifecycleGate.enter();
        try {
            return get(modelType)
                .tooling()
                .preflight(Objects.requireNonNull(definition, "definition"), Objects.requireNonNull(options, "options"));
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public void close() {
        long startedAtNanos = System.nanoTime();
        OperationGate.DrainResult drain = lifecycleGate.beginCloseAndAwaitDrained(shutdownTimeout);
        if (!drain.cleanupOwner()) {
            return;
        }
        Throwable failure = null;
        try {
            if (drain != OperationGate.DrainResult.DRAINED) {
                LOGGER.warn("ProcessEngineRegistry drain did not complete before engine cleanup: result={}, active={}",
                        drain, lifecycleGate.activeOperations());
            }
            List<ProcessEngine> values = new ArrayList<>(engines.values());
            Collections.reverse(values);
            for (ProcessEngine engine : values) {
                try {
                    closeEngine(engine, remaining(shutdownTimeout, startedAtNanos));
                } catch (RuntimeException | Error closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
        } finally {
            lifecycleGate.finishClose();
        }
        rethrowCloseFailure(failure);
    }

    private static void closeEngine(ProcessEngine engine, Duration gracefulBudget) {
        if (engine instanceof BudgetedShutdown budgetedShutdown) {
            budgetedShutdown.close(gracefulBudget);
        } else {
            engine.close();
        }
    }

    private AliasExecution executeAlias(ProcessRef.Alias alias,
            Function<ProcessRef.Version, ProcessModelType> modelTypeResolver, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        ProcessExecutionOptions requestOptions = prepareExecutionOptions(options);
        Map<String, Object> requestVariables = Objects.requireNonNull(variables, "variables");
        int retriesRemaining = ALIAS_HANDOFF_RETRIES;
        while (true) {
            AliasSelection selection = aliasAdmission.admit(alias, requestOptions.getAliasRouting());
            ProcessResult<Map<String, Object>> result =
                    selectionExecutor(selection, modelTypeResolver)
                .execute(alias, selection, requestVariables, requestOptions);
            if (result.isSuccess() || retriesRemaining == 0
                    || !ErrorCode.CF_EXEC_012.getCode().equals(result.getError().getCode())) {
                return new AliasExecution(result, selection);
            }
            retriesRemaining--;
        }
    }

    /**
     * Outcome of one Alias execution together with its authoritative admission selection.
     *
     * @param result    process outcome
     * @param selection exact Alias selection used by the final execution attempt
     */
    public record AliasExecution(ProcessResult<Map<String, Object>> result, AliasSelection selection) {
        public AliasExecution {
            result = Objects.requireNonNull(result, "result");
            selection = Objects.requireNonNull(selection, "selection");
        }
    }

    private AliasSelectionExecutor selectionExecutor(AliasSelection selection,
            Function<ProcessRef.Version, ProcessModelType> modelTypeResolver) {
        ProcessRef.Version effective = selection.version();
        ProcessEngine engine = get(requireResolvedModelType(modelTypeResolver, effective));
        return requireAliasSelectionExecutor(engine);
    }
}
