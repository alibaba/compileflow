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
package com.alibaba.compileflow.deploy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DeploymentSyncRoutingStateSubscriber;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DeployRuntimeLocalReadyTest {
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(1);
    private static final String NAMESPACE = "default";
    private static final String CODE = "order.ready";
    private static final String ALIAS = "production";

    private static DeployRuntime runtime(InMemoryDeploymentSyncChannel channel, LocalRoutingState localRoutingState,
            ProcessArtifactRuntimeLoader loader, Executor executor) {
        ProcessArtifactResolver resolver =
                key -> {
            ProcessDefinition.Inline resolvedDefinition = definition(key);
            return new ProcessArtifact(ref(key), ProcessModelType.TBBPM, resolvedDefinition,
                    ProcessArtifactDigest.compute(ProcessModelType.TBBPM, resolvedDefinition, Map.of()));
        };
        RuntimeInstaller installer = RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, executor,
                localRoutingState, Duration.ofMinutes(1), 1_000);
        return new DeployRuntime(new DeploymentSyncRoutingStateSubscriber(channel, Collections.singletonList(stateKey()),
                        CHANNEL_TIMEOUT), new VersionDemandPlanner(), installer, localRoutingState, java.util.Set.of());
    }

    private static LocalRoutingState snapshotsWithLocalRoute(String version, long revision) {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, CODE, ALIAS);
        localRoutingState.applyAliasRoute(ProcessAliasRoute.stable(alias, version, revision));
        return localRoutingState;
    }

    private static ProcessRef.Version ref(ProcessRef.Version key) {
        return ProcessRef.version(key.namespace(), key.code(), key.version());
    }

    private static ProcessDefinition.Inline definition(ProcessRef.Version key) {
        return ProcessDefinition.inline(key.code(), "<definitions/>");
    }

    private static Optional<ProcessAliasRoute> resolve(LocalRoutingState localRoutingState) {
        return localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, ALIAS);
    }

    private static void writeRoute(InMemoryDeploymentSyncChannel channel, String version, long revision) {
        String key = stateKey();
        String current = channel.read(key, CHANNEL_TIMEOUT);
        String payload = RoutingStatePayloads.aliasStateJson(NAMESPACE, CODE, ALIAS, version, null, null, revision,
                "operator", System.currentTimeMillis());
        assertThat(channel.compareAndSet(key, current, payload, "json", CHANNEL_TIMEOUT)).isTrue();
    }

    private static String stateKey() {
        return RoutingStateKeys.aliasState(RoutingStateKeys.DEFAULT_PREFIX, NAMESPACE, CODE, ALIAS);
    }

    private static void assertEventually(Duration timeout, BooleanSupplier check) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!check.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertThat(check.getAsBoolean()).isTrue();
    }

    @Test
    void keepsPreviousRouteVisibleUntilTheNewRuntimeIsReady() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        LocalRoutingState localRoutingState = snapshotsWithLocalRoute("v1", 1L);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadStarted.countDown();
            assertThat(releaseLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        DeployRuntime runtime = runtime(channel, localRoutingState, loader, executor);
        try {
            runtime.start();
            writeRoute(channel, "v2", 2L);

            assertThat(loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(resolve(localRoutingState).map(route -> route.stableVersion().version())).contains("v1");

            releaseLoad.countDown();

            assertEventually(Duration.ofSeconds(2), () -> resolve(localRoutingState)
                .map(route -> route.stableVersion().version())
                .filter("v2"::equals)
                .isPresent());
        } finally {
            releaseLoad.countDown();
            runtime.close();
            executor.shutdownNow();
        }
    }

    @Test
    void installationFailureRetainsThePreviousLocalReadyRoute() {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        LocalRoutingState localRoutingState = snapshotsWithLocalRoute("v1", 1L);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        doThrow(new IllegalStateException("compile failed")).when(loader).load(any(ProcessArtifact.class));

        DeployRuntime runtime = runtime(channel, localRoutingState, loader, Runnable::run);
        try {
            runtime.start();
            writeRoute(channel, "v2", 2L);

            assertThat(resolve(localRoutingState).map(route -> route.stableVersion().version())).contains("v1");
            assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isFalse();
        } finally {
            runtime.close();
        }
    }
}
