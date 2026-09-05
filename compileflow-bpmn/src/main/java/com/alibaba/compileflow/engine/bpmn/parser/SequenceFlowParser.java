/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.Expression;
import com.alibaba.compileflow.engine.bpmn.model.SequenceFlow;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN sequence flows.
 *
 * @author yusu
 */
public class SequenceFlowParser extends AbstractBpmnElementParser<SequenceFlow> {
    @Override
    protected SequenceFlow doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        SequenceFlow sequenceFlow = new SequenceFlow();
        sequenceFlow.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        sequenceFlow.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        sequenceFlow.setSource(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF));
        sequenceFlow.setTarget(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF));
        String isImmediate = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_IS_IMMEDIATE);
        if (isImmediate != null
                && !BpmnAttributeValues.parseBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_IS_IMMEDIATE, isImmediate)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Executable BPMN sequenceFlow cannot declare isImmediate=\"false\", id=" + sequenceFlow.getId(),
                    null);
        }
        return sequenceFlow;
    }

    @Override
    protected void attachChildElement(Element childElement, SequenceFlow element, ParseContext parseContext) {
        if (childElement instanceof Expression) {
            if (element.getCondition() != null) {
                throw new IllegalArgumentException("A BPMN sequenceFlow must declare at most one conditionExpression");
            }
            element.setCondition(((Expression) childElement).getValue());
        } else {
            super.attachChildElement(childElement, element, parseContext);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SEQUENCE_FLOW;
    }
}
