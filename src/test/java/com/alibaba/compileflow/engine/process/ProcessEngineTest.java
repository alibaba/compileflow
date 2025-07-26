package com.alibaba.compileflow.engine.process;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.build.converter.impl.TbbpmModelConverter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yusu
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:bean/common.xml", "classpath:bean/ktv.xml", "classpath:bean/orderFulfillment.xml"
})
public class ProcessEngineTest {

    @Test
    public void testProcessEngine() {
        final String code = "bpm.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(processEngine.getJavaCode(code));
        System.out.println(processEngine.execute(code, context));
        Assert.assertNotNull(processEngine);
    }

    @Test
    public void testProcessEngineBpmn20() {
        final String code = "bpmn20.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);
        System.out.println(processEngine.execute(code, context));
        Assert.assertNotNull(processEngine);
    }

    @Test
    public void testTbbpmModelConvert() {
        final String code = "bpm.om.waitPaySuccessFlow";
        final ProcessEngine<TbbpmModel> processEngine = ProcessEngineFactory.getProcessEngine();
        final TbbpmModel tbbpmModel = processEngine.load(code);
        final OutputStream outputStream = TbbpmModelConverter.getInstance().convertToStream(tbbpmModel);
        System.out.println(outputStream.toString());
        final String srcCode = processEngine.getJavaCode(code);
        System.out.println(srcCode);
        Assert.assertNotNull(tbbpmModel);
    }

    @Test
    public void testWaitPayProcess() {
        String code = "bpm.om.waitPaySuccessFlow";
        String javaCode = ProcessEngineFactory.getProcessEngine().getJavaCode(code);
        System.out.println(javaCode);
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);
        ProcessEngineFactory.getProcessEngine().execute(code, context);
        Assert.assertNotNull(javaCode);
    }

    @Test
    public void testTriggerWaitPayProcess() {
        String code = "bpm.om.waitPaySuccessFlow";
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println("------receiver not real event------");
            System.out.println(processEngine.trigger(code, "randomEvent", null, context));
            System.out.println("------receiver real event------");
            System.out.println(processEngine.trigger(code, "PaymentPendingCallback", "PaymentPendingCallback", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testPaymentHandlingSubFlow() {
        String code = "bpm.om.paymentHandlingSubFlow";
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();

        System.out.println(ProcessEngineFactory.getProcessEngine().getJavaCode(code));

        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println(processEngine.trigger(code, "paymentHandlingSubFlow", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testStatefulProcessEngine() {
        String code = "bpm.om.generalOrderFulfillmentFlow";
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(ProcessEngineFactory.getProcessEngine().getJavaCode(code));
        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            System.out.println(processEngine.getJavaCode(code));
            System.out.println(processEngine.trigger(code, "PaymentPendingCallback", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testProcessEngineLoadFromContent() {
        final String code = "bpm.ktv.ktvExample";
        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        System.out.println(processEngine.execute(code, context, bpmXmlContent));
        Assert.assertNotNull(processEngine);
    }

//    @Test
//    public void testSampleBranchMergeFlow() {
//        final String code = "bpm.gateway.sampleBranchMerge";
//        final Map<String, Object> context = new HashMap<>();
//        context.put("route", "A");
//        context.put("next", "D");
//        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
//        Object result = processEngine.execute(code, context);
//        System.out.println(result);
//        Assert.assertNotNull(result);
//    }

    @Test
    public void testComplexBranchMergeFlow() {
        final String code = "bpm.gateway.complexBranchMerge";
        final Map<String, Object> context = new HashMap<>();
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testSimple4LevelGateway() {
        final String code = "bpm.gateway.simple4LevelGateway";
        final Map<String, Object> context = new HashMap<>();
        context.put("flag", true);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
    public void testNested4LevelGateway() {
        final String code = "bpm.gateway.nested4LevelGateway";
        final Map<String, Object> context = new HashMap<>();
        context.put("flag1", true);
        context.put("flag2", true);
        context.put("flag3", true);
        context.put("flag4", true);
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    @Test
        public void testUltraComplexStructure() {
        final String code = "bpm.gateway.ultraComplexStructure";
        final Map<String, Object> context = new HashMap<>();
        final ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        Object result = processEngine.execute(code, context);
        System.out.println(result);
        Assert.assertNotNull(result);
    }

    private final String bpmXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<bpm code=\"bpm.ktv.ktvExample\" name=\"ktv example\" type=\"process\" description=\"ktv example\">\n" +
            "    <var name=\"price\" description=\"支付价格\" dataType=\"java.lang.Integer\" inOutType=\"return\"/>\n" +
            "    <var name=\"totalPrice\" description=\"实付价\" dataType=\"java.lang.Integer\" inOutType=\"inner\"/>\n" +
            "    <var name=\"pList\" description=\"人员\" dataType=\"java.util.List&lt;java.lang.String&gt;\" inOutType=\"param\"/>\n" +
            "    <end id=\"11\" name=\"结束\" g=\"101,549,30,30\"/>\n" +
            "    <autoTask id=\"12\" name=\"付款\" g=\"72,469,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"11\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.process.mock.KtvService\" method=\"payMoney\">\n" +
            "                <var name=\"p1\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"param\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </autoTask>\n" +
            "    <scriptTask id=\"9\" name=\"原价\" g=\"132,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"price*1\">\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "    <loopProcess id=\"13\" name=\"循环节点\" g=\"20,75,198,190\" collectionVarName=\"pList\" variableName=\"p\" indexVarName=\"i\"\n" +
            "                 variableClass=\"java.lang.String\" startNodeId=\"13-1\" endNodeId=\"13-1\">\n" +
            "        <transition g=\":-15,20\" to=\"8\"/>\n" +
            "        <autoTask id=\"13-1\" name=\"每人唱一首歌\" g=\"70,95,88,48\">\n" +
            "            <action type=\"spring-bean\">\n" +
            "                <actionHandle bean=\"ktvService\" clazz=\"com.alibaba.compileflow.engine.process.mock.KtvService\" method=\"sing\">\n" +
            "                    <var name=\"p1\" description=\"\" dataType=\"java.lang.String\" contextVarName=\"p\" defaultValue=\"\"\n" +
            "                         inOutType=\"param\"/>\n" +
            "                </actionHandle>\n" +
            "            </action>\n" +
            "        </autoTask>\n" +
            "    </loopProcess>\n" +
            "    <decision id=\"8\" name=\"计算费用\" g=\"72,309,88,48\">\n" +
            "        <transition expression=\"\" name=\"不超过400\" priority=\"1\" g=\":-15,20\" to=\"9\"/>\n" +
            "        <transition expression=\"totalPrice&gt;=400\" name=\"超过400\" priority=\"10\" g=\":-15,20\" to=\"10\"/>\n" +
            "        <action type=\"java\">\n" +
            "            <actionHandle clazz=\"com.alibaba.compileflow.engine.process.mock.MockJavaClazz\" method=\"calPrice\">\n" +
            "                <var name=\"p1\" description=\"人数\" dataType=\"java.lang.Integer\" contextVarName=\"pList.size()\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"p2\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </decision>\n" +
            "    <start id=\"1\" name=\"开始\" g=\"105,17,30,30\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </start>\n" +
            "    <note id=\"14\" g=\"273,82,93,55\" comment=\"外框为循环节点\" visible=\"true\">\n" +
            "        <transition g=\":-15,20\" to=\"13\"/>\n" +
            "    </note>\n" +
            "    <scriptTask id=\"10\" name=\"9折优惠\" g=\"12,389,88,48\">\n" +
            "        <transition g=\":-15,20\" to=\"12\"/>\n" +
            "        <action type=\"ql\">\n" +
            "            <actionHandle expression=\"(round(price*0.9,0)).intValue()\">\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"totalPrice\"\n" +
            "                     defaultValue=\"\" inOutType=\"param\"/>\n" +
            "                <var name=\"price\" description=\"价格\" dataType=\"java.lang.Integer\" contextVarName=\"price\" defaultValue=\"\"\n" +
            "                     inOutType=\"return\"/>\n" +
            "            </actionHandle>\n" +
            "        </action>\n" +
            "    </scriptTask>\n" +
            "</bpm>";

}
