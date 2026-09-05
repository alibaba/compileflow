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
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes truthful outbox health and explicit dead-letter recovery operations.
 *
 * @author yusu
 */
public final class RoutingOutboxAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingOutboxAdminService.class);
    private final RoutingOutboxRepository outboxRepository;
    private final RoutingOutboxScheduler scheduler;

    public RoutingOutboxAdminService(RoutingOutboxRepository outboxRepository, RoutingOutboxScheduler scheduler) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public int requeueDeadLetters() {
        int requeued = outboxRepository.requeueFailed();
        if (requeued > 0) {
            LOGGER.info("Re-queued {} dead-letter outbox records for retry", requeued);
        }
        return requeued;
    }

    public PipelineHealth getHealth() {
        try {
            return new PipelineHealth(outboxRepository.countByStatus(RoutingOutboxRecord.Status.PENDING),
                    outboxRepository.countByStatus(RoutingOutboxRecord.Status.PROCESSING),
                    outboxRepository.countExpiredClaims(),
                    outboxRepository.countByStatus(RoutingOutboxRecord.Status.FAILED), scheduler.isRunning(), true);
        } catch (Exception failure) {
            LOGGER.warn("Failed to query outbox health counts", failure);
            return new PipelineHealth(0L, 0L, 0L, 0L, scheduler.isRunning(), false);
        }
    }

    public static final class PipelineHealth {
        private final long pendingCount;
        private final long processingCount;
        private final long expiredClaimCount;
        private final long failedCount;
        private final boolean dispatcherRunning;
        private final boolean outboxStateAvailable;

        public PipelineHealth(long pendingCount, long processingCount, long expiredClaimCount, long failedCount,
                boolean dispatcherRunning, boolean outboxStateAvailable) {
            this.pendingCount = pendingCount;
            this.processingCount = processingCount;
            this.expiredClaimCount = expiredClaimCount;
            this.failedCount = failedCount;
            this.dispatcherRunning = dispatcherRunning;
            this.outboxStateAvailable = outboxStateAvailable;
        }

        public long getPendingCount() {
            return pendingCount;
        }

        public long getProcessingCount() {
            return processingCount;
        }

        public long getExpiredClaimCount() {
            return expiredClaimCount;
        }

        public long getFailedCount() {
            return failedCount;
        }

        public boolean isDispatcherRunning() {
            return dispatcherRunning;
        }

        public boolean isOutboxStateAvailable() {
            return outboxStateAvailable;
        }

        public String getStatus() {
            if (!outboxStateAvailable || !dispatcherRunning) {
                return "DOWN";
            }
            if (failedCount > 0 || expiredClaimCount > 0) {
                return "DEGRADED";
            }
            return "UP";
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "PipelineHealth{status=%s, pending=%d, processing=%d, expiredClaims=%d, failed=%d, "
                    + "dispatcher=%s, outboxStateAvailable=%s}", getStatus(), pendingCount, processingCount,
                    expiredClaimCount, failedCount, dispatcherRunning, outboxStateAvailable);
        }
    }
}
