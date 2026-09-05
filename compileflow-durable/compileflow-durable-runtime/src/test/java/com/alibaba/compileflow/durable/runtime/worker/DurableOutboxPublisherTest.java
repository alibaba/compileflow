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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableOutboxPublisherTest {
    private static final com.alibaba.compileflow.engine.ProcessRef.Version PROCESS =
            com.alibaba.compileflow.engine.ProcessRef.version("test", "outbox", "v1");
    private static final UUID PROCESS_ID = UUID.fromString("88d56466-3649-4cdf-a85e-5bf75c9c273c");
    private static final DurableStore.RunProcess RUN_PROCESS =
            new DurableStore.RunProcess(PROCESS_ID, PROCESS.namespace(), PROCESS.code(), PROCESS);
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final ProcessRunId RUN_ID = new ProcessRunId("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LEASE_TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void emptyQueueDoesNoWork() {
        CapturingStore store = new CapturingStore(Optional.empty());
        DurableOutboxPublisher publisher = publisher(store, event -> {
            throw new AssertionError("sink must not be called");
        }, 3);

        assertThat(publisher.runOnce()).isFalse();
        assertThat(store.terminalCall.get()).isNull();
    }

    @Test
    void successfulDeliveryCompletesTheClaimWithStableIdentity() {
        CapturingStore store = new CapturingStore(Optional.of(claim(1)));
        AtomicReference<DurableOutboxSink.OutboundEvent> delivered = new AtomicReference<>();
        DurableOutboxPublisher publisher = publisher(store, delivered::set, 3);

        assertThat(publisher.runOnce()).isTrue();
        assertThat(delivered.get().eventId()).isEqualTo(EVENT_ID);
        assertThat(delivered.get().runId()).isEqualTo(RUN_ID);
        assertThat(delivered.get().namespace()).isEqualTo(RUN_PROCESS.namespace());
        assertThat(delivered.get().processCode()).isEqualTo(RUN_PROCESS.processCode());
        assertThat(delivered.get().processVersion()).isEqualTo(PROCESS);
        assertThat(delivered.get().eventType()).isEqualTo("RUN_SUCCEEDED");
        assertThat(delivered.get().payload()).containsEntry("result", "ok");
        assertThat(store.terminalCall.get()).isEqualTo("complete");
        assertThat(store.lease.get()).isEqualTo(new DurableStore.OutboxLease(RUN_ID, EVENT_ID, LEASE_TOKEN));
    }

    @Test
    void deliveryFailureRetriesBeforeLimitAndAbandonsAtLimit() {
        CapturingStore retryStore = new CapturingStore(Optional.of(claim(2)));
        DurableOutboxPublisher retrying = publisher(retryStore, event -> {
            throw new IllegalStateException("unavailable");
        }, 3);

        assertThat(retrying.runOnce()).isTrue();
        assertThat(retryStore.terminalCall.get()).isEqualTo("retry");
        assertThat(retryStore.retryDelay.get()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(10));

        CapturingStore abandonStore = new CapturingStore(Optional.of(claim(3)));
        DurableOutboxPublisher abandoning = publisher(abandonStore, event -> {
            throw new LinkageError("broken adapter");
        }, 3);

        assertThat(abandoning.runOnce()).isTrue();
        assertThat(abandonStore.terminalCall.get()).isEqualTo("abandon");
    }

    @Test
    void staleCompletionIsObservedAsLeaseLossInsteadOfSuccess() {
        CapturingStore store = new CapturingStore(Optional.of(claim(1)), false);
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();

        assertThat(publisher(store, event -> {}, 3, metrics).runOnce()).isTrue();

        assertThat(metrics.count(Operation.OUTBOX, Outcome.CLAIMED)).isOne();
        assertThat(metrics.count(Operation.OUTBOX, Outcome.LEASE_LOST)).isOne();
        assertThat(metrics.count(Operation.OUTBOX, Outcome.SUCCESS)).isZero();
    }

    @Test
    void acceptedDeliveryWithLostCompletionReplaysTheSameLogicalEvent() {
        List<DurableOutboxSink.OutboundEvent> deliveries = new ArrayList<>();
        CapturingStore lostCompletion = new CapturingStore(Optional.of(claim(1, LEASE_TOKEN)), false);

        assertThat(publisher(lostCompletion, deliveries::add, 3).runOnce()).isTrue();
        assertThat(lostCompletion.terminalCall.get()).isEqualTo("complete");

        UUID replacementToken = UUID.fromString("00000000-0000-0000-0000-000000000004");
        CapturingStore recovered = new CapturingStore(Optional.of(claim(2, replacementToken)));

        assertThat(publisher(recovered, deliveries::add, 3).runOnce()).isTrue();
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries.get(1)).isEqualTo(deliveries.get(0));
        assertThat(recovered.lease.get().token()).isEqualTo(replacementToken);
        assertThat(recovered.terminalCall.get()).isEqualTo("complete");
    }

    private static DurableOutboxPublisher publisher(CapturingStore store, DurableOutboxSink sink, int maxAttempts) {
        return publisher(store, sink, maxAttempts, new DurableRuntimeMetrics());
    }

    private static DurableOutboxPublisher publisher(CapturingStore store, DurableOutboxSink sink, int maxAttempts,
            DurableRuntimeMetrics metrics) {
        return new DurableOutboxPublisher(store.proxy(), sink,
                new DurableOutboxPublisherOptions("publisher", Duration.ofSeconds(5), Duration.ofSeconds(20),
                        maxAttempts), new DurableLeaseRenewer(store.proxy(), Duration.ofSeconds(30)), metrics);
    }

    private static DurableStore.OutboxClaim claim(int attempt) {
        return claim(attempt, LEASE_TOKEN);
    }

    private static DurableStore.OutboxClaim claim(int attempt, UUID leaseToken) {
        return new DurableStore.OutboxClaim(new DurableStore.OutboxLease(RUN_ID, EVENT_ID, leaseToken), RUN_ID,
                RUN_PROCESS, "RUN_SUCCEEDED", null,
                new DurableStore.Envelope("{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8)), attempt,
                NOW.plusSeconds(30));
    }

    private static final class CapturingStore {
        private final Optional<DurableStore.OutboxClaim> claim;
        private final AtomicReference<String> terminalCall = new AtomicReference<>();
        private final AtomicReference<DurableStore.OutboxLease> lease = new AtomicReference<>();
        private final AtomicReference<Duration> retryDelay = new AtomicReference<>();
        private final boolean terminalApplied;

        private CapturingStore(Optional<DurableStore.OutboxClaim> claim) {
            this(claim, true);
        }

        private CapturingStore(Optional<DurableStore.OutboxClaim> claim, boolean terminalApplied) {
            this.claim = claim;
            this.terminalApplied = terminalApplied;
        }

        private DurableStore proxy() {
            InvocationHandler handler =
                    (instance, method, arguments) -> switch (method.getName()) {
                case "claimOutbox" -> claim;
                case "completeOutbox" -> terminal("complete", arguments);
                case "retryOutbox" -> {
                    retryDelay.set((Duration) arguments[1]);
                    yield terminal("retry", arguments);
                }
                case "abandonOutbox" -> terminal("abandon", arguments);
                case "toString" -> "CapturingDurableStore";
                default -> throw new AssertionError("Unexpected Store call: " + method.getName());
            };
            return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(),
                    new Class<?>[] {DurableStore.class}, handler);
        }

        private boolean terminal(String operation, Object[] arguments) {
            terminalCall.set(operation);
            lease.set((DurableStore.OutboxLease) arguments[0]);
            return terminalApplied;
        }
    }
}
