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
package com.alibaba.compileflow.engine.core.builder.generator.node;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.core.builder.generator.constants.Modifier;
import com.alibaba.compileflow.engine.core.definition.*;
import com.alibaba.compileflow.engine.core.infrastructure.ClassWrapper;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ProcessUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for gateway node code generators.
 * Provides functionality for generating code from gateway nodes,
 * including branch logic, join handling, and gateway-specific processing.
 *
 * @param <N> The type of gateway node this generator handles
 * @author yusu
 */
public abstract class AbstractGatewayNodeCodeGenerator<N extends GatewayNode> extends AbstractActionNodeCodeGenerator<N> {

    protected static final boolean INLINE = true;

    public AbstractGatewayNodeCodeGenerator(GeneratorContext context, N flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (isJoinGateway()) {
            return;
        }

        generateNodeComment(codeTargetSupport);
        if (isTriggerMethod(codeTargetSupport)) {
            if (flowNode instanceof ExclusiveGatewayElement) {
                super.generateCode(codeTargetSupport);
                return;
            }
            generateGatewayLogicForTrigger(codeTargetSupport);
            return;
        }

        generateGatewayLogic(codeTargetSupport);
    }

    protected void generateGatewayLogicForTrigger(CodeTargetSupport codeTargetSupport) {
        generateActionMethodCode(codeTargetSupport, flowNode.getAction());
        generateBranchNodeCode(codeTargetSupport);
    }

    protected void generateGatewayLogic(CodeTargetSupport codeTargetSupport) {
        if (shouldExtractMethod(codeTargetSupport)) {
            generateActionMethodCode(codeTargetSupport, flowNode.getAction());
        } else {
            super.generateCode(codeTargetSupport);
        }
        generateBranchNodeCode(codeTargetSupport);
        generateFollowingNodeCode(codeTargetSupport);
    }

    protected abstract void generateBranchNodeCode(CodeTargetSupport codeTargetSupport);

    protected void generateFollowingNodeCode(CodeTargetSupport codeTargetSupport) {
        Map<String, List<TransitionNode>> followingGraph = context.getFollowingGraph();
        List<TransitionNode> followingNodes = followingGraph.get(flowNode.getId());
        executeNodes(followingNodes, codeTargetSupport);
    }

    protected void executeNodes(List<TransitionNode> nodes, CodeTargetSupport codeTargetSupport) {
        if (CollectionUtils.isNotEmpty(nodes)) {
            nodes.stream()
                    .map(context::getGenerator)
                    .forEach(generator -> generator.generateCode(codeTargetSupport));
        }
    }

    protected boolean isJoinGateway() {
        return flowNode.getIncomingNodes().size() > 1 && flowNode.getOutgoingNodes().size() == 1;
    }

    protected String generateBranchMethodName(Transition transition) {
        return "executeBranch_" + JavaIdentifierUtils.toMethodSuffix(transition.getTarget());
    }

    /**
     * Generates branch methods for all outgoing transitions.
     * This method creates private methods for each transition branch.
     */
    protected void generateBranchMethods(CodeTargetSupport codeTargetSupport, List<? extends Transition> transitions) {
        for (Transition transition : transitions) {
            MethodTarget branchMethod = new MethodTarget();
            branchMethod.setClassTarget(getClassTarget(codeTargetSupport));
            branchMethod.setName(generateBranchMethodName(transition));
            branchMethod.addModifier(Modifier.PRIVATE);
            branchMethod.addException(ClassWrapper.of(Exception.class));
            generateBranchBody(branchMethod, transition);
            getClassTarget(codeTargetSupport).addMethod(branchMethod);
        }
    }

    /**
     * Generates the body of a branch method.
     * Handles end node checking and subsequent node execution.
     */
    protected void generateBranchBody(MethodTarget methodTarget, Transition transition) {
        methodTarget.addParameter(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), "_pContext"));
        if (isEndNode(transition.getTarget())) {
            methodTarget.addBodyLine("return _wrapResult();");
        } else {
            Map<String, List<TransitionNode>> branchGraph = context.getBranchGraph();
            Node toNode = context.getNodeById(transition.getTarget());
            String branchKey = ProcessUtils.buildBranchKey(flowNode, toNode);
            executeNodes(branchGraph.get(branchKey), methodTarget);
        }
    }

    /**
     * Retrieves the list of nodes in a branch for the given transition.
     * Uses ProcessUtils.buildBranchKey to get the branch node list.
     */
    protected List<TransitionNode> getBranchNodes(Transition transition) {
        Map<String, List<TransitionNode>> branchGraph = context.getBranchGraph();
        Node toNode = context.getNodeById(transition.getTarget());
        String branchKey = ProcessUtils.buildBranchKey(flowNode, toNode);
        return branchGraph.get(branchKey);
    }

    /**
     * Filters transitions that have conditional expressions.
     * Used by Exclusive and Inclusive gateways that require condition evaluation.
     */
    protected <T extends Transition> List<T> getConditionalFlows(List<T> outgoingFlows) {
        return outgoingFlows.stream()
                .filter(flow -> StringUtils.isNotBlank(flow.getExpression()))
                .collect(Collectors.toList());
    }

    /**
     * Generates branch execution logic.
     * Handles end node checking and node execution.
     */
    protected void generateBranchExecutionLogic(CodeTargetSupport target, Transition transition, List<TransitionNode> branchNodes) {
        if (isEndNode(transition.getTarget())) {
            target.addBodyLine("return _wrapResult();");
        } else {
            executeNodes(branchNodes, target);
        }
    }

    /**
     * Template method for generating conditional branch logic.
     * Subclasses only need to provide the specific Gateway executor method name.
     */
    protected void generateConditionalBranchLogic(CodeTargetSupport codeTargetSupport,
                                                  String executorMethod,
                                                  List<? extends Transition> conditionalFlows,
                                                  Transition defaultTransition,
                                                  List<? extends Transition> allTransitions) {
        codeTargetSupport.addBodyLine(executorMethod + "(\"" + flowNode.getId() + "\", _pContext)");

        for (Transition flow : conditionalFlows) {
            final String method = generateBranchMethodName(flow);
            codeTargetSupport.addBodyLine(".when(" + flow.getExpression() + ", this::" + method + ")");
        }

        if (defaultTransition != null) {
            codeTargetSupport.addBodyLine(".otherwise(this::" + generateBranchMethodName(defaultTransition) + ")");
        }
        codeTargetSupport.addBodyLine(".run();");

        generateBranchMethods(codeTargetSupport, allTransitions);
    }

    /**
     * Generates parallel branch logic.
     * Used by ParallelGateway and other parallel execution gateways.
     */
    protected void generateParallelBranchLogic(CodeTargetSupport codeTargetSupport,
                                               List<? extends Transition> outgoingFlows) {
        codeTargetSupport.addBodyLine("GatewayExecutor.parallel(\"" + flowNode.getId() + "\", _pContext)");
        for (Transition transition : outgoingFlows) {
            final String method = generateBranchMethodName(transition);
            codeTargetSupport.addBodyLine(".branch(this::" + method + ")");
        }
        codeTargetSupport.addBodyLine(".run();");

        generateBranchMethods(codeTargetSupport, outgoingFlows);
    }

}
