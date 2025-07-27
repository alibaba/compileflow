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
package com.alibaba.compileflow.engine.process.builder.generator.impl;

import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.action.HasInOutAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

import java.util.List;
import java.util.Map;

/**
 * @author yusu
 * inAction（进入节点时执行），outAction（事件触发时执行）。
 */
public abstract class AbstractInOutActionNodeGenerator<N extends Node>
        extends AbstractActionNodeGenerator<N> {

    public AbstractInOutActionNodeGenerator(AbstractProcessRuntime runtime, N flowNode) {
        super(runtime, flowNode);
    }

    protected void generateCode(String event, CodeTargetSupport codeTargetSupport) {
        HasInOutAction hasInOutAction = (HasInOutAction) flowNode;

        if (isTriggerMethod(codeTargetSupport)) {
            generateTriggerBranch(event, hasInOutAction, codeTargetSupport);
        } else {
            generateInActionBranch(hasInOutAction, codeTargetSupport);
            Map<String, List<TransitionNode>> followingGraph = runtime.getFollowingGraph();
            if (!followingGraph.get(runtime.getFlowModel().getStartNode().getId()).contains(flowNode)) {
                codeTargetSupport.addBodyLine("return _wrapResult();");
            }
        }
    }

    private void generateTriggerBranch(String event, HasInOutAction node, CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("if (trigger) {");
        if (event == null) {
            generateOutActionBranch(node, codeTargetSupport);
        } else {
            codeTargetSupport.addBodyLine("if(\"" + event + "\".equals(event)) {");
            generateOutActionBranch(node, codeTargetSupport);
            codeTargetSupport.addBodyLine("} else {");
            codeTargetSupport.addBodyLine("running = false;");
            codeTargetSupport.addBodyLine("}");
        }
        codeTargetSupport.addBodyLine("} else {");
        generateInActionBranch(node, codeTargetSupport);
        codeTargetSupport.addBodyLine("trigger = false;");
        codeTargetSupport.addBodyLine("running = false;");
        codeTargetSupport.addBodyLine("}");
    }

    private void generateInActionBranch(HasInOutAction node, CodeTargetSupport codeTargetSupport) {
        IAction inAction = node.getInAction();
        if (inAction != null) {
            generateActionMethodCode(codeTargetSupport, inAction);
        }
    }

    private void generateOutActionBranch(HasInOutAction node, CodeTargetSupport codeTargetSupport) {
        IAction outAction = node.getOutAction();
        if (outAction != null) {
            generateActionMethodCode(codeTargetSupport, outAction);
        }
    }
}
