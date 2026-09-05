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

import com.alibaba.compileflow.engine.bpmn.validation.BpmnLoopContract;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.LoopCondition;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN standard loop characteristics.
 *
 * @author yusu
 */
public class StandardLoopCharacteristicsParser extends AbstractBpmnElementParser<StandardLoopCharacteristics> {
    @Override
    protected StandardLoopCharacteristics doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        StandardLoopCharacteristics standardLoopCharacteristics = new StandardLoopCharacteristics();
        standardLoopCharacteristics.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        String testBefore = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TEST_BEFORE);
        if (testBefore != null) {
            standardLoopCharacteristics.setTestBefore(BpmnAttributeValues.parseBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_TEST_BEFORE,
                    testBefore));
        }
        String loopMaximum = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_MAXIMUM);
        if (loopMaximum != null) {
            standardLoopCharacteristics.setLoopMaximum(BpmnAttributeValues.parseInteger(BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_MAXIMUM,
                    loopMaximum));
        }
        return standardLoopCharacteristics;
    }

    @Override
    protected void parseChildElements(XmlSource xmlSource, StandardLoopCharacteristics element,
            ParseContext parseContext) throws Exception {
        super.parseChildElements(xmlSource, element, parseContext);
        BpmnLoopContract.validate(element);
    }

    @Override
    protected void attachChildElement(Element childElement, StandardLoopCharacteristics element,
            ParseContext parseContext) {
        if (childElement instanceof LoopCondition) {
            if (element.getLoopCondition() != null) {
                throw new IllegalArgumentException(
                        "BPMN standardLoopCharacteristics must declare at most one loopCondition");
            }
            element.setLoopCondition(((LoopCondition) childElement).getValue());
        } else {
            super.attachChildElement(childElement, element, parseContext);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_STANDARD_LOOP_CHARACTERISTICS;
    }
}
