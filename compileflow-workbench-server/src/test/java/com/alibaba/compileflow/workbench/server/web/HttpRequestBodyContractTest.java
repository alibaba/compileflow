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
package com.alibaba.compileflow.workbench.server.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxAdminService;
import com.alibaba.compileflow.workbench.server.config.ServerJacksonConfiguration;
import com.alibaba.compileflow.workbench.server.deployment.CanaryHealthService;
import com.alibaba.compileflow.workbench.server.deployment.DeploymentControlController;
import com.alibaba.compileflow.workbench.server.deployment.DeploymentController;
import com.alibaba.compileflow.workbench.server.deployment.DeploymentService;
import com.alibaba.compileflow.workbench.server.execution.AsyncInvocationController;
import com.alibaba.compileflow.workbench.server.execution.AsyncInvocationService;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogController;
import com.alibaba.compileflow.workbench.server.monitoring.ExecutionLogService;
import com.alibaba.compileflow.workbench.server.process.ProcessController;
import com.alibaba.compileflow.workbench.server.process.ProcessDraftService;
import com.alibaba.compileflow.workbench.server.process.ProcessDefinitionPreflightService;
import com.alibaba.compileflow.workbench.server.execution.PublishedProcessExecutionService;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class HttpRequestBodyContractTest {
    private AsyncInvocationService asyncInvocationService;
    private CanaryHealthService canaryHealthService;
    private RoutingOutboxAdminService routingOutboxAdminService;
    private DeploymentService deploymentService;
    private ExecutionLogService executionLogService;
    private PublishedProcessExecutionService executionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        asyncInvocationService = mock(AsyncInvocationService.class);
        canaryHealthService = mock(CanaryHealthService.class);
        routingOutboxAdminService = mock(RoutingOutboxAdminService.class);
        deploymentService = mock(DeploymentService.class);
        executionLogService = mock(ExecutionLogService.class);
        executionService = mock(PublishedProcessExecutionService.class);
        JsonMapper.Builder jsonMapperBuilder = JsonMapper.builder();
        new ServerJacksonConfiguration().strictJsonMapperBuilderCustomizer().customize(jsonMapperBuilder);
        jsonMapperBuilder.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        JsonMapper jsonMapper = jsonMapperBuilder.build();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AsyncInvocationController(asyncInvocationService),
                    new DeploymentControlController(routingOutboxAdminService),
                    new DeploymentController(deploymentService, canaryHealthService),
                    new ProcessController(executionService, mock(ProcessDefinitionPreflightService.class),
                            mock(ProcessDraftService.class), mock(ProcessDeploymentService.class),
                            mock(ServerIdentity.class)), new ExecutionLogController(executionLogService))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
            .build();
    }

    @Test
    void rejectsUnknownTopLevelField() throws Exception {
        assertMalformed("/api/processes/payment.approve/execute",
                """
            {
              "routing": {"alias": "production"},
              "unexpected": true
            }
            """);
    }

    @Test
    void rejectsUnknownPreflightModelType() throws Exception {
        assertMalformed("/api/processes/preflight",
                """
            {
              "code": "payment.approve",
              "modelType": "UNKNOWN",
              "xml": "<bpm/>"
            }
            """);
    }

    @Test
    void distinguishesSemanticFieldValidationFromMalformedJson() throws Exception {
        mockMvc
            .perform(post("/api/processes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                    {
                      "code": "invalid code",
                      "name": "Invalid",
                      "type": "BPMN"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:compileflow:problem:invalid-request"))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail")
                .value(
                        "code must start with an ASCII letter or digit and contain only " + "ASCII letters, digits, '.', '_', or '-'"));
        verifyNoServiceInteractions();
    }

    @Test
    void classifiesMissingRequiredFieldAsSemanticValidation() throws Exception {
        mockMvc
            .perform(post("/api/processes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Missing code",
                      "type": "BPMN"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail").value("code is required"));
        verifyNoServiceInteractions();
    }

    @Test
    void distinguishesInvalidInvocationIdFromJsonTypeFailure() throws Exception {
        mockMvc
            .perform(post("/api/processes/payment.approve/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                    {
                      "invocationId": "invalid id",
                      "routing": {"alias": "production"}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail")
                .value(
                        "invocationId must start with an ASCII letter or digit and contain only "
                        + "ASCII letters, digits, '.', '_', ':', '@', or '-'"));
        verifyNoServiceInteractions();
    }

    @Test
    void rejectsUnknownRoutingField() throws Exception {
        assertMalformed("/api/processes/payment.approve/async-invocations",
                """
            {
              "routing": {
                "alias": "production",
                "region": "cn-hangzhou"
              }
            }
            """);
    }

    @Test
    void rejectsNonObjectProcessParameters() throws Exception {
        assertMalformed("/api/processes/payment.approve/async-invocations",
                """
            {
              "params": "not-an-object",
              "routing": {"alias": "production"}
            }
            """);
    }

    @Test
    void rejectsScalarCoercionForFixedFields() throws Exception {
        assertMalformed("/api/processes/payment.approve/async-invocations",
                """
            {
              "invocationId": 42,
              "maxAttempts": "2",
              "routing": {"alias": "production"}
            }
            """);
    }

    @Test
    void rejectsFractionalIntegerField() throws Exception {
        assertMalformed("/api/processes/payment.approve/async-invocations",
                """
            {
              "maxAttempts": 1.5,
              "routing": {"alias": "production"}
            }
            """);
    }

    @Test
    void rejectsFractionalCanaryWeightBps() throws Exception {
        mockMvc
            .perform(put("/api/deployments/deploy-1/canary")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "percentage": 12.5,
                      "expectedRevision": 2
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
            .andExpect(jsonPath("$.detail").value("The request body is invalid or malformed"));
        verifyNoServiceInteractions();
    }

    @Test
    void rejectsNonStringProcessTag() throws Exception {
        assertMalformed("/api/processes",
                """
            {
              "code": "order.approve",
              "name": "Order Approval",
              "type": "BPMN",
              "tags": ["order", 42]
            }
            """);
    }

    @Test
    void rejectsNonStringProcessMetadata() throws Exception {
        mockMvc
            .perform(put("/api/processes/order.approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": 42
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
            .andExpect(jsonPath("$.detail").value("The request body is invalid or malformed"));
        verifyNoServiceInteractions();
    }

    @Test
    void rejectsNonStringLogExportFilter() throws Exception {
        assertMalformed("/api/execution-logs/export", """
            {
              "processCode": 42
            }
            """);
    }

    @Test
    void rejectsPaginationFieldsOnLogExport() throws Exception {
        assertMalformed("/api/execution-logs/export", """
            {
              "page": 1
            }
            """);
    }

    @Test
    void rejectsNonStringLogPurgeCutoff() throws Exception {
        assertMalformed("/api/execution-logs/purge", """
            {
              "before": 42
            }
            """);
    }

    private void assertMalformed(String path, String body) throws Exception {
        mockMvc
            .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:compileflow:problem:malformed-request-body"))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
            .andExpect(jsonPath("$.detail").value("The request body is invalid or malformed"));
        verifyNoServiceInteractions();
    }

    private void verifyNoServiceInteractions() {
        verifyNoInteractions(asyncInvocationService, canaryHealthService, routingOutboxAdminService, deploymentService,
                executionLogService, executionService);
    }
}
