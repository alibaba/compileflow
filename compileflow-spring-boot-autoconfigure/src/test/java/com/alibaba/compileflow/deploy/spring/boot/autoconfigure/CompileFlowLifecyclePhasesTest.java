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
import static org.mockito.Mockito.verifyNoInteractions;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxScheduler;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.ReconciliationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;

class CompileFlowLifecyclePhasesTest {
    private static final int WEB_SERVER_START_STOP_PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;
    private static final int WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE = SmartLifecycle.DEFAULT_PHASE - 1024;

    @Test
    void deploymentComponentsStartInDependencyOrderInsideTheWebLifecycleWindow() {
        SmartLifecycle dataPlane =
                new CompileFlowDeployDataPlaneAutoConfiguration().deployRuntimeLifecycle(mock(DeployRuntime.class));
        SmartLifecycle controlPlane = new CompileFlowDeployOutboxAutoConfiguration()
            .routingOutboxSchedulerLifecycle(mock(RoutingOutboxScheduler.class));
        SmartLifecycle reconciliation = new CompileFlowDeployControlPlaneAutoConfiguration()
            .routingProjectionReconcilerLifecycle(mock(RoutingProjectionReconciler.ScheduledReconciler.class),
                    mock(ReconciliationProperties.class));

        assertThat(dataPlane.getPhase()).isGreaterThan(WEB_SERVER_START_STOP_PHASE);
        assertThat(controlPlane.getPhase()).isGreaterThan(dataPlane.getPhase());
        assertThat(reconciliation.getPhase()).isGreaterThan(controlPlane.getPhase());
        assertThat(reconciliation.getPhase()).isLessThan(WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE);
    }

    @Test
    void disabledReconciliationModeDoesNotStartTheScheduledReconciler() {
        RoutingProjectionReconciler.ScheduledReconciler scheduled =
                mock(RoutingProjectionReconciler.ScheduledReconciler.class);
        ReconciliationProperties properties =
                new ReconciliationProperties(ReconciliationProperties.Mode.DISABLED, java.time.Duration.ofMinutes(1));
        SmartLifecycle lifecycle = new CompileFlowDeployControlPlaneAutoConfiguration()
            .routingProjectionReconcilerLifecycle(scheduled, properties);

        lifecycle.start();

        assertThat(lifecycle.isRunning()).isTrue();
        verifyNoInteractions(scheduled);
    }
}
