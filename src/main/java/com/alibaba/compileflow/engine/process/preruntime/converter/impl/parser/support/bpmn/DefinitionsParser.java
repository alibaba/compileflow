/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.BpmnModelConstants;
import com.alibaba.compileflow.engine.definition.bpmn.Definitions;
import com.alibaba.compileflow.engine.definition.bpmn.Process;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractBpmnElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class DefinitionsParser extends AbstractBpmnElementParser<Definitions> {

    @Override
    protected Definitions doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        Definitions definitions = new Definitions();
        definitions.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        definitions.setTargetNamespace(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE));
        definitions.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        definitions.setExpressionLanguage(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE));
        definitions.setTypeLanguage(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_TYPE_LANGUAGE));
        definitions.setExporter(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER));
        definitions.setExporterVersion(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER_VERSION));

        parseContext.setTop(definitions);
        return definitions;
    }

    @Override
    protected void attachChildElement(Element childElement, Definitions element, ParseContext parseContext) {
        if (childElement instanceof Process) {
            element.getProcesses().add((Process)childElement);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS;
    }

}
