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
import com.alibaba.compileflow.engine.definition.bpmn.SubProcess;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractBpmnElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class SubProcessParser extends AbstractBpmnElementParser<SubProcess> {

    @Override
    protected SubProcess doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        SubProcess subProcess = new SubProcess();
        subProcess.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        subProcess.setSubProcessCode(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_SUB_PROCESS_CODE));
        return subProcess;
    }

    @Override
    protected void attachChildElement(Element childElement, SubProcess element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SUB_PROCESS;
    }

}
