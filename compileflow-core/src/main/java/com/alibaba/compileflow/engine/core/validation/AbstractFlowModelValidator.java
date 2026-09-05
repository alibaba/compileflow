/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.validation;

import com.alibaba.compileflow.engine.core.java.expression.JavaExpressionInspector;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.ExclusiveGatewayElement;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.GatewayElement;
import com.alibaba.compileflow.engine.core.model.GatewayNode;
import com.alibaba.compileflow.engine.core.model.Node;
import com.alibaba.compileflow.engine.core.model.NodeContainer;
import com.alibaba.compileflow.engine.core.model.ParallelGatewayElement;
import com.alibaba.compileflow.engine.core.model.StartElement;
import com.alibaba.compileflow.engine.core.model.Transition;
import com.alibaba.compileflow.engine.core.model.TransitionNode;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Abstract structural validator for flow models.
 *
 * @author yusu
 */
public abstract class AbstractFlowModelValidator implements FlowModelValidator {
    @Override
    public List<ValidationFailure> validate(FlowModel<?> flowModel) {
        List<? extends Node> nodes = flowModel.getAllNodes();
        List<Node> allNodes = collectNodes(flowModel);
        Set<Node> containerEntries = collectContainerEntries(flowModel);
        List<ValidationFailure> validateMessages = new ArrayList<>();

        if (CollectionUtils.isEmpty(nodes)) {
            validateMessages.add(ValidationFailure.of(
                    "flow model has no node, please check flow definition, code is " + flowModel.getCode()));
        }

        allNodes
            .stream()
            .filter(node -> StringUtils.isBlank(node.getId()))
            .forEach(node -> validateMessages.add(ValidationFailure.of(
                    "Flow model contains a node with a blank id, code=" + flowModel.getCode())));

        List<String> nodeIds =
                allNodes.stream().map(Element::getId).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        Set<String> duplicateNodeIdSet = findDuplicateNodeId(nodeIds);
        if (CollectionUtils.isNotEmpty(duplicateNodeIdSet)) {
            validateMessages.add(ValidationFailure.of(
                    "flow model has duplicate node id across the complete model, code=" + flowModel.getCode()
                    + ", duplicate id:[" + String.join(",", duplicateNodeIdSet) + "]"));
        }

        validateStartEndNode(flowModel, validateMessages);
        validateDuplicateVarNames(flowModel, validateMessages);
        validateReservedVariableNames(flowModel, validateMessages);
        validateBoundaryDirections(allNodes, validateMessages);
        validateExplicitRouting(allNodes, validateMessages);
        validateGatewayNodes(allNodes, containerEntries, validateMessages);
        validateGatewayDefaultTransitions(allNodes, containerEntries, validateMessages);
        return validateMessages;
    }

    private List<Node> collectNodes(NodeContainer<?> root) {
        List<Node> nodes = new ArrayList<>();
        Set<NodeContainer<?>> visitedContainers = Collections.newSetFromMap(new IdentityHashMap<>());
        collectNodes(root, nodes, visitedContainers);
        return nodes;
    }

    private void collectNodes(NodeContainer<?> container, List<Node> destination,
            Set<NodeContainer<?>> visitedContainers) {
        if (!visitedContainers.add(container)) {
            return;
        }
        for (Node node : container.getAllNodes()) {
            if (node == null) {
                continue;
            }
            destination.add(node);
            if (node instanceof NodeContainer<?> childContainer) {
                collectNodes(childContainer, destination, visitedContainers);
            }
        }
    }

    private Set<Node> collectContainerEntries(NodeContainer<?> root) {
        Set<Node> entries = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<NodeContainer<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<NodeContainer<?>> pending = new ArrayList<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            NodeContainer<?> container = pending.remove(pending.size() - 1);
            if (!visited.add(container)) {
                continue;
            }
            try {
                Node entry = container.getStartNode();
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (RuntimeException invalidBoundary) {
                // Format-specific validators report malformed boundaries.
            }
            for (Node node : container.getAllNodes()) {
                if (node instanceof NodeContainer<?> nested) {
                    pending.add(nested);
                }
            }
        }
        return entries;
    }

    private void validateStartEndNode(FlowModel<?> flowModel, List<ValidationFailure> validateMessages) {
        List<? extends Node> nodes = flowModel.getAllNodes();
        List<? extends Node> startNodes =
                nodes
            .stream()
            .filter(node -> node instanceof StartElement)
            .collect(Collectors.toList());
        if (startNodes.size() == 0) {
            validateMessages.add(ValidationFailure.of(
                    "no start node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (startNodes.size() > 1) {
            validateMessages.add(ValidationFailure.of(
                    "more than one start node(node "
                    + startNodes.stream().map(Node::getId).collect(Collectors.joining(","))
                    + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }

        List<? extends Node> endNodes =
                nodes
            .stream()
            .filter(node -> node instanceof EndElement)
            .collect(Collectors.toList());
        if (endNodes.size() == 0) {
            validateMessages.add(ValidationFailure.of(
                    "no end node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (endNodes.size() > 1) {
            validateMessages.add(ValidationFailure.of(
                    "more than one end node(node " + endNodes
                                .stream()
                                .map(Node::getId)
                                .collect(Collectors.joining(","))
                    + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }
    }

    private void validateGatewayNodes(List<? extends Node> nodes, Set<Node> containerEntries,
            List<ValidationFailure> validateMessages) {
        List<? extends Node> gatewayNodes =
                nodes
            .stream()
            .filter(node -> node instanceof GatewayElement)
            .collect(Collectors.toList());
        gatewayNodes.forEach(gatewayNode -> {
            int incomingCount = effectiveIncomingCount((GatewayNode<?>) gatewayNode, containerEntries);
            int outgoingCount = ((GatewayNode<?>) gatewayNode).getOutgoingTransitions().size();
            if (gatewayNode instanceof ExclusiveGatewayElement<?> exclusiveGateway && incomingCount == 1 && outgoingCount > 1) {
                validateExclusiveSplitGateway(exclusiveGateway, validateMessages);
            } else if (gatewayNode instanceof ParallelGatewayElement<?> parallelGateway && incomingCount > 1 && outgoingCount == 1) {
                validateParallelJoinGateway(parallelGateway, validateMessages);
            }
        });
    }

    private void validateDuplicateVarNames(FlowModel<?> flowModel, List<ValidationFailure> validateMessages) {
        List<String> names = new ArrayList<>();
        addVariableiableNames(flowModel.getParameterVariables(), names);
        addVariableiableNames(flowModel.getInternalVariables(), names);
        addVariableiableNames(flowModel.getReturnVariables(), names);
        if (CollectionUtils.isEmpty(names)) {
            return;
        }
        Set<String> dups = findDuplicates(names);
        if (CollectionUtils.isNotEmpty(dups)) {
            validateMessages.add(ValidationFailure.of(
                    "flow model has duplicate variable name, please check flow definition, code is "
                    + flowModel.getCode() + ", duplicate names:[" + String.join(",", dups) + "]"));
        }
    }

    private void addVariableiableNames(List<Variable> variables, List<String> destination) {
        if (CollectionUtils.isEmpty(variables)) {
            return;
        }
        variables
            .stream()
            .filter(Objects::nonNull)
            .map(Variable::getName)
            .filter(Objects::nonNull)
            .forEach(destination::add);
    }

    private void validateReservedVariableNames(FlowModel<?> flowModel, List<ValidationFailure> validateMessages) {
        if (CollectionUtils.isEmpty(flowModel.getVariables())) {
            return;
        }
        flowModel
            .getVariables()
            .stream()
            .filter(Objects::nonNull)
            .map(Variable::getName)
            .filter(ProcessNames::isReserved)
            .forEach(name -> validateMessages.add(ValidationFailure.of(
                    "Flow variable uses the reserved CompileFlow identifier prefix: " + name)));
    }

    private void validateGatewayDefaultTransitions(List<? extends Node> nodes, Set<Node> containerEntries,
            List<ValidationFailure> messages) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }

        for (Node node : nodes) {
            if (node instanceof GatewayNode<?> gateway) {
                validateGatewayConnectivity(gateway, effectiveIncomingCount(gateway, containerEntries), messages);
                validateGatewayTransitions(gateway, messages);
            }
        }
    }

    private void validateGatewayConnectivity(GatewayNode<?> gateway, int incomingCount,
            List<ValidationFailure> messages) {
        int outgoingCount = gateway.getOutgoingTransitions().size();
        boolean split = incomingCount == 1 && outgoingCount > 1;
        boolean join = incomingCount > 1 && outgoingCount == 1;
        if (!split && !join) {
            messages.add(ValidationFailure.of(
                    "Gateway must be a split (one incoming and multiple outgoing) or a join (multiple incoming and one outgoing), found "
                    + incomingCount + " incoming and " + outgoingCount + " outgoing transitions, id=" + gateway.getId()));
        }
    }

    private void validateBoundaryDirections(List<? extends Node> nodes, List<ValidationFailure> messages) {
        for (Node node : nodes) {
            if (!(node instanceof TransitionNode<?> transitionNode)) {
                continue;
            }
            if (node instanceof StartElement) {
                if (!transitionNode.getIncomingTransitions().isEmpty()) {
                    messages.add(ValidationFailure.of(
                            "Start node must not have incoming transitions, id=" + node.getId()));
                }
                if (transitionNode.getOutgoingTransitions().size() != 1) {
                    messages.add(ValidationFailure.of(
                            "Start node must have exactly one outgoing transition, found "
                            + transitionNode.getOutgoingTransitions().size() + ", id=" + node.getId()));
                }
            }
            if (node instanceof EndElement) {
                if (transitionNode.getIncomingTransitions().isEmpty()) {
                    messages.add(ValidationFailure.of(
                            "End node must have at least one incoming transition, id=" + node.getId()));
                }
                if (!transitionNode.getOutgoingTransitions().isEmpty()) {
                    messages.add(ValidationFailure.of("End node must not have outgoing transitions, id=" + node.getId()));
                }
            }
        }
    }

    private void validateExplicitRouting(List<? extends Node> nodes, List<ValidationFailure> messages) {
        for (Node node : nodes) {
            if (!(node instanceof TransitionNode<?> transitionNode) || node instanceof GatewayElement) {
                continue;
            }
            if (transitionNode.getOutgoingTransitions().size() > 1) {
                messages.add(ValidationFailure.of(
                        "Non-gateway node must not branch; use an explicit gateway, id=" + node.getId()));
            }
            if (transitionNode
                .getOutgoingTransitions()
                .stream()
                .anyMatch(transition -> StringUtils.isNotBlank(transition.getCondition()))) {
                messages.add(ValidationFailure.of(
                        "Conditional outgoing transitions are supported only on explicit split gateways, id=" + node.getId()));
            }
        }
    }

    private int effectiveIncomingCount(GatewayNode<?> gateway, Set<Node> containerEntries) {
        int incomingCount = gateway.getIncomingTransitions().size();
        return incomingCount == 0 && containerEntries.contains(gateway) ? 1 : incomingCount;
    }

    private void validateGatewayTransitions(GatewayNode<?> gateway, List<ValidationFailure> messages) {
        List<? extends Transition> transitions = gateway.getOutgoingTransitions();
        transitions
            .stream()
            .filter(transition -> StringUtils.isNotBlank(transition.getCondition()))
            .forEach(transition -> validateConditionExpression("Gateway condition on transition " + gateway.getId()
                    + " -> " + transition.getTarget(), transition.getCondition(), messages));

        if (gateway.getIncomingTransitions().size() > 1 && transitions.size() == 1
                && StringUtils.isNotBlank(transitions.get(0).getCondition())) {
            messages.add(ValidationFailure.of(
                    "Join gateway must not have a conditional outgoing transition, id=" + gateway.getId()));
        }

        if (gateway instanceof ParallelGatewayElement) {
            if (transitions
                .stream()
                .anyMatch(t -> StringUtils.isNotBlank(t.getCondition()))) {
                messages.add(ValidationFailure.of(
                        "Parallel gateway should not have conditional outgoing transitions, id=" + gateway.getId()));
            }
            return;
        }

        long defaultCount = transitions
            .stream()
            .filter(t -> StringUtils.isBlank(t.getCondition()))
            .count();
        if (defaultCount > 1) {
            messages.add(ValidationFailure.of(
                    "Exclusive or Inclusive Gateway node has multiple default transitions (empty expression), id=" + gateway.getId()));
        }
    }

    protected final void validateConditionExpression(String location, String expression,
            List<ValidationFailure> messages) {
        String value = expression.trim();
        if (value.startsWith("${") || value.startsWith("#{")) {
            messages.add(ValidationFailure.of(
                    location
                    + " must contain the raw Java expression body; ${...} and #{...} wrappers are not valid CompileFlow model syntax"));
        }
        String mutation = JavaExpressionInspector.findDirectMutation(expression);
        if (mutation != null) {
            messages.add(ValidationFailure.of(
                    location + " must be side-effect free by author contract; detected direct mutation '" + mutation
                    + "'. Static validation is intentionally limited and does not prove purity"));
        }
    }

    private Set<String> findDuplicateNodeId(List<String> nodeIds) {
        return findDuplicates(nodeIds);
    }

    private Set<String> findDuplicates(List<String> values) {
        final Set<String> nodeIdSet = new HashSet<>();
        final Set<String> duplicateNodeIdSet = new HashSet<>();

        for (String value : values) {
            if (!nodeIdSet.add(value)) {
                duplicateNodeIdSet.add(value);
            }
        }
        return duplicateNodeIdSet;
    }

    protected void validateExclusiveSplitGateway(ExclusiveGatewayElement<?> gateway, List<ValidationFailure> messages) {
        List<? extends Transition> outgoingTransitions = gateway.getOutgoingTransitions();

        List<String> conditions = outgoingTransitions
            .stream()
            .filter(transition -> StringUtils.isNotBlank(transition.getCondition()))
            .map(Transition::getCondition)
            .collect(Collectors.toList());

        if (conditions.size() >= 2) {
            Set<String> uniqueConditions = new HashSet<>(conditions);
            if (uniqueConditions.size() < conditions.size()) {
                messages.add(ValidationFailure.of("Exclusive gateway has duplicate conditions, id=" + gateway.getId()));
            }
        }
    }

    protected void validateParallelJoinGateway(ParallelGatewayElement<?> gatewayNode, List<ValidationFailure> messages) {
        List<? extends Transition> incomingTransitions = gatewayNode.getIncomingTransitions();
        long conditionalTransitions =
                incomingTransitions
            .stream()
            .filter(transition -> StringUtils.isNotBlank(transition.getCondition()))
            .count();

        if (conditionalTransitions > 0) {
            messages.add(ValidationFailure.of(
                    "Parallel join gateway should not have conditional incoming transitions, id=" + gatewayNode.getId()));
        }
    }
}
