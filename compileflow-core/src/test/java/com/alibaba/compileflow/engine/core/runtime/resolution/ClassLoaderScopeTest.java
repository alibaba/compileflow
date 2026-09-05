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

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.core.runtime.NoOpProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the ClassLoader scope invariant: every engine operation uses the
 * ClassLoader fixed at construction time. Per-call ClassLoader overrides were
 * removed from {@link ProcessRuntimeManager} to keep the runtime cache
 * homogeneous — mixing runtimes compiled by different loaders under the same
 * {@code (namespace, code, version)} key caused {@code ClassCastException}
 * on the execute hot path.
 *
 * @author yusu
 */
class ClassLoaderScopeTest {
    private static final ProcessRuntime TEST_RUNTIME = NoOpProcessRuntime.INSTANCE;
    private StubCache cache;
    private CountingProcessRuntimeLoader runtimeLoader;
    private LocalRoutingState localRoutingState;

    private static <T> T assertNoThrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        runtimeLoader = new CountingProcessRuntimeLoader();
        localRoutingState = new LocalRoutingState();
    }

    /**
     * Same engine-fixed ClassLoader + cache hit must return the cached runtime
     * directly. This guards the hot-path performance invariant: the fast path
     * in {@link ProcessRuntimeResolver} must NOT add a
     * ClassLoader comparison cost, because the root cause (per-call loader
     * override) has been eliminated at the API boundary.
     */
    @Test
    void engineFixedLoaderMustReuseCachedRuntime() {
        ClassLoader cl = getClass().getClassLoader();
        // Seed cache with a runtime compiled by the engine's fixed loader
        ProcessRuntimeRequest cachedRequest = versioned("default", "order.process", "v1", "<flow/>");
        cache.current = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(cachedRequest, cl));
        localRoutingState.getInstalledVersionState().markInstalled("default", "order.process", "v1");

        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        ProcessRuntimeRequest requested =
                ProcessRuntimeRequest.from(ProcessRef.version("default", "order.process", "v1"));
        ProcessRuntime result = resolver.resolveRuntime(cl, requested);
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

        ProcessRuntimeRequest request = ProcessRuntimeRequest.from(ProcessDefinition.classpath("order.process",
                "order.process".replace(".", "/") + ".bpm"));
        resolver.resolveRuntime(cl, request);

        assertThat(runtimeLoader.syncCount.get()).isEqualTo(1);
        assertThat(runtimeLoader.lastSyncClassLoader).as("lookup must use the engine's fixed ClassLoader").isSameAs(cl);
    }

    // --- Stubs ---
    /**
     * The per-call ClassLoader override methods were removed from
     * {@link ProcessRuntimeManager}. This is a compile-time contract — verified
     * here at runtime via reflection to catch accidental re-introduction.
     */
    @Test
    void perCallLoaderOverrideIsRejected() {
        // warmUp(ClassLoader, ProcessDefinition...) must not exist
        assertThatThrownBy(() -> ProcessRuntimeManager.class
            .getMethod("warmUp", ClassLoader.class, ProcessDefinition[].class))
            .isInstanceOf(NoSuchMethodException.class);
        // preflight(ClassLoader, ProcessDefinition, ProcessPreflightOptions) must not exist
        assertThatThrownBy(() -> ProcessToolingService.class
            .getMethod("preflight", ClassLoader.class, ProcessDefinition.class, ProcessPreflightOptions.class))
            .isInstanceOf(NoSuchMethodException.class);
        // Sanity: the engine-scoped operations must still exist
        Method warmUp = assertNoThrow(() -> ProcessRuntimeManager.class.getMethod("warmUp", ProcessDefinition[].class));
        assertThat(warmUp).isNotNull();

        Method preflight = assertNoThrow(() -> ProcessToolingService.class
            .getMethod("preflight", ProcessDefinition.class, ProcessPreflightOptions.class));
        assertThat(preflight).isNotNull();
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class StubCache implements ProcessRuntimeCache {
        ProcessRuntimeEntry current;

        @Override
        public ProcessRuntimeEntry getIfPresent(String code) {
            return current;
        }

        @Override
        public ProcessRuntimeEntry getIfPresent(ProcessRuntimeIdentity runtimeIdentity) {
            return current != null && current.matches(runtimeIdentity) ? current : null;
        }

        @Override
        public ProcessRuntimeEntry cacheExact(ProcessRuntimeEntry runtime) {
            current = runtime;
            return runtime;
        }

        @Override
        public boolean conflictsWithImmutableBinding(String bindingKey, ProcessRuntimeIdentity runtimeIdentity) {
            return false;
        }

        @Override
        public InstallResult install(String code, ProcessRuntimeEntry expectedEntry, ProcessRuntimeEntry newEntry) {
            return InstallResult.INSTALLED;
        }

        @Override
        public InstallResult installAndRetainBatch(List<Installation> installations, String ownerId) {
            return InstallResult.INSTALLED;
        }

        @Override
        public boolean retain(String bindingKey, String ownerId) {
            return current != null;
        }

        @Override
        public ReleaseResult release(String bindingKey, String ownerId) {
            return ReleaseResult.INVALIDATED;
        }

        @Override
        public boolean invalidateIfUnretained(String bindingKey) {
            return true;
        }

        @Override
        public void invalidate(String code) {}

        @Override
        public void invalidateAll() {}

        @Override
        public long size() {
            return current == null ? 0 : 1;
        }
    }

    private static final class CountingProcessRuntimeLoader implements ProcessRuntimeLoader {
        private static final ProcessRuntimeIdentity.PipelineIdentity PIPELINE_IDENTITY =
                ProcessRuntimeIdentity.newPipelineIdentity();
        final AtomicInteger syncCount = new AtomicInteger();
        final AtomicInteger asyncCount = new AtomicInteger();
        volatile ProcessRuntimeRequest lastSyncRequest;
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
            lastSyncRequest = request;
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
            return ProcessDefinitionSnapshot.of(request.getNamespace(), request.getCode(), request.getVersion(),
                    content.getBytes(StandardCharsets.UTF_8), "test");
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
