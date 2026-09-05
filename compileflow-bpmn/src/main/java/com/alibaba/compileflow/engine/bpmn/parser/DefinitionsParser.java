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
import com.alibaba.compileflow.engine.bpmn.model.Definitions;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN definitions roots.
 *
 * @author yusu
 */
public class DefinitionsParser extends AbstractBpmnElementParser<Definitions> {
    @Override
    protected Definitions doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        Definitions definitions = new Definitions();
        definitions.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        definitions.setTargetNamespace(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE));
        definitions.setTypeLanguage(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TYPE_LANGUAGE));
        definitions.setExpressionLanguage(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE));

        parseContext.setTop(definitions);
        return definitions;
    }

    @Override
    protected void attachChildElement(Element childElement, Definitions element, ParseContext parseContext) {
        if (childElement instanceof Process) {
            element.getProcesses().add((Process) childElement);
        } else if (childElement instanceof Message) {
            element.getMessages().add((Message) childElement);
        } else {
            super.attachChildElement(childElement, element, parseContext);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS;
    }
}
