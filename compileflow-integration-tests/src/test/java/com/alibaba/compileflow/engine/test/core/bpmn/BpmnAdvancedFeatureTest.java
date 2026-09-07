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
package com.alibaba.compileflow.engine.test.core.bpmn;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.KtvService;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("BPMN Advanced Feature Tests")
@Tag("integration")
@Tag("bpmn")
public class BpmnAdvancedFeatureTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BpmnAdvancedFeatureTest.class);
    protected ProcessEngine engine;
    private ProcessRuntimeManager runtimeManager;
    private ProcessToolingService toolingService;
    @Autowired
    private KtvService ktvService;

    private static ProcessDefinition.Classpath classpathDefinition(String code) {
        return ProcessDefinition.classpath(ProcessModelType.BPMN, code, code.replace('.', '/') + ".bpmn");
    }

    @BeforeEach
    void setUp() {
        engine = ProcessEngineFactory.create(ProcessEngineTestFactory
            .builder()
            .componentResolver(componentResolver("ktvService", ktvService))
            .build());
        runtimeManager = engine.runtime();
        toolingService = engine.tooling();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should execute a BPMN flow with strongly-typed DTOs when typed input is provided")
    void shouldExecuteBpmnFlowWithTypedDtosWhenTypedInputIsProvided() {
        // Given: BPMN KTV example flow with typed DTO input
        String bpmnKtvFlowCode = "bpmn20.ktv.ktvExample";
        KtvRequest bpmnKtvRequest = new KtvRequest();
        bpmnKtvRequest.pList = Lists.newArrayList("user1", "user2");
        // When: Execute the flow with typed DTOs
        ProcessResult<KtvResponse> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        bpmnKtvFlowCode, bpmnKtvFlowCode.replace(".", "/") + ".bpmn"), bpmnKtvRequest, KtvResponse.class,
                ProcessExecutionOptions.defaults());
        // Then: Verify execution success and typed response
        assertThat(executionResult.isSuccess()).as("Type-safe execution should succeed").isTrue();
        assertThat(executionResult.getOutput()).as("Typed response data should not be null").isNotNull();
        assertThat(executionResult.getOutput().price).as("Price should be calculated and positive").isGreaterThan(0);
    }

    @Test
    @DisplayName("should hot-reload BPMN ktv example successfully when new content is provided")
    void shouldHotReloadBpmnKtvExampleWhenNewContentIsProvided() throws IOException {
        // Given: BPMN KTV example flow code
        String bpmnKtvFlowCode = "bpmn20.ktv.ktvExample";
        // V1: Use the original ktvExample from the resource file
        String v1JavaCode = toolingService.generateJavaCode(classpathDefinition(bpmnKtvFlowCode));
        assertThat(v1JavaCode).as("V1 Java code generation should succeed").isNotNull();
        // Define context parameters.
        Map<String, Object> ktvContext = new HashMap<>();
        ktvContext.put("pList", Lists.newArrayList("u1", "u2"));
        // When: Execute V1 (without explicit deploy, execute will compile and deploy automatically)
        ProcessResult<Map<String, Object>> v1Result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        bpmnKtvFlowCode, bpmnKtvFlowCode.replace(".", "/") + ".bpmn"), ktvContext);
        // Then: Verify V1 execution and price
        assertThat(v1Result.isSuccess()).as("V1 execution should succeed").isTrue();
        assertThat(v1Result.getOutput().get("price")).as("V1 price should be 2 people × 27 = 54").isEqualTo(54);
        // When: Hot-reload to V2 (replace calculatePrice with calculatePriceForHotDeploy)
        String bpmnResourcePath = bpmnKtvFlowCode.replace('.', '/') + ".bpmn";
        String v1XmlContent = Resources.toString(Resources.getResource(bpmnResourcePath), StandardCharsets.UTF_8);

        String v2XmlContent = v1XmlContent.replace("calculatePrice", "calculatePriceForHotDeploy");
        // Warm V2 and execute the exact definition.
        runtimeManager.warmUp(ProcessDefinition.inline(ProcessModelType.BPMN, bpmnKtvFlowCode, v2XmlContent));
        ProcessResult<Map<String, Object>> v2Result =
                engine.execute(ProcessDefinition.inline(ProcessModelType.BPMN, bpmnKtvFlowCode, v2XmlContent),
                        ktvContext);
        // Then: Verify V2 execution and price
        assertThat(v2Result.isSuccess()).as("V2 execution should succeed").isTrue();
        assertThat(v2Result.getOutput().get("price")).as("V2 price should be 2 people × 900 = 1800").isEqualTo(1800);
        // Verify that the generated code has also been updated
        String v2JavaCode = toolingService.generateJavaCode(ProcessDefinition.inline(ProcessModelType.BPMN,
                bpmnKtvFlowCode, v2XmlContent));
        assertThat(v2JavaCode)
            .as("Generated code should contain calculatePriceForHotDeploy")
            .contains("calculatePriceForHotDeploy");
    }

    @Test
    @DisplayName("should execute service task with timeout InvocationPolicy successfully when operation "
            + "completes within timeout")
    void shouldExecuteServiceTaskWithTimeoutInvocationPolicySuccessfullyWhenOperationCompletesWithinTimeout() {
        // Given: Service task flow with timeout InvocationPolicy
        String flowCode = "bpmn20.compat.service_task_with_invocation_policy_timeout";
        Map<String, Object> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 5);
        // Verify code generation
        String generatedSource = toolingService.generateJavaCode(classpathDefinition(flowCode));
        assertThat(generatedSource).as("Code generation with timeout InvocationPolicy should succeed").isNotNull();
        assertThat(generatedSource.lines().mapToInt(String::length).max().orElseThrow())
            .as("Generated source should remain readable")
            .isLessThanOrEqualTo(120);
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        flowCode, flowCode.replace(".", "/") + ".bpmn"), context);
        // Then: Verify execution success and result
        assertThat(executionResult.isSuccess())
            .as("Service task with timeout InvocationPolicy should execute successfully, error=%s",
                    executionResult.getError())
            .isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null")
            .isNotNull()
            .as("Service task result should be 10 × 5 = 50")
            .containsEntry("result", 50);
    }

    @Test
    @DisplayName("should execute service task with retry InvocationPolicy successfully when operation succeeds "
            + "on first attempt")
    void shouldExecuteServiceTaskWithRetryInvocationPolicySuccessfullyWhenOperationSucceedsOnFirstAttempt() {
        // Given: Service task flow with retry InvocationPolicy
        String flowCode = "bpmn20.compat.service_task_with_invocation_policy_retry";
        Map<String, Object> context = new HashMap<>();
        context.put("a", 5);
        context.put("b", 8);
        // Verify code generation
        assertThat(toolingService.generateJavaCode(classpathDefinition(flowCode)))
            .as("Code generation with retry InvocationPolicy should succeed")
            .isNotNull();
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        flowCode, flowCode.replace(".", "/") + ".bpmn"), context);
        // Then: Verify execution success and result
        assertThat(executionResult.isSuccess())
            .as("Service task with retry InvocationPolicy should execute successfully, error=%s",
                    executionResult.getError())
            .isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null")
            .isNotNull()
            .as("Service task result should be add(5, 8) = 13")
            .containsEntry("result", 13);
    }

    @Test
    @DisplayName("should execute service task with full InvocationPolicy successfully when all attributes "
            + "are configured")
    void shouldExecuteServiceTaskWithComprehensiveInvocationPolicySuccessfullyWhenAllAttributesAreConfigured() {
        // Given: Service task flow with full InvocationPolicy
        String flowCode = "bpmn20.compat.service_task_with_invocation_policy";
        Map<String, Object> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 20);
        // Verify code generation
        assertThat(toolingService.generateJavaCode(classpathDefinition(flowCode)))
            .as("Code generation with full InvocationPolicy should succeed")
            .isNotNull();
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        flowCode, flowCode.replace(".", "/") + ".bpmn"), context);
        // Then: Verify execution success and result
        assertThat(executionResult.isSuccess())
            .as("Service task with full InvocationPolicy should execute successfully, error=%s",
                    executionResult.getError())
            .isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null")
            .isNotNull()
            .as("Service task result should be 10 + 20 = 30")
            .containsEntry("result", 30);
    }

    public static class KtvRequest {
        public List<String> pList;
    }

    public static class KtvResponse {
        public int price;
    }

    private static ProcessComponentResolver componentResolver(String componentName, Object component) {
        return new ProcessComponentResolver() {
            @Override
            public <T> T resolve(String name, Class<T> requiredType) {
                if (!componentName.equals(name) || !requiredType.isInstance(component)) {
                    throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                            "Process component is not available: " + name);
                }
                return requiredType.cast(component);
            }
        };
    }
}
