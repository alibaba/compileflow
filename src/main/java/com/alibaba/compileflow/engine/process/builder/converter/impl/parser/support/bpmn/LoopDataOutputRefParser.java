package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.LoopDataOutputRef;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

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
        // loopDataOutputRef 没有子元素
    }
    @Override
    public String getName() { return "loopDataOutputRef"; }
}
