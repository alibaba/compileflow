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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

class HealthIndicatorSecurityTest {
    @Test
    void deploymentHealthDoesNotExposeRepositoryFailureMessages() {
        RoutingOutboxAdminService control = mock(RoutingOutboxAdminService.class);
        String sensitiveMessage = "jdbc:postgresql://user:secret@database";
        when(control.getHealth()).thenThrow(new IllegalStateException(sensitiveMessage));

        Health health = new DeploymentPipelineHealthIndicator(control).health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails())
            .containsEntry("error", IllegalStateException.class.getName())
            .containsEntry("message", "Deployment health query failed")
            .doesNotContainValue(sensitiveMessage);
    }
}
