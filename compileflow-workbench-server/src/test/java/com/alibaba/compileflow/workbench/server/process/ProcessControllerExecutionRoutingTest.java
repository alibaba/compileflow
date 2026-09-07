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

import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.workbench.server.execution.ExecutionRoutingRequest;
import com.alibaba.compileflow.workbench.server.execution.ProcessExecutionRequest;
import com.alibaba.compileflow.workbench.server.execution.ProcessExecutionResponse;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import com.alibaba.compileflow.workbench.server.execution.PublishedProcessExecutionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProcessControllerExecutionRoutingTest {
    private static ProcessController controller(PublishedProcessExecutionService executionService) {
        return new ProcessController(executionService, mock(ProcessDefinitionPreflightService.class),
                mock(ProcessDraftService.class), mock(ProcessDeploymentService.class), mock(ServerIdentity.class));
    }

    private static ExecutionRoutingRequest aliasRouting(String alias) {
        return new ExecutionRoutingRequest(null, alias, null, Map.of());
    }

    @Test
    void executePassesRoutingOptionsToPublishedProcessExecutionService() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        ProcessExecutionResponse serviceResponse = mock(ProcessExecutionResponse.class);
        when(executionService.execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(serviceResponse);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("amount", 1200);
        ProcessExecutionRequest body = new ProcessExecutionRequest("inv-client-1", params,
                new ExecutionRoutingRequest(null, "production", "user-42", Map.of()));

        ResponseEntity<ProcessExecutionResponse> response = controller.executePublishedProcess("payment.approve", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<ProcessRef> refCaptor = ArgumentCaptor.forClass(ProcessRef.class);
        ArgumentCaptor<ProcessExecutionOptions> optionsCaptor = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(executionService)
            .execute(refCaptor.capture(),
                    argThat(capturedParams -> Integer.valueOf(1200).equals(capturedParams.get("amount"))),
                    optionsCaptor.capture());
        ProcessExecutionOptions options = optionsCaptor.getValue();
        assertThat(refCaptor.getValue()).isEqualTo(ProcessRef.alias("payment.approve", "production"));
        assertThat(options.getInvocationId()).isEqualTo("inv-client-1");
        assertThat(options.getAliasRouting().routingKey()).isEqualTo("user-42");
    }

    @Test
    void executePreservesOpaqueRoutingKeyExactly() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        when(executionService.execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(mock(ProcessExecutionResponse.class));
        ProcessExecutionRequest body = new ProcessExecutionRequest(null, null,
                new ExecutionRoutingRequest(null, "production", "  opaque-user-key  ", Map.of()));

        controller.executePublishedProcess("payment.approve", body);

        ArgumentCaptor<ProcessExecutionOptions> options = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(executionService).execute(any(ProcessRef.class), anyMap(), options.capture());
        assertThat(options.getValue().getAliasRouting().routingKey()).isEqualTo("  opaque-user-key  ");
    }

    @Test
    void executeRejectsAliasRoutingOptionsForExactVersion() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        ProcessExecutionRequest body =
                new ProcessExecutionRequest(null, null, new ExecutionRoutingRequest("v2", null, "user-42", Map.of()));

        assertProblem(() -> controller.executePublishedProcess("payment.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "routingKey and attributes require an alias route");
        verify(executionService, never()).execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void executeRejectsMissingManagedReference() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);

        assertProblem(() -> controller.executePublishedProcess("payment.approve", ProcessExecutionRequest.empty()),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "routing must contain exactly one of version or alias");
        verify(executionService, never()).execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void executeRejectsVersionAndAliasTogether() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        ProcessExecutionRequest body =
                new ProcessExecutionRequest(null, null, new ExecutionRoutingRequest("v2", "production", null, Map.of()));

        assertProblem(() -> controller.executePublishedProcess("payment.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "routing must contain exactly one of version or alias");
        verify(executionService, never()).execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class));
    }

    @Test
    void missingPublishedVersionPreservesDeploymentFailureForTheSharedHandler() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        var failure = DeploymentException.of(DeploymentErrorCode.VERSION_NOT_FOUND, "missing version");
        when(executionService.execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class))).thenThrow(
                failure);
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> controller.executePublishedProcess("payment.approve",
                    new ProcessExecutionRequest(null, null, new ExecutionRoutingRequest("v404", null, null, Map.of()))))
            .isSameAs(failure);
    }

    @Test
    void executeMapsRequestConstructionFailuresToBadRequest() {
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        assertProblem(() -> controller.executePublishedProcess("invalid code",
                        new ProcessExecutionRequest(null, null, aliasRouting("production"))), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "code must start with an ASCII letter or digit and contain only ASCII letters, digits, '.', '_', or '-'");
    }

    @Test
    void executeDoesNotExposeUnexpectedExceptionDetails() {
        String sensitiveMessage = "database-password=secret";
        PublishedProcessExecutionService executionService = mock(PublishedProcessExecutionService.class);
        ProcessController controller = controller(executionService);
        when(executionService.execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenThrow(new IllegalArgumentException(sensitiveMessage));
        Logger logger = (Logger) LoggerFactory.getLogger(ProcessController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertProblem(() -> controller.executePublishedProcess("payment.approve",
                            new ProcessExecutionRequest(null, null, aliasRouting("production"))),
                    HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal execution error");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).doesNotContain(sensitiveMessage);
        assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
            .contains(IllegalArgumentException.class.getName())
            .doesNotContain(sensitiveMessage);
    }
}
