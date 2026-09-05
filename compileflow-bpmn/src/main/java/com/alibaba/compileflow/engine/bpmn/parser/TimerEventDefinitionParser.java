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
import com.alibaba.compileflow.engine.bpmn.model.TimerEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for the minimal executable BPMN timer definition.
 *
 * @author yusu
 */
public final class TimerEventDefinitionParser extends AbstractBpmnElementParser<TimerEventDefinition> {
    @Override
    protected TimerEventDefinition doParse(XmlSource source, ParseContext context) {
        TimerEventDefinition definition = new TimerEventDefinition();
        definition.setId(source.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        return definition;
    }

    @Override
    protected void parseChildElements(XmlSource source, TimerEventDefinition definition, ParseContext context)
            throws Exception {
        super.parseChildElements(source, definition, context);
        if (definition.getValue() == null) {
            throw invalid("BPMN timerEventDefinition must declare exactly one timeDuration, timeDate, or timeCycle");
        }
    }

    @Override
    protected void attachChildElement(Element child, TimerEventDefinition definition, ParseContext context) {
        if (!(child instanceof TimerValue value)) {
            super.attachChildElement(child, definition, context);
            return;
        }
        if (definition.getValue() != null) {
            throw invalid("BPMN timerEventDefinition must declare exactly one timer value");
        }
        definition.setValue(value);
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_TIMER_EVENT_DEFINITION;
    }

    private static CompileFlowException invalid(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }
}
