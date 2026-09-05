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
package com.alibaba.compileflow.workbench.server.process;

import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadataKeys;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionPage;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionQuery;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.workbench.server.execution.PublishedProcessExecutionService;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class ProcessControllerTest {
    private static ProcessDraftService.ProcessRecord flow(String code) {
        return new ProcessDraftService.ProcessRecord(code, "Order Approval", ProcessModelType.TBBPM,
                "<bpm code=\"" + code + "\"/>", "Approval flow", Arrays.asList("order", "approval"),
                "2026-07-05T00:00:00Z", "2026-07-05T00:00:00Z", "tester", 7L);
    }

    private static ProcessPreflightReport passingPreflight() {
        return ProcessPreflightReport
            .builder()
            .code("order.approve")
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS, 1L, "lint ok")
            .addItem(ProcessPreflightReport.ItemType.COMPILE, ProcessPreflightReport.ItemStatus.PASS, 1L, "compile ok")
            .totalDurationMs(2L)
            .build();
    }

    private static ProcessPreflightReport failingPreflight() {
        return ProcessPreflightReport
            .builder()
            .code("order.approve")
            .addItem(ProcessPreflightReport.ItemType.COMPILE, ProcessPreflightReport.ItemStatus.FAIL, 1L,
                    "compile failed")
            .totalDurationMs(1L)
            .build();
    }

    private static PublishedProcessVersion publishResult(PublishProcessVersionCommand command) {
        ProcessRef.Version ref = command.getRef();
        return new PublishedProcessVersion(ref, command.getModelType(),
                DigestUtils.sha256Hex(command.getDefinition().content()), command.getMetadata(), command.getActor(),
                Instant.ofEpochMilli(1_700_000_000_000L));
    }

    private static PublishedProcessVersion version(String code, String version, String changelog) {
        return new PublishedProcessVersion(ProcessRef.version("default", code, version), ProcessModelType.TBBPM,
                DigestUtils.sha256Hex("<bpm/>"), Map.of(ReleaseMetadataKeys.CHANGELOG, changelog), "tester",
                Instant.ofEpochMilli(1_700_000_000_000L));
    }

    private static ProcessController controller(PublishedProcessExecutionService executionService,
            ProcessDraftService flowDraftService) {
        return controller(executionService, mock(ProcessDefinitionPreflightService.class), flowDraftService);
    }

    private static ProcessController controller(PublishedProcessExecutionService executionService,
            ProcessDefinitionPreflightService preflightService, ProcessDraftService flowDraftService) {
        return new ProcessController(executionService, preflightService, flowDraftService,
                mock(ProcessDeploymentService.class), serverIdentity());
    }

    private static ServerIdentity serverIdentity() {
        ServerIdentity identity = mock(ServerIdentity.class);
        when(identity.principal()).thenReturn("test-service");
        return identity;
    }

    @Test
    void listProcessesNormalizesContractFiltersBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        when(flowDraftService.listProcesses(any(ProcessDraftService.ProcessListQuery.class)))
            .thenReturn(
                    new ProcessDraftService.ProcessListResult(Collections.<ProcessDraftService.ProcessSummaryRecord>emptyList(),
                            0, 1, 20));
        ArgumentCaptor<ProcessDraftService.ProcessListQuery> captor =
                ArgumentCaptor.forClass(ProcessDraftService.ProcessListQuery.class);

        ResponseEntity<ProcessListResponse> response =
                controller.listProcesses(" BPMN ", "order", 1, 20, " updatedAt ", " desc ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(flowDraftService).listProcesses(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ProcessModelType.BPMN);
        assertThat(captor.getValue().sortBy()).isEqualTo("updatedAt");
        assertThat(captor.getValue().sortOrder()).isEqualTo("desc");
    }

    @Test
    void listProcessesRejectsUnsupportedTypeBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);

        assertProblem(() -> controller.listProcesses("DMN", null, 1, 20, "updatedAt", "desc"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "type must be one of: BPMN, TBBPM");
        verify(flowDraftService, never()).listProcesses(any(ProcessDraftService.ProcessListQuery.class));
    }

    @Test
    void listProcessesRejectsInvalidPaginationBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);

        assertProblem(() -> controller.listProcesses(null, null, 0, 20, "updatedAt", "desc"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "page must be greater than or equal to 1");
        verify(flowDraftService, never()).listProcesses(any(ProcessDraftService.ProcessListQuery.class));
    }

    @Test
    void listProcessesRejectsPageSizeAboveThePublishedLimit() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);

        assertProblem(() -> controller.listProcesses(null, null, 1, 101, "updatedAt", "desc"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "pageSize must be less than or equal to 100");
        verify(flowDraftService, never()).listProcesses(any(ProcessDraftService.ProcessListQuery.class));
    }

    @Test
    void createProcessNormalizesMetadataAndPreservesDefinitionBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        when(flowDraftService.createProcess(any(ProcessDraftService.ProcessCreate.class))).thenReturn(
                flow("order.approve"));
        CreateProcessRequest body = new CreateProcessRequest(" order.approve ", " Order Approval ",
                ProcessModelType.TBBPM, " <bpm code=\"order.approve\"/> ", " Approval flow ",
                Arrays.asList(" order ", "", " approval "));
        ArgumentCaptor<ProcessDraftService.ProcessCreate> captor =
                ArgumentCaptor.forClass(ProcessDraftService.ProcessCreate.class);

        ResponseEntity<ProcessDefinitionResponse> response = controller.createProcess(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(flowDraftService).createProcess(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("order.approve");
        assertThat(captor.getValue().name()).isEqualTo("Order Approval");
        assertThat(captor.getValue().type()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(captor.getValue().xml()).isEqualTo(" <bpm code=\"order.approve\"/> ");
        assertThat(captor.getValue().description()).isEqualTo("Approval flow");
        assertThat(captor.getValue().tags()).isEqualTo(Arrays.asList("order", "approval"));
    }

    @Test
    void processRequestsUseUnicodeCodePointBoundsAndRejectMalformedText() {
        String longestName = "\uD83D\uDE00".repeat(CreateProcessRequest.MAX_NAME_LENGTH);
        CreateProcessRequest request = new CreateProcessRequest(" order.approve ", "\u00a0" + longestName + "\u00a0",
                ProcessModelType.TBBPM, "<bpm/>", null, List.of("\u00a0order\u00a0"));

        assertThat(request.name()).isEqualTo(longestName);
        assertThat(request.tags()).containsExactly("order");
        assertThatThrownBy(() -> new CreateProcessRequest("order.approve", longestName + "x", ProcessModelType.TBBPM,
                null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("256 characters");
        assertThatThrownBy(() -> new ProcessPreflightRequest("order.approve", ProcessModelType.TBBPM, "\ud800"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("xml must be valid Unicode");
        assertThatThrownBy(() -> new ProcessPreflightRequest("invalid code", ProcessModelType.TBBPM, "<bpm/>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code must start with an ASCII letter or digit");
        assertThat(new PublishProcessRequest("\uD83D\uDE00".repeat(2_048), 1L).changelog()).hasSize(4_096);
        assertThatThrownBy(() -> new PublishProcessRequest("\uD83D\uDE00".repeat(2_049), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2048 characters");
    }

    @Test
    void createProcessRequiresTypeBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        CreateProcessRequest body = new CreateProcessRequest("order.approve", "Order Approval", null, null, null, null);

        assertProblem(() -> controller.createProcess(body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "type is required");
        verify(flowDraftService, never()).createProcess(any(ProcessDraftService.ProcessCreate.class));
    }

    @Test
    void publishProcessRequiresIdempotencyKeyBeforeReadingDraft() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        ProcessController controller = new ProcessController(mock(PublishedProcessExecutionService.class),
                mock(ProcessDefinitionPreflightService.class), flowDraftService, deploymentService, serverIdentity());

        assertProblem(() -> controller.publishProcess("order.approve", null, new PublishProcessRequest(null, 7L)),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Idempotency-Key is required");
        assertProblem(() -> controller.publishProcess("order.approve", " request-1 ",
                        new PublishProcessRequest(null, 7L)), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "idempotencyKey must not contain surrounding whitespace");
        verify(flowDraftService, never()).getProcess(any(String.class));
        verify(deploymentService, never()).publish(any(PublishProcessVersionCommand.class));
    }

    @Test
    void publishProcessUsesStableVersionIdentityAndPersistsChangelog() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        when(flowDraftService.getProcess("order.approve")).thenReturn(Optional.of(flow("order.approve")));
        AtomicReference<PublishedProcessVersion> stored = new AtomicReference<>();
        when(deploymentService.getVersion(any(ProcessRef.Version.class)))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(deploymentService.publish(any(PublishProcessVersionCommand.class))).thenAnswer(invocation -> {
            PublishedProcessVersion published = publishResult(invocation.getArgument(0));
            stored.set(published);
            return published;
        });
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        when(preflightService.preflight(any(String.class), any(ProcessModelType.class), any(String.class)))
            .thenReturn(passingPreflight());
        ProcessController controller = new ProcessController(executionService, preflightService, flowDraftService,
                deploymentService, serverIdentity());
        PublishProcessRequest body = new PublishProcessRequest("Add approval limit", 7L);

        ResponseEntity<ProcessVersionResponse> first =
                controller.publishProcess("order.approve", "publish-request-1", body);
        ResponseEntity<ProcessVersionResponse> replay =
                controller.publishProcess("order.approve", "publish-request-1", body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().version()).isEqualTo(replay.getBody().version());
        assertThat(first.getBody().changelog()).isEqualTo("Add approval limit");
        assertThat(first.getBody().modelType()).isEqualTo(ProcessModelType.TBBPM);
        ArgumentCaptor<PublishProcessVersionCommand> captor =
                ArgumentCaptor.forClass(PublishProcessVersionCommand.class);
        verify(deploymentService).publish(captor.capture());
        verify(flowDraftService).getProcess("order.approve");
        verify(preflightService).preflight("order.approve", ProcessModelType.TBBPM, flow("order.approve").xml());
        assertThat(captor.getValue().getMetadata()).containsEntry(ReleaseMetadataKeys.CHANGELOG, "Add approval limit");
        assertThat(captor.getValue().getActor()).isEqualTo("test-service");
    }

    @Test
    void publishProcessRejectsReusingACommittedKeyForDifferentMetadata() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        when(flowDraftService.getProcess("order.approve")).thenReturn(Optional.of(flow("order.approve")));
        AtomicReference<PublishedProcessVersion> stored = new AtomicReference<>();
        when(deploymentService.getVersion(any(ProcessRef.Version.class)))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(deploymentService.publish(any(PublishProcessVersionCommand.class))).thenAnswer(invocation -> {
            PublishedProcessVersion published = publishResult(invocation.getArgument(0));
            stored.set(published);
            return published;
        });
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        when(preflightService.preflight(any(String.class), any(ProcessModelType.class), any(String.class)))
            .thenReturn(passingPreflight());
        ProcessController controller = new ProcessController(executionService, preflightService, flowDraftService,
                deploymentService, serverIdentity());

        controller.publishProcess("order.approve", "publish-request-conflict",
                new PublishProcessRequest("First request", 7L));

        assertThatThrownBy(() -> controller.publishProcess("order.approve", "publish-request-conflict",
                new PublishProcessRequest("Different request", 7L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.IDEMPOTENCY_CONFLICT));
        verify(deploymentService).publish(any(PublishProcessVersionCommand.class));
        verify(flowDraftService).getProcess("order.approve");
    }

    @Test
    void publishProcessRejectsAStaleDraftBeforePreflight() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        when(flowDraftService.getProcess("order.approve")).thenReturn(Optional.of(flow("order.approve")));
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        ProcessController controller = new ProcessController(executionService, preflightService, flowDraftService,
                deploymentService, serverIdentity());

        assertProblem(() -> controller.publishProcess("order.approve", "publish-request-stale",
                        new PublishProcessRequest(null, 6L)), HttpStatus.PRECONDITION_FAILED,
                "PROCESS_REVISION_CONFLICT", "Process revision mismatch: code=order.approve, expected=6, current=7");
        verify(preflightService, never()).preflight(any(String.class), any(ProcessModelType.class), any(String.class));
        verify(deploymentService, never()).publish(any(PublishProcessVersionCommand.class));
    }

    @Test
    void publishProcessCreatesNoVersionWhenStrictPreflightFails() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        when(flowDraftService.getProcess("order.approve")).thenReturn(Optional.of(flow("order.approve")));
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        when(preflightService.preflight(any(String.class), any(ProcessModelType.class), any(String.class)))
            .thenReturn(failingPreflight());
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        ProcessController controller = new ProcessController(executionService, preflightService, flowDraftService,
                deploymentService, serverIdentity());

        var failure = assertProblem(() -> controller.publishProcess("order.approve", "publish-request-invalid",
                        new PublishProcessRequest(null, 7L)), HttpStatus.UNPROCESSABLE_CONTENT,
                "PROCESS_PREFLIGHT_FAILED", "Process preflight failed; no version was published");
        assertThat(failure.getBody().getProperties())
            .containsEntry("preflight", PreflightReportResponse.from(failingPreflight()));
        verify(deploymentService, never()).publish(any(PublishProcessVersionCommand.class));
    }

    @Test
    void getVersionsReadsControlPlaneRecords() {
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        PublishedProcessVersion version = new PublishedProcessVersion(ProcessRef.version("default", "order.approve",
                        "v7"), ProcessModelType.TBBPM, DigestUtils.sha256Hex("<bpm/>"),
                Map.of(ReleaseMetadataKeys.CHANGELOG, "Ready for production"), "tester",
                Instant.ofEpochMilli(1_700_000_000_000L));
        when(deploymentService.listVersions(any(PublishedVersionQuery.class)))
            .thenReturn(new PublishedVersionPage(List.of(version), null));
        ProcessController controller = new ProcessController(mock(PublishedProcessExecutionService.class),
                mock(ProcessDefinitionPreflightService.class), mock(ProcessDraftService.class), deploymentService,
                serverIdentity());

        ResponseEntity<ProcessVersionListResponse> response =
                controller.getProcessVersions("order.approve", " v", null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessVersionResponse item = response.getBody().data().get(0);
        assertThat(item.version()).isEqualTo("v7");
        assertThat(item.changelog()).isEqualTo("Ready for production");
        assertThat(item.modelType()).isEqualTo(ProcessModelType.TBBPM);
        ArgumentCaptor<PublishedVersionQuery> query = ArgumentCaptor.forClass(PublishedVersionQuery.class);
        verify(deploymentService).listVersions(query.capture());
        assertThat(query.getValue().getNamespace()).isEqualTo("default");
        assertThat(query.getValue().getCode()).isEqualTo("order.approve");
        assertThat(query.getValue().getVersionPrefix()).isEqualTo("v");
        assertThat(query.getValue().getCursor()).isNull();
        assertThat(query.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    void getVersionsRejectsInvalidPaginationBeforeControlPlaneCall() {
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        ProcessController controller = new ProcessController(mock(PublishedProcessExecutionService.class),
                mock(ProcessDefinitionPreflightService.class), mock(ProcessDraftService.class), deploymentService,
                serverIdentity());

        assertProblem(() -> controller.getProcessVersions("order.approve", null, null, 0), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "limit must be between 1 and 100");
        verify(deploymentService, never()).listVersions(any(PublishedVersionQuery.class));
    }

    @Test
    void getVersionsMapsMalformedOpaqueCursorToBadRequest() {
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        when(deploymentService.listVersions(any(PublishedVersionQuery.class)))
            .thenThrow(new IllegalArgumentException("Invalid published-version cursor"));
        ProcessController controller = new ProcessController(mock(PublishedProcessExecutionService.class),
                mock(ProcessDefinitionPreflightService.class), mock(ProcessDraftService.class), deploymentService,
                serverIdentity());

        assertProblem(() -> controller.getProcessVersions("order.approve", null, "not-a-cursor", 20),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid published-version cursor");
    }

    @Test
    void duplicateProcessRejectsBlankNewNameBeforeStoreCall() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        DuplicateProcessRequest body = new DuplicateProcessRequest("order.copy", " ");

        assertProblem(() -> controller.duplicateProcess("order.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "newName is required");
        verify(flowDraftService, never()).duplicateProcess(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void updateProcessRequiresACompleteReplacementAndExpectedRevision() {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        UpdateProcessRequest body = new UpdateProcessRequest("Order Approval", null, null, List.of(), 7L);

        assertProblem(() -> controller.updateProcess("order.approve", body), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "xml is required");
        verify(flowDraftService, never()).updateProcess(any(String.class), any(ProcessDraftService.ProcessUpdate.class));
    }

    @Test
    void importProcessDetectsBpmnIdentityFromTheDocument() throws Exception {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        when(flowDraftService.createProcess(any(ProcessDraftService.ProcessCreate.class))).thenReturn(
                flow("order.imported"));
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        MockMultipartFile file = new MockMultipartFile("file", "ignored-name.xml", "application/xml",
                ("<?xml version=\"1.0\"?>" + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"order.imported\" name=\"Imported Order\"/>" + "</definitions>")
                    .getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<ProcessDraftService.ProcessCreate> command =
                ArgumentCaptor.forClass(ProcessDraftService.ProcessCreate.class);

        ResponseEntity<ProcessDefinitionResponse> response = controller.importProcess(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(flowDraftService).createProcess(command.capture());
        assertThat(command.getValue().code()).isEqualTo("order.imported");
        assertThat(command.getValue().name()).isEqualTo("Imported Order");
        assertThat(command.getValue().type()).isEqualTo(ProcessModelType.BPMN);
    }

    @Test
    void importProcessUsesCreateValidationBeforeStoreCall() throws Exception {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        MockMultipartFile file = new MockMultipartFile("file", "invalid.xml", "application/xml",
                "<bpm code=\"invalid code\" name=\"Imported\"/>".getBytes(StandardCharsets.UTF_8));

        assertProblem(() -> controller.importProcess(file), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "code must start with an ASCII letter or digit and contain only ASCII letters, digits, '.', '_', or '-'");
        verify(flowDraftService, never()).createProcess(any(ProcessDraftService.ProcessCreate.class));
    }

    @Test
    void importProcessAppliesTheSameIdentityNormalizationAsCreate() throws Exception {
        ProcessDraftService flowDraftService = mock(ProcessDraftService.class);
        when(flowDraftService.createProcess(any(ProcessDraftService.ProcessCreate.class))).thenReturn(
                flow("order.imported"));
        ProcessController controller = controller(mock(PublishedProcessExecutionService.class), flowDraftService);
        MockMultipartFile file = new MockMultipartFile("file", "normalized.xml", "application/xml",
                "<bpm code=\" order.imported \" name=\" Imported Order \"/>".getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<ProcessDraftService.ProcessCreate> command =
                ArgumentCaptor.forClass(ProcessDraftService.ProcessCreate.class);

        controller.importProcess(file);

        verify(flowDraftService).createProcess(command.capture());
        assertThat(command.getValue().code()).isEqualTo("order.imported");
        assertThat(command.getValue().name()).isEqualTo("Imported Order");
    }

    @Test
    void preflightRejectsMissingBodyBeforeServiceCall() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        ProcessController controller = controller(executionService, preflightService, mock(ProcessDraftService.class));

        assertProblem(() -> controller.preflightProcessDefinition(null), HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "request body is required");
        verify(preflightService, never()).preflight(any(String.class), any(ProcessModelType.class), any(String.class));
    }

    @Test
    void preflightMapsInvalidInlineDefinitionToBadRequest() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        ProcessController controller = controller(executionService, preflightService, mock(ProcessDraftService.class));
        when(preflightService.preflight("order.approve", ProcessModelType.BPMN, "<xml/>"))
            .thenThrow(
                    new ProcessDefinitionPreflightService.InvalidProcessDefinitionException("code contains unsupported "
                            + "characters", new IllegalArgumentException("invalid code")));

        assertProblem(() -> controller.preflightProcessDefinition(
                        new ProcessPreflightRequest("order.approve", ProcessModelType.BPMN, "<xml/>")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "code contains unsupported characters");
    }

    @Test
    void preflightMapsEngineReportToServerTransportContract() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessDefinitionPreflightService preflightService = mock(ProcessDefinitionPreflightService.class);
        ProcessController controller = controller(executionService, preflightService, mock(ProcessDraftService.class));
        ProcessPreflightReport report = passingPreflight();
        when(preflightService.preflight("order.approve", ProcessModelType.TBBPM, "<bpm/>")).thenReturn(report);

        ResponseEntity<PreflightReportResponse> response = controller.preflightProcessDefinition(
                new ProcessPreflightRequest("order.approve", ProcessModelType.TBBPM, "<bpm/>"));

        assertThat(response.getBody()).isEqualTo(PreflightReportResponse.from(report));
    }
}
