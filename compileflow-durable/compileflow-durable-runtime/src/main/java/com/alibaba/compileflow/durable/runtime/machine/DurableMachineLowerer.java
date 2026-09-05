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
package com.alibaba.compileflow.durable.runtime.machine;

import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateField;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateSchema;
import com.alibaba.compileflow.engine.core.java.expression.JavaExpressionInspector;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lowers eligibility-checked Process semantics into the one machine shared by every Durable backend.
 *
 * @author yusu
 */
final class DurableMachineLowerer {
    private final DurableExpressionValidator expressionValidator;

    DurableMachineLowerer() {
        this(new DurableJavaExpressionValidator());
    }

    DurableMachineLowerer(DurableExpressionValidator expressionValidator) {
        this.expressionValidator = Objects.requireNonNull(expressionValidator, "expressionValidator");
    }

    DurableMachinePlan lower(ProcessSemanticPlan semantics, StructuredControlFlowPlan structured) {
        ProcessSemanticPlan semanticPlan = Objects.requireNonNull(semantics, "semantics");
        StructuredControlFlowPlan structuredPlan = Objects.requireNonNull(structured, "structured");
        Map<String, DurableMachinePlan.Step> steps = new LinkedHashMap<>();
        Map<String, DurableMachinePlan.Iteration> iterations = new LinkedHashMap<>();
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.iteration() != null) {
                iterations.put(node.id(), lowerIteration(semanticPlan, node));
            }
            steps.put(node.id(), lowerStep(semanticPlan, structuredPlan, node));
        }
        ProcessStateSchema stateSchema = stateSchema(semanticPlan);
        return new DurableMachinePlan(semanticPlan, structuredPlan.getEntryNodeId(), stateSchema, steps, iterations);
    }

    private DurableMachinePlan.Step lowerStep(ProcessSemanticPlan semantics, StructuredControlFlowPlan structured,
            ProcessSemanticPlan.NodePlan node) {
        if (node.iteration() != null) {
            return new DurableMachinePlan.Step.EnterIteration(node.id());
        }
        if (node.kind() == ProcessSemanticPlan.NodeKind.END && ProcessSemanticPlan.ROOT_SCOPE_ID.equals(node.scopeId())) {
            return new DurableMachinePlan.Step.Complete();
        }
        if (node.kind() == ProcessSemanticPlan.NodeKind.BREAK) {
            BoundExpression condition = booleanExpression(semantics, node.id(), node.controlCondition());
            return new DurableMachinePlan.Step.BreakIteration(enclosingIteration(semantics, node), condition,
                    condition == null ? null : nextAfter(semantics, node));
        }
        if (node.kind() == ProcessSemanticPlan.NodeKind.CONTINUE) {
            BoundExpression condition = booleanExpression(semantics, node.id(), node.controlCondition());
            return new DurableMachinePlan.Step.ContinueIteration(enclosingIteration(semantics, node), condition,
                    condition == null ? null : nextAfter(semantics, node));
        }
        if (isGateway(node)) {
            GatewayPlan gateway = structured.requireGatewayPlan(node.id());
            if (gateway.isJoin()) {
                return new DurableMachinePlan.Step.Advance(nextAfter(semantics, node));
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY) {
                return chooseOne(semantics, node);
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY) {
                return forkAll(node, gateway);
            }
            return forkSelected(semantics, node, gateway);
        }
        if (node.scopeBoundary() != null) {
            return new DurableMachinePlan.Step.Advance(
                    new DurableMachinePlan.Next.Node(node.scopeBoundary().startNodeId()));
        }

        DurableMachinePlan.Next next = nextAfter(semantics, node);
        OperationPlan operation = node.operation();
        if (operation == null) {
            return new DurableMachinePlan.Step.Advance(next);
        }
        if (operation instanceof ActionPlan action) {
            return action.execution() == ActionExecution.REPLAYABLE
                    ? new DurableMachinePlan.Step.Replayable(next)
                    : new DurableMachinePlan.Step.Effect(next);
        }
        if (operation instanceof AwaitPlan) {
            return new DurableMachinePlan.Step.Await(next);
        }
        if (operation instanceof TimerPlan timer) {
            BoundExpression schedule = switch (timer.kind()) {
                case DURATION_LITERAL -> null;
                case DURATION_EXPRESSION -> boundExpression(semantics, node.id(), timer.value(),
                        BoundExpression.ResultKind.DURATION);
                case WAKE_AT_LITERAL -> null;
                case WAKE_AT_EXPRESSION -> boundExpression(semantics, node.id(), timer.value(),
                        BoundExpression.ResultKind.INSTANT);
            };
            return new DurableMachinePlan.Step.Timer(schedule, next);
        }
        if (operation instanceof ProcessCallPlan) {
            return new DurableMachinePlan.Step.ProcessCall(next);
        }
        throw unsupported(operation);
    }

    private DurableMachinePlan.Step chooseOne(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node) {
        List<DurableMachinePlan.ConditionalBranch> branches = new ArrayList<>();
        String defaultTarget = null;
        for (int ordinal = 0; ordinal < node.outgoingTransitions().size(); ordinal++) {
            ProcessSemanticPlan.TransitionPlan transition = node.outgoingTransitions().get(ordinal);
            if (isDefault(transition)) {
                defaultTarget = uniqueDefault(node.id(), defaultTarget, transition.targetId());
            } else {
                branches.add(
                        new DurableMachinePlan.ConditionalBranch(ordinal,
                                boundExpression(semantics, node.id(), transition.condition(),
                                        BoundExpression.ResultKind.BOOLEAN), transition.targetId()));
            }
        }
        return new DurableMachinePlan.Step.ChooseOne(branches, defaultTarget);
    }

    private DurableMachinePlan.Step forkAll(ProcessSemanticPlan.NodePlan node, GatewayPlan gateway) {
        for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
            if (transition.condition() != null || transition.defaultFlow()) {
                throw new IllegalArgumentException(
                        "Parallel gateway '" + node.id() + "' cannot declare conditional or default flows");
            }
        }
        return new DurableMachinePlan.Step.ForkAll(gateway.getConvergenceNodeId(),
                java.util.stream.IntStream
                    .range(0, node.outgoingTransitions().size())
                    .mapToObj(ordinal -> new BranchActivation(ordinal,
                            node.outgoingTransitions().get(ordinal).targetId()))
                    .toList());
    }

    private DurableMachinePlan.Step forkSelected(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            GatewayPlan gateway) {
        List<DurableMachinePlan.ConditionalBranch> branches = new ArrayList<>();
        BranchActivation defaultActivation = null;
        for (int ordinal = 0; ordinal < node.outgoingTransitions().size(); ordinal++) {
            ProcessSemanticPlan.TransitionPlan transition = node.outgoingTransitions().get(ordinal);
            if (isDefault(transition)) {
                if (defaultActivation != null) {
                    throw new IllegalArgumentException(
                            "Gateway '" + node.id() + "' declares more than one default transition");
                }
                defaultActivation = new BranchActivation(ordinal, transition.targetId());
            } else {
                branches.add(
                        new DurableMachinePlan.ConditionalBranch(ordinal,
                                boundExpression(semantics, node.id(), transition.condition(),
                                        BoundExpression.ResultKind.BOOLEAN), transition.targetId()));
            }
        }
        return new DurableMachinePlan.Step.ForkSelected(gateway.getConvergenceNodeId(), branches, defaultActivation);
    }

    private DurableMachinePlan.Iteration lowerIteration(ProcessSemanticPlan semantics,
            ProcessSemanticPlan.NodePlan owner) {
        DurableMachinePlan.Body body = iterationBody(semantics, owner);
        DurableMachinePlan.Next exit = nextAfter(semantics, owner);
        if (owner.iteration() instanceof IterationPlan.While loop) {
            return new DurableMachinePlan.Iteration.While(boundExpression(semantics, owner.id(), loop.condition(),
                            BoundExpression.ResultKind.BOOLEAN), loop.timing(), loop.maxIterations(),
                    loop.limitBehavior(), loop.indexVariable(), body, exit);
        }
        if (owner.iteration() instanceof IterationPlan.ForEach loop) {
            return new DurableMachinePlan.Iteration.ForEach(loop.collectionVariable(),
                    DurableMachinePlan.collectionOwnerIterationId(semantics, owner, loop.collectionVariable()),
                    loop.itemVariable(), loop.itemType(), loop.indexVariable(), loop.execution(),
                    loop.outputSourceVariable(), loop.outputTargetVariable(), body, exit);
        }
        throw unsupported(owner.iteration());
    }

    private DurableMachinePlan.Body iterationBody(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan owner) {
        if (owner.scopeBoundary() != null) {
            return new DurableMachinePlan.Body.Scope(owner.scopeBoundary().startNodeId());
        }
        if (!(owner.operation() instanceof TimerPlan timer) || timer.isLiteral()) {
            return new DurableMachinePlan.Body.Operation(null);
        }
        BoundExpression.ResultKind kind = timer.kind() == TimerPlan.Kind.DURATION_EXPRESSION
                ? BoundExpression.ResultKind.DURATION
                : BoundExpression.ResultKind.INSTANT;
        return new DurableMachinePlan.Body.Operation(boundExpression(semantics, owner.id(), timer.value(), kind));
    }

    private BoundExpression booleanExpression(ProcessSemanticPlan semantics, String nodeId, String expression) {
        return expression == null
                ? null
                : boundExpression(semantics, nodeId, expression, BoundExpression.ResultKind.BOOLEAN);
    }

    private BoundExpression boundExpression(ProcessSemanticPlan semantics, String nodeId, String expression,
            BoundExpression.ResultKind resultKind) {
        Map<String, ProcessSemanticPlan.VisibleVariable> visible = semantics.visibleVariables(nodeId);
        Map<String, String> visibleTypes = new LinkedHashMap<>();
        visible.forEach((name, variable) -> visibleTypes.put(name, variable.typeName()));
        DurableExpressionValidator.TargetType targetType = switch (resultKind) {
            case BOOLEAN -> DurableExpressionValidator.TargetType.BOOLEAN;
            case DURATION -> DurableExpressionValidator.TargetType.DURATION;
            case INSTANT -> DurableExpressionValidator.TargetType.INSTANT;
        };
        List<String> problems = expressionValidator.validate(expression, visibleTypes, targetType);
        if (!problems.isEmpty()) {
            throw new DurableModelEligibilityException(
                    new DurableModelEligibility(problems
                        .stream()
                        .map(message -> new DurableModelEligibility.Problem("DURABLE_EXPRESSION_UNSAFE", nodeId, message))
                        .toList()));
        }
        Set<String> references = JavaExpressionInspector.referencedIdentifiers(expression, visible.keySet());
        List<BoundExpression.Binding> bindings = references.stream().map(name -> {
            ProcessSemanticPlan.VisibleVariable variable = visible.get(name);
            BoundExpression.Binding.Source source = variable.origin() == ProcessSemanticPlan.VisibleVariable.Origin.PROCESS
                    ? BoundExpression.Binding.Source.STATE
                    : BoundExpression.Binding.Source.FRAME;
            return new BoundExpression.Binding(name, variable.typeName(), source);
        }).toList();
        return new BoundExpression(expression, resultKind, bindings);
    }

    private DurableMachinePlan.Next nextAfter(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan source) {
        return nextAfter(semantics, source, new LinkedHashSet<>());
    }

    private DurableMachinePlan.Next nextAfter(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan source,
            Set<String> visitedScopeEnds) {
        if (!ProcessSemanticPlan.ROOT_SCOPE_ID.equals(source.scopeId())) {
            ProcessSemanticPlan.NodePlan owner = semantics.requireNode(source.scopeId());
            if (source.id().equals(owner.scopeBoundary().endNodeId())) {
                if (!visitedScopeEnds.add(source.id())) {
                    throw new IllegalArgumentException(
                            "Cyclic nested-scope exit normalization at node '" + source.id() + "'");
                }
                if (owner.iteration() != null) {
                    return new DurableMachinePlan.Next.AdvanceIteration(owner.id());
                }
                return nextAfter(semantics, owner, visitedScopeEnds);
            }
        }
        if (source.outgoingTransitions().size() != 1) {
            throw new IllegalArgumentException(
                    "Node '" + source.id()
                    + "' must have exactly one outgoing transition after Durable lowering, found "
                    + source.outgoingTransitions().size());
        }
        return new DurableMachinePlan.Next.Node(source.outgoingTransitions().get(0).targetId());
    }

    private String enclosingIteration(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node) {
        for (
                String scope = node.scopeId();
                !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scope);
                scope = semantics.parentScope(scope)) {
            if (semantics.requireNode(scope).iteration() != null) {
                return scope;
            }
        }
        throw new IllegalArgumentException(node.kind() + " node '" + node.id() + "' is not enclosed by an iteration");
    }

    private ProcessStateSchema stateSchema(ProcessSemanticPlan semantics) {
        return new ProcessStateSchema(semantics
            .getVariables()
            .values()
            .stream()
            .map(variable -> new ProcessStateField(variable.name(), variable.dataType(), true,
                    variable.role() == ProcessSemanticPlan.VariableRole.PARAM))
            .toList());
    }

    private static boolean isGateway(ProcessSemanticPlan.NodePlan node) {
        return node.kind() == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY
                || node.kind() == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY
                || node.kind() == ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY;
    }

    private static boolean isDefault(ProcessSemanticPlan.TransitionPlan transition) {
        return transition.defaultFlow() || transition.condition() == null;
    }

    private static String uniqueDefault(String nodeId, String current, String candidate) {
        if (current != null) {
            throw new IllegalArgumentException("Gateway '" + nodeId + "' declares more than one default flow");
        }
        return candidate;
    }

    private static IllegalStateException unsupported(Object value) {
        return new IllegalStateException(
                "Unsupported Durable lowering variant " + (value == null ? "null" : value.getClass().getName()));
    }
}
