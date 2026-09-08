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
package com.alibaba.compileflow.engine.core.runtime.loading;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.source.loader.ProcessDefinitionLoader;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache.InstallResult;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache.Installation;
import com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys;
import com.alibaba.compileflow.engine.core.runtime.concurrent.FutureTimeouts;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default runtime loader with inflight deduplication and caching.
 *
 * @author yusu
 */
public final class DefaultProcessRuntimeLoader implements ProcessRuntimeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessRuntimeLoader.class);
    private final long waitTimeoutMillis;
    private final ProcessRuntimeCache runtimeCache;
    private final ProcessDefinitionLoader definitionLoader;
    private final Function<ProcessDefinitionSnapshot, ProcessSemanticCompilation> semanticCompiler;
    private final ProcessRuntimeFactory runtimeFactory;
    private final ProcessRuntimeIdentity.PipelineIdentity pipelineIdentity =
            ProcessRuntimeIdentity.newPipelineIdentity();
    private final ExecutorService runtimeLoadExecutor;
    private final InflightRuntimeLoadRegistry inflightRuntimeLoadRegistry = new InflightRuntimeLoadRegistry();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cacheLifecycleMonitor = new Object();

    public DefaultProcessRuntimeLoader(ProcessRuntimeCache runtimeCache, ProcessDefinitionLoader definitionLoader,
            Function<ProcessDefinitionSnapshot, ProcessSemanticCompilation> semanticCompiler,
            ProcessRuntimeFactory runtimeFactory, ExecutorService runtimeLoadExecutor, Duration runtimeLoadTimeout) {
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.definitionLoader = Objects.requireNonNull(definitionLoader, "definitionLoader");
        this.semanticCompiler = Objects.requireNonNull(semanticCompiler, "semanticCompiler");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.runtimeLoadExecutor = Objects.requireNonNull(runtimeLoadExecutor, "runtimeLoadExecutor");
        this.waitTimeoutMillis = Objects.requireNonNull(runtimeLoadTimeout, "runtimeLoadTimeout").toMillis();
    }

    private static ProcessRuntimeEntry requireMatchingRuntime(ProcessDefinitionSnapshot source,
            ProcessRuntimeIdentity expected, ProcessRuntimeEntry actual) {
        if (actual != null && actual.matches(expected)) {
            return actual;
        }
        throw new CompileFlowException(ErrorCode.CF_COMPILE_001,
                "Runtime loading returned a runtime for a different identity", null)
            .withContext("processCode", source.getCode())
            .withContext("expectedDigest", expected.getDigest())
            .withContext("actualDigest", actual == null ? null : actual.getDigest());
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static CompileFlowException.ValidationException versionConflict(ProcessDefinitionSnapshot source,
            ProcessRuntimeEntry requested) {
        return versionConflict(source, requested.getDigest());
    }

    private static CompileFlowException.ValidationException versionConflict(ProcessDefinitionSnapshot source,
            String requestedDigest) {
        CompileFlowException.ValidationException conflict = new CompileFlowException.ValidationException("version",
                "Process version is already bound to different content: namespace=" + source.getNamespace() + " code="
                + source.getCode() + " version=" + source.getVersion());
        conflict.withContext("requestedDigest", requestedDigest);
        return conflict;
    }

    private static void requireVersioned(ProcessDefinitionSnapshot source) {
        if (source.getVersion() == null) {
            throw new IllegalArgumentException("A runtime binding requires an exact version");
        }
    }

    private static void requireUnversioned(ProcessDefinitionSnapshot source) {
        if (source.getVersion() != null) {
            throw new IllegalArgumentException("Exact cache warm-up does not create a version binding");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String failureCode(Throwable failure) {
        return failure instanceof CompileFlowException compileFlowException
                ? compileFlowException.getErrorCode().getCode()
                : ErrorCode.CF_COMPILE_001.getCode();
    }

    private static String failureType(Throwable failure) {
        return failure.getClass().getName();
    }

    private static String bindingKey(ProcessDefinitionSnapshot definition) {
        return RuntimeCacheKeys.forProcess(definition.getNamespace(), definition.getCode(), definition.getVersion());
    }

    @Override
    public CompletableFuture<ProcessRuntimeEntry> loadAsync(ProcessRuntimeRequest request, ClassLoader classLoader,
            ProcessRuntimeEntry expectedEntry) {
        ensureOpen();
        ProcessDefinitionSnapshot source = resolveSource(request, classLoader);
        requireVersioned(source);
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
        ProcessRuntimeEntry cached = findBoundRuntime(source, runtimeIdentity);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loadAndInstall(source, classLoader, runtimeIdentity, expectedEntry);
    }

    @Override
    public ProcessRuntimeEntry loadSync(ProcessRuntimeRequest request, ClassLoader classLoader) {
        ensureOpen();
        ProcessDefinitionSnapshot source = resolveSource(request, classLoader);
        requireVersioned(source);
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
        ProcessRuntimeEntry cached = findBoundRuntime(source, runtimeIdentity);
        if (cached != null) {
            return cached;
        }
        ProcessRuntimeEntry expectedEntry = runtimeCache.getIfPresent(bindingKey(source));
        CompletableFuture<ProcessRuntimeEntry> future =
                loadAndInstall(source, classLoader, runtimeIdentity, expectedEntry);
        return awaitRuntimeLoad(source, runtimeIdentity, future);
    }

    @Override
    public ProcessRuntimeEntry loadExactSync(ProcessRuntimeRequest request, ClassLoader classLoader) {
        ensureOpen();
        ProcessDefinitionSnapshot source = resolveSource(request, classLoader);
        requireUnversioned(source);
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
        ProcessRuntimeEntry cached = runtimeCache.getIfPresent(runtimeIdentity);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<ProcessRuntimeEntry> future = getOrStartRuntimeLoad(source, classLoader, runtimeIdentity);
        ProcessRuntimeEntry compiled = awaitRuntimeLoad(source, runtimeIdentity, future);
        return cacheExactIfOpen(compiled);
    }

    @Override
    public ProcessDefinitionSnapshot resolve(ProcessRuntimeRequest request, ClassLoader classLoader) {
        ensureOpen();
        return resolveSource(request, classLoader);
    }

    @Override
    public ProcessRuntimeEntry runtimeCheckSync(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
        ensureOpen();
        ProcessDefinitionSnapshot source = Objects.requireNonNull(definition, "definition");
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
        ProcessRuntimeEntry cached = runtimeCache.getIfPresent(runtimeIdentity);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<ProcessRuntimeEntry> future = getOrStartRuntimeLoad(source, classLoader, runtimeIdentity);
        return awaitRuntimeLoad(source, runtimeIdentity, future);
    }

    @Override
    public void loadBatch(String ownerId, ClassLoader classLoader, ProcessRuntimeRequest... requests) {
        String owner = SemanticText.requireIdentity(ownerId, "ownerId");
        ensureOpen();
        List<PendingRuntimeLoad> pending = startLoadBatch(classLoader, requests);
        awaitBatch(owner, pending);
    }

    @Override
    public void loadExactBatch(ClassLoader classLoader, ProcessRuntimeRequest... requests) {
        ensureOpen();
        List<PendingExactRuntimeLoad> pending = startExactLoadBatch(classLoader, requests);
        awaitExactLoadBatch(pending);
    }

    @Override
    public void close() {
        synchronized (cacheLifecycleMonitor) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
        }
        inflightRuntimeLoadRegistry.close();
    }

    private CompletableFuture<ProcessRuntimeEntry> loadAndInstall(ProcessDefinitionSnapshot source,
            ClassLoader classLoader, ProcessRuntimeIdentity runtimeIdentity, ProcessRuntimeEntry expectedEntry) {
        String codeKey = bindingKey(source);
        return getOrStartRuntimeLoad(source, classLoader, runtimeIdentity).thenApply(entry -> {
            synchronized (cacheLifecycleMonitor) {
                if (closed.get()) {
                    LOGGER.debug("Built runtime was not installed because the runtime loader is closed");
                    return entry;
                }
                InstallResult result = runtimeCache.install(codeKey, expectedEntry, entry);
                if (result == InstallResult.VERSION_CONFLICT) {
                    throw versionConflict(source, entry);
                }
                return entry;
            }
        });
    }

    private CompletableFuture<ProcessRuntimeEntry> getOrStartRuntimeLoad(ProcessDefinitionSnapshot source,
            ClassLoader classLoader, ProcessRuntimeIdentity runtimeIdentity) {
        CompletableFuture<ProcessRuntimeEntry> proposed = new CompletableFuture<>();
        CompletableFuture<ProcessRuntimeEntry> active =
                inflightRuntimeLoadRegistry.putIfAbsent(runtimeIdentity, proposed);
        if (active != proposed) {
            return active;
        }

        Runnable runtimeLoadTask = () -> runRuntimeLoad(source, classLoader, runtimeIdentity, proposed);

        Future<?> submittedTask;
        try {
            submittedTask = runtimeLoadExecutor.submit(runtimeLoadTask);
        } catch (RejectedExecutionException rejected) {
            LOGGER.error("Runtime-load task rejected: failureType={}", rejected.getClass().getName());
            inflightRuntimeLoadRegistry.remove(runtimeIdentity, proposed);
            proposed.completeExceptionally(rejected);
            return proposed;
        }

        proposed.whenComplete((entry, failure) -> {
            if (proposed.isCancelled() && !submittedTask.isDone()) {
                submittedTask.cancel(true);
            }
            if (failure != null) {
                Throwable cause = unwrap(failure);
                if (cause instanceof CancellationException) {
                    LOGGER.debug("Runtime load cancelled");
                } else {
                    LOGGER.warn("Runtime load finished with failure: errorCode={}, failureType={}", failureCode(cause),
                            failureType(cause));
                }
            } else {
                LOGGER.debug("Runtime load finished successfully");
            }
        });
        return proposed;
    }

    private void runRuntimeLoad(ProcessDefinitionSnapshot source, ClassLoader classLoader,
            ProcessRuntimeIdentity runtimeIdentity, CompletableFuture<ProcessRuntimeEntry> proposed) {
        try {
            ProcessRuntimeEntry cached = runtimeCache.getIfPresent(runtimeIdentity);
            proposed.complete(cached != null ? cached : buildRuntime(source, runtimeIdentity, classLoader));
        } catch (Throwable failure) {
            // Remove the failed owner before waking waiters so an immediate retry can become owner.
            inflightRuntimeLoadRegistry.remove(runtimeIdentity, proposed);
            proposed.completeExceptionally(failure);
            return;
        }
        inflightRuntimeLoadRegistry.remove(runtimeIdentity, proposed);
    }

    private ProcessRuntimeEntry buildRuntime(ProcessDefinitionSnapshot source, ProcessRuntimeIdentity runtimeIdentity,
            ClassLoader classLoader) {
        long startedAtNanos = System.nanoTime();
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Runtime load interrupted before execution",
                        new InterruptedException())
                    .withContext("operation", "LOAD")
                    .withContext("cause", "INTERRUPTED")
                    .withContext("processCode", source.getCode())
                    .withContext("durationMs", elapsedMillis(startedAtNanos));
            }
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoader);
                ProcessSemanticCompilation compilation = semanticCompiler.apply(source);
                ProcessRuntime runtime = runtimeFactory.createRuntime(compilation, classLoader);
                return new ProcessRuntimeEntry(runtime, runtimeIdentity);
            } finally {
                thread.setContextClassLoader(previous);
            }
        } catch (RuntimeException failure) {
            long durationMs = elapsedMillis(startedAtNanos);
            if (failure instanceof CompileFlowException compileFlowException) {
                throw compileFlowException;
            }
            throw new CompileFlowException(ErrorCode.CF_COMPILE_001,
                    "Runtime load failed for code '" + source.getCode() + "'", failure)
                .withContext("processCode", source.getCode())
                .withContext("digest", runtimeIdentity.getDigest())
                .withContext("totalDuration", durationMs);
        }
    }

    private List<PendingRuntimeLoad> startLoadBatch(ClassLoader classLoader, ProcessRuntimeRequest... requests) {
        Objects.requireNonNull(requests, "requests");
        List<BatchPlan> plans = new ArrayList<>(requests.length);
        Set<String> bindingKeys = new HashSet<>();
        for (ProcessRuntimeRequest request : requests) {
            ProcessDefinitionSnapshot source = resolveSource(request, classLoader);
            requireVersioned(source);
            String bindingKey = bindingKey(source);
            if (!bindingKeys.add(bindingKey)) {
                throw new IllegalArgumentException("Batch contains duplicate process binding: " + bindingKey);
            }
            ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
            if (source.getVersion() != null && runtimeCache.conflictsWithImmutableBinding(bindingKey, runtimeIdentity)) {
                throw versionConflict(source, runtimeIdentity.getDigest());
            }
            plans.add(new BatchPlan(source, bindingKey, runtimeIdentity, runtimeCache.getIfPresent(bindingKey)));
        }

        List<PendingRuntimeLoad> pending = new ArrayList<>(plans.size());
        for (BatchPlan plan : plans) {
            ProcessRuntimeEntry cached = plan.expectedEntry();
            if (cached == null || !cached.matches(plan.runtimeIdentity())) {
                cached = runtimeCache.getIfPresent(plan.runtimeIdentity());
            }
            CompletableFuture<ProcessRuntimeEntry> future = cached == null
                    ? getOrStartRuntimeLoad(plan.source(), classLoader, plan.runtimeIdentity())
                    : CompletableFuture.completedFuture(cached);
            pending.add(
                    new PendingRuntimeLoad(plan.source(), plan.bindingKey(), plan.runtimeIdentity(),
                            plan.expectedEntry(), future));
        }
        return pending;
    }

    private void awaitBatch(String ownerId, List<PendingRuntimeLoad> pending) {
        List<CompletableFuture<ProcessRuntimeEntry>> futures = pending
            .stream()
            .map(PendingRuntimeLoad::future)
            .toList();
        try {
            FutureTimeouts.awaitAllWithoutCancellation(futures, waitTimeoutMillis);
        } catch (TimeoutException timeout) {
            long unfinished = futures
                .stream()
                .filter(future -> !future.isDone())
                .count();
            LOGGER.warn("Timed out waiting for batch runtime load: waitTimeoutMs={}, unfinished={}, total={}",
                    waitTimeoutMillis, unfinished, futures.size());
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Timed out waiting for batch runtime load", timeout)
                .withContext("operation", "LOAD")
                .withContext("cause", "TIMEOUT")
                .withContext("timeoutMs", waitTimeoutMillis)
                .withContext("batchSize", futures.size())
                .withContext("unfinished", unfinished);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Batch runtime load interrupted", interrupted)
                .withContext("operation", "LOAD")
                .withContext("cause", "INTERRUPTED")
                .withContext("batchSize", futures.size());
        } catch (CancellationException cancelled) {
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Batch runtime load cancelled", cancelled)
                .withContext("operation", "LOAD")
                .withContext("cause", "CANCELLED")
                .withContext("batchSize", futures.size());
        } catch (ExecutionException failed) {
            LOGGER.debug("At least one runtime load failed during batch execution: failureType={}",
                    failureType(unwrap(failed)));
        }

        List<RuntimeLoadFailure> failures = new ArrayList<>();
        List<Installation> installations = new ArrayList<>(pending.size());
        for (PendingRuntimeLoad item : pending) {
            try {
                ProcessRuntimeEntry entry = item.future().join();
                ProcessRuntimeEntry matching = requireMatchingRuntime(item.source(), item.runtimeIdentity(), entry);
                installations.add(new Installation(item.bindingKey(), item.expectedEntry(), matching));
            } catch (CompletionException | CancellationException failure) {
                Throwable cause = unwrap(failure);
                if (cause instanceof Error error) {
                    throw error;
                }
                failures.add(new RuntimeLoadFailure(item.source().getCode(), cause));
            } catch (RuntimeException failure) {
                failures.add(new RuntimeLoadFailure(item.source().getCode(), failure));
            }
        }

        if (!failures.isEmpty()) {
            String failedCodes = failures
                .stream()
                .map(failure -> failure.code() == null ? "<unknown>" : failure.code())
                .collect(Collectors.joining(", "));
            CompileFlowException aggregate = new CompileFlowException(ErrorCode.CF_COMPILE_001,
                    "Batch runtime load failed for codes: [" + failedCodes + "]", null);
            failures.forEach(failure -> aggregate.addSuppressed(failure.cause()));
            throw aggregate;
        }

        synchronized (cacheLifecycleMonitor) {
            ensureOpen();
            InstallResult result = runtimeCache.installAndRetainBatch(installations, ownerId);
            if (result == InstallResult.VERSION_CONFLICT) {
                throw new CompileFlowException.ValidationException("version",
                        "A process version was bound to different content while the batch was compiling");
            }
        }
    }

    private List<PendingExactRuntimeLoad> startExactLoadBatch(ClassLoader classLoader,
            ProcessRuntimeRequest... requests) {
        Objects.requireNonNull(requests, "requests");
        List<PendingExactRuntimeLoad> pending = new ArrayList<>(requests.length);
        Set<ProcessRuntimeIdentity> requestedIdentities = new HashSet<>();
        for (int index = 0; index < requests.length; index++) {
            ProcessRuntimeRequest request = Objects.requireNonNull(requests[index], "requests[" + index + "]");
            ProcessDefinitionSnapshot source = resolveSource(request, classLoader);
            requireUnversioned(source);
            ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, classLoader);
            if (!requestedIdentities.add(runtimeIdentity)) {
                continue;
            }
            ProcessRuntimeEntry cached = runtimeCache.getIfPresent(runtimeIdentity);
            CompletableFuture<ProcessRuntimeEntry> future = cached == null
                    ? getOrStartRuntimeLoad(source, classLoader, runtimeIdentity)
                    : CompletableFuture.completedFuture(cached);
            pending.add(new PendingExactRuntimeLoad(source, runtimeIdentity, future));
        }
        return pending;
    }

    private void awaitExactLoadBatch(List<PendingExactRuntimeLoad> pending) {
        List<CompletableFuture<ProcessRuntimeEntry>> futures =
                pending.stream().map(PendingExactRuntimeLoad::future).toList();
        try {
            FutureTimeouts.awaitAllWithoutCancellation(futures, waitTimeoutMillis);
        } catch (TimeoutException timeout) {
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Timed out waiting for runtime warm-up", timeout)
                .withContext("operation", "WARM_UP")
                .withContext("cause", "TIMEOUT")
                .withContext("timeoutMs", waitTimeoutMillis)
                .withContext("batchSize", futures.size());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Runtime warm-up interrupted", interrupted)
                .withContext("operation", "WARM_UP")
                .withContext("cause", "INTERRUPTED")
                .withContext("batchSize", futures.size());
        } catch (CancellationException cancelled) {
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Runtime warm-up cancelled", cancelled)
                .withContext("operation", "WARM_UP")
                .withContext("cause", "CANCELLED")
                .withContext("batchSize", futures.size());
        } catch (ExecutionException failed) {
            LOGGER.debug("At least one runtime warm-up failed: failureType={}", failureType(unwrap(failed)));
        }

        List<RuntimeLoadFailure> failures = new ArrayList<>();
        List<ProcessRuntimeEntry> successful = new ArrayList<>(pending.size());
        for (PendingExactRuntimeLoad item : pending) {
            try {
                ProcessRuntimeEntry entry =
                        requireMatchingRuntime(item.source(), item.runtimeIdentity(), item.future().join());
                successful.add(entry);
            } catch (CompletionException | CancellationException failure) {
                Throwable cause = unwrap(failure);
                if (cause instanceof Error error) {
                    throw error;
                }
                failures.add(new RuntimeLoadFailure(item.source().getCode(), cause));
            } catch (RuntimeException failure) {
                failures.add(new RuntimeLoadFailure(item.source().getCode(), failure));
            }
        }
        cacheExactIfOpen(successful);
        if (!failures.isEmpty()) {
            String failedCodes = failures
                .stream()
                .map(failure -> failure.code() == null ? "<unknown>" : failure.code())
                .collect(Collectors.joining(", "));
            CompileFlowException aggregate = new CompileFlowException(ErrorCode.CF_COMPILE_001,
                    "Runtime warm-up failed for codes: [" + failedCodes + "]", null);
            failures.forEach(failure -> aggregate.addSuppressed(failure.cause()));
            throw aggregate;
        }
    }

    private ProcessRuntimeEntry cacheExactIfOpen(ProcessRuntimeEntry runtime) {
        synchronized (cacheLifecycleMonitor) {
            if (closed.get()) {
                return runtime;
            }
            return runtimeCache.cacheExact(runtime);
        }
    }

    private void cacheExactIfOpen(List<ProcessRuntimeEntry> successful) {
        synchronized (cacheLifecycleMonitor) {
            if (closed.get()) {
                return;
            }
            successful.forEach(runtimeCache::cacheExact);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Runtime loader is closed");
        }
    }

    private ProcessRuntimeEntry awaitRuntimeLoad(ProcessDefinitionSnapshot source,
            ProcessRuntimeIdentity runtimeIdentity, CompletableFuture<ProcessRuntimeEntry> future) {
        try {
            ProcessRuntimeEntry entry = FutureTimeouts.getWithoutCancellation(future, waitTimeoutMillis);
            return requireMatchingRuntime(source, runtimeIdentity, entry);
        } catch (TimeoutException timeout) {
            LOGGER.warn("Timed out waiting for runtime load: waitTimeoutMs={}", waitTimeoutMillis);
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001,
                    "Timed out waiting for runtime load of code '" + source.getCode() + "'", timeout)
                .withContext("operation", "LOAD")
                .withContext("cause", "TIMEOUT")
                .withContext("processCode", source.getCode())
                .withContext("timeoutMs", waitTimeoutMillis);
        } catch (ExecutionException failed) {
            Throwable cause = unwrap(failed);
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof CompileFlowException compileFlowException) {
                throw compileFlowException;
            }
            throw new CompileFlowException(ErrorCode.CF_COMPILE_001,
                    "Runtime load failed for code '" + source.getCode() + "'", cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001,
                    "Runtime load interrupted for code '" + source.getCode() + "'", interrupted)
                .withContext("operation", "LOAD")
                .withContext("cause", "INTERRUPTED")
                .withContext("processCode", source.getCode());
        } catch (CancellationException cancelled) {
            throw new CompileFlowException(ErrorCode.CF_RUNTIME_001,
                    "Runtime load was cancelled for code '" + source.getCode() + "'", cancelled)
                .withContext("operation", "LOAD")
                .withContext("cause", "CANCELLED")
                .withContext("processCode", source.getCode());
        }
    }

    private ProcessDefinitionSnapshot resolveSource(ProcessRuntimeRequest request, ClassLoader classLoader) {
        ProcessRuntimeRequest supplied = Objects.requireNonNull(request, "request");
        return Objects.requireNonNull(definitionLoader.load(supplied, classLoader),
                "ProcessDefinitionLoader must return a definition");
    }

    private ProcessRuntimeEntry findBoundRuntime(ProcessDefinitionSnapshot source,
            ProcessRuntimeIdentity runtimeIdentity) {
        String bindingKey = bindingKey(source);
        if (source.getVersion() != null && runtimeCache.conflictsWithImmutableBinding(bindingKey, runtimeIdentity)) {
            throw versionConflict(source, runtimeIdentity.getDigest());
        }
        ProcessRuntimeEntry bound = runtimeCache.getIfPresent(bindingKey);
        if (bound != null && bound.matches(runtimeIdentity)) {
            return bound;
        }
        ProcessRuntimeEntry exact = runtimeCache.getIfPresent(runtimeIdentity);
        if (exact == null) {
            return null;
        }
        synchronized (cacheLifecycleMonitor) {
            if (!closed.get()) {
                InstallResult result = runtimeCache.install(bindingKey, bound, exact);
                if (result == InstallResult.VERSION_CONFLICT) {
                    throw versionConflict(source, exact.getDigest());
                }
            }
        }
        return exact;
    }

    private ProcessRuntimeIdentity runtimeIdentity(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
        return ProcessRuntimeIdentity.of(definition, pipelineIdentity, classLoader);
    }

    private record BatchPlan(ProcessDefinitionSnapshot source, String bindingKey, ProcessRuntimeIdentity runtimeIdentity,
            ProcessRuntimeEntry expectedEntry) {}

    private record PendingRuntimeLoad(ProcessDefinitionSnapshot source, String bindingKey,
            ProcessRuntimeIdentity runtimeIdentity, ProcessRuntimeEntry expectedEntry,
            CompletableFuture<ProcessRuntimeEntry> future) {}

    private record PendingExactRuntimeLoad(ProcessDefinitionSnapshot source, ProcessRuntimeIdentity runtimeIdentity,
            CompletableFuture<ProcessRuntimeEntry> future) {}

    private record RuntimeLoadFailure(String code, Throwable cause) {}
}
