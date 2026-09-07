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
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * XML parser for BPMN process elements.
 *
 * @author yusu
 */
public class ProcessParser extends AbstractBpmnElementParser<Process> {
    @Override
    protected Process doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        Process process = new Process();
        process.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        process.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        String executable = xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE);
        if (executable != null) {
            process.setExecutable(BpmnAttributeValues.parseBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE,
                    executable));
        }
        return process;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_PROCESS;
    }
}
