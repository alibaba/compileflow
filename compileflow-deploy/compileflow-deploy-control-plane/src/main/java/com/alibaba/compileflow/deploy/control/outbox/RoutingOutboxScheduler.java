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
package com.alibaba.compileflow.deploy.control.outbox;

import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules periodic routing-outbox dispatch and retention cleanup.
 *
 * @author yusu
 */
public final class RoutingOutboxScheduler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingOutboxScheduler.class);
    /**
     * Run retention cleanup every 5 minutes, independently of dispatch cadence.
     */
    private static final Duration RETENTION_CLEANUP_INTERVAL = Duration.ofMinutes(5);
    private static final int RETENTION_CLEANUP_SLICE_SIZE = 1_000;
    private static final long BACKLOG_YIELD_MILLIS = 1L;
    private static final long STOP_TIMEOUT_MILLIS = 5_000L;
    private static final long CLOSE_TIMEOUT_MILLIS = 10_000L;
    private final RoutingOutboxDispatcher dispatcher;
    private final long dispatchIntervalMs;
    private final long retentionCleanupIntervalMs;
    private final ScheduledExecutorService executor;
    /**
     * Optional outbox repository for retention cleanup of DELIVERED records.
     */
    private final RoutingOutboxStore outboxRepository;
    /**
     * Retention window in ms; DELIVERED records older than this are deleted periodically.
     */
    private final long retentionMs;
    private volatile ScheduledFuture<?> dispatchFuture;
    private volatile ScheduledFuture<?> cleanupFuture;
    private long dispatchGeneration;
    private long cleanupGeneration;
    private int activeTasks;
    private boolean dispatchActive;
    private boolean dispatchRequested;
    private volatile boolean running;
    private volatile boolean closed;

    public RoutingOutboxScheduler(RoutingOutboxDispatcher dispatcher, Duration dispatchInterval) {
        this(dispatcher, dispatchInterval, null, null, RETENTION_CLEANUP_INTERVAL);
    }

    public RoutingOutboxScheduler(RoutingOutboxDispatcher dispatcher, Duration dispatchInterval,
            RoutingOutboxStore outboxRepository, Duration retention) {
        this(dispatcher, dispatchInterval, Objects.requireNonNull(outboxRepository, "outboxRepository"),
                Objects.requireNonNull(retention, "retention"), RETENTION_CLEANUP_INTERVAL);
    }

    RoutingOutboxScheduler(RoutingOutboxDispatcher dispatcher, Duration dispatchInterval,
            RoutingOutboxStore outboxRepository, Duration retention, Duration retentionCleanupInterval) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.dispatchIntervalMs = requirePositiveMillis(dispatchInterval, "dispatchInterval");
        this.retentionCleanupIntervalMs = requirePositiveMillis(retentionCleanupInterval, "retentionCleanupInterval");
        this.outboxRepository = outboxRepository;
        this.retentionMs = outboxRepository == null ? 0L : requirePositiveMillis(retention, "retention");
        ScheduledThreadPoolExecutor created = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "compileflow-outbox-dispatcher");
            t.setDaemon(true);
            return t;
        });
        created.setRemoveOnCancelPolicy(true);
        created.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        created.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        this.executor = created;
    }

    private static long requirePositiveMillis(Duration duration, String name) {
        Duration value = Objects.requireNonNull(duration, name);
        long millis;
        try {
            millis = value.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must be a positive whole-millisecond duration", exception);
        }
        if (millis <= 0 || !value.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(name + " must be a positive whole-millisecond duration");
        }
        return millis;
    }

    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("RoutingOutboxScheduler is closed");
        }
        if (running) {
            return;
        }
        if (activeTasks > 0) {
            throw new IllegalStateException("RoutingOutboxScheduler is still stopping");
        }
        running = true;
        try {
            scheduleDispatch(randomInitialDelay(dispatchIntervalMs));
            if (outboxRepository != null) {
                scheduleCleanup(randomInitialDelay(retentionCleanupIntervalMs));
            }
        } catch (RuntimeException failure) {
            running = false;
            cancelScheduledTasks();
            throw failure;
        }
        LOGGER.info("RoutingOutboxScheduler started: dispatchIntervalMs={}, retentionMs={}", dispatchIntervalMs,
                retentionMs);
    }

    private void runDispatch(long generation) {
        synchronized (this) {
            if (!running || generation != dispatchGeneration) {
                return;
            }
            dispatchFuture = null;
            dispatchActive = true;
            activeTasks++;
        }
        boolean saturated = false;
        try {
            RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();
            saturated = result.saturated();
            if (result.delivered() > 0) {
                LOGGER.debug("Outbox dispatch cycle delivered {} records", result.delivered());
            }
        } catch (Exception failure) {
            LOGGER.error("Unhandled error in outbox dispatch cycle", failure);
        } finally {
            synchronized (this) {
                dispatchActive = false;
                activeTasks--;
                notifyAll();
                if (running) {
                    boolean continueImmediately = saturated || dispatchRequested;
                    dispatchRequested = false;
                    scheduleDispatch(continueImmediately ? BACKLOG_YIELD_MILLIS : dispatchIntervalMs);
                }
            }
        }
    }

    private void runCleanup(long generation) {
        synchronized (this) {
            if (!running || generation != cleanupGeneration) {
                return;
            }
            cleanupFuture = null;
            activeTasks++;
        }
        boolean saturated = false;
        try {
            int deleted = outboxRepository.deleteDeliveredOlderThan(retentionMs, RETENTION_CLEANUP_SLICE_SIZE);
            saturated = deleted >= RETENTION_CLEANUP_SLICE_SIZE;
            if (deleted > 0) {
                LOGGER.info("Outbox retention cleanup deleted {} DELIVERED records older than {}ms", deleted,
                        retentionMs);
            }
        } catch (Exception failure) {
            LOGGER.warn("Outbox retention cleanup failed (will retry next cycle)", failure);
        } finally {
            synchronized (this) {
                activeTasks--;
                notifyAll();
                if (running) {
                    scheduleCleanup(saturated ? BACKLOG_YIELD_MILLIS : retentionCleanupIntervalMs);
                }
            }
        }
    }

    private void scheduleDispatch(long delayMillis) {
        try {
            long generation = ++dispatchGeneration;
            dispatchFuture = executor.schedule(() -> runDispatch(generation), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException failure) {
            if (running) {
                throw failure;
            }
        }
    }

    private void scheduleCleanup(long delayMillis) {
        try {
            long generation = ++cleanupGeneration;
            cleanupFuture = executor.schedule(() -> runCleanup(generation), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException failure) {
            if (running) {
                throw failure;
            }
        }
    }

    private static long randomInitialDelay(long intervalMillis) {
        return intervalMillis <= 1L ? 0L : ThreadLocalRandom.current().nextLong(intervalMillis);
    }

    public void stop() {
        stopBefore(deadlineAfterMillis(STOP_TIMEOUT_MILLIS));
    }

    private void stopBefore(long deadlineNanos) {
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            cancelScheduledTasks();
        }
        awaitScheduledTasks(deadlineNanos);
        if (!dispatcher.awaitIdle(remainingMillis(deadlineNanos))) {
            LOGGER.warn("Outbox dispatcher did not quiesce within the available shutdown budget; continuing pause.");
        }
        LOGGER.info("RoutingOutboxScheduler stopped.");
    }

    /**
     * Reports whether periodic dispatch is currently scheduled.
     *
     * @return {@code true} only between a successful {@link #start()} and {@link #stop()}
     */
    public boolean isRunning() {
        return running && !closed;
    }

    /**
     * Requests an immediate dispatch cycle without waiting for the periodic cadence.
     * Concurrent requests are coalesced; the periodic schedule remains the recovery path.
     */
    public synchronized void requestDispatch() {
        if (!isRunning()) {
            return;
        }
        if (dispatchActive) {
            dispatchRequested = true;
            return;
        }
        if (dispatchFuture != null) {
            dispatchFuture.cancel(false);
            dispatchFuture = null;
        }
        scheduleDispatch(0L);
    }

    private void cancelScheduledTasks() {
        dispatchGeneration++;
        cleanupGeneration++;
        if (dispatchFuture != null) {
            dispatchFuture.cancel(false);
            dispatchFuture = null;
        }
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
        }
    }

    private void awaitScheduledTasks(long deadlineNanos) {
        boolean interrupted = false;
        synchronized (this) {
            while (activeTasks > 0) {
                long remaining = remainingMillis(deadlineNanos);
                if (remaining == 0L) {
                    LOGGER.warn("Outbox scheduler tasks did not quiesce within the available shutdown budget.");
                    break;
                }
                try {
                    wait(remaining);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        long deadlineNanos = deadlineAfterMillis(CLOSE_TIMEOUT_MILLIS);
        synchronized (this) {
            if (closed) {
                return;
            }
            running = false;
            cancelScheduledTasks();
            closed = true;
        }
        dispatcher.stop();
        if (!dispatcher.awaitIdle(remainingMillis(deadlineNanos))) {
            LOGGER.warn("Outbox dispatcher did not terminate within the shared shutdown budget.");
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Outbox scheduler executor did not terminate within the shared budget; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        LOGGER.info("RoutingOutboxScheduler closed.");
    }

    private static long deadlineAfterMillis(long timeoutMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    private static long remainingMillis(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return 0L;
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }
}
