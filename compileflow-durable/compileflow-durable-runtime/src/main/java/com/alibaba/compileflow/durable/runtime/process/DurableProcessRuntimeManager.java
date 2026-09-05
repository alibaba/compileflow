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
package com.alibaba.compileflow.durable.runtime.process;

import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.codec.RunContinuationCodec;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibility;
import com.alibaba.compileflow.durable.runtime.machine.DurableModelEligibilityException;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableCatalogStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallContract;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.source.loader.DefaultProcessDefinitionLoader;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Owns exact stored Process semantics and their disposable node-local executable realization.
 *
 * @author yusu
 */
public final class DurableProcessRuntimeManager {
    private final DurableCatalogStore store;
    private final DurableProcessRuntimeCache runtimeCache;
    private final DurableProcessCompiler structureCompiler;
    private final DurableProcessCompiler planCompiler;
    private final DurableProgramCompiler programCompiler;
    private final ProcessModelType definitionModelType;
    private final DefaultProcessDefinitionLoader definitionLoader;
    private final DurableVersionDefinitionSource versionSource;
    private final ClassLoader classLoader;
    private final int maxProcessCallDepth;
    private final RunContinuationCodec continuations = new RunContinuationCodec();
    private final ConcurrentMap<ProcessModelType, ProcessSemanticCompiler<?>> semanticCompilers =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<DurableProcessRuntime>> loads = new ConcurrentHashMap<>();

    public DurableProcessRuntimeManager(DurableCatalogStore store, DurableProcessRuntimeCache runtimeCache,
            DurableProgramCompiler programCompiler, ProcessEngineConfig engineConfig,
            DurableVersionDefinitionSource versionSource) {
        this(store, runtimeCache, DurableProcessCompiler.structural(), new DurableProcessCompiler(), programCompiler,
                engineConfig, versionSource);
    }

    public DurableProcessRuntimeManager(DurableCatalogStore store, DurableProcessRuntimeCache runtimeCache,
            DurableProcessCompiler planCompiler, DurableProgramCompiler programCompiler,
            ProcessEngineConfig engineConfig, DurableVersionDefinitionSource versionSource) {
        this(store, runtimeCache, DurableProcessCompiler.structural(), planCompiler, programCompiler, engineConfig,
                versionSource);
    }

    private DurableProcessRuntimeManager(DurableCatalogStore store, DurableProcessRuntimeCache runtimeCache,
            DurableProcessCompiler structureCompiler, DurableProcessCompiler planCompiler,
            DurableProgramCompiler programCompiler, ProcessEngineConfig engineConfig,
            DurableVersionDefinitionSource versionSource) {
        ProcessEngineConfig config = Objects.requireNonNull(engineConfig, "engineConfig");
        this.store = Objects.requireNonNull(store, "store");
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.structureCompiler = Objects.requireNonNull(structureCompiler, "structureCompiler");
        this.planCompiler = Objects.requireNonNull(planCompiler, "planCompiler");
        this.programCompiler = Objects.requireNonNull(programCompiler, "programCompiler");
        this.definitionModelType = config.getModelType();
        this.definitionLoader = new DefaultProcessDefinitionLoader(config.getDefinitionConfig());
        this.versionSource = Objects.requireNonNull(versionSource, "versionSource");
        this.classLoader = config.getClassLoader();
        this.maxProcessCallDepth = config.getMaxCallDepth();
    }

    /**
     * Stores the exact semantics supplied by one direct definition source.
     */
    public DurableStore.RunProcess register(ProcessDefinition definition) {
        ProcessDefinition requested = Objects.requireNonNull(definition, "definition");
        return registerDefinition(ProcessRef.DEFAULT_NAMESPACE, definitionModelType, requested);
    }

    /**
     * Stores one exact versioned definition acquired for a Run admission.
     */
    public DurableStore.RunProcess register(ProcessRef.Version version) {
        ProcessRef.Version requested = Objects.requireNonNull(version, "version");
        return inClassLoaderScope(() -> {
            DurableVersionDefinitionSource.VersionDefinition versionDefinition = versionSource
                .find(requested)
                .orElseThrow(() -> DurableProcessException.of(DurableErrorCode.VERSION_NOT_FOUND,
                        "Versioned Durable Process definition was not found: " + requested));
            validateVersionBindings(requested, versionDefinition);
            DurableStore.StoredProcess stored =
                    register(requested.code(), versionDefinition.modelType(), versionDefinition.definition());
            return runProcess(stored, requested.namespace(), requested);
        });
    }

    public DurableProcessRuntime requireRuntime(UUID processId) {
        UUID requested = Objects.requireNonNull(processId, "processId");
        DurableProcessRuntime cached = runtimeCache.get(requested).orElse(null);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<DurableProcessRuntime> candidate = new CompletableFuture<>();
        CompletableFuture<DurableProcessRuntime> active = loads.putIfAbsent(requested, candidate);
        if (active != null) {
            return awaitRuntimeLoad(active);
        }
        try {
            DurableProcessRuntime loaded = inClassLoaderScope(() -> loadRuntimeUncached(requested));
            candidate.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error failure) {
            candidate.completeExceptionally(failure);
            throw failure;
        } finally {
            loads.remove(requested, candidate);
        }
    }

    private static DurableProcessRuntime awaitRuntimeLoad(CompletableFuture<DurableProcessRuntime> load) {
        try {
            return load.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Unexpected checked Durable Process runtime load failure", cause);
        }
    }

    public DurableValueSerializer requireValueSerializer(UUID processId) {
        return requireRuntime(processId).valueSerializer();
    }

    /**
     * Creates the initial continuation and resolves every exact Process reference it may enter.
     */
    public DurableStore.NewRun createNewRun(ProcessRunId runId, DurableStore.RunProcess registered,
            Map<String, Object> input, DurableStore.Envelope admissionFact) {
        DurableStore.RunProcess root = Objects.requireNonNull(registered, "registered");
        Map<RunContinuation.ProcessCallSite, UUID> processCallTargets = new LinkedHashMap<>();
        Map<UUID, DurableStore.RunProcess> processes = new LinkedHashMap<>();
        processes.put(root.processId(), root);
        resolveProcessCalls(root, processCallTargets, processes, new LinkedHashMap<>(), new LinkedHashSet<>(),
                new LinkedHashSet<>());
        validateProcessCallDepth(root, processCallTargets, processes);
        Set<UUID> recoveryProcessIds = new LinkedHashSet<>();
        recoveryProcessIds.add(root.processId());
        recoveryProcessIds.addAll(processCallTargets.values());
        byte[] continuation = continuations.encode(RunContinuation.start(root.processId(),
                        ContinuationSnapshot.start(input), processCallTargets), this::requireRuntime);
        return new DurableStore.NewRun(Objects.requireNonNull(runId, "runId"), root, recoveryProcessIds,
                new DurableStore.Envelope(continuation), admissionFact);
    }

    private void resolveProcessCalls(DurableStore.RunProcess caller,
            Map<RunContinuation.ProcessCallSite, UUID> processCallTargets, Map<UUID, DurableStore.RunProcess> processes,
            Map<CallTargetKey, DurableStore.RunProcess> targetsByKey, Set<UUID> visitedProcesses, Set<UUID> visiting) {
        UUID callerKey = caller.processId();
        if (visitedProcesses.contains(callerKey)) {
            return;
        }
        if (!visiting.add(callerKey)) {
            throw DurableProcessException.of(DurableErrorCode.INVALID_ARGUMENT,
                    "Durable Process dependency graph contains a cycle");
        }
        try {
            DurableProcessRuntime loaded = requireRuntime(caller.processId());
            for (ProcessSemanticPlan.NodePlan node : loaded.machinePlan().semanticPlan().getNodes().values()) {
                if (!(node.operation() instanceof ProcessCallPlan call)) {
                    continue;
                }
                if (processCallTargets.size() >= RunContinuation.MAX_PROCESS_CALL_TARGETS) {
                    throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                            "Durable Process dependency graph exceeds the call-site limit");
                }
                validateCallAuthority(caller, call);
                DurableStore.RunProcess calledProcess =
                        resolveProcessCall(caller, loaded.modelType(), call, targetsByKey);
                try {
                    ProcessCallContract.validate(node.id(), call,
                            requireRuntime(calledProcess.processId()).machinePlan().semanticPlan(), classLoader);
                } catch (IllegalArgumentException failure) {
                    throw DurableProcessException.of(DurableErrorCode.INVALID_ARGUMENT, failure.getMessage(), failure);
                }
                RunContinuation.ProcessCallSite site =
                        new RunContinuation.ProcessCallSite(caller.processId(), node.id());
                processCallTargets.put(site, calledProcess.processId());
                processes.putIfAbsent(calledProcess.processId(), calledProcess);
                resolveProcessCalls(calledProcess, processCallTargets, processes, targetsByKey, visitedProcesses,
                        visiting);
            }
            visitedProcesses.add(callerKey);
        } finally {
            visiting.remove(callerKey);
        }
    }

    private void validateProcessCallDepth(DurableStore.RunProcess root,
            Map<RunContinuation.ProcessCallSite, UUID> processCallTargets,
            Map<UUID, DurableStore.RunProcess> processes) {
        Map<UUID, List<UUID>> targetsByCaller = new LinkedHashMap<>();
        Map<UUID, Integer> incoming = new LinkedHashMap<>();
        for (Map.Entry<RunContinuation.ProcessCallSite, UUID> entry : processCallTargets.entrySet()) {
            UUID callerId = entry.getKey().callerProcessId();
            UUID targetId = entry.getValue();
            targetsByCaller
                .computeIfAbsent(callerId, ignored -> new java.util.ArrayList<>())
                .add(targetId);
            incoming.merge(targetId, 1, Integer::sum);
        }

        Map<UUID, Integer> depths = new LinkedHashMap<>();
        Map<UUID, List<String>> paths = new LinkedHashMap<>();
        depths.put(root.processId(), 1);
        paths.put(root.processId(), List.of(root.processCode()));
        java.util.ArrayDeque<UUID> ready = new java.util.ArrayDeque<>();
        for (UUID processId : processes.keySet()) {
            if (!incoming.containsKey(processId)) {
                ready.addLast(processId);
            }
        }
        while (!ready.isEmpty()) {
            UUID callerId = ready.removeFirst();
            Integer callerDepth = depths.get(callerId);
            if (callerDepth == null) {
                continue;
            }
            List<String> callerPath = paths.get(callerId);
            for (UUID targetId : targetsByCaller.getOrDefault(callerId, List.of())) {
                DurableStore.RunProcess target = processes.get(targetId);
                int targetDepth = callerDepth + 1;
                List<String> targetPath = new java.util.ArrayList<>(callerPath);
                targetPath.add(target.processCode());
                if (targetDepth > depths.getOrDefault(targetId, 0)) {
                    depths.put(targetId, targetDepth);
                    paths.put(targetId, targetPath);
                    if (targetDepth > maxProcessCallDepth) {
                        throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                                "Durable Process call depth exceeds the configured maximum: " + String.join(" -> ",
                                        targetPath));
                    }
                }
                int remainingIncoming = incoming.merge(targetId, -1, Integer::sum);
                if (remainingIncoming == 0) {
                    ready.addLast(targetId);
                }
            }
        }
    }

    private DurableStore.RunProcess resolveProcessCall(DurableStore.RunProcess caller, ProcessModelType callerModelType,
            ProcessCallPlan call, Map<CallTargetKey, DurableStore.RunProcess> targetsByKey) {
        CallTargetKey key = new CallTargetKey(caller.namespace(), call.code(), call.target(), callerModelType);
        if (!targetsByKey.containsKey(key) && targetsByKey.size() >= RunContinuation.MAX_PROCESS_CALL_TARGETS) {
            throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                    "Durable Process dependency graph exceeds the target limit");
        }
        return targetsByKey.computeIfAbsent(key, ignored -> registerProcessCallTarget(caller, callerModelType, call));
    }

    private static void validateCallAuthority(DurableStore.RunProcess caller, ProcessCallPlan call) {
        if (caller.processVersion() != null && call.target() instanceof ProcessCallTarget.Classpath) {
            throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                    "An exact Process Version may contain only versioned Process calls: caller=" + caller.processCode()
                    + ", target=" + call.code());
        }
    }

    private void validateVersionBindings(ProcessRef.Version requested,
            DurableVersionDefinitionSource.VersionDefinition versionDefinition) {
        ProcessSemanticCompilation parsed = parse(requested.code(), versionDefinition.modelType(),
                versionDefinition.definition().content().getBytes(StandardCharsets.UTF_8));
        Map<String, ProcessRef.Version> actual = new LinkedHashMap<>();
        for (ProcessSemanticPlan.NodePlan node : parsed.semanticPlan().getNodes().values()) {
            if (!(node.operation() instanceof ProcessCallPlan call)) {
                continue;
            }
            if (!(call.target() instanceof ProcessCallTarget.Version version)) {
                throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                        "A published Process Version may contain only exact versioned Process calls: " + node.id());
            }
            actual.put(node.id(), ProcessRef.version(requested.namespace(), call.code(), version.version()));
        }
        for (ProcessRef.Version target : versionDefinition.callBindings().values()) {
            if (!requested.namespace().equals(target.namespace())) {
                throw DurableProcessException.of(DurableErrorCode.ARTIFACT_DIGEST_MISMATCH,
                        "Published Process call binding must use the caller namespace");
            }
        }
        if (!actual.equals(versionDefinition.callBindings())) {
            throw DurableProcessException.of(DurableErrorCode.ARTIFACT_DIGEST_MISMATCH,
                    "Published Process call bindings do not match its exact definition");
        }
    }

    private DurableStore.RunProcess registerProcessCallTarget(DurableStore.RunProcess caller,
            ProcessModelType callerModelType, ProcessCallPlan call) {
        if (call.target() instanceof ProcessCallTarget.Classpath classpath) {
            return registerDefinition(caller.namespace(), callerModelType,
                    ProcessDefinition.classpath(call.code(), classpath.resourcePath()));
        }
        if (call.target() instanceof ProcessCallTarget.Version version) {
            return register(ProcessRef.version(caller.namespace(), call.code(), version.version()));
        }
        throw new IllegalStateException("Unsupported Process call target: " + call.target().getClass().getName());
    }

    private DurableStore.RunProcess registerDefinition(String namespace, ProcessModelType modelType,
            ProcessDefinition definition) {
        return inClassLoaderScope(() -> {
            ProcessDefinitionSnapshot source = loadDefinition(definition, modelType);
            DurableStore.StoredProcess stored = register(source.getCode(), modelType, source.getBytes());
            return runProcess(stored, namespace, null);
        });
    }

    private ProcessDefinitionSnapshot loadDefinition(ProcessDefinition definition, ProcessModelType modelType) {
        try {
            return definitionLoader.load(ProcessRuntimeRequest.from(definition), modelType, classLoader);
        } catch (CompileFlowException failure) {
            throw DurableProcessException.of(DurableErrorCode.INVALID_ARGUMENT,
                    "Durable Process definition could not be loaded", failure);
        }
    }

    private DurableStore.StoredProcess register(String processCode, ProcessModelType modelType,
            ProcessDefinition.Inline definition) {
        ProcessDefinition.Inline source = Objects.requireNonNull(definition, "definition");
        if (!processCode.equals(source.code())) {
            throw new IllegalStateException("Definition source returned a different Process code");
        }
        byte[] bytes = source.content().getBytes(StandardCharsets.UTF_8);
        return register(processCode, modelType, bytes);
    }

    private DurableStore.StoredProcess register(String processCode, ProcessModelType modelType, byte[] bytes) {
        if (bytes.length > DurableStore.MAX_DEFINITION_BYTES) {
            throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                    "Process definition exceeds the Durable size limit of " + DurableStore.MAX_DEFINITION_BYTES + " bytes");
        }
        ProcessSemanticCompilation parsed = parse(processCode, modelType, bytes);
        validateProcess(processCode, parsed);
        String definitionDigest = ProcessDefinitionDigest.compute(modelType, processCode, bytes);
        UUID processId = DurableProcessIdentity.fromDefinitionDigest(definitionDigest);
        DurableStore.ProcessRegistration registration =
                new DurableStore.ProcessRegistration(processId, processCode, modelType, bytes, definitionDigest);
        DurableStore.StoredProcess stored =
                Objects.requireNonNull(store.registerProcess(registration), "Store returned null Process registration");
        requireRegistration(registration, stored);
        return stored;
    }

    private DurableProcessRuntime loadRuntimeUncached(UUID processId) {
        DurableStore.StoredProcess stored = store
            .findProcess(processId)
            .orElseThrow(() -> DurableProcessException.of(DurableErrorCode.INTERNAL_ERROR,
                    "Stored Durable Process was not found"));
        if (!processId.equals(stored.processId())) {
            throw new IllegalStateException("Store returned a different Process ID");
        }
        ProcessSemanticCompilation parsed = parse(stored.processCode(), stored.modelType(), stored.definitionBytes());
        validateProcess(stored.processCode(), parsed);
        DurableMachinePlan machinePlan;
        try {
            machinePlan = planCompiler.lower(parsed.semanticPlan(), parsed.structuredPlan());
        } catch (DurableModelEligibilityException failure) {
            throw unsupported(failure.getEligibility().problems().get(0));
        }
        Map<ScriptProgramSpec, ScriptProgram> scripts = planCompiler.compileScripts(machinePlan);
        return runtimeCache.put(
                new DurableProcessRuntime(stored.processId(), stored.processCode(), stored.modelType(),
                        stored.definitionDigest(), machinePlan, programCompiler.compile(machinePlan, classLoader),
                        new DurableValueSerializer(machinePlan, DurableValueSerializer.Limits.defaults(), classLoader),
                        scripts));
    }

    private void validateProcess(String processCode, ProcessSemanticCompilation compilation) {
        ProcessSemanticPlan semanticPlan = compilation.semanticPlan();
        if (!processCode.equals(semanticPlan.getProcessCode())) {
            throw DurableProcessException.of(DurableErrorCode.PROCESS_IDENTITY_MISMATCH,
                    "Parsed Process code does not match stored Process identity");
        }
        DurableModelEligibility eligibility = structureCompiler.check(semanticPlan, compilation.structuredPlan());
        if (!eligibility.isEligible()) {
            throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                    bounded(eligibility.problems().get(0).message()));
        }
    }

    private ProcessSemanticCompilation parse(String processCode, ProcessModelType modelType, byte[] bytes) {
        ProcessDefinitionSnapshot definition;
        try {
            definition = ProcessDefinitionSnapshot.of(ProcessRef.DEFAULT_NAMESPACE, processCode, null, bytes,
                    "Durable stored Process");
        } catch (IllegalArgumentException failure) {
            throw DurableProcessException.of(DurableErrorCode.INVALID_ARGUMENT,
                    "Process definition must be exact valid UTF-8", failure);
        }
        try {
            ProcessSemanticCompiler<?> compiler =
                    semanticCompilers.computeIfAbsent(modelType, type -> ProcessSemanticCompiler.discover(type,
                    classLoader));
            return compiler.compile(definition);
        } catch (DurableProcessException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw DurableProcessException.of(DurableErrorCode.UNSUPPORTED_PROCESS,
                    "Process definition cannot be compiled to shared semantics for model type " + modelType, failure);
        }
    }

    private <T> T inClassLoaderScope(Supplier<T> operation) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            return operation.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static DurableStore.RunProcess runProcess(DurableStore.StoredProcess stored, String namespace,
            ProcessRef.Version version) {
        return new DurableStore.RunProcess(stored.processId(), namespace, stored.processCode(), version);
    }

    private static void requireRegistration(DurableStore.ProcessRegistration requested,
            DurableStore.StoredProcess stored) {
        if (!requested.processId().equals(stored.processId()) || !requested.processCode().equals(stored.processCode())
                || requested.modelType() != stored.modelType()
                || !requested.definitionDigest().equals(stored.definitionDigest())) {
            throw new IllegalStateException("Store returned a different Process registration");
        }
    }

    private static DurableProcessException unsupported(DurableModelEligibility.Problem problem) {
        DurableProcessException.Builder failure = DurableProcessException
            .builder(DurableErrorCode.UNSUPPORTED_PROCESS, bounded(problem.message()))
            .context("problemCode", problem.code());
        return failure.build();
    }

    private static String bounded(String value) {
        if (value == null) {
            return "Process is not ready under the current deployment";
        }
        return ProcessText.truncateCodePoints(value, 2_048);
    }

    private record CallTargetKey(String namespace, String processCode, ProcessCallTarget target,
            ProcessModelType callerModelType) {}
}
