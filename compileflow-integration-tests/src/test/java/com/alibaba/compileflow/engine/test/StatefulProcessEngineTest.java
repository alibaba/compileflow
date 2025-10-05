package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for stateful process flows covering:
 * 1. Various waiting node types (waitTask, waitEventTask)
 * 2. Stateful gateways and branching logic
 * 3. Nested stateful subprocesses
 * 4. Complex trigger scenarios
 * 5. Error handling and timeout scenarios
 * 6. BPMN stateful flows
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful Process Engine Comprehensive Integration Tests")
class StatefulProcessEngineTest {

    private ProcessEngine engine;
    private ProcessToolingService<?> toolingService;

    @BeforeEach
    void setUp() {
        // Create engine instance for stateful testing
        engine = ProcessEngineFactory.createTbbpm();
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
        @DisplayName("should handle waitTask node with trigger")
        void testWaitTaskFlow() {
            String code = "bpm.stateful.waitTaskFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("taskData", "test_task_data");

            // Verify model loading
            assertNotNull(toolingService.loadFlowModel(ProcessSource.fromCode(code)));

            engine.execute(ProcessSource.fromCode(code), context);

            // Trigger the waiting task
            ProcessResult<Map<String, Object>> result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitForExternalTask",
                    context
            );

            assertTrue(result.isSuccess(), "WaitTask flow should execute successfully");
            assertNotNull(result.getData());
            assertTrue(result.getData().containsKey("waitResult"));
            assertEquals("processed_test_task_data", result.getData().get("waitResult"));
        }

        @Test
        @DisplayName("should handle multiple waitEventTask nodes in sequence")
        void testMultiWaitEventFlow() {
            String code = "bpm.stateful.multiWaitEventFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("orderData", "order_123");

            // First trigger - payment complete
            Map<String, Object> paymentContext = new HashMap<>(context);
            paymentContext.put("payment_received", "payment_success");

            ProcessResult<Map<String, Object>> paymentResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitPaymentComplete",
                    "paymentComplete",
                    paymentContext
            );

            assertTrue(paymentResult.isSuccess(), "Payment trigger should succeed");

            // Second trigger - delivery complete
            Map<String, Object> deliveryContext = new HashMap<>(context);
            deliveryContext.put("delivery_completed", "delivery_success");

            ProcessResult<Map<String, Object>> deliveryResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitDeliveryComplete",
                    "deliveryComplete",
                    deliveryContext
            );

            assertTrue(deliveryResult.isSuccess(), "Delivery trigger should succeed");
            assertNotNull(deliveryResult.getData());
            assertTrue(deliveryResult.getData().containsKey("finalResult"));
        }
    }

    @Nested
    @DisplayName("C. Nested Stateful SubProcess Tests")
    class NestedStatefulSubProcessTests {

        @Test
        @DisplayName("should handle nested stateful subprocess with multiple triggers")
        void testNestedStatefulSubProcess() {
            String code = "bpm.stateful.nestedStatefulSubProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("mainData", "main_process_data");

            // First trigger - main event
            ProcessResult<Map<String, Object>> mainResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitMainEvent",
                    "mainEvent",
                    context
            );

            assertTrue(mainResult.isSuccess(), "Main event trigger should succeed");
        }
    }

    @Nested
    @DisplayName("D. BPMN Stateful Flow Tests")
    class BpmnStatefulFlowTests {

        @Test
        @DisplayName("should handle BPMN stateful receive task flow")
        void testBpmnStatefulReceiveTask() {
            // Create BPMN engine for this test
            try (ProcessEngine bpmnEngine = ProcessEngineFactory.createBpmn()) {
                String code = "bpmn20.stateful.stateful_receive_task";
                Map<String, Object> context = new HashMap<>();
                context.put("taskData", "user_task_data");

                // Verify model loading
                ProcessToolingService<?> bpmnTooling = bpmnEngine.tooling();
                assertNotNull(bpmnTooling.loadFlowModel(ProcessSource.fromCode(code)));

                // Trigger final approval
                Map<String, Object> approvalContext = new HashMap<>(context);
                approvalContext.put("final_approval", "approved");

                ProcessResult<Map<String, Object>> result = bpmnEngine.trigger(ProcessSource.fromCode(code),
                        "receiveTask1", "approvalMessage", approvalContext);

                assertTrue(result.isSuccess(), "BPMN stateful user task should succeed");
                assertNotNull(result.getData());
            }
        }

        @Test
        @DisplayName("should handle BPMN stateful parallel gateway flow")
        void testBpmnStatefulParallelGateway() {
            try (ProcessEngine bpmnEngine = ProcessEngineFactory.createBpmn()) {
                String code = "bpmn20.stateful.stateful_parallel_gateway";
                Map<String, Object> context = new HashMap<>();
                context.put("processData", "parallel_process_data");

                // Trigger branch 1
                Map<String, Object> branch1Context = new HashMap<>(context);
                branch1Context.put("branch1_data", "branch1_result");

                ProcessResult<Map<String, Object>> branch1Result = bpmnEngine.trigger(
                        ProcessSource.fromCode(code),
                        "receiveTask1",
                        "branch1Message",
                        branch1Context
                );

                assertTrue(branch1Result.isSuccess(), "Branch 1 trigger should succeed");

                // Trigger branch 2
                Map<String, Object> branch2Context = new HashMap<>(context);
                branch2Context.put("branch2_data", "branch2_result");

                ProcessResult<Map<String, Object>> branch2Result = bpmnEngine.trigger(
                        ProcessSource.fromCode(code),
                        "receiveTask2",
                        "branch2Message",
                        branch2Context
                );

                assertTrue(branch2Result.isSuccess(), "Branch 2 trigger should succeed");
                assertNotNull(branch2Result.getData());
            }
        }
    }

    @Nested
    @DisplayName("E. Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle invalid trigger tag gracefully")
        void testInvalidTriggerTag() {
            String code = "bpm.stateful.waitTaskFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("taskData", "test_data");

            // Try to trigger with invalid tag

            ProcessResult result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "invalidTag",
                    null,
                    context
            );
            assertFalse(result.isSuccess(), "Trigger with invalid tag should fail");
        }

        @Test
        @DisplayName("should handle trigger on non-stateful process")
        void testTriggerOnNonStatefulProcess() {
            String code = "bpmn20.compat.simple_service"; // This is a stateless process
            Map<String, Object> context = new HashMap<>();

            // Try to trigger on stateless process
            ProcessResult result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "someTag",
                    null,
                    context
            );
            assertFalse(result.isSuccess(), "Trigger on non-stateful process should fail");
        }

        @Test
        @DisplayName("should handle concurrent triggers on same stateful process")
        void testConcurrentTriggers() throws InterruptedException {
            String code = "bpm.stateful.waitTaskFlow";
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<Boolean>> futures = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                Future<Boolean> future = executor.submit(() -> {
                    try {
                        Map<String, Object> context = new HashMap<>();
                        context.put("taskData", "concurrent_test_" + index);

                        ProcessResult<Map<String, Object>> result = engine.trigger(
                                ProcessSource.fromCode(code),
                                "waitForExternalTask",
                                null,
                                context
                        );

                        latch.countDown();
                        return result.isSuccess();
                    } catch (Exception e) {
                        latch.countDown();
                        return false;
                    }
                });
                futures.add(future);
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Check results
            long successCount = futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertTrue(successCount > 0, "At least some concurrent triggers should succeed");
        }
    }

    @Nested
    @DisplayName("F. Performance and Scalability Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should handle rapid sequential triggers efficiently")
        void testRapidSequentialTriggers() {
            String code = "bpm.stateful.waitTaskFlow";
            int iterations = 50;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("taskData", "rapid_test_" + i);

                ProcessResult<Map<String, Object>> result = engine.trigger(
                        ProcessSource.fromCode(code),
                        "waitForExternalTask",
                        null,
                        context
                );

                assertTrue(result.isSuccess(), "Rapid trigger " + i + " should succeed");
            }
            long endTime = System.currentTimeMillis();

            System.out.println("Executed " + iterations + " rapid triggers in " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 5000, "Rapid triggers should complete within reasonable time");
        }

        @Test
        @DisplayName("should handle complex stateful flow with multiple waiting nodes")
        void testComplexStatefulFlowPerformance() {
            String code = "bpm.stateful.multiWaitEventFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("orderData", "performance_test_order");

            long startTime = System.currentTimeMillis();

            // Payment trigger
            Map<String, Object> paymentContext = new HashMap<>(context);
            paymentContext.put("payment_received", "payment_success");

            ProcessResult<Map<String, Object>> paymentResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitPaymentComplete",
                    "paymentComplete",
                    paymentContext
            );

            assertTrue(paymentResult.isSuccess());

            // Delivery trigger
            Map<String, Object> deliveryContext = new HashMap<>(context);
            deliveryContext.put("delivery_completed", "delivery_success");

            ProcessResult<Map<String, Object>> deliveryResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitDeliveryComplete",
                    "deliveryComplete",
                    deliveryContext
            );

            long endTime = System.currentTimeMillis();

            assertTrue(deliveryResult.isSuccess());
            System.out.println("Complex stateful flow execution time: " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 5000, "Complex stateful flow should complete within reasonable time");
        }
    }
}


