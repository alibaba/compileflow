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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChannelRoutingStateDeliveryTargetTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String KEY =
            RoutingStateKeys.aliasState("compileflow.deployment.", "default", "order.flow", "production");

    private static String payload(String stableVersion, long revision) {
        return RoutingStatePayloads.aliasStateJson("default", "order.flow", "production", stableVersion, null, null,
                revision, "test", 1_000L + revision);
    }

    @Test
    void staleDeliveryIsAnIdempotentNoOp() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ChannelRoutingStateDeliveryTarget target = new ChannelRoutingStateDeliveryTarget(channel, TIMEOUT);

        target.deliver(KEY, payload("v2", 2L));
        target.deliver(KEY, payload("v1", 1L));

        assertThat(RoutingStateParser.parse(channel.read(KEY, TIMEOUT)).getAliasRevision()).isEqualTo(2L);
    }

    @Test
    void conflictingContentAtOneRevisionFailsClosed() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ChannelRoutingStateDeliveryTarget target = new ChannelRoutingStateDeliveryTarget(channel, TIMEOUT);
        target.deliver(KEY, payload("v1", 1L));

        assertThatThrownBy(() -> target.deliver(KEY, payload("v2", 1L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("conflicting content at revision 1");
    }

    @Test
    void lateOlderWriterCannotOverwriteAConcurrentNewerRevision() throws Exception {
        DelayedOlderCompareAndSetChannel channel = new DelayedOlderCompareAndSetChannel();
        ChannelRoutingStateDeliveryTarget target = new ChannelRoutingStateDeliveryTarget(channel, TIMEOUT);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> older = executor.submit(() -> {
                target.deliver(KEY, payload("v1", 1L));
                return null;
            });
            assertThat(channel.olderCompareAndSetEntered.await(5, TimeUnit.SECONDS)).isTrue();

            target.deliver(KEY, payload("v2", 2L));
            channel.allowOlderCompareAndSet.countDown();
            older.get(5, TimeUnit.SECONDS);

            assertThat(RoutingStateParser.parse(channel.read(KEY, TIMEOUT)).getAliasRevision()).isEqualTo(2L);
        } finally {
            channel.allowOlderCompareAndSet.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void boundedContentionIsDelegatedToDurableOutboxRetry() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        DeploymentSyncChannel channel = new DeploymentSyncChannel() {
            @Override
            public String read(String key, Duration timeout) {
                reads.incrementAndGet();
                return null;
            }

            @Override
            public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                    Duration timeout) {
                writes.incrementAndGet();
                return false;
            }

            @Override
            public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) {
                throw new UnsupportedOperationException();
            }
        };
        ChannelRoutingStateDeliveryTarget target = new ChannelRoutingStateDeliveryTarget(channel, TIMEOUT);

        assertThatThrownBy(() -> target.deliver(KEY, payload("v1", 1L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("after 2 compare-and-set attempts");
        assertThat(reads).hasValue(2);
        assertThat(writes).hasValue(2);
    }

    private static final class DelayedOlderCompareAndSetChannel implements DeploymentSyncChannel {
        private final InMemoryDeploymentSyncChannel delegate = new InMemoryDeploymentSyncChannel();
        private final CountDownLatch olderCompareAndSetEntered = new CountDownLatch(1);
        private final CountDownLatch allowOlderCompareAndSet = new CountDownLatch(1);

        @Override
        public String read(String key, Duration timeout) throws Exception {
            return delegate.read(key, timeout);
        }

        @Override
        public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                Duration timeout) throws Exception {
            if (RoutingStateParser.parse(content).getAliasRevision() == 1L) {
                olderCompareAndSetEntered.countDown();
                if (!allowOlderCompareAndSet.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release older CAS");
                }
            }
            return delegate.compareAndSet(key, expectedContent, content, contentType, timeout);
        }

        @Override
        public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception {
            return delegate.subscribe(key, callback, timeout);
        }
    }
}
