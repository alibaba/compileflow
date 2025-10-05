package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for stateful process flows with complex trigger scenarios.
 * <p>
 * This test suite covers:
 * 1. Complex gateway scenarios with triggers (Exclusive, Parallel, Inclusive)
 * 2. Nested stateful processes with multiple trigger points
 * 3. Mixed stateful/stateless node combinations
 * 4. Error handling and edge cases in trigger scenarios
 * 5. Performance and concurrency testing
 * 6. BPMN stateful flows with complex trigger patterns
 * 7. State persistence and recovery scenarios
 * 8. Complex branching with conditional triggers
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful Trigger Comprehensive Integration Tests")
class StatefulTriggerComprehensiveTest {

    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineFactory.createTbbpm();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("A. Complex Gateway Trigger Scenarios")
    class ComplexGatewayTriggerTests {

        @Test
        @DisplayName("should handle exclusive gateway with multiple conditional triggers")
        void testExclusiveGatewayWithConditionalTriggers() {
            String code = "bpm.stateful.complexExclusiveGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("requestType", "loan_application");
            context.put("amount", 15000);
            context.put("creditScore", 750);

            // Test high amount approval path
            Map<String, Object> highAmountContext = new HashMap<>(context);
            highAmountContext.put("high_approval_result", "approved");
            highAmountContext.put("approval_reason", "excellent_credit");

            ProcessResult<Map<String, Object>> highAmountResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitHighAmountApproval",
                    "highAmountApprovalComplete",
                    highAmountContext
            );

            assertTrue(highAmountResult.isSuccess(), "High amount approval should succeed");
            assertNotNull(highAmountResult.getData());
            assertTrue(highAmountResult.getData().containsKey("finalResult"));
            // processHighApproval calls combineResults("approved", "excellent_credit")
            // Expected: "combined_approved_excellent_credit"
            assertEquals("combined_approved_excellent_credit", highAmountResult.getData().get("finalResult"));

            // Test low amount approval path
            Map<String, Object> lowAmountContext = new HashMap<>();
            lowAmountContext.put("requestType", "loan_application");
            lowAmountContext.put("amount", 5000);
            lowAmountContext.put("creditScore", 650);
            lowAmountContext.put("low_approval_result", "approved");

            ProcessResult<Map<String, Object>> lowAmountResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitLowAmountApproval",
                    "lowAmountApprovalComplete",
                    lowAmountContext
            );

            assertTrue(lowAmountResult.isSuccess(), "Low amount approval should succeed");
            // processLowApproval calls combineResults("approved", "loan_application")
            // Expected: "combined_approved_loan_application"
            assertEquals("combined_approved_loan_application", lowAmountResult.getData().get("finalResult"));

            // Test rejection path
            Map<String, Object> rejectionContext = new HashMap<>();
            rejectionContext.put("requestType", "loan_application");
            rejectionContext.put("amount", 20000);
            rejectionContext.put("creditScore", 500);
            rejectionContext.put("rejection_reason", "poor_credit");

            ProcessResult<Map<String, Object>> rejectionResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitRejection",
                    "rejectionComplete",
                    rejectionContext
            );

            assertTrue(rejectionResult.isSuccess(), "Rejection should succeed");
            // processRejection calls combineResults("poor_credit", "loan_application")
            // Expected: "combined_poor_credit_loan_application"
            assertEquals("combined_poor_credit_loan_application", rejectionResult.getData().get("finalResult"));
        }

        @Test
        @DisplayName("should handle parallel gateway with concurrent triggers")
        void testParallelGatewayWithConcurrentTriggers() throws InterruptedException {
            String code = "bpm.stateful.complexParallelGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("orderId", "order_12345");
            context.put("customerId", "customer_67890");

            // Simulate concurrent triggers for parallel branches
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch latch = new CountDownLatch(3);
            List<Future<ProcessResult<Map<String, Object>>>> futures = new ArrayList<>();

            // Branch 1: Payment processing
            Future<ProcessResult<Map<String, Object>>> paymentFuture = executor.submit(() -> {
                try {
                    Map<String, Object> paymentContext = new HashMap<>(context);
                    paymentContext.put("payment_status", "completed");
                    paymentContext.put("payment_method", "credit_card");

                    ProcessResult<Map<String, Object>> result = engine.trigger(
                            ProcessSource.fromCode(code),
                            "waitPaymentProcessing",
                            "paymentComplete",
                            paymentContext
                    );
                    latch.countDown();
                    return result;
                } catch (Exception e) {
                    latch.countDown();
                    throw new RuntimeException(e);
                }
            });
            futures.add(paymentFuture);

            // Branch 2: Inventory check
            Future<ProcessResult<Map<String, Object>>> inventoryFuture = executor.submit(() -> {
                try {
                    Map<String, Object> inventoryContext = new HashMap<>(context);
                    inventoryContext.put("inventory_status", "available");
                    inventoryContext.put("reserved_quantity", 2);

                    ProcessResult<Map<String, Object>> result = engine.trigger(
                            ProcessSource.fromCode(code),
                            "waitInventoryCheck",
                            "inventoryComplete",
                            inventoryContext
                    );
                    latch.countDown();
                    return result;
                } catch (Exception e) {
                    latch.countDown();
                    throw new RuntimeException(e);
                }
            });
            futures.add(inventoryFuture);

            // Branch 3: Customer validation
            Future<ProcessResult<Map<String, Object>>> validationFuture = executor.submit(() -> {
                try {
                    Map<String, Object> validationContext = new HashMap<>(context);
                    validationContext.put("validation_status", "verified");
                    validationContext.put("customer_tier", "premium");

                    ProcessResult<Map<String, Object>> result = engine.trigger(
                            ProcessSource.fromCode(code),
                            "waitCustomerValidation",
                            "validationComplete",
                            validationContext
                    );
                    latch.countDown();
                    return result;
                } catch (Exception e) {
                    latch.countDown();
                    throw new RuntimeException(e);
                }
            });
            futures.add(validationFuture);

            // Wait for all branches to complete
            assertTrue(latch.await(10, TimeUnit.SECONDS), "All parallel branches should complete within timeout");

            // Verify all branches succeeded
            for (Future<ProcessResult<Map<String, Object>>> future : futures) {
                ProcessResult<Map<String, Object>> result = null;
                try {
                    result = future.get();
                } catch (ExecutionException e) {

                }
                assertTrue(result.isSuccess(), "All parallel branches should succeed");
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("should handle inclusive gateway with conditional branch triggers")
        void testInclusiveGatewayWithConditionalTriggers() {
            String code = "bpm.stateful.complexInclusiveGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("orderType", "premium");
            context.put("customerTier", "gold");
            context.put("hasSpecialRequests", true);

            // Test scenario 1: Premium processing branch
            Map<String, Object> premiumContext = new HashMap<>(context);
            premiumContext.put("premium_processing_result", "completed");

            ProcessResult<Map<String, Object>> premiumResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitPremiumProcessing",
                    "premiumProcessingComplete",
                    premiumContext
            );

            assertTrue(premiumResult.isSuccess(), "Premium processing should succeed");
            assertNotNull(premiumResult.getData());
            assertTrue(premiumResult.getData().containsKey("combinedResult"));
            // combineResults("premium", "gold") => "combined_premium_gold"
            assertEquals("combined_premium_gold", premiumResult.getData().get("combinedResult"));

            // Test scenario 2: Standard processing branch
            Map<String, Object> standardContext = new HashMap<>();
            standardContext.put("orderType", "standard");
            standardContext.put("customerTier", "silver");
            standardContext.put("hasSpecialRequests", false);
            standardContext.put("standard_processing_result", "completed");

            ProcessResult<Map<String, Object>> standardResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitStandardProcessing",
                    "standardProcessingComplete",
                    standardContext
            );

            assertTrue(standardResult.isSuccess(), "Standard processing should succeed");
            assertEquals("combined_standard_silver", standardResult.getData().get("combinedResult"));
        }
    }

    @Nested
    @DisplayName("B. Nested Stateful Process Tests")
    class NestedStatefulProcessTests {

        @Test
        @DisplayName("should handle deeply nested stateful subprocesses with multiple trigger levels")
        void testDeeplyNestedStatefulSubProcesses() {
            String code = "bpm.stateful.deeplyNestedStatefulProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("mainProcessData", "main_data");
            context.put("subProcessLevel1Data", "level1_data");
            context.put("subProcessLevel2Data", "level2_data");

            // Level 1 trigger
            Map<String, Object> level1Context = new HashMap<>(context);
            level1Context.put("level1_trigger_data", "level1_result");

            ProcessResult<Map<String, Object>> level1Result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitLevel1Event",
                    "level1EventComplete",
                    level1Context
            );

            assertTrue(level1Result.isSuccess(), "Level 1 trigger should succeed");

            // Level 2 trigger
            Map<String, Object> level2Context = new HashMap<>(context);
            level2Context.put("level2_trigger_data", "level2_result");

            ProcessResult<Map<String, Object>> level2Result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitLevel2Event",
                    "level2EventComplete",
                    level2Context
            );

            assertTrue(level2Result.isSuccess(), "Level 2 trigger should succeed");

            // Level 3 trigger
            Map<String, Object> level3Context = new HashMap<>(context);
            level3Context.put("level3_trigger_data", "level3_result");

            ProcessResult<Map<String, Object>> level3Result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitLevel3Event",
                    "level3EventComplete",
                    level3Context
            );

            assertTrue(level3Result.isSuccess(), "Level 3 trigger should succeed");
            assertNotNull(level3Result.getData());
            assertTrue(level3Result.getData().containsKey("finalNestedResult"));
            assertEquals("combined_level2_data_level3_result",
                    level3Result.getData().get("finalNestedResult"));
        }

        @Test
        @DisplayName("should handle mixed stateful/stateless nodes in nested processes")
        void testMixedStatefulStatelessNodes() {
            String code = "bpm.stateful.mixedStatefulStatelessProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("processData", "mixed_process_data");

            // Trigger stateful part; stateless portion runs automatically
            Map<String, Object> statefulContext = new HashMap<>(context);
            statefulContext.put("stateful_trigger_data", "stateful_result");
            statefulContext.put("statelessResult", "stateless_result");
            statefulContext.put("statefulResult", "stateful_result");

            ProcessResult<Map<String, Object>> statefulResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitStatefulEvent",
                    "statefulEventComplete",
                    statefulContext
            );

            assertTrue(statefulResult.isSuccess(), "Stateful trigger should succeed");
            assertNotNull(statefulResult.getData());
            assertTrue(statefulResult.getData().containsKey("mixedResult"));
            assertEquals("combined_stateless_result_stateful_result",
                    statefulResult.getData().get("mixedResult"));
        }
    }

    @Nested
    @DisplayName("C. Complex Trigger Error Handling")
    class ComplexTriggerErrorHandlingTests {

        @Test
        @DisplayName("should handle invalid trigger scenarios gracefully")
        void testInvalidTriggerScenarios() {
            String code = "bpm.stateful.complexExclusiveGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("requestType", "loan_application");

            // Test 1: Invalid tag
            ProcessResult<Map<String, Object>> invalidTagResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "invalidTag",
                    "someEvent",
                    context
            );

            assertFalse(invalidTagResult.isSuccess(), "Invalid tag should fail gracefully");
        }

        @Test
        @DisplayName("should handle trigger on non-stateful process")
        void testTriggerOnNonStatefulProcess() {
            String code = "bpm.ktv.ktvExample"; // Stateless process
            Map<String, Object> context = new HashMap<>();
            context.put("testData", "test_value");

            ProcessResult<Map<String, Object>> result = engine.trigger(
                    ProcessSource.fromCode(code),
                    "someTag",
                    "someEvent",
                    context
            );

            assertFalse(result.isSuccess(), "Trigger on non-stateful process should fail");
        }

        @Test
        @DisplayName("should handle concurrent triggers on same process instance")
        void testConcurrentTriggersOnSameInstance() throws InterruptedException {
            String code = "bpm.stateful.complexParallelGateway";
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        Map<String, Object> context = new HashMap<>();
                        context.put("orderId", "concurrent_order_" + index);
                        context.put("payment_status", "completed");

                        ProcessResult<Map<String, Object>> result = engine.trigger(
                                ProcessSource.fromCode(code),
                                "waitPaymentProcessing",
                                "paymentComplete",
                                context
                        );

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS), "All concurrent triggers should complete within timeout");
            executor.shutdown();

            System.out.println("Concurrent triggers - Success: " + successCount.get() + ", Failure: " + failureCount.get());
            assertTrue(successCount.get() > 0, "At least some concurrent triggers should succeed");
        }
    }

    @Nested
    @DisplayName("D. Performance and Scalability Tests")
    class PerformanceAndScalabilityTests {

        @Test
        @DisplayName("should handle high-frequency triggers efficiently")
        void testHighFrequencyTriggers() {
            String code = "bpm.stateful.highFrequencyTriggerProcess";
            int iterations = 100;

            long startTime = System.currentTimeMillis();
            int successCount = 0;

            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("triggerId", "trigger_" + i);
                context.put("triggerData", "data_" + i);

                ProcessResult<Map<String, Object>> result = engine.trigger(
                        ProcessSource.fromCode(code),
                        "waitHighFrequencyEvent",
                        "highFrequencyEventComplete",
                        context
                );

                if (result.isSuccess()) {
                    successCount++;
                    // Verify: combineResults("trigger_0", "data_0") => "combined_trigger_0_data_0"
                    assertEquals("combined_trigger_" + i + "_data_" + i, result.getData().get("processedData"));
                }
            }

            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            System.out.println("High frequency triggers - Executed: " + iterations +
                    ", Success: " + successCount +
                    ", Time: " + executionTime + "ms");

            assertTrue(successCount > iterations * 0.9, "At least 90% of triggers should succeed");
            assertTrue(executionTime < 10000, "High frequency triggers should complete within reasonable time");
        }

        @Test
        @DisplayName("should handle complex stateful flow with multiple waiting nodes efficiently")
        void testComplexStatefulFlowPerformance() {
            String code = "bpm.stateful.complexMultiWaitProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("processId", "performance_test_process");

            long startTime = System.currentTimeMillis();

            // Sequential triggers for complex flow
            String[] triggerTags = {"waitStep1", "waitStep2", "waitStep3", "waitStep4", "waitStep5"};
            String[] triggerEvents = {"step1Complete", "step2Complete", "step3Complete", "step4Complete", "step5Complete"};

            ProcessResult<Map<String, Object>> finalResult = null;
            for (int i = 0; i < triggerTags.length; i++) {
                Map<String, Object> triggerContext = new HashMap<>(context);
                triggerContext.put("step" + (i + 1) + "_data", "step" + (i + 1) + "_result");

                finalResult = engine.trigger(
                        ProcessSource.fromCode(code),
                        triggerTags[i],
                        triggerEvents[i],
                        triggerContext
                );

                assertTrue(finalResult.isSuccess(), "Step " + (i + 1) + " trigger should succeed");
            }

            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            System.out.println("Complex stateful flow execution time: " + executionTime + "ms");
            assertTrue(executionTime < 5000, "Complex stateful flow should complete within reasonable time");
            assertNotNull(finalResult.getData());
            assertTrue(finalResult.getData().containsKey("finalComplexResult"));
            assertEquals("combined_null_step5_result",
                    finalResult.getData().get("finalComplexResult"));
        }
    }

    @Nested
    @DisplayName("E. BPMN Stateful Flow Tests")
    class BpmnStatefulFlowTests {

        @Test
        @DisplayName("should handle BPMN stateful receive task with complex trigger patterns")
        void testBpmnStatefulReceiveTaskComplexTriggers() {
            try (ProcessEngine bpmnEngine = ProcessEngineFactory.createBpmn()) {
                String code = "bpmn20.stateful.complex_stateful_receive_task";
                Map<String, Object> context = new HashMap<>();
                context.put("taskData", "complex_bpmn_task_data");

                // Multiple sequential triggers
                String[] events = {"initialApproval", "managerApproval", "finalApproval"};
                String[] tags = {"receiveTask1", "receiveTask2", "receiveTask3"};

                ProcessResult<Map<String, Object>> finalResult = null;
                for (int i = 0; i < events.length; i++) {
                    Map<String, Object> triggerContext = new HashMap<>(context);
                    triggerContext.put("approval" + (i + 1) + "_result", "approved");

                    finalResult = bpmnEngine.trigger(
                            ProcessSource.fromCode(code),
                            tags[i],
                            events[i],
                            triggerContext
                    );

                    assertTrue(finalResult.isSuccess(), "BPMN trigger " + (i + 1) + " should succeed");
                }

                assertNotNull(finalResult.getData());
                assertTrue(finalResult.getData().containsKey("finalApprovalResult"));
            }
        }

        @Test
        @DisplayName("should handle BPMN stateful parallel gateway with complex trigger scenarios")
        void testBpmnStatefulParallelGatewayComplexTriggers() {
            try (ProcessEngine bpmnEngine = ProcessEngineFactory.createBpmn()) {
                String code = "bpmn20.stateful.complex_stateful_parallel_gateway";
                Map<String, Object> context = new HashMap<>();
                context.put("processData", "complex_parallel_process_data");

                // Test parallel branches with different trigger patterns
                ExecutorService executor = Executors.newFixedThreadPool(4);
                CountDownLatch latch = new CountDownLatch(4);
                List<Future<ProcessResult<Map<String, Object>>>> futures = new ArrayList<>();

                // Branch 1: Fast processing
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, Object> branchContext = new HashMap<>(context);
                        branchContext.put("fast_processing_result", "completed");

                        ProcessResult<Map<String, Object>> result = bpmnEngine.trigger(
                                ProcessSource.fromCode(code),
                                "receiveTask1",
                                "fastProcessingComplete",
                                branchContext
                        );
                        latch.countDown();
                        return result;
                    } catch (Exception e) {
                        latch.countDown();
                        throw new RuntimeException(e);
                    }
                }));

                // Branch 2: Slow processing
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(100); // Simulate slow processing
                        Map<String, Object> branchContext = new HashMap<>(context);
                        branchContext.put("slow_processing_result", "completed");

                        ProcessResult<Map<String, Object>> result = bpmnEngine.trigger(
                                ProcessSource.fromCode(code),
                                "receiveTask2",
                                "slowProcessingComplete",
                                branchContext
                        );
                        latch.countDown();
                        return result;
                    } catch (Exception e) {
                        latch.countDown();
                        throw new RuntimeException(e);
                    }
                }));

                // Branch 3: Conditional processing
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, Object> branchContext = new HashMap<>(context);
                        branchContext.put("conditional_processing_result", "completed");
                        branchContext.put("condition_met", true);

                        ProcessResult<Map<String, Object>> result = bpmnEngine.trigger(
                                ProcessSource.fromCode(code),
                                "receiveTask3",
                                "conditionalProcessingComplete",
                                branchContext
                        );
                        latch.countDown();
                        return result;
                    } catch (Exception e) {
                        latch.countDown();
                        throw new RuntimeException(e);
                    }
                }));

                // Branch 4: Error handling
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, Object> branchContext = new HashMap<>(context);
                        branchContext.put("error_handling_result", "recovered");
                        branchContext.put("error_occurred", false);

                        ProcessResult<Map<String, Object>> result = bpmnEngine.trigger(
                                ProcessSource.fromCode(code),
                                "receiveTask4",
                                "errorHandlingComplete",
                                branchContext
                        );
                        latch.countDown();
                        return result;
                    } catch (Exception e) {
                        latch.countDown();
                        throw new RuntimeException(e);
                    }
                }));

                try {
                    assertTrue(latch.await(15, TimeUnit.SECONDS), "All BPMN parallel branches should complete within timeout");
                } catch (InterruptedException ignored) {

                }

                // Verify all branches succeeded
                for (Future<ProcessResult<Map<String, Object>>> future : futures) {
                    ProcessResult<Map<String, Object>> result = null;
                    try {
                        result = future.get();
                    } catch (Exception ignored) {
                    }
                    assertTrue(result.isSuccess(), "All BPMN parallel branches should succeed");
                }

                executor.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("F. State Persistence and Recovery Tests")
    class StatePersistenceAndRecoveryTests {

        @Test
        @DisplayName("should maintain state consistency across multiple trigger calls")
        void testStateConsistencyAcrossTriggers() {
            String code = "bpm.stateful.stateConsistencyProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("sessionId", "session_12345");
            context.put("userId", "user_67890");

            // First trigger - initialize state
            Map<String, Object> initContext = new HashMap<>(context);
            initContext.put("initial_data", "initial_value");

            ProcessResult<Map<String, Object>> initResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitInitialization",
                    "initializationComplete",
                    initContext
            );

            assertTrue(initResult.isSuccess(), "Initialization trigger should succeed");
            assertNotNull(initResult.getData());
            assertTrue(initResult.getData().containsKey("stateData"));

            // Second trigger - update state
            Map<String, Object> updateContext = new HashMap<>(context);
            updateContext.put("update_data", "updated_value");

            ProcessResult<Map<String, Object>> updateResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitStateUpdate",
                    "stateUpdateComplete",
                    updateContext
            );

            assertTrue(updateResult.isSuccess(), "State update trigger should succeed");
            assertNotNull(updateResult.getData());
            assertTrue(updateResult.getData().containsKey("updatedStateData"));

            // Third trigger - finalize state
            Map<String, Object> finalizeContext = new HashMap<>(context);
            finalizeContext.put("finalize_data", "finalized_value");

            ProcessResult<Map<String, Object>> finalizeResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitStateFinalization",
                    "stateFinalizationComplete",
                    finalizeContext
            );

            assertTrue(finalizeResult.isSuccess(), "State finalization trigger should succeed");
            assertNotNull(finalizeResult.getData());
            assertTrue(finalizeResult.getData().containsKey("finalStateData"));
        }

        @Test
        @DisplayName("should handle state recovery after process interruption")
        void testStateRecoveryAfterInterruption() {
            String code = "bpm.stateful.stateRecoveryProcess";
            Map<String, Object> context = new HashMap<>();
            context.put("processId", "recovery_test_process");

            // Simulate process interruption by triggering an error
            Map<String, Object> errorContext = new HashMap<>(context);
            errorContext.put("error_condition", true);

            ProcessResult<Map<String, Object>> errorResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitErrorCondition",
                    "errorOccurred",
                    errorContext
            );

            // Process should handle error gracefully
            assertTrue(errorResult.isSuccess(), "Error handling should succeed");
            assertNotNull(errorResult.getData());
            assertTrue(errorResult.getData().containsKey("errorHandled"));

            // Recovery trigger
            Map<String, Object> recoveryContext = new HashMap<>(context);
            recoveryContext.put("recovery_data", "recovered_value");

            ProcessResult<Map<String, Object>> recoveryResult = engine.trigger(
                    ProcessSource.fromCode(code),
                    "waitRecovery",
                    "recoveryComplete",
                    recoveryContext
            );

            assertTrue(recoveryResult.isSuccess(), "Recovery trigger should succeed");
            assertNotNull(recoveryResult.getData());
            assertTrue(recoveryResult.getData().containsKey("recoveredState"));
        }
    }
}
