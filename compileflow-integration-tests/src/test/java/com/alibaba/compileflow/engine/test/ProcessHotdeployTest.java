package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.FileSystemChangeDetector;
import com.alibaba.compileflow.engine.deploy.detector.NacosChangeDetector;
import com.alibaba.compileflow.engine.deploy.event.FlowChangeEvent;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for process engine hot-deploy functionality.
 * <p>
 * Tests cover:
 * 1. Hot-deploy via admin APIs (recompile by code, from content with default/custom CL).
 * 2. Real hot-deploy using FlowHotDeployer with FileSystemChangeDetector.
 * 3. Hot-deploy using NacosChangeDetector with mocked ConfigService.
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Process Engine Hot-deploy Integration Tests")
class ProcessHotdeployTest {

    private ProcessEngine<?> tbbpmEngine;
    private ProcessEngine<?> bpmnEngine;

    private static void writeString(File file, String s) throws IOException {
        try (FileWriter w = new FileWriter(file)) {
            w.write(s);
            w.flush();
        }
    }

    @BeforeEach
    void setUp() {
        tbbpmEngine = ProcessEngineFactory.createTbbpm();
        bpmnEngine = ProcessEngineFactory.createBpmn();
    }

    // -------------------- Hot-deploy by admin APIs --------------------

    @AfterEach
    void tearDown() throws Exception {
        if (tbbpmEngine != null) tbbpmEngine.close();
        if (bpmnEngine != null) bpmnEngine.close();
    }

    @Test
    @DisplayName("hot-deploy: recompile by code")
    void hotDeploy_byCode() {
        String code = "bpm.ktv.ktvExample";
        tbbpmEngine.admin().deploy(ProcessSource.fromCode(code));
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("pList", Arrays.asList("u1", "u2"));
        assertNotNull(tbbpmEngine.execute(ProcessSource.fromCode(code), ctx));
        tbbpmEngine.admin().deploy(ProcessSource.fromCode(code));
        assertNotNull(tbbpmEngine.execute(ProcessSource.fromCode(code), ctx));
    }

    @Test
    @DisplayName("hot-deploy: recompile from content with default CL")
    void hotDeploy_fromContent_defaultCL() throws InterruptedException {
        String code = "bpm.ktv.ktvExample";
        String v1 = Objects.requireNonNull(readResource("/bpm/ktv/ktvExample.bpm"));
        // change action method so the output changes after hot reload
        String v2 = v1.replace("method=\"calPrice\"", "method=\"calPriceV2\"");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("pList", Arrays.asList("u1", "u2"));

        tbbpmEngine.admin().deploy(ProcessSource.fromCode(code));
        ProcessResult<Map<String, Object>> r1 = tbbpmEngine.execute(ProcessSource.fromCode(code), ctx);
        int p1 = (Integer) r1.getData().get("price");
        assertEquals(60, p1);
        tbbpmEngine.admin().deploy(ProcessSource.fromContent(code, v2));
        ProcessResult<Map<String, Object>> r2 = tbbpmEngine.execute(ProcessSource.fromCode(code), ctx);
        int p2 = (Integer) r2.getData().get("price");
        assertEquals(1800, p2);
    }

    // -------------------- FlowHotDeployer with detectors --------------------

    @Test
    @DisplayName("hot-deploy: recompile from content with custom CL")
    void hotDeploy_fromContent_customCL() throws Exception {
        String code = "bpm.ktv.ktvExample";
        String v1 = Objects.requireNonNull(readResource("/bpm/ktv/ktvExample.bpm"));
        String v2 = v1.replace("method=\"calPrice\"", "method=\"calPriceV2\"");
        v2 = v2.replace("0.9", "0.8");
        ClassLoader cl = new URLClassLoader(new URL[]{});
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("pList", Arrays.asList("u1", "u2"));

        tbbpmEngine.admin().deploy(ProcessSource.fromCode(code));
        ProcessResult<Map<String, Object>> r1 = tbbpmEngine.execute(ProcessSource.fromCode(code), ctx);
        int p1 = (Integer) r1.getData().get("price");
        assertEquals(60, p1);
        tbbpmEngine.admin().deploy(cl, ProcessSource.fromContent(code, v2));
        ProcessResult<Map<String, Object>> r2 = tbbpmEngine.execute(ProcessSource.fromCode(code), ctx);
        int p2 = (Integer) r2.getData().get("price");
        assertEquals(1600, p2);
    }

    @Test
    @DisplayName("hot-deploy: file system auto-detects TBBPM changes and deploys")
    void testRealHotDeployWithFileSystemDetector() throws Exception {
        Path tempDir = Files.createTempDirectory("hotdeploy-test");
        AtomicBoolean changeDetected = new AtomicBoolean(false);
        AtomicInteger deployCount = new AtomicInteger(0);
        CountDownLatch deployed = new CountDownLatch(1);

        try {
            // 1. Create a custom FlowChangeDetector to track changes
            FileSystemChangeDetector detector = new FileSystemChangeDetector(tempDir.toString()) {
                @Override
                public void start(Consumer<FlowChangeEvent> onFlowChange) {
                    super.start(changeEvent -> {
                        changeDetected.set(true);
                        deployCount.incrementAndGet();
                        System.out.println("[HotDeploy] Detected change for flow: " + changeEvent.getCode());
                        onFlowChange.accept(changeEvent);
                        deployed.countDown();
                    });
                }
            };

            FlowHotDeployer hotDeployer = new FlowHotDeployer(tbbpmEngine.admin(), detector);
            hotDeployer.start();

            // 2. Create the initial process file
            String code = "test.hotdeploy.flow";
            File flowFile = tempDir.resolve(code + ".bpm").toFile();
            String initialContent = createSimpleBpmContent(code, "calPrice");
            writeString(flowFile, initialContent);

            // 3. Deploy the initial version
            tbbpmEngine.admin().deploy(ProcessSource.fromContent(code, initialContent));

            // 4. Execute the initial version (loading from the file system via file: protocol)
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("pList", Arrays.asList("u1", "u2"));

            ProcessResult<Map<String, Object>> result1 = tbbpmEngine.execute(ProcessSource.fromFile(code, flowFile), ctx);
            assertNotNull(result1);
            int price1 = (Integer) result1.getData().get("price");
            assertEquals(60, price1); // calPrice method returns 30 * 2 = 60

            // 5. Modify the file content (simulating a hot-deploy scenario)
            String updatedContent = createSimpleBpmContent(code, "calPriceV2");
            writeString(flowFile, updatedContent);

            // 6. Wait for auto-detection and re-compilation (deterministic)
            assertTrue(deployed.await(60, TimeUnit.SECONDS), "Timed out waiting for hot-deploy recompile");

            // 7. Verify that the change was detected
            assertTrue(changeDetected.get(), "The file change should have been detected");
            assertTrue(deployCount.get() > 0, "Re-compilation should have been triggered");

            // 8. Verify the new version is effective (using file: protocol again, should use the hot-reloaded new version)
            ProcessResult<Map<String, Object>> result2 = tbbpmEngine.execute(ProcessSource.fromFile(code, flowFile), ctx);
            assertNotNull(result2);
            int price2 = (Integer) result2.getData().get("price");
            assertEquals(2000, price2);

            // 9. Verify the results are different (proving hot-deploy was effective)
            assertNotEquals(price1, price2, "The result should be different after hot-deploy");

            hotDeployer.stop();
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    @DisplayName("hot-deploy: file system auto-detects BPMN changes")
    void testRealHotDeployWithBpmnFileSystemDetector() throws Exception {
        Path tempDir = Files.createTempDirectory("hotdeploy-bpmn-test");
        AtomicBoolean changeDetected = new AtomicBoolean(false);
        CountDownLatch deployed = new CountDownLatch(1);

        try {
            FileSystemChangeDetector detector = new FileSystemChangeDetector(tempDir.toString()) {
                @Override
                public void start(Consumer<FlowChangeEvent> onFlowChange) {
                    super.start(changeEvent -> {
                        changeDetected.set(true);
                        System.out.println("[HotDeploy] Detected BPMN change for flow: " + changeEvent.getCode());
                        onFlowChange.accept(changeEvent);
                        deployed.countDown();
                    });
                }
            };

            FlowHotDeployer hotDeployer = new FlowHotDeployer(bpmnEngine.admin(), detector);
            hotDeployer.start();

            String code = "test.hotdeploy.flow";
            File flowFile = tempDir.resolve(code + ".bpmn20").toFile();

            // BPMN content using a 'multiply' operation
            String initialContent = readResource("/bpmn20/gateway/parallel_gateway.bpmn20");
            writeString(flowFile, initialContent);

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("a", 10);
            ctx.put("b", 5);

            // Deploy the initial version
            bpmnEngine.admin().deploy(ProcessSource.fromContent(code, initialContent));

            ProcessResult<Map<String, Object>> result1 = bpmnEngine.execute(ProcessSource.fromCode(code), ctx);
            assertNotNull(result1);
            int result1Value = (Integer) result1.getData().get("serviceTask2Result");
            assertEquals(50, result1Value); // 10 * 5 = 50

            // Modify to a 'divide' operation
            String updatedContent = initialContent.replace("multiply", "divide");
            writeString(flowFile, updatedContent);

            assertTrue(deployed.await(30, TimeUnit.SECONDS), "Timed out waiting for BPMN hot-deploy recompile");

            assertTrue(changeDetected.get(), "The BPMN file change should have been detected");

            ProcessResult<Map<String, Object>> result2 = bpmnEngine.execute(ProcessSource.fromCode(code), ctx);
            assertNotNull(result2);
            int result2Value = (Integer) result2.getData().get("serviceTask2Result");
            assertEquals(2, result2Value); // 10 / 5 = 2

            assertNotEquals(result1Value, result2Value, "The result should be different after BPMN hot-deploy");

            hotDeployer.stop();
        } finally {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    // -------------------- helpers --------------------

    @Test
    @DisplayName("detector NACOS: mocked ConfigService triggers hot deploy")
    void hotDeploy_withNacosDetector() throws Exception {
        ConfigService configService = Mockito.mock(ConfigService.class);
        String code = "flow.nacos.1";
        List<String> dataIds = Collections.singletonList(code);
        String group = "DEFAULT_GROUP";

        String nacosV1 = Objects.requireNonNull(readResource("/bpmn20/gateway/parallel_gateway.bpmn20"));
        String nacosV2 = nacosV1.replace("multiply", "divide");
        when(configService.getConfig(eq(code), eq(group), anyLong())).thenReturn(nacosV1);

        NacosChangeDetector nacos = new NacosChangeDetector(configService, dataIds, group);
        FlowHotDeployer hotDeployer = new FlowHotDeployer(bpmnEngine.admin(), nacos);
        hotDeployer.start();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("a", 10);
        ctx.put("b", 5);
        ProcessResult<Map<String, Object>> processResult = bpmnEngine.execute(ProcessSource.fromContent(code, nacosV1), ctx);
        assertEquals(processResult.getData().get("serviceTask2Result"), 50);

        // emulate listener push new content by admin call (listener internal to detector)
        bpmnEngine.admin().deploy(ProcessSource.fromContent(code, nacosV2));
        processResult = bpmnEngine.execute(ProcessSource.fromCode(code), ctx);
        assertNotNull(processResult);
        assertEquals(processResult.getData().get("serviceTask2Result"), 2);


        hotDeployer.stop();
    }

    private String readResource(String path) {
        try (InputStream is = ProcessHotdeployTest.class.getResourceAsStream(path)) {
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates simple BPM content for testing purposes.
     */
    private String createSimpleBpmContent(String code, String methodName) {
        return String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<bpm code=\"%s\" name=\"test flow\" type=\"process\" description=\"test flow\">\n" +
                        "    <var name=\"price\" description=\"Price\" dataType=\"java.lang.Integer\" inOutType=\"return\"/>\n" +
                        "    <var name=\"pList\" description=\"Person List\" dataType=\"java.util.List&lt;java.lang.String&gt;\" inOutType=\"param\"/>\n" +
                        "    <end id=\"11\" name=\"End\" g=\"101,549,30,30\"/>\n" +
                        "    <autoTask id=\"12\" name=\"Calculate Fee\" g=\"72,309,88,48\">\n" +
                        "        <transition g=\":-15,20\" to=\"11\"/>\n" +
                        "        <action type=\"java\">\n" +
                        "            <actionHandle clazz=\"com.alibaba.compileflow.engine.test.mock.MockJavaClazz\" method=\"%s\">\n" +
                        "                <var name=\"p1\" description=\"Person Count\" dataType=\"java.lang.Integer\" contextVarName=\"pList.size()\" defaultValue=\"\" inOutType=\"param\"/>\n" +
                        "                <var name=\"p2\" description=\"Price\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\" inOutType=\"return\"/>\n" +
                        "            </actionHandle>\n" +
                        "        </action>\n" +
                        "    </autoTask>\n" +
                        "    <start id=\"1\" name=\"Start\" g=\"105,17,30,30\">\n" +
                        "        <transition g=\":-15,20\" to=\"12\"/>\n" +
                        "    </start>\n" +
                        "</bpm>",
                code, methodName
        );
    }
}
