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
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableProcessRuntimeLoadWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final ProcessRef.Version PROCESS = ProcessRef.version("test", "restart.demand", "v1");
    private static final UUID PROCESS_ID = UUID.fromString("88d56466-3649-4cdf-a85e-5bf75c9c273c");
    private static final byte[] DEFINITION = """
        <bpm code="restart.demand">
          <start id="start" g="0,0,32,32"><transition to="end"/></start>
          <end id="end" g="80,0,32,32"/>
        </bpm>
        """
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void rebuildsAnEmptyNodeLocalCacheFromCommittedRunDemand() {
        AtomicInteger demandQueries = new AtomicInteger();
        DurableStore store = store(demandQueries);
        InMemoryDurableProcessRuntimeCache runtimeCache = new InMemoryDurableProcessRuntimeCache(2);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DurableProcessRuntimeManager processes = new DurableProcessRuntimeManager(store, runtimeCache,
                new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults()), ProcessDefinitionConfig.defaults(),
                getClass().getClassLoader(), 32, DurableVersionDefinitionSource.empty());
        DurableProcessRuntimeLoadWorker worker =
                new DurableProcessRuntimeLoadWorker(store, processes, runtimeCache, Duration.ofSeconds(30), clock);

        assertThat(runtimeCache.get(PROCESS_ID)).isEmpty();
        assertThat(worker.loadDemanded(100)).isEqualTo(1);
        assertThat(runtimeCache.get(PROCESS_ID)).isPresent();
        assertThat(demandQueries).hasValue(1);
        assertThat(worker.loadDemanded(100)).isZero();
        assertThat(demandQueries).hasValue(2);
    }

    @Test
    void rejectsProviderPagesThatExceedTheRequestedBound() {
        DurableStore store = oversizedDemandStore();
        InMemoryDurableProcessRuntimeCache runtimeCache = new InMemoryDurableProcessRuntimeCache(2);
        DurableProcessRuntimeManager processes = new DurableProcessRuntimeManager(store, runtimeCache,
                new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults()), ProcessDefinitionConfig.defaults(),
                getClass().getClassLoader(), 32, DurableVersionDefinitionSource.empty());
        DurableProcessRuntimeLoadWorker worker = new DurableProcessRuntimeLoadWorker(store, processes, runtimeCache,
                Duration.ofSeconds(30), Clock.systemUTC());

        assertThatThrownBy(() -> worker.loadDemanded(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requested limit");
    }

    private static DurableStore oversizedDemandStore() {
        return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(), new Class<?>[] {DurableStore.class}, (
                                                                                                                                       instance,
                                                                                                                                       method,
                                                                                                                                       arguments
                                                                                                                               ) -> switch (method.getName()) {
            case "listProcessRuntimeDemand" -> new DurableStore.ProcessRuntimeDemandPage(List.of(PROCESS_ID,
                            UUID.randomUUID()), null);
            case "toString" -> "OversizedDemandStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
    }

    private static DurableStore store(AtomicInteger demandQueries) {
        String digest = ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, PROCESS.code(), DEFINITION);
        DurableStore.StoredProcess stored =
                new DurableStore.StoredProcess(PROCESS_ID, PROCESS.code(), ProcessModelType.TBBPM, DEFINITION, digest,
                        NOW);
        return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(), new Class<?>[] {DurableStore.class}, (
                                                                                                                                       instance,
                                                                                                                                       method,
                                                                                                                                       arguments
                                                                                                                               ) -> switch (method.getName()) {
            case "listProcessRuntimeDemand" -> {
                demandQueries.incrementAndGet();
                yield new DurableStore.ProcessRuntimeDemandPage(List.of(PROCESS_ID), null);
            }
            case "findProcess" -> Optional.of(stored);
            case "toString" -> "RestartDemandStore";
            default -> throw new AssertionError("Unexpected Store call: " + method.getName());
        });
    }
}
