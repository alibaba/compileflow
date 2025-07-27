package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.InputDataItem;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class InputDataItemParser extends AbstractBpmnElementParser<InputDataItem> {
    @Override
    protected InputDataItem doParse(XMLSource xmlSource, ParseContext parseContext) {
        InputDataItem item = new InputDataItem();
        item.setId(xmlSource.getString("id"));
        return item;
    }
    @Override
    protected void attachChildElement(Element childElement, InputDataItem element, ParseContext parseContext) {
        // inputDataItem 没有子元素
    }
    @Override
    public String getName() { return "inputDataItem"; }
}
