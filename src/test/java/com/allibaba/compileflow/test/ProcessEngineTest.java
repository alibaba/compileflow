package com.allibaba.compileflow.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.StatefulProcessEngine;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.TbbpmModelConverter;
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
        System.out.println(processEngine.getTestCode(code));

        System.out.println(processEngine.execute(code, context));
    }

    @Test
    public void testProcessEngineBpmn20() {
        final String code = "bpmn20.ktv.ktvExample";

        final Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);

        final ProcessEngine processEngine = ProcessEngineFactory.getStatelessProcessEngine(FlowModelType.BPMN);
        System.out.println(processEngine.start(code, context));
    }

    @Test
    public void testTbbpmModelConvert() {
        final String code = "bpm.ktv.ktvExample";

        final ProcessEngine<TbbpmModel> processEngine = ProcessEngineFactory.getProcessEngine();

        final TbbpmModel tbbpmModel = processEngine.load(code);
        final OutputStream outputStream = TbbpmModelConverter.getInstance().convertToStream(tbbpmModel);
        System.out.println(outputStream.toString());

        final String srcCode = processEngine.getJavaCode(code);
        System.out.println(srcCode);
    }


    @Test
    public void testWaitPayProcess(){
        String code = "bpm.om.waitpaySuccessflow";
        System.out.println(ProcessEngineFactory.getProcessEngine().getJavaCode(code));
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);

        ProcessEngineFactory.getProcessEngine().execute(code,context);
    }

    @Test
    public void testTiggerWaitPayProcess() {
        String code = "bpm.om.waitpaySuccessflow";
        Map<String, Object> context = new HashMap<>();
        context.put("num", 100d);

        StatefulProcessEngine processEngine = ProcessEngineFactory.getStatefulProcessEngine();
        try {
//            System.out.println(processEngine.getJavaCode(code));
            System.out.println("------receiver not real event------");
            System.out.println(processEngine.trigger(code, "randomEvent", context));
            System.out.println("------receiver real event------");
            context.put("eventName","PaymentPendingCallback");
            System.out.println(processEngine.trigger(code, "PaymentPendingCallback", context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testStatefulProcessEngine() {
        String code = "bpm.om.generalOrderFulfillmentFlow";
        //String code = "bpm.route.uopOrderFullLinkRouteDecide";
        StatefulProcessEngine processEngine = ProcessEngineFactory.getStatefulProcessEngine();

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

}
