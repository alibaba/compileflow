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
package com.alibaba.compileflow.engine.tbbpm.validation;

import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.validation.AbstractFlowModelValidator;
import com.alibaba.compileflow.engine.core.validation.ExecutableModelValidation;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.core.semantic.ProtocolDuration;
import com.alibaba.compileflow.engine.core.model.AbruptCompletionElement;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.ForEachElement;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.StartElement;
import com.alibaba.compileflow.engine.core.model.TransitionNode;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.tbbpm.model.AutoTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.BpmCallNode;
import com.alibaba.compileflow.engine.tbbpm.model.BreakNode;
import com.alibaba.compileflow.engine.tbbpm.model.ContinueNode;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachNode;
import com.alibaba.compileflow.engine.tbbpm.model.LoopScopeNode;
import com.alibaba.compileflow.engine.tbbpm.model.NoteNode;
import com.alibaba.compileflow.engine.tbbpm.model.ScriptTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.SubBpmNode;
import com.alibaba.compileflow.engine.tbbpm.model.StructuredScopeNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmNodeContainer;
import com.alibaba.compileflow.engine.tbbpm.model.TimerTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitEventTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WhileNode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates TBBPM-specific model semantics that cannot be expressed by XSD 1.0.
 *
 * @author yusu
 */
public final class TbbpmModelValidator extends AbstractFlowModelValidator {
    private static final long MAX_WAIT_TIMEOUT_MILLIS = Duration.ofDays(36500).toMillis();

    @Override
    public List<ValidationFailure> validate(FlowModel<?> flowModel) {
        List<ValidationFailure> messages = new ArrayList<>(super.validate(flowModel));
        if (!(flowModel instanceof TbbpmModel tbbpmModel)) {
            messages.add(ValidationFailure.of("TBBPM validator requires a TBBPM model"));
            return messages;
        }

        if (StringUtils.isBlank(tbbpmModel.getCode())) {
            messages.add(ValidationFailure.of("TBBPM process code must not be blank"));
        }
        Set<String> processVariableNames =
                ExecutableModelValidation.validateProcessVariables("TBBPM", tbbpmModel, messages);
        Set<String> innerProcessVariableNames = tbbpmModel.getVariables() == null
                ? Set.of()
                : tbbpmModel
            .getVariables()
            .stream()
            .filter(Objects::nonNull)
            .filter(variable -> ProcessVariableContainer.VARIABLE_TYPE_INNER.equals(variable.getInOutType()))
            .filter(variable -> variable.getName() != null)
            .map(variable -> variable.getName())
            .collect(Collectors.toUnmodifiableSet());
        validateNoteIsolation(tbbpmModel.getAllNodes(), messages);
        validateTopLevelReachability(tbbpmModel, messages);
        validateStructuredScopes(tbbpmModel.getAllNodes(), messages);
        validateLoopNodes(tbbpmModel.getAllNodes(), false, processVariableNames, innerProcessVariableNames, Set.of(),
                messages);
        validateBpmCallVariables(tbbpmModel.getAllNodes(), processVariableNames, messages);
        validateActions(tbbpmModel.getAllNodes(), processVariableNames, messages);
        validateTriggerEntryProperties(tbbpmModel.getAllNodes(), messages);
        validateTimerProperties(tbbpmModel.getAllNodes(), messages);
        return messages;
    }

    private void validateTimerProperties(List<? extends FlowNode> nodes, List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof TimerTaskNode timer) {
                int schedules = 0;
                schedules += timer.getDuration() != null ? 1 : 0;
                schedules += timer.getDurationExpression() != null ? 1 : 0;
                schedules += timer.getWakeAtExpression() != null ? 1 : 0;
                if (schedules != 1
                        || StringUtils.isAllBlank(timer.getDuration(), timer.getDurationExpression(),
                                timer.getWakeAtExpression())) {
                    messages.add(ValidationFailure.of(
                            "TBBPM timerTask must declare exactly one non-blank duration, durationExpression, or wakeAtExpression, id="
                            + node.getId()));
                }
                if (StringUtils.isNotBlank(timer.getDuration())) {
                    validateLiteralDuration(timer, messages);
                }
            }
            validateTimerProperties(childNodes(node), messages);
        }
    }

    private void validateLiteralDuration(TimerTaskNode timer, List<ValidationFailure> messages) {
        try {
            ProtocolDuration.parseNonNegativeMillis(timer.getDuration(), "TBBPM timerTask duration");
        } catch (IllegalArgumentException invalid) {
            messages.add(ValidationFailure.of(invalid.getMessage() + ", id=" + timer.getId()));
        }
    }

    private void validateTriggerEntryProperties(List<? extends FlowNode> nodes, List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof WaitEventTaskNode waitEvent) {
                if (StringUtils.isBlank(waitEvent.getEvent())) {
                    messages.add(ValidationFailure.of(
                            "TBBPM waitEventTask must declare a non-blank event, id=" + node.getId()));
                }
                validateWaitTimeout(waitEvent.getTimeout(), waitEvent.getId(), messages);
            } else if (node instanceof WaitTaskNode wait) {
                validateWaitTimeout(wait.getTimeout(), wait.getId(), messages);
            }
            validateTriggerEntryProperties(childNodes(node), messages);
        }
    }

    private void validateWaitTimeout(String value, String nodeId, List<ValidationFailure> messages) {
        if (value == null) {
            return;
        }
        try {
            long timeout = ProtocolDuration.parseNonNegativeMillis(value, "TBBPM wait timeout");
            if (timeout > MAX_WAIT_TIMEOUT_MILLIS) {
                messages.add(ValidationFailure.of("TBBPM wait timeout must be in [PT0S, P36500D], id=" + nodeId));
            }
        } catch (IllegalArgumentException invalid) {
            messages.add(ValidationFailure.of(invalid.getMessage() + ", id=" + nodeId));
        }
    }

    private void validateBpmCallVariables(List<? extends FlowNode> nodes, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof BpmCallNode call) {
                validateBpmCallReference(call, messages);
                ExecutableModelValidation.validateCalledProcessMappings("TBBPM",
                        "mappings on bpmCall node " + node.getId(), call, processVariableNames, messages);
            }
            validateBpmCallVariables(childNodes(node), processVariableNames, messages);
        }
    }

    private void validateActions(List<? extends FlowNode> nodes, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof HasAction actionOwner) {
                validateActionNodeKind(node, actionOwner.getAction(), messages);
                ExecutableModelValidation.validateAction("TBBPM", "action on node " + node.getId(),
                        actionOwner.getAction(), processVariableNames, messages);
                ExecutableModelValidation.validateInvocationPolicy("TBBPM",
                        "invocationPolicy on action at node " + node.getId(),
                        actionOwner.getAction() == null ? null : actionOwner.getAction().getInvocationPolicy(), messages);
                ExecutableModelValidation.validateEffectPolicy("TBBPM", "action on node " + node.getId(),
                        actionOwner.getAction(), messages);
            }
            validateActions(childNodes(node), processVariableNames, messages);
        }
    }

    private void validateActionNodeKind(FlowNode node, Action action, List<ValidationFailure> messages) {
        if ((node instanceof AutoTaskNode || node instanceof ScriptTaskNode) && action == null) {
            messages.add(ValidationFailure.of(
                    "TBBPM " + nodeType(node) + " must declare one action, id=" + node.getId()));
            return;
        }
        if (action == null) {
            return;
        }
        ActionType type = action.getType();
        if (node instanceof AutoTaskNode && type != null && type != ActionType.JAVA && type != ActionType.SPRING_BEAN) {
            messages.add(ValidationFailure.of(
                    "TBBPM autoTask action type must be java or spring-bean, id=" + node.getId() + ", actionType=" + type));
        }
        if (node instanceof ScriptTaskNode && type != ActionType.SCRIPT) {
            messages.add(ValidationFailure.of(
                    "TBBPM scriptTask action type must be script, id=" + node.getId() + ", actionType=" + type));
        }
    }

    private String nodeType(FlowNode node) {
        return node instanceof ScriptTaskNode ? "scriptTask" : "autoTask";
    }

    private void validateNoteIsolation(List<? extends FlowNode> nodes, List<ValidationFailure> messages) {
        Set<String> noteIds = new LinkedHashSet<>();
        for (FlowNode node : nodes) {
            if (node instanceof NoteNode) {
                noteIds.add(node.getId());
                if (!node.getOutgoingTransitions().isEmpty()) {
                    messages.add(ValidationFailure.of(
                            "TBBPM note nodes cannot have outgoing transitions, id=" + node.getId()));
                }
            }
        }
        for (FlowNode node : nodes) {
            node
                .getOutgoingTransitions()
                .stream()
                .filter(transition -> noteIds.contains(transition.getTarget()))
                .forEach(transition -> messages.add(ValidationFailure.of(
                        "TBBPM note nodes cannot be transition targets, sourceId=" + node.getId() + ", noteId=" + transition.getTarget())));
            validateNoteIsolation(childNodes(node), messages);
        }
    }

    private void validateLoopNodes(List<? extends FlowNode> nodes, boolean insideLoop, Set<String> processVariableNames,
            Set<String> innerProcessVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof BreakNode || node instanceof ContinueNode) {
                validateLoopControlNode(node, insideLoop, messages);
            }
            if (node instanceof LoopScopeNode loop) {
                validateLoop(loop, processVariableNames, innerProcessVariableNames, visibleLexicalVariableNames,
                        messages);
                validateLoopNodes(loop.getAllNodes(), true, processVariableNames, innerProcessVariableNames,
                        lexicalVariablesInside(loop, visibleLexicalVariableNames), messages);
            } else if (node instanceof TbbpmNodeContainer container) {
                validateLoopNodes(container.getAllNodes(), insideLoop, processVariableNames, innerProcessVariableNames,
                        visibleLexicalVariableNames, messages);
            }
        }
    }

    private void validateLoopControlNode(FlowNode node, boolean insideLoop, List<ValidationFailure> messages) {
        if (!insideLoop) {
            messages.add(ValidationFailure.of(
                    "TBBPM " + loopControlName(node) + " node must be contained by a loop, id=" + node.getId()));
        }
        String expression = node instanceof BreakNode breakNode ? breakNode.getCondition() : ((ContinueNode) node)
            .getCondition();
        if (StringUtils.isNotBlank(expression)) {
            validateConditionExpression("TBBPM " + loopControlName(node) + " condition on node " + node.getId(),
                    expression, messages);
        }
        if (StringUtils.isBlank(expression) && !node.getOutgoingTransitions().isEmpty()) {
            messages.add(ValidationFailure.of(
                    "Unconditional TBBPM " + loopControlName(node) + " node must not declare an outgoing transition, id=" + node.getId()));
        }
        if (node.getOutgoingTransitions().size() > 1) {
            messages.add(ValidationFailure.of(
                    "TBBPM " + loopControlName(node) + " node may declare at most one normal outgoing transition, id=" + node.getId()));
        }
    }

    private String loopControlName(FlowNode node) {
        return node instanceof BreakNode ? "break" : "continue";
    }

    private void validateLoop(LoopScopeNode loop, Set<String> processVariableNames,
            Set<String> innerProcessVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        if (loop instanceof ForEachNode forEach) {
            validateForEach(forEach, processVariableNames, innerProcessVariableNames, visibleLexicalVariableNames,
                    messages);
        } else if (loop instanceof WhileNode whileNode) {
            validateWhile(whileNode, processVariableNames, visibleLexicalVariableNames, messages);
        }
        if (loop instanceof ForEachNode forEach && forEach.getExecution() == ForEachElement.Execution.PARALLEL
                && containsBreakForCurrentLoop(loop.getAllNodes())) {
            messages.add(ValidationFailure.of(
                    "Parallel foreach does not support break because one iteration cannot cancel the collection scope, loopId="
                    + loop.getId()));
        }
    }

    private boolean containsBreakForCurrentLoop(List<? extends FlowNode> nodes) {
        for (FlowNode node : nodes) {
            if (node instanceof BreakNode) {
                return true;
            }
            if (!(node instanceof LoopScopeNode) && node instanceof TbbpmNodeContainer container
                    && containsBreakForCurrentLoop(container.getAllNodes())) {
                return true;
            }
        }
        return false;
    }

    private void validateForEach(ForEachNode loop, Set<String> processVariableNames,
            Set<String> innerProcessVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        String loopId = loop.getId();
        requireNonBlank(loopId, "collection", loop.getCollection(), messages);
        validateGeneratedVariable(loopId, "collection", loop.getCollection(), messages);
        if (StringUtils.isNotBlank(loop.getCollection()) && !processVariableNames.contains(loop.getCollection())
                && !visibleLexicalVariableNames.contains(loop.getCollection())) {
            messages.add(ValidationFailure.of(
                    "foreach collection must reference a declared process variable or an enclosing loop variable, id="
                    + loopId + ", collection=" + loop.getCollection()));
        }
        requireNonBlank(loopId, "item", loop.getItem(), messages);
        requireNonBlank(loopId, "itemType", loop.getItemType(), messages);
        validateGeneratedVariable(loopId, "item", loop.getItem(), messages);
        validateGeneratedVariable(loopId, "index", loop.getIndex(), messages);
        rejectLexicalCollision(loopId, "item", loop.getItem(), processVariableNames, visibleLexicalVariableNames,
                messages);
        rejectLexicalCollision(loopId, "index", loop.getIndex(), processVariableNames, visibleLexicalVariableNames,
                messages);
        if (StringUtils.isNotBlank(loop.getItem()) && loop.getItem().equals(loop.getIndex())) {
            messages.add(ValidationFailure.of("foreach item and index must be different, id=" + loopId));
        }
        if (loop.getItemType() != null && !JavaNames.isClassName(loop.getItemType())) {
            messages.add(ValidationFailure.of(
                    "foreach itemType must be a valid Java class name, id=" + loopId + ", itemType=" + loop.getItemType()));
        }
        if (loop.getOutput() != null) {
            requireProcessVariable(loopId, "output.target", loop.getOutput().getTarget(), processVariableNames, messages);
            requireProcessVariable(loopId, "output.source", loop.getOutput().getSource(), processVariableNames, messages);
            if (StringUtils.isNotBlank(loop.getOutput().getSource())
                    && processVariableNames.contains(loop.getOutput().getSource())
                    && !innerProcessVariableNames.contains(loop.getOutput().getSource())) {
                messages.add(ValidationFailure.of(
                        "foreach output.source must reference an inner process variable, id=" + loopId + ", variable="
                        + loop.getOutput().getSource()));
            }
            if (Objects.equals(loop.getOutput().getTarget(), loop.getOutput().getSource())) {
                messages.add(ValidationFailure.of("foreach output target and source must be different, id=" + loopId));
            }
        }
    }

    private void validateWhile(WhileNode loop, Set<String> processVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        String loopId = loop.getId();
        requireNonBlank(loopId, "condition", loop.getCondition(), messages);
        if (StringUtils.isNotBlank(loop.getCondition())) {
            validateConditionExpression("TBBPM while condition on loop " + loopId, loop.getCondition(), messages);
        }
        validateGeneratedVariable(loopId, "index", loop.getIndex(), messages);
        rejectLexicalCollision(loopId, "index", loop.getIndex(), processVariableNames, visibleLexicalVariableNames,
                messages);
        if (loop.getMaxIterations() == null) {
            messages.add(ValidationFailure.of("while requires maxIterations, id=" + loopId));
        } else if (loop.getMaxIterations() <= 0) {
            messages.add(ValidationFailure.of("while maxIterations must be positive, id=" + loopId));
        }
    }

    private void requireProcessVariable(String loopId, String property, String variableName,
            Set<String> processVariableNames, List<ValidationFailure> messages) {
        requireNonBlank(loopId, property, variableName, messages);
        validateGeneratedVariable(loopId, property, variableName, messages);
        if (StringUtils.isNotBlank(variableName) && !processVariableNames.contains(variableName)) {
            messages.add(ValidationFailure.of(
                    "foreach " + property + " must reference a declared process variable, id=" + loopId + ", variable=" + variableName));
        }
    }

    private void rejectLexicalCollision(String loopId, String property, String variableName,
            Set<String> processVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(variableName)) {
            return;
        }
        if (processVariableNames.contains(variableName) || visibleLexicalVariableNames.contains(variableName)) {
            messages.add(ValidationFailure.of(
                    "Loop " + property + " must not shadow a process or enclosing lexical variable, id=" + loopId
                    + ", variable=" + variableName));
        }
    }

    private Set<String> lexicalVariablesInside(LoopScopeNode loop, Set<String> visibleLexicalVariableNames) {
        Set<String> nested = new LinkedHashSet<>(visibleLexicalVariableNames);
        if (loop instanceof ForEachNode forEach) {
            if (StringUtils.isNotBlank(forEach.getItem())) {
                nested.add(forEach.getItem());
            }
            if (StringUtils.isNotBlank(forEach.getIndex())) {
                nested.add(forEach.getIndex());
            }
        } else if (loop instanceof WhileNode whileNode && StringUtils.isNotBlank(whileNode.getIndex())) {
            nested.add(whileNode.getIndex());
        }
        return Set.copyOf(nested);
    }

    private void requireNonBlank(String loopId, String property, String value, List<ValidationFailure> messages) {
        if (StringUtils.isBlank(value)) {
            messages.add(ValidationFailure.of("Loop must declare " + property + ", id=" + loopId));
        }
    }

    private void validateGeneratedVariable(String loopId, String property, String value,
            List<ValidationFailure> messages) {
        if (value == null) {
            return;
        }
        if (!ProcessNames.isIdentifier(value)) {
            messages.add(ValidationFailure.of(
                    "Loop " + property + " must be a valid Java identifier, id=" + loopId + ", value=" + value));
        } else if (ProcessNames.isReserved(value)) {
            messages.add(ValidationFailure.of(
                    "Loop " + property + " uses the reserved CompileFlow identifier prefix, id=" + loopId + ", value=" + value));
        }
    }

    private void validateBpmCallReference(BpmCallNode call, List<ValidationFailure> messages) {
        ExecutableModelValidation.validateCalledProcessReference("TBBPM", "bpmCall node " + call.getId(), call, messages);
    }

    private void validateStructuredScopes(List<? extends FlowNode> nodes, List<ValidationFailure> messages) {
        for (FlowNode node : nodes) {
            if (node instanceof StructuredScopeNode scope) {
                List<FlowNode> children = scope.getAllNodes();
                List<FlowNode> starts = children.stream().filter(StartElement.class::isInstance).toList();
                List<FlowNode> ends = children.stream().filter(EndElement.class::isInstance).toList();
                String type = scope instanceof SubBpmNode ? "subBpm" : scope instanceof WhileNode ? "while" : "foreach";
                if (starts.size() != 1) {
                    messages.add(ValidationFailure.of(
                            "TBBPM " + type + " must contain exactly one start node, id=" + scope.getId()));
                }
                if (ends.size() != 1) {
                    messages.add(ValidationFailure.of(
                            "TBBPM " + type + " must contain exactly one end node, id=" + scope.getId()));
                }
                if (starts.size() == 1 && ends.size() == 1) {
                    validateReachability(type + " " + scope.getId(), starts.get(0), ends.get(0), children, messages);
                    if (!ends.get(0).getOutgoingTransitions().isEmpty()) {
                        messages.add(ValidationFailure.of(
                                "TBBPM " + type + " end node must not have outgoing transitions, id=" + scope.getId()));
                    }
                }
            }
            validateStructuredScopes(childNodes(node), messages);
        }
    }

    private List<? extends FlowNode> childNodes(FlowNode node) {
        return node instanceof TbbpmNodeContainer container ? container.getAllNodes() : List.of();
    }

    private void validateTopLevelReachability(TbbpmModel model, List<ValidationFailure> messages) {
        List<FlowNode> executableNodes = executableNodes(model.getAllNodes());
        List<FlowNode> startNodes =
                executableNodes.stream().filter(StartElement.class::isInstance).collect(Collectors.toList());
        if (startNodes.size() == 1) {
            validateReachability("process " + model.getCode(), startNodes.get(0), null, executableNodes, messages);
        }
    }

    private void validateReachability(String container, FlowNode start, FlowNode abruptExit,
            List<? extends FlowNode> nodes, List<ValidationFailure> messages) {
        List<FlowNode> executableNodes = executableNodes(nodes);
        Set<FlowNode> localNodes = new HashSet<>(executableNodes);
        Set<FlowNode> reachable = new HashSet<>();
        Deque<FlowNode> pending = new ArrayDeque<>();
        pending.add(start);

        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (!reachable.add(current)) {
                continue;
            }
            List<TransitionNode<?>> outgoing = current.getOutgoingNodes();
            if (outgoing.isEmpty() && current instanceof AbruptCompletionElement && abruptExit != null
                    && current != abruptExit) {
                pending.addLast(abruptExit);
            }
            for (TransitionNode<?> target : outgoing) {
                if (target instanceof FlowNode flowTarget && localNodes.contains(flowTarget)) {
                    pending.addLast(flowTarget);
                }
            }
        }

        List<String> unreachableIds = executableNodes
            .stream()
            .filter(node -> !reachable.contains(node))
            .map(node -> String.valueOf(node.getId()))
            .sorted()
            .collect(Collectors.toList());
        if (!unreachableIds.isEmpty()) {
            messages.add(ValidationFailure.of(
                    "TBBPM " + container + " contains executable nodes unreachable from its start node, nodeIds=["
                    + String.join(",", unreachableIds) + "]"));
        }
    }

    private List<FlowNode> executableNodes(List<? extends FlowNode> nodes) {
        return nodes
            .stream()
            .filter(node -> !(node instanceof NoteNode))
            .collect(Collectors.toList());
    }
}
