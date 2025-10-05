package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.*;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A comprehensive and modernized integration test suite for the BPMN ProcessEngine.
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("BPMN Process Engine Comprehensive Integration Tests")
class BpmnProcessEngineTest {

    private ProcessEngine engine;
    private ProcessAdminService adminService;
    private ProcessToolingService<?> toolingService;

    @BeforeEach
    void setUp() {
        // Crucially, create a BPMN-specific engine instance for all tests.
        engine = ProcessEngineFactory.createBpmn();
        adminService = engine.admin();
        toolingService = engine.tooling();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    public static class KtvRequest {
        public List<String> pList;
    }

    public static class KtvResponse {
        public int price;
    }

    @Nested
    @DisplayName("A. Standard BPMN Element Tests")
    class BpmnElementTests {

        @Test
        @DisplayName("[Original] should execute a simple service task")
        void testSimpleService() {
            String code = "bpmn20.compat.simple_service";

            // Tooling check
            assertNotNull(toolingService.loadFlowModel(ProcessSource.fromCode(code)));

            // Execution check
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), new HashMap<>());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("[Original] should execute a synchronous subprocess")
        void testSubprocessSync() {
            String code = "bpmn20.compat.subprocess_sync";
            Map<String, Object> context = new HashMap<>();
            context.put("a", 11);
            context.put("b", 22);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should execute CallActivity with external process invocation")
        void testCallActivityExecution() {
            String code = "bpmn20.compat.call_activity";
            Map<String, Object> context = new HashMap<>();
            context.put("inputValue", 5);

            // Verify model loading
            assertNotNull(toolingService.loadFlowModel(ProcessSource.fromCode(code)));

            // Verify code generation contains CallActivity logic
            String javaCode = toolingService.generateJavaCode(ProcessSource.fromCode(code));
            assertNotNull(javaCode);
            assertTrue(javaCode.contains("CallActivity"));

            // Execute the flow
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess(), "CallActivity flow should execute successfully");
            assertNotNull(result.getData());
            assertTrue(result.getData().containsKey("outputValue"));

            // Verify the result is processed correctly (5 + 5 = 10, then 10 * 2 = 20)
            assertEquals(20, result.getData().get("outputValue"));
        }

        @Nested
        @DisplayName("Gateway and Branching Logic")
        class GatewayAndBranchingTests {
            @Test
            @DisplayName("[Original] should execute exclusive gateway - true path")
            void testExclusiveGatewayTrue() {
                String code = "bpmn20.gateway.exclusive_gateway";
                Map<String, Object> context = new HashMap<>();
                context.put("flag", true);

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess());
            }

            @Test
            @DisplayName("[Original] should execute exclusive gateway - false path")
            void testExclusiveGatewayFalse() {
                String code = "bpmn20.gateway.exclusive_gateway";
                Map<String, Object> context = new HashMap<>();
                context.put("flag", false);

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess());
            }

            @Test
            @DisplayName("[Original] should execute a parallel gateway")
            void testParallelGateway() {
                String code = "bpmn20.gateway.parallel_gateway";
                Map<String, Object> context = new HashMap<>();
                context.put("a", 10);
                context.put("b", 200);

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess());
                assertEquals(210, result.getData().get("serviceTask1Result"));
                assertEquals(2000, result.getData().get("serviceTask2Result"));
            }

            @Test
            @DisplayName("should handle InclusiveGateway with multiple condition branches")
            void testInclusiveGatewayExecution() {
                String code = "bpmn20.gateway.inclusive_gateway";

                // Test case 1: Both conditions true - should execute both branches
                Map<String, Object> context1 = new HashMap<>();
                context1.put("condition1", true);
                context1.put("condition2", true);
                context1.put("branch1", "Branch 1");
                context1.put("branch2", "Branch 2");
                context1.put("branch3", "Default Branch");


                ProcessResult<Map<String, Object>> result1 = engine.execute(ProcessSource.fromCode(code), context1);
                assertTrue(result1.isSuccess(), "InclusiveGateway with both conditions should succeed");

                // Test case 2: Only condition1 true - should execute branch1
                Map<String, Object> context2 = new HashMap<>();
                context2.put("condition1", true);
                context2.put("condition2", false);
                context2.put("branch1", "Branch 1");
                context2.put("branch2", "Branch 2");
                context2.put("branch3", "Default Branch");

                ProcessResult<Map<String, Object>> result2 = engine.execute(ProcessSource.fromCode(code), context2);
                assertTrue(result2.isSuccess(), "InclusiveGateway with condition1 only should succeed");

                // Test case 3: No conditions true - should execute default branch
                Map<String, Object> context3 = new HashMap<>();
                context3.put("condition1", false);
                context3.put("condition2", false);
                context3.put("branch1", "Branch 1");
                context3.put("branch2", "Branch 2");
                context3.put("branch3", "Default Branch");

                ProcessResult<Map<String, Object>> result3 = engine.execute(ProcessSource.fromCode(code), context3);
                assertTrue(result3.isSuccess(), "InclusiveGateway with default branch should succeed");

                // Verify code generation
                String javaCode = toolingService.generateJavaCode(ProcessSource.fromCode(code));
                assertNotNull(javaCode);
            }
        }
    }

    @Nested
    @DisplayName("B. Loop and Multi-Instance Tests")
    class LoopTests {

        @Test
        @DisplayName("[Original] should execute a standard loop")
        void testStandardLoop() {
            String code = "bpmn20.compat.standard_loop";
            Map<String, Object> context = new HashMap<>();
            context.put("i", 0);
            context.put("msg", "hello");

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("[Original] should execute a multi-instance loop")
        void testMultiInstanceLoop() {
            String code = "bpmn20.compat.multi_instance_loop";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("A", "B", "C"));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("[Original] should execute nested multi-instance loops")
        void testNestedMultiInstance() {
            String code = "bpmn20.compat.nested_multi_instance";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("A", "B"));
            context.put("subList", Arrays.asList(1, 2, 3));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("[Original] should execute a parallel gateway within a multi-instance loop")
        void testParallelGatewayMultiInstance() {
            String code = "bpmn20.gateway.parallel_gateway_multi_instance";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("X", "Y"));
            context.put("subList", Arrays.asList(10, 20));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("C. Advanced Architectural Feature Tests")
    class NewFeatureTests {

        @Test
        @DisplayName("should execute a BPMN flow with strongly-typed DTOs")
        void testExecuteWithTypedDto() {
            String code = "bpmn20.ktv.ktvExample";
            KtvRequest request = new KtvRequest();
            request.pList = Lists.newArrayList("user1", "user2");

            ProcessResult<KtvResponse> result = engine.execute(ProcessSource.fromCode(code), request, KtvResponse.class);

            assertTrue(result.isSuccess());
            assertNotNull(result.getData());
            assertTrue(result.getData().price > 0);
        }

        @Test
        @DisplayName("should hot-reload BPMN ktv example successfully")
        void testHotReloadFlow() throws IOException {
            String code = "bpmn20.ktv.ktvExample";

            // V1: Use the original ktvExample from the resource file
            String v1 = toolingService.generateJavaCode(ProcessSource.fromCode(code));
            assertNotNull(v1);

            // Prepare context parameters
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("u1", "u2"));

            // Pre-compile and execute V1
            adminService.deploy(ProcessSource.fromCode(code));
            ProcessResult<Map<String, Object>> r1 = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(r1.isSuccess());
            assertEquals(r1.getData().get("price"), 54);

            // V2: Replace the price calculation logic in the XML content (calPrice -> calPriceV2)
            String path = code.replace('.', '/') + ".bpmn20";
            String v1Xml = Resources.toString(
                    Resources.getResource(path),
                    StandardCharsets.UTF_8);

            String v2Xml = v1Xml.replace(
                    "cf:method=\"calPrice\"",
                    "cf:method=\"calPriceV2\"");

            // Hot-reload to V2 and execute
            adminService.deploy(ProcessSource.fromContent(code, v2Xml));
            ProcessResult<Map<String, Object>> r2 = engine.execute(ProcessSource.fromContent(code, v2Xml), context);
            assertTrue(r2.isSuccess());
            assertEquals(r2.getData().get("price"), 1800);

            // Verify that the generated code has also been updated
            String javaCodeV2 = toolingService.generateJavaCode(ProcessSource.fromContent(code, v2Xml));
            assertTrue(javaCodeV2.contains("calPriceV2"));
        }
    }

    @Nested
    @DisplayName("I. Error Handling and Edge Cases Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle complex nested structures with error recovery")
        void testComplexNestedStructureWithErrorRecovery() {
            String code = "bpmn20.compat.nested_subprocess";
            Map<String, Object> context = new HashMap<>();
            context.put("a", 100);
            context.put("b", 200);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess(), "Complex nested structure should execute successfully");
            assertNotNull(result.getData());
            assertEquals(300, result.getData().get("deepServiceResult"));
        }

    }

    @Nested
    @DisplayName("J. Performance and Scalability Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should handle large parallel gateway execution efficiently")
        void testLargeParallelGatewayPerformance() {
            String code = "bpmn20.gateway.parallel_gateway";
            int iterations = 100;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("a", i);
                context.put("b", i * 2);

                ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
                assertTrue(result.isSuccess(), "Parallel gateway should execute successfully in iteration " + i);
            }
            long endTime = System.currentTimeMillis();

            System.out.println("Executed " + iterations + " parallel gateway flows in " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 10000, "Performance should be acceptable for " + iterations + " iterations");
        }

        @Test
        @DisplayName("should handle deeply nested multi-instance loops efficiently")
        void testDeeplyNestedMultiInstancePerformance() {
            String code = "bpmn20.compat.nested_multi_instance";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Arrays.asList("A", "B", "C"));
            context.put("subList", Arrays.asList(1, 2, 3, 4, 5));

            long startTime = System.currentTimeMillis();
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            long endTime = System.currentTimeMillis();

            assertTrue(result.isSuccess(), "Nested multi-instance should execute successfully");
            System.out.println("Nested multi-instance execution time: " + (endTime - startTime) + "ms");
            assertTrue((endTime - startTime) < 5000, "Nested multi-instance should complete within reasonable time");
        }
    }

}
