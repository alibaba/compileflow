package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.BpmnModelConstants;
import com.alibaba.compileflow.engine.definition.bpmn.DataObject;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class DataObjectParser extends AbstractBpmnElementParser<DataObject> {
    @Override
    protected DataObject doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        DataObject dataObject = new DataObject();
        dataObject.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        dataObject.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        String isCollection = xmlSource.getString("isCollection");
        dataObject.setCollection("true".equalsIgnoreCase(isCollection));
        return dataObject;
    }

    @Override
    protected void attachChildElement(Element childElement, DataObject element, ParseContext parseContext) {
        // No child elements expected for DataObject in this context
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_DATA_OBJECT;
    }
}
