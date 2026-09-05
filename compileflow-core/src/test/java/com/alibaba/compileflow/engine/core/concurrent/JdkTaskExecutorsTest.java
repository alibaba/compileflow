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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JdkTaskExecutorsTest {
    @Test
    void selectsVirtualThreadsOnlyWhenTheRuntimeProvidesThem() throws Exception {
        ExecutorService executor =
                JdkTaskExecutors.newOrchestrationExecutor("runtime-strategy-", 4, (thread, failure) -> {});
        try {
            assertThat(executor
                .submit(() -> Thread.currentThread().getName())
                .get()).startsWith("runtime-strategy-");
            assertThat(JdkTaskExecutors.usesVirtualThreads()).isEqualTo(Runtime.version().feature() >= 21);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsWorkAfterShutdown() throws Exception {
        ExecutorService executor =
                JdkTaskExecutors.newOrchestrationExecutor("runtime-shutdown-", 4, (thread, failure) -> {});
        executor.shutdown();

        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void validatesRequiredDiagnostics() {
        assertThatThrownBy(() -> JdkTaskExecutors.newOrchestrationExecutor(" ", 4, (thread, failure) -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("threadNamePrefix must not be blank");
        assertThatThrownBy(() -> JdkTaskExecutors.newOrchestrationExecutor("worker-", 4, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("exceptionHandler must not be null");
        assertThatThrownBy(() -> JdkTaskExecutors.newOrchestrationExecutor("worker-", 0, (thread, failure) -> {}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxPlatformThreads must be positive");
    }

    @Test
    void java17FallbackBoundsOwnedThreadsAndRunsSaturatedWorkInCaller() throws Exception {
        assumeFalse(JdkTaskExecutors.usesVirtualThreads());
        ExecutorService executor =
                JdkTaskExecutors.newOrchestrationExecutor("runtime-bounded-", 1, (thread, failure) -> {});
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            Future<?> occupied = executor.submit(() -> {
                workerStarted.countDown();
                releaseWorker.await();
                return null;
            });
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            String submittingThread = Thread.currentThread().getName();
            AtomicReference<String> executionThread = new AtomicReference<>();
            executor.execute(() -> executionThread.set(Thread.currentThread().getName()));

            assertThat(executionThread).hasValue(submittingThread);
            releaseWorker.countDown();
            occupied.get(5, TimeUnit.SECONDS);
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }
}
