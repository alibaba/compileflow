/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.runtime.compilation;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.alibaba.compileflow.engine.core.event.ProcessEventPublisher;
import com.alibaba.compileflow.engine.core.executor.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.infrastructure.LogContext;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ExecutorUtils;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;
import com.alibaba.compileflow.engine.core.runtime.RuntimeSpec;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Default implementation mirroring the behavior from AbstractProcessEngine.
 *
 * @author yusu
 */
public class DefaultCompilationService implements CompilationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompilationService.class);

    private final long timeoutSeconds = ProcessPropertyProvider.Compilation.getTimeoutSeconds();
    private final int maxAttempts = ProcessPropertyProvider.Compilation.getMaxAttempts();
    private final long initialBackoffMs = ProcessPropertyProvider.Compilation.getInitialBackoffMs();
    private final long maxBackoffMs = ProcessPropertyProvider.Compilation.getMaxBackoffMs();

    private final ProcessRuntimeCache runtimeCache;
    private final ProcessRuntimeFactory runtimeFactory;
    private final ProcessEngineExecutors executors;
    private final ProcessEventPublisher eventPublisher;

    /**
     * digest -> shared future (single-flight registry)
     */
    private final InflightCompilationRegistry inflightCompilationRegistry = new DefaultInflightCompilationRegistry();

    public DefaultCompilationService(ProcessRuntimeCache runtimeCache,
                                     ProcessRuntimeFactory runtimeFactory,
                                     ProcessEngineExecutors executors,
                                     ProcessEventPublisher eventPublisher) {
        this.runtimeCache = runtimeCache;
        this.runtimeFactory = runtimeFactory;
        this.executors = executors;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CompletableFuture<ProcessRuntimeWrapper> compileAsync(ProcessSource processSource,
                                                                 ClassLoader cl,
                                                                 String newDigest,
                                                                 String expectedDigest) {
        return compileInternal(processSource, cl, newDigest, expectedDigest, false);
    }

    @Override
    public ProcessRuntimeWrapper compileSync(ProcessSource processSource, ClassLoader cl) {
        final String code = processSource.getCode();
        final String version = StringUtils.trimToNull(processSource.getVersion());
        final String codeKey = (version == null) ? code : code + "#" + version;
        final String newDigest = RuntimeSpec.of(processSource, cl).getDigest();
        final String expectedDigest = Optional.ofNullable(runtimeCache.getIfPresent(codeKey))
                .map(ProcessRuntimeWrapper::getDigest)
                .orElse(null);

        CompletableFuture<ProcessRuntimeWrapper> cf = compileInternal(processSource, cl, newDigest, expectedDigest, true);
        try {
            return ExecutorUtils.getWithTimeoutNoCancel(cf, timeoutSeconds * 1000L);
        } catch (TimeoutException te) {
            LOGGER.error("Synchronous compilation timed out after {}s: code={}, version={}, digest={}, traceId={}",
                    timeoutSeconds, code, version, newDigest, LogContext.getTraceId(), te);
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004,
                    "Compilation timed out for code '" + code + "'", te);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            LOGGER.error("Synchronous compilation failed: code={}, version={}, digest={}, traceId={}",
                    code, version, newDigest, LogContext.getTraceId(), cause);
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_001,
                    "Compilation failed for code '" + code + "'", cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Synchronous compilation interrupted: code={}, version={}, digest={}, traceId={}",
                    code, version, newDigest, LogContext.getTraceId(), ie);
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004,
                    "Compilation interrupted for code '" + code + "'", ie);
        } catch (CancellationException ce) {
            LOGGER.warn("Synchronous compilation cancelled while waiting: code={}, version={}, digest={}, traceId={}",
                    code, version, newDigest, LogContext.getTraceId(), ce);
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004,
                    "Compilation was cancelled for code '" + code + "'", ce);
        }
    }

    private ProcessRuntimeWrapper compileWithRetries(ProcessSource s, String digest, ClassLoader cl) {
        RuntimeException last = null;
        long backoff = initialBackoffMs;
        long startTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CompileFlowException.SystemException(
                        ErrorCode.CF_COMPILE_004,
                        "Compilation interrupted before attempt " + attempt,
                        new InterruptedException()
                ).withContext("processCode", s.getCode())
                        .withContext("attempt", attempt)
                        .withContext("maxAttempts", maxAttempts);
            }
            try {
                ProcessRuntime runtime = runtimeFactory.createRuntime(s, cl, digest);
                if (attempt > 1) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    LOGGER.info("Compilation for code [{}] succeeded on attempt {}/{}, duration={}ms",
                            s.getCode(), attempt, maxAttempts, durationMs);
                }
                return new ProcessRuntimeWrapper(runtime, s.getCode(), digest);
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == maxAttempts) {
                    break;
                }

                LOGGER.warn("Compilation attempt {}/{} for code [{}] failed. Retrying in {}ms... (Cause: {})",
                        attempt, maxAttempts, s.getCode(), backoff, ex.getClass().getSimpleName());
                try {
                    long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, backoff / 2), backoff + 1);
                    TimeUnit.MILLISECONDS.sleep(jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CompileFlowException.SystemException(
                            ErrorCode.CF_COMPILE_004, "Compilation interrupted during retry", ie)
                            .withContext("processCode", s.getCode())
                            .withContext("attempt", attempt)
                            .withContext("maxAttempts", maxAttempts)
                            .withContext("totalDuration", System.currentTimeMillis() - startTime);
                }
                backoff = Math.min(backoff * 2, maxBackoffMs);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        eventPublisher.publishCompilationFailed(new String[]{s.getCode()}, durationMs,
                last != null ? last.getMessage() : "Unknown error");

        throw new CompileFlowException.SystemException(
                ErrorCode.CF_COMPILE_001,
                String.format("Compilation failed after %d attempts", maxAttempts),
                last
        ).withContext("processCode", s.getCode())
                .withContext("digest", digest)
                .withContext("maxAttempts", maxAttempts)
                .withContext("totalDuration", durationMs);
    }

    @Override
    public void compileBatch(ClassLoader cl, ProcessSource... sources) {
        long batchTimeoutMs = timeoutSeconds * 1000L;

        List<CompletableFuture<ProcessRuntimeWrapper>> futures = new ArrayList<>();
        Map<CompletableFuture<ProcessRuntimeWrapper>, ProcessSource> futureToSource = new IdentityHashMap<>();

        for (ProcessSource src : sources) {
            String code = Objects.requireNonNull(src.getCode(), "Process code must not be null");
            String version = StringUtils.trimToNull(src.getVersion());
            String codeKey = (version == null) ? code : code + "#" + version;
            String digest = RuntimeSpec.of(src, cl).getDigest();
            String expectedDigest = Optional.ofNullable(runtimeCache.getIfPresent(codeKey))
                    .map(ProcessRuntimeWrapper::getDigest).orElse(null);
            CompletableFuture<ProcessRuntimeWrapper> future = compileAsync(src, cl, digest, expectedDigest);
            futures.add(future);
            futureToSource.put(future, src);
        }

        try {
            ExecutorUtils.waitAllWithTimeout(futures, batchTimeoutMs);
        } catch (TimeoutException te) {
            LOGGER.warn("Batch compilation timed out after {}ms. {} of {} tasks did not complete.",
                    batchTimeoutMs, futures.stream().filter(f -> !f.isDone()).count(), futures.size());
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004, "Batch compilation timeout", te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004, "Batch compilation interrupted", ie);
        } catch (CancellationException ce) {
            throw new CompileFlowException.SystemException(ErrorCode.CF_COMPILE_004, "Batch compilation cancelled", ce);
        } catch (ExecutionException ee) {
            LOGGER.debug("At least one compilation failed during batch execution.", ee.getCause());
        }

        List<CompilationFailure> failures = new ArrayList<>();
        for (Map.Entry<CompletableFuture<ProcessRuntimeWrapper>, ProcessSource> e : futureToSource.entrySet()) {
            CompletableFuture<ProcessRuntimeWrapper> future = e.getKey();
            ProcessSource src = e.getValue();
            try {
                ProcessRuntimeWrapper wrapper = future.join();
                String wanted = RuntimeSpec.of(src, cl).getDigest();
                if (!Objects.equals(wrapper.getDigest(), wanted)) {
                    LOGGER.warn("Stale process runtime used (SWR) after batch compilation: code={}, version={}, wantedDigest={}, currentDigest={}",
                            src.getCode(), StringUtils.trimToNull(src.getVersion()), wanted, wrapper.getDigest());
                } else {
                    LOGGER.debug("Batch compilation successful: code={}, version={}, digest={}",
                            src.getCode(), StringUtils.trimToNull(src.getVersion()), wrapper.getDigest());
                }
            } catch (CompletionException | CancellationException ex) {
                String msg = (ex.getCause() != null ? ex.getCause() : ex).getMessage();
                eventPublisher.publishCompilationFailed(new String[]{src.getCode()}, 0L, msg);
                failures.add(new CompilationFailure(src.getCode(), ex.getCause() != null ? ex.getCause() : ex));
            }
        }

        if (!failures.isEmpty()) {
            String failedCodes = failures.stream()
                    .map(cf -> cf.code == null ? "<unknown>" : cf.code)
                    .collect(Collectors.joining(", "));
            CompileFlowException aggregate = new CompileFlowException.SystemException(
                    ErrorCode.CF_COMPILE_001,
                    "Batch compilation failed for codes: [" + failedCodes + "]",
                    null
            );
            failures.forEach(f -> aggregate.addSuppressed(f.cause));
            throw aggregate;
        }
    }

    @Override
    public void close() throws Exception {
        inflightCompilationRegistry.close();
    }

    private CompletableFuture<ProcessRuntimeWrapper> compileInternal(ProcessSource processSource,
                                                                     ClassLoader cl,
                                                                     String digest,
                                                                     String expectedDigest,
                                                                     boolean runInCallerThread) {
        final CompletableFuture<ProcessRuntimeWrapper> sharedFuture = new CompletableFuture<>();
        CompletableFuture<ProcessRuntimeWrapper> existing = inflightCompilationRegistry.putIfAbsent(digest, sharedFuture);
        if (existing != sharedFuture) {
            return existing;
        }

        String code = processSource.getCode();
        String version = StringUtils.trimToNull(processSource.getVersion());
        String codeKey = (version == null) ? code : code + "#" + version;
        final Runnable compileTask = () -> {
            try {
                ProcessRuntimeWrapper wrapper = compileWithRetries(processSource, digest, cl);
                runtimeCache.compareAndInstall(codeKey, expectedDigest, wrapper);
                sharedFuture.complete(wrapper);
            } catch (Throwable ex) {
                sharedFuture.completeExceptionally(ex);
            } finally {
                inflightCompilationRegistry.remove(digest, sharedFuture);
            }
        };

        if (runInCallerThread) {
            compileTask.run();
        } else {
            final Future<?> compilationTask;
            try {
                compilationTask = executors.compilation().submit(compileTask);
            } catch (RejectedExecutionException ree) {
                LOGGER.error("Compilation task rejected for code [{}]: queue is full.", code, ree);
                eventPublisher.publishCompilationFailed(new String[]{code}, 0L, ree.getMessage());
                sharedFuture.completeExceptionally(ree);
                inflightCompilationRegistry.remove(digest, sharedFuture);
                return sharedFuture;
            }

            sharedFuture.whenComplete((r, ex) -> {
                if (sharedFuture.isCancelled() && !compilationTask.isDone()) {
                    compilationTask.cancel(true);
                }
                if (ex != null) {
                    String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    LOGGER.warn("Asynchronous compilation finished with failure: code={}, version={}, digest={}, error='{}'",
                            code, version, digest, msg);
                } else {
                    LOGGER.debug("Asynchronous compilation finished successfully: code={}, version={}, digest={}",
                            code, version, digest);
                }
            });
        }

        return sharedFuture;
    }

    private static final class CompilationFailure {
        final String code;
        final Throwable cause;

        CompilationFailure(String code, Throwable cause) {
            this.code = code;
            this.cause = cause;
        }
    }
}


