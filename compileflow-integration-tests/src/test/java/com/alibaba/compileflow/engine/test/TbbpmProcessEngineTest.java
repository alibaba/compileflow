package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.*;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the TBBPM process engine covering:
 * 1. Engine creation and configuration
 * 2. Core execution and tooling
 * 3. Stateful flows and triggers
 * 4. Gateway and branching logic
 * 5. Advanced architectural features
 * 6. Concurrency stress tests
 * <p>
 * Each test is designed to be independent and repeatable, ensuring a clean state.
 * <p>
 * Note: These tests assume the presence of certain mock classes (e.g., KtvService, MockJavaClazz)
 * in the classpath for action handling within the BPMN XML definitions.
 *
 * @author wuxiang
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("TBBPM Process Engine Comprehensive Integration Tests")
class TbbpmProcessEngineTest {

    private static final String bpmXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<bpm code=\"bpm.ktv.ktvExample\" name=\"KTV Example\" type=\"process\" description=\"A KTV process example\">\n" +
            "    <var name=\"price\" description=\"Payment Price\" dataType=\"java.lang.Integer\" inOutType=\"return\"/>\n" +
            "    <var name=\"totalPrice\" description=\"Actual Price\" dataType=\"java.lang.Integer\" inOutType=\"inner\"/>\n" +
            "    <var name=\"pList\" description=\"Person List\" dataType=\"java.util.List&lt;java.lang.String&gt;\" inOutType=\"param\"/>\n" +
            "    <end id=\"11\" name=\"End\" g=\"101,549,30,30\"/>\n" +
            "    <autoTask id=\"12\" name=\"Payment\" g=\"72,469,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"11\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.KtvService\" method=\"payMoney\">\n" +
            "                <var name=\"p1\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"param\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </autoTask>\n" +
            "    <scriptTask id=\"9\" name=\"Original Price\" g=\"132,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"price*1\">\n" +
            "                <var name=\"price\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "    <loopProcess id=\"13\" name=\"Loop Node\" g=\"20,75,198,190\" collectionVarName=\"pList\" variableName=\"p\" indexVarName=\"i\"\n" +
            "                 variableClass=\"java.lang.String\" startNodeId=\"13-1\" endNodeId=\"13-1\">\n" +
            "        <transition g=\":-15,20\" to=\"8\"/>\n" +
            "        <autoTask id=\"13-1\" name=\"Everyone Sings a Song\" g=\"70,95,88,48\">\n" +
            "            <action type=\"spring-bean\">\n" +
            "                <actionHandle bean=\"ktvService\" clazz=\"com.alibaba.compileflow.engine.test.mock.KtvService\" method=\"sing\">\n" +
            "                    <var name=\"p1\" description=\"\" dataType=\"java.lang.String\" contextVarName=\"p\" defaultValue=\"\"\n" +
            "                         inOutType=\"param\"/>\n" +
            "                </actionHandle>\n" +
            "            </action>\n" +
            "        </autoTask>\n" +
            "    </loopProcess>\n" +
            "    <decision id=\"8\" name=\"Calculate Fee\" g=\"72,309,88,48\">\n" +
            "        <transition expression=\"\" name=\"Not Over 400\" priority=\"1\" g=\":-15,20\" to=\"9\"/>\n" +
            "        <transition expression=\"totalPrice&gt;=400\" name=\"Over 400\" priority=\"10\" g=\":-15,20\" to=\"10\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"calPrice\">\n" +
            "                <var name=\"p1\" description=\"Person Count\" dataType=\"java.lang.Integer\" contextVarName=\"pList.size()\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"p2\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </decision>\n" +
            "    <start id=\"1\" name=\"Start\" g=\"105,17,30,30\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </start>\n" +
            "    <note id=\"14\" g=\"273,82,93,55\" comment=\"The outer frame is a loop node\" visible=\"true\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </note>\n" +
            "    <scriptTask id=\"10\" name=\"10% Off Discount\" g=\"12,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"(round(price*0.9,0)).intValue()\">\n" +
            "                <var name=\"price\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "</bpm>";
    private ProcessEngine<TbbpmModel> engine;
    private ProcessAdminService adminService;
    private ProcessToolingService<TbbpmModel> toolingService;

    @BeforeEach
    void setUp() {
        // Create a new, clean engine instance for each test to ensure isolation.
        engine = ProcessEngineFactory.createTbbpm();
        adminService = engine.admin();
        toolingService = engine.tooling();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    static class KtvRequest {
        public List<String> pList;

        public KtvRequest() {
        }
    }

    static class KtvResponse {
        public int price;

        public KtvResponse() {
        }
    }

    @Nested
    @DisplayName("1. Engine Creation and Configuration")
    class EngineLifecycleTests {

        @Test
        @DisplayName("should support convenient static factory methods")
        void testConvenientFactoryMethods() {
            // Direct convenient zero-config factory
            try (ProcessEngine<TbbpmModel> zeroConfig = ProcessEngineFactory.createTbbpm()) {
                assertNotNull(zeroConfig);
            }

            // Zero configuration
            try (ProcessEngine<TbbpmModel> defaultEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpm())) {
                assertNotNull(defaultEngine);
            }

            // Only set execution thread count — the most common scenario
            try (ProcessEngine<TbbpmModel> executionEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpm(8))) {
                assertNotNull(executionEngine);
            }

            try (ProcessEngine<TbbpmModel> customEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpmBuilder()
                            .parallelCompilation(true)
                            .threads(2, 2)
                            .classLoader(TbbpmProcessEngineTest.class.getClassLoader())
                            .build())) {
                assertNotNull(customEngine, "Engine should be built successfully");
            }

            // Using ExecutorConfig convenience builder
            try (ProcessEngine<TbbpmModel> configEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpm(ProcessExecutorConfig.of(
                            2, 4)))) {
                assertNotNull(configEngine);
            }

            // Convenient execution() on builder
            try (ProcessEngine<TbbpmModel> builderEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpmBuilder()
                            .executionThreads(6)  // adjust execution only; compilation uses defaults
                            .build())) {
                assertNotNull(builderEngine);
            }

            // Use enableParallelCompilation() convenience method (no-arg = true)
            try (ProcessEngine<TbbpmModel> parallelEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpmBuilder()
                            .parallelCompilation(true)  // explicit boolean for clarity
                            .executionThreads(4)
                            .build())) {
                assertNotNull(parallelEngine);
            }

            // Using generic builder(modelType) with all defaults
            try (ProcessEngine<TbbpmModel> genericBuilder = ProcessEngineFactory.create(
                    ProcessEngineConfig.builder(FlowModelType.TBBPM).build())) {
                assertNotNull(genericBuilder);
            }

            // Using explicit ExecutorConfig builder
            try (ProcessEngine<TbbpmModel> withExecutorConfigBuilder = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpmBuilder()
                            .executors(ProcessExecutorConfig.builder()
                                    .compilationThreads(1)
                                    .executionThreads(3)
                                    .build())
                            .build())) {
                assertNotNull(withExecutorConfigBuilder);
            }
        }
    }

    @Nested
    @DisplayName("2. Core Execution and Tooling")
    class CoreFunctionalityTests {

        @Test
        @DisplayName("should execute a KTV flow from cached code")
        void testKtvExampleFlow() {
            String code = "bpm.ktv.ktvExample";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("wuxiang", "yusu"));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertEquals(result.getData().get("price"), 60);
        }

        @Test
        @DisplayName("should execute a flow dynamically from XML content")
        void testExecuteFromContent() {
            String code = "bpm.ktv.ktvExample";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("wuxiang", "yusu"));

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromContent(code, bpmXmlContent), context);
            assertEquals(result.getData().get("price"), 60);
        }

        @Test
        @DisplayName("should load model and generate java code")
        void testModelLoadingAndCodeGeneration() {
            String code = "bpm.om.waitPaySuccessFlow";
            assertNotNull(toolingService.loadFlowModel(ProcessSource.fromCode(code)));
            String javaCode = toolingService.generateJavaCode(ProcessSource.fromCode(code));
            assertNotNull(javaCode);
            assertTrue(javaCode.contains("public class WaitPaySuccessFlow"));
        }
    }

    @Nested
    @DisplayName("3. Stateful Flows and Triggers")
    class StatefulAndTriggerTests {

        @Test
        @DisplayName("should trigger a waiting node in a stateful process")
        void testTriggerWaitPayProcess() {
            String code = "bpm.om.waitPaySuccessFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("num", 100d);

            ProcessResult<Map<String, Object>> result = engine.trigger(ProcessSource.fromCode(code),
                    "PaymentPendingCallback", "PaymentPendingCallback", context);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should trigger a sub-flow")
        void testPaymentHandlingSubFlow() {
            String code = "bpm.om.paymentHandlingSubFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("wuxiang", "yusu"));
            ProcessResult<Map<String, Object>> result = engine.trigger(ProcessSource.fromCode(code), "PaymentPendingCallback", null, context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should trigger a general stateful process")
        void testStatefulProcessEngine() {
            String code = "bpm.om.generalOrderFulfillmentFlow";
            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("wuxiang", "yusu"));
            ProcessResult<Map<String, Object>> result = engine.trigger(ProcessSource.fromCode(code), "PaymentPendingCallback", null, context);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("4. Gateway and Branching Logic")
    class GatewayAndBranchingTests {
        @Test
        @DisplayName("should handle simple branch merge logic")
        void testSampleBranchMergeFlow() {
            String code = "bpm.gateway.sampleBranchMerge";
            Map<String, Object> context = new HashMap<>();
            context.put("route", "A");
            context.put("next", "D");
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should handle complex branch merge logic")
        void testComplexBranchMergeFlow() {
            String code = "bpm.gateway.complexBranchMerge";
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), new HashMap<>());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should handle simple 4-level gateway logic")
        void testSimple4LevelGateway() {
            String code = "bpm.gateway.simple4LevelGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("flag", true);
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should handle nested 4-level gateway logic")
        void testNested4LevelGateway() {
            String code = "bpm.gateway.nested4LevelGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("flag1", true);
            context.put("flag2", true);
            context.put("flag3", true);
            context.put("flag4", true);
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should handle ultra complex structure")
        void testUltraComplexStructure() {
            String code = "bpm.gateway.ultraComplexStructure";
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), new HashMap<>());
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should execute a simple parallel gateway")
        void testParallelGateway() {
            String code = "bpm.gateway.simpleParallelGateway";
            Map<String, Object> context = new HashMap<>();
            context.put("a", 10);
            context.put("b", 200);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("5. Advanced Architectural Features")
    class AdvancedFeatureTests {
        @Test
        @DisplayName("should execute a flow with strongly-typed DTOs for type safety")
        void testExecuteWithTypedDto() {
            String code = "bpm.ktv.ktvExample";
            KtvRequest request = new KtvRequest();
            request.pList = Lists.newArrayList("user1", "user2", "user3");

            ProcessResult<KtvResponse> result = engine.execute(ProcessSource.fromCode(code), request, KtvResponse.class);

            assertTrue(result.isSuccess(), "Type-safe execution should be successful");
            KtvResponse response = result.getData();
            assertNotNull(response, "Typed response data should not be null");
            assertTrue(response.price > 0, "Price should be calculated and positive");
        }

        @Test
        @DisplayName("should hot-reload a flow successfully with new content")
        void testHotReloadFlow() {
            String code = "bpm.ktv.ktvExample";
            String contentV1 = bpmXmlContent;

            Map<String, Object> context = new HashMap<>();
            context.put("pList", Lists.newArrayList("wuxiang", "yusu"));

            try {
                adminService.deploy(ProcessSource.fromContent(code, contentV1));
            } catch (Exception e) {
                fail("Should not throw exception");
            }
            ProcessResult<Map<String, Object>> processResult = engine.execute(ProcessSource.fromContent(code, contentV1), context);
            assertEquals(processResult.getData().get("price"), 60);

            String contentV2 = bpmXmlContent.replace("<actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"calPrice\">",
                    "<actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"calPriceV2\">");
            try {
                adminService.deploy(ProcessSource.fromContent(code, contentV2));
            } catch (Exception e) {
                fail("Should not throw exception");
            }
            processResult = engine.execute(ProcessSource.fromContent(code, contentV2), context);
            assertEquals(processResult.getData().get("price"), 1800);
        }

        @Test
        @DisplayName("should use custom ClassLoader to find flow dependencies")
        void testEngineUsesCustomClassLoader() throws Exception {
            Path tempDir = Files.createTempDirectory("compileflow-cl-test-");
            try {
                String sourceCode = "package com.mycompany.service; " +
                        "public class MyService {" +
                        "    public int calPrice(int num) {\n" +
                        "        System.out.println(\"total price: \" + 100 * num);\n" +
                        "        return 100 * num;\n" +
                        "    }" +
                        "}";
                File packageDir = new File(tempDir.toFile(), "com/mycompany/service");
                assertTrue(packageDir.mkdirs(), "Package directory structure should be created");
                File sourceFile = new File(packageDir, "MyService.java");
                try (FileWriter writer = new FileWriter(sourceFile)) {
                    writer.write(sourceCode);
                }

                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                assertEquals(0, compiler.run(null, null, null, sourceFile.getPath()));

                ClassLoader customClassLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});

                String flowCode = "bpm.test.classloaderFlow";
                String flowContent = bpmXmlContent.replace("bpm.ktv.ktvExample", flowCode);
                flowContent = flowContent.replace("<actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"calPrice\">",
                        "<actionHandle clazz=\"com.mycompany.service.MyService\" method=\"calPrice\">");

                try (ProcessEngine<TbbpmModel> customEngine = ProcessEngineFactory.create(
                        ProcessEngineConfig.tbbpmBuilder().classLoader(customClassLoader).build())) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("pList", Lists.newArrayList("wuxiang", "yusu"));
                    ProcessResult<Map<String, Object>> result = customEngine.execute(ProcessSource.fromContent(flowCode, flowContent), context);
                    assertTrue(result.isSuccess());
                }
            } finally {
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Nested
    @DisplayName("6. Concurrency Stress Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle concurrent execution of a single flow")
        void testConcurrentExecutionOfOneFlow() throws Exception {
            String code = "bpm.ktv.ktvExample";
            int threadCount = 20;
            int executionsPerThread = 10;
            adminService.deploy(ProcessSource.fromCode(code));
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Callable<Boolean>> tasks = new ArrayList<>();

            for (int i = 0; i < threadCount * executionsPerThread; i++) {
                tasks.add(() -> {
                    Map<String, Object> context = new HashMap<>();
                    context.put("pList", Lists.newArrayList("userA", "userB"));
                    ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(code), context);
                    return result.isSuccess() && result.getData().containsKey("price");
                });
            }

            List<Future<Boolean>> futures = executor.invokeAll(tasks, 1, TimeUnit.MINUTES);
            executor.shutdown();
            long successCount = futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();
            assertEquals(threadCount * executionsPerThread, successCount, "All concurrent executions should succeed");
        }

        @Test
        @DisplayName("should handle concurrent compilation of multiple flows from content")
        void testConcurrentCompilation() {
            // 1. Arrange: Prepare test data
            int flowCount = 50;
            // Create a list of ProcessSource instead of a map
            List<ProcessSource> sources = new ArrayList<>();
            for (int i = 0; i < flowCount; i++) {
                String code = "flow.concurrent.compile." + i;
                String content = bpmXmlContent.replace("bpm.ktv.ktvExample", code);
                // Use ProcessSource.fromContent() to clearly represent the flow source
                sources.add(ProcessSource.fromContent(code, content));
            }

            // 2. Act & Assert: Execute using a concurrency-enabled engine instance
            try (ProcessEngine<TbbpmModel> concurrentEngine = ProcessEngineFactory.create(
                    ProcessEngineConfig.tbbpmBuilder().parallelCompilation(true).build())) {
                // Use assertTimeout to ensure concurrent execution completes within the expected time
                assertTimeout(Duration.ofSeconds(30), () -> {
                    // Call the precompile method that accepts a vararg of ProcessSource
                    concurrentEngine.admin().deploy(sources.toArray(new ProcessSource[0]));
                }, "Concurrent compilation should be fast and complete without errors.");
            }
        }

        @Test
        @DisplayName("[Original] should handle concurrent execution with different flows")
        void testConcurrentExecutionWithDifferentFlows() throws Exception {
            String[] flowCodes = {"bpm.ktv.ktvExample", "bpm.gateway.simple4LevelGateway", "bpm.gateway.complexBranchMerge"};
            int threadCount = 15;
            int executionsPerThread = 5;
            adminService.deploy(Arrays.stream(flowCodes).map(ProcessSource::fromCode).toArray(ProcessSource[]::new));
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount * executionsPerThread; i++) {
                String flowCode = flowCodes[i % flowCodes.length];
                tasks.add(() -> {
                    Map<String, Object> context = new HashMap<>();
                    if (flowCode.contains("ktv")) {
                        context.put("pList", Lists.newArrayList("user" + Thread.currentThread().getId()));
                    } else if (flowCode.contains("gateway")) {
                        context.put("flag", ThreadLocalRandom.current().nextBoolean());
                    }
                    ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode(flowCode), context);
                    return result.isSuccess();
                });
            }
            List<Future<Boolean>> futures = executor.invokeAll(tasks, 1, TimeUnit.MINUTES);
            executor.shutdown();
            long successCount = futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();
            assertEquals(threadCount * executionsPerThread, successCount);
        }
    }

}
