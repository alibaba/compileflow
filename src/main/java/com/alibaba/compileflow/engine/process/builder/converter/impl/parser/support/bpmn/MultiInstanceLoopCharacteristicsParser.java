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
package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.BpmnModelConstants;
import com.alibaba.compileflow.engine.definition.bpmn.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractBpmnElementParser;

/**
 * @author yusu
 */
public class MultiInstanceLoopCharacteristicsParser extends AbstractBpmnElementParser<MultiInstanceLoopCharacteristics> {

    @Override
    protected MultiInstanceLoopCharacteristics doParse(XMLSource xmlSource, ParseContext parseContext)
            throws Exception {
        MultiInstanceLoopCharacteristics multiInstanceLoopCharacteristics = new MultiInstanceLoopCharacteristics();
        multiInstanceLoopCharacteristics.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        multiInstanceLoopCharacteristics.setTestBefore(xmlSource.getBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_TEST_BEFORE));
        multiInstanceLoopCharacteristics.setLoopCondition(
                xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_CONDITION));
        multiInstanceLoopCharacteristics.setLoopMaximum(xmlSource.getLong(BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_MAXIMUM));
        String isSequentialStr = xmlSource.getString("isSequential");
        multiInstanceLoopCharacteristics.setSequential("true".equalsIgnoreCase(isSequentialStr));


        multiInstanceLoopCharacteristics.setCollection(
                xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_COLLECTION));
        multiInstanceLoopCharacteristics.setElementVar(
                xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ELEMENT_VAR));
        multiInstanceLoopCharacteristics.setIndexVar(
                xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INDEX_VAR));
        multiInstanceLoopCharacteristics.setElementVarClass(
                xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ELEMENT_VAR_CLASS));
        return multiInstanceLoopCharacteristics;
    }

    @Override
    protected void attachChildElement(Element childElement, MultiInstanceLoopCharacteristics element,
                                      ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS;
    }

}
