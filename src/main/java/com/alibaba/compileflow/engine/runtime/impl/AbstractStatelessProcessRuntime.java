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

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import com.alibaba.compileflow.engine.runtime.instance.ProcessInstance;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractStatelessProcessRuntime<T extends AbstractFlowModel> extends AbstractProcessRuntime<T> {

    public AbstractStatelessProcessRuntime(T flowModel) {
        super(flowModel);
    }

    @Override
    public void init() {
        super.init();
        initGatewayGraph();
    }

    @Override
    public String generateJavaCode() {
        classTarget.addSuperInterface(ClassWrapper.of(ProcessInstance.class));
        generateFlowMethod("execute", this::generateExecuteMethodBody);
        return classTarget.generateCode();
    }

    @Override
    protected boolean isStateless() {
        return true;
    }

    @Override
    protected List<Class> getExtImportedTypes() {
        return Collections.singletonList(ProcessInstance.class);
    }

    @Override
    protected List<ValidateMessage> validateFlowModel() {
        return super.validateFlowModel();
    }

    private void generateExecuteMethodBody(CodeTargetSupport codeTargetSupport) {
        nodeGeneratorProvider.getGenerator(flowModel).generateCode(codeTargetSupport);
    }

    @SuppressWarnings("unchecked")
    private void initGatewayGraph() {
        List<TransitionNode> nodes = flowModel.getTransitionNodes();
        nodes.forEach(this::buildFollowingNodes);
        nodes.stream()
            .filter(flowNode -> flowNode instanceof GatewayElement)
            .forEach(gatewayNode -> {
                if (CollectionUtils.isNotEmpty(gatewayNode.getIncomingNodes())
                    && gatewayNode.getIncomingNodes().stream()
                    .allMatch(incomingNode -> isContainedByIncomingNode(gatewayNode, incomingNode))) {
                    followingGraph.put(gatewayNode.getId(), Collections.emptyList());
                }

                gatewayNode.getOutgoingNodes().forEach(outgoingNode -> {
                    List<TransitionNode> branchNodes = buildBranchNodes(outgoingNode).stream()
                        .filter(node -> !followingGraph.get(gatewayNode.getId()).contains(node))
                        .collect(Collectors.toList());
                    branchGraph.put(outgoingNode.getId(), branchNodes);
                });
            });
    }

    private List<TransitionNode> buildFollowingNodes(TransitionNode flowNode) {
        if (followingGraph.containsKey(flowNode.getId())) {
            return followingGraph.get(flowNode.getId());
        }

        List<TransitionNode> followingNodes;
        if (flowNode instanceof EndElement) {
            followingNodes = Collections.emptyList();
        } else if (flowNode instanceof GatewayElement) {
            followingNodes = buildGatewayFollowingNodes(flowNode);
        } else {
            followingNodes = new ArrayList<>();
            TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(flowNode);
            followingNodes.add(theOnlyOutgoingNode);
            followingNodes.addAll(buildFollowingNodes(theOnlyOutgoingNode));
        }

        followingGraph.put(flowNode.getId(), followingNodes);
        return followingNodes;
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode flowNode) {
        return flowNode.getOutgoingNodes().get(0);
    }

    private boolean isContainedByIncomingNode(Node decisionNode, TransitionNode incomingNode) {
        if (incomingNode instanceof StartElement) {
            return false;
        }
        if (incomingNode instanceof GatewayElement) {
            List<TransitionNode> decisionFollowingNodes = followingGraph.get(decisionNode.getId());
            List<TransitionNode> incomingFollowingNodes = followingGraph.get(incomingNode.getId());
            if (decisionFollowingNodes.size() == incomingFollowingNodes.size()
                && incomingFollowingNodes.containsAll(decisionFollowingNodes)) {
                return true;
            }
        }
        return CollectionUtils.isNotEmpty(incomingNode.getIncomingNodes())
            && incomingNode.getIncomingNodes().stream()
            .allMatch(node -> isContainedByIncomingNode(decisionNode, node));
    }

    private List<TransitionNode> buildGatewayFollowingNodes(TransitionNode flowNode) {
        List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        List<TransitionNode> followingNodes = Collections.emptyList();
        for (int i = 0; i < outgoingNodes.size(); i++) {
            TransitionNode branchNode = outgoingNodes.get(i);
            List<TransitionNode> branchFollowingNodes = buildFollowingNodes(branchNode);

            if (i == 0) {
                followingNodes = new ArrayList<>(branchFollowingNodes);
            } else {
                Iterator<TransitionNode> flowNodeIterator = followingNodes.iterator();
                while (flowNodeIterator.hasNext()) {
                    TransitionNode followingNode = flowNodeIterator.next();
                    if (branchFollowingNodes.stream()
                        .anyMatch(node -> node.getId().equals(followingNode.getId()))) {
                        break;
                    } else {
                        flowNodeIterator.remove();
                    }
                    if (CollectionUtils.isEmpty(followingNodes)) {
                        return followingNodes;
                    }
                }
            }
        }
        return followingNodes;
    }

    private List<TransitionNode> buildBranchNodes(TransitionNode branchNode) {
        if (branchGraph.containsKey(branchNode.getId())) {
            return branchGraph.get(branchNode.getId());
        }

        List<TransitionNode> branchNodes = new ArrayList<>();
        branchNodes.add(branchNode);
        if (!(branchNode instanceof EndElement) && !(branchNode instanceof GatewayElement)) {
            branchNodes.addAll(buildBranchNodes(getTheOnlyOutgoingNode(branchNode)));
        }

        branchGraph.put(branchNode.getId(), branchNodes);
        return branchNodes;
    }

}