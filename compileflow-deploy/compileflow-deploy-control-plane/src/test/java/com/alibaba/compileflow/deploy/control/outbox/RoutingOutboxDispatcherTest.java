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
package com.alibaba.compileflow.deploy.control.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RoutingOutboxDispatcherTest {
    private RoutingOutboxStore outboxRepository;
    private RoutingStateDeliveryTarget deliveryTarget;
    private RoutingOutboxDispatcher dispatcher;

    private static RoutingOutboxDispatchPolicy policy(int batchSize) {
        return new RoutingOutboxDispatchPolicy(batchSize, Duration.ofMinutes(1), 10, Duration.ofSeconds(5),
                Duration.ofMinutes(5));
    }

    @BeforeEach
    void setUp() {
        outboxRepository = mock(RoutingOutboxStore.class);
        deliveryTarget = mock(RoutingStateDeliveryTarget.class);
        dispatcher = new RoutingOutboxDispatcher(outboxRepository, deliveryTarget, policy(10));
    }

    @Test
    void dispatchReturnsZeroWhenNoPendingRecords() {
        when(outboxRepository.claimPending(eq(1), anyString(), anyLong())).thenReturn(Collections.emptyList());
        RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();
        assertThat(result).isEqualTo(new RoutingOutboxDispatcher.DispatchResult(0, 0, false));
    }

    @Test
    void dispatchDeliversAllPendingAndMarksDelivered() throws Exception {
        RoutingOutboxRecord r1 = pendingRecord(1L, "ALIAS_STATE", "key1", "{\"kind\":\"aliasState\"}");
        RoutingOutboxRecord r2 = pendingRecord(2L, "ALIAS_STATE", "key2", "{\"kind\":\"aliasState\"}");
        claimInOrder(List.of(Collections.singletonList(r1), Collections.singletonList(r2), Collections.emptyList()));
        when(outboxRepository.markDelivered(anyLong(), anyString())).thenReturn(1);

        RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();

        assertThat(result).isEqualTo(new RoutingOutboxDispatcher.DispatchResult(2, 2, false));
        // The transactionally captured payload is replayed verbatim.
        verify(deliveryTarget).deliver("key1", "{\"kind\":\"aliasState\"}");
        verify(deliveryTarget).deliver("key2", "{\"kind\":\"aliasState\"}");
        verify(outboxRepository).markDelivered(1L, "lease-1");
        verify(outboxRepository).markDelivered(2L, "lease-2");
        verify(outboxRepository, never()).markFailed(anyLong(), anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void dispatchMarksFailedOnProjectionStoreErrorAndContinuesWithRemainingRecords() throws Exception {
        RoutingOutboxRecord r1 = pendingRecord(1L, "ALIAS_STATE", "key1", "{\"kind\":\"x\"}");
        RoutingOutboxRecord r2 = pendingRecord(2L, "ALIAS_STATE", "key2", "{\"kind\":\"y\"}");
        claimInOrder(List.of(Collections.singletonList(r1), Collections.singletonList(r2), Collections.emptyList()));
        when(outboxRepository.markDelivered(anyLong(), anyString())).thenReturn(1);
        when(outboxRepository.markFailed(anyLong(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        doThrow(new RuntimeException("delivery failed")).when(deliveryTarget).deliver(eq("key1"), anyString());

        RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();

        assertThat(result).isEqualTo(new RoutingOutboxDispatcher.DispatchResult(2, 1, false));
        verify(deliveryTarget).deliver("key1", "{\"kind\":\"x\"}");
        verify(deliveryTarget).deliver("key2", "{\"kind\":\"y\"}");
        verify(outboxRepository, never()).markDelivered(eq(1L), anyString());
        verify(outboxRepository).markDelivered(2L, "lease-2");
        verify(outboxRepository, times(1))
            .markFailed(eq(1L), eq("lease-1"), eq("Delivery failed: java.lang.RuntimeException"), eq(10), anyLong());
    }

    @Test
    void interruptedDeliveryStopsTheBatchAndPreservesTheInterrupt() throws Exception {
        RoutingOutboxRecord first = pendingRecord(1L, "ALIAS_STATE", "key1", "{}");
        RoutingOutboxRecord second = pendingRecord(2L, "ALIAS_STATE", "key2", "{}");
        claimInOrder(List.of(List.of(first), List.of(second), List.of()));
        doThrow(new InterruptedException("delivery cancelled")).when(deliveryTarget).deliver("key1", "{}");

        try {
            RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();

            assertThat(result).isEqualTo(new RoutingOutboxDispatcher.DispatchResult(1, 0, false));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(outboxRepository, times(1)).claimPending(eq(1), anyString(), anyLong());
            verify(outboxRepository)
                .markFailed(eq(1L), eq("lease-1"), eq("Delivery failed: java.lang.InterruptedException"), eq(10),
                        anyLong());
            verify(deliveryTarget, never()).deliver("key2", "{}");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void alreadyInterruptedDispatchDoesNotClaimRecords() {
        try {
            Thread.currentThread().interrupt();

            assertThat(dispatcher.dispatch()).isEqualTo(new RoutingOutboxDispatcher.DispatchResult(0, 0, false));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(outboxRepository, never()).claimPending(anyInt(), anyString(), anyLong());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void dispatchUnknownEventTypeMarksFailedAndDoesNotDeliver() throws Exception {
        RoutingOutboxRecord unknown = pendingRecord(99L, "UNKNOWN_TYPE", "key99", "{}");
        claimInOrder(List.of(Collections.singletonList(unknown), Collections.emptyList()));
        when(outboxRepository.markFailed(anyLong(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);

        RoutingOutboxDispatcher.DispatchResult result = dispatcher.dispatch();
        // Record is marked FAILED (not DELIVERED) so an operator can investigate.
        assertThat(result.delivered()).isZero();
        verify(outboxRepository, never()).markDelivered(anyLong(), anyString());
        verify(outboxRepository).markFailed(eq(99L), eq("lease-99"), anyString(), eq(10), anyLong());
        // No projection store.write for unknown types.
        verify(deliveryTarget, never()).deliver(anyString(), anyString());
    }

    @Test
    void dispatchReturnsZeroImmediatelyWhenPreviousCycleStillRunning() throws Exception {
        // Simulate a slow first dispatch cycle by making claimPending block until a latch is
        // released.
        // A second concurrent dispatch call must return 0 without touching the repository.
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);

        when(outboxRepository.claimPending(eq(1), anyString(), anyLong()))
            .thenAnswer(inv -> {
                // signal that first dispatch entered claimPending
                dispatchStarted.countDown();
                // block until test releases it
                releaseDispatch.await();
                return Collections.emptyList();
            });

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<RoutingOutboxDispatcher.DispatchResult> firstDispatch = exec.submit(dispatcher::dispatch);
        // Wait until the first dispatch cycle has actually started (inside claimPending).
        dispatchStarted.await(2, TimeUnit.SECONDS);
        // Second call must be rejected immediately by the AtomicBoolean guard.
        RoutingOutboxDispatcher.DispatchResult second = dispatcher.dispatch();
        assertThat(second.claimed()).as("concurrent dispatch should be skipped").isZero();
        // Release the first cycle and verify it completed normally.
        releaseDispatch.countDown();
        RoutingOutboxDispatcher.DispatchResult first = firstDispatch.get(2, TimeUnit.SECONDS);
        assertThat(first.claimed()).as("first dispatch should complete after release").isZero();
        exec.shutdown();
    }

    @Test
    void awaitIdleWaitsForTheActiveDispatchWithoutPolling() throws Exception {
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        when(outboxRepository.claimPending(eq(1), anyString(), anyLong())).thenAnswer(inv -> {
            dispatchStarted.countDown();
            releaseDispatch.await();
            return Collections.emptyList();
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RoutingOutboxDispatcher.DispatchResult> activeDispatch = executor.submit(dispatcher::dispatch);
            assertThat(dispatchStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatcher.awaitIdle(0)).isFalse();

            Future<Boolean> idle = executor.submit(() -> {
                waiterStarted.countDown();
                return dispatcher.awaitIdle(1_000);
            });
            assertThat(waiterStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(idle.isDone()).isFalse();

            releaseDispatch.countDown();
            assertThat(activeDispatch.get(1, TimeUnit.SECONDS).claimed()).isZero();
            assertThat(idle.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatcher.awaitIdle(0)).isTrue();
        } finally {
            releaseDispatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void constructorThrowsOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new RoutingOutboxDispatcher(null, deliveryTarget, policy(10)));
        assertThatNullPointerException().isThrownBy(() -> new RoutingOutboxDispatcher(outboxRepository, null, policy(10)));
        assertThatNullPointerException().isThrownBy(() -> new RoutingOutboxDispatcher(outboxRepository, deliveryTarget,
                null));
    }

    @Test
    void awaitIdleRejectsOnlyNegativeBudgets() {
        assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.awaitIdle(-1));
    }

    @Test
    void policyThrowsOnNonPositiveBatchSize() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy(0));
        assertThatIllegalArgumentException().isThrownBy(() -> policy(-1));
    }

    @Test
    void dispatchDoesNotCountDeliveryWhenLeaseTransitionIsFenced() throws Exception {
        RoutingOutboxRecord record = pendingRecord(7L, "ALIAS_STATE", "key7", "{}");
        claimInOrder(List.of(Collections.singletonList(record), Collections.emptyList()));
        when(outboxRepository.markDelivered(7L, "lease-7")).thenReturn(0);

        assertThat(dispatcher.dispatch().delivered()).isZero();
        verify(deliveryTarget).deliver("key7", "{}");
    }

    @Test
    void deadLetterLogDoesNotExposeTheDeliveryStorageKey() throws Exception {
        String sensitiveStorageKey = "compileflow.deployment.alias.secret-key-material";
        RoutingOutboxRecord record = pendingRecord(9L, "ALIAS_STATE", sensitiveStorageKey, "{}");
        claimInOrder(List.of(Collections.singletonList(record), Collections.emptyList()));
        when(outboxRepository.markFailed(anyLong(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        doThrow(new RuntimeException("projection store unavailable"))
            .when(deliveryTarget)
            .deliver(eq(sensitiveStorageKey), anyString());
        Logger logger = (Logger) LoggerFactory.getLogger(RoutingOutboxDispatcher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RoutingOutboxDispatcher oneAttemptDispatcher = new RoutingOutboxDispatcher(outboxRepository, deliveryTarget,
                    new RoutingOutboxDispatchPolicy(1, Duration.ofSeconds(30), 1, Duration.ofSeconds(1),
                            Duration.ofSeconds(1)));

            assertThat(oneAttemptDispatcher.dispatch().delivered()).isZero();

            assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(sensitiveStorageKey));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void policyCapsExponentialRetryDelay() {
        RoutingOutboxDispatchPolicy policy =
                new RoutingOutboxDispatchPolicy(10, Duration.ofSeconds(30), 4, Duration.ofSeconds(2),
                        Duration.ofSeconds(5));

        assertThat(policy.retryCeilingMs(0)).isEqualTo(2000L);
        assertThat(policy.retryCeilingMs(1)).isEqualTo(4000L);
        assertThat(policy.retryCeilingMs(2)).isEqualTo(5000L);
        assertThat(policy.retryCeilingMs(Integer.MAX_VALUE)).isEqualTo(5000L);
        assertThat(policy.retryDelayMs(2)).isBetween(1L, 5000L);
    }

    @Test
    void policyJittersTheLargestRepresentableMillisecondDurationWithoutOverflow() {
        RoutingOutboxDispatchPolicy policy = new RoutingOutboxDispatchPolicy(10, Duration.ofSeconds(30), 4,
                Duration.ofMillis(Long.MAX_VALUE), Duration.ofMillis(Long.MAX_VALUE));

        assertThat(policy.retryCeilingMs(0)).isEqualTo(Long.MAX_VALUE);
        assertThat(policy.retryDelayMs(0)).isPositive().isLessThan(Long.MAX_VALUE);
    }

    @Test
    void policyRejectsDurationsThatCannotBeConsumedExactlyAsMilliseconds() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RoutingOutboxDispatchPolicy(10, Duration.ofNanos(1_000_001), 4, Duration.ofSeconds(2),
                    Duration.ofSeconds(5)))
            .withMessageContaining("whole-millisecond");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RoutingOutboxDispatchPolicy(10, Duration.ofSeconds(30), 4, Duration.ofSeconds(2),
                    Duration.ofMillis(Long.MAX_VALUE).plusMillis(1)))
            .withMessageContaining("representable as a long");
    }

    private RoutingOutboxRecord pendingRecord(long id, String eventType, String routingKey, String payload) {
        return new RoutingOutboxRecord(id, eventType, "default", "test.flow", null, routingKey, payload,
                RoutingOutboxRecord.Status.PROCESSING, 0, null, null, "lease-" + id, System.currentTimeMillis() + 30000L,
                System.currentTimeMillis(), System.currentTimeMillis());
    }

    private void claimInOrder(List<List<RoutingOutboxRecord>> claims) {
        AtomicInteger nextClaim = new AtomicInteger();
        when(outboxRepository.claimPending(eq(1), anyString(), anyLong())).thenAnswer(ignored -> {
            int index = nextClaim.getAndIncrement();
            return index < claims.size() ? claims.get(index) : Collections.emptyList();
        });
    }
}
