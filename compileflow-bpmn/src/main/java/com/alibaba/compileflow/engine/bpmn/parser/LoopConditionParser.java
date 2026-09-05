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

import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.LoopCondition;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN loop conditions.
 *
 * @author yusu
 */
public class LoopConditionParser extends AbstractBpmnElementParser<LoopCondition> {
    @Override
    protected LoopCondition doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        LoopCondition loopCondition = new LoopCondition();
        loopCondition.setValue(BpmnJavaExpressionParser.parse(xmlSource, parseContext));
        return loopCondition;
    }

    @Override
    protected void attachChildElement(Element childElement, LoopCondition element, ParseContext parseContext) {}

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_LOOP_CONDITION;
    }
}
