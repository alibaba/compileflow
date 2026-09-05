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
package com.alibaba.compileflow.engine.tbbpm.semantic;

import com.alibaba.compileflow.engine.core.model.DesignTimeElement;
import com.alibaba.compileflow.engine.core.model.ForEachElement;
import com.alibaba.compileflow.engine.core.model.WhileElement;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.ExclusiveGatewayElement;
import com.alibaba.compileflow.engine.core.model.HasCondition;
import com.alibaba.compileflow.engine.core.model.InclusiveGatewayElement;
import com.alibaba.compileflow.engine.core.model.LoopControlElement;
import com.alibaba.compileflow.engine.core.model.ParallelGatewayElement;
import com.alibaba.compileflow.engine.core.model.ProcessCallModel;
import com.alibaba.compileflow.engine.core.model.StartElement;
import com.alibaba.compileflow.engine.core.model.TriggerEntryElement;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.SemanticActionNormalizer;
import com.alibaba.compileflow.engine.core.semantic.SemanticProcessNormalizer;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.StructuredScopeNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmNodeContainer;
import com.alibaba.compileflow.engine.tbbpm.model.TimerTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitEventTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitTaskNode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles TBBPM authoring objects into the shared Process semantic representation.
 *
 * @author yusu
 */
public final class TbbpmSemanticFrontend {
    /**
     * Normalizes a source-validated TBBPM model exactly once.
     */
    public ProcessSemanticPlan compile(TbbpmModel model) {
        Objects.requireNonNull(model, "model");
        Map<String, ProcessSemanticPlan.VariablePlan> variables =
                SemanticProcessNormalizer.variables(model.getVariables());
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        Deque<ContainerWork> pending = new ArrayDeque<>();
        pending.addLast(new ContainerWork(model.getAllNodes(), ProcessSemanticPlan.ROOT_SCOPE_ID));
        while (!pending.isEmpty()) {
            ContainerWork work = pending.removeFirst();
            for (FlowNode node : work.nodes()) {
                if (node instanceof DesignTimeElement) {
                    continue;
                }
                ProcessSemanticPlan.NodePlan plan = nodePlan(node, work.scopeId(), variables);
                put(nodes, plan);
                if (node instanceof TbbpmNodeContainer container) {
                    pending.addLast(new ContainerWork(container.getAllNodes(), node.getId()));
                }
            }
        }
        return new ProcessSemanticPlan(model.getCode(), variables, nodes);
    }

    private ProcessSemanticPlan.NodePlan nodePlan(FlowNode node, String scopeId,
            Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        ProcessSemanticPlan.NodeKind kind = kind(node);
        return new ProcessSemanticPlan.NodePlan(node.getId(), kind, scopeId, scopeBoundary(node), transitions(node),
                controlCondition(node, kind), operation(node, variables), iteration(node));
    }

    private ProcessSemanticPlan.ScopeBoundary scopeBoundary(FlowNode node) {
        if (node instanceof StructuredScopeNode scope) {
            return new ProcessSemanticPlan.ScopeBoundary(scope.getStartNode().getId(), scope.getEndNode().getId());
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
        if (node instanceof LoopControlElement control) {
            return control.getLoopControlKind() == LoopControlElement.Kind.BREAK
                    ? ProcessSemanticPlan.NodeKind.BREAK
                    : ProcessSemanticPlan.NodeKind.CONTINUE;
        }
        return ProcessSemanticPlan.NodeKind.ACTIVITY;
    }

    private List<ProcessSemanticPlan.TransitionPlan> transitions(FlowNode node) {
        boolean conditionalGateway =
                node instanceof ExclusiveGatewayElement<?> || node instanceof InclusiveGatewayElement<?>;
        return node
            .getOutgoingTransitions()
            .stream()
            .map(transition -> new ProcessSemanticPlan.TransitionPlan(transition.getTarget(), transition.getCondition(),
                    conditionalGateway && !text(transition.getCondition())))
            .toList();
    }

    private String controlCondition(FlowNode node, ProcessSemanticPlan.NodeKind kind) {
        return kind == ProcessSemanticPlan.NodeKind.BREAK || kind == ProcessSemanticPlan.NodeKind.CONTINUE
                ? ((HasCondition) node).getCondition()
                : null;
    }

    private OperationPlan operation(FlowNode node, Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        if (node instanceof HasAction actionOwner && actionOwner.getAction() != null) {
            return SemanticActionNormalizer.normalize(actionOwner.getAction(), variables);
        }
        if (node instanceof TriggerEntryElement) {
            String event = node instanceof WaitEventTaskNode waitEvent ? waitEvent.getEvent() : null;
            String timeout = node instanceof WaitEventTaskNode waitEvent
                    ? waitEvent.getTimeout()
                    : node instanceof WaitTaskNode wait ? wait.getTimeout() : null;
            return new AwaitPlan(event, timeout == null ? null : Duration.parse(timeout));
        }
        if (node instanceof TimerTaskNode timer) {
            if (text(timer.getDuration())) {
                return new TimerPlan(TimerPlan.Kind.DURATION_LITERAL, timer.getDuration());
            }
            if (text(timer.getDurationExpression())) {
                return new TimerPlan(TimerPlan.Kind.DURATION_EXPRESSION, timer.getDurationExpression());
            }
            return new TimerPlan(TimerPlan.Kind.WAKE_AT_EXPRESSION, timer.getWakeAtExpression());
        }
        if (node instanceof ProcessCallModel call) {
            return SemanticProcessNormalizer.processCall(call, variables);
        }
        return null;
    }

    private IterationPlan iteration(FlowNode node) {
        if (node instanceof WhileElement loop) {
            return new IterationPlan.While(loop.getCondition(), IterationPlan.ConditionTiming.BEFORE,
                    loop.getMaxIterations(), IterationPlan.LimitBehavior.FAIL, loop.getIndex());
        }
        if (node instanceof ForEachElement loop) {
            return new IterationPlan.ForEach(loop.getCollection(), loop.getItem(), loop.getItemType(), loop.getIndex(),
                    loop.getExecution() == ForEachElement.Execution.PARALLEL
                    ? IterationPlan.Execution.PARALLEL
                    : IterationPlan.Execution.SEQUENTIAL, loop.getOutputSource(), loop.getOutputTarget());
        }
        return null;
    }

    private static void put(Map<String, ProcessSemanticPlan.NodePlan> nodes, ProcessSemanticPlan.NodePlan plan) {
        if (nodes.putIfAbsent(plan.id(), plan) != null) {
            throw new IllegalArgumentException("Duplicate executable node '" + plan.id() + "'");
        }
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private record ContainerWork(List<FlowNode> nodes, String scopeId) {}
}
