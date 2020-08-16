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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support;

import com.alibaba.compileflow.engine.definition.bpmn.Activity;
import com.alibaba.compileflow.engine.definition.bpmn.ExtensionElements;
import com.alibaba.compileflow.engine.definition.bpmn.FlowElement;
import com.alibaba.compileflow.engine.definition.bpmn.LoopCharacteristics;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.ElementContainer;
import com.alibaba.compileflow.engine.definition.common.HasVar;
import com.alibaba.compileflow.engine.definition.common.action.HasAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.extension.ExtensionElement;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.definition.common.var.impl.Var;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.BpmnElementParserProvider;

/**
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
        if (element instanceof HasVar && childElement instanceof ExtensionElements) {
            ((ExtensionElements)childElement).getExtensionElements().stream()
                .filter(extensionElement -> "var".equals(extensionElement.getName()))
                .map(this::buildExtensionVar)
                .forEach(((HasVar)element)::addVar);
            return false;
        }
        if (element instanceof ElementContainer && childElement instanceof FlowElement) {
            ((ElementContainer)element).addElement(childElement);
            return true;
        }
        if (element instanceof HasAction && childElement instanceof IAction) {
            ((HasAction)element).setAction((IAction)childElement);
            return true;
        }
        if (element instanceof Activity && childElement instanceof LoopCharacteristics) {
            ((Activity)element).setLoopCharacteristics((LoopCharacteristics)childElement);
            return true;
        }
        return false;
    }

    private IVar buildExtensionVar(ExtensionElement varElement) {
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

}
