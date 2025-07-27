package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.OutputDataItem;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class OutputDataItemParser extends AbstractBpmnElementParser<OutputDataItem> {
    @Override
    protected OutputDataItem doParse(XMLSource xmlSource, ParseContext parseContext) {
        OutputDataItem item = new OutputDataItem();
        item.setId(xmlSource.getString("id"));
        return item;
    }
    @Override
    protected void attachChildElement(Element childElement, OutputDataItem element, ParseContext parseContext) {
        // outputDataItem 没有子元素
    }
    @Override
    public String getName() { return "outputDataItem"; }
}
