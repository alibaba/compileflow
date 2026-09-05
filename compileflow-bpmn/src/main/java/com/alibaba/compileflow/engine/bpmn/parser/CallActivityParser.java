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
import com.alibaba.compileflow.engine.bpmn.model.CallActivity;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * XML parser for BPMN call activities.
 *
 * @author yusu
 */
public class CallActivityParser extends AbstractBpmnElementParser<CallActivity> {
    @Override
    protected CallActivity doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        CallActivity callActivity = new CallActivity();
        callActivity.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        callActivity.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        callActivity.setCalledElement(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT));
        callActivity.setClasspath(xmlSource.getCfString(BpmnModelConstants.CF_ATTRIBUTE_CLASSPATH));
        callActivity.setVersion(xmlSource.getCfString(BpmnModelConstants.CF_ATTRIBUTE_VERSION));
        return callActivity;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_CALL_ACTIVITY;
    }
}
