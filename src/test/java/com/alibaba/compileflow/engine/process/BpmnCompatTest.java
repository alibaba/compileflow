package com.alibaba.compileflow.engine.process;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.BpmnModel;
import com.alibaba.compileflow.engine.process.builder.converter.impl.BpmnModelConverter;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.FileFlowStreamSource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:bean/common.xml"
})
public class BpmnCompatTest {

    private static final String BASE = "src/test/resources/bpmn20/compat/";

    @Test
    public void testSimpleService() {
        testBpmnFile("simple_service.bpmn20", new HashMap<>());
    }

    @Test
    public void testExclusiveGatewayTrue() {
        Map<String, Object> context = new HashMap<>();
        context.put("flag", true);
        testBpmnFile("exclusive_gateway.bpmn20", context);
    }

    @Test
    public void testExclusiveGatewayFalse() {
        Map<String, Object> context = new HashMap<>();
        context.put("flag", false);
        testBpmnFile("exclusive_gateway.bpmn20", context);
    }

    @Test
    public void testParallelGateway() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 1);
        context.put("b", 2);
        testBpmnFile("parallel_gateway.bpmn20", context);
    }

    @Test
    public void testSubprocessSync() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 11);
        context.put("b", 22);
        testBpmnFile("subprocess_sync.bpmn20", context);
    }

    @Test
    public void testScriptTask() {
        testBpmnFile("script_task.bpmn20", new HashMap<>());
    }

    @Test
    public void testNestedSubprocess() {
        testBpmnFile("nested_subprocess.bpmn20", new HashMap<>());
    }

//    @Test
//    public void testBoundaryEventPlaceholder() {
//        testBpmnFile("boundary_event_placeholder.bpmn20", new HashMap<>());
//    }

    @Test
    public void testStandardLoop() {
        Map<String, Object> context = new HashMap<>();
        context.put("i", 0);
        context.put("msg", "hello");
        testBpmnFile("standard_loop.bpmn20", context);
    }

    @Test
    public void testMultiInstanceLoop() {
        Map<String, Object> context = new HashMap<>();
        context.put("pList", java.util.Arrays.asList("A", "B", "C"));
        testBpmnFile("multi_instance_loop.bpmn20", context);
    }

    @Test
    public void testNestedMultiInstance() {
        Map<String, Object> context = new HashMap<>();
        context.put("pList", java.util.Arrays.asList("A", "B"));
        context.put("subList", java.util.Arrays.asList(1, 2, 3));
        testBpmnFile("nested_multi_instance.bpmn20", context);
    }

    @Test
    public void testParallelGatewayMultiInstance() {
        Map<String, Object> context = new HashMap<>();
        context.put("pList", java.util.Arrays.asList("X", "Y"));
        context.put("subList", java.util.Arrays.asList(10, 20));
        testBpmnFile("parallel_gateway_multi_instance.bpmn20", context);
    }

    @Test
    public void testVariableScopeComplex() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 1);
        context.put("b", 2);
        context.put("sum", 0);
        testBpmnFile("variable_scope_complex.bpmn20", context);
    }

    private void testBpmnFile(String fileName, Map<String, Object> context) {
        String code = fileName.replace(".bpmn20", "");
        code = "bpmn20.compat." + code;
        File file = new File(BASE + fileName);
        Assert.assertTrue("File not found: " + file.getPath(), file.exists());
        // 1. 解析
        BpmnModel model = BpmnModelConverter.getInstance().convertToModel(FileFlowStreamSource.of(code, file));
        Assert.assertNotNull("Model parse failed: " + fileName, model);
        // 2. 建模断言（起止节点、节点数等）
        Assert.assertNotNull("No start node: " + fileName, model.getStartNode());
        Assert.assertNotNull("No end node: " + fileName, model.getEndNode());
        Assert.assertTrue("No nodes: " + fileName, model.getAllNodes().size() > 0);
        // 3. 代码生成
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);
        String javaCode = processEngine.getJavaCode(code);
        System.out.println(javaCode);
        Assert.assertNotNull("Codegen failed: " + fileName, javaCode);
        Assert.assertTrue("Codegen missing class: " + fileName, javaCode.contains("public class"));
        // 4. 执行（只断言不抛异常即可）
        try {
            processEngine.execute(code, context);
        } catch (Exception e) {
            Assert.fail("Execution failed for " + fileName + ": " + e.getMessage());
        }
    }
}
