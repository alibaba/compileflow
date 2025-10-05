package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.LoopDataOutputRef;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class LoopDataOutputRefParser extends AbstractBpmnElementParser<LoopDataOutputRef> {
    @Override
    protected LoopDataOutputRef doParse(XMLSource xmlSource, ParseContext parseContext) {
        LoopDataOutputRef ref = new LoopDataOutputRef();
        ref.setValue(xmlSource.getElementText());
        return ref;
    }

    @Override
    protected void attachChildElement(Element childElement, LoopDataOutputRef element, ParseContext parseContext) {
        // loopDataOutputRef has no child elements
    }

    @Override
    public String getName() {
        return "loopDataOutputRef";
    }
}
