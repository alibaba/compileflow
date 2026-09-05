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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DurableWorkerCoordinatorTest {
    @Test
    void rejectsInvalidLifecycleConfigurationAtConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> coordinator(0, Duration.ofMillis(5)));
        assertThatIllegalArgumentException().isThrownBy(() -> coordinator(1, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> coordinator(1, Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test
    void idleDatabaseAcquisitionDoesNotScaleWithExecutionCapacity() throws Exception {
        AtomicInteger turnPolls = new AtomicInteger();
        AtomicInteger effectPolls = new AtomicInteger();
        DurableTurnWorker turns = mock(DurableTurnWorker.class);
        DurableEffectWorker effects = mock(DurableEffectWorker.class);
        when(turns.runOnce()).thenAnswer(ignored -> {
            turnPolls.incrementAndGet();
            return false;
        });
        when(effects.runOnce()).thenAnswer(ignored -> {
            effectPolls.incrementAndGet();
            return false;
        });
        DurableWorkerCoordinator coordinator = coordinator(turns, effects, 32, 32);

        coordinator.start();
        try {
            assertThat(awaitAtLeast(turnPolls, 2, Duration.ofSeconds(1))).isTrue();
            assertThat(awaitAtLeast(effectPolls, 2, Duration.ofSeconds(1))).isTrue();
            TimeUnit.MILLISECONDS.sleep(100);
            assertThat(turnPolls.get()).isLessThan(16);
            assertThat(effectPolls.get()).isLessThan(16);
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void availableWorkExpandsToConfiguredExecutionCapacity() throws Exception {
        int capacity = 4;
        AtomicInteger invocation = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch expanded = new CountDownLatch(capacity);
        CountDownLatch release = new CountDownLatch(1);
        DurableTurnWorker turns = mock(DurableTurnWorker.class);
        when(turns.runOnce()).thenAnswer(ignored -> {
            if (invocation.getAndIncrement() == 0) {
                return true;
            }
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            expanded.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } finally {
                active.decrementAndGet();
            }
            return false;
        });
        DurableEffectWorker effects = mock(DurableEffectWorker.class);
        when(effects.runOnce()).thenReturn(false);
        DurableWorkerCoordinator coordinator = coordinator(turns, effects, capacity, 1);

        coordinator.start();
        try {
            assertThat(expanded.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum.get()).isEqualTo(capacity);
        } finally {
            release.countDown();
            coordinator.stop();
        }
    }

    @Test
    void persistentUnexpectedWorkerFaultsBecomeOperationallyVisible() throws Exception {
        DurableTurnWorker turns = mock(DurableTurnWorker.class);
        when(turns.runOnce()).thenThrow(new IllegalStateException("secret-process-state"));
        DurableEffectWorker effects = mock(DurableEffectWorker.class);
        when(effects.runOnce()).thenReturn(false);
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        DurableWorkerCoordinator coordinator = coordinator(turns, effects, 1, 1, metrics);

        coordinator.start();
        try {
            assertThat(awaitDegraded(coordinator, Duration.ofSeconds(2))).isTrue();
            assertThat(metrics.count(Operation.TURN, Outcome.FAULT)).isGreaterThanOrEqualTo(3L);
            assertThat(coordinator.workerHealthSnapshot().operations().get("turn"))
                .containsEntry("state", "faulting")
                .doesNotContainValue("secret-process-state");
        } finally {
            coordinator.stop();
        }
    }

    @Test
    void gracefulStopDrainsInFlightWorkWithoutInterruptingIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        DurableTurnWorker turns = mock(DurableTurnWorker.class);
        when(turns.runOnce()).thenAnswer(ignored -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException failure) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return false;
        });
        DurableEffectWorker effects = mock(DurableEffectWorker.class);
        when(effects.runOnce()).thenReturn(false);
        DurableWorkerCoordinator coordinator = coordinator(turns, effects, 1, 1);

        coordinator.start();
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.stop(stopped::countDown);
            assertThat(coordinator.isRunning()).isFalse();
            assertThat(stopped.await(100, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(interrupted).isFalse();

            release.countDown();
            assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isFalse();
        } finally {
            release.countDown();
            coordinator.stop();
        }
    }

    private static DurableWorkerCoordinator coordinator(DurableTurnWorker turns, DurableEffectWorker effects,
            int turnCapacity, int effectCapacity) {
        return coordinator(turns, effects, turnCapacity, effectCapacity, new DurableRuntimeMetrics());
    }

    private static DurableWorkerCoordinator coordinator(DurableTurnWorker turns, DurableEffectWorker effects,
            int turnCapacity, int effectCapacity, DurableRuntimeMetrics metrics) {
        return new DurableWorkerCoordinator(mock(DurableStore.class), mock(DurableProcessRuntimeLoadWorker.class), turns,
                effects, Optional.empty(), Optional.empty(), Duration.ofMillis(5), Duration.ofHours(1),
                Duration.ofHours(1), 10, turnCapacity, effectCapacity, 1, metrics);
    }

    private static DurableWorkerCoordinator coordinator(int turnCapacity, Duration idlePollDelay) {
        return new DurableWorkerCoordinator(mock(DurableStore.class), mock(DurableProcessRuntimeLoadWorker.class),
                mock(DurableTurnWorker.class), mock(DurableEffectWorker.class), Optional.empty(), Optional.empty(),
                idlePollDelay, Duration.ofHours(1), Duration.ofHours(1), 10, turnCapacity, 1, 1,
                new DurableRuntimeMetrics());
    }

    private static boolean awaitAtLeast(AtomicInteger value, int expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (value.get() >= expected) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(1);
        }
        return value.get() >= expected;
    }

    private static boolean awaitDegraded(DurableWorkerCoordinator coordinator, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (coordinator.workerHealthSnapshot().degraded()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(1);
        }
        return coordinator.workerHealthSnapshot().degraded();
    }
}
