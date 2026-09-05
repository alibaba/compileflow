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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.runtime.artifact.RepositoryProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DeploymentSyncRoutingStateSubscriber;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeployRuntimeTombstoneTest {
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void aliasTombstoneShouldUnloadPreviouslyDemandedVersion() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRuntimeOwnership ownership = mock(ProcessRuntimeOwnership.class);
        ProcessArtifactSource source = mock(ProcessArtifactSource.class);
        String namespace = "default";
        String code = "order.offline";
        String version = "v1";
        String stateKey = RoutingStateKeys.aliasState(RoutingStateKeys.DEFAULT_PREFIX, namespace, code, "production");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(code, "<definitions/>");
        ProcessRef.Version versionRef = ProcessRef.version(namespace, code, version);
        ProcessArtifact artifact = new ProcessArtifact(versionRef, ProcessModelType.TBBPM, definition,
                ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of()));
        when(source.find(versionRef)).thenReturn(Optional.of(artifact));
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.REMOVED);

        DeployRuntime runtime =
                dbRuntime(channel, Collections.singletonList(stateKey), ownership, source, localRoutingState);
        try {
            runtime.start();
            long now = System.currentTimeMillis();
            ArgumentCaptor<ProcessRef.Version> loadCaptor = ArgumentCaptor.forClass(ProcessRef.Version.class);
            ArgumentCaptor<ProcessRef.Version> unloadCaptor = ArgumentCaptor.forClass(ProcessRef.Version.class);
            String activePayload = RoutingStatePayloads.aliasStateJson(namespace, code, "production", version, null,
                    null, 100L, "operator", now);
            assertThat(channel.compareAndSet(stateKey, null, activePayload, "json", CHANNEL_TIMEOUT)).isTrue();

            verify(ownership, timeout(2000)).loadOwned(any(String.class), loadCaptor.capture(),
                    any(ProcessDefinition.class));
            assertThat(loadCaptor.getValue().code()).isEqualTo(code);
            assertThat(loadCaptor.getValue().version()).isEqualTo(version);
            assertThat(runtime.snapshot().getInstaller().getDemandedVersions()).extracting("version").containsExactly(
                    version);
            assertThat(localRoutingState.getAliasRouteState().resolve(namespace, code, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo(version));

            String tombstonePayload =
                    RoutingStatePayloads.aliasTombstoneJson(namespace, code, "production", 101L, "operator", now + 1L);
            assertThat(channel.compareAndSet(stateKey, activePayload, tombstonePayload, "json", CHANNEL_TIMEOUT)).isTrue();

            verify(ownership, timeout(2000)).releaseOwned(any(String.class), unloadCaptor.capture());
            assertThat(unloadCaptor.getValue().code()).isEqualTo(code);
            assertThat(unloadCaptor.getValue()).isEqualTo(ProcessRef.version(namespace, code, version));
            assertThat(runtime.snapshot().getInstaller().getDemandedVersions()).isEmpty();
            assertThat(runtime.snapshot().getInstaller().getInstalledVersions())
                .noneMatch(process -> process.namespace().equals(namespace) && process.code().equals(code));
            assertThat(localRoutingState.getAliasRouteState().resolve(namespace, code, "production")).isEmpty();
        } finally {
            runtime.close();
        }
    }

    private DeployRuntime dbRuntime(InMemoryDeploymentSyncChannel channel, List<String> routingStateKeys,
            ProcessRuntimeOwnership ownership, ProcessArtifactSource source, LocalRoutingState localRoutingState) {
        return new DeployRuntime(new DeploymentSyncRoutingStateSubscriber(channel, routingStateKeys, CHANNEL_TIMEOUT),
                new VersionDemandPlanner(),
                RuntimeInstaller.withOwnedRetryScheduler(new RepositoryProcessArtifactResolver(source),
                        new ProcessArtifactRuntimeLoader(ownership, ignored -> java.util.List.of(),
                                ProcessModelType.TBBPM, ignored -> null,
                                new com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics()),
                        Runnable::run, localRoutingState, Duration.ofMinutes(5), 1_000), localRoutingState,
                java.util.Set.of());
    }
}
