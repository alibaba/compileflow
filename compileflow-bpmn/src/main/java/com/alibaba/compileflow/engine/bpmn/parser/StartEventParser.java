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
import com.alibaba.compileflow.engine.bpmn.model.StartEvent;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * XML parser for BPMN start events.
 *
 * @author yusu
 */
public class StartEventParser extends AbstractBpmnElementParser<StartEvent> {
    @Override
    protected StartEvent doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        StartEvent startEvent = new StartEvent();
        startEvent.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        String isInterrupting = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_IS_INTERRUPTING);
        if (isInterrupting != null
                && !BpmnAttributeValues.parseBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_IS_INTERRUPTING, isInterrupting)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Non-interrupting start events require an event subprocess, which CompileFlow does not support",
                    null);
        }
        return startEvent;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_START_EVENT;
    }
}
