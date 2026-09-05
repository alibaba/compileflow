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
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import org.apache.commons.lang3.StringUtils;

/**
 * XML parser for BPMN receive tasks.
 *
 * @author yusu
 */
public class ReceiveTaskParser extends AbstractBpmnElementParser<ReceiveTask> {
    @Override
    protected ReceiveTask doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        ReceiveTask receiveTask = new ReceiveTask();
        receiveTask.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        receiveTask.setMessageRef(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF));
        String implementation = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION);
        String operationRef = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_OPERATION_REF);
        if (StringUtils.isNotBlank(implementation) || StringUtils.isNotBlank(operationRef)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "BPMN receiveTask implementation and operationRef are not"
                    + " supported; CompileFlow trigger entries route by messageRef", null);
        }
        return receiveTask;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_RECEIVE_TASK;
    }
}
