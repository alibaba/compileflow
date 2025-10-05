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
package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractGatewayNodeCodeGenerator;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import com.alibaba.compileflow.engine.tbbpm.definition.DecisionNode;
import com.alibaba.compileflow.engine.tbbpm.definition.Transition;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Generates code for a DecisionNode.
 * Builds an if/else-if/else chain from conditional and default transitions.
 *
 * @author yusu
 */
public class DecisionGenerator extends AbstractGatewayNodeCodeGenerator<DecisionNode> {

    public DecisionGenerator(GeneratorContext context, DecisionNode flowNode) {
        super(context, flowNode);
    }

    @Override
    protected void generateBranchNodeCode(CodeTargetSupport codeTargetSupport) {
        List<Transition> outgoingFlows = flowNode.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }

        List<Transition> conditionalFlows = getConditionalFlows(outgoingFlows);
        Transition defaultTransition = flowNode.getDefaultTransition();

        if (INLINE) {
            if (conditionalFlows.isEmpty() && defaultTransition != null) {
                generateTransitionComment(codeTargetSupport, defaultTransition);
                generateBranchExecutionLogic(codeTargetSupport, defaultTransition, getBranchNodes(defaultTransition));
                return;
            }

            for (int i = 0; i < conditionalFlows.size(); i++) {
                boolean isFirst = (i == 0);
                generateBranch(codeTargetSupport, conditionalFlows.get(i), isFirst, false);
            }

            if (defaultTransition != null) {
                generateBranch(codeTargetSupport, defaultTransition, false, true);
            }

            codeTargetSupport.addBodyLine("}");
        } else {
            generateConditionalBranchLogic(codeTargetSupport, "GatewayExecutor.exclusive",
                    conditionalFlows, defaultTransition, outgoingFlows);
        }
    }

    private void generateBranch(CodeTargetSupport codeTargetSupport, Transition transition, boolean isFirst, boolean isDefault) {
        List<TransitionNode> branchNodes = getBranchNodes(transition);

        openBranchStatement(codeTargetSupport, transition, isFirst, isDefault);
        generateTransitionComment(codeTargetSupport, transition);

        generateBranchExecutionLogic(codeTargetSupport, transition, branchNodes);
    }

    private void openBranchStatement(CodeTargetSupport support, Transition t, boolean isFirst, boolean isDefault) {
        if (isDefault) {
            support.addBodyLine("} else {");
        } else if (isFirst) {
            support.addBodyLine("if (" + t.getExpression() + ") {");
        } else {
            support.addBodyLine("} else if (" + t.getExpression() + ") {");
        }
    }

    private void generateTransitionComment(CodeTargetSupport support, Transition transition) {
        if (StringUtils.isNotEmpty(transition.getName())) {
            support.addBodyLine("// Branch: " + transition.getName());
        }
    }

}
