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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.container;

import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.definition.tbbpm.SubBpmNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class ContainerGenerator extends AbstractContainerGenerator {

    public ContainerGenerator(AbstractProcessRuntime runtime,
                              NodeContainer nodeContainer) {
        super(runtime, nodeContainer);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        TransitionNode startNode = (TransitionNode) nodeContainer.getStartNode();
        generateCode(startNode, codeTargetSupport);
    }

    private void generateCode(TransitionNode flowNode, CodeTargetSupport codeTargetSupport) {
        if (flowNode instanceof EndElement) {
            return;
        }
        if (flowNode instanceof WaitElement) {
            // 这里要产生wait节点里的前置任务
            Generator generator = getGenerator(flowNode);
            generator.generateCode(codeTargetSupport);
            return;
        }
        if (flowNode instanceof SubBpmNode && ((SubBpmNode) flowNode).isWaitForTrigger()) {
            return;
        }

        Generator generator = getGenerator(flowNode);
        generator.generateCode(codeTargetSupport);
        if (flowNode instanceof GatewayElement) {
            return;
        }

        List<TransitionNode> outgoingNodes = getOutingNodes(flowNode);
        if (CollectionUtils.isNotEmpty(outgoingNodes)) {
            for (TransitionNode outgoingNode : outgoingNodes) {
                generateCode(outgoingNode, codeTargetSupport);
            }
        }
    }

    private List<TransitionNode> getOutingNodes(TransitionNode flowNode) {
        return flowNode.getOutgoingNodes();
    }

}
