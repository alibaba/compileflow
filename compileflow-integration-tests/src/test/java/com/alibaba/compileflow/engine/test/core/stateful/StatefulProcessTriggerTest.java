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
package com.alibaba.compileflow.engine.test.core.stateful;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful Process Trigger Tests")
@Tag("integration")
@Tag("stateful")
@Execution(ExecutionMode.SAME_THREAD)
public class StatefulProcessTriggerTest {
    protected ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("B. Independent Trigger Entry Tests")
    class IndependentTriggerEntryTests {
        @Test
        @DisplayName("should execute each downstream segment from its named trigger entry")
        void shouldExecuteEachDownstreamSegmentFromItsNamedTriggerEntry() {
            String code = "bpm.stateful.deeplyNestedStatefulProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("mainProcessData", "main_data");
            context.put("subProcessLevel1Data", "level1_data");
            context.put("subProcessLevel2Data", "level2_data");
            // Each call is a new execution, so the caller supplies the state required downstream.
            Map<String, Object> level1Context = new HashMap<>(context);
            level1Context.put("level1_trigger_data", "level1_result");

            ProcessResult<Map<String, Object>> level1Result = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitLevel1Event", "level1EventComplete"), level1Context);

            assertThat(level1Result.isSuccess()).as("Level 1 trigger should succeed").isTrue();

            Map<String, Object> level2Context = new HashMap<>(context);
            level2Context.put("level2_trigger_data", "level2_result");

            ProcessResult<Map<String, Object>> level2Result = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitLevel2Event", "level2EventComplete"), level2Context);

            assertThat(level2Result.isSuccess()).as("Level 2 trigger should succeed").isTrue();

            Map<String, Object> level3Context = new HashMap<>(context);
            level3Context.put("level3_trigger_data", "level3_result");

            ProcessResult<Map<String, Object>> level3Result = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitLevel3Event", "level3EventComplete"), level3Context);

            assertThat(level3Result.isSuccess()).as("Level 3 trigger should succeed").isTrue();
            assertThat(level3Result.getOutput()).isNotNull();
            assertThat(level3Result.getOutput()).containsKey("finalNestedResult");
            assertThat(level3Result.getOutput().get("finalNestedResult")).isEqualTo(
                    "completed_combined_level2_data_level3_result");
        }

        @Test
        @DisplayName("should execute only the downstream path from a trigger entry")
        void shouldExecuteOnlyTheDownstreamPathFromATriggerEntry() {
            String code = "bpm.stateful.mixedStatefulStatelessProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("processData", "mixed_process_data");
            // Upstream work is not resumed; its required result is supplied by the caller.
            Map<String, Object> statefulContext = new HashMap<>(context);
            statefulContext.put("stateful_trigger_data", "stateful_result");
            statefulContext.put("statelessResult", "stateless_result");
            statefulContext.put("statefulResult", "stateful_result");

            ProcessResult<Map<String, Object>> statefulResult = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitStatefulEvent", "statefulEventComplete"), statefulContext);

            assertThat(statefulResult.isSuccess()).as("Stateful trigger should succeed").isTrue();
            assertThat(statefulResult.getOutput()).isNotNull();
            assertThat(statefulResult.getOutput()).containsKey("mixedResult");
            assertThat(statefulResult.getOutput().get("mixedResult"))
                .isEqualTo("completed_combined_stateless_result_stateful_result");
        }
    }

    @Nested
    @DisplayName("C. Complex Trigger Error Handling")
    class ComplexTriggerErrorHandlingTests {
        @Test
        @DisplayName("should handle invalid trigger scenarios gracefully")
        void rejectsUnknownStatefulNode() {
            // Given: Stateful process and an unknown stateful node id
            String code = "bpm.stateful.complexExclusiveGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("requestType", "loan_application");
            // When: Try to trigger with the unknown node id
            ProcessResult<Map<String, Object>> invalidTagResult = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ProcessTrigger.on("invalidTag", "someEvent"),
                    context);
            // Then: Verify failure and error information
            assertThat(invalidTagResult.isSuccess()).as("Unknown stateful node id should fail gracefully").isFalse();
            assertThat(invalidTagResult.getError().getMessage()).as("Error message should be present").isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should handle trigger on non-stateful process")
        void rejectsTriggerOnStatelessProcess() {
            // Given: Stateless process (not stateful)
            // Stateless process
            String code = "bpm.ktv.ktvExample";
            Map<String, Object> context = new HashMap<>();
            context.put("testData", "test_value");
            // When: Try to trigger on stateless process
            ProcessResult<Map<String, Object>> result = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ProcessTrigger.on("someTag", "someEvent"), context);
            // Then: Verify failure and error information
            assertThat(result.isSuccess()).as("Trigger on non-stateful process should fail").isFalse();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_008.getCode());
            assertThat(result.getError().getMessage()).as("Error message should be present").isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should reject parallel waits without durable token correlation")
        void shouldRejectParallelWaitsWithoutDurableTokenCorrelation() {
            String code = "bpm.stateful.complexParallelGateway";
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"),
                    Map.of("orderId", "order_1", "customerId", "customer_1"));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_VALIDATION_005.getCode());
        }
    }

    @Nested
    @DisplayName("E. BPMN Stateful Flow Tests")
    class BpmnStatefulFlowTests {
        @Test
        @DisplayName("should handle BPMN stateful receive task with complex trigger patterns")
        void completesSequentialBpmnReceiveTaskTriggers() {
            try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
                String code = "bpmn20.stateful.complex_stateful_receive_task";
                Map<String, Object> context = new HashMap<>();
                context.put("taskData", "complex_bpmn_task_data");
                // Multiple sequential triggers
                String[] events = {"initialApprovalMessage", "managerApprovalMessage", "finalApprovalMessage"};
                String[] nodeIds = {"receiveTask1", "receiveTask2", "receiveTask3"};

                ProcessResult<Map<String, Object>> finalResult = null;
                for (int i = 0; i < events.length; i++) {
                    Map<String, Object> triggerContext = new HashMap<>(context);
                    triggerContext.put("approval" + (i + 1) + "_result", "approved");

                    finalResult = engine.trigger(ProcessDefinition.classpath(ProcessModelType.BPMN, code,
                                    code.replace(".", "/") + ".bpmn"), ProcessTrigger.on(nodeIds[i], events[i]),
                            triggerContext);

                    assertThat(finalResult.isSuccess()).as("BPMN trigger %d should succeed", i + 1).isTrue();
                }

                assertThat(finalResult.getOutput()).isNotNull();
                assertThat(finalResult.getOutput()).containsKey("finalApprovalResult");
            }
        }

        @Test
        @DisplayName("should reject BPMN parallel waits without durable token correlation")
        void shouldRejectBpmnParallelWaitsWithoutDurableTokenCorrelation() {
            try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
                String code = "bpmn20.stateful.complex_stateful_parallel_gateway";
                ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                                code, code.replace(".", "/") + ".bpmn"),
                        Map.of("processData", "complex_parallel_process_data"));

                assertThat(result.isFailure()).isTrue();
                assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_VALIDATION_005.getCode());
            }
        }
    }
}
