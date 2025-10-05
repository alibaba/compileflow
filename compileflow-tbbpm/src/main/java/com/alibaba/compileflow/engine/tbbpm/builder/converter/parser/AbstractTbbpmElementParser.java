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
package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser;

import com.alibaba.compileflow.engine.core.builder.converter.parser.AbstractFlowElementParser;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.action.HasAction;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.job.HasJob;
import com.alibaba.compileflow.engine.core.definition.job.JobPolicyElement;
import com.alibaba.compileflow.engine.core.definition.var.HasVar;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.tbbpm.definition.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.definition.Transition;

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
            ((FlowNode) element).addOutgoingTransition((Transition) childElement);
            return true;
        }
        if (element instanceof HasAction && childElement instanceof IAction) {
            ((HasAction) element).setAction((IAction) childElement);
            return true;
        }
        if (element instanceof HasJob && childElement instanceof JobPolicyElement) {
            ((HasJob) element).setJobPolicy((JobPolicyElement) childElement);
            return true;
        }
        if (element instanceof HasVar && childElement instanceof IVar) {
            ((HasVar) element).addVar((IVar) childElement);
            return true;
        }
        if (element instanceof NodeContainer && childElement instanceof FlowNode) {
            ((NodeContainer) element).addNode((FlowNode) childElement);
            return true;
        }
        return false;
    }

}
