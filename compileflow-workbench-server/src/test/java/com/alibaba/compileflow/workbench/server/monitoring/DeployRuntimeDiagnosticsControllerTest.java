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
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.runtime.DeployRuntimeSnapshot;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalReadyRoutingStateSnapshot;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallerSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DeployRuntimeDiagnosticsControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T01:02:03Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void diagnosticsReportsUnavailableWhenRuntimeBeanIsMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeployRuntime> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeInstaller> installerProvider = mock(ObjectProvider.class);
        DeployRuntimeDiagnosticsController controller =
                new DeployRuntimeDiagnosticsController(provider, localReadyProvider, installerProvider, CLOCK);

        DeployRuntimeDiagnosticsResponse response = controller.getDeployRuntimeDiagnostics();

        assertThat(response.available()).isFalse();
        assertThat(response.started()).isFalse();
        assertThat(response.timestamp()).isEqualTo(NOW.toString());
    }

    @Test
    void diagnosticsReturnsRuntimeSnapshot() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeployRuntime> provider = mock(ObjectProvider.class);
        DeployRuntime runtime = mock(DeployRuntime.class);
        ProcessRef.Version demanded = ProcessRef.version("default", "payment.approve", "v1");
        ProcessRef.Version backedOff = ProcessRef.version("default", "payment.approve", "v0");
        RuntimeInstallerSnapshot installer = new RuntimeInstallerSnapshot(0, 1000, 1000, 300_000L, 2,
                Collections.emptyList(), Collections.singletonList(demanded), Collections.singletonList(backedOff),
                Collections.singletonList(
                        new RuntimeInstallerSnapshot.BackedOffVersion(backedOff, "compilation failed",
                                1_800_000_000_000L, 30_000L)),
                Collections.singletonMap("alias:default/payment.approve@production", "Runtime installation failed"),
                Collections.singletonList(
                        new RuntimeInstallerSnapshot.InstalledProcessSnapshot("default", "payment.approve",
                                Arrays.asList("v2", "v1"))));
        LocalReadyRoutingStateSnapshot routing = new LocalReadyRoutingStateSnapshot(1, 1, 0, 1,
                Collections.singletonList(
                        new LocalReadyRoutingStateSnapshot.AliasState("default", "payment.approve", "production", 7L,
                                false, 6L, false, LocalReadyRoutingStateSnapshot.ConvergenceState.FAILED)));
        when(runtime.snapshot()).thenReturn(new DeployRuntimeSnapshot(true, routing, installer));
        when(provider.getIfAvailable()).thenReturn(runtime);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeInstaller> installerProvider = mock(ObjectProvider.class);
        DeployRuntimeDiagnosticsController controller =
                new DeployRuntimeDiagnosticsController(provider, localReadyProvider, installerProvider, CLOCK);

        DeployRuntimeDiagnosticsResponse response = controller.getDeployRuntimeDiagnostics();

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
    void diagnosticsUsesEmbeddedLocalReadyComponentsWithoutDeployRuntime() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DeployRuntime> runtimeProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalRoutingReconciler> localReadyProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeInstaller> installerProvider = mock(ObjectProvider.class);
        LocalRoutingReconciler localReady = mock(LocalRoutingReconciler.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        LocalReadyRoutingStateSnapshot routing = new LocalReadyRoutingStateSnapshot(0, 0, 0, 0, Collections.emptyList());
        RuntimeInstallerSnapshot installerSnapshot = new RuntimeInstallerSnapshot(0, 1000, 1000, 300_000L, 0,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyList());
        when(localReadyProvider.getIfAvailable()).thenReturn(localReady);
        when(installerProvider.getIfAvailable()).thenReturn(installer);
        when(localReady.snapshot()).thenReturn(routing);
        when(installer.snapshot()).thenReturn(installerSnapshot);
        DeployRuntimeDiagnosticsController controller =
                new DeployRuntimeDiagnosticsController(runtimeProvider, localReadyProvider, installerProvider, CLOCK);

        DeployRuntimeDiagnosticsResponse response = controller.getDeployRuntimeDiagnostics();

        assertThat(response.available()).isTrue();
        assertThat(response.started()).isTrue();
        assertThat(response.topology()).isEqualTo("embedded");
        assertThat(response.desiredAliasCount()).isZero();
        assertThat(response.retainedRuntimeCount()).isZero();
    }
}
