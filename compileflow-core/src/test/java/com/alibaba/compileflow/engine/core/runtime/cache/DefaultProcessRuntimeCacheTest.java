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
package com.alibaba.compileflow.engine.core.runtime.cache;

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.bindingKey;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.inline;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.resolved;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.runtime.NoOpProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultProcessRuntimeCacheTest {
    private static final ProcessRuntime TEST_RUNTIME = NoOpProcessRuntime.INSTANCE;

    @Test
    void installIsIdempotentForTheSameRuntimeIdentity() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(source, getClass().getClassLoader());
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity);
        ProcessRuntimeEntry equalButNotCurrent = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity);
        String key = bindingKey(source);

        assertThat(cache.install(key, null, first)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.install(key, equalButNotCurrent, equalButNotCurrent))
            .isEqualTo(ProcessRuntimeCache.InstallResult.ALREADY_INSTALLED);
        assertThat(cache.getIfPresent(key)).isSameAs(first);
        assertThat(cache.install(key, first, equalButNotCurrent))
            .isEqualTo(ProcessRuntimeCache.InstallResult.ALREADY_INSTALLED);
        assertThat(cache.getIfPresent(key)).isSameAs(first);
    }

    @Test
    void cacheExactCanonicalizesWithoutCreatingABinding() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest source = inline("order.checkout", "<process/>");
        ProcessRuntimeIdentity identity = runtimeIdentity(source, classLoader);
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, identity);
        ProcessRuntimeEntry duplicate = new ProcessRuntimeEntry(TEST_RUNTIME, identity);

        assertThat(cache.cacheExact(first)).isSameAs(first);
        assertThat(cache.cacheExact(duplicate)).isSameAs(first);

        assertThat(cache.getIfPresent(bindingKey(source))).isNull();
        assertThat(cache.getIfPresent(identity)).isSameAs(first);
        assertThat(cache.size()).isOne();
    }

    @Test
    void rejectsRebindingOneVersionToDifferentContent() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest firstSource = versioned("default", "order.checkout", "7", "<process id='first'/>");
        ProcessRuntimeRequest secondSource = versioned("default", "order.checkout", "7", "<process id='second'/>");
        ProcessRuntimeIdentity firstIdentity = runtimeIdentity(firstSource, classLoader);
        ProcessRuntimeIdentity secondIdentity = runtimeIdentity(secondSource, classLoader);
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, firstIdentity);
        ProcessRuntimeEntry second = new ProcessRuntimeEntry(TEST_RUNTIME, secondIdentity);
        String key = bindingKey(firstSource);

        assertThat(cache.install(key, null, first)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.install(key, first, second)).isEqualTo(ProcessRuntimeCache.InstallResult.VERSION_CONFLICT);

        assertThat(cache.getIfPresent(key)).isSameAs(first);
        assertThat(cache.getIfPresent(firstIdentity)).isSameAs(first);
        assertThat(cache.getIfPresent(secondIdentity)).isNull();
    }

    @Test
    void explicitInvalidationReleasesTheLocalBindingGuard() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest firstSource = versioned("default", "order.checkout", "7", "<process id='first'/>");
        ProcessRuntimeRequest changedSource = versioned("default", "order.checkout", "7", "<process id='changed'/>");
        ProcessRuntimeIdentity firstIdentity = runtimeIdentity(firstSource, classLoader);
        ProcessRuntimeIdentity changedIdentity = runtimeIdentity(changedSource, classLoader);
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, firstIdentity);
        ProcessRuntimeEntry changed = new ProcessRuntimeEntry(TEST_RUNTIME, changedIdentity);
        String key = bindingKey(firstSource);

        assertThat(cache.install(key, null, first)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        cache.invalidate(key);

        assertThat(cache.getIfPresent(key)).isNull();
        assertThat(cache.getIfPresent(firstIdentity)).isNull();
        assertThat(cache.conflictsWithImmutableBinding(key, firstIdentity)).isFalse();
        assertThat(cache.conflictsWithImmutableBinding(key, changedIdentity)).isFalse();
        assertThat(cache.install(key, null, changed)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
    }

    @Test
    void runtimeEvictionReleasesAnInactiveBindingGuard() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(1);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest firstSource = versioned("default", "order.checkout", "7", "<process id='first'/>");
        ProcessRuntimeRequest changedSource = versioned("default", "order.checkout", "7", "<process id='changed'/>");
        ProcessRuntimeRequest otherSource = versioned("default", "invoice.create", "1", "<process id='other'/>");
        ProcessRuntimeIdentity firstIdentity = runtimeIdentity(firstSource, classLoader);
        ProcessRuntimeIdentity changedIdentity = runtimeIdentity(changedSource, classLoader);
        ProcessRuntimeIdentity otherIdentity = runtimeIdentity(otherSource, classLoader);
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, firstIdentity);
        ProcessRuntimeEntry other = new ProcessRuntimeEntry(TEST_RUNTIME, otherIdentity);

        assertThat(cache.install(bindingKey(firstSource), null, first)).isEqualTo(
                ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.install(bindingKey(otherSource), null, other)).isEqualTo(
                ProcessRuntimeCache.InstallResult.INSTALLED);

        assertThat(cache.getIfPresent(firstIdentity)).isNull();
        assertThat(cache.conflictsWithImmutableBinding(bindingKey(firstSource), changedIdentity)).isFalse();
        assertThat(cache.bindingGuardSize()).isEqualTo(1);
    }

    @Test
    void bindingGuardUsesPortableProcessSemanticsRatherThanLocalMaterializationIdentity() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity firstIdentity =
                runtimeIdentity(resolved(source), ProcessRuntimeIdentity.newPipelineIdentity(),
                        getClass().getClassLoader());
        ProcessRuntimeIdentity rematerializedIdentity = runtimeIdentity(resolved(source),
                ProcessRuntimeIdentity.newPipelineIdentity(), new ClassLoader(getClass().getClassLoader()) {
                });
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, firstIdentity);
        ProcessRuntimeEntry rematerialized = new ProcessRuntimeEntry(TEST_RUNTIME, rematerializedIdentity);
        String key = bindingKey(source);

        assertThat(cache.install(key, null, first)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.conflictsWithImmutableBinding(key, rematerializedIdentity)).isFalse();
        assertThat(cache.install(key, first, rematerialized)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.getIfPresent(key)).isSameAs(rematerialized);
    }

    @Test
    void bindingGuardRemainsBoundedDuringVersionChurn() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity identity = runtimeIdentity(source, getClass().getClassLoader());
        ProcessRuntimeEntry runtime = new ProcessRuntimeEntry(TEST_RUNTIME, identity);

        for (int version = 0; version < 100_000; version++) {
            String bindingKey = "default#order.checkout#" + version;
            assertThat(cache.install(bindingKey, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
            cache.invalidate(bindingKey);
        }

        assertThat(cache.bindingGuardSize()).isZero();
        assertThat(cache.size()).isZero();
    }

    @Test
    void retainedRuntimeExhaustsResidentCapacityUntilReleased() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(1);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest retainedSource = versioned("default", "order.checkout", "7", "<process id='retained'/>");
        ProcessRuntimeRequest otherSource = versioned("default", "invoice.create", "1", "<process id='other'/>");
        ProcessRuntimeIdentity retainedIdentity = runtimeIdentity(retainedSource, classLoader);
        ProcessRuntimeIdentity otherIdentity = runtimeIdentity(otherSource, classLoader);
        ProcessRuntimeEntry retained = new ProcessRuntimeEntry(TEST_RUNTIME, retainedIdentity);
        ProcessRuntimeEntry other = new ProcessRuntimeEntry(TEST_RUNTIME, otherIdentity);
        String retainedKey = bindingKey(retainedSource);

        assertThat(cache.install(retainedKey, null, retained)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.retain(retainedKey, "test-owner")).isTrue();
        assertThatThrownBy(() -> cache.install(bindingKey(otherSource), null, other))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_RUNTIME_001);
                assertThat(failure.getContext()).containsEntry("cause", "CAPACITY").containsEntry("maxResidentRuntimes",
                        1);
            });

        assertThat(cache.getIfPresent(retainedKey)).isSameAs(retained);
        assertThat(cache.getIfPresent(retainedIdentity)).isSameAs(retained);
        assertThat(cache.getIfPresent(otherIdentity)).isNull();
        assertThat(cache.size()).isEqualTo(1);

        assertThat(cache.release(retainedKey, "test-owner")).isEqualTo(ProcessRuntimeCache.ReleaseResult.INVALIDATED);
        assertThat(cache.install(bindingKey(otherSource), null, other)).isEqualTo(
                ProcessRuntimeCache.InstallResult.INSTALLED);

        assertThat(cache.getIfPresent(retainedKey)).isNull();
        assertThat(cache.getIfPresent(retainedIdentity)).isNull();
        assertThat(cache.getIfPresent(otherIdentity)).isSameAs(other);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void retainReportsAnAbsentBinding() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);

        assertThat(cache.retain("default#missing", "test-owner")).isFalse();
    }

    @Test
    void ownerReleaseKeepsRuntimeUntilTheLastOwnerReleasesIt() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity identity = runtimeIdentity(source, getClass().getClassLoader());
        ProcessRuntimeEntry runtime = new ProcessRuntimeEntry(TEST_RUNTIME, identity);
        String key = bindingKey(source);
        assertThat(cache.install(key, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        assertThat(cache.retain(key, "admin")).isTrue();
        assertThat(cache.retain(key, "published-alias")).isTrue();

        assertThat(cache.release(key, "published-alias")).isEqualTo(ProcessRuntimeCache.ReleaseResult.RETAINED);
        assertThat(cache.getIfPresent(key)).isSameAs(runtime);
        assertThat(cache.invalidateIfUnretained(key)).isFalse();

        assertThat(cache.release(key, "admin")).isEqualTo(ProcessRuntimeCache.ReleaseResult.INVALIDATED);
        assertThat(cache.getIfPresent(key)).isNull();
        assertThat(cache.getIfPresent(identity)).isNull();
    }

    @Test
    void oneOwnerRetainIsIdempotent() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity identity = runtimeIdentity(source, getClass().getClassLoader());
        ProcessRuntimeEntry runtime = new ProcessRuntimeEntry(TEST_RUNTIME, identity);
        String key = bindingKey(source);
        assertThat(cache.install(key, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        assertThat(cache.retain(key, "published-alias")).isTrue();
        assertThat(cache.retain(key, "published-alias")).isTrue();

        assertThat(cache.release(key, "published-alias")).isEqualTo(ProcessRuntimeCache.ReleaseResult.INVALIDATED);
        assertThat(cache.getIfPresent(key)).isNull();
    }

    @Test
    void sharedRuntimeSurvivesInvalidationOfOneBinding() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest firstSource = versioned("first", "order.checkout", "7", "<process/>");
        ProcessRuntimeRequest secondSource = versioned("second", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity runtimeIdentity = runtimeIdentity(firstSource, getClass().getClassLoader());
        ProcessRuntimeEntry runtime = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity);
        String firstKey = bindingKey(firstSource);
        String secondKey = bindingKey(secondSource);

        assertThat(cache.install(firstKey, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
        assertThat(cache.install(secondKey, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        cache.invalidate(firstKey);
        assertThat(cache.getIfPresent(firstKey)).isNull();
        assertThat(cache.getIfPresent(secondKey)).isSameAs(runtime);
        assertThat(cache.getIfPresent(runtimeIdentity)).isSameAs(runtime);

        cache.invalidate(secondKey);
        assertThat(cache.getIfPresent(runtimeIdentity)).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void batchConflictDoesNotInstallOrRetainAnyEarlierItem() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest newSource = versioned("default", "invoice.create", "1", "<process/>");
        ProcessRuntimeRequest installedSource = versioned("default", "order.checkout", "7", "<process id='installed'/>");
        ProcessRuntimeRequest conflictingSource = versioned("default", "order.checkout", "7", "<process id='changed'/>");
        ProcessRuntimeEntry newRuntime = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(newSource, classLoader));
        ProcessRuntimeEntry installedRuntime =
                new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(installedSource, classLoader));
        ProcessRuntimeEntry conflictingRuntime =
                new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(conflictingSource, classLoader));

        assertThat(cache.install(bindingKey(installedSource), null, installedRuntime))
            .isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        assertThat(cache.installAndRetainBatch(List.of(new ProcessRuntimeCache.Installation(bindingKey(newSource), null,
                                newRuntime),
                        new ProcessRuntimeCache.Installation(bindingKey(conflictingSource), installedRuntime,
                                conflictingRuntime)), "batch-owner"))
            .isEqualTo(ProcessRuntimeCache.InstallResult.VERSION_CONFLICT);

        assertThat(cache.getIfPresent(bindingKey(newSource))).isNull();
        assertThat(cache.getIfPresent(newRuntime.getRuntimeIdentity())).isNull();
        assertThat(cache.release(bindingKey(newSource), "batch-owner"))
            .isEqualTo(ProcessRuntimeCache.ReleaseResult.NOT_RETAINED);
        assertThat(cache.getIfPresent(bindingKey(installedSource))).isSameAs(installedRuntime);
    }

    @Test
    void batchRejectsResidentCapacityAtomically() {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(1);
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeRequest firstSource = versioned("default", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest secondSource = versioned("default", "invoice.create", "1", "<process/>");
        ProcessRuntimeEntry first = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(firstSource, classLoader));
        ProcessRuntimeEntry second = new ProcessRuntimeEntry(TEST_RUNTIME, runtimeIdentity(secondSource, classLoader));

        List<ProcessRuntimeCache.Installation> batch = List.of(new ProcessRuntimeCache.Installation(bindingKey(
                                firstSource), null, first),
                new ProcessRuntimeCache.Installation(bindingKey(secondSource), null, second));

        assertThatThrownBy(() -> cache.installAndRetainBatch(batch, "batch-owner"))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getContext())
                .containsEntry("cause", "CAPACITY"));

        assertThat(cache.getIfPresent(bindingKey(firstSource))).isNull();
        assertThat(cache.getIfPresent(bindingKey(secondSource))).isNull();
        assertThat(cache.release(bindingKey(firstSource), "batch-owner"))
            .isEqualTo(ProcessRuntimeCache.ReleaseResult.NOT_RETAINED);
        assertThat(cache.release(bindingKey(secondSource), "batch-owner"))
            .isEqualTo(ProcessRuntimeCache.ReleaseResult.NOT_RETAINED);
        assertThat(cache.size()).isZero();
    }

    @Test
    void lockFreeReadsRemainSafeDuringBindingInvalidationAndReinstallation() throws Exception {
        DefaultProcessRuntimeCache cache = new DefaultProcessRuntimeCache(2048);
        ProcessRuntimeRequest source = versioned("default", "order.checkout", "7", "<process/>");
        ProcessRuntimeIdentity identity = runtimeIdentity(source, getClass().getClassLoader());
        ProcessRuntimeEntry runtime = new ProcessRuntimeEntry(TEST_RUNTIME, identity);
        String key = bindingKey(source);
        assertThat(cache.install(key, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);

        ExecutorService executor = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int reader = 0; reader < 8; reader++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 10_000; iteration++) {
                        ProcessRuntimeEntry byBinding = cache.getIfPresent(key);
                        ProcessRuntimeEntry byIdentity = cache.getIfPresent(identity);
                        assertThat(byBinding).isIn(runtime, null);
                        assertThat(byIdentity).isIn(runtime, null);
                    }
                    return null;
                }));
            }
            tasks.add(executor.submit(() -> {
                start.await();
                for (int iteration = 0; iteration < 1_000; iteration++) {
                    cache.invalidate(key);
                    assertThat(cache.install(key, null, runtime)).isEqualTo(ProcessRuntimeCache.InstallResult.INSTALLED);
                }
                return null;
            }));

            start.countDown();
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
