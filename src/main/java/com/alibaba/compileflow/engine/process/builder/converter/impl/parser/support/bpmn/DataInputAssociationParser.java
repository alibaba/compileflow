package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.DataInputAssociation;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class DataInputAssociationParser extends AbstractBpmnElementParser<DataInputAssociation> {
    @Override
    protected DataInputAssociation doParse(XMLSource xmlSource, ParseContext parseContext) {
        DataInputAssociation assoc = new DataInputAssociation();
        assoc.setSourceRef(xmlSource.getString("sourceRef"));
        assoc.setTargetRef(xmlSource.getString("targetRef"));
        return assoc;
    }

    @Override
    protected void attachChildElement(Element childElement, DataInputAssociation element, ParseContext parseContext) {
        // 可扩展 assignment、transformation 等
    }

    @Override
    public String getName() {
        return "dataInputAssociation";
    }
}
