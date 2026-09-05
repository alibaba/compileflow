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
package com.alibaba.compileflow.deploy.api.sync.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryDeploymentSyncChannelTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void compareAndSetCreatesReadableValue() throws Exception {
        InMemoryDeploymentSyncChannel ch = new InMemoryDeploymentSyncChannel();
        assertThat(ch.read("k1", TIMEOUT)).isNull();
        assertThat(ch.compareAndSet("k1", null, "v1", "json", TIMEOUT)).isTrue();
        assertThat(ch.read("k1", TIMEOUT)).isEqualTo("v1");
    }

    @Test
    void subscriptionReceivesSuccessfulUpdatesUntilClosed() throws Exception {
        InMemoryDeploymentSyncChannel ch = new InMemoryDeploymentSyncChannel();
        AtomicInteger called = new AtomicInteger();

        DeploymentSyncChannel.Subscription sub = ch.subscribe("k1", (key, content) -> called.incrementAndGet(), TIMEOUT);
        assertThat(ch.compareAndSet("k1", null, "v1", "json", TIMEOUT)).isTrue();
        assertThat(called.get()).isEqualTo(1);

        sub.close();
        assertThat(ch.compareAndSet("k1", "v1", "v2", "json", TIMEOUT)).isTrue();
        assertThat(called.get()).isEqualTo(1);
        assertThat(ch.subscriptionKeyCount()).isZero();
    }

    @Test
    void compareAndSetIsAtomicAndNotifiesOnlySuccessfulUpdates() throws Exception {
        InMemoryDeploymentSyncChannel ch = new InMemoryDeploymentSyncChannel();
        AtomicInteger called = new AtomicInteger();
        ch.subscribe("k1", (key, content) -> called.incrementAndGet(), TIMEOUT);

        assertThat(ch.compareAndSet("k1", null, "v1", "text", TIMEOUT)).isTrue();
        assertThat(ch.compareAndSet("k1", null, "lost", "text", TIMEOUT)).isFalse();
        assertThat(ch.compareAndSet("k1", "wrong", "lost", "text", TIMEOUT)).isFalse();
        assertThat(ch.compareAndSet("k1", "v1", "v2", "text", TIMEOUT)).isTrue();

        assertThat(ch.read("k1", TIMEOUT)).isEqualTo("v2");
        assertThat(called).hasValue(2);
    }

    @Test
    void closedSubscriptionSuppressesAnAlreadyQueuedCallback() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        AtomicInteger called = new AtomicInteger();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        DeploymentSyncChannel.Subscription subscription =
                channel.subscribe("k1", new DeploymentSyncChannel.UpdateCallback() {
            @Override
            public void onUpdate(String key, String content) {
                called.incrementAndGet();
            }

            @Override
            public Executor executor() {
                return queued::set;
            }
        }, TIMEOUT);

        assertThat(channel.compareAndSet("k1", null, "v1", "text", TIMEOUT)).isTrue();
        subscription.close();
        queued.get().run();

        assertThat(called).hasValue(0);
    }

    @Test
    void rejectsInvalidOperationArgumentsConsistently() {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();

        assertThatIllegalArgumentException().isThrownBy(() -> channel.read("k1", Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> channel.compareAndSet(" ", null, "v1", "text", TIMEOUT));
        assertThatIllegalArgumentException().isThrownBy(() -> channel.compareAndSet("k1", null, "v1", " ", TIMEOUT));
    }
}
