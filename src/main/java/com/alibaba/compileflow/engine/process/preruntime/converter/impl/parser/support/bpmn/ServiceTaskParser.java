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
import com.alibaba.compileflow.engine.definition.bpmn.ServiceTask;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.action.impl.Action;
import com.alibaba.compileflow.engine.definition.common.action.impl.JavaActionHandle;
import com.alibaba.compileflow.engine.definition.common.action.impl.SpringBeanActionHandle;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractBpmnElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.constants.ActionType;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class ServiceTaskParser extends AbstractBpmnElementParser<ServiceTask> {

    @Override
    protected ServiceTask doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));
        serviceTask.setImplementation(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION));
        String type = xmlSource.getCfString(BpmnModelConstants.BPMN_ATTRIBUTE_TYPE);
        serviceTask.setType(type);

        Action action = new Action();
        action.setType(type);
        if (ActionType.SPRING_BEAN.getType().equals(type)) {
            SpringBeanActionHandle actionHandle = new SpringBeanActionHandle();
            actionHandle.setBean(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_BEAN));
            actionHandle.setClazz(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS));
            actionHandle.setMethod(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(serviceTask.getVars());
            action.setActionHandle(actionHandle);
        } else if (ActionType.JAVA.getType().equals(type)) {
            JavaActionHandle actionHandle = new JavaActionHandle();
            actionHandle.setClazz(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS));
            actionHandle.setMethod(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(serviceTask.getVars());
            action.setActionHandle(actionHandle);
        }

        serviceTask.setAction(action);
        return serviceTask;
    }

    @Override
    protected void attachChildElement(Element childElement, ServiceTask element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_SERVICE_TASK;
    }

}