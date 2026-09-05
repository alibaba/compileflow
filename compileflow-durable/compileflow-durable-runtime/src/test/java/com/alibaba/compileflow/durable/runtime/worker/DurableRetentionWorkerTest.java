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
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableRetentionWorkerTest {
    @Test
    void delegatesOneBoundedSweepAndReportsProgress() {
        AtomicReference<Duration> retention = new AtomicReference<>();
        AtomicInteger limit = new AtomicInteger();
        DurableStore store = store(retention, limit, 2);
        DurableRetentionWorker worker = new DurableRetentionWorker(store, Duration.ofDays(30));

        assertThat(worker.runSlice().progressed()).isTrue();
        assertThat(retention).hasValue(Duration.ofDays(30));
        assertThat(limit).hasValue(DurableRetentionWorker.DELETE_SLICE_SIZE);
    }

    @Test
    void rejectsUnboundedConfiguration() {
        DurableStore store = store(new AtomicReference<>(), new AtomicInteger(), 0);

        assertThatThrownBy(() -> new DurableRetentionWorker(store, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableRetentionWorker(store, null, null, null, new DurableRuntimeMetrics()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sweepsTerminalRunsConsumedOccurrencesAndUnreferencedProcessesIndependently() {
        AtomicInteger runSweeps = new AtomicInteger();
        AtomicInteger occurrenceSweeps = new AtomicInteger();
        AtomicInteger processSweeps = new AtomicInteger();
        InvocationHandler handler =
                (instance, method, arguments) -> switch (method.getName()) {
            case "purgeTerminalRuns" -> runSweeps.incrementAndGet();
            case "purgeConsumedOccurrences" -> occurrenceSweeps.incrementAndGet();
            case "purgeUnusedProcesses" -> processSweeps.incrementAndGet();
            case "toString" -> "RetentionStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        };
        DurableStore store = (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(),
                new Class<?>[] {DurableStore.class}, handler);

        DurableRetentionWorker worker = new DurableRetentionWorker(store, Duration.ofDays(30), Duration.ofDays(90),
                Duration.ofDays(7), new DurableRuntimeMetrics());

        assertThat(worker.runSlice().progressed()).isTrue();
        assertThat(runSweeps).hasValue(1);
        assertThat(occurrenceSweeps).hasValue(1);
        assertThat(processSweeps).hasValue(1);
    }

    private static DurableStore store(AtomicReference<Duration> retention, AtomicInteger limit, int deleted) {
        return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(), new Class<?>[] {DurableStore.class}, (
                                                                                                                                       instance,
                                                                                                                                       method,
                                                                                                                                       arguments
                                                                                                                               ) -> switch (method.getName()) {
            case "purgeTerminalRuns" -> {
                retention.set((Duration) arguments[0]);
                limit.set((Integer) arguments[1]);
                yield deleted;
            }
            case "toString" -> "RetentionStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
    }
}
