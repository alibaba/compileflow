package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.LoopDataInputRef;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

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
