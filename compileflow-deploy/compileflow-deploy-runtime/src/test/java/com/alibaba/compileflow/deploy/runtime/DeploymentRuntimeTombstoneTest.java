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

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import com.alibaba.compileflow.deploy.runtime.artifact.ArtifactSourceResolver;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.ProjectionStoreRoutingStateSubscriber;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeploymentRuntimeTombstoneTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void aliasTombstoneShouldUnloadPreviouslyDemandedVersion() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRuntimeOwnership ownership = mock(ProcessRuntimeOwnership.class);
        ProcessArtifactSource source = mock(ProcessArtifactSource.class);
        String namespace = "default";
        String code = "order.offline";
        String version = "v1";
        String stateKey = RoutingStateKeys.aliasState(null, namespace, code, "production");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, code, "<definitions/>");
        ProcessRef.Version versionRef = ProcessRef.version(namespace, code, version);
        ProcessArtifact artifact =
                new ProcessArtifact(versionRef, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        when(source.find(versionRef)).thenReturn(Optional.of(artifact));
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.REMOVED);

        DeploymentRuntime runtime =
                sourceRuntime(projectionStore, Collections.singletonList(stateKey), ownership, source, localRoutingState);
        try {
            runtime.start();
            long now = System.currentTimeMillis();
            ArgumentCaptor<ProcessRef.Version> loadCaptor = ArgumentCaptor.forClass(ProcessRef.Version.class);
            ArgumentCaptor<ProcessRef.Version> unloadCaptor = ArgumentCaptor.forClass(ProcessRef.Version.class);
            String activePayload =
                    RoutingStateCodec.aliasStateJson(namespace, code, "production", version, null, null, 100L,
                            "operator", now);
            assertThat(projectionStore.compareAndSet(stateKey, null, activePayload, "json", OPERATION_TIMEOUT)).isTrue();

            verify(ownership, timeout(2000)).loadOwned(any(String.class), loadCaptor.capture(),
                    any(ProcessDefinition.class));
            assertThat(loadCaptor.getValue().code()).isEqualTo(code);
            assertThat(loadCaptor.getValue().version()).isEqualTo(version);
            assertThat(runtime.snapshot().getVersionRuntime().getDemandedVersions())
                .extracting("version")
                .containsExactly(version);
            assertThat(localRoutingState.getAliasRouteState().resolve(namespace, code, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo(version));

            String tombstonePayload =
                    RoutingStateCodec.aliasTombstoneJson(namespace, code, "production", 101L, "operator", now + 1L);
            assertThat(projectionStore.compareAndSet(stateKey, activePayload, tombstonePayload, "json",
                    OPERATION_TIMEOUT))
                .isTrue();

            verify(ownership, timeout(2000)).releaseOwned(any(String.class), unloadCaptor.capture());
            assertThat(unloadCaptor.getValue().code()).isEqualTo(code);
            assertThat(unloadCaptor.getValue()).isEqualTo(ProcessRef.version(namespace, code, version));
            assertThat(runtime.snapshot().getVersionRuntime().getDemandedVersions()).isEmpty();
            assertThat(runtime.snapshot().getVersionRuntime().getInstalledVersions())
                .noneMatch(process -> process.namespace().equals(namespace) && process.code().equals(code));
            assertThat(localRoutingState.getAliasRouteState().resolve(namespace, code, "production")).isEmpty();
        } finally {
            runtime.close();
        }
    }

    private DeploymentRuntime sourceRuntime(InMemoryDeploymentProjectionStore projectionStore,
            List<String> routingStateKeys, ProcessRuntimeOwnership ownership, ProcessArtifactSource source,
            LocalRoutingState localRoutingState) {
        return new DeploymentRuntime(new ProjectionStoreRoutingStateSubscriber(projectionStore, routingStateKeys,
                        OPERATION_TIMEOUT),
                VersionRuntimeManager.withOwnedRetryScheduler(new ArtifactSourceResolver(source),
                        new ProcessArtifactRuntimeLoader(ownership, ignored -> java.util.List.of(), ignored -> null,
                                new com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics()),
                        Runnable::run, localRoutingState, Duration.ofMinutes(5), 1_000), localRoutingState,
                java.util.Set.of());
    }
}
