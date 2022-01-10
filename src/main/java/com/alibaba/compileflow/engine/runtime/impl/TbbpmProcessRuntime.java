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

import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.tbbpm.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.impl.TbbpmNodeGeneratorProvider;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmProcessRuntime extends AbstractProcessRuntime<TbbpmModel> {

    private TbbpmProcessRuntime(TbbpmModel tbbpmModel) {
        super(tbbpmModel);
    }

    public static TbbpmProcessRuntime of(TbbpmModel tbbpmModel) {
        return new TbbpmProcessRuntime(tbbpmModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.TBBPM;
    }

    @Override
    protected boolean isBpmn20() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {
        for (TransitionNode node : nodeContainer.getAllNodes()) {

            if (node instanceof AutoTaskNode) {
                registerGenerator(node, new AutoTaskGenerator(this, (AutoTaskNode) node));
            } else if (node instanceof ScriptTaskNode) {
                registerGenerator(node, new ScriptTaskGenerator(this, (ScriptTaskNode) node));
            } else if (node instanceof DecisionNode) {
                registerGenerator(node, new DecisionGenerator(this, (DecisionNode) node));
            } else if (node instanceof StartNode) {
                registerGenerator(node, new StartGenerator(this, (StartNode) node));
            } else if (node instanceof EndNode) {
                registerGenerator(node, new EndGenerator(this, (EndNode) node));
            } else if (node instanceof LoopProcessNode) {
                registerGenerator(node, new LoopProcessGenerator(this, (LoopProcessNode) node));
            } else if (node instanceof BreakNode) {
                registerGenerator(node, new BreakGenerator(this, (BreakNode) node));
            } else if (node instanceof ContinueNode) {
                registerGenerator(node, new ContinueGenerator(this, (ContinueNode) node));
            } else if (node instanceof SubBpmNode) {
                registerGenerator(node, new SubBpmGenerator(this, (SubBpmNode) node));
            } else if (node instanceof NoteNode) {
                registerGenerator(node, new NoteGenerator(this, (NoteNode) node));
            } else if (node instanceof WaitTaskNode) {
                registerGenerator(node, new WaitTaskGenerator(this, (WaitTaskNode) node));
            } else if (node instanceof WaitEventTaskNode) {
                registerGenerator(node, new WaitEventTaskGenerator(this, (WaitEventTaskNode) node));
            } else {
                throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer) node);
            }
        }
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    protected GeneratorProviderFactory getGeneratorProviderFactory() {
        return () -> new TbbpmNodeGeneratorProvider(this);
    }

}
