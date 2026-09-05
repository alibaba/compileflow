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
import com.alibaba.compileflow.engine.bpmn.model.Script;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN script tasks.
 *
 * @author yusu
 */
public class ScriptTaskParser extends AbstractBpmnElementParser<ScriptTask> {
    @Override
    protected ScriptTask doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        ScriptTask scriptTask = new ScriptTask();
        scriptTask.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        scriptTask.setScriptFormat(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_SCRIPT_FORMAT));
        String execution = xmlSource.getCfString(BpmnModelConstants.CF_ATTRIBUTE_EXECUTION);
        if (execution != null) {
            scriptTask.setExecution(ActionExecution.of(execution));
        }
        return scriptTask;
    }

    @Override
    protected void attachChildElement(Element childElement, ScriptTask element, ParseContext parseContext) {
        if (childElement instanceof Script) {
            if (element.getScript() != null) {
                throw new IllegalArgumentException("A BPMN scriptTask must declare at most one script element");
            }
            element.setScript(((Script) childElement).getContent());
        } else {
            super.attachChildElement(childElement, element, parseContext);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SCRIPT_TASK;
    }
}
