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
package com.alibaba.compileflow.engine.bpmn.semantic;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.Activity;
import com.alibaba.compileflow.engine.bpmn.model.BpmnElementContainer;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.ConditionalGateway;
import com.alibaba.compileflow.engine.bpmn.model.FlowNode;
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.LoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.ExclusiveGatewayElement;
import com.alibaba.compileflow.engine.core.model.InclusiveGatewayElement;
import com.alibaba.compileflow.engine.core.model.ParallelGatewayElement;
import com.alibaba.compileflow.engine.core.model.ProcessCallModel;
import com.alibaba.compileflow.engine.core.model.StartElement;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.SemanticActionNormalizer;
import com.alibaba.compileflow.engine.core.semantic.SemanticProcessNormalizer;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles BPMN authoring objects into the shared Process semantic representation.
 *
 * @author yusu
 */
public final class BpmnSemanticFrontend {
    /**
     * Normalizes a source-validated BPMN model exactly once.
     */
    public ProcessSemanticPlan compile(BpmnModel model) {
        Objects.requireNonNull(model, "model");
        Map<String, ProcessSemanticPlan.VariablePlan> variables =
                SemanticProcessNormalizer.variables(model.getVariables());
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        Deque<ContainerWork> pending = new ArrayDeque<>();
        pending.addLast(new ContainerWork(model.getProcess(), ProcessSemanticPlan.ROOT_SCOPE_ID));
        while (!pending.isEmpty()) {
            ContainerWork work = pending.removeFirst();
            for (FlowNode node : work.container().getAllNodes()) {
                ProcessSemanticPlan.NodePlan plan = nodePlan(model, node, work.scopeId(), variables);
                put(nodes, plan);
                if (node instanceof SubProcess subProcess) {
                    pending.addLast(new ContainerWork(subProcess, node.getId()));
                }
            }
        }
        return new ProcessSemanticPlan(model.getCode(), variables, nodes);
    }

    private ProcessSemanticPlan.NodePlan nodePlan(BpmnModel model, FlowNode node, String scopeId,
            Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        return new ProcessSemanticPlan.NodePlan(node.getId(), kind(node), scopeId, scopeBoundary(node),
                transitions(node), null, operation(model, node, variables), iteration(node));
    }

    private ProcessSemanticPlan.ScopeBoundary scopeBoundary(FlowNode node) {
        if (node instanceof SubProcess subProcess) {
            return new ProcessSemanticPlan.ScopeBoundary(subProcess.getStartNode().getId(),
                    subProcess.getEndNode().getId());
        }
        return null;
    }

    private ProcessSemanticPlan.NodeKind kind(FlowNode node) {
        if (node instanceof StartElement) {
            return ProcessSemanticPlan.NodeKind.START;
        }
        if (node instanceof EndElement) {
            return ProcessSemanticPlan.NodeKind.END;
        }
        if (node instanceof ExclusiveGatewayElement<?>) {
            return ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY;
        }
        if (node instanceof ParallelGatewayElement<?>) {
            return ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY;
        }
        if (node instanceof InclusiveGatewayElement<?>) {
            return ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY;
        }
        return ProcessSemanticPlan.NodeKind.ACTIVITY;
    }

    private List<ProcessSemanticPlan.TransitionPlan> transitions(FlowNode node) {
        String defaultFlowId = node instanceof ConditionalGateway conditional ? conditional.getDefaultFlowId() : null;
        return node
            .getOutgoingTransitions()
            .stream()
            .map(transition -> new ProcessSemanticPlan.TransitionPlan(transition.getTarget(), transition.getCondition(),
                    Objects.equals(defaultFlowId, transition.getId())))
            .toList();
    }

    private OperationPlan operation(BpmnModel model, FlowNode node,
            Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        if (node instanceof ScriptTask scriptTask) {
            return scriptAction(scriptTask, variables);
        }
        if (node instanceof HasAction actionOwner && actionOwner.getAction() != null) {
            return SemanticActionNormalizer.normalize(actionOwner.getAction(), variables);
        }
        if (node instanceof ReceiveTask receiveTask) {
            return new AwaitPlan(messageName(model, receiveTask.getMessageRef()), null);
        }
        if (node instanceof IntermediateCatchEvent catchEvent) {
            if (catchEvent.getMessageEventDefinition() != null) {
                return new AwaitPlan(messageName(model, catchEvent.getMessageEventDefinition().getMessageRef()), null);
            }
            TimerValue timer = catchEvent.getTimerEventDefinition().getValue();
            return switch (timer.kind()) {
                case DURATION -> new TimerPlan(timer.expression()
                        ? TimerPlan.Kind.DURATION_EXPRESSION
                        : TimerPlan.Kind.DURATION_LITERAL, timer.value());
                case DATE -> {
                    yield new TimerPlan(timer.expression()
                            ? TimerPlan.Kind.WAKE_AT_EXPRESSION
                            : TimerPlan.Kind.WAKE_AT_LITERAL, timer.value());
                }
                case CYCLE -> throw new CompileFlowException(ErrorCode.CF_VALIDATION_006,
                        "BPMN timeCycle is not supported by CompileFlow Process semantics")
                    .withContext("semantic", "timeCycle")
                    .withContext("nodeId", node.getId());
            };
        }
        if (node instanceof ProcessCallModel call) {
            return SemanticProcessNormalizer.processCall(call, variables);
        }
        return null;
    }

    private ActionPlan scriptAction(ScriptTask scriptTask, Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        Action action = new Action();
        action.setType(ActionType.SCRIPT);
        action.setLanguage(scriptTask.getScriptFormat());
        action.setSource(scriptTask.getScript());
        action.setInputMappings(scriptTask.getInputMappings());
        action.setOutputMappings(scriptTask.getOutputMappings());
        action.setExecution(scriptTask.getExecution());
        action.setInvocationPolicy(scriptTask.getInvocationPolicy());
        action.setEffectPolicy(scriptTask.getEffectPolicy());
        return SemanticActionNormalizer.normalize(action, variables);
    }

    private String messageName(BpmnModel model, String messageRef) {
        if (messageRef == null || messageRef.isBlank()) {
            return null;
        }
        return model.getFlowElement(messageRef, Message.class).getName();
    }

    private IterationPlan iteration(FlowNode node) {
        if (!(node instanceof Activity activity)) {
            return null;
        }
        LoopCharacteristics loop = activity.getLoopCharacteristics();
        if (loop instanceof StandardLoopCharacteristics standard) {
            Integer maxIterations =
                    standard.getLoopMaximum() == null ? null : Math.toIntExact(standard.getLoopMaximum());
            String condition = standard.getLoopCondition() == null || standard.getLoopCondition().isBlank()
                    ? "true"
                    : standard.getLoopCondition();
            return new IterationPlan.While(condition,
                    Boolean.TRUE.equals(standard.getTestBefore())
                    ? IterationPlan.ConditionTiming.BEFORE
                    : IterationPlan.ConditionTiming.AFTER, maxIterations, IterationPlan.LimitBehavior.STOP, null);
        }
        if (loop instanceof MultiInstanceLoopCharacteristics multiInstance) {
            return new IterationPlan.ForEach(multiInstance.getCollection(), multiInstance.getItem(),
                    multiInstance.getItemType() == null ? Object.class.getName() : multiInstance.getItemType(),
                    multiInstance.getIndex(),
                    multiInstance.isSequential() ? IterationPlan.Execution.SEQUENTIAL : IterationPlan.Execution.PARALLEL,
                    multiInstance.getSource(), multiInstance.getTarget());
        }
        return null;
    }

    private static void put(Map<String, ProcessSemanticPlan.NodePlan> nodes, ProcessSemanticPlan.NodePlan plan) {
        if (nodes.putIfAbsent(plan.id(), plan) != null) {
            throw new IllegalArgumentException("Duplicate executable node '" + plan.id() + "'");
        }
    }

    private record ContainerWork(BpmnElementContainer container, String scopeId) {}
}
