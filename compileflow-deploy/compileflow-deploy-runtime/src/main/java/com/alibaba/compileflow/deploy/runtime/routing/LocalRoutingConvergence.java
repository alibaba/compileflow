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

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Bounds caller waits and background admission for embedded local-ready convergence.
 *
 * @author yusu
 */
public final class LocalRoutingConvergence implements AutoCloseable {
    private final LocalRoutingReconciler reconciler;
    private final ThreadPoolExecutor executor;
    private final Semaphore admission;
    private final ThreadLocal<Boolean> workerThread = new ThreadLocal<>();

    public LocalRoutingConvergence(LocalRoutingReconciler reconciler, int concurrency) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive");
        }
        admission = new Semaphore(concurrency);
        AtomicInteger sequence = new AtomicInteger();
        // A completed operation can admit its successor before its worker has returned.
        executor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency),
                runnable -> {
                    Thread thread =
                    new Thread(() -> {
                        workerThread.set(true);
                        try {
                            runnable.run();
                        } finally {
                            workerThread.remove();
                        }
                    }, "compileflow-deploy-convergence-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Applies desired state within the caller's wait budget, including synchronous installation.
     *
     * @param change authoritative desired state
     * @param timeout maximum caller wait; admitted work may continue afterwards
     */
    public void applyAndAwait(DesiredRoutingState change, Duration timeout) {
        DesiredRoutingState desired = Objects.requireNonNull(change, "change");
        resolveAndAwait(ProcessRef.alias(desired.getNamespace(), desired.getCode(), desired.getAlias()),
                () -> Optional.of(desired), timeout);
    }

    /**
     * Resolves and applies an alias off the caller thread under one wait budget.
     *
     * @param alias requested alias, also used in failure diagnostics
     * @param resolve authoritative lookup; an empty result needs no convergence
     * @param timeout maximum caller wait; timeout or interruption does not cancel shared work
     */
    public void resolveAndAwait(ProcessRef.Alias alias, Supplier<Optional<DesiredRoutingState>> resolve,
            Duration timeout) {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(resolve, "resolve");
        Duration wait = Objects.requireNonNull(timeout, "timeout");
        long timeoutMillis;
        try {
            timeoutMillis = wait.toMillis();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("timeout must be representable in milliseconds", failure);
        }
        if (timeoutMillis <= 0 || !wait.equals(Duration.ofMillis(timeoutMillis))) {
            throw new IllegalArgumentException("timeout must be a positive whole-millisecond duration");
        }

        long started = System.nanoTime();
        if (!admission.tryAcquire()) {
            throw failure(alias, "Local-ready convergence capacity is exhausted", null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> start(alias, resolve, completion));
        } catch (RuntimeException | Error rejected) {
            admission.release();
            if (rejected instanceof Error fatal) {
                throw fatal;
            }
            throw failure(alias, "Local-ready convergence could not start", rejected);
        }
        try {
            long remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis) - (System.nanoTime() - started);
            if (remaining <= 0L) {
                throw new TimeoutException();
            }
            completion.get(remaining, TimeUnit.NANOSECONDS);
        } catch (CancellationException cancelled) {
            throw failure(alias, "Local-ready convergence was cancelled", cancelled);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(alias, "Local-ready convergence was interrupted", interrupted);
        } catch (TimeoutException timedOut) {
            throw failure(alias, "Local-ready convergence timed out", timedOut);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            if (cause instanceof DeploymentException deploymentFailure) {
                throw deploymentFailure;
            }
            throw failure(alias, "Local-ready convergence failed", cause);
        }
    }

    private void start(ProcessRef.Alias alias, Supplier<Optional<DesiredRoutingState>> resolve,
            CompletableFuture<Void> completion) {
        CompletableFuture<Void> convergence;
        try {
            convergence = resolve
                .get()
                .map(reconciler::apply)
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        } catch (RuntimeException | Error failed) {
            admission.release();
            completion.completeExceptionally(
                    failed instanceof DeploymentException || failed instanceof Error
                    ? failed
                    : failure(alias, "Local-ready convergence could not start", failed));
            if (failed instanceof Error fatal) {
                throw fatal;
            }
            return;
        }
        // Do not occupy a worker waiting for installation callbacks or a shared future.
        convergence.whenComplete((ignored, failed) -> {
            admission.release();
            if (failed == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(failed);
            }
        });
    }

    private static DeploymentException failure(ProcessRef.Alias alias, String message, Throwable cause) {
        return DeploymentException
            .builder(DeploymentErrorCode.CONVERGENCE_FAILED, message, cause)
            .namespace(alias.namespace())
            .code(alias.code())
            .alias(alias.alias())
            .build();
    }

    /**
     * Stops new submissions and drains executing and queued worker tasks before dependencies can close.
     *
     * <p>The caller convergence timeout does not bound shutdown. Providers must bound their I/O;
     * a blocked lookup or synchronous installation delays shutdown until it returns. Interruptions
     * do not abandon the drain, and the interrupt status is restored before returning. Shared
     * convergence futures are not awaited once workers relinquish them: their completion or
     * cancellation belongs to the version runtime manager.</p>
     *
     * @throws IllegalStateException if invoked on this component's worker thread
     */
    @Override
    public void close() {
        if (Boolean.TRUE.equals(workerThread.get())) {
            throw new IllegalStateException("LocalRoutingConvergence cannot close from its own worker thread");
        }
        executor.shutdown();
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
