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
package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.impl.BpmnNodeGeneratorProvider;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmnStatelessProcessRuntime extends AbstractStatelessProcessRuntime<BpmnModel> {

    private BpmnStatelessProcessRuntime(BpmnModel bpmnModel) {
        super(bpmnModel);
    }

    public static BpmnStatelessProcessRuntime of(BpmnModel bpmnModel) {
        return new BpmnStatelessProcessRuntime(bpmnModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    protected boolean isBpmn20() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {

        for (TransitionNode node : nodeContainer.getAllNodes()) {
            if (node instanceof ServiceTask) {
                registerGenerator(node, new ServiceTaskGenerator(this, (ServiceTask)node));
            } else if (node instanceof ScriptTask) {
                registerGenerator(node, new ScriptTaskGenerator(this, (ScriptTask)node));
            } else if (node instanceof ExclusiveGateway) {
                registerGenerator(node, new ExclusiveGatewayGenerator(this, (ExclusiveGateway)node));
            } else if (node instanceof StartEvent) {
                registerGenerator(node, new StartEventGenerator(this, (StartEvent)node));
            } else if (node instanceof EndEvent) {
                registerGenerator(node, new EndEventGenerator(this, (EndEvent)node));
            } else if (node instanceof SubProcess) {
                registerGenerator(node, new SubProcessGenerator(this, (SubProcess)node));
            } else {
                throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer)node);
            }
        }
    }

    @Override
    protected GeneratorProviderFactory getGeneratorProviderFactory() {
        return () -> new BpmnNodeGeneratorProvider(this);
    }

    @Override
    public void init() {
        super.init();
    }

}