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

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.bindingKey;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.inline;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.resolved;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.NoOpProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeFactory;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.cache.DefaultProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache.ReleaseResult;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.util.function.Function;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DefaultProcessRuntimeLoaderTest {
    private static final ProcessRuntime TEST_RUNTIME = NoOpProcessRuntime.INSTANCE;
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);

    private static ProcessRuntimeRequest source(String namespace) {
        return versioned(namespace, "order.checkout", "1", "<process/>");
    }

    private static ProcessSemanticCompilation compilation(ProcessDefinitionSnapshot source) {
        ProcessSemanticPlan plan = new ProcessSemanticPlan(source.getCode(),
                TEST_RUNTIME.getSemanticPlan().getVariables(), TEST_RUNTIME.getSemanticPlan().getNodes());
        return new ProcessSemanticCompilation(plan, new StructuredControlFlowAnalyzer().analyze(plan));
    }

    @ParameterizedTest
    @EnumSource(FailureStage.class)
    void scopesBothStagesAndRestoresWorkerClassLoader(FailureStage stage) throws Exception {
        ClassLoader compilationLoader = new ClassLoader(getClass().getClassLoader()) {
        };
        AtomicInteger frontendCalls = new AtomicInteger();
        AtomicInteger backendCalls = new AtomicInteger();
        AtomicReference<ProcessSemanticCompilation> compiled = new AtomicReference<>();
        RuntimeException expected = new IllegalArgumentException("failed " + stage);
        Function<ProcessDefinitionSnapshot, ProcessSemanticCompilation> frontend =
                source -> {
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(compilationLoader);
            frontendCalls.incrementAndGet();
            if (stage == FailureStage.FRONTEND) {
                throw expected;
            }
            compiled.set(compilation(source));
            return compiled.get();
        };
        ProcessRuntimeFactory backend =
                (compilation, loader) -> {
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(compilationLoader);
            assertThat(loader).isSameAs(compilationLoader);
            assertThat(compilation).isSameAs(compiled.get());
            backendCalls.incrementAndGet();
            if (stage == FailureStage.BACKEND) {
                throw expected;
            }
            return TEST_RUNTIME;
        };
        try (TestHarness harness = TestHarness.create(frontend, backend, TEST_TIMEOUT, 1)) {
            ClassLoader original = harness.executors
                .runtimeLoad()
                .submit(() -> Thread.currentThread().getContextClassLoader())
                .get(1, TimeUnit.SECONDS);
            if (stage == FailureStage.NONE) {
                ProcessRuntimeEntry first = harness.service.loadSync(source("default"), compilationLoader);
                assertThat(harness.service.loadSync(source("default"), compilationLoader)).isSameAs(first);
            } else {
                assertThatThrownBy(() -> harness.service.loadSync(source("default"), compilationLoader))
                    .isInstanceOf(CompileFlowException.class)
                    .hasCause(expected);
                assertThat(harness.cache.size()).isZero();
            }
            assertThat(frontendCalls).hasValue(1);
            assertThat(backendCalls).hasValue(stage == FailureStage.FRONTEND ? 0 : 1);
            assertThat(harness.executors
                .runtimeLoad()
                .submit(() -> Thread.currentThread().getContextClassLoader())
                .get(1, TimeUnit.SECONDS))
                .isSameAs(original);
        }
    }

    private enum FailureStage {
        NONE,
        FRONTEND,
        BACKEND
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test compilation interrupted", interrupted);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (latch.getCount() > 0L) {
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @SuppressWarnings("try")
    void cachedExactRuntimeDoesNotReinstallAfterLoaderClose() throws Exception {
        AtomicBoolean pauseCacheHit = new AtomicBoolean();
        CountDownLatch cacheHit = new CountDownLatch(1);
        CountDownLatch releaseCacheHit = new CountDownLatch(1);
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(16) {
            @Override
            public ProcessRuntimeEntry getIfPresent(ProcessRuntimeIdentity identity) {
                ProcessRuntimeEntry entry = super.getIfPresent(identity);
                if (entry != null && pauseCacheHit.compareAndSet(true, false)) {
                    cacheHit.countDown();
                    awaitUninterruptibly(releaseCacheHit);
                }
                return entry;
            }
        };
        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create(ProcessExecutorConfig.defaults());
                DefaultProcessRuntimeLoader loader = new DefaultProcessRuntimeLoader(cache,
                        (request, classLoader) -> resolved(request), DefaultProcessRuntimeLoaderTest::compilation,
                        (compiled, classLoader) -> TEST_RUNTIME, executors.runtimeLoad(), TEST_TIMEOUT)) {
            ClassLoader classLoader = getClass().getClassLoader();
            loader.loadExactSync(inline("order.checkout", "<process/>"), classLoader);
            ProcessRuntimeRequest request = source("default");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            pauseCacheHit.set(true);
            Thread caller = new Thread(() -> {
                try {
                    loader.loadSync(request, classLoader);
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            });
            caller.start();
            try {
                assertThat(cacheHit.await(2, TimeUnit.SECONDS)).isTrue();
                loader.close();
                cache.invalidateAll();
                releaseCacheHit.countDown();
                caller.join(2_000L);
                assertThat(caller.isAlive()).isFalse();
                assertThat(failure.get()).isNull();
                assertThat(cache.getIfPresent(bindingKey(request))).isNull();
                assertThat(cache.size()).isZero();
            } finally {
                releaseCacheHit.countDown();
                caller.join(2_000L);
            }
        }
    }

    @Test
    void queuedLoadReusesRuntimePublishedBeforeExecution() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            return TEST_RUNTIME;
        };
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            ProcessRuntimeRequest request = source("tenant");
            ClassLoader classLoader = getClass().getClassLoader();
            ProcessRuntimeEntry prepared = harness.service.runtimeCheckSync(resolved(request), classLoader);
            harness.executors.runtimeLoad().submit(() -> {
                workerBlocked.countDown();
                await(releaseWorker);
            });
            try {
                assertThat(workerBlocked.await(2, TimeUnit.SECONDS)).isTrue();
                CompletableFuture<ProcessRuntimeEntry> queued = harness.service.loadAsync(request, classLoader, null);
                harness.cache.cacheExact(prepared);
                releaseWorker.countDown();

                assertThat(queued.get(2, TimeUnit.SECONDS)).isSameAs(prepared);
                assertThat(harness.cache.getIfPresent(bindingKey(request))).isSameAs(prepared);
                assertThat(compilations).hasValue(1);
            } finally {
                releaseWorker.countDown();
            }
        }
    }

    @Test
    void sharesCompilationAcrossNamespacesAndInstallsEveryCacheKey() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch compilationStarted = new CountDownLatch(1);
        CountDownLatch releaseCompilation = new CountDownLatch(1);
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            compilationStarted.countDown();
            await(releaseCompilation);
            return TEST_RUNTIME;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 2)) {
            ProcessRuntimeRequest alpha = source("tenant-alpha");
            ProcessRuntimeRequest beta = source("tenant-beta");
            ClassLoader classLoader = getClass().getClassLoader();

            CompletableFuture<ProcessRuntimeEntry> alphaFuture = harness.service.loadAsync(alpha, classLoader, null);
            assertThat(compilationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<ProcessRuntimeEntry> betaFuture = harness.service.loadAsync(beta, classLoader, null);
            releaseCompilation.countDown();

            ProcessRuntimeEntry alphaEntry = alphaFuture.get(1, TimeUnit.SECONDS);
            ProcessRuntimeEntry betaEntry = betaFuture.get(1, TimeUnit.SECONDS);

            assertThat(compilations).hasValue(1);
            assertThat(betaEntry).isSameAs(alphaEntry);
            assertThat(harness.cache.getIfPresent(bindingKey(alpha))).isSameAs(alphaEntry);
            assertThat(harness.cache.getIfPresent(bindingKey(beta))).isSameAs(betaEntry);
        } finally {
            releaseCompilation.countDown();
        }
    }

    @Test
    void equalButDistinctClassLoadersCompileIndependently() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch compilationsStarted = new CountDownLatch(2);
        CountDownLatch releaseCompilations = new CountDownLatch(1);
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            compilationsStarted.countDown();
            await(releaseCompilations);
            return TEST_RUNTIME;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 2)) {
            ClassLoader parent = getClass().getClassLoader();
            ClassLoader first = new EqualClassLoader(parent);
            ClassLoader second = new EqualClassLoader(parent);

            CompletableFuture<ProcessRuntimeEntry> firstFuture =
                    harness.service.loadAsync(source("tenant-alpha"), first, null);
            CompletableFuture<ProcessRuntimeEntry> secondFuture =
                    harness.service.loadAsync(source("tenant-beta"), second, null);

            assertThat(compilationsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseCompilations.countDown();
            firstFuture.get(1, TimeUnit.SECONDS);
            secondFuture.get(1, TimeUnit.SECONDS);

            assertThat(compilations).hasValue(2);
        } finally {
            releaseCompilations.countDown();
        }
    }

    @Test
    void synchronousWaitTimeoutDoesNotCancelSharedCompilation() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch releaseCompilation = new CountDownLatch(1);
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            await(releaseCompilation);
            return TEST_RUNTIME;
        };

        try (TestHarness harness = TestHarness.create(factory, Duration.ofMillis(50), 1)) {
            ProcessRuntimeRequest request = source("default");
            ClassLoader classLoader = getClass().getClassLoader();
            assertThatThrownBy(() -> harness.service.loadSync(request, classLoader))
                .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(
                            com.alibaba.compileflow.engine.ErrorCode.CF_RUNTIME_001);
                    assertThat(failure.getContext())
                        .containsEntry("operation", "LOAD")
                        .containsEntry("cause", "TIMEOUT")
                        .containsEntry("processCode", request.getCode())
                        .containsEntry("timeoutMs", 50L);
                });

            CompletableFuture<ProcessRuntimeEntry> follower = harness.service.loadAsync(request, classLoader, null);
            releaseCompilation.countDown();

            ProcessRuntimeEntry compiled = follower.get(1, TimeUnit.SECONDS);
            assertThat(compilations).hasValue(1);
            assertThat(harness.cache.getIfPresent(bindingKey(request))).isSameAs(compiled);
        } finally {
            releaseCompilation.countDown();
        }
    }

    @Test
    void batchTimeoutDoesNotCancelSharedSingleFlightCompilation() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch compilationStarted = new CountDownLatch(1);
        CountDownLatch releaseCompilation = new CountDownLatch(1);
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            compilationStarted.countDown();
            awaitUninterruptibly(releaseCompilation);
            return TEST_RUNTIME;
        };
        ProcessRuntimeRequest request = source("default");
        AtomicReference<Throwable> batchFailure = new AtomicReference<>();

        try (TestHarness harness = TestHarness.create(factory, Duration.ofMillis(50), 1)) {
            Thread batchCaller = new Thread(() -> {
                try {
                    harness.service.loadBatch("batch-owner", getClass().getClassLoader(), request);
                } catch (Throwable failure) {
                    batchFailure.set(failure);
                }
            });
            batchCaller.start();
            assertThat(compilationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            batchCaller.join(1_000L);

            CompletableFuture<ProcessRuntimeEntry> follower =
                    harness.service.loadAsync(request, getClass().getClassLoader(), null);
            releaseCompilation.countDown();

            assertThat(batchCaller.isAlive()).isFalse();
            assertThat(batchFailure.get()).isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(com.alibaba.compileflow.engine.ErrorCode.CF_RUNTIME_001);
                assertThat(failure.getContext())
                    .containsEntry("operation", "LOAD")
                    .containsEntry("cause", "TIMEOUT")
                    .containsEntry("timeoutMs", 50L)
                    .containsEntry("batchSize", 1)
                    .containsEntry("unfinished", 1L);
            });

            assertThat(follower.get(1, TimeUnit.SECONDS)).isNotNull();
            assertThat(compilations).hasValue(1);
        } finally {
            releaseCompilation.countDown();
        }
    }

    @Test
    void closeCancelsInflightWorkAndRejectsNewCalls() throws Exception {
        CountDownLatch compilationStarted = new CountDownLatch(1);
        CountDownLatch releaseCompilation = new CountDownLatch(1);
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilationStarted.countDown();
            await(releaseCompilation);
            return TEST_RUNTIME;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            CompletableFuture<ProcessRuntimeEntry> future =
                    harness.service.loadAsync(source("default"), getClass().getClassLoader(), null);
            assertThat(compilationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            harness.service.close();
            harness.service.close();

            assertThat(future).isCompletedExceptionally();
            assertThatThrownBy(future::join).hasCauseInstanceOf(CancellationException.class);
            assertThatThrownBy(() -> harness.service.loadSync(source("default"), getClass().getClassLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Runtime loader is closed");
        } finally {
            releaseCompilation.countDown();
        }
    }

    @Test
    void fatalErrorsAreNotConvertedIntoCompilationFailures() {
        AssertionError fatal = new AssertionError("fatal compiler failure");
        ProcessRuntimeFactory factory = (source, classLoader) -> {
            throw fatal;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            assertThatThrownBy(() -> harness.service.loadSync(source("default"), getClass().getClassLoader())).isSameAs(
                    fatal);
        }
    }

    @Test
    void oneRequestAttemptsCompilationOnceAndLaterCallsMayTryAgain() {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            if (compilations.incrementAndGet() == 1) {
                throw new IllegalStateException("compiler environment unavailable");
            }
            return TEST_RUNTIME;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            ProcessRuntimeRequest request = source("default");
            ClassLoader classLoader = getClass().getClassLoader();

            assertThatThrownBy(() -> harness.service.loadSync(request, classLoader))
                .isInstanceOf(CompileFlowException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(compilations).hasValue(1);

            assertThat(harness.service.loadSync(request, classLoader)).isNotNull();
            assertThat(compilations).hasValue(2);
        }
    }

    @Test
    void diagnosticLoggingDoesNotTraverseApplicationFailureCauses() {
        RuntimeException failure = new RuntimeException("compiler environment unavailable") {
            @Override
            public synchronized Throwable getCause() {
                throw new AssertionError("diagnostic logging must not inspect application cause chains");
            }
        };
        ProcessRuntimeFactory factory = (source, classLoader) -> {
            throw failure;
        };

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            assertThatThrownBy(() -> harness.service.loadSync(source("default"), getClass().getClassLoader()))
                .isInstanceOf(CompileFlowException.class)
                .hasCause(failure);
        }
    }

    @Test
    void rejectedOwnerIsEvictedBeforeFailedWaitersRetry() throws Exception {
        BlockingRejectOnceExecutor executor = new BlockingRejectOnceExecutor();
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        DefaultProcessRuntimeLoader loader = new DefaultProcessRuntimeLoader(cache,
                (source, classLoader) -> resolved(source), DefaultProcessRuntimeLoaderTest::compilation,
                (source, classLoader) -> TEST_RUNTIME, executor, TEST_TIMEOUT);
        ProcessRuntimeRequest request = source("default");
        ClassLoader classLoader = getClass().getClassLoader();
        AtomicReference<CompletableFuture<ProcessRuntimeEntry>> owner = new AtomicReference<>();
        AtomicReference<CompletableFuture<ProcessRuntimeEntry>> retry = new AtomicReference<>();
        Thread ownerThread = new Thread(() -> owner.set(loader.loadAsync(request, classLoader, null)));

        try {
            ownerThread.start();
            assertThat(executor.firstSubmissionEntered.await(1, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<ProcessRuntimeEntry> waiter = loader.loadAsync(request, classLoader, null);
            waiter.whenComplete((entry, failure) -> retry.set(loader.loadAsync(request, classLoader, null)));
            executor.rejectFirstSubmission.countDown();
            ownerThread.join(1_000L);

            assertThat(ownerThread.isAlive()).isFalse();
            assertThatThrownBy(owner.get()::join).hasCauseInstanceOf(RejectedExecutionException.class);
            assertThatThrownBy(waiter::join).hasCauseInstanceOf(RejectedExecutionException.class);
            assertThat(retry.get()).isNotNull();
            assertThat(retry.get().get(1, TimeUnit.SECONDS)).isNotNull();
            assertThat(executor.submissions).hasValue(2);
        } finally {
            executor.rejectFirstSubmission.countDown();
            ownerThread.join(1_000L);
            loader.close();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsDifferentContentForAnInstalledVersion() {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            return TEST_RUNTIME;
        };
        ProcessRuntimeRequest first = versioned("default", "order.checkout", "7", "<process id='first'/>");
        ProcessRuntimeRequest second = versioned("default", "order.checkout", "7", "<process id='second'/>");

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            ProcessRuntimeEntry installed = harness.service.loadSync(first, getClass().getClassLoader());

            assertThatThrownBy(() -> harness.service.loadSync(second, getClass().getClassLoader()))
                .isInstanceOf(CompileFlowException.ValidationException.class)
                .hasMessageContaining("already bound to different content")
                .hasMessageContaining("version=7");
            assertThat(harness.cache.getIfPresent(bindingKey(first))).isSameAs(installed);
            assertThat(harness.service.loadSync(first, getClass().getClassLoader())).isSameAs(installed);
        }

        assertThat(compilations).hasValue(1);
    }

    @Test
    void sharesOneCompiledRuntimeAcrossVersionsWithIdenticalInputs() {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            return TEST_RUNTIME;
        };
        ProcessRuntimeRequest versionOne = versioned("default", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest versionTwo = versioned("default", "order.checkout", "2", "<process/>");

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            ProcessRuntimeEntry first = harness.service.loadSync(versionOne, getClass().getClassLoader());
            ProcessRuntimeEntry second = harness.service.loadSync(versionTwo, getClass().getClassLoader());

            assertThat(second).isSameAs(first);
            assertThat(harness.cache.getIfPresent(bindingKey(versionOne))).isSameAs(first);
            assertThat(harness.cache.getIfPresent(bindingKey(versionTwo))).isSameAs(first);
        }

        assertThat(compilations).hasValue(1);
    }

    @Test
    void reusesHistoricalExactRuntimeWhenUnversionedContentReturns() {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            compilations.incrementAndGet();
            return TEST_RUNTIME;
        };
        ProcessRuntimeRequest first = inline("order.checkout", "<process id='first'/>");
        ProcessRuntimeRequest second = inline("order.checkout", "<process id='second'/>");

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 1)) {
            ProcessRuntimeEntry firstRuntime = harness.service.loadExactSync(first, getClass().getClassLoader());
            ProcessRuntimeEntry secondRuntime = harness.service.loadExactSync(second, getClass().getClassLoader());
            ProcessRuntimeEntry firstAgain = harness.service.loadExactSync(first, getClass().getClassLoader());

            assertThat(secondRuntime).isNotSameAs(firstRuntime);
            assertThat(firstAgain).isSameAs(firstRuntime);
            assertThat(harness.cache.getIfPresent(bindingKey(first))).isNull();
        }

        assertThat(compilations).hasValue(2);
    }

    @Test
    void exactBatchWarmsRuntimesWithoutCreatingBindings() {
        ProcessRuntimeRequest first = inline("order.checkout", "<process id='first'/>");
        ProcessRuntimeRequest second = inline("invoice.create", "<process id='second'/>");

        try (TestHarness harness = TestHarness.create((source, classLoader) -> TEST_RUNTIME, TEST_TIMEOUT, 2)) {
            harness.service.loadExactBatch(getClass().getClassLoader(), first, second);

            assertThat(harness.cache.getIfPresent(bindingKey(first))).isNull();
            assertThat(harness.cache.getIfPresent(bindingKey(second))).isNull();
            assertThat(harness.cache.size()).isEqualTo(2);
        }
    }

    @Test
    void failedBatchDoesNotExposeSuccessfullyCompiledItems() {
        ProcessRuntimeFactory factory =
                (source, classLoader) -> {
            if ("invoice.create".equals(source.semanticPlan().getProcessCode())) {
                throw new IllegalStateException("invalid invoice process");
            }
            return TEST_RUNTIME;
        };
        ProcessRuntimeRequest successful = versioned("default", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest failing = versioned("default", "invoice.create", "1", "<process/>");

        try (TestHarness harness = TestHarness.create(factory, TEST_TIMEOUT, 2)) {
            assertThatThrownBy(() -> harness.service.loadBatch("batch-owner", getClass().getClassLoader(), successful,
                    failing))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("invoice.create");

            assertThat(harness.cache.getIfPresent(bindingKey(successful))).isNull();
            assertThat(harness.cache.getIfPresent(bindingKey(failing))).isNull();
            assertThat(harness.cache.size()).isZero();
        }
    }

    @Test
    void successfulBatchInstallsAndRetainsEveryItem() {
        ProcessRuntimeRequest first = versioned("default", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest second = versioned("default", "invoice.create", "1", "<process/>");

        try (TestHarness harness = TestHarness.create((source, classLoader) -> TEST_RUNTIME, TEST_TIMEOUT, 2)) {
            harness.service.loadBatch("batch-owner", getClass().getClassLoader(), first, second);

            assertThat(harness.cache.getIfPresent(bindingKey(first))).isNotNull();
            assertThat(harness.cache.getIfPresent(bindingKey(second))).isNotNull();
            assertThat(harness.cache.release(bindingKey(first), "batch-owner")).isEqualTo(ReleaseResult.INVALIDATED);
            assertThat(harness.cache.release(bindingKey(second), "batch-owner")).isEqualTo(ReleaseResult.INVALIDATED);
        }
    }

    @Test
    void duplicateBatchBindingIsRejectedBeforeCompilationStarts() {
        AtomicInteger compilations = new AtomicInteger();
        ProcessRuntimeRequest request = versioned("default", "order.checkout", "1", "<process/>");

        try (TestHarness harness = TestHarness.create((source, classLoader) -> {
            compilations.incrementAndGet();
            return TEST_RUNTIME;
        }, TEST_TIMEOUT, 2)) {
            assertThatThrownBy(() -> harness.service.loadBatch("batch-owner", getClass().getClassLoader(), request,
                    request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate process binding");
            assertThat(compilations).hasValue(0);
        }
    }

    private static final class TestHarness implements AutoCloseable {
        private final DefaultProcessRuntimeCache cache;
        private final ProcessEngineExecutors executors;
        private final DefaultProcessRuntimeLoader service;

        private TestHarness(DefaultProcessRuntimeCache cache, ProcessEngineExecutors executors,
                DefaultProcessRuntimeLoader service) {
            this.cache = cache;
            this.executors = executors;
            this.service = service;
        }

        private static TestHarness create(ProcessRuntimeFactory runtimeFactory, Duration timeout,
                int runtimeLoadMaxConcurrency) {
            return create(DefaultProcessRuntimeLoaderTest::compilation, runtimeFactory, timeout,
                    runtimeLoadMaxConcurrency);
        }

        private static TestHarness create(Function<ProcessDefinitionSnapshot, ProcessSemanticCompilation> frontend,
                ProcessRuntimeFactory runtimeFactory, Duration timeout, int runtimeLoadMaxConcurrency) {
            DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
            ProcessEngineExecutors executors = ProcessEngineExecutors.create("compilation-service-test",
                    ProcessExecutorConfig
                        .builder()
                        .runtimeLoadMaxConcurrency(runtimeLoadMaxConcurrency)
                        .runtimeLoadMaxPending(4)
                        .actionTimeoutMaxConcurrency(1)
                        .build());
            DefaultProcessRuntimeLoader service = new DefaultProcessRuntimeLoader(cache,
                    (source, classLoader) -> resolved(source), frontend, runtimeFactory, executors.runtimeLoad(),
                    timeout);
            return new TestHarness(cache, executors, service);
        }

        @Override
        public void close() {
            service.close();
            executors.close();
        }
    }

    private static final class EqualClassLoader extends ClassLoader {
        private EqualClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualClassLoader;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class BlockingRejectOnceExecutor extends AbstractExecutorService {
        private final CountDownLatch firstSubmissionEntered = new CountDownLatch(1);
        private final CountDownLatch rejectFirstSubmission = new CountDownLatch(1);
        private final AtomicInteger submissions = new AtomicInteger();
        private final AtomicBoolean shutdown = new AtomicBoolean();

        @Override
        public void execute(Runnable command) {
            if (shutdown.get()) {
                throw new RejectedExecutionException("executor is shut down");
            }
            if (submissions.incrementAndGet() == 1) {
                firstSubmissionEntered.countDown();
                await(rejectFirstSubmission);
                throw new RejectedExecutionException("first submission rejected");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
            rejectFirstSubmission.countDown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown.get();
        }
    }
}
