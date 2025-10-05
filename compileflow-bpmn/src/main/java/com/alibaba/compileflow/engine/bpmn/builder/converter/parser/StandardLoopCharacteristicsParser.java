package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.LoopCondition;
import com.alibaba.compileflow.engine.bpmn.definition.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class StandardLoopCharacteristicsParser extends AbstractBpmnElementParser<StandardLoopCharacteristics> {

    @Override
    protected StandardLoopCharacteristics doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        StandardLoopCharacteristics standardLoopCharacteristics = new StandardLoopCharacteristics();
        // Parse attributes
        String testBefore = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TEST_BEFORE);
        if (testBefore != null) {
            standardLoopCharacteristics.setTestBefore(Boolean.valueOf(testBefore));
        }
        String loopMaximum = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_MAXIMUM);
        if (loopMaximum != null) {
            standardLoopCharacteristics.setLoopMaximum(Long.valueOf(loopMaximum));
        }
        // CF extension attributes can be parsed here if needed
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
