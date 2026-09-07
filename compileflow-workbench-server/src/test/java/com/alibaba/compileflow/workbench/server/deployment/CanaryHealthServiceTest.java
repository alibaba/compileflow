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
package com.alibaba.compileflow.workbench.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogEntity;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanaryHealthServiceTest {
    private static final Instant ROLLOUT_STARTED = Instant.parse("2026-07-22T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-22T10:05:00Z"), ZoneOffset.UTC);

    private static DeploymentView deployment(String status) {
        return new DeploymentView("rollout-1", "payment.approve", "v2", "v1", "production", "deploy", status, "canary",
                10, null, 3L, 1L, 2L, ROLLOUT_STARTED.toString(), null, "tester", null, null);
    }

    private static List<ExecutionLogEntity> samples(int count, int failures) {
        List<ExecutionLogEntity> logs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ExecutionLogEntity log = new ExecutionLogEntity();
            log.setEffectiveVersion("v2");
            log.setStatus(index < failures ? "failed" : "success");
            log.setDurationMs(20L + index);
            logs.add(log);
        }
        return logs;
    }

    @Test
    void reportsUnhealthyActiveCanaryWithoutMutatingTheRollout() {
        ExecutionLogService logs = mock(ExecutionLogService.class);
        DeploymentService deployments = mock(DeploymentService.class);
        DeploymentView active = deployment("in_progress");
        when(deployments.getDeployment("rollout-1")).thenReturn(Optional.of(active));
        when(logs.findForRouteSince(ProcessRef.DEFAULT_NAMESPACE, "payment.approve", "production",
                ROLLOUT_STARTED.toEpochMilli()))
            .thenReturn(samples(20, 2));
        CanaryHealthService service = new CanaryHealthService(logs, deployments, CLOCK);
        CanaryHealthService.CanaryHealthRequest request =
                CanaryHealthService.CanaryHealthRequest.of(null, 20, 0.05D, null);

        CanaryHealthEvaluationResponse result = service.evaluate("rollout-1", request).orElseThrow();

        assertThat(result.decision()).isEqualTo("unhealthy");
        assertThat(result.metricsScope()).isEqualTo("workbench_server");
        assertThat(result.metricsSource()).isEqualTo("execution_logs");
        assertThat(result.reason()).isEqualTo("Canary error rate breached maxCanaryErrorRate.");
        assertThat(result.canary().samples()).isEqualTo(20);
        assertThat(result.canary().failures()).isEqualTo(2);
        verify(deployments, never())
            .abortCanary(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsEmptyWhenDeploymentDoesNotExist() {
        ExecutionLogService logs = mock(ExecutionLogService.class);
        DeploymentService deployments = mock(DeploymentService.class);
        when(deployments.getDeployment("missing")).thenReturn(Optional.empty());
        CanaryHealthService service = new CanaryHealthService(logs, deployments, CLOCK);

        assertThat(service.evaluate("missing", CanaryHealthService.CanaryHealthRequest.of(null, null, null, null))).isEmpty();
        verify(logs, never())
            .findForRouteSince(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsNonFiniteErrorRateThresholds() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> CanaryHealthService.CanaryHealthRequest.of(null, null, value, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCanaryErrorRate must be between 0 and 1");
        }
    }

    @Test
    void rejectsHealthEvaluationAfterCanaryIsTerminal() {
        ExecutionLogService logs = mock(ExecutionLogService.class);
        DeploymentService deployments = mock(DeploymentService.class);
        when(deployments.getDeployment("rollout-1")).thenReturn(Optional.of(deployment("completed")));
        CanaryHealthService service = new CanaryHealthService(logs, deployments, CLOCK);

        assertThatThrownBy(() -> service.evaluate("rollout-1", null))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ROLLOUT_CONFLICT))
            .hasMessageContaining("active canary");
        verify(logs, never())
            .findForRouteSince(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
