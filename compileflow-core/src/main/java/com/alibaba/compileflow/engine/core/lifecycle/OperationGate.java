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
package com.alibaba.compileflow.engine.core.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

/**
 * Lock-free admission and bounded drain gate for a closeable component.
 *
 * <p>The steady-state path uses one shutdown flag plus cache-line-isolated counters. Each thread is
 * assigned a stable counter stripe, so admission and completion update the same non-negative
 * counter. Admission increments before rechecking the flag: a request racing with shutdown either
 * remains visible to the post-cutoff drain scan or rolls back before touching protected resources.
 * Unlike a {@code LongAdder} snapshot, scanning stable non-negative stripes after the admission
 * cutoff can prove that no admitted operation remains. Shutdown is rare and uses low-frequency
 * polling, avoiding notification locks and a globally contended request counter.
 *
 * @author yusu
 */
public final class OperationGate {
    private static final long DRAIN_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final int CACHE_LINE_LONGS = 16;
    private static final int COUNTER_STRIPES = counterStripes();
    private static final int COUNTER_MASK = COUNTER_STRIPES - 1;
    private final String componentName;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicInteger nextStripe = new AtomicInteger();
    private final AtomicLongArray activeOperations = new AtomicLongArray(COUNTER_STRIPES * CACHE_LINE_LONGS);
    private final ThreadLocal<LocalState> localState = ThreadLocal.withInitial(this::newLocalState);
    private final CountDownLatch closeCompleted = new CountDownLatch(1);
    private volatile boolean cleanupStarted;

    public OperationGate(String componentName) {
        this.componentName = Objects.requireNonNull(componentName, "componentName");
    }

    /**
     * Admits an operation or fails after shutdown starts.
     */
    public void enter() {
        if (!tryEnter()) {
            throw closedException();
        }
    }

    /**
     * Attempts admission without blocking.
     *
     * @return {@code false} after shutdown starts
     */
    public boolean tryEnter() {
        LocalState state = localState.get();
        if (state.depth > 0) {
            if (cleanupStarted) {
                return false;
            }
            state.depth++;
            return true;
        }
        if (shutdown.get()) {
            return false;
        }
        activeOperations.incrementAndGet(state.counterIndex);
        if (shutdown.get()) {
            activeOperations.decrementAndGet(state.counterIndex);
            return false;
        }
        state.depth = 1;
        return true;
    }

    /**
     * Completes one operation admitted by {@link #enter()}.
     */
    public void exit() {
        LocalState state = localState.get();
        if (state.depth == 0) {
            throw new IllegalStateException(componentName + " operation gate exited without admission");
        }
        if (state.depth > 1) {
            state.depth--;
            return;
        }
        state.depth = 0;
        long remaining = activeOperations.decrementAndGet(state.counterIndex);
        if (remaining < 0L) {
            activeOperations.incrementAndGet(state.counterIndex);
            throw new IllegalStateException(componentName + " operation gate counter underflow");
        }
    }

    /**
     * Stops admission and waits up to the supplied timeout for admitted operations to drain.
     *
     * <p>The cleanup owner is returned even after timeout or interruption so the caller can enter
     * an explicit forced-cleanup phase. Other close callers wait for the owner only within the same
     * timeout and return {@link DrainResult#NOT_OWNER}.
     *
     * @param timeout non-negative drain timeout
     * @return drain result and cleanup ownership
     */
    public DrainResult beginCloseAndAwaitDrained(Duration timeout) {
        if (isEnteredByCurrentThread()) {
            throw new IllegalStateException(componentName + " cannot be closed from an active operation");
        }
        long timeoutNanos = toNanosSaturated(requireNonNegative(timeout));
        if (!shutdown.compareAndSet(false, true)) {
            awaitCloseCompletion(timeoutNanos);
            return DrainResult.NOT_OWNER;
        }

        DrainResult result = awaitDrain(timeoutNanos);
        cleanupStarted = true;
        return result;
    }

    /**
     * Publishes completion of cleanup and releases concurrent close callers.
     */
    public void finishClose() {
        if (!shutdown.get() || !cleanupStarted) {
            throw new IllegalStateException(componentName + " cleanup has not started");
        }
        closeCompleted.countDown();
    }

    public void ensureOpen() {
        if (shutdown.get() && (!isEnteredByCurrentThread() || cleanupStarted)) {
            throw closedException();
        }
    }

    public boolean isEnteredByCurrentThread() {
        return localState.get().depth > 0;
    }

    /**
     * Returns a shutdown-only diagnostic snapshot of admitted top-level operations.
     */
    public long activeOperations() {
        long total = 0L;
        for (int stripe = 0; stripe < COUNTER_STRIPES; stripe++) {
            total += activeOperations.get(counterIndex(stripe));
        }
        return total;
    }

    private DrainResult awaitDrain(long timeoutNanos) {
        long startedAt = System.nanoTime();
        while (!isDrained()) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return DrainResult.INTERRUPTED;
            }
            long remaining = remainingNanos(startedAt, timeoutNanos);
            if (remaining <= 0L) {
                return DrainResult.TIMED_OUT;
            }
            LockSupport.parkNanos(Math.min(DRAIN_POLL_NANOS, remaining));
        }
        return DrainResult.DRAINED;
    }

    private void awaitCloseCompletion(long timeoutNanos) {
        try {
            closeCompleted.await(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isDrained() {
        // Once shutdown is true, no new top-level operation can pass the second flag check. Every
        // operation that did pass keeps its increment on one stable stripe until exit, and every
        // stripe is non-negative. A scan of all zeroes therefore proves that no admitted operation
        // remains; pre-cutoff racers that have not passed the second check can only roll back.
        for (int stripe = 0; stripe < COUNTER_STRIPES; stripe++) {
            if (activeOperations.get(counterIndex(stripe)) != 0L) {
                return false;
            }
        }
        return true;
    }

    private LocalState newLocalState() {
        return new LocalState(counterIndex(nextStripe.getAndIncrement() & COUNTER_MASK));
    }

    private static int counterIndex(int stripe) {
        return stripe * CACHE_LINE_LONGS;
    }

    private static int counterStripes() {
        int target = Math.max(16, Math.min(256, Runtime.getRuntime().availableProcessors() * 4));
        return Integer.highestOneBit(target - 1) << 1;
    }

    private static long remainingNanos(long startedAt, long timeoutNanos) {
        long elapsed = System.nanoTime() - startedAt;
        return elapsed >= timeoutNanos ? 0L : timeoutNanos - elapsed;
    }

    private static Duration requireNonNegative(Duration timeout) {
        Duration value = Objects.requireNonNull(timeout, "timeout");
        if (value.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        return value;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private IllegalStateException closedException() {
        return new IllegalStateException(componentName + " is closed");
    }

    private static final class LocalState {
        private final int counterIndex;
        private int depth;

        private LocalState(int counterIndex) {
            this.counterIndex = counterIndex;
        }
    }

    /**
     * Result of a bounded drain attempt.
     */
    public enum DrainResult {
        NOT_OWNER,
        DRAINED,
        TIMED_OUT,
        INTERRUPTED;
        public boolean cleanupOwner() {
            return this != NOT_OWNER;
        }
    }
}
