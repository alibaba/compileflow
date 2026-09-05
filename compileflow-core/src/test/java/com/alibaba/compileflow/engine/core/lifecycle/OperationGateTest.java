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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class OperationGateTest {
    @Test
    void closeCutsOffAdmissionAndWaitsForActiveOperation() throws Exception {
        OperationGate gate = new OperationGate("test component");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean closeReturned = new AtomicBoolean();
        Thread operation = new Thread(() -> {
            gate.enter();
            entered.countDown();
            try {
                await(release);
            } finally {
                gate.exit();
            }
        });
        operation.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        Thread closer = new Thread(() -> {
            closeAfterDrain(gate);
            closeReturned.set(true);
        });
        closer.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                gate.ensureOpen();
                if (System.nanoTime() >= deadline) {
                    fail("Close did not cut off admission");
                }
                Thread.onSpinWait();
            } catch (IllegalStateException closed) {
                break;
            }
        }
        assertThatThrownBy(gate::enter).isInstanceOf(IllegalStateException.class).hasMessage("test component is closed");
        assertThat(closeReturned).isFalse();

        release.countDown();
        operation.join(5_000L);
        closer.join(5_000L);
        assertThat(closeReturned).isTrue();
    }

    @Test
    void admittedThreadMayReenterAfterCloseStarts() throws Exception {
        OperationGate gate = new OperationGate("test component");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch nestedCompleted = new CountDownLatch(1);
        Thread operation = new Thread(() -> {
            gate.enter();
            try {
                entered.countDown();
                await(closeStarted);
                gate.ensureOpen();
                gate.enter();
                try {
                    nestedCompleted.countDown();
                } finally {
                    gate.exit();
                }
            } finally {
                gate.exit();
            }
        });
        operation.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread closer = new Thread(() -> closeAfterDrain(gate));
        closer.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                gate.ensureOpen();
                if (System.nanoTime() >= deadline) {
                    fail("Close did not cut off admission");
                }
                Thread.onSpinWait();
            } catch (IllegalStateException closing) {
                break;
            }
        }
        closeStarted.countDown();
        assertThat(nestedCompleted.await(5, TimeUnit.SECONDS)).isTrue();
        operation.join(5_000L);
        closer.join(5_000L);
    }

    @Test
    void closeFromActiveOperationFailsFast() {
        OperationGate gate = new OperationGate("test component");
        gate.enter();
        try {
            assertThatThrownBy(() -> gate.beginCloseAndAwaitDrained(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test component cannot be closed from an active operation");
        } finally {
            gate.exit();
        }
    }

    @Test
    void boundedCloseForcesCleanupAfterTimeoutAndBlocksFurtherReentry() throws Exception {
        OperationGate gate = new OperationGate("test component");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch attemptReentry = new CountDownLatch(1);
        AtomicBoolean reentered = new AtomicBoolean(true);
        Thread operation = new Thread(() -> {
            gate.enter();
            try {
                entered.countDown();
                await(attemptReentry);
                reentered.set(gate.tryEnter());
                if (reentered.get()) {
                    gate.exit();
                }
            } finally {
                gate.exit();
            }
        });
        operation.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        OperationGate.DrainResult result = gate.beginCloseAndAwaitDrained(Duration.ofMillis(50));

        assertThat(result).isEqualTo(OperationGate.DrainResult.TIMED_OUT);
        assertThat(gate.activeOperations()).isEqualTo(1L);
        assertThat(gate.tryEnter()).isFalse();
        attemptReentry.countDown();
        operation.join(5_000L);
        assertThat(reentered).isFalse();
        gate.finishClose();
    }

    @Test
    void boundedCloseReturnsAsSoonAsOperationsDrain() throws Exception {
        OperationGate gate = new OperationGate("test component");
        CountDownLatch entered = new CountDownLatch(1);
        Thread operation = new Thread(() -> {
            gate.enter();
            entered.countDown();
            try {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            } finally {
                gate.exit();
            }
        });
        operation.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        OperationGate.DrainResult result = gate.beginCloseAndAwaitDrained(Duration.ofSeconds(2));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(result).isEqualTo(OperationGate.DrainResult.DRAINED);
        assertThat(elapsedMillis).isLessThan(1_000L);
        gate.finishClose();
        operation.join(5_000L);
    }

    @Test
    void cleanupNeverOverlapsOperationsRacingAcrossCounterStripes() throws Exception {
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers + 1);
        try {
            for (int round = 0; round < 50; round++) {
                OperationGate gate = new OperationGate("test component");
                CountDownLatch firstAdmissions = new CountDownLatch(workers);
                AtomicBoolean cleanupStarted = new AtomicBoolean();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Future<?>[] operations = new Future<?>[workers];

                for (int worker = 0; worker < workers; worker++) {
                    operations[worker] = executor.submit(() -> {
                        boolean first = true;
                        while (gate.tryEnter()) {
                            try {
                                if (first) {
                                    first = false;
                                    firstAdmissions.countDown();
                                }
                                if (cleanupStarted.get()) {
                                    failure.compareAndSet(null,
                                            new AssertionError("cleanup overlapped an admitted operation"));
                                }
                                Thread.onSpinWait();
                            } finally {
                                gate.exit();
                            }
                        }
                    });
                }

                assertThat(firstAdmissions.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> closing = executor.submit(() -> {
                    OperationGate.DrainResult result = gate.beginCloseAndAwaitDrained(Duration.ofSeconds(5));
                    if (result != OperationGate.DrainResult.DRAINED) {
                        throw new AssertionError("Operation gate did not drain: " + result);
                    }
                    cleanupStarted.set(true);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    gate.finishClose();
                });

                closing.get(5, TimeUnit.SECONDS);
                for (Future<?> operation : operations) {
                    operation.get(5, TimeUnit.SECONDS);
                }
                assertThat(failure.get()).isNull();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void closeAfterDrain(OperationGate gate) {
        OperationGate.DrainResult result = gate.beginCloseAndAwaitDrained(Duration.ofSeconds(5));
        if (result != OperationGate.DrainResult.DRAINED) {
            throw new AssertionError("Operation gate did not drain: " + result);
        }
        gate.finishClose();
    }
}
