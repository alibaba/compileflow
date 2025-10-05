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


import com.alibaba.compileflow.engine.bpmn.definition.Activity;
import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.ExtensionElements;
import com.alibaba.compileflow.engine.bpmn.definition.LoopCharacteristics;
import com.alibaba.compileflow.engine.core.builder.constants.ActionType;
import com.alibaba.compileflow.engine.core.builder.converter.parser.AbstractFlowElementParser;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.definition.BaseFlowElement;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.ElementContainer;
import com.alibaba.compileflow.engine.core.definition.action.HasAction;
import com.alibaba.compileflow.engine.core.definition.action.HasInOutAction;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.impl.Action;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaActionHandle;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaSourceActionHandle;
import com.alibaba.compileflow.engine.core.definition.action.impl.SpringBeanActionHandle;
import com.alibaba.compileflow.engine.core.definition.extension.ExtensionElement;
import com.alibaba.compileflow.engine.core.definition.job.HasJob;
import com.alibaba.compileflow.engine.core.definition.job.JobPolicy;
import com.alibaba.compileflow.engine.core.definition.job.JobPolicyElement;
import com.alibaba.compileflow.engine.core.definition.var.HasVar;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.definition.var.Var;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The abstract base class for all BPMN-specific XML element parsers.
 * <p>
 * This class provides common logic for parsing and attaching standard BPMN constructs
 * that are defined as child XML elements. It is responsible for handling:
 * <ul>
 *   <li><b>Extension Elements:</b> Parsing custom variables, actions, and job policies
 *       from CompileFlow's extension namespace.</li>
 *   <li><b>Flow Elements:</b> Adding child flow elements (e.g., tasks, gateways) to
 *       their parent containers (e.g., a SubProcess).</li>
 *   <li><b>Loop Characteristics:</b> Attaching looping behavior to activities.</li>
 * </ul>
 * Subclasses are expected to implement the logic for parsing the attributes of their
 * specific BPMN element and any other unique child elements.
 *
 * @param <E> The type of the {@link Element} that this parser handles.
 * @author yusu
 */
public abstract class AbstractBpmnElementParser<E extends Element> extends AbstractFlowElementParser<E> {

    @Override
    public AbstractFlowElementParserProvider getParserProvider() {
        return BpmnElementParserProvider.getInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext) {
        if (childElement instanceof ExtensionElements) {
            // Extension elements are handled here but do not count as a "real" attachment,
            // as they are containers for other definitions. Return false to allow further processing.
            handleExtensionElements((ExtensionElements) childElement, element);
            return false;
        }
        if (element instanceof ElementContainer && childElement instanceof BaseFlowElement) {
            ((ElementContainer) element).addElement(childElement);
            return true;
        }
        if (element instanceof HasAction && childElement instanceof IAction) {
            ((HasAction) element).setAction((IAction) childElement);
            return true;
        }
        if (element instanceof Activity && childElement instanceof LoopCharacteristics) {
            ((Activity) element).setLoopCharacteristics((LoopCharacteristics) childElement);
            return true;
        }
        return false;
    }

    private void handleExtensionElements(ExtensionElements extensionElements, E element) {
        if (element instanceof HasVar) {
            extensionElements.getExtensionElements().stream()
                    .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_VAR.equals(ext.getName()))
                    .map(this::buildVar)
                    .forEach(((HasVar) element)::addVar);
        }
        if (element instanceof HasJob) {
            extensionElements.getExtensionElements().stream()
                    .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_JOB_POLICY.equals(ext.getName()))
                    .findFirst()
                    .map(this::buildJobPolicy)
                    .ifPresent(((HasJob) element)::setJobPolicy);
        }
        if (element instanceof HasAction) {
            extensionElements.getExtensionElements().stream()
                    .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION.equals(ext.getName()))
                    .findFirst()
                    .map(this::buildAction)
                    .ifPresent(((HasAction) element)::setAction);
        }
        if (element instanceof HasInOutAction) {
            extensionElements.getExtensionElements().stream()
                    .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_IN_ACTION.equals(ext.getName()))
                    .findFirst()
                    .map(this::buildAction)
                    .ifPresent(((HasInOutAction) element)::setInAction);
            extensionElements.getExtensionElements().stream()
                    .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_OUT_ACTION.equals(ext.getName()))
                    .findFirst()
                    .map(this::buildAction)
                    .ifPresent(((HasInOutAction) element)::setOutAction);
        }
    }

    private JobPolicyElement buildJobPolicy(ExtensionElement extensionElement) {
        JobPolicy jobPolicy = new JobPolicy();
        jobPolicy.setTimeout(extensionElement.getAttributeValueOrDefault(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_JOB_TIMEOUT, "PT0S"));
        jobPolicy.setRetry(extensionElement.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_JOB_RETRY, "R0/PT0S"));
        jobPolicy.setRetryOn(extensionElement.getAttributeValueOrDefault(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_JOB_RETRY_ON, "never"));
        jobPolicy.setOnFailure(extensionElement.getAttributeValueOrDefault(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_JOB_ON_FAILURE, "incident"));
        return jobPolicy;
    }

    private IVar buildVar(ExtensionElement varElement) {
        Var var = new Var();
        var.setId(varElement.getAttributeValue("id"));
        var.setName(varElement.getAttributeValue("name"));
        var.setDataType(varElement.getAttributeValue("dataType"));
        var.setDescription(varElement.getAttributeValue("description"));
        var.setContextVarName(varElement.getAttributeValue("contextVarName"));
        var.setDefaultValue(varElement.getAttributeValue("defaultValue"));
        var.setInOutType(varElement.getAttributeValue("inOutType"));
        return var;
    }

    private IAction buildAction(ExtensionElement extensionElement) {
        Action action = new Action();
        String actionType = extensionElement.getAttributeValue("type");
        action.setType(actionType);
        ExtensionElement actionHandleExtension = extensionElement.getChildElements("actionHandle").stream().findFirst().orElse(null);
        if (actionHandleExtension == null) {
            return action;
        }

        List<IVar> vars = actionHandleExtension.getChildElements("var").stream()
                .map(this::buildVar).collect(Collectors.toList());
        if (ActionType.SPRING_BEAN.getValue().equals(actionType)) {
            SpringBeanActionHandle actionHandle = new SpringBeanActionHandle();
            actionHandle.setBean(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_BEAN));
            actionHandle.setClazz(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS));
            actionHandle.setMethod(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(vars);
            action.setActionHandle(actionHandle);
        } else if (ActionType.JAVA.getValue().equals(actionType)) {
            JavaActionHandle actionHandle = new JavaActionHandle();
            actionHandle.setClazz(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CLASS));
            actionHandle.setMethod(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setVars(vars);
            action.setActionHandle(actionHandle);
        } else if (ActionType.JAVA_SOURCE.getValue().equals(actionType)) {
            JavaSourceActionHandle actionHandle = new JavaSourceActionHandle();
            actionHandle.setMethod(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_METHOD));
            actionHandle.setCode(actionHandleExtension.getAttributeValue(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE));
            actionHandle.setVars(vars);
            action.setActionHandle(actionHandle);
        }

        return action;
    }

}
