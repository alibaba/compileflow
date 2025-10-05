package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.LoopCondition;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class LoopConditionParser extends AbstractBpmnElementParser<LoopCondition> {
    @Override
    protected LoopCondition doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        LoopCondition loopCondition = new LoopCondition();
        String value = xmlSource.getElementText();
        if (value != null) {
            value = value.trim();
            if (value.startsWith("${") && value.endsWith("}")) {
                value = value.substring(2, value.length() - 1).trim();
            } else if (value.startsWith("#{") && value.endsWith("}")) {
                value = value.substring(2, value.length() - 1).trim();
            }
            loopCondition.setValue(value);
        }
        return loopCondition;
    }

    @Override
    protected void attachChildElement(Element childElement, LoopCondition element, ParseContext parseContext) {
    }

    @Override
    public String getName() {
        return "loopCondition";
    }
}
