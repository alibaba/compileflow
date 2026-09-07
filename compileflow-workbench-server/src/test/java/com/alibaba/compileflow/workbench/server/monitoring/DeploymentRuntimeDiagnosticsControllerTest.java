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
package com.alibaba.compileflow.workbench.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntimeSnapshot;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.RoutingConvergenceSnapshot;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManagerSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DeploymentRuntimeDiagnosticsControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T01:02:03Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void diagnosticsReportsUnavailableWhenRuntimeBeanIsMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeploymentRuntime> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider = mock(ObjectProvider.class);
        DeploymentRuntimeDiagnosticsController controller = new DeploymentRuntimeDiagnosticsController(provider,
                localReadyProvider, versionRuntimeManagerProvider, CLOCK);

        DeploymentRuntimeDiagnosticsResponse response = controller.getDeploymentRuntimeDiagnostics();

        assertThat(response.available()).isFalse();
        assertThat(response.started()).isFalse();
        assertThat(response.timestamp()).isEqualTo(NOW.toString());
    }

    @Test
    void diagnosticsReturnsRuntimeSnapshot() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeploymentRuntime> provider = mock(ObjectProvider.class);
        DeploymentRuntime runtime = mock(DeploymentRuntime.class);
        ProcessRef.Version demanded = ProcessRef.version("default", "payment.approve", "v1");
        ProcessRef.Version backedOff = ProcessRef.version("default", "payment.approve", "v0");
        VersionRuntimeManagerSnapshot manager = new VersionRuntimeManagerSnapshot(0, 1000, 1000, 300_000L, 2,
                Collections.emptyList(), Collections.singletonList(demanded), Collections.singletonList(backedOff),
                Collections.singletonList(
                        new VersionRuntimeManagerSnapshot.BackedOffVersion(backedOff, "compilation failed",
                                1_800_000_000_000L, 30_000L)),
                Collections.singletonList(
                        new VersionRuntimeManagerSnapshot.InstalledProcessSnapshot("default", "payment.approve",
                                Arrays.asList("v2", "v1"))));
        RoutingConvergenceSnapshot routing = new RoutingConvergenceSnapshot(1, 1, 0, 1,
                Collections.singletonList(
                        new RoutingConvergenceSnapshot.AliasState("default", "payment.approve", "production", 7L, false,
                                6L, false, RoutingConvergenceSnapshot.ConvergenceState.FAILED,
                                "Runtime installation failed")));
        when(runtime.snapshot()).thenReturn(new DeploymentRuntimeSnapshot(true, routing, manager));
        when(provider.getIfAvailable()).thenReturn(runtime);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider = mock(ObjectProvider.class);
        DeploymentRuntimeDiagnosticsController controller = new DeploymentRuntimeDiagnosticsController(provider,
                localReadyProvider, versionRuntimeManagerProvider, CLOCK);

        DeploymentRuntimeDiagnosticsResponse response = controller.getDeploymentRuntimeDiagnostics();

        assertThat(response.available()).isTrue();
        assertThat(response.started()).isTrue();
        assertThat(response.topology()).isEqualTo("distributed");
        assertThat(response.inflightCount()).isZero();
        assertThat(response.inflightCapacity()).isEqualTo(1000);
        assertThat(response.retainedRuntimeCount()).isEqualTo(2);
        assertThat(response.desiredAliasCount()).isEqualTo(1);
        assertThat(response.localReadyAliasCount()).isEqualTo(1);
        assertThat(response.aliases().get(0).desiredRevision()).isEqualTo(7L);
        assertThat(response.aliases().get(0).localReadyRevision()).isEqualTo(6L);
        assertThat(response.aliases().get(0).state()).isEqualTo("failed");
        assertThat(response.aliases().get(0).failureReason()).isEqualTo("Runtime installation failed");
        assertThat(response.demandedVersions().get(0).code()).isEqualTo("payment.approve");
        assertThat(response.backedOffVersions().get(0).reason()).isEqualTo("compilation failed");
        assertThat(response.backedOffVersions().get(0).blockedUntil()).isEqualTo("2027-01-15T08:00:00Z");
        assertThat(response.pendingReleaseVersions().get(0).version()).isEqualTo("v0");
        assertThat(response.deployedVersions().get(0).namespace()).isEqualTo("default");
        assertThat(response.deployedVersions().get(0).code()).isEqualTo("payment.approve");
        assertThat(response.deployedVersions().get(0).versions()).containsExactly("v1", "v2");
    }

    @Test
    void diagnosticsUsesEmbeddedLocalReadyComponentsWithoutDeploymentRuntime() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeploymentRuntime> runtimeProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider = mock(ObjectProvider.class);
        LocalRoutingReconciler localReady = mock(LocalRoutingReconciler.class);
        VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
        RoutingConvergenceSnapshot routing = new RoutingConvergenceSnapshot(0, 0, 0, 0, Collections.emptyList());
        VersionRuntimeManagerSnapshot versionRuntimeSnapshot = new VersionRuntimeManagerSnapshot(0, 1000, 1000, 300_000L,
                0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());
        when(localReadyProvider.getIfAvailable()).thenReturn(localReady);
        when(versionRuntimeManagerProvider.getIfAvailable()).thenReturn(manager);
        when(localReady.snapshot()).thenReturn(routing);
        when(manager.snapshot()).thenReturn(versionRuntimeSnapshot);
        DeploymentRuntimeDiagnosticsController controller = new DeploymentRuntimeDiagnosticsController(runtimeProvider,
                localReadyProvider, versionRuntimeManagerProvider, CLOCK);

        DeploymentRuntimeDiagnosticsResponse response = controller.getDeploymentRuntimeDiagnostics();

        assertThat(response.available()).isTrue();
        assertThat(response.started()).isTrue();
        assertThat(response.topology()).isEqualTo("embedded");
        assertThat(response.desiredAliasCount()).isZero();
        assertThat(response.retainedRuntimeCount()).isZero();
    }
}
