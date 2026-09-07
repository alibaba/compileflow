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
package com.alibaba.compileflow.engine.core.runtime.context;

import com.alibaba.compileflow.engine.core.runtime.ProcessCallDepthException;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.ProcessCallGraph;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Engine execution context bound for one process run.
 *
 * <p>The bound execution target is published once before process code starts; all other execution
 * metadata and engine resources are immutable.
 *
 * @author yusu
 */
public final class EngineExecutionContext {
    private static final ProcessCalls UNBOUND_PROCESS_CALLS =
            (callSiteId, variables) -> {
        throw new IllegalStateException("Process call graph was not bound before execution");
    };
    // Execution metadata
    private final String traceId;
    private final String invocationId;
    private final String parentInvocationId;
    private final int callDepth;
    private final String namespace;
    private final String processCode;
    private final List<String> processCallPath;
    private final int maxProcessCallDepth;
    private final ProcessRef.Alias admittedAlias;
    private ProcessModelType modelType;
    private final Instant startedAt;
    private final long startedAtNanos;
    private final ConcurrentMap<String, AtomicLong> actionInvocationOrdinals = new ConcurrentHashMap<>();
    // Engine resources
    private final ProcessCallInvoker processCallInvoker;
    private final ExecutorService actionExecutor;
    private final ExecutorService parallelExecutor;
    private final Duration actionTimeoutCancellationGracePeriod;
    private final Duration parallelCancellationGracePeriod;
    private final ProcessComponentResolver componentResolver;
    private final ScriptExecutorRegistry scriptExecutors;
    private final Deque<ScriptProgramsScope> scriptProgramScopes = new ArrayDeque<>();
    private final Map<String, RetryPolicy> retryPolicies;
    private final Map<String, FailureHandler> failureHandlers;
    private final boolean mdcPropagationEnabled;
    private final ProcessContextPropagator contextPropagator;
    private volatile BoundExecution boundExecution;

    private EngineExecutionContext(Builder builder) {
        this.traceId = builder.traceId;
        this.invocationId = builder.invocationId;
        this.parentInvocationId = builder.parentContext == null ? null : builder.parentContext.invocationId;
        this.callDepth = builder.parentContext == null ? 0 : builder.parentContext.callDepth + 1;
        this.namespace = ProcessIdentifiers.requireNamespace(builder.namespace);
        this.processCode = ProcessIdentifiers.requireCode(builder.processCode);
        this.processCallPath = buildProcessCallPath(builder.parentContext, builder.processCode);
        this.maxProcessCallDepth = builder.maxProcessCallDepth;
        this.admittedAlias = builder.admittedAlias;
        this.modelType = builder.modelType;
        this.startedAt = builder.startedAt;
        this.startedAtNanos = builder.startedAtNanos;
        this.processCallInvoker = builder.processCallInvoker;
        this.actionExecutor = builder.executors.action();
        this.parallelExecutor = builder.executors.parallel();
        this.actionTimeoutCancellationGracePeriod = builder.executors.actionTimeoutCancellationGracePeriod();
        this.parallelCancellationGracePeriod = builder.executors.parallelCancellationGracePeriod();
        this.componentResolver = builder.componentResolver;
        this.scriptExecutors = builder.scriptExecutors;
        this.retryPolicies = Map.copyOf(builder.retryPolicies);
        this.failureHandlers = Map.copyOf(builder.failureHandlers);
        this.mdcPropagationEnabled = builder.mdcPropagationEnabled;
        this.contextPropagator = builder.contextPropagator;
        this.boundExecution = BoundExecution.unbound(builder.processVersion, builder.sourceDigest);
    }

    public static Builder builder() {
        return new Builder();
    }

    // Convenient thread pool access methods
    static Instant completionTime(Instant startedAt, long startedAtNanos, long completedAtNanos) {
        return startedAt.plusNanos(elapsedNanos(startedAtNanos, completedAtNanos));
    }

    private static long elapsedNanos(long startedAtNanos, long completedAtNanos) {
        long elapsedNanos = completedAtNanos - startedAtNanos;
        return Math.max(0L, elapsedNanos);
    }

    private static List<String> buildProcessCallPath(EngineExecutionContext parent, String processCode) {
        if (parent == null) {
            return List.of(processCode);
        }
        List<String> path = new ArrayList<>(parent.processCallPath.size() + 1);
        path.addAll(parent.processCallPath);
        path.add(processCode);
        return List.copyOf(path);
    }

    public ExecutorService action() {
        return actionExecutor;
    }

    public ExecutorService parallel() {
        return parallelExecutor;
    }

    /**
     * Resolves an application component for generated process code.
     * <p>
     * The configured resolver is authoritative. A missing or type-incompatible component
     * fails execution; {@code spring-bean} actions never instantiate arbitrary classes as a
     * fallback. Applications should expose narrow service interfaces through the resolver.
     *
     * @param name         component name declared in the flow definition
     * @param requiredType declared component type
     * @param <T>          component type
     * @return resolved component instance
     */
    public <T> T component(String name, Class<T> requiredType) {
        return componentResolver.resolve(name, requiredType);
    }

    public ScriptExecutor scriptExecutor(String name) {
        return scriptExecutors.getScriptExecutor(name);
    }

    public ProcessContextPropagator contextPropagator() {
        return contextPropagator;
    }

    /**
     * Evaluates one script program owned by the active Process runtime.
     */
    public Object evaluateScript(ScriptProgramSpec spec, Map<String, Object> context) {
        return scriptExecutors.evaluate(scriptProgram(spec), context);
    }

    /**
     * Makes one runtime-owned script-program catalog available to generated Process code for the
     * duration of its invocation. The execution context is propagated to action and parallel
     * workers, so the same immutable catalog follows that invocation across engine-owned threads.
     */
    public ScriptProgramsScope openScriptPrograms(Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        Map<ScriptProgramSpec, ScriptProgram> catalog =
                Map.copyOf(Objects.requireNonNull(scriptPrograms, "scriptPrograms"));
        ScriptProgramsScope scope = new ScriptProgramsScope(this, catalog);
        synchronized (scriptProgramScopes) {
            scriptProgramScopes.addLast(scope);
        }
        return scope;
    }

    private ScriptProgram scriptProgram(ScriptProgramSpec spec) {
        Objects.requireNonNull(spec, "spec");
        synchronized (scriptProgramScopes) {
            ScriptProgramsScope active = scriptProgramScopes.peekLast();
            if (active == null) {
                throw new IllegalStateException("No script program catalog is active for the current Process runtime");
            }
            ScriptProgram program = active.catalog.get(spec);
            if (program == null) {
                throw new IllegalArgumentException(
                        "No script program is available for language '" + spec.language() + "'");
            }
            return program;
        }
    }

    /**
     * Restorable binding of one runtime-owned script-program catalog.
     */
    public static final class ScriptProgramsScope implements AutoCloseable {
        private final EngineExecutionContext owner;
        private final Map<ScriptProgramSpec, ScriptProgram> catalog;
        private boolean closed;

        private ScriptProgramsScope(EngineExecutionContext owner, Map<ScriptProgramSpec, ScriptProgram> catalog) {
            this.owner = owner;
            this.catalog = catalog;
        }

        @Override
        public void close() {
            synchronized (owner.scriptProgramScopes) {
                if (closed) {
                    return;
                }
                if (owner.scriptProgramScopes.peekLast() != this) {
                    throw new IllegalStateException("Script program catalogs must close in invocation order");
                }
                owner.scriptProgramScopes.removeLast();
                closed = true;
            }
        }
    }

    // Execution metadata access
    public RetryPolicy retryPolicy(String name) {
        return retryPolicies.get(name);
    }

    public FailureHandler failureHandler(String name) {
        return failureHandlers.get(name);
    }

    public boolean isMdcPropagationEnabled() {
        return mdcPropagationEnabled;
    }

    public String traceId() {
        return traceId;
    }

    public String invocationId() {
        return invocationId;
    }

    /**
     * Allocates the next logical invocation ordinal for one action node.
     *
     * <p>Counters are node-local so unrelated parallel branch scheduling does
     * not change a node's idempotency keys.
     *
     * @param nodeId action node identifier
     * @return one-based invocation ordinal
     */
    public long nextActionInvocationOrdinal(String nodeId) {
        String key = Objects.requireNonNull(nodeId, "nodeId");
        return actionInvocationOrdinals
            .computeIfAbsent(key, ignored -> new AtomicLong())
            .incrementAndGet();
    }

    public String parentInvocationId() {
        return parentInvocationId;
    }

    public int callDepth() {
        return callDepth;
    }

    public String processCode() {
        return processCode;
    }

    public ProcessModelType modelType() {
        return modelType;
    }

    /**
     * Returns the digest of the exact process source selected for this execution.
     *
     * <p>The runtime provider records this value before generated process code is
     * invoked. A {@code null} value therefore means action execution has been
     * attempted outside the normal runtime-resolution pipeline.
     *
     * @return exact source digest, or {@code null} before runtime resolution
     */
    public String sourceDigest() {
        return boundExecution.sourceDigest();
    }

    /**
     * Returns the root-inclusive immutable synchronous process call path.
     *
     * @return ordered process codes from root to the current invocation
     */
    public List<String> processCallPath() {
        return processCallPath;
    }

    /**
     * Fails before process actions run when the configured call-depth budget is exceeded.
     */
    public void validateProcessCallDepth() {
        if (processCallPath.size() <= maxProcessCallDepth) {
            return;
        }
        throw new ProcessCallDepthException(processCallPath, maxProcessCallDepth);
    }

    public String namespace() {
        return namespace;
    }

    public long duration() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos(startedAtNanos, System.nanoTime()));
    }

    /**
     * Returns the exact version selected for the current invocation, when versioned.
     */
    public String processVersion() {
        return boundExecution.version();
    }

    /**
     * Executes the exact target resolved for one Process call site.
     *
     * @param callSiteId call-site identifier in the current Process
     * @param variables called-Process input variables
     * @return called-Process outcome
     */
    public ProcessResult<Map<String, Object>> callProcess(String callSiteId, Map<String, Object> variables) {
        String nodeId = Objects.requireNonNull(callSiteId, "callSiteId");
        Map<String, Object> input = Objects.requireNonNull(variables, "variables");
        return boundExecution.invoke(nodeId, input);
    }

    public void bindInvocation(ProcessCallGraph callGraph, ProcessCallGraph.ProcessNode processNode,
            AliasSelection aliasSelection) {
        ProcessCallGraph.ProcessNode target = Objects.requireNonNull(processNode, "processNode");
        ProcessRuntimeIdentity runtime = target.runtimeEntry().getRuntimeIdentity();
        ProcessRef.Version targetVersion = target.version();
        String version = targetVersion == null ? null : targetVersion.version();
        AdmittedAliasSelection admittedSelection = admittedSelection(aliasSelection);
        BoundExecution binding = new BoundExecution(version, runtime.getSourceDigest(), admittedSelection,
                new BoundProcessCalls(callGraph, target, processCallInvoker));
        if (boundExecution.bound()) {
            throw new IllegalStateException("Process invocation was already bound");
        }
        this.modelType = runtime.getModelType();
        this.boundExecution = binding;
    }

    private AdmittedAliasSelection admittedSelection(AliasSelection selection) {
        if (selection == null) {
            return null;
        }
        if (admittedAlias == null) {
            throw new IllegalStateException("Alias selection requires an admitted Alias");
        }
        return new AdmittedAliasSelection(selection.aliasRevision(), selection.target());
    }

    public ProcessExecution processExecution() {
        String version = boundExecution.version();
        return ProcessExecution
            .builder()
            .traceId(traceId)
            .invocationId(invocationId)
            .namespace(namespace)
            .processCode(processCode)
            .processVersion(version == null ? null : ProcessRef.version(namespace, processCode, version))
            .startedAt(startedAt)
            .completedAt(completionTime(startedAt, startedAtNanos, System.nanoTime()))
            .build();
    }

    /**
     * Returns operational attribution for the terminal lifecycle event.
     */
    public ProcessEvent.ExecutionAttribution executionAttribution() {
        return boundExecution.attribution(parentInvocationId, callDepth, modelType, admittedAlias);
    }

    private record BoundExecution(String version, String sourceDigest, AdmittedAliasSelection aliasSelection,
            ProcessCalls processCalls) {
        private static BoundExecution unbound(String version, String sourceDigest) {
            return new BoundExecution(version, sourceDigest, null, UNBOUND_PROCESS_CALLS);
        }

        private boolean bound() {
            return processCalls != UNBOUND_PROCESS_CALLS;
        }

        private ProcessResult<Map<String, Object>> invoke(String callSiteId, Map<String, Object> variables) {
            return processCalls.invoke(callSiteId, variables);
        }

        private ProcessEvent.ExecutionAttribution attribution(String parentInvocationId, int callDepth,
                ProcessModelType modelType, ProcessRef.Alias admittedAlias) {
            return new ProcessEvent.ExecutionAttribution(parentInvocationId, callDepth, modelType, sourceDigest,
                    admittedAlias, aliasSelection == null ? null : aliasSelection.revision(),
                    aliasSelection == null ? null : aliasSelection.target());
        }
    }

    private record BoundProcessCalls(ProcessCallGraph callGraph, ProcessCallGraph.ProcessNode caller,
            ProcessCallInvoker invoker) implements ProcessCalls {
        private BoundProcessCalls {
            callGraph = Objects.requireNonNull(callGraph, "callGraph");
            caller = Objects.requireNonNull(caller, "caller");
            invoker = Objects.requireNonNull(invoker, "invoker");
        }

        @Override
        public ProcessResult<Map<String, Object>> invoke(String callSiteId, Map<String, Object> variables) {
            ProcessCallGraph.BoundCall call = callGraph.bind(caller, callSiteId, variables);
            return invoker.invoke(callGraph, call.target(), call.input());
        }
    }

    @FunctionalInterface
    private interface ProcessCalls {
        ProcessResult<Map<String, Object>> invoke(String callSiteId, Map<String, Object> variables);
    }

    private record AdmittedAliasSelection(long revision, ProcessAliasTarget target) {
        private AdmittedAliasSelection {
            target = Objects.requireNonNull(target, "target");
        }
    }

    public boolean ownedBy(ProcessCallInvoker candidate) {
        return processCallInvoker == Objects.requireNonNull(candidate, "candidate");
    }

    public Duration actionTimeoutCancellationGracePeriod() {
        return actionTimeoutCancellationGracePeriod;
    }

    public Duration parallelCancellationGracePeriod() {
        return parallelCancellationGracePeriod;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "EngineExecutionContext{traceId='%s', processCode='%s', duration=%dms}",
                traceId, processCode, duration());
    }

    public static final class Builder {
        private String traceId;
        private String invocationId;
        private String namespace;
        private String processCode;
        private EngineExecutionContext parentContext;
        private int maxProcessCallDepth = 32;
        private ProcessRef.Alias admittedAlias;
        private ProcessModelType modelType;
        private Instant startedAt = Instant.now();
        private long startedAtNanos = System.nanoTime();
        private ProcessCallInvoker processCallInvoker;
        private ProcessEngineExecutors executors;
        private ProcessComponentResolver componentResolver;
        private ScriptExecutorRegistry scriptExecutors;
        private Map<String, RetryPolicy> retryPolicies = Map.of();
        private Map<String, FailureHandler> failureHandlers = Map.of();
        private boolean mdcPropagationEnabled;
        private ProcessContextPropagator contextPropagator = ProcessContextPropagator.none();
        private String processVersion;
        private String sourceDigest;

        private Builder() {
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder invocationId(String value) {
            this.invocationId = value;
            return this;
        }

        public Builder namespace(String value) {
            this.namespace = value;
            return this;
        }

        public Builder processCode(String processCode) {
            this.processCode = Objects.requireNonNull(processCode, "processCode cannot be null");
            return this;
        }

        public Builder parentContext(EngineExecutionContext context) {
            this.parentContext = context;
            return this;
        }

        public Builder maxProcessCallDepth(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxProcessCallDepth must be positive");
            }
            this.maxProcessCallDepth = value;
            return this;
        }

        public Builder admittedAlias(ProcessRef.Alias value) {
            this.admittedAlias = value;
            return this;
        }

        public Builder modelType(ProcessModelType value) {
            this.modelType = value;
            return this;
        }

        Builder executionStartedAt(Instant value, long monotonicNanos) {
            this.startedAt = Objects.requireNonNull(value, "startedAt cannot be null");
            this.startedAtNanos = monotonicNanos;
            return this;
        }

        public Builder processCallInvoker(ProcessCallInvoker value) {
            this.processCallInvoker = Objects.requireNonNull(value, "processCallInvoker");
            return this;
        }

        public Builder executors(ProcessEngineExecutors executors) {
            this.executors = Objects.requireNonNull(executors, "executors cannot be null");
            return this;
        }

        public Builder componentResolver(ProcessComponentResolver componentResolver) {
            this.componentResolver = Objects.requireNonNull(componentResolver, "componentResolver cannot be null");
            return this;
        }

        public Builder scriptExecutors(ScriptExecutorRegistry scriptExecutors) {
            this.scriptExecutors = Objects.requireNonNull(scriptExecutors, "scriptExecutors cannot be null");
            return this;
        }

        public Builder retryPolicies(Map<String, RetryPolicy> retryPolicies) {
            this.retryPolicies = Map.copyOf(Objects.requireNonNull(retryPolicies, "retryPolicies"));
            return this;
        }

        public Builder failureHandlers(Map<String, FailureHandler> failureHandlers) {
            this.failureHandlers = Map.copyOf(Objects.requireNonNull(failureHandlers, "failureHandlers"));
            return this;
        }

        public Builder mdcPropagationEnabled(boolean value) {
            this.mdcPropagationEnabled = value;
            return this;
        }

        public Builder contextPropagator(ProcessContextPropagator value) {
            this.contextPropagator = Objects.requireNonNull(value, "contextPropagator");
            return this;
        }

        public Builder sourceDigest(String value) {
            this.sourceDigest = value;
            return this;
        }

        public Builder processVersion(String value) {
            this.processVersion = value;
            return this;
        }

        public EngineExecutionContext build() {
            if (namespace == null) {
                throw new IllegalStateException("namespace is required");
            }
            if (processCode == null) {
                throw new IllegalStateException("processCode is required");
            }
            if (processCallInvoker == null) {
                throw new IllegalStateException("processCallInvoker is required");
            }
            if (executors == null) {
                throw new IllegalStateException("executors is required");
            }
            if (componentResolver == null) {
                throw new IllegalStateException("componentResolver is required");
            }
            if (scriptExecutors == null) {
                throw new IllegalStateException("scriptExecutors is required");
            }
            return new EngineExecutionContext(this);
        }
    }
}
