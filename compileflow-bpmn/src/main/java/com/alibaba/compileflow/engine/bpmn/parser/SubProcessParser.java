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
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * XML parser for BPMN sub-processes.
 *
 * @author yusu
 */
public class SubProcessParser extends AbstractBpmnElementParser<SubProcess> {
    @Override
    protected SubProcess doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        SubProcess subProcess = new SubProcess();
        subProcess.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        String triggeredByEvent = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TRIGGERED_BY_EVENT);
        if (triggeredByEvent != null
                && BpmnAttributeValues.parseBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_TRIGGERED_BY_EVENT,
                        triggeredByEvent)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "Event subprocesses are not supported", null);
        }
        return subProcess;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SUB_PROCESS;
    }
}
