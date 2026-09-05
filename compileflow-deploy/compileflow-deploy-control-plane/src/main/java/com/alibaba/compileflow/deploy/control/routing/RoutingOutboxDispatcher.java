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
package com.alibaba.compileflow.deploy.control.routing;

import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims and delivers pending routing-outbox records to a delivery target.
 *
 * @author yusu
 */
public final class RoutingOutboxDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingOutboxDispatcher.class);
    private final RoutingOutboxRepository outboxRepository;
    private final RoutingStateDeliveryTarget deliveryTarget;
    private final RoutingOutboxDispatchPolicy policy;
    private final String claimantId;
    /**
     * Guards against overlapping dispatch cycles triggered by a slow previous cycle.
     */
    private final AtomicBoolean dispatching = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();
    private volatile boolean stopped = false;

    public RoutingOutboxDispatcher(RoutingOutboxRepository outboxRepository, RoutingStateDeliveryTarget deliveryTarget,
            RoutingOutboxDispatchPolicy policy) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.deliveryTarget = Objects.requireNonNull(deliveryTarget, "deliveryTarget");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.claimantId = "outbox-" + UUID.randomUUID();
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    public DispatchResult dispatch() {
        if (stopped) {
            return DispatchResult.EMPTY;
        }
        if (!dispatching.compareAndSet(false, true)) {
            LOGGER.warn("Skipping dispatch cycle: previous cycle still in progress");
            return DispatchResult.EMPTY;
        }
        try {
            return doDispatch();
        } finally {
            synchronized (idleMonitor) {
                dispatching.set(false);
                idleMonitor.notifyAll();
            }
        }
    }

    private DispatchResult doDispatch() {
        int claimedCount = 0;
        int delivered = 0;
        for (int processed = 0; processed < policy.batchSize(); processed++) {
            if (stopped) {
                break;
            }
            List<RoutingOutboxRecord> claimed = outboxRepository.claimPending(1, claimantId, policy.claimLeaseMs());
            if (claimed.isEmpty()) {
                break;
            }
            if (claimed.size() != 1) {
                throw new IllegalStateException(
                        "Outbox repository returned more records than the requested claim limit");
            }
            claimedCount++;
            delivered += dispatchClaimed(claimed.get(0));
        }
        return new DispatchResult(claimedCount, delivered, claimedCount >= policy.batchSize());
    }

    private int dispatchClaimed(RoutingOutboxRecord record) {
        try {
            deliver(record);
        } catch (Exception failure) {
            markDeliveryFailed(record, failure);
            return 0;
        }
        try {
            int transitioned = outboxRepository.markDelivered(record.getId(), record.getLeaseToken());
            if (transitioned == 1) {
                return 1;
            }
            LOGGER.warn("Delivered outbox payload after its lease expired; result was fenced: id={}", record.getId());
        } catch (Exception markFailure) {
            // The channel accepted the payload, but the row remains PROCESSING until its
            // lease expires. A later claim delivers it again, preserving at-least-once.
            LOGGER.warn("Delivered to channel but failed to mark as DELIVERED (will retry): "
                    + "id={} eventType={} ns={} code={} failureType={}", record.getId(), record.getEventType(),
                    record.getNamespace(), record.getCode(), markFailure.getClass().getName());
        }
        return 0;
    }

    private void markDeliveryFailed(RoutingOutboxRecord record, Exception failure) {
        String failureType = failure.getClass().getName();
        String error = "Delivery failed: " + failureType;
        int newAttemptCount = record.getAttemptCount() + 1;
        int transitioned;
        try {
            transitioned = outboxRepository.markFailed(record.getId(), record.getLeaseToken(), error,
                    policy.maxAttempts(), policy.retryDelayMs(record.getAttemptCount()));
        } catch (Exception markFailure) {
            LOGGER.warn("Failed to mark outbox record as failed: id={} failureType={}", record.getId(),
                    markFailure.getClass().getName());
            return;
        }
        if (transitioned == 0) {
            LOGGER.warn("Discarded stale outbox failure transition: id={}", record.getId());
            return;
        }
        if (newAttemptCount >= policy.maxAttempts()) {
            LOGGER.error("[DEAD-LETTER] Routing outbox record exhausted all {} retry attempts and will no longer "
                    + "be retried: id={} eventType={} ns={} code={} alias={} failureType={}", policy.maxAttempts(),
                    record.getId(), record.getEventType(), record.getNamespace(), record.getCode(), record.getAlias(),
                    failureType);
        } else {
            LOGGER.warn("Routing outbox delivery failed: id={} eventType={} code={} attempt={}/{}, failureType={}",
                    record.getId(), record.getEventType(), record.getCode(), newAttemptCount, policy.maxAttempts(),
                    failureType);
        }
    }

    private void deliver(RoutingOutboxRecord record) throws Exception {
        String eventType = record.getEventType();
        if (RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE.equals(eventType)) {
            // Replay the transactionally captured complete resource without re-deriving it.
            deliveryTarget.deliver(record.getRoutingKey(), record.getPayload());
        } else {
            // Throw instead of silently marking DELIVERED so the fenced failure transition
            // returns the record to PENDING with backoff. Unknown event types must not be consumed;
            // they should be investigated by an operator.
            throw new IllegalArgumentException(
                    "Unknown outbox event type: id=" + record.getId() + " type=" + eventType
                    + ". Record will be retried. Investigate the publisher that wrote this record.");
        }
    }

    public boolean awaitIdle(long timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must be >= 0, got " + timeoutMs);
        }
        long startedAtNanos = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (idleMonitor) {
            while (dispatching.get()) {
                long remainingNanos = timeoutNanos - (System.nanoTime() - startedAtNanos);
                if (remainingNanos <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(idleMonitor, remainingNanos);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    public record DispatchResult(int claimed, int delivered, boolean saturated) {
        private static final DispatchResult EMPTY = new DispatchResult(0, 0, false);

        public DispatchResult {
            if (claimed < 0 || delivered < 0 || delivered > claimed) {
                throw new IllegalArgumentException("dispatch counts must satisfy 0 <= delivered <= claimed");
            }
        }
    }
}
