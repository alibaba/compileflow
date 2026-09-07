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
package com.alibaba.compileflow.durable.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableProcessEngineFactoryTest {
    private DurableProcessEngineConfig config(DurableStore store, boolean enabled) {
        var worker = DurableProcessEngineConfig.Worker.defaults();
        return DurableProcessEngineConfig
            .builder(store)
            .worker(
                    new DurableProcessEngineConfig.Worker(enabled, null, worker.leaseDuration(), worker.idlePollDelay(),
                            worker.turnFaultBackoff(), worker.turnMaxSteps(), worker.maxActiveIterations(), 1, 1))
            .build();
    }

    @Test
    void admissionOnlyEngineCreatesNoWorkerGraphAndDoesNotOwnStore() {
        DurableStore store = mock(DurableStore.class);
        try (var engine = DurableProcessEngineFactory.create(config(store, false))) {
            assertThat(engine.getWorkerCoordinator()).isNull();
            assertThat(engine.getLeaseRenewer()).isNull();
            engine.start();
            assertThat(engine.isRunning()).isFalse();
            engine.stop();
        }
        verifyNoInteractions(store);
    }

    @Test
    void workersHaveExplicitRestartableLifecycleButClosedEngineCannotRestart() {
        DurableStore store = mock(DurableStore.class);
        var engine = DurableProcessEngineFactory.create(config(store, true));
        try {
            assertThat(engine.isRunning()).isFalse();
            engine.start();
            engine.start();
            assertThat(engine.isRunning()).isTrue();
            engine.stop();
            assertThat(engine.isRunning()).isFalse();
            engine.start();
            assertThat(engine.isRunning()).isTrue();
        } finally {
            engine.close();
        }
        engine.close();
        assertThat(engine.isRunning()).isFalse();
        assertThatThrownBy(engine::start).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        assertThatThrownBy(() -> engine.getRun(ProcessRunId.random())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void interruptedCloseStillDrainsAdmittedQueries() throws Exception {
        DurableStore store = mock(DurableStore.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        when(store.findRun(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("query not released");
            }
            return Optional.empty();
        });
        var engine = DurableProcessEngineFactory.create(config(store, false));
        Thread query = new Thread(() -> {
            try {
                engine.getRun(ProcessRunId.random());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        Thread close = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                engine.close();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                closed.countDown();
            }
        });
        try {
            query.start();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            close.start();
            assertThat(closed.await(100, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            release.countDown();
            query.join(5000);
            close.join(5000);
            engine.close();
        }
        assertThat(closed.getCount()).isZero();
        assertThat(failure.get()).isNull();
    }

    @Test
    void rejectsCloseFromRenewalBeforeChangingEngineLifecycle() throws Exception {
        DurableStore store = mock(DurableStore.class);
        var defaults = DurableProcessEngineConfig.Worker.defaults();
        var configuration = DurableProcessEngineConfig
            .builder(store)
            .worker(
                    new DurableProcessEngineConfig.Worker(true, null, Duration.ofMillis(90), defaults.idlePollDelay(),
                            defaults.turnFaultBackoff(), defaults.turnMaxSteps(), defaults.maxActiveIterations(), 1, 1))
            .build();
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ProcessRunId runId = ProcessRunId.random();
        when(store.findRun(runId)).thenReturn(Optional.empty());
        try (var engine = DurableProcessEngineFactory.create(configuration)) {
            when(store.renewRunLeases(any(), any())).thenAnswer(call -> {
                try {
                    assertThatThrownBy(engine::close).isInstanceOf(IllegalStateException.class).hasMessageContaining(
                            "renewal");
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    attempted.countDown();
                }
                return call.getArgument(0);
            });
            try (var handle = engine.getLeaseRenewer().trackRun(new DurableStore.RunLease(runId, UUID.randomUUID()))) {
                assertThat(handle).isNotNull();
                assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(failure.get()).isNull();
                assertThat(engine.getRun(runId)).isEmpty();
            }
        }
    }

    @Test
    void rejectsWorkerAndOutboxPolicyErrorsBeforeAssembly() {
        var worker = DurableProcessEngineConfig.Worker.defaults();
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Worker(true, null, worker.leaseDuration(),
                worker.idlePollDelay(), worker.turnFaultBackoff(), 0, worker.maxActiveIterations(), 1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("turnMaxSteps");
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Worker(true, null, worker.leaseDuration(),
                worker.idlePollDelay(), worker.turnFaultBackoff(), worker.turnMaxSteps(), 0, 1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxActiveIterations");
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Outbox(1, Duration.ofSeconds(2), Duration.ofSeconds(1),
                1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDelay");
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Outbox(1, Duration.ofNanos(1), Duration.ofSeconds(1), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Outbox(1, Duration.ofSeconds(1), Duration.ofSeconds(2),
                0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts");
    }

    @Test
    void maintenanceBatchSizeMatchesRuntimeDemandQueryBounds() {
        assertThat(DurableProcessEngineConfig.Maintenance.defaults().batchSize()).isEqualTo(100);
        for (int batchSize : new int[] {1, 100, 1000}) {
            var maintenance = new DurableProcessEngineConfig.Maintenance(Duration.ofSeconds(1), batchSize);
            assertThat(maintenance.batchSize()).isEqualTo(batchSize);
            assertThat(new DurableStore.ProcessRuntimeDemandQuery(null, maintenance.batchSize()).limit()).isEqualTo(
                    batchSize);
        }
        for (int batchSize : new int[] {-1, 0, 1001, 10000, 10001, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> new DurableProcessEngineConfig.Maintenance(Duration.ofSeconds(1), batchSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maintenanceBatchSize")
                .hasMessageContaining("1000");
        }
    }

    @Test
    void maximumMaintenanceBatchReachesDemandLoadingAndEveryRepairOperation() {
        DurableStore store = mock(DurableStore.class);
        var demandQuery = new DurableStore.ProcessRuntimeDemandQuery(null, 1000);
        when(store.listProcessRuntimeDemand(demandQuery))
            .thenReturn(new DurableStore.ProcessRuntimeDemandPage(List.of(), null));
        var configuration = DurableProcessEngineConfig
            .builder(store)
            .maintenance(new DurableProcessEngineConfig.Maintenance(Duration.ofMillis(10), 1000))
            .build();
        try (var engine = DurableProcessEngineFactory.create(configuration)) {
            engine.start();
            verify(store, timeout(2000).atLeastOnce()).resolveDueWaits(1000);
            verify(store, atLeastOnce()).listProcessRuntimeDemand(demandQuery);
            verify(store, atLeastOnce()).reclaimExpiredRuns(1000);
            verify(store, atLeastOnce()).reclaimExpiredEffects(1000);
            verify(store, atLeastOnce()).reclaimExpiredOutbox(1000);
        }
    }

    @Test
    void definitionConfigurationRejectsLimitsAboveTheStoreCeiling() {
        var store = mock(DurableStore.class);
        for (int maxBytes : new int[] {DurableStore.MAX_DEFINITION_BYTES + 1, ProcessDefinitionConfig.MAX_BYTES}) {
            var definitions = ProcessDefinitionConfig.builder().maxBytes(maxBytes).build();
            assertThatThrownBy(() -> DurableProcessEngineConfig.builder(store).definitions(definitions).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definition.maxBytes");
        }
        for (int maxBytes : new int[] {1, 32, DurableStore.MAX_DEFINITION_BYTES}) {
            var definitions = ProcessDefinitionConfig.builder().maxBytes(maxBytes).build();
            assertThat(DurableProcessEngineConfig
                .builder(store)
                .definitions(definitions)
                .build()
                .getDefinitionConfig())
                .isSameAs(definitions);
        }
        assertThat(DurableProcessEngineConfig.builder(store).build().getDefinitionConfig().getMaxBytes())
            .isEqualTo(DurableStore.MAX_DEFINITION_BYTES);
        verifyNoInteractions(store);
    }

    @Test
    void configurationCapturesIndependentRealizationAndContextClassLoader() {
        var store = mock(DurableStore.class);
        var config = DurableProcessEngineConfig.builder(store).runtimeMode(ProcessRuntimeMode.INTERPRETED).build();
        assertThat(config.getRuntimeMode()).isEqualTo(ProcessRuntimeMode.INTERPRETED);
        assertThat(config.getClassLoader()).isSameAs(Thread.currentThread().getContextClassLoader());
        assertThat(DurableProcessEngineConfig.builder(store).build().getRuntimeMode()).isEqualTo(
                ProcessRuntimeMode.COMPILED);
        assertThat(DurableProcessEngineConfig.builder(store).build().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(
                15));
        assertThat(DurableProcessEngineConfig
            .builder(store)
            .shutdownTimeout(Duration.ofSeconds(9))
            .build()
            .getShutdownTimeout())
            .isEqualTo(Duration.ofSeconds(9));
        assertThatThrownBy(() -> DurableProcessEngineConfig.builder(store).maxCallDepth(257).build())
            .isInstanceOf(IllegalArgumentException.class);
        var worker = config.getWorker();
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Worker(true, null, Duration.ZERO, worker.idlePollDelay(),
                worker.turnFaultBackoff(), worker.turnMaxSteps(), worker.maxActiveIterations(), 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableProcessEngineConfig.Worker(true, null, Duration.ofMillis(1),
                worker.idlePollDelay(), worker.turnFaultBackoff(), worker.turnMaxSteps(), worker.maxActiveIterations(),
                1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("renewal before expiry");
        assertThat(new DurableProcessEngineConfig.Worker(true, null, Duration.ofMillis(2), worker.idlePollDelay(),
                worker.turnFaultBackoff(), worker.turnMaxSteps(), worker.maxActiveIterations(), 1, 1)
            .leaseDuration())
            .isEqualTo(Duration.ofMillis(2));
    }
}
