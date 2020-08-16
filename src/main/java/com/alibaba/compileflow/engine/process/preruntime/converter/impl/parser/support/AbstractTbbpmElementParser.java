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

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.HasVar;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.action.HasAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.definition.tbbpm.FlowNode;
import com.alibaba.compileflow.engine.definition.tbbpm.Transition;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.TbbpmElementParserProvider;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmElementParser<E extends Element> extends AbstractFlowElementParser<E> {

    @Override
    public AbstractFlowElementParserProvider getParserProvider() {
        return TbbpmElementParserProvider.getInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext) {
        if (element instanceof FlowNode && childElement instanceof Transition) {
            ((FlowNode)element).addOutgoingTransition((Transition)childElement);
            return true;
        }
        if (element instanceof HasAction && childElement instanceof IAction) {
            ((HasAction)element).setAction((IAction)childElement);
            return true;
        }
        if (element instanceof HasVar && childElement instanceof IVar) {
            ((HasVar)element).addVar((IVar)childElement);
            return true;
        }
        if (element instanceof NodeContainer && childElement instanceof FlowNode) {
            ((NodeContainer)element).addNode((FlowNode)childElement);
            return true;
        }
        return false;
    }

}
