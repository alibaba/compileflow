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
package com.alibaba.compileflow.engine.bpmn.validation;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.bpmn.model.Activity;
import com.alibaba.compileflow.engine.bpmn.model.BpmnElementContainer;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.CallActivity;
import com.alibaba.compileflow.engine.bpmn.model.ConditionalGateway;
import com.alibaba.compileflow.engine.bpmn.model.FlowNode;
import com.alibaba.compileflow.engine.bpmn.model.Gateway;
import com.alibaba.compileflow.engine.bpmn.model.GatewayDirection;
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.LoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.SequenceFlow;
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.validation.AbstractFlowModelValidator;
import com.alibaba.compileflow.engine.core.validation.ExecutableModelValidation;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.core.semantic.ProtocolDuration;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.StartElement;
import com.alibaba.compileflow.engine.core.model.TransitionNode;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates the executable BPMN subset implemented by CompileFlow.
 *
 * @author yusu
 */
public final class BpmnModelValidator extends AbstractFlowModelValidator {
    @Override
    public List<ValidationFailure> validate(FlowModel<?> flowModel) {
        List<ValidationFailure> messages = new ArrayList<>(super.validate(flowModel));
        if (!(flowModel instanceof BpmnModel bpmnModel)) {
            messages.add(ValidationFailure.of("BPMN validator requires a BPMN model"));
            return messages;
        }

        Set<String> processVariableNames =
                ExecutableModelValidation.validateProcessVariables("BPMN", bpmnModel, messages);
        validateDocumentContract(bpmnModel, messages);
        validateElementIdentifiers(bpmnModel, messages);
        validateMessages(bpmnModel, messages);
        validateContainer(bpmnModel, bpmnModel.getProcess(), false, processVariableNames, Set.of(), messages);
        return messages;
    }

    private void validateContainer(BpmnModel model, BpmnElementContainer container, boolean nested,
            Set<String> processVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        List<FlowNode> nodes = container.getAllNodes();
        String containerId = container.getId();

        if (nested) {
            validateNestedBoundaries(containerId, nodes, messages);
        }
        validateReachability(containerId, nodes, messages);

        for (FlowNode node : nodes) {
            if (node instanceof Activity activity) {
                validateLoop(model, activity, processVariableNames, visibleLexicalVariableNames, messages);
            }
            validateNodeContract(model, node, processVariableNames, messages);
            if (node instanceof SubProcess subProcess) {
                validateContainer(model, subProcess, true, processVariableNames,
                        lexicalVariablesInside(subProcess, visibleLexicalVariableNames), messages);
            }
        }
    }

    private void validateNodeContract(BpmnModel model, FlowNode node, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        if (node instanceof ServiceTask serviceTask && serviceTask.getAction() == null) {
            messages.add(ValidationFailure.of("BPMN serviceTask must declare one cf:action, id=" + node.getId()));
        }
        if (node instanceof ServiceTask serviceTask && serviceTask.getAction() != null
                && serviceTask.getAction().getType() == ActionType.SCRIPT) {
            messages.add(ValidationFailure.of(
                    "BPMN serviceTask action type must be java or spring-bean, id=" + node.getId() + ", actionType="
                    + serviceTask.getAction().getType()));
        }

        if (node instanceof HasAction actionOwner) {
            ExecutableModelValidation.validateAction("BPMN", "action on node " + node.getId(), actionOwner.getAction(),
                    processVariableNames, messages);
            ExecutableModelValidation.validateInvocationPolicy("BPMN",
                    "invocationPolicy on action at node " + node.getId(),
                    actionOwner.getAction() == null ? null : actionOwner.getAction().getInvocationPolicy(), messages);
            ExecutableModelValidation.validateEffectPolicy("BPMN", "action on node " + node.getId(),
                    actionOwner.getAction(), messages);
        }
        if (node instanceof Gateway gateway) {
            validateGatewayDirection(gateway, messages);
            validateGatewaySequenceFlows(gateway, messages);
        }

        if (node instanceof ScriptTask scriptTask) {
            validateScriptTask(scriptTask, processVariableNames, messages);
        } else if (node instanceof CallActivity callActivity) {
            validateCallActivity(callActivity, processVariableNames, messages);
        } else if (node instanceof ReceiveTask receiveTask) {
            validateReceiveTask(model, receiveTask, messages);
        } else if (node instanceof IntermediateCatchEvent catchEvent) {
            validateIntermediateCatchEvent(model, catchEvent, messages);
        }
    }

    private void validateGatewaySequenceFlows(Gateway gateway, List<ValidationFailure> messages) {
        if (!(gateway instanceof ConditionalGateway conditionalGateway)) {
            return;
        }
        if (gateway.getIncomingTransitions().size() != 1 || gateway.getOutgoingTransitions().size() <= 1) {
            return;
        }

        String defaultFlowId = conditionalGateway.getDefaultFlowId();
        SequenceFlow defaultFlow = null;
        if (StringUtils.isNotBlank(defaultFlowId)) {
            List<SequenceFlow> matches =
                    gateway
                .getOutgoingTransitions()
                .stream()
                .filter(flow -> defaultFlowId.equals(flow.getId()))
                .toList();
            if (matches.size() != 1) {
                messages.add(ValidationFailure.of(
                        "BPMN gateway default must reference exactly one outgoing sequenceFlow, id=" + gateway.getId()
                        + ", default=" + defaultFlowId));
            } else {
                defaultFlow = matches.get(0);
                if (StringUtils.isNotBlank(defaultFlow.getCondition())) {
                    messages.add(ValidationFailure.of(
                            "BPMN default sequenceFlow must not declare a condition, gateway=" + gateway.getId()
                            + ", sequenceFlow=" + defaultFlow.getId()));
                }
            }
        }

        for (SequenceFlow flow : gateway.getOutgoingTransitions()) {
            if (flow != defaultFlow && StringUtils.isBlank(flow.getCondition())) {
                messages.add(ValidationFailure.of(
                        "BPMN non-default outgoing sequenceFlow must declare a Java condition, gateway="
                        + gateway.getId() + ", sequenceFlow=" + flow.getId()));
            }
        }
    }

    private void validateGatewayDirection(Gateway gateway, List<ValidationFailure> messages) {
        GatewayDirection declared = gateway.getGatewayDirection();
        if (declared == GatewayDirection.UNSPECIFIED) {
            return;
        }

        int incoming = gateway.getIncomingTransitions().size();
        int outgoing = gateway.getOutgoingTransitions().size();
        GatewayDirection actual = gatewayDirection(incoming, outgoing);
        if (declared != actual) {
            messages.add(ValidationFailure.of(
                    "BPMN gatewayDirection must match control-flow topology, id=" + gateway.getId() + ", declared="
                    + declared.getXmlValue() + ", actual=" + actual.getXmlValue()));
        }
    }

    private static GatewayDirection gatewayDirection(int incoming, int outgoing) {
        if (incoming > 1) {
            return outgoing > 1 ? GatewayDirection.MIXED : GatewayDirection.CONVERGING;
        }
        return outgoing > 1 ? GatewayDirection.DIVERGING : GatewayDirection.UNSPECIFIED;
    }

    private void validateScriptTask(ScriptTask scriptTask, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(scriptTask.getScriptFormat())) {
            messages.add(ValidationFailure.of(
                    "BPMN scriptTask must declare a non-blank scriptFormat, id=" + scriptTask.getId()));
        }
        if (StringUtils.isBlank(scriptTask.getScript())) {
            messages.add(ValidationFailure.of(
                    "BPMN scriptTask must contain a non-blank script, id=" + scriptTask.getId()));
        }
        Action action = new Action();
        action.setType(ActionType.SCRIPT);
        action.setLanguage(scriptTask.getScriptFormat());
        action.setSource(scriptTask.getScript());
        action.setInputMappings(scriptTask.getInputMappings());
        action.setOutputMappings(scriptTask.getOutputMappings());
        action.setExecution(scriptTask.getExecution());
        action.setInvocationPolicy(scriptTask.getInvocationPolicy());
        action.setEffectPolicy(scriptTask.getEffectPolicy());
        String location = "scriptTask " + scriptTask.getId();
        ExecutableModelValidation.validateAction("BPMN", location, action, processVariableNames, messages);
        ExecutableModelValidation.validateInvocationPolicy("BPMN", "invocationPolicy on " + location,
                scriptTask.getInvocationPolicy(), messages);
        ExecutableModelValidation.validateEffectPolicy("BPMN", location, action, messages);
    }

    private void validateCallActivity(CallActivity callActivity, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        ExecutableModelValidation.validateCalledProcessReference("BPMN", "callActivity " + callActivity.getId(),
                callActivity, messages);
        ExecutableModelValidation.validateCalledProcessMappings("BPMN",
                "mappings on callActivity " + callActivity.getId(), callActivity, processVariableNames, messages);
    }

    private void validateReceiveTask(BpmnModel model, ReceiveTask receiveTask, List<ValidationFailure> messages) {
        validateMessageReference(model, "receiveTask", receiveTask.getId(), receiveTask.getMessageRef(), messages);
    }

    private void validateIntermediateCatchEvent(BpmnModel model, IntermediateCatchEvent event,
            List<ValidationFailure> messages) {
        if ((event.getMessageEventDefinition() == null) == (event.getTimerEventDefinition() == null)) {
            messages.add(ValidationFailure.of(
                    "BPMN intermediateCatchEvent must declare exactly one supported event definition, id=" + event.getId()));
            return;
        }
        if (event.getMessageEventDefinition() != null) {
            validateMessageReference(model, "intermediateCatchEvent", event.getId(),
                    event.getMessageEventDefinition().getMessageRef(), messages);
            return;
        }
        TimerValue timer = event.getTimerEventDefinition().getValue();
        if (timer == null) {
            messages.add(ValidationFailure.of(
                    "BPMN timerEventDefinition must declare a timer value, id=" + event.getId()));
        } else if (timer.kind() == TimerValue.Kind.DURATION && !timer.expression()) {
            try {
                ProtocolDuration.parseNonNegativeMillis(timer.value(), "BPMN timeDuration");
            } catch (IllegalArgumentException failure) {
                messages.add(ValidationFailure.of(failure.getMessage() + ", id=" + event.getId()));
            }
        }
    }

    private void validateMessageReference(BpmnModel model, String elementName, String elementId, String messageRef,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(messageRef)) {
            messages.add(ValidationFailure.of(
                    "Executable BPMN " + elementName + " must declare messageRef, id=" + elementId));
            return;
        }
        List<Message> messageDefinitions = model.getMessages();
        if (messageDefinitions == null) {
            messages.add(ValidationFailure.of(
                    "BPMN " + elementName + " messageRef cannot be resolved because message definitions are null, id=" + elementId));
            return;
        }
        List<Message> matchingMessages = messageDefinitions
            .stream()
            .filter(message -> message != null && messageRef.equals(message.getId()))
            .collect(Collectors.toList());
        if (matchingMessages.size() != 1) {
            messages.add(ValidationFailure.of(
                    "BPMN " + elementName + " messageRef must resolve to exactly one message, id=" + elementId
                    + ", messageRef=" + messageRef));
        } else if (StringUtils.isBlank(matchingMessages.get(0).getName())) {
            messages.add(ValidationFailure.of(
                    "BPMN " + elementName + " message must declare a non-blank name, id=" + elementId + ", messageRef=" + messageRef));
        }
    }

    private void validateMessages(BpmnModel model, List<ValidationFailure> messages) {
        List<Message> messageDefinitions = model.getMessages();
        if (messageDefinitions == null) {
            messages.add(ValidationFailure.of("BPMN message definitions must not be null"));
            return;
        }
        for (Message message : messageDefinitions) {
            if (message == null) {
                messages.add(ValidationFailure.of("BPMN message definitions must not contain null"));
            } else if (StringUtils.isBlank(message.getName())) {
                messages.add(ValidationFailure.of("BPMN message name must not be blank, id=" + message.getId()));
            }
        }
    }

    private void validateDocumentContract(BpmnModel model, List<ValidationFailure> messages) {
        if (StringUtils.isBlank(model.getTargetNamespace())) {
            messages.add(ValidationFailure.of("BPMN targetNamespace must not be blank"));
        }
        if (!Boolean.TRUE.equals(model.getProcess().getExecutable())) {
            messages.add(ValidationFailure.of(
                    "BPMN process must declare isExecutable=\"true\", id=" + model.getProcess().getId()));
        }
    }

    private void validateElementIdentifiers(BpmnModel model, List<ValidationFailure> messages) {
        Set<String> identifiers = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        registerOptionalIdentifier(model.getDefinitionsId(), identifiers, duplicates);
        registerRequiredIdentifier("process", model.getProcess().getId(), identifiers, duplicates, messages);
        collectElementIdentifiers(model.getProcess(), identifiers, duplicates, messages);

        List<Message> messageDefinitions = model.getMessages();
        if (messageDefinitions != null) {
            for (Message message : messageDefinitions) {
                if (message != null) {
                    registerRequiredIdentifier("message", message.getId(), identifiers, duplicates, messages);
                }
            }
        }

        if (!duplicates.isEmpty()) {
            messages.add(ValidationFailure.of(
                    "BPMN element ids must be unique across the complete document, duplicateIds=[" + String.join(",",
                            duplicates) + "]"));
        }
    }

    private void collectElementIdentifiers(BpmnElementContainer container, Set<String> identifiers,
            Set<String> duplicates, List<ValidationFailure> messages) {
        for (AbstractFlowElement element : container.getAllElements()) {
            if (element == null) {
                messages.add(ValidationFailure.of("BPMN element containers must not contain null"));
                continue;
            }
            registerRequiredIdentifier(element.getClass().getSimpleName(), element.getId(), identifiers, duplicates,
                    messages);
            if (element instanceof Activity activity && activity.getLoopCharacteristics() != null) {
                registerOptionalIdentifier(activity.getLoopCharacteristics().getId(), identifiers, duplicates);
            }
            if (element instanceof IntermediateCatchEvent event) {
                if (event.getMessageEventDefinition() != null) {
                    registerOptionalIdentifier(event.getMessageEventDefinition().getId(), identifiers, duplicates);
                }
                if (event.getTimerEventDefinition() != null) {
                    registerOptionalIdentifier(event.getTimerEventDefinition().getId(), identifiers, duplicates);
                }
            }
            if (element instanceof SubProcess subProcess) {
                collectElementIdentifiers(subProcess, identifiers, duplicates, messages);
            }
        }
    }

    private void registerRequiredIdentifier(String elementType, String identifier, Set<String> identifiers,
            Set<String> duplicates, List<ValidationFailure> messages) {
        if (StringUtils.isBlank(identifier)) {
            messages.add(ValidationFailure.of("BPMN " + elementType + " id must not be blank"));
            return;
        }
        registerOptionalIdentifier(identifier, identifiers, duplicates);
    }

    private void registerOptionalIdentifier(String identifier, Set<String> identifiers, Set<String> duplicates) {
        if (StringUtils.isNotBlank(identifier) && !identifiers.add(identifier)) {
            duplicates.add(identifier);
        }
    }

    private void validateNestedBoundaries(String containerId, List<FlowNode> nodes, List<ValidationFailure> messages) {
        List<FlowNode> startNodes = nodes
            .stream()
            .filter(StartElement.class::isInstance)
            .collect(Collectors.toList());
        if (startNodes.size() != 1) {
            messages.add(ValidationFailure.of(
                    "Embedded subprocess must contain exactly one start event, subprocess=" + containerId + ", found="
                    + startNodes.size()));
        }

        List<FlowNode> endNodes = nodes.stream().filter(EndElement.class::isInstance).collect(Collectors.toList());
        if (endNodes.size() != 1) {
            messages.add(ValidationFailure.of(
                    "Embedded subprocess must contain exactly one end event, subprocess=" + containerId + ", found=" + endNodes.size()));
        }
    }

    private void validateReachability(String containerId, List<FlowNode> nodes, List<ValidationFailure> messages) {
        List<FlowNode> startNodes = nodes
            .stream()
            .filter(StartElement.class::isInstance)
            .collect(Collectors.toList());
        if (startNodes.size() != 1) {
            return;
        }

        Set<FlowNode> localNodes = new HashSet<>(nodes);
        Set<FlowNode> reachable = new HashSet<>();
        Deque<FlowNode> pending = new ArrayDeque<>();
        pending.add(startNodes.get(0));

        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (!reachable.add(current)) {
                continue;
            }
            for (TransitionNode<?> target : current.getOutgoingNodes()) {
                if (!(target instanceof FlowNode flowTarget) || !localNodes.contains(flowTarget)) {
                    messages.add(ValidationFailure.of(
                            "BPMN transition crosses an embedded process boundary, container=" + containerId
                            + ", source=" + current.getId() + ", target=" + target.getId()));
                    continue;
                }
                pending.addLast(flowTarget);
            }
        }

        List<String> unreachableIds = nodes
            .stream()
            .filter(node -> !reachable.contains(node))
            .map(node -> String.valueOf(node.getId()))
            .sorted()
            .collect(Collectors.toList());
        if (!unreachableIds.isEmpty()) {
            messages.add(ValidationFailure.of(
                    "BPMN container contains nodes unreachable from its start event, container=" + containerId
                    + ", nodeIds=[" + String.join(",", unreachableIds) + "]"));
        }
    }

    private void validateLoop(BpmnModel model, Activity activity, Set<String> processVariableNames,
            Set<String> visibleLexicalVariableNames, List<ValidationFailure> messages) {
        LoopCharacteristics loop = activity.getLoopCharacteristics();
        if (loop == null) {
            return;
        }
        try {
            if (loop instanceof MultiInstanceLoopCharacteristics multiInstance) {
                BpmnLoopContract.validate(multiInstance);
                validateMultiInstanceScope(model, activity, multiInstance, processVariableNames,
                        visibleLexicalVariableNames, messages);
            } else if (loop instanceof StandardLoopCharacteristics standard) {
                BpmnLoopContract.validate(standard);
                if (StringUtils.isNotBlank(standard.getLoopCondition())) {
                    validateConditionExpression("BPMN standard loop condition on activity " + activity.getId(),
                            standard.getLoopCondition(), messages);
                }
            } else {
                messages.add(ValidationFailure.of(
                        "Unsupported BPMN loop characteristics on activity " + activity.getId() + ": " + loop
                            .getClass()
                            .getName()));
            }
        } catch (CompileFlowException exception) {
            messages.add(ValidationFailure.of(exception.getMessage()));
        }
    }

    private void validateMultiInstanceScope(BpmnModel model, Activity activity, MultiInstanceLoopCharacteristics loop,
            Set<String> processVariableNames, Set<String> visibleLexicalVariableNames,
            List<ValidationFailure> messages) {
        String collection = loop.getCollection();
        if (!processVariableNames.contains(collection) && !visibleLexicalVariableNames.contains(collection)) {
            messages.add(ValidationFailure.of(
                    "BPMN multi-instance cf:collection must reference a declared process variable or an enclosing multi-instance variable, id="
                    + activity.getId() + ", collection=" + collection));
        }

        Set<String> unavailableLocalNames = new LinkedHashSet<>(processVariableNames);
        unavailableLocalNames.addAll(visibleLexicalVariableNames);
        rejectLexicalCollision(activity.getId(), "cf:item", loop.getItem(), unavailableLocalNames, messages);
        rejectLexicalCollision(activity.getId(), "cf:index", loop.getIndex(), unavailableLocalNames, messages);

        if (StringUtils.isNotBlank(loop.getTarget()) && !processVariableNames.contains(loop.getTarget())) {
            messages.add(ValidationFailure.of(
                    "BPMN multi-instance cf:target must reference a declared process variable, id=" + activity.getId()
                    + ", target=" + loop.getTarget()));
        }
        if (StringUtils.isNotBlank(loop.getSource())) {
            var output = model
                .getVariables()
                .stream()
                .filter(variable -> loop.getSource().equals(variable.getName()))
                .findFirst()
                .orElse(null);
            if (output == null) {
                messages.add(ValidationFailure.of(
                        "BPMN multi-instance cf:source must reference a declared process variable, id="
                        + activity.getId() + ", source=" + loop.getSource()));
            } else if (!ProcessVariableContainer.VARIABLE_TYPE_INNER.equals(output.getInOutType())) {
                messages.add(ValidationFailure.of(
                        "BPMN multi-instance cf:source must reference an inner process variable, id=" + activity.getId()
                        + ", source=" + loop.getSource()));
            }
        }
    }

    private void rejectLexicalCollision(String activityId, String attribute, String variableName,
            Set<String> unavailableNames, List<ValidationFailure> messages) {
        if (StringUtils.isNotBlank(variableName) && unavailableNames.contains(variableName)) {
            messages.add(ValidationFailure.of(
                    "BPMN multi-instance " + attribute + " must not shadow a process or enclosing lexical variable, id="
                    + activityId + ", variable=" + variableName));
        }
    }

    private Set<String> lexicalVariablesInside(Activity activity, Set<String> visibleLexicalVariableNames) {
        if (!(activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics loop)) {
            return visibleLexicalVariableNames;
        }
        Set<String> nested = new LinkedHashSet<>(visibleLexicalVariableNames);
        if (StringUtils.isNotBlank(loop.getItem())) {
            nested.add(loop.getItem());
        }
        if (StringUtils.isNotBlank(loop.getIndex())) {
            nested.add(loop.getIndex());
        }
        return Set.copyOf(nested);
    }
}
