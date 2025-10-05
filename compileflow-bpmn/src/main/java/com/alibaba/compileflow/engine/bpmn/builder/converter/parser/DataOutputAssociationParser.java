package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.DataOutputAssociation;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class DataOutputAssociationParser extends AbstractBpmnElementParser<DataOutputAssociation> {
    @Override
    protected DataOutputAssociation doParse(XMLSource xmlSource, ParseContext parseContext) {
        DataOutputAssociation assoc = new DataOutputAssociation();
        assoc.setSourceRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF));
        assoc.setTargetRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF));
        return assoc;
    }

    @Override
    protected void attachChildElement(Element childElement, DataOutputAssociation element, ParseContext parseContext) {
        // Extension point for assignment/transformation, reserved
    }

    @Override
    public String getName() {
        return "dataOutputAssociation";
    }
}
