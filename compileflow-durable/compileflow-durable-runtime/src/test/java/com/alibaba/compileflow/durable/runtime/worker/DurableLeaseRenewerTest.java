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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.spi.store.DurableLeaseStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DurableLeaseRenewerTest {
    private static final Duration TEST_LEASE = Duration.ofMillis(90);

    @Test
    void heartbeatsAllThreeKindsOfApplicationWork() throws Exception {
        CountDownLatch renewed = new CountDownLatch(3);
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases", "renewEffectLeases", "renewOutboxLeases" -> {
                renewed.countDown();
                yield arguments[0];
            }
            case "toString" -> "LeaseHeartbeatStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE);
                DurableLeaseRenewer.Handle run = renewer.trackRun(runLease(1));
                DurableLeaseRenewer.Handle effect = renewer.trackEffect(effectLease(2));
                DurableLeaseRenewer.Handle outbox = renewer.trackOutbox(outboxLease(3))) {
            assertThat(run).isNotNull();
            assertThat(effect).isNotNull();
            assertThat(outbox).isNotNull();
            assertThat(renewer.leaseDuration()).isEqualTo(TEST_LEASE);
            assertThat(renewed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void slowRunRenewalCannotStarveEffectOrOutboxRenewal() throws Exception {
        CountDownLatch runEntered = new CountDownLatch(1);
        CountDownLatch releaseRun = new CountDownLatch(1);
        CountDownLatch otherLanesRenewed = new CountDownLatch(2);
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                runEntered.countDown();
                if (!releaseRun.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release Run renewal");
                }
                yield arguments[0];
            }
            case "renewEffectLeases", "renewOutboxLeases" -> {
                otherLanesRenewed.countDown();
                yield arguments[0];
            }
            case "toString" -> "BlockingRunLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE);
                DurableLeaseRenewer.Handle run = renewer.trackRun(runLease(1));
                DurableLeaseRenewer.Handle effect = renewer.trackEffect(effectLease(2));
                DurableLeaseRenewer.Handle outbox = renewer.trackOutbox(outboxLease(3))) {
            assertThat(run).isNotNull();
            assertThat(effect).isNotNull();
            assertThat(outbox).isNotNull();
            assertThat(runEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(otherLanesRenewed.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseRun.countDown();
        }
    }

    @Test
    void removesAuthorityThatTheStoreCouldNotRenew() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicInteger runCalls = new AtomicInteger();
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                runCalls.incrementAndGet();
                attempted.countDown();
                yield Set.of();
            }
            case "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "LostLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE, metrics);
                DurableLeaseRenewer.Handle ignored = renewer.trackRun(runLease(1))) {
            assertThat(ignored).isNotNull();
            assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(TEST_LEASE.multipliedBy(2).toMillis());
            assertThat(runCalls).hasValue(1);
            assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.LEASE_LOST)).isOne();
        }
    }

    @Test
    void retriesAfterTransientStoreFailure() throws Exception {
        CountDownLatch recovered = new CountDownLatch(1);
        AtomicInteger runCalls = new AtomicInteger();
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                if (runCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("database temporarily unavailable");
                }
                recovered.countDown();
                yield arguments[0];
            }
            case "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "RecoveringLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE, metrics);
                DurableLeaseRenewer.Handle ignored = renewer.trackRun(runLease(1))) {
            assertThat(ignored).isNotNull();
            assertThat(recovered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(runCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.FAULT)).isOne();
            assertThat(
                    awaitCondition(() -> metrics.count(Operation.LEASE_RENEWAL, Outcome.SUCCESS) >= 1L,
                            Duration.ofSeconds(2)))
                .isTrue();
        }
    }

    @Test
    void persistentRenewalFaultsDegradeAndSuccessfulCycleRecovers() throws Exception {
        AtomicBoolean failing = new AtomicBoolean(true);
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                if (failing.get()) {
                    throw new IllegalStateException("secret-database-location");
                }
                yield arguments[0];
            }
            case "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "HealthLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE);
                DurableLeaseRenewer.Handle ignored = renewer.trackRun(runLease(1))) {
            assertThat(ignored).isNotNull();
            assertThat(
                    awaitCondition(() -> renewer.renewalHealth().run().consecutiveFaults() >= 2, Duration.ofSeconds(2)))
                .isTrue();
            DurableLeaseRenewer.LaneHealth faulting = renewer.renewalHealth().run();
            assertThat(faulting.activeAuthorities()).isOne();
            assertThat(faulting.consecutiveFaults()).isGreaterThanOrEqualTo(2);
            assertThat(faulting.degraded()).isTrue();
            assertThat(faulting.lastFault()).isNotNull();
            assertThat(faulting.lastFailureType()).isEqualTo(IllegalStateException.class.getName());
            assertThat(faulting.toString()).doesNotContain("secret-database-location");

            failing.set(false);
            assertThat(awaitCondition(() -> {
                DurableLeaseRenewer.LaneHealth recovered = renewer.renewalHealth().run();
                return recovered.consecutiveFaults() == 0 && recovered.lastSuccessfulCycle() != null;
            }, Duration.ofSeconds(2))).isTrue();
            assertThat(renewer.renewalHealth().degraded()).isFalse();
        }
    }

    @Test
    void stalledRenewalCycleBecomesDegradedBeforeLeaseExpiry() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                entered.countDown();
                release.await(2, TimeUnit.SECONDS);
                yield arguments[0];
            }
            case "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "StalledLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, TEST_LEASE);
                DurableLeaseRenewer.Handle ignored = renewer.trackRun(runLease(1))) {
            assertThat(ignored).isNotNull();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitCondition(() -> renewer.renewalHealth().run().stale(), Duration.ofSeconds(2))).isTrue();
            DurableLeaseRenewer.LaneHealth stalled = renewer.renewalHealth().run();
            assertThat(stalled.degraded()).isTrue();
            assertThat(stalled.consecutiveFaults()).isZero();
            assertThat(stalled.currentCycleStarted()).isNotNull();
        } finally {
            release.countDown();
        }
    }

    @Test
    void renewsMaximumConfiguredCapacityInBoundedBatches() throws Exception {
        int capacity = DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE;
        CountDownLatch fullBatches = new CountDownLatch(3);
        Set<String> fullBatchKinds = ConcurrentHashMap.newKeySet();
        AtomicInteger renewalCalls = new AtomicInteger();
        AtomicInteger renewedAuthorities = new AtomicInteger();
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases", "renewEffectLeases", "renewOutboxLeases" -> {
                @SuppressWarnings("unchecked")
                Set<Object> leases = (Set<Object>) arguments[0];
                assertThat(leases).hasSizeLessThanOrEqualTo(capacity);
                renewalCalls.incrementAndGet();
                renewedAuthorities.addAndGet(leases.size());
                if (leases.size() == capacity && fullBatchKinds.add(method.getName())) {
                    fullBatches.countDown();
                }
                yield leases;
            }
            case "toString" -> "BatchLeaseHeartbeatStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        List<DurableLeaseRenewer.Handle> handles = new ArrayList<>(capacity * 3);
        Duration leaseDuration = Duration.ofMillis(300);

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, leaseDuration, metrics)) {
            for (int index = 1; index <= capacity; index++) {
                handles.add(renewer.trackRun(runLease(index)));
                handles.add(renewer.trackEffect(effectLease(index)));
                handles.add(renewer.trackOutbox(outboxLease(index)));
            }

            assertThat(fullBatches.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fullBatchKinds).containsExactlyInAnyOrder("renewRunLeases", "renewEffectLeases",
                    "renewOutboxLeases");
            assertThat(renewalCalls.get()).isGreaterThanOrEqualTo(3);
            assertThat(renewedAuthorities.get()).isGreaterThanOrEqualTo(capacity * 3);
            awaitSuccessfulRenewals(metrics, capacity * 3L);
            assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.LEASE_LOST)).isZero();
        } finally {
            handles.forEach(DurableLeaseRenewer.Handle::close);
        }
    }

    @Test
    void oneFailedChunkCannotStarveLaterAuthorities() throws Exception {
        int authorityCount = DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE + 1;
        CountDownLatch laterChunkRenewed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases" -> {
                @SuppressWarnings("unchecked")
                Set<DurableStore.RunLease> leases = (Set<DurableStore.RunLease>) arguments[0];
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("one renewal chunk failed");
                }
                laterChunkRenewed.countDown();
                yield leases;
            }
            case "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "ChunkedLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
        List<DurableLeaseRenewer.Handle> handles = new ArrayList<>(authorityCount);

        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, Duration.ofMillis(600), metrics)) {
            for (int index = 1; index <= authorityCount; index++) {
                handles.add(renewer.trackRun(runLease(index)));
            }
            assertThat(laterChunkRenewed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(calls.get()).isGreaterThanOrEqualTo(2);
            assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.FAULT)).isEqualTo(
                    DurableLeaseStore.MAX_RENEWAL_BATCH_SIZE);
            assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.SUCCESS)).isGreaterThanOrEqualTo(1L);
        } finally {
            handles.forEach(DurableLeaseRenewer.Handle::close);
        }
    }

    @Test
    void rejectsDuplicateAuthorityAndTrackingAfterClose() {
        DurableLeaseStore store = store((instance, method, arguments) -> switch (method.getName()) {
            case "renewRunLeases", "renewEffectLeases", "renewOutboxLeases" -> arguments[0];
            case "toString" -> "InvariantLeaseStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
        DurableStore.RunLease lease = runLease(1);
        DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, Duration.ofSeconds(30));

        try (DurableLeaseRenewer.Handle ignored = renewer.trackRun(lease)) {
            assertThat(ignored).isNotNull();
            assertThatThrownBy(() -> renewer.trackRun(lease))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Lease authority is already tracked");
        }
        renewer.close();
        assertThatThrownBy(() -> renewer.trackRun(runLease(2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    private static DurableLeaseStore store(InvocationHandler handler) {
        return (DurableLeaseStore) Proxy.newProxyInstance(DurableLeaseStore.class.getClassLoader(),
                new Class<?>[] {DurableLeaseStore.class}, handler);
    }

    private static DurableStore.RunLease runLease(int value) {
        return new DurableStore.RunLease(runId(value), new UUID(1L, value));
    }

    private static DurableStore.EffectLease effectLease(int value) {
        return new DurableStore.EffectLease(runId(value), new UUID(2L, value), new UUID(3L, value));
    }

    private static DurableStore.OutboxLease outboxLease(int value) {
        return new DurableStore.OutboxLease(runId(value), new UUID(4L, value), new UUID(5L, value));
    }

    private static ProcessRunId runId(int value) {
        return new ProcessRunId(new UUID(0L, value).toString());
    }

    private static void awaitSuccessfulRenewals(DurableRuntimeMetrics metrics, long expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (metrics.count(Operation.LEASE_RENEWAL, Outcome.SUCCESS) < expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(metrics.count(Operation.LEASE_RENEWAL, Outcome.SUCCESS)).isGreaterThanOrEqualTo(expected);
    }

    private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        return condition.getAsBoolean();
    }
}
