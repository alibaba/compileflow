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
import static org.mockito.Mockito.verifyNoInteractions;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;

class CompileFlowLifecyclePhasesTest {
    private static final int WEB_SERVER_START_STOP_PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;
    private static final int WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE = SmartLifecycle.DEFAULT_PHASE - 1024;

    @Test
    void deploymentComponentsStartInDependencyOrderInsideTheWebLifecycleWindow() {
        SmartLifecycle dataPlane =
                new CompileFlowDeployRuntimeAutoConfiguration()
            .deploymentRuntimeLifecycle(mock(DeploymentRuntime.class));
        SmartLifecycle controlPlane = new CompileFlowDeployOutboxAutoConfiguration()
            .routingOutboxSchedulerLifecycle(mock(RoutingOutboxScheduler.class));
        SmartLifecycle reconciliation = new CompileFlowDeployControlPlaneAutoConfiguration()
            .deploymentProjectionReconcilerLifecycle(mock(DeploymentProjectionReconciler.ScheduledReconciler.class),
                    mock(CompileFlowDeploymentProperties.class));

        assertThat(dataPlane.getPhase()).isGreaterThan(WEB_SERVER_START_STOP_PHASE);
        assertThat(controlPlane.getPhase()).isGreaterThan(dataPlane.getPhase());
        assertThat(reconciliation.getPhase()).isGreaterThan(controlPlane.getPhase());
        assertThat(reconciliation.getPhase()).isLessThan(WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE);
    }

    @Test
    void disabledReconciliationModeDoesNotStartTheScheduledReconciler() {
        DeploymentProjectionReconciler.ScheduledReconciler scheduled =
                mock(DeploymentProjectionReconciler.ScheduledReconciler.class);
        ReconciliationProperties properties =
                new ReconciliationProperties(ReconciliationProperties.Mode.DISABLED, java.time.Duration.ofMinutes(1));
        CompileFlowDeploymentProperties deploymentProperties = mock(CompileFlowDeploymentProperties.class);
        when(deploymentProperties.getReconciliation()).thenReturn(properties);
        SmartLifecycle lifecycle = new CompileFlowDeployControlPlaneAutoConfiguration()
            .deploymentProjectionReconcilerLifecycle(scheduled, deploymentProperties);

        lifecycle.start();

        assertThat(lifecycle.isRunning()).isTrue();
        verifyNoInteractions(scheduled);
    }
}
