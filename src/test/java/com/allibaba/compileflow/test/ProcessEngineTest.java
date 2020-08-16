package com.allibaba.compileflow.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
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
@ContextConfiguration(
    locations = {"classpath:bean/common.xml", "classpath:bean/ktv.xml", "classpath:bean/orderFulfillment.xml"})
public class ProcessEngineTest {

    @Test
    public void testProcessEngine() {
        String code = "bpm.ktv.ktvExample";
        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
            TbbpmModel tbbpmModel = (TbbpmModel)processEngine.load(code);
            OutputStream outputStream = TbbpmModelConverter.getInstance().convertToStream(tbbpmModel);
            System.out.println(outputStream);
            System.out.println(processEngine.getTestCode(code));
            System.out.println(processEngine.start(code, context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testProcessEngineBpmn20() {
        String code = "bpmn20.ktv.ktvExample";
        Map<String, Object> context = new HashMap<>();
        List<String> pList = new ArrayList<>();
        pList.add("wuxiang");
        pList.add("yusu");
        context.put("pList", pList);
        try {
            ProcessEngine processEngine = ProcessEngineFactory.getStatelessProcessEngine(FlowModelType.BPMN);
            System.out.println(processEngine.start(code, context));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testTbbpmConvert() {
        String code = "bpm.ktv.ktvExample2";
        ProcessEngine processEngine = ProcessEngineFactory.getProcessEngine();
        TbbpmModel tbbpmModel = (TbbpmModel)processEngine.load(code);

        OutputStream outputStream = TbbpmModelConverter.getInstance().convertToStream(tbbpmModel);
        System.out.println(outputStream.toString());

        String srcCode = processEngine.getJavaCode(code);
        System.out.println(srcCode);
    }

}
