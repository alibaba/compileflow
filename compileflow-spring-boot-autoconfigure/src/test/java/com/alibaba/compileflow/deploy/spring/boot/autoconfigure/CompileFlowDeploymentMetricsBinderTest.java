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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.RuntimeInstallReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Outcome;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalReadyRoutingStateSnapshot;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallerSnapshot;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.observability.CompileFlowDeploymentMetricsBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CompileFlowDeploymentMetricsBinderTest {
    @Test
    void bindsBoundedCountersAndAggregateNodeGauges() {
        ProcessDeploymentMetrics runtimeMetrics = new ProcessDeploymentMetrics();
        ProcessDeploymentOperationMetrics controlMetrics = new ProcessDeploymentOperationMetrics();
        runtimeMetrics.recordRuntimeInstall(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_INTEGRITY);
        runtimeMetrics.recordAliasConvergence(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE);
        runtimeMetrics.recordError(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH);
        controlMetrics.recordSuccess(Operation.PUBLISH);

        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallerSnapshot installerSnapshot = mock(RuntimeInstallerSnapshot.class);
        when(installer.snapshot()).thenReturn(installerSnapshot);
        when(installerSnapshot.getRetainedRuntimeCount()).thenReturn(3);
        LocalRoutingReconciler applier = mock(LocalRoutingReconciler.class);
        LocalReadyRoutingStateSnapshot routingSnapshot = mock(LocalReadyRoutingStateSnapshot.class);
        when(applier.snapshot()).thenReturn(routingSnapshot);
        when(routingSnapshot.getDesiredAliasCount()).thenReturn(5);
        when(routingSnapshot.getLocalReadyAliasCount()).thenReturn(4);
        when(routingSnapshot.getPendingAliasCount()).thenReturn(1);
        RoutingProjectionReconciler reconciliation = mock(RoutingProjectionReconciler.class);
        when(reconciliation.getTotalArtifactMissing()).thenReturn(2L);
        when(reconciliation.getTotalArtifactRepairs()).thenReturn(1L);
        when(reconciliation.getTotalArtifactConflicts()).thenReturn(1L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new CompileFlowDeploymentMetricsBinder(runtimeMetrics, controlMetrics, () -> installer, () -> applier, () -> reconciliation)
            .bindTo(registry);

        assertThat(registry
            .get("compileflow.deploy.runtime.install.attempts")
            .tags("outcome", "failure", "reason", "artifact_integrity")
            .functionCounter()
            .count())
            .isEqualTo(1.0);
        assertThat(registry
            .get("compileflow.deploy.alias.convergence")
            .tags("outcome", "success", "reason", "none")
            .functionCounter()
            .count())
            .isEqualTo(1.0);
        assertThat(registry
            .get("compileflow.deploy.errors")
            .tag("error.code", "ARTIFACT_DIGEST_MISMATCH")
            .functionCounter()
            .count())
            .isEqualTo(1.0);
        assertThat(registry
            .get("compileflow.deploy.operations")
            .tags("operation", "publish", "outcome", "success")
            .functionCounter()
            .count())
            .isEqualTo(1.0);
        assertThat(registry.get("compileflow.deploy.reconciliation.artifact.missing").functionCounter().count()).isEqualTo(
                2.0);
        assertThat(registry.get("compileflow.deploy.reconciliation.artifact.repairs").functionCounter().count()).isEqualTo(
                1.0);
        assertThat(registry.get("compileflow.deploy.reconciliation.artifact.conflicts").functionCounter().count())
            .isEqualTo(1.0);
        assertThat(registry.find("compileflow.deploy.operations").functionCounters())
            .hasSize(Operation.values().length * Outcome.values().length);
        assertThat(registry.get("compileflow.deploy.alias.desired.count").gauge().value()).isEqualTo(5.0);
        assertThat(registry.get("compileflow.deploy.alias.local_ready.count").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("compileflow.deploy.alias.pending.count").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("compileflow.deploy.runtime.retained.count").gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("compileflow.runtime.install.attempts").meters()).isEmpty();
        assertThat(registry.find("compileflow.alias.convergence").meters()).isEmpty();
    }
}
