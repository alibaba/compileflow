package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.LoopDataInputRef;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class LoopDataInputRefParser extends AbstractBpmnElementParser<LoopDataInputRef> {
    @Override
    protected LoopDataInputRef doParse(XMLSource xmlSource, ParseContext parseContext) {
        LoopDataInputRef ref = new LoopDataInputRef();
        ref.setValue(xmlSource.getElementText());
        return ref;
    }

    @Override
    protected void attachChildElement(Element childElement, LoopDataInputRef element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return "loopDataInputRef";
    }
}
