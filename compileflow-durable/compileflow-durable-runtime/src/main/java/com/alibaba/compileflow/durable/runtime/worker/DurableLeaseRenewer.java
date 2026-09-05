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
package com.alibaba.compileflow.durable.runtime.worker;

import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.spi.store.DurableLeaseStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared node-local heartbeat coordinator for in-flight application execution.
 *
 * <p>One coordinator owns one lease policy. Run, Effect, and Outbox authorities use independent
 * lanes so a slow Store operation for one work kind cannot delay the other kinds.</p>
 *
 * @author yusu
 */
public final class DurableLeaseRenewer implements AutoCloseable {
    private static final int LANE_COUNT = 3;
    private static final int DEGRADED_AFTER_CONSECUTIVE_FAULTS = 2;
    private final Duration leaseDuration;
    private final ScheduledExecutorService scheduler;
    private final DurableRuntimeMetrics metrics;
    private final LeaseLane<DurableStore.RunLease> runs;
    private final LeaseLane<DurableStore.EffectLease> effects;
    private final LeaseLane<DurableStore.OutboxLease> outbox;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DurableLeaseRenewer(DurableLeaseStore store, Duration leaseDuration) {
        this(store, leaseDuration, new DurableRuntimeMetrics());
    }

    public DurableLeaseRenewer(DurableLeaseStore store, Duration leaseDuration, DurableRuntimeMetrics metrics) {
        DurableLeaseStore value = Objects.requireNonNull(store, "store");
        this.leaseDuration = WorkerDurationConstraints.requirePositive(leaseDuration, "leaseDuration",
                Duration.ofHours(1));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        AtomicInteger threadSequence = new AtomicInteger();
        this.scheduler = Executors.newScheduledThreadPool(LANE_COUNT, runnable -> {
            Thread thread =
                    new Thread(runnable, "compileflow-durable-lease-renewer-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.runs = new LeaseLane<>(value::renewRunLeases);
        this.effects = new LeaseLane<>(value::renewEffectLeases);
        this.outbox = new LeaseLane<>(value::renewOutboxLeases);
        long intervalMillis = Math.max(1L, this.leaseDuration.toMillis() / 3L);
        runs.start(intervalMillis);
        effects.start(intervalMillis);
        outbox.start(intervalMillis);
    }

    public Duration leaseDuration() {
        return leaseDuration;
    }

    /**
     * Returns bounded, payload-free operational state for the three renewal lanes.
     *
     * @return current renewal health
     */
    public RenewalHealth renewalHealth() {
        return new RenewalHealth(runs.health(), effects.health(), outbox.health());
    }

    public Handle trackRun(DurableStore.RunLease lease) {
        return track(runs, Objects.requireNonNull(lease, "lease"));
    }

    public Handle trackEffect(DurableStore.EffectLease lease) {
        return track(effects, Objects.requireNonNull(lease, "lease"));
    }

    public Handle trackOutbox(DurableStore.OutboxLease lease) {
        return track(outbox, Objects.requireNonNull(lease, "lease"));
    }

    private <T> Handle track(LeaseLane<T> lane, T authority) {
        if (closed.get()) {
            throw new IllegalStateException("DurableLeaseRenewer is closed");
        }
        Handle handle = lane.track(authority);
        if (closed.get()) {
            handle.close();
            throw new IllegalStateException("DurableLeaseRenewer is closed");
        }
        return handle;
    }

    private final class LeaseLane<T> {
        private final BatchRenewal<T> renewal;
        private final Set<T> authorities = new LinkedHashSet<>();
        private ScheduledFuture<?> future;
        private boolean stopped;
        private long activeSinceNanos;
        private long staleAfterNanos;
        private int consecutiveFaults;
        private long lastSuccessfulCycleMillis;
        private long lastSuccessfulCycleNanos;
        private long lastFaultMillis;
        private String lastFailureType;
        private long currentCycleStartedMillis;

        private LeaseLane(BatchRenewal<T> renewal) {
            this.renewal = renewal;
        }

        private synchronized void start(long intervalMillis) {
            staleAfterNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis * 2L);
            long minimumInitialDelay = Math.max(1L, intervalMillis / 2L);
            long initialDelay = minimumInitialDelay == intervalMillis
                    ? intervalMillis
                    : ThreadLocalRandom.current().nextLong(minimumInitialDelay, intervalMillis + 1L);
            future = scheduler.scheduleAtFixedRate(this::renewSnapshot, initialDelay, intervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        private synchronized Handle track(T authority) {
            if (stopped) {
                throw new IllegalStateException("DurableLeaseRenewer is closed");
            }
            if (authorities.isEmpty()) {
                consecutiveFaults = 0;
                activeSinceNanos = System.nanoTime();
                lastSuccessfulCycleNanos = 0L;
            }
            if (!authorities.add(authority)) {
                throw new IllegalStateException("Lease authority is already tracked");
            }
            return once(() -> {
                synchronized (this) {
                    authorities.remove(authority);
                }
            });
        }

        private void renewSnapshot() {
            List<T> snapshot;
            synchronized (this) {
                if (stopped || authorities.isEmpty()) {
                    return;
                }
                snapshot = List.copyOf(authorities);
                currentCycleStartedMillis = System.currentTimeMillis();
            }
            Throwable cycleFailure = null;
            for (int offset = 0; offset < snapshot.size(); offset += DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE) {
                int end = Math.min(snapshot.size(), offset + DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE);
                Set<T> batch = Set.copyOf(snapshot.subList(offset, end));
                Throwable batchFailure = renewBatch(batch);
                if (cycleFailure == null && batchFailure != null) {
                    cycleFailure = batchFailure;
                }
            }
            recordCycle(cycleFailure);
        }

        private Throwable renewBatch(Set<T> batch) {
            try {
                Set<T> renewed = Set.copyOf(Objects.requireNonNull(renewal.renew(batch, leaseDuration), "renewed"));
                if (!batch.containsAll(renewed)) {
                    throw new IllegalStateException("Lease Store returned an authority outside the renewal batch");
                }
                LinkedHashSet<T> lost = new LinkedHashSet<>(batch);
                lost.removeAll(renewed);
                if (!lost.isEmpty()) {
                    synchronized (this) {
                        authorities.removeAll(lost);
                    }
                }
                metrics.record(Operation.LEASE_RENEWAL, Outcome.SUCCESS, renewed.size());
                metrics.record(Operation.LEASE_RENEWAL, Outcome.LEASE_LOST, lost.size());
                return null;
            } catch (RuntimeException | LinkageError failure) {
                metrics.record(Operation.LEASE_RENEWAL, Outcome.FAULT, batch.size());
                // A Store fault is not proof of authority loss. Keep the batch registered so a
                // later heartbeat can recover before expiry; completion remains token-fenced.
                return failure;
            }
        }

        private synchronized void recordCycle(Throwable failure) {
            long now = System.currentTimeMillis();
            currentCycleStartedMillis = 0L;
            if (failure == null) {
                consecutiveFaults = 0;
                lastSuccessfulCycleMillis = now;
                lastSuccessfulCycleNanos = System.nanoTime();
                return;
            }
            if (consecutiveFaults < Integer.MAX_VALUE) {
                consecutiveFaults++;
            }
            lastFaultMillis = now;
            lastFailureType = failure.getClass().getName();
        }

        private synchronized LaneHealth health() {
            boolean stale = !authorities.isEmpty()
                    && System.nanoTime() - (lastSuccessfulCycleNanos == 0L ? activeSinceNanos : lastSuccessfulCycleNanos) >= staleAfterNanos;
            return new LaneHealth(authorities.size(), consecutiveFaults, stale, instant(currentCycleStartedMillis),
                    instant(lastSuccessfulCycleMillis), instant(lastFaultMillis), lastFailureType);
        }

        private synchronized void stop() {
            stopped = true;
            if (future != null) {
                future.cancel(false);
            }
            authorities.clear();
        }
    }

    private static Handle once(Runnable release) {
        AtomicBoolean released = new AtomicBoolean(false);
        return () -> {
            if (released.compareAndSet(false, true)) {
                release.run();
            }
        };
    }

    private static Instant instant(long epochMillis) {
        return epochMillis == 0L ? null : Instant.ofEpochMilli(epochMillis);
    }

    @FunctionalInterface
    private interface BatchRenewal<T> {
        Set<T> renew(Set<T> leases, Duration leaseDuration);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        runs.stop();
        effects.stop();
        outbox.stop();
        scheduler.shutdownNow();
    }

    @FunctionalInterface
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Current health of all bounded renewal lanes.
     *
     * @param run Run-authority lane
     * @param effect Effect-authority lane
     * @param outbox Outbox-authority lane
     */
    public record RenewalHealth(LaneHealth run, LaneHealth effect, LaneHealth outbox) {
        public RenewalHealth {
            run = Objects.requireNonNull(run, "run");
            effect = Objects.requireNonNull(effect, "effect");
            outbox = Objects.requireNonNull(outbox, "outbox");
        }

        /**
         * Reports whether any active lane is stale or has reached the consecutive-fault threshold.
         *
         * @return {@code true} when renewal machinery is degraded
         */
        public boolean degraded() {
            return run.degraded() || effect.degraded() || outbox.degraded();
        }
    }

    /**
     * Payload-free health facts for one renewal lane.
     *
     * @param activeAuthorities authorities currently tracked by the lane
     * @param consecutiveFaults consecutive cycles containing at least one failed batch
     * @param stale whether an active lane has gone two renewal intervals without a successful cycle
     * @param currentCycleStarted current in-progress cycle start, or {@code null}
     * @param lastSuccessfulCycle last completely successful non-empty cycle, or {@code null}
     * @param lastFault last failed cycle, or {@code null}
     * @param lastFailureType sanitized failure class name, or {@code null}
     */
    public record LaneHealth(int activeAuthorities, int consecutiveFaults, boolean stale, Instant currentCycleStarted,
            Instant lastSuccessfulCycle, Instant lastFault, String lastFailureType) {
        /**
         * Reports whether this active lane is stale or has reached the consecutive-fault threshold.
         *
         * @return {@code true} when the lane is degraded
         */
        public boolean degraded() {
            return activeAuthorities > 0 && (stale || consecutiveFaults >= DEGRADED_AFTER_CONSECUTIVE_FAULTS);
        }
    }
}
