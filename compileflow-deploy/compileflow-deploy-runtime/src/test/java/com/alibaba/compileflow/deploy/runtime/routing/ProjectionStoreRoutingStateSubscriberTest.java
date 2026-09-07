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
package com.alibaba.compileflow.deploy.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProjectionStoreRoutingStateSubscriberTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(1);

    private static String key() {
        return key("demo.flow");
    }

    private static String key(String code) {
        return RoutingStateKeys.aliasState("compileflow.deployment.", "default", code, "prod");
    }

    private static String payload(String stableVersion, long updatedAt, long revision) {
        return payload("demo.flow", stableVersion, updatedAt, revision);
    }

    private static String payload(String code, String stableVersion, long updatedAt, long revision) {
        if (stableVersion == null) {
            return RoutingStateCodec.aliasTombstoneJson("default", code, "prod", revision, "test", updatedAt);
        }
        return RoutingStateCodec.aliasStateJson("default", code, "prod", stableVersion, null, null, revision, "test",
                updatedAt);
    }

    @Test
    void rejectsANullStateCallbackBeforeStartingSubscriptions() {
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(new InMemoryDeploymentProjectionStore(),
                Collections.singletonList(key()), OPERATION_TIMEOUT);

        assertThatThrownBy(() -> subscriber.start(null)).isInstanceOf(NullPointerException.class).hasMessage(
                "onDesiredState");

        subscriber.start(ignored -> {});
        subscriber.close();
    }

    @Test
    void rejectsUnicodeWhitespaceAroundConfiguredStateKey() {
        assertThatThrownBy(() -> new ProjectionStoreRoutingStateSubscriber(new InMemoryDeploymentProjectionStore(),
                Collections.singletonList("\u00a0" + key()), OPERATION_TIMEOUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("state keys must be non-blank and trimmed");
    }

    @Test
    void closePermanentlyTerminatesTheSubscriber() {
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(new InMemoryDeploymentProjectionStore(),
                Collections.singletonList(key()), OPERATION_TIMEOUT);

        subscriber.close();

        assertThatThrownBy(() -> subscriber.start(ignored -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("DesiredRoutingStateSubscriber is closed");
    }

    @Test
    void failsStartupWhenConfiguredInitialRouteIsCorrupt() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        assertThat(projectionStore.compareAndSet(key(), null,
                payload("v1", System.currentTimeMillis(), 1L).replace("aliasState", "unknown"), "json",
                OPERATION_TIMEOUT))
            .isTrue();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);

        assertThatThrownBy(() -> subscriber.start(ignored -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to initialize routing state subscriptions")
            .hasRootCauseMessage("Unsupported routing state kind: unknown");
        subscriber.close();
    }

    @Test
    void failsStartupWhenInitialPayloadIdentityDoesNotMatchTheSubscribedKey() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        assertThat(projectionStore.compareAndSet(key(), null,
                RoutingStateCodec.aliasStateJson("default", "other.flow", "prod", "v1", null, null, 1L, "test",
                        System.currentTimeMillis()), "json", OPERATION_TIMEOUT))
            .isTrue();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);

        assertThatThrownBy(() -> subscriber.start(ignored -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to initialize routing state subscriptions")
            .hasRootCauseMessage("Routing key and payload identity do not match");
        subscriber.close();
    }

    @Test
    void invalidLaterInitialRouteDoesNotPartiallyEmitEarlierRoutes() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        String corruptKey = RoutingStateKeys.aliasState("compileflow.deployment.", "default", "corrupt.flow", "prod");
        assertThat(projectionStore.compareAndSet(key(), null, payload("v1", System.currentTimeMillis(), 1L), "json",
                OPERATION_TIMEOUT))
            .isTrue();
        assertThat(projectionStore.compareAndSet(corruptKey, null, "{\"kind\":\"unknown\"}", "json", OPERATION_TIMEOUT))
            .isTrue();
        ProjectionStoreRoutingStateSubscriber subscriber =
                new ProjectionStoreRoutingStateSubscriber(projectionStore, List.of(key(), corruptKey), OPERATION_TIMEOUT);
        CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> subscriber.start(events::add))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to initialize routing state subscriptions");
        assertThat(events).isEmpty();
        subscriber.close();
    }

    @Test
    void failsStartupWhenInitialStateCannotBeApplied() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        assertThat(projectionStore.compareAndSet(key(), null, payload("v1", System.currentTimeMillis(), 1L), "json",
                OPERATION_TIMEOUT))
            .isTrue();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);

        assertThatThrownBy(() -> subscriber.start(ignored -> {
            throw new IllegalStateException("runtime admission rejected");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to initialize routing state subscriptions")
            .hasRootCauseMessage("runtime admission rejected");
        subscriber.close();
    }

    @Test
    void emitsHigherDesiredRevisionWithoutPublishingLocalReadyState() {
        Fixture fixture = new Fixture();
        try {
            long now = System.currentTimeMillis();
            fixture.write("v1", now, 100);
            fixture.write("v2", now - 1_000, 101);

            assertThat(fixture.events)
                .extracting(DesiredRoutingState::getStableVersion, DesiredRoutingState::getRevision)
                .containsExactly(tuple("v1", 100L), tuple("v2", 101L));
            assertThat(fixture.localRoutingState.getAliasRouteState().resolve("default", "demo.flow", "prod")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    void lowerAndDuplicateRevisionsAreIgnored() {
        Fixture fixture = new Fixture();
        try {
            long now = System.currentTimeMillis();
            fixture.write("v2", now, 100);
            fixture.write("v3", now + 1_000, 99);
            fixture.write("v4", now + 2_000, 100);

            assertThat(fixture.events).extracting(DesiredRoutingState::getRevision).containsExactly(100L);
            assertThat(fixture.localRoutingState.getAliasRouteState().resolve("default", "demo.flow", "prod")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    void oldInitialSnapshotRemainsAuthoritative() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        String key = key();
        long oldTimestamp = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
        assertThat(projectionStore.compareAndSet(key, null, payload("v1", oldTimestamp, 7), "json", OPERATION_TIMEOUT))
            .isTrue();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key), OPERATION_TIMEOUT);
        CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();
        try {
            subscriber.start(events::add);

            assertThat(events).extracting(DesiredRoutingState::getRevision).containsExactly(7L);
            assertThat(localRoutingState.getAliasRouteState().resolve("default", "demo.flow", "prod")).isEmpty();
        } finally {
            subscriber.close();
        }
    }

    @Test
    void tombstoneRemovesAliasAndBlocksDelayedResurrection() {
        Fixture fixture = new Fixture();
        try {
            long now = System.currentTimeMillis();
            fixture.write("v1", now, 100);
            fixture.write(null, now + 1, 101);
            fixture.write("v1", now + 2, 100);

            assertThat(fixture.events)
                .extracting(DesiredRoutingState::isDeleted, DesiredRoutingState::getRevision)
                .containsExactly(tuple(false, 100L), tuple(true, 101L));
            assertThat(fixture.localRoutingState.getAliasRouteState().resolve("default", "demo.flow", "prod")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    void ignoresCorruptLiveUpdateAndRetainsLastValidRoute() {
        Fixture fixture = new Fixture();
        try {
            long now = System.currentTimeMillis();
            fixture.write("v1", now, 100);
            fixture.writeRaw("{\"kind\":\"unknown\"}");

            assertThat(fixture.events).extracting(DesiredRoutingState::getRevision).containsExactly(100L);
            assertThat(fixture.localRoutingState.getAliasRouteState().resolve("default", "demo.flow", "prod")).isEmpty();
        } finally {
            fixture.close();
        }
    }

    @Test
    void ignoresLiveUpdateWithMismatchedIdentityWithoutAdvancingRevision() {
        Fixture fixture = new Fixture();
        try {
            long now = System.currentTimeMillis();
            fixture.write("v1", now, 100);
            fixture.writeRaw(RoutingStateCodec.aliasStateJson("default", "other.flow", "prod", "v2", null, null, 200L,
                    "test", now + 1));
            fixture.write("v3", now + 2, 101);

            assertThat(fixture.events)
                .extracting(DesiredRoutingState::getStableVersion, DesiredRoutingState::getRevision)
                .containsExactly(tuple("v1", 100L), tuple("v3", 101L));
        } finally {
            fixture.close();
        }
    }

    @Test
    void rejectedLiveStateDoesNotAdvanceTheAcceptedRevision() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        AtomicBoolean rejectFirstUpdate = new AtomicBoolean(true);
        CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();
        subscriber.start(change -> {
            if (rejectFirstUpdate.compareAndSet(true, false)) {
                throw new IllegalStateException("temporary admission failure");
            }
            events.add(change);
        });
        try {
            long now = System.currentTimeMillis();
            assertThat(projectionStore.compareAndSet(key(), null, payload("v1", now, 100L), "json", OPERATION_TIMEOUT)).isTrue();
            assertThat(projectionStore.compareAndSet(key(), payload("v1", now, 100L), payload("v2", now + 1L, 101L),
                    "json", OPERATION_TIMEOUT))
                .isTrue();

            assertThat(events)
                .extracting(DesiredRoutingState::getStableVersion, DesiredRoutingState::getRevision)
                .containsExactly(tuple("v2", 101L));
        } finally {
            subscriber.close();
        }
    }

    @Test
    void stopDuringStartupClosesTheSubscriptionCreatedByThatStartup() throws Exception {
        BlockingSubscribeProjectionStore projectionStore = new BlockingSubscribeProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try {
                subscriber.start(ignored -> {});
            } catch (Throwable failure) {
                startupFailure.set(failure);
            }
        }, "routing-subscriber-start");
        Thread stopper = new Thread(subscriber::stop, "routing-subscriber-stop");

        starter.start();
        assertThat(projectionStore.subscribeEntered.await(5, TimeUnit.SECONDS)).isTrue();
        stopper.start();
        projectionStore.allowSubscribeReturn.countDown();
        starter.join(TimeUnit.SECONDS.toMillis(5));
        stopper.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(starter.isAlive()).isFalse();
        assertThat(stopper.isAlive()).isFalse();
        assertThat(startupFailure.get()).isNull();
        assertThat(projectionStore.activeSubscriptions.get()).isZero();
        assertThat(projectionStore.closedSubscriptions.get()).isOne();
    }

    @Test
    void updateBetweenSubscriptionAndInitialReadIsNotLost() throws Exception {
        ReadDuringStartupProjectionStore projectionStore = new ReadDuringStartupProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try {
                subscriber.start(events::add);
            } catch (Throwable failure) {
                startupFailure.set(failure);
            }
        }, "routing-subscriber-start");
        try {
            starter.start();
            assertThat(projectionStore.readEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(projectionStore.subscribed.get()).isTrue();
            assertThat(projectionStore.compareAndSet(key(), null, payload("v1", System.currentTimeMillis(), 1L), "json",
                    OPERATION_TIMEOUT))
                .isTrue();
            projectionStore.allowReadReturn.countDown();
            starter.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(starter.isAlive()).isFalse();
            assertThat(startupFailure.get()).isNull();
            assertThat(events).extracting(DesiredRoutingState::getRevision).containsExactly(1L);
        } finally {
            projectionStore.allowReadReturn.countDown();
            subscriber.close();
        }
    }

    @Test
    void startupBufferCoalescesIntermediateValuesPerKey() throws Exception {
        ReadDuringStartupProjectionStore projectionStore = new ReadDuringStartupProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try {
                subscriber.start(events::add);
            } catch (Throwable failure) {
                startupFailure.set(failure);
            }
        }, "routing-subscriber-start");
        try {
            starter.start();
            assertThat(projectionStore.readEntered.await(5, TimeUnit.SECONDS)).isTrue();
            String first = payload("v1", System.currentTimeMillis(), 1L);
            String second = payload("v2", System.currentTimeMillis() + 1L, 2L);
            assertThat(projectionStore.compareAndSet(key(), null, first, "json", OPERATION_TIMEOUT)).isTrue();
            assertThat(projectionStore.compareAndSet(key(), first, second, "json", OPERATION_TIMEOUT)).isTrue();
            projectionStore.allowReadReturn.countDown();
            starter.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(starter.isAlive()).isFalse();
            assertThat(startupFailure.get()).isNull();
            assertThat(events)
                .extracting(DesiredRoutingState::getStableVersion, DesiredRoutingState::getRevision)
                .containsExactly(tuple("v2", 2L));
        } finally {
            projectionStore.allowReadReturn.countDown();
            subscriber.close();
        }
    }

    @Test
    void stopWaitsForAnInFlightCallbackAndRejectsLaterUpdates() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch allowCallbackReturn = new CountDownLatch(1);
        CountDownLatch stopAttempted = new CountDownLatch(1);
        CountDownLatch stopReturned = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        subscriber.start(ignored -> {
            callbackEntered.countDown();
            try {
                if (!allowCallbackReturn.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to finish callback");
                }
                applied.incrementAndGet();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        Thread writer = new Thread(() -> projectionStore.compareAndSet(key(), null,
                        payload("v1", System.currentTimeMillis(), 1L), "json", OPERATION_TIMEOUT),
                "routing-projection-store-writer");
        Thread stopper = new Thread(() -> {
            stopAttempted.countDown();
            subscriber.stop();
            stopReturned.countDown();
        }, "routing-subscriber-stop");
        try {
            writer.start();
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            stopper.start();
            assertThat(stopAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stopReturned.await(100, TimeUnit.MILLISECONDS)).isFalse();

            allowCallbackReturn.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(5));
            stopper.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(writer.isAlive()).isFalse();
            assertThat(stopper.isAlive()).isFalse();
            assertThat(applied).hasValue(1);

            String current = projectionStore.read(key(), OPERATION_TIMEOUT);
            assertThat(projectionStore.compareAndSet(key(), current, payload("v2", System.currentTimeMillis(), 2L),
                    "json", OPERATION_TIMEOUT))
                .isTrue();
            assertThat(applied).hasValue(1);
        } finally {
            allowCallbackReturn.countDown();
            subscriber.close();
        }
    }

    @Test
    void stopFromCallbackFailsFastWithoutCorruptingSubscriberState() {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger applied = new AtomicInteger();
        subscriber.start(ignored -> {
            applied.incrementAndGet();
            try {
                subscriber.stop();
            } catch (Throwable callbackFailure) {
                failure.set(callbackFailure);
            }
        });
        try {
            assertThat(projectionStore.compareAndSet(key(), null, payload("v1", System.currentTimeMillis(), 1L), "json",
                    OPERATION_TIMEOUT))
                .isTrue();

            assertThat(applied).hasValue(1);
            assertThat(failure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DesiredRoutingStateSubscriber cannot be stopped from its callback");
        } finally {
            subscriber.close();
        }
    }

    @Test
    void callbackLifecycleCallCannotDeadlockConcurrentStop() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProjectionStoreRoutingStateSubscriber subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore,
                Collections.singletonList(key()), OPERATION_TIMEOUT);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch invokeCallbackStop = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        subscriber.start(ignored -> {
            callbackEntered.countDown();
            try {
                assertThat(invokeCallbackStop.await(5, TimeUnit.SECONDS)).isTrue();
                subscriber.stop();
            } catch (Throwable failure) {
                callbackFailure.set(failure);
            }
        });
        Thread writer = new Thread(() -> projectionStore.compareAndSet(key(), null,
                        payload("v1", System.currentTimeMillis(), 1L), "json", OPERATION_TIMEOUT),
                "routing-projection-store-writer");
        Thread stopper = new Thread(subscriber::stop, "routing-subscriber-stop");
        try {
            writer.start();
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            stopper.start();
            invokeCallbackStop.countDown();

            writer.join(TimeUnit.SECONDS.toMillis(5));
            stopper.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(writer.isAlive()).isFalse();
            assertThat(stopper.isAlive()).isFalse();
            assertThat(callbackFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DesiredRoutingStateSubscriber cannot be stopped from its callback");
        } finally {
            invokeCallbackStop.countDown();
            subscriber.close();
        }
    }

    @Test
    void differentAliasKeysDoNotBlockEachOthersCallbacks() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        String secondKey = key("second.flow");
        ProjectionStoreRoutingStateSubscriber subscriber =
                new ProjectionStoreRoutingStateSubscriber(projectionStore, List.of(key(), secondKey), OPERATION_TIMEOUT);
        CountDownLatch firstCallbackEntered = new CountDownLatch(1);
        CountDownLatch finishFirstCallback = new CountDownLatch(1);
        CountDownLatch secondCallbackCompleted = new CountDownLatch(1);
        subscriber.start(change -> {
            if ("demo.flow".equals(change.getCode())) {
                firstCallbackEntered.countDown();
                try {
                    assertThat(finishFirstCallback.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            } else {
                secondCallbackCompleted.countDown();
            }
        });
        Thread firstWriter = new Thread(() -> projectionStore.compareAndSet(key(), null,
                        payload("demo.flow", "v1", System.currentTimeMillis(), 1L), "json", OPERATION_TIMEOUT),
                "first-routing-projection-store-writer");
        Thread secondWriter = new Thread(() -> projectionStore.compareAndSet(secondKey, null,
                        payload("second.flow", "v1", System.currentTimeMillis(), 1L), "json", OPERATION_TIMEOUT),
                "second-routing-projection-store-writer");
        try {
            firstWriter.start();
            assertThat(firstCallbackEntered.await(5, TimeUnit.SECONDS)).isTrue();
            secondWriter.start();

            assertThat(secondCallbackCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            finishFirstCallback.countDown();
            firstWriter.join(TimeUnit.SECONDS.toMillis(5));
            secondWriter.join(TimeUnit.SECONDS.toMillis(5));
            subscriber.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        private final LocalRoutingState localRoutingState = new LocalRoutingState();
        private final CopyOnWriteArrayList<DesiredRoutingState> events = new CopyOnWriteArrayList<>();
        private final ProjectionStoreRoutingStateSubscriber subscriber;

        private Fixture() {
            subscriber = new ProjectionStoreRoutingStateSubscriber(projectionStore, Collections.singletonList(key()),
                    OPERATION_TIMEOUT);
            subscriber.start(events::add);
        }

        private void write(String stableVersion, long updatedAt, long revision) {
            writeRaw(payload(stableVersion, updatedAt, revision));
        }

        private void writeRaw(String payload) {
            String key = key();
            String current = projectionStore.read(key, OPERATION_TIMEOUT);
            assertThat(projectionStore.compareAndSet(key, current, payload, "json", OPERATION_TIMEOUT)).isTrue();
        }

        @Override
        public void close() {
            subscriber.close();
        }
    }

    private static final class BlockingSubscribeProjectionStore implements DeploymentProjectionStore {
        private final CountDownLatch subscribeEntered = new CountDownLatch(1);
        private final CountDownLatch allowSubscribeReturn = new CountDownLatch(1);
        private final AtomicInteger activeSubscriptions = new AtomicInteger();
        private final AtomicInteger closedSubscriptions = new AtomicInteger();

        @Override
        public String read(String key, Duration timeout) {
            return null;
        }

        @Override
        public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception {
            subscribeEntered.countDown();
            if (!allowSubscribeReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish subscription");
            }
            activeSubscriptions.incrementAndGet();
            AtomicBoolean open = new AtomicBoolean(true);
            return () -> {
                if (open.compareAndSet(true, false)) {
                    activeSubscriptions.decrementAndGet();
                    closedSubscriptions.incrementAndGet();
                }
            };
        }
    }

    private static final class ReadDuringStartupProjectionStore implements DeploymentProjectionStore {
        private final InMemoryDeploymentProjectionStore delegate = new InMemoryDeploymentProjectionStore();
        private final CountDownLatch readEntered = new CountDownLatch(1);
        private final CountDownLatch allowReadReturn = new CountDownLatch(1);
        private final AtomicBoolean subscribed = new AtomicBoolean();

        @Override
        public String read(String key, Duration timeout) throws Exception {
            String valueBeforeWait = delegate.read(key, timeout);
            readEntered.countDown();
            if (!allowReadReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish initial read");
            }
            return valueBeforeWait;
        }

        @Override
        public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                Duration timeout) throws Exception {
            return delegate.compareAndSet(key, expectedContent, content, contentType, timeout);
        }

        @Override
        public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception {
            Subscription subscription = delegate.subscribe(key, callback, timeout);
            subscribed.set(true);
            return subscription;
        }
    }
}
