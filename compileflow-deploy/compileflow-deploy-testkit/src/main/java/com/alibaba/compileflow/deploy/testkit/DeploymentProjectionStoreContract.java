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
package com.alibaba.compileflow.deploy.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executable conformance contract for deployment projection stores.
 *
 * <p>The contract fixes observable semantics only: exact-content CAS, per-key linearizability,
 * read-after-success, eventually observable post-subscription updates, and idempotent close. It
 * intentionally permits duplicate or coalesced update notifications.
 *
 * @author yusu
 */
public abstract class DeploymentProjectionStoreContract {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private DeploymentProjectionStore store;

    /**
     * Creates a new empty store for the current test.
     */
    protected abstract DeploymentProjectionStore createEmptyStore() throws Exception;

    @BeforeEach
    final void initializeStore() throws Exception {
        store = createEmptyStore();
    }

    @Test
    void createIfAbsentAndReadAfterSuccess() throws Exception {
        assertThat(store.read("contract/create", TIMEOUT)).isNull();
        assertThat(store.compareAndSet("contract/create", null, "v1", "application/json", TIMEOUT)).isTrue();
        assertThat(store.read("contract/create", TIMEOUT)).isEqualTo("v1");
    }

    @Test
    void compareAndSetUsesExactContentAndPreservesFailedCas() throws Exception {
        assertThat(store.compareAndSet("contract/exact", null, "Alpha", "text/plain", TIMEOUT)).isTrue();
        assertThat(store.compareAndSet("contract/exact", "alpha", "lost", "text/plain", TIMEOUT)).isFalse();
        assertThat(store.compareAndSet("contract/exact", "Alpha ", "lost", "text/plain", TIMEOUT)).isFalse();
        assertThat(store.compareAndSet("contract/exact", "Alpha", "Beta", "text/plain", TIMEOUT)).isTrue();
        assertThat(store.read("contract/exact", TIMEOUT)).isEqualTo("Beta");
    }

    @Test
    void concurrentCreateIsLinearizablePerKey() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                String content = "candidate-" + index;
                attempts.add(executor.submit(() -> {
                    start.await();
                    return store.compareAndSet("contract/race", null, content, "text/plain", TIMEOUT);
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isOne();
            assertThat(store.read("contract/race", TIMEOUT)).startsWith("candidate-");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void subscriptionEventuallyObservesAnUpdateEstablishedAfterSubscribe() throws Exception {
        CountDownLatch observed = new CountDownLatch(1);
        DeploymentProjectionStore.Subscription subscription =
                store.subscribe("contract/watch", (key, content) -> {
            if ("contract/watch".equals(key) && "ready".equals(content)) {
                observed.countDown();
            }
        }, TIMEOUT);
        try {
            assertThat(store.compareAndSet("contract/watch", null, "ready", "text/plain", TIMEOUT)).isTrue();
            assertThat(observed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            subscription.close();
        }
    }

    @Test
    void subscriptionCloseIsIdempotent() throws Exception {
        DeploymentProjectionStore.Subscription subscription =
                store.subscribe("contract/close", (key, content) -> {}, TIMEOUT);
        subscription.close();
        subscription.close();
    }

    @Test
    void failedCasDoesNotCreateState() throws Exception {
        assertThat(store.compareAndSet("contract/absent", "unexpected", "lost", "text/plain", TIMEOUT)).isFalse();
        assertThat(store.read("contract/absent", TIMEOUT)).isNull();
    }
}
