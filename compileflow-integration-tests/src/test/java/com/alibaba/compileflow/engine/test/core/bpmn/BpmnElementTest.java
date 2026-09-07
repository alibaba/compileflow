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
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("BPMN Element and Gateway Tests")
@Tag("integration")
@Tag("bpmn")
public class BpmnElementTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BpmnElementTest.class);
    protected ProcessEngine engine;
    private ProcessRuntimeManager runtimeManager;
    private ProcessToolingService toolingService;

    private static ProcessDefinition.Classpath classpathDefinition(String code) {
        return ProcessDefinition.classpath(ProcessModelType.BPMN, code, code.replace('.', '/') + ".bpmn");
    }

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.create();
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
    @Tag("smoke")
    @DisplayName("should execute simple service task with arithmetic operation when inputs are provided")
    void shouldExecuteSimpleServiceTaskWhenInputsAreProvided() {
        // Given: Simple service task flow with input values
        String simpleServiceFlowCode = "bpmn20.compat.simple_service";
        Map<String, Object> calculationContext = new HashMap<>();
        calculationContext.put("a", 5);
        calculationContext.put("b", 3);
        // Verify code generation
        assertThat(toolingService.generateJavaCode(classpathDefinition(simpleServiceFlowCode)))
            .as("Code generation should succeed")
            .isNotNull();
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        simpleServiceFlowCode, simpleServiceFlowCode.replace(".", "/") + ".bpmn"), calculationContext);
        // Then: Verify execution success and result
        assertThat(executionResult.isSuccess()).as("Simple service task should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null")
            .isNotNull()
            .as("Service task result should be 5 + 3 = 8")
            .containsEntry("serviceTask1Result", 8);
    }

    @Test
    @DisplayName("should generate an invocation ID for DTO-friendly execution")
    void shouldGenerateInvocationIdForDtoFriendlyExecute() {
        // Given: Simple service task flow with DTO input
        String simpleServiceFlowCode = "bpmn20.compat.simple_service";
        SimpleInput input = new SimpleInput();
        input.a = 1;
        input.b = 2;
        // When: Execute the flow with DTO input/output
        ProcessResult<SimpleOutput> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        simpleServiceFlowCode, simpleServiceFlowCode.replace(".", "/") + ".bpmn"), input,
                SimpleOutput.class, ProcessExecutionOptions.defaults());
        // Then: Verify execution success, invocation attribution, and result
        assertThat(executionResult.isSuccess()).as("DTO execution should succeed").isTrue();
        assertThat(executionResult.getExecution().getInvocationId())
            .as("Invocation ID should be preserved for DTO execution")
            .isNotNull()
            .as("Invocation ID should not be empty")
            .isNotEmpty();
        assertThat(executionResult.getOutput()).as("DTO result should contain data").isNotNull();
        assertThat(executionResult.getOutput().serviceTask1Result)
            .as("Service task result should be 1 + 2 = 3")
            .isEqualTo(Integer.valueOf(3));
    }

    @Test
    @DisplayName("should distinguish post-execution output mapping failure")
    void shouldDistinguishPostExecutionOutputMappingFailure() {
        SimpleInput input = new SimpleInput();
        input.a = 1;
        input.b = 2;

        ProcessResult<Integer> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.simple_service", "bpmn20.compat.simple_service".replace(".", "/") + ".bpmn"),
                input, Integer.class, ProcessExecutionOptions.defaults());

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_009.getCode());
        assertThat(result.getError().getMessage())
            .contains("Process completed")
            .contains("side effects may already have occurred")
            .doesNotContain("MismatchedInputException");
        assertThat(result.getExecution().getInvocationId()).isNotBlank();
    }

    @Test
    @DisplayName("should execute synchronous subprocess when nested processing is required")
    void shouldExecuteSynchronousSubprocessWhenNestedProcessingIsRequired() {
        // Given: Synchronous subprocess flow with input values
        String synchronousSubprocessFlowCode = "bpmn20.compat.subprocess_sync";
        Map<String, Object> subprocessContext = new HashMap<>();
        subprocessContext.put("a", 11);
        subprocessContext.put("b", 22);
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        synchronousSubprocessFlowCode, synchronousSubprocessFlowCode.replace(".", "/") + ".bpmn"),
                subprocessContext);
        // Then: Verify execution success and subprocess result
        assertThat(executionResult.isSuccess()).as("Synchronous subprocess should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null")
            .isNotNull()
            .as("Subprocess result should be 11 + 22 = 33")
            .containsEntry("subServiceResult", 33);
    }

    @Test
    @DisplayName("should execute CallActivity when external process invocation is required")
    void shouldExecuteCallActivityWhenExternalProcessInvocationIsRequired() {
        // Given: CallActivity flow with input value
        String callActivityFlowCode = "bpmn20.compat.call_activity";
        Map<String, Object> callActivityContext = new HashMap<>();
        callActivityContext.put("inputValue", 5);
        // Verify code generation
        assertThat(toolingService.generateJavaCode(classpathDefinition(callActivityFlowCode)))
            .as("Code generation should succeed")
            .isNotNull();
        // Verify code generation contains CallActivity logic
        String generatedJavaCode = toolingService.generateJavaCode(classpathDefinition(callActivityFlowCode));
        assertThat(generatedJavaCode)
            .as("Generated Java code should not be null")
            .isNotNull()
            .as("Generated code should contain CallActivity logic")
            .contains("CallActivity");
        // When: Execute the flow
        ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        callActivityFlowCode, callActivityFlowCode.replace(".", "/") + ".bpmn"), callActivityContext);
        // Then: Verify execution success and result
        assertThat(executionResult.isSuccess()).as("CallActivity flow should execute successfully").isTrue();
        assertThat(executionResult.getOutput())
            .as("Result data should not be null and contain outputValue")
            .isNotNull()
            .containsKey("outputValue")
            .as("OutputValue should be 20 (5 + 5 = 10, then 10 × 2 = 20)")
            .containsEntry("outputValue", 20);
    }

    @Test
    @DisplayName("should convert CallActivity output to the parent variable type")
    void shouldConvertCallActivityOutputToParentType() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 7);
        context.put("b", 8);

        ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                        "bpmn20.compat.call_" + "activity_numeric_conversion",
                        "bpmn20.compat.call_activity_numeric_conversion".replace(".", "/") + ".bpmn"), context);

        assertThat(result.isSuccess())
            .as("CallActivity output conversion should succeed: %s",
                    result.getError() == null ? null : result.getError().getMessage())
            .isTrue();
        assertThat(result.getOutput()).containsEntry("result", 15L);
    }

    public static class SimpleInput {
        public int a;
        public int b;
    }

    public static class SimpleOutput {
        public Integer serviceTask1Result;
    }

    @Nested
    @DisplayName("Gateway and Branching Logic")
    // Gateway tests are prone to routing instability in parallel
    @Execution(ExecutionMode.SAME_THREAD)
    class // environments
    GatewayAndBranchingTests {
        @Test
        @DisplayName("should route through serviceTask1 when flag is true")
        void shouldRouteThroughServiceTask1WhenFlagIsTrue() {
            // Given: Exclusive gateway flow with flag=true
            String exclusiveGatewayFlowCode = "bpmn20.gateway.exclusive_gateway";
            Map<String, Object> gatewayContext = new HashMap<>();
            gatewayContext.put("flag", true);
            // When: Execute the flow
            ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            exclusiveGatewayFlowCode, exclusiveGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    gatewayContext);
            // Then: Verify execution success and semantic result
            assertThat(executionResult.isSuccess())
                .as("Exclusive gateway with true condition should execute successfully")
                .isTrue();
            assertThat(executionResult.getOutput())
                .as("True branch should publish the serviceTask1 result")
                .containsEntry("result", "task1");
        }

        @Test
        @DisplayName("should route through serviceTask2 when flag is false")
        void shouldRouteThroughServiceTask2WhenFlagIsFalse() {
            // Given: Exclusive gateway flow with flag=false
            String exclusiveGatewayFlowCode = "bpmn20.gateway.exclusive_gateway";
            Map<String, Object> gatewayContext = new HashMap<>();
            gatewayContext.put("flag", false);
            // When: Execute the flow
            ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            exclusiveGatewayFlowCode, exclusiveGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    gatewayContext);
            // Then: Verify execution success and semantic result
            assertThat(executionResult.isSuccess())
                .as("Exclusive gateway with false condition should execute successfully")
                .isTrue();
            assertThat(executionResult.getOutput())
                .as("False branch should publish the serviceTask2 result")
                .containsEntry("result", "task2");
        }

        @Test
        @DisplayName("should execute parallel gateway with concurrent arithmetic operations when inputs are provided")
        void shouldExecuteParallelGatewayWhenInputsAreProvided() {
            // Given: Parallel gateway flow with input values
            String parallelGatewayFlowCode = "bpmn20.gateway.parallel_gateway";
            Map<String, Object> parallelGatewayContext = new HashMap<>();
            parallelGatewayContext.put("a", 10);
            parallelGatewayContext.put("b", 200);
            // When: Execute the flow
            ProcessResult<Map<String, Object>> executionResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            parallelGatewayFlowCode, parallelGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    parallelGatewayContext);
            // Then: Verify execution success and results from both parallel branches
            assertThat(executionResult.isSuccess()).as("Parallel gateway should execute successfully").isTrue();
            assertThat(executionResult.getOutput())
                .as("Result data should contain both parallel branch results")
                .isNotNull()
                .containsEntry("serviceTask1Result", 210)
                .containsEntry("serviceTask2Result", 2000);
        }

        @Test
        @DisplayName("should handle InclusiveGateway with multiple condition branches when conditions vary")
        void shouldHandleInclusiveGatewayWhenConditionsVary() {
            // Given: Inclusive gateway flow code
            String inclusiveGatewayFlowCode = "bpmn20.gateway.inclusive_gateway";
            // Test case 1: Both conditions true - should execute both branches
            Map<String, Object> bothConditionsTrueContext = new HashMap<>();
            bothConditionsTrueContext.put("condition1", true);
            bothConditionsTrueContext.put("condition2", true);
            bothConditionsTrueContext.put("branch1", "Branch 1");
            bothConditionsTrueContext.put("branch2", "Branch 2");
            bothConditionsTrueContext.put("branch3", "Default Branch");

            ProcessResult<Map<String, Object>> bothConditionsResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            inclusiveGatewayFlowCode, inclusiveGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    bothConditionsTrueContext);
            assertThat(bothConditionsResult.isSuccess()).as("InclusiveGateway with both conditions should succeed").isTrue();
            assertThat(bothConditionsResult.getOutput())
                .containsEntry("result1", "processed_Branch 1")
                .containsEntry("result2", "processed_Branch 2");
            // Test case 2: Only condition1 true - should execute branch1
            Map<String, Object> condition1OnlyContext = new HashMap<>();
            condition1OnlyContext.put("condition1", true);
            condition1OnlyContext.put("condition2", false);
            condition1OnlyContext.put("branch1", "Branch 1");
            condition1OnlyContext.put("branch2", "Branch 2");
            condition1OnlyContext.put("branch3", "Default Branch");

            ProcessResult<Map<String, Object>> condition1OnlyResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            inclusiveGatewayFlowCode, inclusiveGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    condition1OnlyContext);
            assertThat(condition1OnlyResult.isSuccess()).as("InclusiveGateway with condition1 only should succeed").isTrue();
            assertThat(condition1OnlyResult.getOutput()).containsEntry("result1", "processed_Branch 1");
            // Test case 3: No conditions true - should execute default branch
            Map<String, Object> defaultBranchContext = new HashMap<>();
            defaultBranchContext.put("condition1", false);
            defaultBranchContext.put("condition2", false);
            defaultBranchContext.put("branch1", "Branch 1");
            defaultBranchContext.put("branch2", "Branch 2");
            defaultBranchContext.put("branch3", "Default Branch");

            ProcessResult<Map<String, Object>> defaultBranchResult = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            inclusiveGatewayFlowCode, inclusiveGatewayFlowCode.replace(".", "/") + ".bpmn"),
                    defaultBranchContext);
            assertThat(defaultBranchResult.isSuccess()).as("InclusiveGateway with default branch should succeed").isTrue();
            assertThat(defaultBranchResult.getOutput()).containsEntry("result3", "processed_Default Branch");
            // Verify code generation
            String generatedJavaCode = toolingService.generateJavaCode(classpathDefinition(inclusiveGatewayFlowCode));
            assertThat(generatedJavaCode).as("Generated Java code should not be null").isNotNull();
        }

        @Test
        @DisplayName("should treat missing nullable Boolean conditions as false")
        void shouldTreatMissingNullableBooleanConditionsAsFalse() {
            Map<String, Object> context =
                    Map.of("branch1", "Branch 1", "branch2", "Branch 2", "branch3", "Default Branch");

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            "bpmn20.gateway." + "inclusive_gateway",
                            "bpmn20.gateway.inclusive_gateway".replace(".", "/") + ".bpmn"), context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result3", "processed_Default Branch");
        }
    }
}
