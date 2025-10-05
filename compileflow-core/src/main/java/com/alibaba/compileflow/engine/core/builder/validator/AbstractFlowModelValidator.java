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
package com.alibaba.compileflow.engine.core.builder.validator;

import com.alibaba.compileflow.engine.core.definition.*;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public abstract class AbstractFlowModelValidator<T extends FlowModel> implements FlowModelValidator<T> {

    @Override
    @SuppressWarnings("unchecked")
    public List<ValidateMessage> validate(T flowModel) {
        List<Node> nodes = flowModel.getAllNodes();
        List<ValidateMessage> validateMessages = new ArrayList<>();

        if (CollectionUtils.isEmpty(nodes)) {
            validateMessages.add(ValidateMessage
                    .failure("flow model has no node, please check flow definition, code is " + flowModel.getCode()));
        }

        List<String> nodeIds = nodes.stream().map(Element::getId).collect(Collectors.toList());
        Set<String> duplicateNodeIdSet = findDuplicateNodeId(nodeIds);
        if (CollectionUtils.isNotEmpty(duplicateNodeIdSet)) {
            validateMessages.add(ValidateMessage
                    .failure("flow model has duplicate node id, please check flow definition, code is " + flowModel.getCode()
                            + ", duplicate id:[" + String.join(",", duplicateNodeIdSet) + "]"));
        }

        validateStartEndNode(flowModel, validateMessages);
        validateDuplicateVarNames(flowModel, validateMessages);
        validateGatewayNodes(flowModel, validateMessages);
        validateGatewayDefaultTransitions(flowModel, validateMessages);
        return validateMessages;
    }

    @SuppressWarnings("unchecked")
    private void validateStartEndNode(T flowModel,
                                      List<ValidateMessage> validateMessages) {
        List<Node> nodes = flowModel.getAllNodes();
        List<Node> startNodes = nodes.stream().filter(node -> node instanceof StartElement).collect(
                Collectors.toList());
        if (startNodes.size() == 0) {
            validateMessages.add(ValidateMessage.failure(
                    "no start node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (startNodes.size() > 1) {
            validateMessages.add(ValidateMessage.failure(
                    "more than one start node(node "
                            + startNodes.stream().map(Node::getId).collect(Collectors.joining(","))
                            + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }

        List<Node> endNodes = nodes.stream().filter(node -> node instanceof EndElement).collect(Collectors.toList());
        if (endNodes.size() == 0) {
            validateMessages.add(ValidateMessage.failure(
                    "no end node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (endNodes.size() > 1) {
            validateMessages.add(ValidateMessage.failure(
                    "more than one end node(node "
                            + endNodes.stream().map(Node::getId).collect(Collectors.joining(","))
                            + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }
    }

    private void validateGatewayNodes(FlowModel<Node> flowModel, List<ValidateMessage> validateMessages) {
        List<Node> gatewayNodes = flowModel.getAllNodes().stream()
                .filter(node -> node instanceof GatewayElement)
                .collect(Collectors.toList());
        gatewayNodes.forEach(gatewayNode -> {
            if (gatewayNode instanceof ExclusiveGatewayElement) {
                validateExclusiveGateway((ExclusiveGatewayElement) gatewayNode, validateMessages);
            } else if (gatewayNode instanceof InclusiveGatewayElement) {
                validateInclusiveGateway((InclusiveGatewayElement) gatewayNode, validateMessages);
            } else if (gatewayNode instanceof ParallelGatewayElement) {
                validateParallelGateway((ParallelGatewayElement) gatewayNode, validateMessages);
            }
        });
    }

    private void validateDuplicateVarNames(FlowModel<Node> flowModel, List<ValidateMessage> validateMessages) {
        List<String> names = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(flowModel.getParamVars())) {
            for (IVar v : flowModel.getParamVars()) {
                names.add(v.getName());
            }
        }
        if (CollectionUtils.isNotEmpty(flowModel.getInnerVars())) {
            for (IVar v : flowModel.getInnerVars()) {
                names.add(v.getName());
            }
        }
        if (CollectionUtils.isNotEmpty(flowModel.getReturnVars())) {
            for (IVar v : flowModel.getReturnVars()) {
                names.add(v.getName());
            }
        }
        if (CollectionUtils.isEmpty(names)) {
            return;
        }
        Set<String> dups = findDuplicateNodeId(names);
        if (CollectionUtils.isNotEmpty(dups)) {
            validateMessages.add(ValidateMessage.failure(
                    "flow model has duplicate variable name, please check flow definition, code is "
                            + flowModel.getCode() + ", duplicate names:[" + String.join(",", dups) + "]"));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateGatewayDefaultTransitions(T flowModel, List<ValidateMessage> messages) {
        List<Node> nodes = flowModel.getAllNodes();
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }

        for (Node node : nodes) {
            if (node instanceof GatewayNode) {
                GatewayNode gateway = (GatewayNode) node;
                validateGatewayConnectivity(gateway, messages);
                validateGatewayTransitions(gateway, messages);
                validateGatewayConditions(gateway, messages);
            }
        }
    }

    private void validateGatewayConnectivity(GatewayNode gateway, List<ValidateMessage> messages) {
        int incomingCount = gateway.getIncomingTransitions().size();
        int outgoingCount = gateway.getOutgoingTransitions().size();

        if (incomingCount == 0 || outgoingCount == 0) {
            messages.add(ValidateMessage.failure(
                    "Gateway node must have at least one incoming and one outgoing transition, id=" + gateway.getId()));
        }

        if (incomingCount == 1 && outgoingCount <= 1) {
            messages.add(ValidateMessage.failure(
                    "Split gateway should have more than one outgoing transition, found " + outgoingCount +
                            ", id=" + gateway.getId()));
        }

        if (outgoingCount == 1 && incomingCount <= 1) {
            messages.add(ValidateMessage.failure(
                    "Join gateway should have more than one incoming transition, found " + incomingCount +
                            ", id=" + gateway.getId()));
        }
    }

    private void validateGatewayTransitions(GatewayNode gateway, List<ValidateMessage> messages) {
        List<Transition> transitions = gateway.getOutgoingTransitions();

        if (gateway instanceof ParallelGatewayElement) {
            if (transitions.stream().anyMatch(t -> StringUtils.isNotBlank(t.getExpression()))) {
                messages.add(ValidateMessage.failure(
                        "Parallel gateway should not have conditional outgoing transitions, id=" + gateway.getId()));
            }
            return;
        }

        long defaultCount = transitions.stream()
                .filter(t -> StringUtils.isBlank(t.getExpression()))
                .count();
        if (defaultCount > 1) {
            messages.add(ValidateMessage.failure(
                    "Exclusive or Inclusive Gateway node has multiple default transitions (empty expression), id=" + gateway.getId())
            );
        }
    }

    private void validateGatewayConditions(GatewayNode gateway, List<ValidateMessage> messages) {
        List<Transition> transitions = gateway.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(transitions)) {
            return;
        }

        for (Transition transition : transitions) {
            String expression = transition.getExpression();
            if (StringUtils.isNotBlank(expression)) {
                validateConditionExpression(gateway.getId(), transition.getTarget(), expression, messages);
            }
        }
    }

    private void validateConditionExpression(String gatewayId, String targetId, String expression, List<ValidateMessage> messages) {
        if (expression.trim().isEmpty()) {
            messages.add(ValidateMessage.failure(
                    "Gateway condition expression cannot be empty, gateway=" + gatewayId +
                            ", target=" + targetId));
            return;
        }

        int openParens = 0;
        for (char c : expression.toCharArray()) {
            if (c == '(') openParens++;
            else if (c == ')') openParens--;
            if (openParens < 0) {
                messages.add(ValidateMessage.failure(
                        "Gateway condition expression has unmatched closing parenthesis, " +
                                "gateway=" + gatewayId + ", target=" + targetId + ", expression=" + expression));
                return;
            }
        }
        if (openParens > 0) {
            messages.add(ValidateMessage.failure(
                    "Gateway condition expression has unmatched opening parenthesis, " +
                            "gateway=" + gatewayId + ", target=" + targetId + ", expression=" + expression));
        }
    }

    private Set<String> findDuplicateNodeId(List<String> nodeIds) {
        final Set<String> nodeIdSet = new HashSet<>();
        final Set<String> duplicateNodeIdSet = new HashSet<>();

        for (String id : nodeIds) {
            if (!nodeIdSet.add(id)) {
                duplicateNodeIdSet.add(id);
            }
        }
        return duplicateNodeIdSet;
    }

    protected void validateExclusiveGateway(ExclusiveGatewayElement gateway, List<ValidateMessage> messages) {
        int incomingCount = gateway.getIncomingTransitions().size();
        int outgoingCount = gateway.getOutgoingTransitions().size();

        if (incomingCount == 1 && outgoingCount > 1) {
            validateExclusiveSplitGateway(gateway, messages);
        }
    }

    protected void validateExclusiveSplitGateway(ExclusiveGatewayElement gateway, List<ValidateMessage> messages) {
        List<? extends Transition> outgoingTransitions = gateway.getOutgoingTransitions();

        List<String> conditions = outgoingTransitions.stream()
                .filter(transition -> StringUtils.isNotBlank(transition.getExpression()))
                .map(Transition::getExpression)
                .collect(Collectors.toList());

        if (conditions.size() >= 2) {
            Set<String> uniqueConditions = new HashSet<>(conditions);
            if (uniqueConditions.size() < conditions.size()) {
                messages.add(ValidateMessage.failure(
                        "Exclusive gateway has duplicate conditions, id=" + gateway.getId()));
            }
        }
    }

    protected void validateInclusiveGateway(InclusiveGatewayElement gateway, List<ValidateMessage> messages) {
        int incomingCount = gateway.getIncomingTransitions().size();
        int outgoingCount = gateway.getOutgoingTransitions().size();

        if (incomingCount == 1 && outgoingCount > 1) {
            validateInclusiveSplitGateway(gateway, messages);
        }
    }

    protected void validateInclusiveSplitGateway(InclusiveGatewayElement gateway, List<ValidateMessage> messages) {
        List<? extends Transition> outgoingTransitions = gateway.getOutgoingTransitions();

        List<String> conditions = outgoingTransitions.stream()
                .filter(transition -> StringUtils.isNotBlank(transition.getExpression()))
                .map(Transition::getExpression)
                .collect(Collectors.toList());

        if (conditions.size() >= 2) {
            Set<String> uniqueConditions = new HashSet<>(conditions);
            if (uniqueConditions.size() < conditions.size()) {
                messages.add(ValidateMessage.failure(
                        "Inclusive gateway has duplicate conditions, id=" + gateway.getId()));
            }
        }
    }

    protected void validateParallelGateway(ParallelGatewayElement gatewayNode, List<ValidateMessage> messages) {
        int incomingCount = gatewayNode.getIncomingTransitions().size();
        int outgoingCount = gatewayNode.getOutgoingTransitions().size();

        if (incomingCount == 1 && outgoingCount > 1) {
            validateParallelSplitGateway(gatewayNode, messages);
        } else if (incomingCount > 1 && outgoingCount == 1) {
            validateParallelJoinGateway(gatewayNode, messages);
        } else {
            messages.add(ValidateMessage.failure(
                    "Parallel gateway should have one incoming transition and more than one outgoing transition " +
                            "or one outgoing transition and more than one incoming transition, but found " +
                            incomingCount + " incoming and " + outgoingCount + " outgoing transitions, " +
                            "id=" + gatewayNode.getId()));
        }
    }

    protected void validateParallelSplitGateway(ParallelGatewayElement gatewayNode, List<ValidateMessage> messages) {
        List<? extends Transition> outgoingTransitions = gatewayNode.getOutgoingTransitions();
        long conditionalTransitions = outgoingTransitions.stream()
                .filter(transition -> StringUtils.isNotBlank(transition.getExpression()))
                .count();

        if (conditionalTransitions > 0) {
            messages.add(ValidateMessage.failure(
                    "Parallel split gateway should not have conditional outgoing transitions, " +
                            "id=" + gatewayNode.getId()));
        }
    }

    protected void validateParallelJoinGateway(ParallelGatewayElement gatewayNode, List<ValidateMessage> messages) {
        List<? extends Transition> incomingTransitions = gatewayNode.getIncomingTransitions();
        long conditionalTransitions = incomingTransitions.stream()
                .filter(transition -> StringUtils.isNotBlank(transition.getExpression()))
                .count();

        if (conditionalTransitions > 0) {
            messages.add(ValidateMessage.failure(
                    "Parallel join gateway should not have conditional incoming transitions, " +
                            "id=" + gatewayNode.getId()));
        }
    }

}
