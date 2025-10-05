package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.DataInputAssociation;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public class DataInputAssociationParser extends AbstractBpmnElementParser<DataInputAssociation> {
    @Override
    protected DataInputAssociation doParse(XMLSource xmlSource, ParseContext parseContext) {
        DataInputAssociation assoc = new DataInputAssociation();
        // The sourceRef attribute is optional in the BPMN 2.0 schema, but often points
        // to a data object or property that provides the input.
        assoc.setSourceRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF));
        assoc.setTargetRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF));
        return assoc;
    }

    @Override
    protected void attachChildElement(Element childElement, DataInputAssociation element, ParseContext parseContext) {
        // According to the BPMN 2.0 spec, DataInputAssociation can contain
        // 'assignment' or 'transformation' elements. Parsing for these
        // complex children would be implemented here if needed.
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_DATA_INPUT_ASSOCIATION;
    }
}
