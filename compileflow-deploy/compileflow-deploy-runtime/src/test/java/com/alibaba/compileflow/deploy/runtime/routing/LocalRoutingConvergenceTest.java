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
package com.alibaba.compileflow.deploy.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalRoutingConvergenceTest {
    private static final ProcessRef.Alias ALIAS = ProcessRef.alias("default", "bounded.flow", "production");
    private static final Duration TIMEOUT = Duration.ofMillis(100);

    private static DesiredRoutingState desired() {
        return DesiredRoutingState
            .builder()
            .namespace(ALIAS.namespace())
            .code(ALIAS.code())
            .alias(ALIAS.alias())
            .stableVersion("v1")
            .revision(1L)
            .actor("test")
            .updatedAt(1L)
            .build();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void timedOutAsynchronousConvergenceRetainsAdmissionUntilCompletion(int concurrency) {
        LocalRoutingReconciler reconciler = mock(LocalRoutingReconciler.class);
        CompletableFuture<Void> shared = new CompletableFuture<>();
        when(reconciler.apply(any())).thenReturn(shared);
        try (LocalRoutingConvergence convergence = new LocalRoutingConvergence(reconciler, concurrency)) {
            for (int index = 0; index < concurrency; index++) {
                assertThatThrownBy(() -> convergence.applyAndAwait(desired(), TIMEOUT))
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("timed out");
            }
            assertThatThrownBy(() -> convergence.applyAndAwait(desired(), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                    assertThat(failure.getNamespace()).isEqualTo(ALIAS.namespace());
                    assertThat(failure.getCode()).isEqualTo(ALIAS.code());
                    assertThat(failure.getAlias()).isEqualTo(ALIAS.alias());
                })
                .hasMessageContaining("capacity is exhausted");
            assertThat(shared.isCancelled()).isFalse();
            Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .until(() -> shared.getNumberOfDependents() == concurrency);
            verify(reconciler, times(concurrency)).apply(any());

            shared.complete(null);
            convergence.applyAndAwait(desired(), Duration.ofSeconds(1));
            verify(reconciler, times(concurrency + 1)).apply(any());
        } finally {
            shared.complete(null);
        }
    }

    @Test
    void callerInterruptionPreservesTheFlagAndSharedConvergence() throws Exception {
        LocalRoutingReconciler reconciler = mock(LocalRoutingReconciler.class);
        CompletableFuture<Void> shared = new CompletableFuture<>();
        CountDownLatch started = new CountDownLatch(1);
        when(reconciler.apply(any())).thenAnswer(invocation -> {
            started.countDown();
            return shared;
        });
        AtomicReference<Thread> caller = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (LocalRoutingConvergence convergence = new LocalRoutingConvergence(reconciler, 1)) {
            Future<Throwable> result = callers.submit(() -> {
                caller.set(Thread.currentThread());
                Throwable failure = catchThrowable(() -> convergence.applyAndAwait(desired(), Duration.ofSeconds(5)));
                interrupted.set(Thread.interrupted());
                return failure;
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.get().interrupt();
            assertThat(result.get(1, TimeUnit.SECONDS))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("interrupted");
            assertThat(interrupted).isTrue();
            assertThat(shared.isCancelled()).isFalse();
            assertThatThrownBy(() -> convergence.applyAndAwait(desired(), TIMEOUT)).hasMessageContaining(
                    "capacity is exhausted");
        } finally {
            shared.complete(null);
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shutdownRejectsNewLookupsWithoutWaitingForSharedConvergence() throws Exception {
        LocalRoutingReconciler reconciler = mock(LocalRoutingReconciler.class);
        CompletableFuture<Void> shared = new CompletableFuture<>();
        when(reconciler.apply(any())).thenReturn(shared);
        LocalRoutingConvergence convergence = new LocalRoutingConvergence(reconciler, 2);
        ExecutorService lifecycle = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> convergence.applyAndAwait(desired(), TIMEOUT)).hasMessageContaining("timed out");
            lifecycle.submit(convergence::close).get(1, TimeUnit.SECONDS);
            AtomicBoolean resolved = new AtomicBoolean();
            assertThatThrownBy(() -> convergence.resolveAndAwait(ALIAS, () -> {
                resolved.set(true);
                return Optional.empty();
            }, TIMEOUT))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("could not start");
            assertThat(resolved).isFalse();
            assertThat(shared.isDone()).isFalse();
            shared.complete(null);
        } finally {
            shared.complete(null);
            convergence.close();
            lifecycle.shutdownNow();
            assertThat(lifecycle.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownDrainsBlockedLookupAndPreservesInterruption(boolean initiallyInterrupted) throws Exception {
        LocalRoutingConvergence convergence = new LocalRoutingConvergence(mock(LocalRoutingReconciler.class), 2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean lookupFinished = new AtomicBoolean();
        AtomicReference<Thread> closingThread = new AtomicReference<>();
        ExecutorService lifecycle = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> convergence.resolveAndAwait(ALIAS, () -> {
                started.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Shutdown interrupted the admitted lookup", failure);
                }
                lookupFinished.set(true);
                return Optional.empty();
            }, TIMEOUT))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("timed out");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> closed = lifecycle.submit(() -> {
                closingThread.set(Thread.currentThread());
                if (initiallyInterrupted) {
                    Thread.currentThread().interrupt();
                }
                convergence.close();
                return Thread.interrupted();
            });
            Awaitility
                .await()
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThatThrownBy(() -> convergence.resolveAndAwait(ALIAS, Optional::empty,
                        TIMEOUT))
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("could not start"));
            if (!initiallyInterrupted) {
                closingThread.get().interrupt();
            }
            assertThatThrownBy(() -> closed.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(lookupFinished).isFalse();

            release.countDown();
            assertThat(closed.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(lookupFinished).isTrue();
        } finally {
            release.countDown();
            convergence.close();
            lifecycle.shutdownNow();
            assertThat(lifecycle.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void workerCallbackCannotCloseItsOwnExecutor() {
        LocalRoutingConvergence convergence = new LocalRoutingConvergence(mock(LocalRoutingReconciler.class), 1);
        convergence.resolveAndAwait(ALIAS, () -> {
            assertThatThrownBy(convergence::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("own worker thread");
            return Optional.empty();
        }, Duration.ofSeconds(1));
        try {
            convergence.resolveAndAwait(ALIAS, Optional::empty, Duration.ofSeconds(1));
        } finally {
            convergence.close();
        }
    }
}
