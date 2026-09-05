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

import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DeploymentRouteControllerTest {
    @Test
    void mapsAuthoritativeAliasStateToTypedResponse() {
        DeploymentService service = mock(DeploymentService.class);
        ProcessAliasState state = new ProcessAliasState(ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "payment.approve",
                        "regional-blue"), ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "payment.approve", "2.0.0"),
                ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "payment.approve", "2.1.0"), 1_000, 7L,
                "release-operator", Instant.ofEpochMilli(1_722_000_000_000L));
        when(service.getRoute("payment.approve", "regional-blue")).thenReturn(Optional.of(state));
        DeploymentRouteController controller = new DeploymentRouteController(service);

        DeploymentRouteResponse response = controller.getDeploymentRoute("payment.approve", "regional-blue").getBody();

        assertThat(response)
            .isEqualTo(
                    new DeploymentRouteResponse("payment.approve", "regional-blue", "2.0.0", "2.1.0", 1_000, null, null,
                            7L, "release-operator", 1_722_000_000_000L));
    }

    @Test
    void rejectsMalformedAliasBeforeServiceCall() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentRouteController controller = new DeploymentRouteController(service);

        assertProblem(() -> controller.getDeploymentRoute("payment.approve", "bad alias"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "alias must start with an ASCII letter or digit and contain only ASCII letters, " + "digits, '.', '_', or '-'");
        verify(service, never()).getRoute("payment.approve", "bad alias");
    }

    @Test
    void preservesTypedDeploymentFailuresForTheSharedHandler() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentException failure =
                DeploymentException.of(DeploymentErrorCode.REPOSITORY_ERROR, "repository unavailable");
        when(service.getRoute("payment.approve", "production")).thenThrow(failure);
        DeploymentRouteController controller = new DeploymentRouteController(service);

        assertThatThrownBy(() -> controller.getDeploymentRoute("payment.approve", "production")).isSameAs(failure);
    }

    @Test
    void doesNotExposeOrMisclassifyUnexpectedServiceFailure() {
        DeploymentService service = mock(DeploymentService.class);
        String sensitiveMessage = "database-password=secret";
        when(service.getRoute("payment.approve", "production")).thenThrow(
                new IllegalArgumentException(sensitiveMessage));
        DeploymentRouteController controller = new DeploymentRouteController(service);

        var failure = assertProblem(() -> controller.getDeploymentRoute("payment.approve", "production"),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal deployment route error");
        assertThat(failure.getBody().toString()).doesNotContain(sensitiveMessage);
    }
}
