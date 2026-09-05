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

import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableOutboxDeliveryStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.util.Objects;
import java.util.Optional;

/**
 * One-at-a-time token-fenced publisher for the closed Kernel Integration Event set.
 *
 * @author yusu
 */
public final class DurableOutboxPublisher {
    private final DurableOutboxDeliveryStore store;
    private final DurableOutboxSink sink;
    private final DurableOutboxPublisherOptions options;
    private final DurableLeaseRenewer leases;
    private final DurableRuntimeMetrics metrics;
    private final DurableKernelJsonCodec json = new DurableKernelJsonCodec();

    public DurableOutboxPublisher(DurableOutboxDeliveryStore store, DurableOutboxSink sink,
            DurableOutboxPublisherOptions options, DurableLeaseRenewer leases) {
        this(store, sink, options, leases, new DurableRuntimeMetrics());
    }

    public DurableOutboxPublisher(DurableOutboxDeliveryStore store, DurableOutboxSink sink,
            DurableOutboxPublisherOptions options, DurableLeaseRenewer leases, DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.options = Objects.requireNonNull(options, "options");
        this.leases = Objects.requireNonNull(leases, "leases");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public boolean runOnce() {
        Optional<DurableStore.OutboxClaim> claimed =
                store.claimOutbox(new DurableStore.OutboxClaimRequest(options.workerId(), leases.leaseDuration()));
        if (claimed.isEmpty()) {
            return false;
        }
        metrics.record(Operation.OUTBOX, Outcome.CLAIMED);
        DurableStore.OutboxClaim claim = claimed.orElseThrow();
        DurableLeaseRenewer.Handle heartbeat = leases.trackOutbox(claim.lease());
        try {
            sink.deliver(
                    new DurableOutboxSink.OutboundEvent(claim.lease().eventId(), claim.runId(),
                            claim.rootProcess().namespace(), claim.rootProcess().processCode(),
                            claim.rootProcess().processVersion(), claim.eventType(), claim.occurrenceId(),
                            json.decode(claim.payload().payload())));
            record(store.completeOutbox(claim.lease()), Outcome.SUCCESS);
        } catch (Exception | LinkageError failure) {
            if (claim.attempt() >= options.maxAttempts() && !"WAIT_COMMITTED".equals(claim.eventType())) {
                record(store.abandonOutbox(claim.lease()), Outcome.ABANDONED);
            } else {
                record(store.retryOutbox(claim.lease(), options.retryDelay(claim.attempt())), Outcome.RETRY_SCHEDULED);
            }
        } finally {
            heartbeat.close();
        }
        return true;
    }

    private void record(boolean applied, Outcome outcome) {
        metrics.record(Operation.OUTBOX, applied ? outcome : Outcome.LEASE_LOST);
    }
}
