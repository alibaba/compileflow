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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisher;
import com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableRetentionWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.springframework.context.SmartLifecycle;

/**
 * Owns adaptive work acquisition, bounded execution slots, and staggered maintenance.
 *
 * <p>Execution concurrency is capacity, not poller count. A busy work kind expands to its configured
 * capacity; an idle kind converges to one jittered probe with exponential backoff.
 *
 * @author yusu
 */
public final class DurableWorkerCoordinator implements SmartLifecycle {
    private static final Duration BACKLOG_YIELD = Duration.ofMillis(1);
    private static final Duration MAXIMUM_IDLE_BACKOFF_CAP = Duration.ofSeconds(5);
    private final DurableStore store;
    private final DurableProcessRuntimeLoadWorker programLoadWorker;
    private final DurableTurnWorker turnWorker;
    private final DurableEffectWorker effectWorker;
    private final Optional<DurableOutboxPublisher> outboxPublisher;
    private final Optional<DurableRetentionWorker> retentionWorker;
    private final Duration idlePollDelay;
    private final Duration maintenanceInterval;
    private final Duration retentionInterval;
    private final int maintenanceBatchSize;
    private final int turnConcurrency;
    private final int effectConcurrency;
    private final int outboxConcurrency;
    private final int executorThreadCount;
    private final DurableRuntimeMetrics metrics;
    private final DurableWorkerHealth workerHealth;
    private volatile boolean running;
    private volatile ScheduledExecutorService executor;
    private volatile ScheduledExecutorService drainingExecutor;

    public DurableWorkerCoordinator(DurableStore store, DurableProcessRuntimeLoadWorker programLoadWorker,
            DurableTurnWorker turnWorker, DurableEffectWorker effectWorker,
            Optional<DurableOutboxPublisher> outboxPublisher, Optional<DurableRetentionWorker> retentionWorker,
            Duration idlePollDelay, Duration maintenanceInterval, Duration retentionInterval, int maintenanceBatchSize,
            int turnConcurrency, int effectConcurrency, int outboxConcurrency, DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.programLoadWorker = Objects.requireNonNull(programLoadWorker, "programLoadWorker");
        this.turnWorker = Objects.requireNonNull(turnWorker, "turnWorker");
        this.effectWorker = Objects.requireNonNull(effectWorker, "effectWorker");
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher");
        this.retentionWorker = Objects.requireNonNull(retentionWorker, "retentionWorker");
        this.idlePollDelay = requirePositiveNanos(idlePollDelay, "idlePollDelay");
        this.maintenanceInterval = requirePositiveNanos(maintenanceInterval, "maintenanceInterval");
        this.retentionInterval = requirePositiveNanos(retentionInterval, "retentionInterval");
        this.maintenanceBatchSize = requirePositive(maintenanceBatchSize, "maintenanceBatchSize");
        this.turnConcurrency = requirePositive(turnConcurrency, "turnConcurrency");
        this.effectConcurrency = requirePositive(effectConcurrency, "effectConcurrency");
        this.outboxConcurrency = outboxPublisher.isPresent()
                ? requirePositive(outboxConcurrency, "outboxConcurrency")
                : 0;
        try {
            executorThreadCount = Math.addExact(Math.addExact(this.turnConcurrency, this.effectConcurrency),
                    Math.addExact(this.outboxConcurrency, retentionWorker.isPresent() ? 2 : 1));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Combined worker concurrency is too large", overflow);
        }
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        ArrayList<Operation> enabledOperations =
                new ArrayList<>(List.of(Operation.TURN, Operation.EFFECT_DISPATCH, Operation.MAINTENANCE));
        if (outboxPublisher.isPresent()) {
            enabledOperations.add(Operation.OUTBOX);
        }
        if (retentionWorker.isPresent()) {
            enabledOperations.add(Operation.RETENTION);
        }
        this.workerHealth = new DurableWorkerHealth(enabledOperations);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requirePositiveNanos(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " must be representable in nanoseconds", overflow);
        }
        return duration;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (drainingExecutor != null) {
            if (!drainingExecutor.isTerminated()) {
                throw new IllegalStateException("Durable workers are still stopping");
            }
            drainingExecutor = null;
        }
        running = true;
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor created = null;
        try {
            created = new ScheduledThreadPoolExecutor(executorThreadCount, runnable -> {
                Thread thread = new Thread(runnable, "compileflow-durable-worker-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            created.setRemoveOnCancelPolicy(true);
            created.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            created.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            executor = created;

            ArrayList<AdaptiveLane> started = new ArrayList<>();
            started.add(new AdaptiveLane(Operation.TURN, turnWorker::runOnce, turnConcurrency));
            started.add(new AdaptiveLane(Operation.EFFECT_DISPATCH, effectWorker::runOnce, effectConcurrency));
            outboxPublisher.ifPresent(publisher -> started.add(
                    new AdaptiveLane(Operation.OUTBOX, publisher::runOnce, outboxConcurrency)));
            started.forEach(AdaptiveLane::start);

            scheduleMaintenance(randomInitialDelay(maintenanceInterval));
            retentionWorker.ifPresent(ignored -> scheduleRetention(randomInitialDelay(retentionInterval)));
        } catch (RuntimeException | Error startupFailure) {
            running = false;
            executor = null;
            if (created != null) {
                created.shutdownNow();
            }
            throw startupFailure;
        }
    }

    private void scheduleMaintenance(Duration delay) {
        schedule(() -> {
            if (!running) {
                return;
            }
            Duration nextDelay = maintenanceInterval;
            try {
                int loaded = programLoadWorker.loadDemanded(maintenanceBatchSize);
                int runs = store.reclaimExpiredRuns(maintenanceBatchSize);
                int effects = store.reclaimExpiredEffects(maintenanceBatchSize);
                int outbox = store.reclaimExpiredOutbox(maintenanceBatchSize);
                int waits = store.resolveDueWaits(maintenanceBatchSize);
                boolean progressed = loaded > 0 || runs > 0 || effects > 0 || outbox > 0 || waits > 0;
                if (loaded >= maintenanceBatchSize || runs >= maintenanceBatchSize || effects >= maintenanceBatchSize
                        || outbox >= maintenanceBatchSize || waits >= maintenanceBatchSize) {
                    nextDelay = BACKLOG_YIELD;
                }
                metrics.record(Operation.MAINTENANCE, Outcome.SUCCESS);
                workerHealth.successfulCycle(Operation.MAINTENANCE, progressed);
            } catch (RuntimeException | LinkageError failure) {
                metrics.record(Operation.MAINTENANCE, Outcome.FAULT);
                workerHealth.fault(Operation.MAINTENANCE, failure);
            } finally {
                if (running) {
                    scheduleMaintenance(nextDelay);
                }
            }
        }, delay);
    }

    private void scheduleRetention(Duration delay) {
        schedule(() -> {
            if (!running) {
                return;
            }
            Duration nextDelay = retentionInterval;
            try {
                DurableRetentionWorker.RetentionSweep sweep = retentionWorker.orElseThrow().runSlice();
                workerHealth.successfulCycle(Operation.RETENTION, sweep.progressed());
                nextDelay = sweep.saturated() ? BACKLOG_YIELD : retentionInterval;
            } catch (RuntimeException | LinkageError failure) {
                metrics.record(Operation.RETENTION, Outcome.FAULT);
                workerHealth.fault(Operation.RETENTION, failure);
            }
            if (running) {
                scheduleRetention(nextDelay);
            }
        }, delay);
    }

    private void schedule(Runnable task, Duration delay) {
        ScheduledExecutorService active = executor;
        if (active == null || !running) {
            return;
        }
        try {
            active.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException ignored) {
            // A concurrent stop owns shutdown; no work may be admitted afterwards.
        }
    }

    private static Duration randomInitialDelay(Duration interval) {
        long upper = interval.toNanos();
        return upper <= 1L ? Duration.ZERO : Duration.ofNanos(ThreadLocalRandom.current().nextLong(upper));
    }

    @Override
    public void stop() {
        awaitDrain(beginStop(), null);
    }

    @Override
    public void stop(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        ScheduledExecutorService active = beginStop();
        if (active == null) {
            callback.run();
            return;
        }
        Thread waiter = new Thread(() -> awaitDrain(active, callback), "compileflow-durable-worker-stop");
        waiter.setDaemon(true);
        waiter.start();
    }

    private synchronized ScheduledExecutorService beginStop() {
        if (!running) {
            return drainingExecutor;
        }
        running = false;
        ScheduledExecutorService active = executor;
        executor = null;
        if (active != null) {
            drainingExecutor = active;
            active.shutdown();
        }
        return active;
    }

    private void awaitDrain(ScheduledExecutorService active, Runnable callback) {
        boolean interrupted = false;
        try {
            if (active != null) {
                while (!active.isTerminated()) {
                    try {
                        active.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                synchronized (this) {
                    if (drainingExecutor == active) {
                        drainingExecutor = null;
                    }
                }
            }
        } finally {
            if (callback != null) {
                callback.run();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    DurableWorkerHealth.Snapshot workerHealthSnapshot() {
        return workerHealth.snapshot();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private final class AdaptiveLane {
        private final Operation operation;
        private final BooleanSupplier work;
        private final int capacity;
        private final long baseIdleNanos;
        private final long maxIdleNanos;
        private int activeTasks;
        private long nextIdleNanos;

        private AdaptiveLane(Operation operation, BooleanSupplier work, int capacity) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.work = Objects.requireNonNull(work, "work");
            this.capacity = capacity;
            this.baseIdleNanos = idlePollDelay.toNanos();
            long multiplied = baseIdleNanos > Long.MAX_VALUE / 32L ? Long.MAX_VALUE : baseIdleNanos * 32L;
            this.maxIdleNanos = Math.min(multiplied, Math.max(baseIdleNanos, MAXIMUM_IDLE_BACKOFF_CAP.toNanos()));
            this.nextIdleNanos = baseIdleNanos;
        }

        private synchronized void start() {
            scheduleLocked(0L);
        }

        private void attempt() {
            boolean progressed = false;
            if (running) {
                try {
                    progressed = work.getAsBoolean();
                    workerHealth.successfulCycle(operation, progressed);
                } catch (RuntimeException | LinkageError failure) {
                    metrics.record(operation, Outcome.FAULT);
                    workerHealth.fault(operation, failure);
                }
            }
            complete(progressed);
        }

        private synchronized void complete(boolean progressed) {
            activeTasks--;
            if (!running) {
                return;
            }
            if (progressed) {
                nextIdleNanos = baseIdleNanos;
                while (activeTasks < capacity) {
                    scheduleLocked(0L);
                }
                return;
            }
            if (activeTasks == 0) {
                long delay = equalJitter(nextIdleNanos);
                nextIdleNanos = Math.min(maxIdleNanos, saturatedDouble(nextIdleNanos));
                scheduleLocked(delay);
            }
        }

        private void scheduleLocked(long delayNanos) {
            ScheduledExecutorService active = executor;
            if (active == null || !running) {
                return;
            }
            try {
                activeTasks++;
                active.schedule(this::attempt, delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException ignored) {
                activeTasks--;
            }
        }

        private long equalJitter(long upperNanos) {
            long lower = Math.max(1L, upperNanos / 2L);
            return lower >= upperNanos ? upperNanos : ThreadLocalRandom.current().nextLong(lower, upperNanos + 1L);
        }

        private long saturatedDouble(long value) {
            return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
        }
    }
}
