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
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * XML parser for BPMN service tasks.
 *
 * @author yusu
 */
public class ServiceTaskParser extends AbstractBpmnElementParser<ServiceTask> {
    @Override
    protected ServiceTask doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        return serviceTask;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SERVICE_TASK;
    }
}
