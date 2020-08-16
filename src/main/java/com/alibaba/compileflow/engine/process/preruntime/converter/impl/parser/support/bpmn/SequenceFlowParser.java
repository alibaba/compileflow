/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.BpmnModelConstants;
import com.alibaba.compileflow.engine.definition.bpmn.Expression;
import com.alibaba.compileflow.engine.definition.bpmn.SequenceFlow;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractBpmnElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class SequenceFlowParser extends AbstractBpmnElementParser<SequenceFlow> {

    @Override
    protected SequenceFlow doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        SequenceFlow sequenceFlow = new SequenceFlow();
        sequenceFlow.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        sequenceFlow.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        sequenceFlow.setSourceRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF));
        sequenceFlow.setTargetRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF));
        sequenceFlow.setImmediate(xmlSource.getBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_IS_IMMEDIATE));
        return sequenceFlow;
    }

    @Override
    protected void attachChildElement(Element childElement, SequenceFlow element, ParseContext parseContext) {
        if (childElement instanceof Expression) {
            element.setConditionExpression(((Expression)childElement).getValue());
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SEQUENCE_FLOW;
    }

}
