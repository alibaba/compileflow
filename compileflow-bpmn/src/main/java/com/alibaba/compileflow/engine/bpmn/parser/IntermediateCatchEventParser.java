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
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.MessageEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.TimerEventDefinition;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for supported BPMN intermediate catch events.
 *
 * @author yusu
 */
public final class IntermediateCatchEventParser extends AbstractBpmnElementParser<IntermediateCatchEvent> {
    @Override
    protected IntermediateCatchEvent doParse(XmlSource source, ParseContext context) {
        IntermediateCatchEvent event = new IntermediateCatchEvent();
        event.setId(source.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        return event;
    }

    @Override
    protected void parseChildElements(XmlSource source, IntermediateCatchEvent event, ParseContext context)
            throws Exception {
        super.parseChildElements(source, event, context);
        if ((event.getMessageEventDefinition() == null) == (event.getTimerEventDefinition() == null)) {
            throw invalid(
                    "BPMN intermediateCatchEvent must declare exactly one messageEventDefinition"
                    + " or timerEventDefinition, id=" + event.getId());
        }
    }

    @Override
    protected void attachChildElement(Element child, IntermediateCatchEvent event, ParseContext context) {
        if (child instanceof MessageEventDefinition message) {
            if (event.getMessageEventDefinition() != null) {
                throw invalid("BPMN intermediateCatchEvent must declare at most one messageEventDefinition");
            }
            event.setMessageEventDefinition(message);
        } else if (child instanceof TimerEventDefinition timer) {
            if (event.getTimerEventDefinition() != null) {
                throw invalid("BPMN intermediateCatchEvent must declare at most one timerEventDefinition");
            }
            event.setTimerEventDefinition(timer);
        } else {
            super.attachChildElement(child, event, context);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_CATCH_EVENT;
    }

    private static CompileFlowException invalid(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }
}
