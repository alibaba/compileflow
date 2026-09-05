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

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessContextBuilder;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful Process Engine Comprehensive Integration Tests")
// Stateful process trigger mechanism is prone to race conditions in
@Execution(ExecutionMode.SAME_THREAD)
class // parallel environments
StatefulProcessEngineTest {
    protected ProcessEngine engine;
    private ProcessToolingService toolingService;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createTbbpm();
        toolingService = engine.tooling();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("A. Basic Waiting Node Tests")
    class BasicWaitingNodeTests {
        @Test
        @DisplayName("should handle waitTask node with trigger when external task completes")
        void shouldHandleWaitTaskNodeWhenTriggered() {
            // Given: Stateful process with waitTask node and task data
            String waitTaskProcessCode = "bpm.stateful.waitTaskProcess";
            Map<String, Object> taskContext =
                    ProcessContextBuilder.newContext().with("taskData", "test_task_data").build();
            // Verify code generation
            assertThat(toolingService.generateJavaCode(ProcessDefinition.classpath(waitTaskProcessCode,
                    "bpm/stateful/waitTaskProcess.bpm")))
                .as("Code generation should succeed")
                .contains("private int _cf$awaitWaitTask1()")
                .contains("this._cf$triggerPending")
                .contains("return _CF_PAUSED;");
            // When: Execute initial flow and trigger the waiting task
            engine.execute(ProcessDefinition.classpath(waitTaskProcessCode,
                            waitTaskProcessCode.replace(".", "/") + ".bpm"), taskContext);
            taskContext.put("waitResult", "external_result");
            ProcessResult<Map<String, Object>> triggerResult = engine.trigger(ProcessDefinition.classpath(waitTaskProcessCode,
                            waitTaskProcessCode.replace(".", "/") + ".bpm"), ProcessTrigger.at("waitTask1"), taskContext);
            // Then: Verify execution success and semantic output (waitResult)
            assertThat(triggerResult.isSuccess()).as("WaitTask flow should execute successfully").isTrue();
            assertThat(triggerResult.getOutput())
                .as("Result data should contain waitResult")
                .containsEntry("waitResult", "completed_external_result");
        }

        @Test
        @DisplayName("should generate actionless wait entries without empty branches")
        void shouldGenerateActionlessWaitEntriesWithoutEmptyBranches() {
            String source = toolingService.generateJavaCode(ProcessDefinition.classpath("bpm.stateful."
                    + "nestedStatefulSubProcess", "bpm/stateful/nestedStatefulSubProcess.bpm"));

            assertThat(source)
                .contains("_cf$awaitAfterSubProcess1", "_cf$awaitAfterSubProcess2", "_cf$awaitMainWait")
                .contains("this._cf$triggerPending")
                .contains("return _CF_PAUSED;")
                .doesNotContain("while (current")
                .doesNotContain("if (_cf$triggering) {\n" + "                    } else {");
        }

        @Test
        @DisplayName("should handle multiple waitEventTask nodes in sequence when events are triggered")
        void shouldHandleMultipleWaitEventTasksWhenEventsAreTriggered() {
            // Given: Stateful process with multiple waitEventTask nodes
            String multiWaitEventFlowCode = "bpm.stateful.multiWaitEventFlow";
            Map<String, Object> orderContext = new HashMap<>();
            orderContext.put("orderData", "order_123");
            // When: Trigger payment complete event
            Map<String, Object> paymentContext = new HashMap<>(orderContext);
            paymentContext.put("payment_received", "payment_success");
            // trigger starts a new execution at the named entry with caller-supplied variables.
            ProcessResult<Map<String, Object>> paymentResult = engine.trigger(ProcessDefinition.classpath(multiWaitEventFlowCode,
                            multiWaitEventFlowCode.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitPayment", "paymentComplete"), paymentContext);
            // Then: Verify payment trigger succeeds and semantic output
            assertThat(paymentResult.isSuccess()).as("Payment trigger should succeed").isTrue();
            assertThat(paymentResult.getOutput())
                .as("Payment trigger should return a data map")
                .isNotNull()
                .containsKey("paymentResult");
            // NOTE: Stateful process intermediate trigger return var may not be populated in this
            // trigger's return (allowed to be null).
            // When: Trigger delivery complete event
            Map<String, Object> deliveryContext = new HashMap<>(orderContext);
            deliveryContext.put("delivery_completed", "delivery_success");
            ProcessResult<Map<String, Object>> deliveryResult = engine.trigger(ProcessDefinition.classpath(multiWaitEventFlowCode,
                            multiWaitEventFlowCode.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitDelivery", "deliveryComplete"), deliveryContext);
            // Then: Verify delivery trigger succeeds and semantic outputs
            assertThat(deliveryResult.isSuccess()).as("Delivery trigger should succeed").isTrue();
            assertThat(deliveryResult.getOutput())
                .as("Delivery trigger should return a data map")
                .isNotNull()
                .containsKey("deliveryResult")
                .containsKey("finalResult");
            // A trigger result contains only values produced by this new invocation.
        }
    }

    @Nested
    @DisplayName("C. Nested Stateful SubProcess Tests")
    class NestedStatefulSubProcessTests {
        @Test
        @DisplayName("should handle nested stateful subprocess when multiple triggers are received")
        void shouldHandleNestedStatefulSubProcessWhenMultipleTriggersAreReceived() {
            // Given: Nested stateful subprocess with main data
            String nestedStatefulSubProcessCode = "bpm.stateful.nestedStatefulSubProcess";
            Map<String, Object> subprocessContext = new HashMap<>();
            subprocessContext.put("mainData", "main_process_data");
            // When: Trigger main event
            ProcessResult<Map<String, Object>> mainTriggerResult = engine.trigger(ProcessDefinition.classpath(nestedStatefulSubProcessCode,
                            nestedStatefulSubProcessCode.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("mainWait", "mainEvent"), subprocessContext);
            // Then: Verify main event trigger succeeds
            assertThat(mainTriggerResult.isSuccess()).as("Main event trigger should succeed").isTrue();
        }
    }

    @Nested
    @DisplayName("D. BPMN Stateful Flow Tests")
    class BpmnStatefulFlowTests {
        @Test
        @DisplayName("should handle BPMN stateful receive task flow when approval message is received")
        void shouldHandleBpmnStatefulReceiveTaskWhenApprovalMessageIsReceived() {
            // Given: BPMN engine and stateful receive task flow
            try (ProcessEngine bpmnEngine = ProcessEngineTestFactory.createBpmn()) {
                String bpmnStatefulReceiveTaskCode = "bpmn20.stateful.stateful_receive_task";
                Map<String, Object> receiveTaskContext = new HashMap<>();
                receiveTaskContext.put("taskData", "user_task_data");
                // Verify code generation
                ProcessToolingService bpmnTooling = bpmnEngine.tooling();
                assertThat(bpmnTooling.generateJavaCode(ProcessDefinition.classpath(bpmnStatefulReceiveTaskCode,
                        "bpmn20/stateful/stateful_receive_task.bpmn")))
                    .as("Code generation should succeed")
                    .isNotNull();
                // When: Trigger final approval
                Map<String, Object> approvalContext = new HashMap<>(receiveTaskContext);
                approvalContext.put("final_approval", "approved");
                ProcessResult<Map<String, Object>> modelIdTrigger = bpmnEngine.trigger(ProcessDefinition.classpath(bpmnStatefulReceiveTaskCode,
                                bpmnStatefulReceiveTaskCode.replace(".", "/") + ".bpmn"),
                        ProcessTrigger.on("receiveTask1", "approvalMessage"), approvalContext);
                assertThat(modelIdTrigger.isFailure()).as("BPMN message id is model identity, not the trigger event").isTrue();

                ProcessResult<Map<String, Object>> triggerResult = bpmnEngine.trigger(ProcessDefinition.classpath(bpmnStatefulReceiveTaskCode,
                                bpmnStatefulReceiveTaskCode.replace(".", "/") + ".bpmn"),
                        ProcessTrigger.on("receiveTask1", "approval.received"), approvalContext);
                // Then: Verify trigger succeeds
                assertThat(triggerResult.isSuccess()).as("BPMN stateful receive task should succeed").isTrue();
                assertThat(triggerResult.getOutput()).as("Result data should not be null").isNotNull();
            }
        }

        @Test
        @DisplayName("should reject BPMN stateful parallel waits without token correlation")
        void shouldRejectBpmnStatefulParallelWaitsWithoutTokenCorrelation() {
            try (ProcessEngine bpmnStatefulEngine = ProcessEngineTestFactory.createBpmn()) {
                String statefulParallelGatewayFlowCode = "bpmn20.stateful.stateful_parallel_gateway";
                ProcessResult<Map<String, Object>> result = bpmnStatefulEngine.execute(ProcessDefinition.classpath(statefulParallelGatewayFlowCode,
                                statefulParallelGatewayFlowCode.replace(".", "/") + ".bpmn"),
                        Map.of("processData", "parallel_process_data"));

                assertThat(result.isFailure()).isTrue();
                assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_VALIDATION_005.getCode());
            }
        }
    }

    @Nested
    @DisplayName("E. Error Handling and Edge Cases")
    class ErrorHandlingTests {
        @Test
        @DisplayName("should handle an unknown stateful node id gracefully")
        void shouldHandleInvalidTriggerTagGracefullyWhenInvalidTagIsProvided() {
            // Given: Stateful process and an unknown stateful node id
            String waitTaskProcessCode = "bpm.stateful.waitTaskProcess";
            Map<String, Object> taskContext = new HashMap<>();
            taskContext.put("taskData", "test_data");
            // When: Try to trigger with the unknown node id
            ProcessResult<Map<String, Object>> triggerResult = engine.trigger(ProcessDefinition.classpath(waitTaskProcessCode,
                            waitTaskProcessCode.replace(".", "/") + ".bpm"), ProcessTrigger.at("invalidTag"),
                    taskContext);
            // Then: Verify failure and error information
            assertThat(triggerResult.isSuccess()).as("Trigger with an unknown stateful node id should fail").isFalse();
            assertThat(triggerResult.getError().getMessage())
                .as("Error message should be present")
                .isNotNull()
                .as("Error message should not be empty")
                .hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("should handle trigger on non-stateful process gracefully when stateless process is used")
        void shouldHandleTriggerOnNonStatefulProcessGracefullyWhenStatelessProcessIsUsed() {
            // Given: Stateless process (not stateful)
            // This is a stateless process
            String statelessProcessCode = "bpmn20.compat.simple_service";
            Map<String, Object> processContext = new HashMap<>();
            // When: Try to trigger on stateless process
            ProcessResult<Map<String, Object>> triggerResult = engine.trigger(ProcessDefinition.classpath(statelessProcessCode,
                            statelessProcessCode.replace(".", "/") + ".bpm"), ProcessTrigger.at("someTag"),
                    processContext);
            // Then: Verify failure and error information
            assertThat(triggerResult.isSuccess()).as("Trigger on non-stateful process should fail").isFalse();
            assertThat(triggerResult.getError().getMessage())
                .as("Error message should be present")
                .isNotNull()
                .as("Error message should not be empty")
                .hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("should isolate concurrent trigger invocations for the same process")
        void shouldHandleConcurrentTriggersWhenMultipleThreadsTriggerSameStatefulProcess() throws InterruptedException {
            // Given: Stateful wait task process and concurrent execution setup
            String waitTaskProcessCode = "bpm.stateful.waitTaskProcess";
            int concurrentThreadCount = 5;
            CountDownLatch completionLatch = new CountDownLatch(concurrentThreadCount);
            List<Future<Boolean>> triggerFutures = new ArrayList<>();
            ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(concurrentThreadCount);
            // When: Trigger concurrently from multiple threads
            for (int i = 0; i < concurrentThreadCount; i++) {
                final int threadIndex = i;
                Future<Boolean> triggerFuture = threadPoolExecutor.submit(() -> {
                    try {
                        Map<String, Object> concurrentTriggerContext = new HashMap<>();
                        concurrentTriggerContext.put("taskData", "concurrent_test_" + threadIndex);

                        ProcessResult<Map<String, Object>> concurrentTriggerResult = engine.trigger(ProcessDefinition.classpath(waitTaskProcessCode,
                                        waitTaskProcessCode.replace(".", "/") + ".bpm"), ProcessTrigger.at("waitTask1"),
                                concurrentTriggerContext);

                        completionLatch.countDown();
                        return concurrentTriggerResult.isSuccess();
                    } catch (Exception failure) {
                        completionLatch.countDown();
                        return false;
                    }
                });
                triggerFutures.add(triggerFuture);
            }

            completionLatch.await(10, TimeUnit.SECONDS);
            threadPoolExecutor.shutdown();
            // Then: Verify at least some concurrent triggers succeeded
            long successfulTriggerCount = triggerFutures.stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception failure) {
                    return false;
                }
            }).count();

            assertThat(successfulTriggerCount).as("At least some concurrent triggers should succeed").isPositive();
        }
    }

    @Nested
    @DisplayName("F. Repeated and multi-step trigger tests")
    class RepeatedTriggerTests {
        @Test
        @DisplayName("should preserve results across repeated sequential triggers")
        void shouldPreserveResultsAcrossRepeatedSequentialTriggers() {
            // Given: Stateful wait task process and iteration count
            String waitTaskProcessCode = "bpm.stateful.waitTaskProcess";
            int rapidTriggerIterations = 50;

            for (int i = 0; i < rapidTriggerIterations; i++) {
                Map<String, Object> rapidTriggerContext = new HashMap<>();
                rapidTriggerContext.put("taskData", "rapid_test_" + i);

                ProcessResult<Map<String, Object>> rapidTriggerResult = engine.trigger(ProcessDefinition.classpath(waitTaskProcessCode,
                                waitTaskProcessCode.replace(".", "/") + ".bpm"), ProcessTrigger.at("waitTask1"),
                        rapidTriggerContext);

                assertThat(rapidTriggerResult.isSuccess()).as("Rapid trigger %d should succeed", i).isTrue();
            }
        }

        @Test
        @DisplayName("should execute a stateful flow across multiple waiting nodes")
        void shouldExecuteStatefulFlowAcrossMultipleWaitingNodes() {
            // Given: Complex stateful multi-wait event flow
            String multiWaitEventFlowCode = "bpm.stateful.multiWaitEventFlow";
            Map<String, Object> orderContext = new HashMap<>();
            orderContext.put("orderData", "performance_test_order");
            // Payment trigger
            Map<String, Object> paymentTriggerContext = new HashMap<>(orderContext);
            paymentTriggerContext.put("payment_received", "payment_success");

            ProcessResult<Map<String, Object>> paymentTriggerResult = engine.trigger(ProcessDefinition.classpath(multiWaitEventFlowCode,
                            multiWaitEventFlowCode.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitPayment", "paymentComplete"), paymentTriggerContext);

            assertThat(paymentTriggerResult.isSuccess()).as("Payment trigger should succeed").isTrue();
            // Delivery trigger
            Map<String, Object> deliveryTriggerContext = new HashMap<>(orderContext);
            deliveryTriggerContext.put("delivery_completed", "delivery_success");

            ProcessResult<Map<String, Object>> deliveryTriggerResult = engine.trigger(ProcessDefinition.classpath(multiWaitEventFlowCode,
                            multiWaitEventFlowCode.replace(".", "/") + ".bpm"),
                    ProcessTrigger.on("waitDelivery", "deliveryComplete"), deliveryTriggerContext);

            assertThat(deliveryTriggerResult.isSuccess()).as("Delivery trigger should succeed").isTrue();
        }
    }
}
