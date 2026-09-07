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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.control.outbox.RoutingOutboxAdminService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DeploymentControlControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T01:02:03Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static RoutingOutboxAdminService.PipelineHealth health(long pendingCount, long failedCount,
            boolean dispatcherRunning, boolean outboxStateAvailable) {
        return new RoutingOutboxAdminService.PipelineHealth(pendingCount, 0L, 0L, failedCount, dispatcherRunning,
                outboxStateAvailable);
    }

    @Test
    void healthReturnsPipelineSnapshot() {
        RoutingOutboxAdminService control = mock(RoutingOutboxAdminService.class);
        when(control.getHealth()).thenReturn(health(2L, 0L, true, true));
        DeploymentControlController controller = new DeploymentControlController(control, CLOCK);

        ResponseEntity<DeploymentControlHealthResponse> response = controller.getDeploymentControlHealth();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().pendingCount()).isEqualTo(2L);
        assertThat(response.getBody().processingCount()).isZero();
        assertThat(response.getBody().expiredClaimCount()).isZero();
        assertThat(response.getBody().outboxStateAvailable()).isTrue();
        assertThat(response.getBody().checkedAt()).isEqualTo(NOW.toString());
    }

    @Test
    void requeueDeadLettersReturnsCountAndHealth() {
        RoutingOutboxAdminService control = mock(RoutingOutboxAdminService.class);
        when(control.requeueDeadLetters()).thenReturn(3);
        when(control.getHealth()).thenReturn(health(3L, 0L, true, true));
        DeploymentControlController controller = new DeploymentControlController(control, CLOCK);

        ResponseEntity<RequeueDeploymentDeadLettersResponse> response = controller.requeueDeploymentDeadLetters();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().requeued()).isEqualTo(3);
        assertThat(response.getBody().requeuedAt()).isEqualTo(NOW.toString());
        assertThat(response.getBody().health().status()).isEqualTo("UP");
    }
}
