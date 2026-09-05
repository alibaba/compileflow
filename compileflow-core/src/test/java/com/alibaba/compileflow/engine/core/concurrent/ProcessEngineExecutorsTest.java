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
package com.alibaba.compileflow.engine.core.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessEngineExecutorsTest {
    private static ProcessExecutorConfig singleThreadConfig() {
        return ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build();
    }

    private static void await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        boolean interrupted = false;
        while (true) {
            try {
                release.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void saturatedRuntimeLoadExecutorRejectsInsteadOfLoadingOnRequestThreads() throws Exception {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .runtimeLoadMaxPending(1)
            .actionTimeoutMaxConcurrency(1)
            .build();

        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("compilation-saturation-test", config)) {
            ExecutorService compilation = executors.runtimeLoad();
            CountDownLatch workerStarted = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            compilation.execute(() -> await(workerStarted, releaseWorker));
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            compilation.execute(() -> {});

            assertThatThrownBy(() -> compilation.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            releaseWorker.countDown();
        }
    }

    @Test
    void saturatedActionPoolRejectsInsteadOfRunningOnTheCaller() throws Exception {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .actionTimeoutMaxConcurrency(1)
            .actionTimeoutMaxPending(1)
            .build();

        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("saturation-test", config)) {
            ExecutorService action = executors.action();
            CountDownLatch workerStarted = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            action.execute(() -> await(workerStarted, releaseWorker));
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            action.execute(() -> {});

            assertThatThrownBy(() -> action.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            releaseWorker.countDown();
        }
    }

    @Test
    void saturatedPreflightPoolRejectsInsteadOfRunningOnTheCaller() throws Exception {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(17)
            .runtimeLoadMaxPending(19)
            .actionTimeoutMaxConcurrency(1)
            .build();

        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("preflight-saturation-test", config)) {
            ThreadPoolExecutor preflight = (ThreadPoolExecutor) executors.preflight();
            int maxConcurrency = preflight.getMaximumPoolSize();
            int maxPending = preflight.getQueue().remainingCapacity();
            assertThat(maxConcurrency).isNotEqualTo(config.getRuntimeLoadMaxConcurrency());
            assertThat(maxPending).isNotEqualTo(config.getRuntimeLoadMaxPending());

            CountDownLatch workerStarted = new CountDownLatch(maxConcurrency);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            for (int index = 0; index < maxConcurrency; index++) {
                preflight.execute(() -> await(workerStarted, releaseWorker));
            }
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < maxPending; index++) {
                preflight.execute(() -> {});
            }

            assertThatThrownBy(() -> preflight.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            releaseWorker.countDown();
        }
    }

    @Test
    void zeroPendingLimitsRejectImmediatelyWhenExecutionSlotsAreOccupied() throws Exception {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .runtimeLoadMaxPending(0)
            .actionTimeoutMaxConcurrency(1)
            .actionTimeoutMaxPending(0)
            .build();
        ProcessObservabilityConfig observability =
                ProcessObservabilityConfig
            .builder()
            .eventDeliveryMaxConcurrency(1)
            .eventDeliveryMaxPending(0)
            .build();

        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("fail-fast-test", config, observability)) {
            CountDownLatch started = new CountDownLatch(3);
            CountDownLatch release = new CountDownLatch(1);
            executors
                .runtimeLoad()
                .execute(() -> await(started, release));
            executors.action().execute(() -> await(started, release));
            executors.event().execute(() -> await(started, release));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executors.runtimeLoad().execute(() -> {})).isInstanceOf(
                    RejectedExecutionException.class);
            assertThatThrownBy(() -> executors.action().execute(() -> {})).isInstanceOf(
                    RejectedExecutionException.class);
            assertThatThrownBy(() -> executors.event().execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            release.countDown();
        }
    }

    @Test
    void cancellingQueuedActionReleasesQueueCapacityImmediately() throws Exception {
        ProcessExecutorConfig config = ProcessExecutorConfig
            .builder()
            .runtimeLoadMaxConcurrency(1)
            .actionTimeoutMaxConcurrency(1)
            .actionTimeoutMaxPending(1)
            .build();

        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("cancel-test", config)) {
            ExecutorService action = executors.action();
            CountDownLatch workerStarted = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            action.execute(() -> await(workerStarted, releaseWorker));
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> cancelled = action.submit(() -> {});

            assertThat(cancelled.cancel(false)).isTrue();
            assertThat(action.submit(() -> "accepted")).isNotNull();
            releaseWorker.countDown();
        }
    }

    @Test
    void closedManagedExecutorsRejectNewWork() {
        ProcessEngineExecutors executors = ProcessEngineExecutors.create("shutdown-test", singleThreadConfig());
        ExecutorService preflight = executors.preflight();
        ExecutorService action = executors.action();
        ExecutorService parallel = executors.parallel();
        executors.close();

        assertThatThrownBy(() -> preflight.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(() -> action.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(() -> parallel.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void rejectsShutdownFromAnOwnedWorkerWithoutPartiallyClosingExecutors() throws Exception {
        ProcessEngineExecutors executors = ProcessEngineExecutors.create("self-shutdown-test", singleThreadConfig());
        try {
            Future<Throwable> attemptedClose = executors.event().submit(() -> {
                try {
                    executors.close();
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });

            assertThat(attemptedClose.get(5, TimeUnit.SECONDS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Engine executors cannot be closed from one of their worker threads");
            assertThat(executors.event().isShutdown()).isFalse();
            assertThat(executors
                .action()
                .submit(() -> "still-open")
                .get(5, TimeUnit.SECONDS)).isEqualTo("still-open");
        } finally {
            executors.close();
        }
    }

    @Test
    void rejectsShutdownFromAParallelTaskWithoutPartiallyClosingExecutors() throws Exception {
        ProcessEngineExecutors executors =
                ProcessEngineExecutors.create("parallel-self-shutdown-test", singleThreadConfig());
        try {
            Future<Throwable> attemptedClose = executors.parallel().submit(() -> {
                try {
                    executors.close();
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });

            assertThat(attemptedClose.get(5, TimeUnit.SECONDS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Engine executors cannot be closed from one of their worker threads");
            assertThat(executors.parallel().isShutdown()).isFalse();
            assertThat(executors
                .action()
                .submit(() -> "still-open")
                .get(5, TimeUnit.SECONDS)).isEqualTo("still-open");
        } finally {
            executors.close();
        }
    }

    @Test
    void parallelExecutorUsesTheRuntimeThreadStrategy() throws Exception {
        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("virtual-test", singleThreadConfig())) {
            assertThat(executors
                .parallel()
                .submit(() -> Thread.currentThread().getName())
                .get())
                .startsWith("compileflow-virtual-test-parallel-");
            assertThat(JdkTaskExecutors.usesVirtualThreads()).isEqualTo(Runtime.version().feature() >= 21);
        }
    }

    @Test
    void java17CallerRunsBranchEntersAndRestoresEngineOwnership() throws Exception {
        assumeFalse(JdkTaskExecutors.usesVirtualThreads());
        ProcessExecutorConfig config =
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build();
        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("caller-runs-owner-test", config)) {
            int platformThreads = JdkTaskExecutors.defaultMaxPlatformThreads();
            CountDownLatch workerStarted = new CountDownLatch(platformThreads);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            List<Future<?>> occupied = new java.util.ArrayList<>();
            for (int index = 0; index < platformThreads; index++) {
                occupied.add(executors
                    .parallel()
                    .submit(() -> await(workerStarted, releaseWorker)));
            }
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            try {
                assertThat(executors.ownsCurrentThread()).isFalse();
                assertThat(executors.parallel().submit(executors::ownsCurrentThread).get()).isTrue();
                assertThat(executors.ownsCurrentThread()).isFalse();
            } finally {
                releaseWorker.countDown();
            }
            for (Future<?> future : occupied) {
                future.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void shutdownBudgetsAreSharedAcrossTheExecutorGroup() throws Exception {
        ProcessExecutorConfig config =
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build();
        ProcessEngineExecutors executors = ProcessEngineExecutors.create("deadline-test", config,
                ProcessObservabilityConfig.defaults(), Duration.ofMillis(150));
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        executors
            .runtimeLoad()
            .execute(() -> await(started, release));
        executors
            .preflight()
            .execute(() -> await(started, release));
        executors.action().execute(() -> await(started, release));
        executors.event().execute(() -> await(started, release));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        executors.close();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        release.countDown();

        assertThat(elapsedMillis).isLessThan(500L);
    }

    @Test
    void reportsWorkersThatIgnoreTheForcedShutdownBudget() throws Exception {
        ProcessExecutorConfig config =
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build();
        ProcessEngineExecutors executors = ProcessEngineExecutors.create("stuck-worker-test", config,
                ProcessObservabilityConfig.defaults(), Duration.ofMillis(60));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executors
            .event()
            .execute(() -> awaitIgnoringInterrupt(started, release));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(executors::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not terminate")
                .hasMessageContaining("event");
        } finally {
            release.countDown();
        }
    }

    @Test
    void saturatedEventPoolRejectsSoThePublisherCanRecover() throws Exception {
        ProcessExecutorConfig config =
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build();

        ProcessObservabilityConfig observability =
                ProcessObservabilityConfig
            .builder()
            .eventDeliveryMaxConcurrency(1)
            .eventDeliveryMaxPending(1)
            .build();
        try (ProcessEngineExecutors executors = ProcessEngineExecutors.create("event-test", config, observability)) {
            ExecutorService event = executors.event();
            CountDownLatch workerStarted = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            event.execute(() -> await(workerStarted, releaseWorker));
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            event.execute(() -> {});

            assertThatThrownBy(() -> event.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            releaseWorker.countDown();
        }
    }
}
