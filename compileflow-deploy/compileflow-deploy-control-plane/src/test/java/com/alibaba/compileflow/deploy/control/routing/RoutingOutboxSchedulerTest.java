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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRepository;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RoutingOutboxSchedulerTest {
    private static RoutingOutboxDispatcher idleDispatcher() {
        RoutingOutboxDispatcher dispatcher = mock(RoutingOutboxDispatcher.class);
        when(dispatcher.awaitIdle(anyLong())).thenReturn(true);
        when(dispatcher.dispatch()).thenReturn(new RoutingOutboxDispatcher.DispatchResult(0, 0, false));
        return dispatcher;
    }

    @Test
    void constructorRejectsNonPositiveIntervals() {
        RoutingOutboxDispatcher dispatcher = mock(RoutingOutboxDispatcher.class);

        assertThatIllegalArgumentException().isThrownBy(() -> new RoutingOutboxScheduler(dispatcher, Duration.ZERO));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(1), null, null, Duration.ZERO));
        assertThatNullPointerException()
            .isThrownBy(() -> new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(1), null, Duration.ofMillis(1)));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(1),
                    mock(RoutingOutboxRepository.class), Duration.ZERO));
    }

    @Test
    void retentionCleanupRunsFromImmutableConstructionPolicy() {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        RoutingOutboxRepository repository = mock(RoutingOutboxRepository.class);
        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(5), repository,
                Duration.ofSeconds(1), Duration.ofMillis(5))) {
            scheduler.start();

            verify(repository, timeout(1_000).atLeastOnce()).deleteDeliveredOlderThan(1_000L, 1_000);
        }
    }

    @Test
    void saturatedRetentionCleanupContinuesWithoutWaitingForThePeriodicCadence() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        RoutingOutboxRepository repository = mock(RoutingOutboxRepository.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(2);
        long[] callNanos = new long[2];
        when(repository.deleteDeliveredOlderThan(1_000L, 1_000)).thenAnswer(invocation -> {
            int call = calls.getAndIncrement();
            if (call < callNanos.length) {
                callNanos[call] = System.nanoTime();
                completed.countDown();
            }
            return call == 0 ? 1_000 : 0;
        });
        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofHours(1), repository,
                Duration.ofSeconds(1), Duration.ofSeconds(1))) {
            scheduler.start();

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(Duration.ofNanos(callNanos[1] - callNanos[0])).isLessThan(Duration.ofMillis(500));
        }
    }

    @Test
    void dispatchRunsOnItsOwnCadenceWithoutRetention() {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(5))) {
            scheduler.start();

            verify(dispatcher, timeout(1_000).atLeastOnce()).dispatch();
        }
    }

    @Test
    void commitSignalRequestsDispatchWithoutWaitingForThePeriodicCadence() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        CountDownLatch requestedDispatch = new CountDownLatch(1);
        when(dispatcher.dispatch()).thenAnswer(invocation -> {
            requestedDispatch.countDown();
            return new RoutingOutboxDispatcher.DispatchResult(0, 0, false);
        });

        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofHours(1))) {
            scheduler.start();
            scheduler.requestDispatch();

            assertThat(requestedDispatch.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void commitSignalDuringRequestedDispatchRunsAnotherCycle() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        CountDownLatch requestedDispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseRequestedDispatch = new CountDownLatch(1);
        CountDownLatch followUpDispatch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch()).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                requestedDispatchStarted.countDown();
                assertThat(releaseRequestedDispatch.await(1, TimeUnit.SECONDS)).isTrue();
            } else if (call == 2) {
                followUpDispatch.countDown();
            }
            return new RoutingOutboxDispatcher.DispatchResult(0, 0, false);
        });

        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofHours(1))) {
            scheduler.start();
            scheduler.requestDispatch();
            assertThat(requestedDispatchStarted.await(1, TimeUnit.SECONDS)).isTrue();
            scheduler.requestDispatch();
            releaseRequestedDispatch.countDown();

            assertThat(followUpDispatch.await(1, TimeUnit.SECONDS)).isTrue();
            TimeUnit.MILLISECONDS.sleep(50);
            assertThat(calls).hasValue(2);
        } finally {
            releaseRequestedDispatch.countDown();
        }
    }

    @Test
    void saturatedDispatchContinuesWithoutWaitingForThePeriodicCadence() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        CountDownLatch followUpDispatch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(dispatcher.dispatch()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                return new RoutingOutboxDispatcher.DispatchResult(50, 50, true);
            }
            followUpDispatch.countDown();
            return new RoutingOutboxDispatcher.DispatchResult(0, 0, false);
        });

        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofHours(1))) {
            scheduler.start();
            scheduler.requestDispatch();

            assertThat(followUpDispatch.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void stopCanBeFollowedByStartWithoutTerminatingTheDispatcher() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        AtomicInteger phase = new AtomicInteger(1);
        CountDownLatch firstDispatch = new CountDownLatch(1);
        CountDownLatch resumedDispatch = new CountDownLatch(1);
        when(dispatcher.dispatch()).thenAnswer(invocation -> {
            if (phase.get() == 1) {
                firstDispatch.countDown();
            } else {
                resumedDispatch.countDown();
            }
            return new RoutingOutboxDispatcher.DispatchResult(0, 0, false);
        });

        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofMillis(5))) {
            scheduler.start();
            assertThat(firstDispatch.await(1, TimeUnit.SECONDS)).isTrue();
            scheduler.stop();

            phase.set(2);
            scheduler.start();
            assertThat(resumedDispatch.await(1, TimeUnit.SECONDS)).isTrue();
        }

        verify(dispatcher).stop();
    }

    @Test
    void stopWaitsForAnActiveDispatchCycle() throws Exception {
        RoutingOutboxDispatcher dispatcher = idleDispatcher();
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        when(dispatcher.dispatch()).thenAnswer(invocation -> {
            dispatchStarted.countDown();
            assertThat(releaseDispatch.await(1, TimeUnit.SECONDS)).isTrue();
            return new RoutingOutboxDispatcher.DispatchResult(0, 0, false);
        });

        try (RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(dispatcher, Duration.ofHours(1))) {
            scheduler.start();
            scheduler.requestDispatch();
            assertThat(dispatchStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Thread stopper = new Thread(() -> {
                scheduler.stop();
                stopped.countDown();
            });
            stopper.start();
            assertThat(stopped.await(100, TimeUnit.MILLISECONDS)).isFalse();

            releaseDispatch.countDown();
            assertThat(stopped.await(1, TimeUnit.SECONDS)).isTrue();
            stopper.join();
        } finally {
            releaseDispatch.countDown();
        }
    }

    @Test
    void closedSchedulerCannotBeRestarted() {
        RoutingOutboxScheduler scheduler = new RoutingOutboxScheduler(idleDispatcher(), Duration.ofMillis(5));
        scheduler.close();

        assertThatIllegalStateException().isThrownBy(scheduler::start).withMessage("RoutingOutboxScheduler is closed");
    }
}
