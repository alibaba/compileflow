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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn;

import com.alibaba.compileflow.engine.common.util.VarUtils;
import com.alibaba.compileflow.engine.definition.bpmn.ExclusiveGateway;
import com.alibaba.compileflow.engine.definition.bpmn.FlowElement;
import com.alibaba.compileflow.engine.definition.bpmn.SequenceFlow;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author yusu
 */
public class ExclusiveGatewayGenerator extends AbstractBpmnActionNodeGenerator<ExclusiveGateway> {

    public ExclusiveGatewayGenerator(AbstractProcessRuntime runtime,
                                     ExclusiveGateway flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        String methodName = generateExclusiveGatewayMethodName();
        codeTargetSupport.addBodyLine(methodName + "();");

        generateMethodCode(codeTargetSupport, methodName, this::generateExclusiveGatewayMethodCode);
        Map<String, List<TransitionNode>> followingGraph = runtime.getFollowingGraph();
        List<TransitionNode> followingNodes = followingGraph.get(flowNode.getId());
        executeNodes(followingNodes, codeTargetSupport);
    }

    private String generateExclusiveGatewayMethodName() {
        return "exclusiveGateway" + getLegalVarName(flowNode);
    }

    private String getLegalVarName(FlowElement element) {
        if (VarUtils.isLegalVarName(element.getName())) {
            return StringUtils.capitalize(element.getName());
        }
        return VarUtils.getLegalVarName(element.getId());
    }

    private void generateExclusiveGatewayMethodCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        generateActionMethodCode(codeTargetSupport, flowNode.getAction());

        List<SequenceFlow> transitions = flowNode.getOutgoingFlows();
        for (SequenceFlow transition : transitions) {
            String condition = StringUtils.isEmpty(transition.getExpression())
                ? "true" : transition.getExpression();
            Map<String, List<TransitionNode>> branchGraph = runtime.getBranchGraph();
            List<TransitionNode> branchNodes = branchGraph.get(transition.getTargetRef());
            if (transition.equals(transitions.get(0))) {
                String ifCondition = "if (" + condition + ") {";
                codeTargetSupport.addBodyLine(ifCondition);
                generateTransitionComment(codeTargetSupport, transition);
                executeNodes(branchNodes, codeTargetSupport);
                codeTargetSupport.addBodyLine("}");
            } else if (StringUtils.isEmpty(transition.getExpression())
                && transition.equals(transitions.get(transitions.size() - 1))) {
                if (isEndNode(transition.getTargetRef())) {
                    return;
                }
                codeTargetSupport.appendLine(" else {");
                generateTransitionComment(codeTargetSupport, transition);
                executeNodes(branchNodes, codeTargetSupport);
                codeTargetSupport.addBodyLine("}");
            } else {
                String elseIfCondition = " else if (" + condition + ") {";
                codeTargetSupport.appendLine(elseIfCondition);
                generateTransitionComment(codeTargetSupport, transition);
                executeNodes(branchNodes, codeTargetSupport);
                codeTargetSupport.addBodyLine("}");
            }
        }
    }

    private void generateTransitionComment(CodeTargetSupport codeTargetSupport, SequenceFlow sequenceFlow) {
        if (StringUtils.isNotEmpty(sequenceFlow.getName())) {
            codeTargetSupport.addBodyLine("//" + sequenceFlow.getName());
        }
    }

    private void executeNodes(List<TransitionNode> flowNodes, CodeTargetSupport codeTargetSupport) {
        if (CollectionUtils.isNotEmpty(flowNodes)) {
            flowNodes.stream().map(flowNode -> runtime.getNodeGeneratorProvider().getGenerator(flowNode))
                .forEach(generator -> generator.generateCode(codeTargetSupport));
        }
    }

}
