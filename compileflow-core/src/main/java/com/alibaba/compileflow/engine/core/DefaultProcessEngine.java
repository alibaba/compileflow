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
package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessExecutionException;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.java.codegen.JavaProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEligibilityChecker;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallContract;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import com.alibaba.compileflow.engine.core.event.ProcessEventPublisher;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.lifecycle.BudgetedShutdown;
import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import com.alibaba.compileflow.engine.core.observability.tracing.TraceIds;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.preflight.ProcessPreflightService;
import com.alibaba.compileflow.engine.core.runtime.CompiledProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.InterpretedProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessFailureClassifier;
import com.alibaba.compileflow.engine.core.runtime.ProcessCallDepthException;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.ProcessCallGraph;
import com.alibaba.compileflow.engine.core.runtime.ProcessExecutionGraphPreparer;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.runtime.cache.DefaultProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.runtime.loading.DefaultProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionScope;
import com.alibaba.compileflow.engine.core.runtime.context.ProcessCallInvoker;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.engine.core.runtime.resolution.ProcessRuntimeResolver;
import com.alibaba.compileflow.engine.core.runtime.resolution.ProcessRuntimeResolution;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.core.routing.AliasSelectionExecutor;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.observability.TraceIdProvider;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ProcessEngine} assembled from source-format front-end collaborators.
 *
 * @author yusu
 */
public final class DefaultProcessEngine
        implements ProcessEngine, ProcessRuntimeManager, ProcessToolingService, ProcessRuntimeOwnership,
        ProcessCallInspector, ProcessExecutionGraphPreparer, AliasSelectionExecutor, ProcessCallInvoker,
        BudgetedShutdown {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessEngine.class);
    private static final String RUNTIME_MANAGER_OWNER = ProcessRuntimeManager.class.getName();
    private static final int VARIABLE_DIAGNOSTIC_LIMIT = 8;
    private static final int MAX_PROCESS_CALL_SITES = 4_096;
    private final ProcessEngineExecutors executors;
    private final ProcessEventPublisher eventPublisher;
    private final ProcessComponentResolver componentResolver;
    private final ProcessDataMapper dataMapper;
    private final ProcessSemanticCompiler<?> semanticCompiler;
    private final ProcessModelType modelType;
    private final ProcessRuntimeCache runtimeCache;
    private final Cache<ProcessRef.Version, CachedProcessCallGraph> processCallGraphs;
    private final Object processCallGraphMutationLock = new Object();
    private long processCallGraphGeneration;
    private final int runtimeLoadMaxConcurrency;
    private final int maxProcessCallDepth;
    private final ClassLoader classLoader;
    private final ProcessObservabilityConfig observabilityConfig;
    private final TraceIdProvider traceIdProvider;
    private final ScriptExecutorRegistry scriptExecutors;
    private final Map<String, RetryPolicy> retryPolicies;
    private final Map<String, FailureHandler> failureHandlers;
    private final ProcessContextPropagator contextPropagator;
    private final ProcessRuntimeLoader runtimeLoader;
    private final ProcessRuntimeResolver processRuntimeResolver;
    private final LocalRoutingState localRoutingState;
    private final ProcessPreflightService preflightService;
    private final OperationGate lifecycleGate = new OperationGate("ProcessEngine");

    public DefaultProcessEngine(ProcessEngineConfig config, EngineDependencies dependencies,
            ProcessSemanticCompiler<?> semanticCompiler) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dependencies, "dependencies");
        this.semanticCompiler = Objects.requireNonNull(semanticCompiler, "semanticCompiler");
        this.modelType = config.getModelType();
        this.runtimeLoadMaxConcurrency = config.getExecutorConfig().getRuntimeLoadMaxConcurrency();
        this.maxProcessCallDepth = config.getMaxCallDepth();
        this.classLoader = config.getClassLoader();
        JavaDiagnosticsConfig javaDiagnostics = config.getJavaDiagnostics();
        this.observabilityConfig = config.getObservabilityConfig();
        this.traceIdProvider = config.getTraceIdProvider();
        this.componentResolver = dependencies.componentResolver();
        this.dataMapper = dependencies.dataMapper();
        this.scriptExecutors = dependencies.scriptExecutors();
        this.retryPolicies = config.getRetryPolicies();
        this.failureHandlers = config.getFailureHandlers();
        this.contextPropagator = config.getContextPropagator();
        this.processCallGraphs = CacheBuilder.newBuilder().maximumSize(config.getMaxResidentRuntimes()).build();
        ProcessEngineExecutors createdExecutors = null;
        ProcessRuntimeCache createdRuntimeCache = null;
        ProcessRuntimeLoader createdProcessRuntimeLoader = null;
        try {
            createdExecutors = ProcessEngineExecutors.create(config.getExecutorConfig(), config.getObservabilityConfig(),
                    config.getShutdownTimeout());
            ProcessEventPublisher createdEventPublisher = new ProcessEventPublisher(config.getObservabilityConfig(),
                    config.getEventListeners(), createdExecutors.event());
            createdRuntimeCache = new DefaultProcessRuntimeCache(config.getMaxResidentRuntimes());
            ProcessRuntimeFactory runtimeFactory = config.getRuntimeMode() == ProcessRuntimeMode.INTERPRETED
                    ? new InterpretedProcessRuntimeFactory(this.semanticCompiler, dependencies.javaCompiler(),
                            javaDiagnostics, dependencies.componentResolver(), dependencies.scriptExecutors())
                    : new CompiledProcessRuntimeFactory(this.semanticCompiler, dependencies.scriptExecutors(),
                            dependencies.javaCompiler(), javaDiagnostics);
            createdProcessRuntimeLoader = new DefaultProcessRuntimeLoader(createdRuntimeCache,
                    dependencies.definitionLoader(), runtimeFactory, createdExecutors.runtimeLoad(),
                    config.getRuntimeLoadTimeout(), config.getModelType());
            ProcessRuntimeResolver createdRuntimeResolver = new ProcessRuntimeResolver(createdRuntimeCache,
                    createdProcessRuntimeLoader, dependencies.localRoutingState(), dependencies.aliasAdmission());
            ProcessPreflightService createdPreflightService = new ProcessPreflightService(createdProcessRuntimeLoader,
                    source -> this.semanticCompiler.compile(source), createdExecutors.preflight());

            this.executors = createdExecutors;
            this.eventPublisher = createdEventPublisher;
            this.runtimeCache = createdRuntimeCache;
            this.runtimeLoader = createdProcessRuntimeLoader;
            this.processRuntimeResolver = createdRuntimeResolver;
            this.localRoutingState = dependencies.localRoutingState();
            this.preflightService = createdPreflightService;
        } catch (RuntimeException | Error startupFailure) {
            closeAfterFailedConstruction(createdProcessRuntimeLoader, createdRuntimeCache, createdExecutors,
                    startupFailure);
            throw startupFailure;
        }
    }

    private static String requireOwnerId(String ownerId) {
        return SemanticText.requireIdentity(ownerId, "ownerId");
    }

    private static void closeAfterFailedConstruction(ProcessRuntimeLoader runtimeLoader,
            ProcessRuntimeCache runtimeCache, ProcessEngineExecutors executors, Throwable startupFailure) {
        closeAfterFailedConstruction(runtimeLoader, startupFailure);
        closeAfterFailedConstruction(runtimeCache == null ? null : runtimeCache::invalidateAll, startupFailure);
        closeAfterFailedConstruction(executors, startupFailure);
    }

    private static void closeAfterFailedConstruction(AutoCloseable resource, Throwable startupFailure) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception | Error closeFailure) {
            startupFailure.addSuppressed(closeFailure);
        }
    }

    private static Map<String, Object> requireMappedVariables(Map<String, Object> variables) {
        return Objects.requireNonNull(variables, "ProcessDataMapper must not return null variables");
    }

    private static String resolveInvocationId(ProcessExecutionOptions options) {
        String requested = options.getInvocationId();
        return requested != null ? requested : UUID.randomUUID().toString();
    }

    private static ProcessExecutionOptions prepareExecutionOptions(ProcessRuntimeRequest request,
            ProcessExecutionOptions options) {
        ProcessExecutionOptions requested = Objects.requireNonNull(options, "options");
        String invocationId = resolveInvocationId(requested);
        AliasRoutingOptions routing = requested.getAliasRouting();
        AliasRoutingOptions effectiveRouting = request.getAlias() != null && routing.routingKey() == null
                ? new AliasRoutingOptions(invocationId, routing.attributes())
                : routing;
        if (invocationId.equals(requested.getInvocationId()) && effectiveRouting == routing) {
            return requested;
        }
        return ProcessExecutionOptions.builder().invocationId(invocationId).aliasRouting(effectiveRouting).build();
    }

    private static Throwable closeStep(String resource, Runnable closeAction, Throwable priorFailure) {
        try {
            closeAction.run();
        } catch (Throwable failure) {
            LOGGER.error("Failed to close ProcessEngine resource [{}]", resource, failure);
            if (priorFailure == null) {
                return failure;
            }
            priorFailure.addSuppressed(failure);
        }
        return priorFailure;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
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

    private static String failureType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName();
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("ProcessEngine shutdown failed", failure);
    }

    private static String bindingKey(ProcessRuntimeRequest request) {
        return RuntimeCacheKeys.forProcess(request.getNamespace(), request.getCode(), request.getVersion());
    }

    @Override
    public <I, O> ProcessResult<O> execute(ProcessRef ref, I input, Class<O> outputType,
            ProcessExecutionOptions options) {
        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(Objects.requireNonNull(ref, "ref"));
        return executeTyped(request, input, outputType, options);
    }

    @Override
    public <I, O> ProcessResult<O> execute(ProcessDefinition definition, I input, Class<O> outputType,
            ProcessExecutionOptions options) {
        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(Objects.requireNonNull(definition, "definition"));
        return executeTyped(request, input, outputType, options);
    }

    @Override
    public ProcessResult<Map<String, Object>> execute(ProcessRef ref, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        return executeRequest(ProcessRuntimeRequest.from(Objects.requireNonNull(ref, "ref")), variables, options);
    }

    @Override
    public ProcessResult<Map<String, Object>> execute(ProcessRef.Alias alias, AliasSelection selection,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        return executeRequest(ProcessRuntimeRequest.forAliasSelection(alias, selection), variables, options);
    }

    @Override
    public ProcessResult<Map<String, Object>> invoke(ProcessCallGraph callGraph, ProcessCallGraph.ProcessNode target,
            Map<String, Object> variables) {
        Objects.requireNonNull(callGraph, "callGraph");
        ProcessCallGraph.ProcessNode processNode = Objects.requireNonNull(target, "target");
        lifecycleGate.enter();
        try {
            ProcessRuntimeRequest request =
                    ProcessRuntimeRequest.forProcessIdentity(processNode.namespace(), processNode.code(),
                            processNode.version());
            ProcessExecutionTarget executionTarget = new ProcessExecutionTarget(callGraph, processNode, null);
            return executeInOperation(request, Objects.requireNonNull(variables, "variables"), ProcessExecutionOptions.defaults(), () -> executionTarget);
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public ProcessResult<Map<String, Object>> execute(ProcessDefinition definition, Map<String, Object> variables,
            ProcessExecutionOptions options) {
        return executeRequest(ProcessRuntimeRequest.from(Objects.requireNonNull(definition, "definition")), variables,
                options);
    }

    private ProcessResult<Map<String, Object>> executeRequest(ProcessRuntimeRequest request,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        Objects.requireNonNull(variables, "variables");
        ProcessExecutionOptions requestOptions = prepareExecutionOptions(request, options);
        ProcessRuntimeRequest admissionRequest = request.withAliasRouting(requestOptions.getAliasRouting());
        lifecycleGate.enter();
        try {
            return executeInOperation(admissionRequest, variables, requestOptions);
        } finally {
            lifecycleGate.exit();
        }
    }

    private ProcessResult<Map<String, Object>> executeInOperation(ProcessRuntimeRequest request,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        return executeInOperation(request, variables, options, () -> resolveExecutionTarget(request));
    }

    private ProcessResult<Map<String, Object>> executeInOperation(ProcessRuntimeRequest request,
            Map<String, Object> variables, ProcessExecutionOptions options,
            Supplier<ProcessExecutionTarget> targetResolver) {
        String processCode = request.getCode();
        EngineExecutionScope executionScope = setupExecutionContext(request, options);
        EngineExecutionContext executionContext = executionScope.context();

        try {
            executionContext.validateProcessCallDepth();
            eventPublisher.publishExecutionStarted(request.getNamespace(), processCode, executionContext.invocationId());

            ProcessExecutionTarget target = targetResolver.get();
            executionContext.bindInvocation(target.callGraph(), target.processNode(), target.aliasSelection());
            ProcessRuntime runtime = target.processNode().runtimeEntry().getRuntime();
            validateProcessInput(runtime.getSemanticPlan(), variables);
            Map<String, Object> result = runtime.execute(variables);

            long durationMs = executionContext.duration();
            ProcessExecution execution = executionContext.processExecution();
            eventPublisher.publishExecutionCompleted(execution, executionContext.executionAttribution(), durationMs);
            return ProcessResult.success(result, execution);
        } catch (Exception failure) {
            long durationMs = executionContext.duration();
            ProcessExecution execution = executionContext.processExecution();
            if (failure instanceof ProcessExecutionException nestedFailure) {
                ProcessError nestedError = nestedFailure.getError();
                LOGGER.error("Nested process execution failed: code={}, durationMs={}, errorCode={}, failureType={}",
                        processCode, durationMs, nestedError.getCode(), failureType(nestedFailure));
                eventPublisher.publishExecutionFailed(execution, executionContext.executionAttribution(), durationMs,
                        nestedError);
                return ProcessResult.failure(nestedError, execution);
            }
            CompileFlowException classifiedFailure = ProcessFailureClassifier
                .classify(failure)
                .withContext("processCode", processCode)
                .withContext("durationMs", durationMs)
                .withContext("traceId", executionContext.traceId());
            LOGGER.error("Process execution failed: code={}, durationMs={}, errorCode={}, failureType={}", processCode,
                    durationMs, classifiedFailure.getErrorCode().getCode(), failureType(classifiedFailure));
            ProcessError processError = ProcessFailureClassifier.toProcessError(classifiedFailure);
            eventPublisher.publishExecutionFailed(execution, executionContext.executionAttribution(), durationMs,
                    processError);
            return ProcessResult.failure(processError, execution);
        } finally {
            executionScope.close();
        }
    }

    @Override
    public <I, O> ProcessResult<O> trigger(ProcessRef ref, ProcessTrigger trigger, I input, Class<O> outputType,
            ProcessExecutionOptions options) {
        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(Objects.requireNonNull(ref, "ref"));
        return triggerTyped(request, Objects.requireNonNull(trigger, "trigger"), input, outputType, options);
    }

    @Override
    public <I, O> ProcessResult<O> trigger(ProcessDefinition definition, ProcessTrigger trigger, I input,
            Class<O> outputType, ProcessExecutionOptions options) {
        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(Objects.requireNonNull(definition, "definition"));
        return triggerTyped(request, Objects.requireNonNull(trigger, "trigger"), input, outputType, options);
    }

    @Override
    public ProcessResult<Map<String, Object>> trigger(ProcessRef ref, ProcessTrigger trigger,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        return triggerRequest(ProcessRuntimeRequest.from(Objects.requireNonNull(ref, "ref")),
                Objects.requireNonNull(trigger, "trigger"), variables, options);
    }

    @Override
    public ProcessResult<Map<String, Object>> trigger(ProcessDefinition definition, ProcessTrigger trigger,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        return triggerRequest(ProcessRuntimeRequest.from(Objects.requireNonNull(definition, "definition")),
                Objects.requireNonNull(trigger, "trigger"), variables, options);
    }

    private ProcessResult<Map<String, Object>> triggerRequest(ProcessRuntimeRequest request, ProcessTrigger trigger,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        Objects.requireNonNull(variables, "variables");
        ProcessExecutionOptions requestOptions = prepareExecutionOptions(request, options);
        ProcessRuntimeRequest admissionRequest = request.withAliasRouting(requestOptions.getAliasRouting());
        lifecycleGate.enter();
        try {
            return triggerInOperation(admissionRequest, trigger, variables, requestOptions);
        } finally {
            lifecycleGate.exit();
        }
    }

    private ProcessResult<Map<String, Object>> triggerInOperation(ProcessRuntimeRequest request, ProcessTrigger trigger,
            Map<String, Object> variables, ProcessExecutionOptions options) {
        String processCode = request.getCode();
        String nodeId = trigger.nodeId();
        String event = trigger.event();
        EngineExecutionScope executionScope = setupExecutionContext(request, options);
        EngineExecutionContext executionContext = executionScope.context();

        try {
            executionContext.validateProcessCallDepth();
            eventPublisher.publishTriggerStarted(request.getNamespace(), processCode, executionContext.invocationId(),
                    trigger);

            ProcessRuntimeResolution resolution = resolveProcessRuntime(request);
            ProcessCallGraph graph = resolveProcessCallGraph(request, resolution.entry(), resolution.version());
            executionContext.bindInvocation(graph, graph.root(), resolution.aliasSelection());
            ProcessRuntime runtime = resolution.entry().getRuntime();
            validateTriggerableProcess(request, nodeId, runtime);
            validateTriggerState(runtime.getSemanticPlan(), variables);
            Map<String, Object> result = runtime.trigger(trigger, variables);

            long durationMs = executionContext.duration();
            ProcessExecution execution = executionContext.processExecution();
            eventPublisher.publishTriggerCompleted(execution, executionContext.executionAttribution(), trigger,
                    durationMs);
            return ProcessResult.success(result, execution);
        } catch (Exception failure) {
            long durationMs = executionContext.duration();
            ProcessExecution execution = executionContext.processExecution();
            if (failure instanceof ProcessExecutionException nestedFailure) {
                ProcessError nestedError = nestedFailure.getError();
                LOGGER.error("Nested process trigger failed: code={}, nodeId={}, event={}, durationMs={}, "
                        + "errorCode={}, failureType={}", processCode, nodeId, event, durationMs, nestedError.getCode(),
                        failureType(nestedFailure));
                eventPublisher.publishTriggerFailed(execution, executionContext.executionAttribution(), trigger,
                        durationMs, nestedError);
                return ProcessResult.failure(nestedError, execution);
            }
            CompileFlowException classifiedFailure = ProcessFailureClassifier
                .classify(failure)
                .withContext("processCode", processCode)
                .withContext("durationMs", durationMs)
                .withContext("traceId", executionContext.traceId())
                .withContext("nodeId", nodeId)
                .withContext("event", event);
            LOGGER.error("Process trigger failed: code={}, nodeId={}, event={}, durationMs={}, errorCode={}, "
                    + "failureType={}", processCode, nodeId, event, durationMs,
                    classifiedFailure.getErrorCode().getCode(), failureType(classifiedFailure));
            ProcessError processError = ProcessFailureClassifier.toProcessError(classifiedFailure);
            eventPublisher.publishTriggerFailed(execution, executionContext.executionAttribution(), trigger, durationMs,
                    processError);
            return ProcessResult.failure(processError, execution);
        } finally {
            executionScope.close();
        }
    }

    @Override
    public ProcessRuntimeManager runtime() {
        return this;
    }

    @Override
    public ProcessToolingService tooling() {
        return this;
    }

    @Override
    public void close() {
        close(executors.shutdownTimeout());
    }

    @Override
    public void close(Duration gracefulBudget) {
        Duration budget = Objects.requireNonNull(gracefulBudget, "gracefulBudget");
        if (budget.isNegative()) {
            throw new IllegalArgumentException("gracefulBudget must not be negative");
        }
        EngineExecutionContext currentContext = EngineExecutionContextHolder.current();
        if ((currentContext != null && currentContext.ownedBy(this)) || executors.ownsCurrentThread()) {
            throw new IllegalStateException(
                    "ProcessEngine cannot be closed from an active operation or engine callback");
        }
        long startedAtNanos = System.nanoTime();
        OperationGate.DrainResult drain = lifecycleGate.beginCloseAndAwaitDrained(budget);
        if (!drain.cleanupOwner()) {
            return;
        }
        Throwable failure = null;
        try {
            LOGGER.info("Shutting down ProcessEngine...");
            int cacheSize = (int) runtimeCache.size();
            if (drain != OperationGate.DrainResult.DRAINED) {
                LOGGER.warn("ProcessEngine operation drain did not complete before forced cleanup: result={}, active={}",
                        drain, lifecycleGate.activeOperations());
            }
            failure = closeStep("runtime loader", runtimeLoader::close, failure);
            failure = closeStep("engine executors", () -> executors.close(remaining(budget, startedAtNanos)), failure);
            failure = closeStep("runtime cache", runtimeCache::invalidateAll, failure);
            invalidateProcessCallGraphs();
            failure = closeStep("script executors", scriptExecutors::close, failure);

            LOGGER.info("ProcessEngine shutdown completed in {}ms (cached={})", elapsedMillis(startedAtNanos), cacheSize);
        } finally {
            lifecycleGate.finishClose();
        }
        rethrowCloseFailure(failure);
    }

    @Override
    public void warmUp(ProcessDefinition... definitions) {
        ProcessDefinition[] sources = Objects.requireNonNull(definitions, "definitions");
        ProcessRuntimeRequest[] requests =
                Arrays.stream(sources).map(ProcessRuntimeRequest::from).toArray(ProcessRuntimeRequest[]::new);
        warmUpRequests(requests);
    }

    @Override
    public void load(ProcessRef.Version ref, ProcessDefinition definition) {
        loadRequest(RUNTIME_MANAGER_OWNER, ProcessRuntimeRequest.versioned(ref, definition), true);
    }

    @Override
    public void loadOwned(String ownerId, ProcessRef.Version ref, ProcessDefinition definition) {
        loadRequest(requireOwnerId(ownerId), ProcessRuntimeRequest.versioned(ref, definition), false);
    }

    @Override
    public boolean retainOwned(String ownerId, ProcessRef.Version ref) {
        ProcessRef.Version versionRef = Objects.requireNonNull(ref, "ref");
        lifecycleGate.enter();
        try {
            return runtimeCache.retain(RuntimeCacheKeys.forProcess(versionRef.namespace(), versionRef.code(),
                            versionRef.version()), requireOwnerId(ownerId));
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public ReleaseOutcome releaseOwned(String ownerId, ProcessRef.Version ref) {
        ProcessRef.Version versionRef = Objects.requireNonNull(ref, "ref");
        lifecycleGate.enter();
        try {
            ProcessRuntimeCache.ReleaseResult result = runtimeCache.release(RuntimeCacheKeys.forProcess(versionRef.namespace(),
                            versionRef.code(), versionRef.version()), requireOwnerId(ownerId));
            if (result == ProcessRuntimeCache.ReleaseResult.INVALIDATED) {
                invalidateProcessCallGraphs();
                markRuntimeUndeployed(versionRef);
                return ReleaseOutcome.REMOVED;
            }
            return result == ProcessRuntimeCache.ReleaseResult.RETAINED
                    ? ReleaseOutcome.RETAINED
                    : ReleaseOutcome.NOT_OWNED;
        } finally {
            lifecycleGate.exit();
        }
    }

    private void loadRequest(String ownerId, ProcessRuntimeRequest request, boolean recordLocalInstallation) {
        lifecycleGate.enter();
        try {
            LOGGER.info("Loading process runtime: code={}", request.getCode());
            long startedAtNanos = System.nanoTime();
            ClassLoader effectiveClassLoader = resolveEffectiveClassLoader(null);
            try {
                runtimeLoader.loadBatch(ownerId, effectiveClassLoader, request);
                ProcessRuntimeEntry entry = runtimeCache.getIfPresent(bindingKey(request));
                if (entry == null) {
                    throw new IllegalStateException(
                            "Retained runtime disappeared after loading: " + bindingKey(request));
                }
                if (recordLocalInstallation) {
                    markInstalled(request);
                }
                LOGGER.info("Process runtime loaded: code={} digest={} durationMs={}", request.getCode(),
                        entry.getDigest(), elapsedMillis(startedAtNanos));
            } catch (Exception failure) {
                LOGGER.error("Process runtime load failed: code={} failureType={}", request.getCode(),
                        failureType(failure));
                throw failure;
            }
        } finally {
            lifecycleGate.exit();
        }
    }

    private void warmUpRequests(ProcessRuntimeRequest... requests) {
        lifecycleGate.enter();
        try {
            if (ArrayUtils.isEmpty(requests)) {
                return;
            }
            LOGGER.info("Warming {} exact process runtimes: codes={}", requests.length,
                    Arrays.stream(requests).map(ProcessRuntimeRequest::getCode).collect(Collectors.joining(", ")));
            long startedAtNanos = System.nanoTime();
            ClassLoader effectiveClassLoader = resolveEffectiveClassLoader(null);
            if (runtimeLoadMaxConcurrency > 1 && requests.length > 1) {
                runtimeLoader.loadExactBatch(effectiveClassLoader, requests);
            } else {
                for (ProcessRuntimeRequest request : requests) {
                    runtimeLoader.loadExactSync(request, effectiveClassLoader);
                }
            }
            LOGGER.info("Warmed {} exact process runtimes in {}ms", requests.length, elapsedMillis(startedAtNanos));
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public void unload(ProcessRef.Version... refs) {
        lifecycleGate.enter();
        try {
            if (ArrayUtils.isEmpty(refs)) {
                return;
            }

            LOGGER.info("Unloading {} processes: codes={}", refs.length,
                    Arrays.stream(refs).filter(Objects::nonNull).map(ProcessRef::code).collect(Collectors.joining(", ")));

            for (int index = 0; index < refs.length; index++) {
                ProcessRef.Version ref = Objects.requireNonNull(refs[index], "refs[" + index + "]");
                String bindingKey = RuntimeCacheKeys.forProcess(ref.namespace(), ref.code(), ref.version());
                ProcessRuntimeCache.ReleaseResult released = runtimeCache.release(bindingKey, RUNTIME_MANAGER_OWNER);
                boolean invalidated = released == ProcessRuntimeCache.ReleaseResult.INVALIDATED
                        || released == ProcessRuntimeCache.ReleaseResult.NOT_RETAINED
                        && runtimeCache.invalidateIfUnretained(bindingKey);
                if (invalidated) {
                    invalidateProcessCallGraphs();
                    markRuntimeUndeployed(ref);
                }
            }
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public ProcessPreflightReport preflight(ProcessDefinition definition, ProcessPreflightOptions options) {
        lifecycleGate.enter();
        try {
            ClassLoader effectiveClassLoader = resolveEffectiveClassLoader(null);
            return preflightService.preflight(effectiveClassLoader, Objects.requireNonNull(definition, "definition"),
                    Objects.requireNonNull(options, "options"));
        } finally {
            lifecycleGate.exit();
        }
    }

    private void markInstalled(ProcessRuntimeRequest request) {
        if (request.getVersion() == null) {
            return;
        }
        localRoutingState
            .getInstalledVersionState()
            .markInstalled(request.getNamespace(), request.getCode(), request.getVersion());
    }

    @Override
    public String generateJavaCode(ProcessDefinition definition) {
        ProcessDefinition requestedDefinition = Objects.requireNonNull(definition, "definition");
        lifecycleGate.enter();
        try {
            ClassLoader effectiveClassLoader = resolveEffectiveClassLoader(null);
            ProcessDefinitionSnapshot snapshot =
                    runtimeLoader.resolve(ProcessRuntimeRequest.from(requestedDefinition), effectiveClassLoader);
            ProcessSemanticCompiler.ProcessSemanticCompilation compilation = semanticCompiler.compile(snapshot);
            ProcessRuntimeEligibilityChecker.validate(compilation.semanticPlan(), compilation.structuredPlan());
            ScriptProgramCatalog.validate(compilation.semanticPlan(), scriptExecutors);
            return new JavaProcessCodeGenerator(compilation.semanticPlan(), compilation.structuredPlan(),
                    compilation.nodeNames())
                .generateCode();
        } catch (CompileFlowException classified) {
            throw classified;
        } catch (Exception failure) {
            LOGGER.error("Failed to generate Java code: code={}", requestedDefinition.code(), failure);
            throw new CompileFlowException(ErrorCode.CF_COMPILE_002, "Failed to generate Java code", failure);
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public List<ProcessCallInspector.DeclaredProcessCall> inspectProcessCalls(ProcessDefinition definition) {
        ProcessDefinition requested = Objects.requireNonNull(definition, "definition");
        lifecycleGate.enter();
        try {
            ProcessDefinitionSnapshot source =
                    runtimeLoader.resolve(ProcessRuntimeRequest.from(requested), resolveEffectiveClassLoader(null));
            ProcessSemanticPlan plan = semanticCompiler.compile(source).semanticPlan();
            List<ProcessCallInspector.DeclaredProcessCall> calls = new ArrayList<>();
            for (ProcessSemanticPlan.NodePlan node : plan.getNodes().values()) {
                if (node.operation() instanceof ProcessCallPlan call) {
                    calls.add(new ProcessCallInspector.DeclaredProcessCall(node.id(), call.code(), call.target()));
                }
            }
            return List.copyOf(calls);
        } finally {
            lifecycleGate.exit();
        }
    }

    @Override
    public ProcessCallGraph prepareExact(ProcessRef.Version root) {
        ProcessRef.Version exact = Objects.requireNonNull(root, "root");
        lifecycleGate.enter();
        try {
            ProcessRuntimeRequest request = ProcessRuntimeRequest.from(exact);
            ProcessRuntimeEntry entry =
                    runtimeCache.getIfPresent(RuntimeCacheKeys.forProcess(exact.namespace(), exact.code(),
                            exact.version()));
            if (entry == null) {
                throw new CompileFlowException(ErrorCode.CF_EXEC_012, "Exact Process runtime is not installed: " + exact);
            }
            return resolveProcessCallGraph(request, entry, exact.version());
        } finally {
            lifecycleGate.exit();
        }
    }

    private ProcessRuntimeResolution resolveProcessRuntime(ProcessRuntimeRequest request) {
        return processRuntimeResolver.resolve(resolveEffectiveClassLoader(classLoader), request);
    }

    private ProcessExecutionTarget resolveExecutionTarget(ProcessRuntimeRequest request) {
        ProcessRuntimeResolution resolution = resolveProcessRuntime(request);
        ProcessCallGraph callGraph = resolveProcessCallGraph(request, resolution.entry(), resolution.version());
        return new ProcessExecutionTarget(callGraph, callGraph.root(), resolution.aliasSelection());
    }

    private ProcessCallGraph resolveProcessCallGraph(ProcessRuntimeRequest request, ProcessRuntimeEntry rootEntry,
            String version) {
        ProcessRef.Version rootVersion =
                version == null ? null : ProcessRef.version(request.getNamespace(), request.getCode(), version);
        if (rootVersion != null) {
            CachedProcessCallGraph cached = processCallGraphs.getIfPresent(rootVersion);
            if (cached != null && cached.rootIdentity().equals(rootEntry.getRuntimeIdentity())) {
                return cached.callGraph();
            }
        }
        long generation = processCallGraphGenerationSnapshot();
        ProcessCallGraph.ProcessNode root =
                processNode(request.getNamespace(), request.getCode(), rootVersion, rootEntry);
        Map<ProcessCallGraph.CallSite, ProcessCallGraph.ProcessNode> calls = new LinkedHashMap<>();
        Map<String, ProcessCallGraph.ProcessNode> nodes = new LinkedHashMap<>();
        nodes.put(root.id(), root);
        resolveProcessCalls(root, calls, nodes, new HashSet<>(), new HashSet<>(),
                resolveEffectiveClassLoader(classLoader));
        validateProcessCallDepth(root, calls, nodes);
        ProcessCallGraph resolved = new ProcessCallGraph(root, calls);
        if (rootVersion != null && calls.values().stream().allMatch(node -> node.version() != null)) {
            synchronized (processCallGraphMutationLock) {
                if (generation == processCallGraphGeneration) {
                    processCallGraphs.put(rootVersion,
                            new CachedProcessCallGraph(rootEntry.getRuntimeIdentity(), resolved));
                }
            }
        }
        return resolved;
    }

    private void invalidateProcessCallGraphs() {
        synchronized (processCallGraphMutationLock) {
            processCallGraphGeneration++;
            processCallGraphs.invalidateAll();
        }
    }

    private long processCallGraphGenerationSnapshot() {
        synchronized (processCallGraphMutationLock) {
            return processCallGraphGeneration;
        }
    }

    private void resolveProcessCalls(ProcessCallGraph.ProcessNode caller,
            Map<ProcessCallGraph.CallSite, ProcessCallGraph.ProcessNode> calls,
            Map<String, ProcessCallGraph.ProcessNode> nodes, Set<String> resolved, Set<String> visiting,
            ClassLoader effectiveClassLoader) {
        if (resolved.contains(caller.id())) {
            return;
        }
        if (!visiting.add(caller.id())) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_014,
                    "Process call graph contains a cycle at " + caller.id());
        }
        try {
            for (ProcessSemanticPlan.NodePlan node : caller
                .runtimeEntry()
                .getRuntime()
                .getSemanticPlan()
                .getNodes()
                .values()) {
                if (!(node.operation() instanceof ProcessCallPlan call)) {
                    continue;
                }
                if (calls.size() >= MAX_PROCESS_CALL_SITES) {
                    throw new CompileFlowException(ErrorCode.CF_EXEC_014,
                            "Process call graph exceeds the maximum of " + MAX_PROCESS_CALL_SITES + " call sites");
                }
                validateCallAuthority(caller, call);
                ProcessCallGraph.ProcessNode target = resolveProcessCall(caller, node.id(), call, effectiveClassLoader);
                ProcessCallGraph.CallSite callSite = new ProcessCallGraph.CallSite(caller.id(), node.id());
                if (calls.putIfAbsent(callSite, target) != null) {
                    throw new IllegalStateException("Duplicate Process call site: " + callSite);
                }
                nodes.putIfAbsent(target.id(), target);
                resolveProcessCalls(target, calls, nodes, resolved, visiting, effectiveClassLoader);
            }
            resolved.add(caller.id());
        } finally {
            visiting.remove(caller.id());
        }
    }

    private void validateProcessCallDepth(ProcessCallGraph.ProcessNode root,
            Map<ProcessCallGraph.CallSite, ProcessCallGraph.ProcessNode> calls,
            Map<String, ProcessCallGraph.ProcessNode> nodes) {
        Map<String, List<ProcessCallGraph.ProcessNode>> targetsByCaller = new LinkedHashMap<>();
        Map<String, Integer> incoming = new LinkedHashMap<>();
        for (Map.Entry<ProcessCallGraph.CallSite, ProcessCallGraph.ProcessNode> entry : calls.entrySet()) {
            String callerId = entry.getKey().callerId();
            ProcessCallGraph.ProcessNode target = entry.getValue();
            targetsByCaller
                .computeIfAbsent(callerId, ignored -> new ArrayList<>())
                .add(target);
            incoming.merge(target.id(), 1, Integer::sum);
        }

        Map<String, Integer> depths = new LinkedHashMap<>();
        Map<String, List<String>> paths = new LinkedHashMap<>();
        depths.put(root.id(), 1);
        paths.put(root.id(), List.of(root.code()));
        java.util.ArrayDeque<ProcessCallGraph.ProcessNode> ready = new java.util.ArrayDeque<>();
        for (ProcessCallGraph.ProcessNode node : nodes.values()) {
            if (!incoming.containsKey(node.id())) {
                ready.addLast(node);
            }
        }
        while (!ready.isEmpty()) {
            ProcessCallGraph.ProcessNode caller = ready.removeFirst();
            Integer callerDepth = depths.get(caller.id());
            if (callerDepth == null) {
                continue;
            }
            List<String> callerPath = paths.get(caller.id());
            for (ProcessCallGraph.ProcessNode target : targetsByCaller.getOrDefault(caller.id(), List.of())) {
                int targetDepth = callerDepth + 1;
                List<String> targetPath = new ArrayList<>(callerPath);
                targetPath.add(target.code());
                if (targetDepth > depths.getOrDefault(target.id(), 0)) {
                    depths.put(target.id(), targetDepth);
                    paths.put(target.id(), targetPath);
                    if (targetDepth > maxProcessCallDepth) {
                        throw new ProcessCallDepthException(targetPath, maxProcessCallDepth);
                    }
                }
                int remainingIncoming = incoming.merge(target.id(), -1, Integer::sum);
                if (remainingIncoming == 0) {
                    ready.addLast(target);
                }
            }
        }
    }

    private ProcessCallGraph.ProcessNode resolveProcessCall(ProcessCallGraph.ProcessNode caller, String callSiteId,
            ProcessCallPlan call, ClassLoader effectiveClassLoader) {
        if (call.target() instanceof ProcessCallTarget.Classpath classpath) {
            return resolveClasspathCall(caller, callSiteId, call, classpath, effectiveClassLoader);
        }
        if (call.target() instanceof ProcessCallTarget.Version version) {
            return resolveVersionCall(caller, callSiteId, call, version);
        }
        throw new IllegalStateException("Unsupported Process call target: " + call.target().getClass().getName());
    }

    private static void validateCallAuthority(ProcessCallGraph.ProcessNode caller, ProcessCallPlan call) {
        if (caller.version() != null && call.target() instanceof ProcessCallTarget.Classpath) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_014,
                    "An exact Process Version may contain only versioned Process calls: caller=" + caller.code()
                    + ", target=" + call.code());
        }
    }

    private ProcessCallGraph.ProcessNode resolveClasspathCall(ProcessCallGraph.ProcessNode caller, String callSiteId,
            ProcessCallPlan call, ProcessCallTarget.Classpath classpath, ClassLoader effectiveClassLoader) {
        ProcessRuntimeRequest request =
                ProcessRuntimeRequest.from(ProcessDefinition.classpath(call.code(), classpath.resourcePath()));
        ProcessRuntimeEntry entry = runtimeLoader.loadExactSync(request, effectiveClassLoader);
        validateResolvedCall(call, callSiteId, entry);
        return processNode(caller.namespace(), call.code(), null, entry);
    }

    private ProcessCallGraph.ProcessNode resolveVersionCall(ProcessCallGraph.ProcessNode caller, String callSiteId,
            ProcessCallPlan call, ProcessCallTarget.Version version) {
        ProcessRef.Version target = ProcessRef.version(caller.namespace(), call.code(), version.version());
        ProcessRuntimeEntry entry =
                runtimeCache.getIfPresent(RuntimeCacheKeys.forProcess(target.namespace(), target.code(),
                        target.version()));
        if (entry == null) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_012,
                    "Versioned Process call target is not ready: caller=" + caller.id() + ", callSiteId=" + callSiteId
                    + ", target=" + target.namespace() + '/' + target.code() + '@' + target.version());
        }
        validateResolvedCall(call, callSiteId, entry);
        return processNode(target.namespace(), target.code(), target, entry);
    }

    private static void validateResolvedCall(ProcessCallPlan call, String callSiteId, ProcessRuntimeEntry entry) {
        try {
            ProcessCallContract.validate(callSiteId, call, entry.getRuntime().getSemanticPlan(),
                    entry.getRuntimeIdentity().getClassLoader());
        } catch (IllegalArgumentException failure) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_014, failure.getMessage(), failure);
        }
    }

    private static ProcessCallGraph.ProcessNode processNode(String namespace, String code, ProcessRef.Version version,
            ProcessRuntimeEntry entry) {
        String id = version == null
                ? "source:" + code + '@' + entry.getRuntimeIdentity().getSourceDigest()
                : "version:" + version.namespace() + '/' + version.code() + '@' + version.version();
        return new ProcessCallGraph.ProcessNode(id, namespace, code, version, entry);
    }

    private record ProcessExecutionTarget(ProcessCallGraph callGraph, ProcessCallGraph.ProcessNode processNode,
            AliasSelection aliasSelection) {
        private ProcessExecutionTarget {
            callGraph = Objects.requireNonNull(callGraph, "callGraph");
            processNode = Objects.requireNonNull(processNode, "processNode");
        }
    }

    private record CachedProcessCallGraph(ProcessRuntimeIdentity rootIdentity, ProcessCallGraph callGraph) {
        private CachedProcessCallGraph {
            rootIdentity = Objects.requireNonNull(rootIdentity, "rootIdentity");
            callGraph = Objects.requireNonNull(callGraph, "callGraph");
        }
    }

    /**
     * Enforces the closed caller-input contract derived from the exact Process model.
     *
     * <p>Only {@code param} variables are caller-owned at ProcessEngine execution admission. Process
     * outputs and internal state remain definition- and execution-owned even when the caller uses
     * the untyped Map API.</p>
     */
    private static void validateProcessInput(ProcessSemanticPlan plan, Map<String, Object> variables) {
        Set<String> parameters = plan
            .getVariables()
            .values()
            .stream()
            .filter(variable -> variable.role() == ProcessSemanticPlan.VariableRole.PARAM)
            .map(ProcessSemanticPlan.VariablePlan::name)
            .collect(Collectors.toSet());
        validateVariableKeys(parameters, variables, "Process input", "parameter");
    }

    /**
     * Validates the state seed supplied to a Process trigger-entry invocation.
     *
     * <p>A trigger starts a fresh downstream invocation rather than resuming a Durable Run, so it
     * may seed any root state field declared by the Process. It must still reject undeclared keys
     * instead of silently discarding caller mistakes.</p>
     */
    private static void validateTriggerState(ProcessSemanticPlan plan, Map<String, Object> variables) {
        validateVariableKeys(plan.getVariables().keySet(), variables, "Trigger state", "declared");
    }

    private static void validateVariableKeys(Set<String> allowedNames, Map<String, Object> variables, String boundary,
            String allowedKind) {
        int rejectedCount = 0;
        Set<String> rejectedNames = new TreeSet<>();
        for (Object candidate : variables.keySet()) {
            if (candidate instanceof String name && allowedNames.contains(name)) {
                continue;
            }
            rejectedCount++;
            if (rejectedNames.size() < VARIABLE_DIAGNOSTIC_LIMIT) {
                rejectedNames.add(candidate instanceof String name ? name : "<non-string-key>");
            }
        }
        if (rejectedCount == 0) {
            return;
        }

        List<String> diagnostics = new ArrayList<>(rejectedNames);
        String omitted =
                rejectedCount > diagnostics.size() ? " and " + (rejectedCount - diagnostics.size()) + " more" : "";
        throw new CompileFlowException.ValidationException("variables",
                boundary + " contains non-" + allowedKind + " variables: " + diagnostics + omitted);
    }

    private void markRuntimeUndeployed(ProcessRef.Version ref) {
        localRoutingState.getInstalledVersionState().markUninstalled(ref.namespace(), ref.code(), ref.version());
    }

    private static void validateTriggerableProcess(ProcessRuntimeRequest request, String nodeId, ProcessRuntime runtime) {
        ProcessSemanticPlan semanticPlan = runtime.getSemanticPlan();
        boolean triggerable = false;
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.operation() instanceof AwaitPlan) {
                triggerable = true;
                if (nodeId.equals(node.id())) {
                    return;
                }
            }
        }
        if (!triggerable) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_008,
                    "Process '" + request.getCode() + "' has no trigger entries.");
        }
        throw new CompileFlowException(ErrorCode.CF_EXEC_008,
                "Process '" + request.getCode() + "' does not have a trigger entry with id '" + nodeId + "'.");
    }

    private <I, O> ProcessResult<O> executeTyped(ProcessRuntimeRequest request, I input, Class<O> outputType,
            ProcessExecutionOptions options) {
        Objects.requireNonNull(input, "input");
        Class<O> targetType = Objects.requireNonNull(outputType, "outputType");
        ProcessExecutionOptions requestOptions = prepareExecutionOptions(request, options);
        ProcessRuntimeRequest admissionRequest = request.withAliasRouting(requestOptions.getAliasRouting());
        lifecycleGate.enter();
        try {
            Map<String, Object> variables;
            try {
                variables = requireMappedVariables(dataMapper.toVariables(input));
            } catch (RuntimeException failure) {
                return mappingFailure(admissionRequest, requestOptions, "Process input could not be mapped to variables",
                        failure);
            }
            return convertResult(executeInOperation(admissionRequest, variables, requestOptions), targetType);
        } finally {
            lifecycleGate.exit();
        }
    }

    private <I, O> ProcessResult<O> triggerTyped(ProcessRuntimeRequest request, ProcessTrigger trigger, I input,
            Class<O> outputType, ProcessExecutionOptions options) {
        Objects.requireNonNull(input, "input");
        Class<O> targetType = Objects.requireNonNull(outputType, "outputType");
        ProcessExecutionOptions requestOptions = prepareExecutionOptions(request, options);
        ProcessRuntimeRequest admissionRequest = request.withAliasRouting(requestOptions.getAliasRouting());
        lifecycleGate.enter();
        try {
            Map<String, Object> variables;
            try {
                variables = requireMappedVariables(dataMapper.toVariables(input));
            } catch (RuntimeException failure) {
                return mappingFailure(admissionRequest, requestOptions, "Trigger input could not be mapped to variables",
                        failure);
            }
            return convertResult(triggerInOperation(admissionRequest, trigger, variables, requestOptions), targetType);
        } finally {
            lifecycleGate.exit();
        }
    }

    private <O> ProcessResult<O> convertResult(ProcessResult<Map<String, Object>> result, Class<O> outputType) {
        if (!result.isSuccess()) {
            return ProcessResult.failure(result.getError(), result.getExecution());
        }
        try {
            if (outputType == Map.class) {
                return ProcessResult.success(outputType.cast(result.getOutput()), result.getExecution());
            }
            O output = dataMapper.fromVariables(result.getOutput(), outputType);
            return ProcessResult.success(output, result.getExecution());
        } catch (RuntimeException failure) {
            LOGGER.warn("Process completed, but its result could not be mapped to {}; mapperFailure={}",
                    outputType.getName(), failure.getClass().getName());
            return ProcessResult.failure(new ProcessError(ErrorCode.CF_EXEC_009.getCode(),
                            "Process completed, but its result could not be mapped to " + outputType.getName()
                            + "; process side effects may already have occurred"), result.getExecution());
        }
    }

    private <O> ProcessResult<O> mappingFailure(ProcessRuntimeRequest request, ProcessExecutionOptions options,
            String message, RuntimeException failure) {
        LOGGER.warn("{}: code={}, mapperFailure={}", message, request.getCode(), failure.getClass().getName());
        Instant now = Instant.now();
        return ProcessResult.failure(new ProcessError(ErrorCode.CF_EXEC_010.getCode(), message),
                ProcessExecution
                    .builder()
                    .traceId(TraceIds.current(traceIdProvider))
                    .invocationId(resolveInvocationId(options))
                    .namespace(request.getNamespace())
                    .processCode(request.getCode())
                    .processVersion(
                            request.getVersion() == null
                            ? null
                            : ProcessRef.version(request.getNamespace(), request.getCode(), request.getVersion()))
                    .startedAt(now)
                    .completedAt(now)
                    .build());
    }

    private EngineExecutionContext createExecutionContext(ProcessRuntimeRequest request,
            ProcessExecutionOptions options) {
        String invocationId = resolveInvocationId(options);
        EngineExecutionContext parentContext = EngineExecutionContextHolder.current();
        return EngineExecutionContext
            .builder()
            .traceId(TraceIds.current(traceIdProvider))
            .invocationId(invocationId)
            .namespace(request.getNamespace())
            .processCode(request.getCode())
            .processVersion(request.getVersion())
            .parentContext(parentContext)
            .maxProcessCallDepth(maxProcessCallDepth)
            .admittedAlias(
                    request.getAlias() == null ? null : ProcessRef.alias(request.getNamespace(), request.getCode(),
                            request.getAlias()))
            .modelType(modelType)
            .processCallInvoker(this)
            .executors(executors)
            .componentResolver(componentResolver)
            .scriptExecutors(scriptExecutors)
            .retryPolicies(retryPolicies)
            .failureHandlers(failureHandlers)
            .contextPropagator(contextPropagator)
            .mdcPropagationEnabled(observabilityConfig.isMdcPropagationEnabled())
            .build();
    }

    private EngineExecutionScope setupExecutionContext(ProcessRuntimeRequest request, ProcessExecutionOptions options) {
        EngineExecutionContext executionContext = createExecutionContext(request, options);
        return EngineExecutionScope.open(executionContext);
    }

    private ClassLoader resolveEffectiveClassLoader(ClassLoader override) {
        ClassLoader provided = (override != null) ? override : this.classLoader;
        return ProcessClassLoaderResolver.resolveEffectiveClassLoader(provided, DefaultProcessEngine.class);
    }
}
