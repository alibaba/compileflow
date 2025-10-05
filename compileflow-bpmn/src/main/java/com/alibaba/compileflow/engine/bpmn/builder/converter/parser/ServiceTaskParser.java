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
package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.ServiceTask;
import com.alibaba.compileflow.engine.core.builder.constants.ActionType;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.impl.Action;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaActionHandle;
import com.alibaba.compileflow.engine.core.definition.action.impl.SpringBeanActionHandle;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wuxiang
 * @author yusu
 */
public class ServiceTaskParser extends AbstractBpmnElementParser<ServiceTask> {

    @Override
    protected ServiceTask doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_ID));

        String bean = xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_BEAN);
        if (StringUtils.isNotEmpty(bean)) {
            Action action = new Action();
            action.setType(ActionType.SPRING_BEAN.getValue());
            SpringBeanActionHandle actionHandle = new SpringBeanActionHandle();
            actionHandle.setBean(bean);
            actionHandle.setClazz(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS));
            actionHandle.setMethod(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(serviceTask.getVars());
            action.setActionHandle(actionHandle);
            serviceTask.setAction(action);
            return serviceTask;
        }

        String clazz = xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS);
        if (StringUtils.isNotEmpty(clazz)) {
            Action action = new Action();
            action.setType(ActionType.JAVA.getValue());
            JavaActionHandle actionHandle = new JavaActionHandle();
            actionHandle.setClazz(clazz);
            actionHandle.setMethod(xmlSource.getCfString(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(serviceTask.getVars());
            action.setActionHandle(actionHandle);
            serviceTask.setAction(action);
            return serviceTask;
        }

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
