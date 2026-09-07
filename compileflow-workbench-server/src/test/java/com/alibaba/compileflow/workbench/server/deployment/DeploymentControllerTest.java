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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DeploymentControllerTest {
    private static DeploymentController controller(DeploymentService service) {
        return new DeploymentController(service, mock(CanaryHealthService.class));
    }

    private static CreateDeploymentRequest createBody(String strategy, Integer weightBps) {
        return new CreateDeploymentRequest("payment.approve", "v2", "production", strategy, null, weightBps, null,
                Map.of(), 0L);
    }

    private static DeploymentView deployment(String id, String status, String strategy, Integer canaryWeightBps,
            long revision) {
        return new DeploymentView(id, "payment.approve", "v2", "v1", "production",
                id.startsWith("rollback") ? "rollback" : "deploy", status, strategy, canaryWeightBps, null, revision, 1L,
                2L, "2026-07-05T00:00:00Z", "2026-07-05T00:00:01Z", "tester", null, 1000L);
    }

    @Test
    void createDeploymentRequiresIdempotencyKeyAndReturnsCreated() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        when(service.createDeployment(any(CreateDeploymentCommand.class)))
            .thenReturn(deployment("deploy-1", "completed", "all_at_once", null, 2L));
        CreateDeploymentRequest body = createBody("all_at_once", null);

        assertProblem(() -> controller.createDeployment(null, body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Idempotency-Key, processCode, and version are required");
        assertProblem(() -> controller.createDeployment(" request-1 ", body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "idempotencyKey must not contain surrounding whitespace");
        ResponseEntity<DeploymentResponse> created = controller.createDeployment("request-1", body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().id()).isEqualTo("deploy-1");
        assertThat(created.getBody().operation()).isEqualTo("deploy");
        assertThat(created.getBody().revision()).isEqualTo(2L);
        ArgumentCaptor<CreateDeploymentCommand> captor = ArgumentCaptor.forClass(CreateDeploymentCommand.class);
        verify(service).createDeployment(captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("request-1");
        assertThat(captor.getValue().alias()).isEqualTo("production");
    }

    @Test
    void createDeploymentAcceptsAnyValidAlias() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        CreateDeploymentRequest body = new CreateDeploymentRequest("payment.approve", "v2", "regional-blue",
                "all_at_once", null, null, null, Map.of(), 0L);
        when(service.createDeployment(any(CreateDeploymentCommand.class)))
            .thenReturn(deployment("deploy-1", "completed", "all_at_once", null, 2L));

        assertThat(controller.createDeployment("request-1", body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<CreateDeploymentCommand> captor = ArgumentCaptor.forClass(CreateDeploymentCommand.class);
        verify(service).createDeployment(captor.capture());
        assertThat(captor.getValue().alias()).isEqualTo("regional-blue");
    }

    @Test
    void createDeploymentRejectsInvalidCanaryWeightBps() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        CreateDeploymentRequest body = createBody("canary", 10_000);

        assertProblem(() -> controller.createDeployment("request-1", body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "canaryWeightBps must be between 1 and 9999");
        verify(service, never()).createDeployment(any(CreateDeploymentCommand.class));
    }

    @Test
    void listDeploymentsValidatesFiltersAndDelegatesToControlPlane() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        when(service.listDeployments("payment.approve", "deploy", "production", "completed", null, 20))
            .thenReturn(
                    new DeploymentPage(Collections.singletonList(
                                    deployment("deploy-1", "completed", "all_at_once", null, 2L)), "next"));

        ResponseEntity<DeploymentListResponse> response =
                controller.listDeployments("payment.approve", "deploy", "production", "completed", null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().nextCursor()).isEqualTo("next");
        assertThat(response.getBody().hasMore()).isTrue();
        verify(service).listDeployments("payment.approve", "deploy", "production", "completed", null, 20);
    }

    @Test
    void listDeploymentsRejectsOversizedPageBeforeServiceCall() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);

        assertProblem(() -> controller.listDeployments(null, null, null, null, null, 101), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "limit must be between 1 and 100");
        verify(service, never()).listDeployments(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void listDeploymentsMapsUnsupportedStatusToBadRequest() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        when(service.listDeployments(null, null, null, "running", null, 20))
            .thenThrow(
                    new InvalidDeploymentRequestException("Unsupported deployment status: running",
                            new IllegalArgumentException("Unsupported deployment status: running")));

        assertProblem(() -> controller.listDeployments(null, null, null, "running", null, 20), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "Unsupported deployment status: running");
    }

    @Test
    void updateCanaryRequiresExpectedRevision() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        UpdateCanaryRequest body = new UpdateCanaryRequest(3_000, null);

        assertProblem(() -> controller.updateCanary("deploy-1", body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "expectedRevision must be greater than 0");
        verify(service, never()).updateCanary(eq("deploy-1"), eq(3_000), anyLong());
    }

    @Test
    void updateAndPromotePassRevisionToService() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        when(service.updateCanary("deploy-1", 3_000, 2L))
            .thenReturn(deployment("deploy-1", "in_progress", "canary", 3_000, 3L));
        when(service.promoteCanary("deploy-1", 3L)).thenReturn(
                deployment("deploy-1", "completed", "canary", 10_000, 4L));
        UpdateCanaryRequest update = new UpdateCanaryRequest(3_000, 2L);
        PromoteCanaryRequest promote = new PromoteCanaryRequest(3L);

        ResponseEntity<DeploymentResponse> updated = controller.updateCanary("deploy-1", update);
        ResponseEntity<DeploymentResponse> promoted = controller.promoteCanary("deploy-1", promote);

        assertThat(updated.getBody().revision()).isEqualTo(3L);
        assertThat(updated.getBody().canaryWeightBps()).isEqualTo(3_000);
        assertThat(promoted.getBody().revision()).isEqualTo(4L);
        assertThat(promoted.getBody().status()).isEqualTo("completed");
    }

    @Test
    void rollbackRequiresASeparateIdempotencyKey() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        when(service.rollbackDeployment("deploy-1", "rollback-1", 2L))
            .thenReturn(deployment("rollback-2", "completed", "all_at_once", null, 2L));
        RollbackDeploymentRequest body = new RollbackDeploymentRequest(2L);

        assertProblem(() -> controller.rollbackDeployment("deploy-1", null, body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "Idempotency-Key is required");
        assertProblem(() -> controller.rollbackDeployment("deploy-1", " rollback-1 ", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "idempotencyKey must not contain surrounding whitespace");
        ResponseEntity<DeploymentResponse> rollback = controller.rollbackDeployment("deploy-1", "rollback-1", body);

        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rollback.getBody().id()).isEqualTo("rollback-2");
        assertThat(rollback.getBody().operation()).isEqualTo("rollback");
    }

    @Test
    void evaluateCanaryDelegatesValidatedThresholds() {
        CanaryHealthService healthService = mock(CanaryHealthService.class);
        DeploymentController controller = new DeploymentController(mock(DeploymentService.class), healthService);
        CanaryVersionStatsResponse stats = new CanaryVersionStatsResponse("v2", 20, 0, 0D, 42L);
        CanaryHealthEvaluationResponse evaluation = new CanaryHealthEvaluationResponse(DeploymentResponseMapper.toResponse(
                        deployment("deploy-1", "in_progress", "canary", 1_000, 2L)), "workbench_server",
                "execution_logs", "healthy", "Canary metrics are within configured thresholds.", stats, stats,
                new CanaryHealthThresholdsResponse(600_000L, 20, 0.05D, 0L));
        when(healthService.evaluate(eq("deploy-1"), any(CanaryHealthService.CanaryHealthRequest.class)))
            .thenReturn(Optional.of(evaluation));

        ResponseEntity<CanaryHealthEvaluationResponse> response =
                controller.evaluateCanary("deploy-1", new EvaluateCanaryRequest(null, 20, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().decision()).isEqualTo("healthy");
    }

    @Test
    void rollbackPreservesUnexpectedServiceFailuresInsteadOfExposingThemAsBadRequests() {
        DeploymentService service = mock(DeploymentService.class);
        IllegalArgumentException failure = new IllegalArgumentException("database-password=secret");
        when(service.rollbackDeployment("deploy-1", "rollback-1", 2L)).thenThrow(failure);

        assertThatThrownBy(() -> controller(service)
            .rollbackDeployment("deploy-1", "rollback-1", new RollbackDeploymentRequest(2L)))
            .isSameAs(failure);
    }

    @Test
    void evaluateCanaryReturnsNotFoundForMissingDeployment() {
        CanaryHealthService healthService = mock(CanaryHealthService.class);
        DeploymentController controller = new DeploymentController(mock(DeploymentService.class), healthService);
        when(healthService.evaluate(eq("missing"), any(CanaryHealthService.CanaryHealthRequest.class)))
            .thenReturn(Optional.empty());

        assertProblem(() -> controller.evaluateCanary("missing", null), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Deployment 'missing' was not found");
    }

    @Test
    void returnsTypedDeploymentEventsAndNotFoundForAnUnknownParent() {
        DeploymentService service = mock(DeploymentService.class);
        DeploymentController controller = controller(service);
        DeploymentEventView event = new DeploymentEventView(1L, 1L, "COMPLETED", null, "completed", "bridge-service",
                null, "2026-07-05T00:00:00Z");
        when(service.getDeploymentEvents("deploy-1")).thenReturn(Optional.of(List.of(event)));
        when(service.getDeploymentEvents("missing")).thenReturn(Optional.empty());

        ResponseEntity<List<DeploymentEventView>> existing = controller.getDeploymentEvents("deploy-1");
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getBody()).containsExactly(event);
        assertProblem(() -> controller.getDeploymentEvents("missing"), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Deployment 'missing' was not found");
    }

    @Test
    void deploymentResponseOmitsAbsentOptionalFields() {
        DeploymentView view = new DeploymentView("deploy-1", "payment.approve", "v1", null, "production", "deploy",
                "completed", "all_at_once", null, null, 1L, 0L, 1L, "2026-07-05T00:00:00Z", null, "tester", null, null);

        DeploymentResponse response = DeploymentResponseMapper.toResponse(view);
        assertThat(response.strategy()).isEqualTo("all_at_once");
        assertThat(response.baselineVersion()).isNull();
        assertThat(response.canaryWeightBps()).isNull();
        assertThat(response.deployedAt()).isNull();
        assertThat(response.notes()).isNull();
        assertThat(response.duration()).isNull();
    }
}
