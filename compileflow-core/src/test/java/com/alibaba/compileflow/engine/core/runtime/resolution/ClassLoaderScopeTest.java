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
package com.alibaba.compileflow.engine.core.runtime.resolution;

import com.alibaba.compileflow.engine.ProcessModelType;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.bindingKey;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.core.runtime.NoOpProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.cache.DefaultProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies cached and explicit-source resolution with the engine's fixed ClassLoader.
 *
 * @author yusu
 */
class ClassLoaderScopeTest {
    private static final ProcessRuntime TEST_RUNTIME = NoOpProcessRuntime.INSTANCE;
    private DefaultProcessRuntimeCache cache;
    private CountingProcessRuntimeLoader runtimeLoader;
    private LocalRoutingState localRoutingState;

    @BeforeEach
    void setUp() {
        cache = new DefaultProcessRuntimeCache(16);
        runtimeLoader = new CountingProcessRuntimeLoader();
        localRoutingState = new LocalRoutingState();
    }

    @Test
    void engineFixedLoaderMustReuseCachedRuntime() {
        ClassLoader cl = getClass().getClassLoader();
        // Seed cache with a runtime compiled by the engine's fixed loader
        ProcessRuntimeRequest cachedRequest = versioned("default", "order.process", "v1", "<flow/>");
        cache.install(bindingKey(cachedRequest), null,
                new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(cachedRequest, cl)));
        localRoutingState.getInstalledVersionState().markInstalled("default", "order.process", "v1");

        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        ProcessRuntimeRequest requested =
                ProcessRuntimeRequest.from(ProcessRef.version("default", "order.process", "v1"));
        ProcessRuntime result = resolver.resolve(cl, requested).entry().getRuntime();
        // Fast path returns cached runtime without any compilation
        assertThat(result).isSameAs(TEST_RUNTIME);
        assertThat(runtimeLoader.syncCount.get()).isZero();
        assertThat(runtimeLoader.asyncCount.get()).isZero();
    }

    /**
     * Explicit classpath resolution uses the engine's fixed ClassLoader.
     */
    @Test
    void classpathLookupUsesEngineFixedLoader() {
        ClassLoader cl = getClass().getClassLoader();
        // Cache empty: the explicit classpath source must compile with the engine-fixed loader.
        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                "order.process", "order/process.bpm"));
        resolver.resolve(cl, request);

        assertThat(runtimeLoader.syncCount.get()).isEqualTo(1);
        assertThat(runtimeLoader.lastSyncClassLoader).as("lookup must use the engine's fixed ClassLoader").isSameAs(cl);
    }

    @Test
    void publicRuntimeOperationsUseTheEngineClassLoader() {
        for (Class<?> api : List.of(ProcessRuntimeManager.class, ProcessToolingService.class)) {
            assertThat(api.getMethods()).noneMatch(method -> Arrays
                .asList(method.getParameterTypes())
                .contains(ClassLoader.class));
        }
    }

    private static final class CountingProcessRuntimeLoader implements ProcessRuntimeLoader {
        private static final ProcessRuntimeIdentity.PipelineIdentity PIPELINE_IDENTITY =
                ProcessRuntimeIdentity.newPipelineIdentity();
        final AtomicInteger syncCount = new AtomicInteger();
        final AtomicInteger asyncCount = new AtomicInteger();
        volatile ClassLoader lastSyncClassLoader;

        private static ProcessRuntimeEntry entry(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
            return new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(definition, PIPELINE_IDENTITY, classLoader));
        }

        @Override
        public CompletableFuture<ProcessRuntimeEntry> loadAsync(ProcessRuntimeRequest request, ClassLoader cl,
                ProcessRuntimeEntry expectedEntry) {
            asyncCount.incrementAndGet();
            return CompletableFuture.completedFuture(entry(resolve(request, cl), cl));
        }

        @Override
        public ProcessRuntimeEntry loadSync(ProcessRuntimeRequest request, ClassLoader cl) {
            syncCount.incrementAndGet();
            lastSyncClassLoader = cl;
            return entry(resolve(request, cl), cl);
        }

        @Override
        public ProcessRuntimeEntry loadExactSync(ProcessRuntimeRequest request, ClassLoader cl) {
            return loadSync(request, cl);
        }

        @Override
        public ProcessDefinitionSnapshot resolve(ProcessRuntimeRequest request, ClassLoader cl) {
            String content =
                    request.getDefinition() instanceof ProcessDefinition.Inline inline ? inline.content() : "<"
                    + "flow/>";
            return ProcessDefinitionSnapshot.of(ProcessModelType.TBBPM, request.getNamespace(), request.getCode(),
                    request.getVersion(), content.getBytes(StandardCharsets.UTF_8), "test");
        }

        @Override
        public ProcessRuntimeEntry runtimeCheckSync(ProcessDefinitionSnapshot definition, ClassLoader cl) {
            return entry(definition, cl);
        }

        @Override
        public void loadBatch(String ownerId, ClassLoader cl, ProcessRuntimeRequest... requests) {}

        @Override
        public void loadExactBatch(ClassLoader cl, ProcessRuntimeRequest... requests) {}

        @Override
        public void close() {}
    }
}
