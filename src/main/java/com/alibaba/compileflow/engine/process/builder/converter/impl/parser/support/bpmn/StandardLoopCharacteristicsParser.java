package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.BpmnModelConstants;
import com.alibaba.compileflow.engine.definition.bpmn.LoopCondition;
import com.alibaba.compileflow.engine.definition.bpmn.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class StandardLoopCharacteristicsParser extends AbstractBpmnElementParser<StandardLoopCharacteristics> {

    @Override
    protected StandardLoopCharacteristics doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        StandardLoopCharacteristics standardLoopCharacteristics = new StandardLoopCharacteristics();
        // 解析属性
        String testBefore = xmlSource.getString("testBefore");
        if (testBefore != null) {
            standardLoopCharacteristics.setTestBefore(Boolean.valueOf(testBefore));
        }
        String loopMaximum = xmlSource.getString("loopMaximum");
        if (loopMaximum != null) {
            standardLoopCharacteristics.setLoopMaximum(Long.valueOf(loopMaximum));
        }
        // 兼容cf扩展属性（如有需要可补充）
        return standardLoopCharacteristics;
    }

    @Override
    protected void attachChildElement(Element childElement, StandardLoopCharacteristics element,
                                      ParseContext parseContext) {
        if (childElement instanceof LoopCondition) {
            element.setLoopCondition(((LoopCondition) childElement).getValue());
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_STANDARD_LOOP_CHARACTERISTICS;
    }

}
