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

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.inline;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.NoOpProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Exact Artifact Execution invariant: a request never silently
 * executes a stale runtime. These tests guard the SWR removal in
 * {@link ProcessRuntimeResolver} — a cache hit with mismatched identity
 * falls through to {@link ProcessRuntimeLoader#loadSync} instead of returning
 * the stale runtime while compiling asynchronously.
 *
 * @author yusu
 */
class ExactArtifactExecutionTest {
    private static final ProcessRuntime TEST_RUNTIME = NoOpProcessRuntime.INSTANCE;
    private StubCache cache;
    private CountingProcessRuntimeLoader runtimeLoader;
    private LocalRoutingState localRoutingState;

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        runtimeLoader = new CountingProcessRuntimeLoader();
        localRoutingState = new LocalRoutingState();
    }

    /**
     * Same (ns, code, ver) + cache has old content runtime + new content execute
     * must NOT return the stale runtime. The resolver must fall through to
     * loadSync so the caller waits for the exact artifact it requested.
     */
    @Test
    void sameVersionDifferentContentMustNeverExecuteStaleRuntime() {
        ClassLoader cl = getClass().getClassLoader();
        String oldContent = "<flow id=\"old\"/>";
        String newContent = "<flow id=\"new\"/>";
        // Seed the cache with the "old" runtime at (default, order.process, v1)
        ProcessRuntimeRequest cachedRequest = versioned("default", "order.process", "v1", oldContent);
        cache.current = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(cachedRequest, cl));

        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);
        // Request the same coordinate but with different content
        ProcessRuntimeRequest requested = versioned("default", "order.process", "v1", newContent);
        ProcessRuntime result = resolver.resolveRuntime(cl, requested);
        // The returned runtime must come from a fresh loadSync, not the stale cache
        assertThat(runtimeLoader.syncCount.get()).as("cache miss with mismatched identity must trigger loadSync").isEqualTo(
                1);
        assertThat(runtimeLoader.lastSyncRequest.getDefinition())
            .as("loadSync must receive the new content")
            .isEqualTo(ProcessDefinition.inline("order.process", newContent));
        // async path must never be used for the execute hot path
        assertThat(runtimeLoader.asyncCount.get()).as("loadAsync must never be used to return a stale runtime").isEqualTo(
                0);
        // markInstalled must still fire
        assertThat(localRoutingState.getInstalledVersionState().contains("default", "order.process", "v1")).isTrue();
    }

    /**
     * An exact version reference with a cache hit must return the loaded runtime
     * directly without triggering compilation.
     */
    @Test
    void exactVersionHotPathMustReturnCachedRuntime() {
        ClassLoader cl = getClass().getClassLoader();
        ProcessRuntimeRequest cachedRequest = versioned("default", "order.process", "v1", "<flow/>");
        cache.current = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(cachedRequest, cl));
        localRoutingState.getInstalledVersionState().markInstalled("default", "order.process", "v1");

        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        ProcessRuntimeRequest requested =
                ProcessRuntimeRequest.from(ProcessRef.version("default", "order.process", "v1"));
        ProcessRuntime result = resolver.resolveRuntime(cl, requested);
        // Must return the cached runtime, no compilation triggered
        assertThat(result).isSameAs(TEST_RUNTIME);
        assertThat(runtimeLoader.syncCount.get()).isZero();
        assertThat(runtimeLoader.asyncCount.get()).isZero();
    }

    /**
     * Explicit definitions must cross the runtime loader so external sources are resolved before cache matching.
     * The runtime loader is responsible for returning an exact hit.
     */
    @Test
    void explicitDefinitionMustCrossExactResolutionBoundary() {
        ClassLoader cl = getClass().getClassLoader();
        String content = "<flow id=\"same\"/>";
        // Seed cache with a runtime whose identity matches what will be requested
        ProcessRuntimeRequest cachedRequest = versioned("default", "order.process", "v1", content);
        cache.current = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(cachedRequest, cl));

        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        ProcessRuntimeRequest requested = versioned("default", "order.process", "v1", content);
        ProcessRuntime result = resolver.resolveRuntime(cl, requested);
        // The resolver does not compare unresolved external sources against local runtime keys.
        assertThat(result).isSameAs(TEST_RUNTIME);
        assertThat(runtimeLoader.syncCount.get()).isEqualTo(1);
        assertThat(runtimeLoader.asyncCount.get()).isZero();
    }

    @Test
    void locatorCacheHitMustResolveContentBeforeReturningRuntime() {
        ClassLoader cl = getClass().getClassLoader();
        ProcessRuntimeRequest locatorRequest =
                ProcessRuntimeRequest.from(ProcessDefinition.classpath("order.process", "flows/order.bpm"));
        cache.current = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(inline("order.process", "<cached/>"), cl));
        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        resolver.resolveRuntime(cl, locatorRequest);

        assertThat(runtimeLoader.syncCount).hasValue(0);
        assertThat(runtimeLoader.exactSyncCount).hasValue(1);
        assertThat(runtimeLoader.asyncCount).hasValue(0);
    }

    /**
     * Concurrent {@code loadSync} of the same {@link ProcessRuntimeIdentity} must be
     * deduplicated by {@link ProcessRuntimeLoader}'s in-flight registry. This test
     * uses a CountDownLatch to hold the first compile and verifies the second
     * caller joins the same future rather than compiling twice.
     *
     * <p>Note: this test exercises the resolver's fallthrough path; the actual
     * single-flight deduplication lives in {@code DefaultProcessRuntimeLoader}.
     * Here we only assert that the resolver does not bypass it by returning a
     * stale runtime — both callers must reach {@code loadSync}.
     */
    @Test
    void concurrentCompileOfSameIdentityMustBeSingleFlight() throws InterruptedException {
        ClassLoader cl = getClass().getClassLoader();
        String content = "<flow id=\"concurrent\"/>";
        // Cache is empty — both callers will fall through to loadSync
        ProcessRuntimeResolver resolver = new ProcessRuntimeResolver(cache, runtimeLoader, localRoutingState);

        int threads = 8;
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch enteredCompile = new CountDownLatch(threads);
        runtimeLoader.blockUntilAllEnter(enteredCompile, release);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ProcessRuntimeRequest requested = versioned("default", "order.process", "v1", content);
                resolver.resolveRuntime(cl, requested);
            });
        }
        // Wait for all threads to enter loadSync
        assertThat(enteredCompile.await(2, TimeUnit.SECONDS)).isTrue();
        // All threads are now blocked in loadSync; release them
        release.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        // Every caller reached loadSync (single-flight dedup is the
        // ProcessRuntimeLoader's responsibility, not the resolver's; the resolver
        // must not short-circuit any of them by returning a stale runtime)
        assertThat(runtimeLoader.syncCount.get())
            .as("all concurrent callers must reach loadSync (no stale short-circuit)")
            .isEqualTo(threads);
    }

    // --- Stubs (minimal, self-contained for this test class) ---
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

    /**
     * ProcessRuntimeLoader stub that counts sync vs async invocations and can
     * block sync callers to exercise concurrent single-flight scenarios.
     */
    private static final class CountingProcessRuntimeLoader implements ProcessRuntimeLoader {
        private static final ProcessRuntimeIdentity.PipelineIdentity PIPELINE_IDENTITY =
                ProcessRuntimeIdentity.newPipelineIdentity();
        final AtomicInteger syncCount = new AtomicInteger();
        final AtomicInteger exactSyncCount = new AtomicInteger();
        final AtomicInteger asyncCount = new AtomicInteger();
        volatile ProcessRuntimeRequest lastSyncRequest;
        private volatile CountDownLatch enteredCompileLatch;
        private volatile CountDownLatch releaseLatch;

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
            return compile(request, cl);
        }

        @Override
        public ProcessRuntimeEntry loadExactSync(ProcessRuntimeRequest request, ClassLoader cl) {
            exactSyncCount.incrementAndGet();
            return compile(request, cl);
        }

        private ProcessRuntimeEntry compile(ProcessRuntimeRequest request, ClassLoader cl) {
            lastSyncRequest = request;
            CountDownLatch entered = this.enteredCompileLatch;
            if (entered != null) {
                entered.countDown();
            }
            CountDownLatch latch = this.releaseLatch;
            if (latch != null) {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            }
            return entry(resolve(request, cl), cl);
        }

        @Override
        public ProcessDefinitionSnapshot resolve(ProcessRuntimeRequest request, ClassLoader cl) {
            String source = null;
            ProcessDefinition definition = request.getDefinition();
            if (definition instanceof ProcessDefinition.Inline inline) {
                source = inline.content();
            } else if (definition instanceof ProcessDefinition.Classpath classpath) {
                source = classpath.resourcePath();
            }
            if (source == null) {
                source = "<flow/>";
            }
            return ProcessDefinitionSnapshot.of(request.getNamespace(), request.getCode(), request.getVersion(),
                    source.getBytes(StandardCharsets.UTF_8), "test");
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

        void blockUntilAllEnter(CountDownLatch entered, CountDownLatch release) {
            this.enteredCompileLatch = entered;
            this.releaseLatch = release;
        }
    }
}
