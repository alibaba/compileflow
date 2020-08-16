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
import com.alibaba.compileflow.engine.definition.bpmn.FlowElement;
import com.alibaba.compileflow.engine.definition.bpmn.Process;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractBpmnElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class ProcessParser extends AbstractBpmnElementParser<Process> {

    @Override
    protected Process doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        Process process = new Process();
        process.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        process.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        process.setExecutable(xmlSource.getBoolean(BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE));
        return process;
    }

    @Override
    protected void attachChildElement(Element childElement, Process element, ParseContext parseContext) {
        if (childElement instanceof FlowElement) {
            element.addElement((FlowElement)childElement);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_PROCESS;
    }

}
