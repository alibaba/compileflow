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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessExecutionException;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchKey;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchPlan;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.core.runtime.action.ProcessActionInvoker;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.execution.ActionExecutor;
import com.alibaba.compileflow.engine.core.runtime.execution.GatewayExecutor;
import com.alibaba.compileflow.engine.core.runtime.execution.LoopSemantics;
import com.alibaba.compileflow.engine.core.runtime.execution.ProcessCallOutputs;
import com.alibaba.compileflow.engine.core.runtime.execution.TriggerValidation;
import com.alibaba.compileflow.engine.core.runtime.expression.CompiledExpressionEvaluator;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct control-flow interpreter over shared Process semantics and structured graph facts.
 *
 * @author yusu
 */
final class InterpretedProcessRuntime implements ProcessRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterpretedProcessRuntime.class);
    private final ProcessSemanticPlan semanticPlan;
    private final StructuredControlFlowPlan structuredPlan;
    private final InterpretedExpressionCatalog expressionCatalog;
    private final CompiledExpressionEvaluator expressions;
    private final ProcessActionInvoker actions;
    private final ClassLoader classLoader;
    private final String entryNodeId;

    InterpretedProcessRuntime(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan,
            InterpretedExpressionCatalog expressionCatalog, CompiledExpressionEvaluator expressions,
            ProcessActionInvoker actions, ClassLoader classLoader) {
        this.semanticPlan = Objects.requireNonNull(semanticPlan, "semanticPlan");
        this.structuredPlan = Objects.requireNonNull(structuredPlan, "structuredPlan");
        this.expressionCatalog = Objects.requireNonNull(expressionCatalog, "expressionCatalog");
        this.expressions = Objects.requireNonNull(expressions, "expressions");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.entryNodeId = structuredPlan.getEntryNodeId();
    }

    @Override
    public ProcessSemanticPlan getSemanticPlan() {
        return semanticPlan;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> variables) {
        return invoke("execute", () -> {
            Map<String, Object> state = loadParameters(variables);
            executeUntil(entryNodeId, null, Invocation.execute(), state, Map.of());
            return result(state);
        });
    }

    @Override
    public Map<String, Object> trigger(ProcessTrigger trigger, Map<String, Object> variables) {
        ProcessTrigger requested = Objects.requireNonNull(trigger, "trigger");
        return invoke("trigger", () -> {
            ProcessSemanticPlan.NodePlan entry = semanticPlan.requireNode(requested.nodeId());
            if (!(entry.operation() instanceof AwaitPlan)) {
                throw TriggerValidation.unknownNodeId(requested.nodeId());
            }
            Map<String, Object> state = restoreState(variables);
            executeUntil(entry.id(), null, Invocation.trigger(requested), state, Map.of());
            return result(state);
        });
    }

    private Map<String, Object> loadParameters(Map<String, Object> supplied) {
        return initializeState(supplied, false);
    }

    private Map<String, Object> restoreState(Map<String, Object> supplied) {
        return initializeState(supplied, true);
    }

    private Map<String, Object> initializeState(Map<String, Object> supplied, boolean includeStateVariables) {
        Map<String, Object> input = Objects.requireNonNull(supplied, "variables");
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        for (ProcessSemanticPlan.VariablePlan variable : semanticPlan.getVariables().values()) {
            state.put(variable.name(), initialValue(variable));
        }
        for (ProcessSemanticPlan.VariablePlan variable : semanticPlan.getVariables().values()) {
            if ((!includeStateVariables && variable.role() != ProcessSemanticPlan.VariableRole.PARAM)
                    || !input.containsKey(variable.name())) {
                continue;
            }
            state.put(variable.name(), convert(input.get(variable.name()), variable.dataType()));
        }
        return state;
    }

    private Signal executeUntil(String startNodeId, String boundaryNodeId, Invocation invocation,
            Map<String, Object> state, Map<String, Object> lexicalBindings) throws Exception {
        return executeRange(startNodeId, boundaryNodeId, false, invocation, state, lexicalBindings);
    }

    private Signal executeThrough(String startNodeId, String boundaryNodeId, Invocation invocation,
            Map<String, Object> state, Map<String, Object> lexicalBindings) throws Exception {
        return executeRange(startNodeId, boundaryNodeId, true, invocation, state, lexicalBindings);
    }

    private Signal executeRange(String startNodeId, String boundaryNodeId, boolean includeBoundary,
            Invocation invocation, Map<String, Object> state, Map<String, Object> lexicalBindings) throws Exception {
        String current = startNodeId;
        while (current != null && (includeBoundary || !current.equals(boundaryNodeId))) {
            ProcessSemanticPlan.NodePlan node = semanticPlan.requireNode(current);
            if (node.kind() == ProcessSemanticPlan.NodeKind.END) {
                return Signal.NORMAL;
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.BREAK
                    || node.kind() == ProcessSemanticPlan.NodeKind.CONTINUE) {
                boolean taken = node.controlCondition() == null
                        || evaluateCondition(node.id(), node.controlCondition(), state, lexicalBindings);
                if (taken) {
                    return node.kind() == ProcessSemanticPlan.NodeKind.BREAK ? Signal.BREAK : Signal.CONTINUE;
                }
                if (node.outgoingTransitions().isEmpty()) {
                    return Signal.NORMAL;
                }
                if (includeBoundary && node.id().equals(boundaryNodeId)) {
                    return Signal.NORMAL;
                }
                current = onlyOutgoing(node);
                continue;
            }
            GatewayPlan gateway = structuredPlan.getGatewayPlan(node.id());
            if (gateway != null) {
                if (gateway.isJoin()) {
                    if (includeBoundary && node.id().equals(boundaryNodeId)) {
                        return Signal.NORMAL;
                    }
                    current = onlyOutgoing(node);
                    continue;
                }
                Signal signal = executeGateway(node, gateway, invocation, state, lexicalBindings);
                if (signal != Signal.NORMAL) {
                    return signal;
                }
                if (includeBoundary && node.id().equals(boundaryNodeId)) {
                    return Signal.NORMAL;
                }
                current = gateway.getConvergenceNodeId();
                continue;
            }
            if (node.kind() == ProcessSemanticPlan.NodeKind.ACTIVITY) {
                Signal signal = executeActivity(node, invocation, state, lexicalBindings);
                if (signal != Signal.NORMAL) {
                    return signal;
                }
            }
            if (includeBoundary && node.id().equals(boundaryNodeId)) {
                return Signal.NORMAL;
            }
            current = onlyOutgoing(node);
        }
        return Signal.NORMAL;
    }

    private Signal executeGateway(ProcessSemanticPlan.NodePlan gatewayNode, GatewayPlan gateway, Invocation invocation,
            Map<String, Object> state, Map<String, Object> lexicalBindings) throws Exception {
        List<SelectedBranch> selected = selectedBranches(gatewayNode, state, lexicalBindings);
        if (gatewayNode.kind() == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY) {
            SelectedBranch branch = selected.get(0);
            return executeUntil(branch.transition().targetId(), gateway.getConvergenceNodeId(), invocation, state,
                    lexicalBindings);
        }

        GatewayExecutor.ParallelBranches<BranchFrame> branches = GatewayExecutor.parallel(gatewayNode.id());
        for (SelectedBranch branch : selected) {
            branches.branch(branch.ordinal(), branch.transition().targetId(), () -> {
                LinkedHashMap<String, Object> branchState = new LinkedHashMap<>(state);
                LinkedHashMap<String, Object> branchLexical = new LinkedHashMap<>(lexicalBindings);
                Signal signal = executeUntil(branch.transition().targetId(), gateway.getConvergenceNodeId(),
                        invocation.copy(), branchState, branchLexical);
                return new BranchFrame(branchState, signal);
            });
        }
        List<GatewayExecutor.BranchResult<BranchFrame>> results = branches.run();
        for (GatewayExecutor.BranchResult<BranchFrame> result : results) {
            if (result.value().signal() != Signal.NORMAL) {
                return result.value().signal();
            }
            GatewayBranchKey key = new GatewayBranchKey(gatewayNode.id(), result.branchId().targetId());
            GatewayBranchPlan branchPlan = gateway.requireBranch(key);
            for (String variable : semanticPlan.getVariables().keySet()) {
                if (branchPlan.getWrites().contains(variable)) {
                    state.put(variable, result.value().state().get(variable));
                }
            }
        }
        return Signal.NORMAL;
    }

    private List<SelectedBranch> selectedBranches(ProcessSemanticPlan.NodePlan gateway, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        List<ProcessSemanticPlan.TransitionPlan> transitions = gateway.outgoingTransitions();
        if (gateway.kind() == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY) {
            List<SelectedBranch> result = new ArrayList<>(transitions.size());
            for (int ordinal = 0; ordinal < transitions.size(); ordinal++) {
                result.add(new SelectedBranch(ordinal, transitions.get(ordinal)));
            }
            return List.copyOf(result);
        }

        ProcessSemanticPlan.TransitionPlan fallback = null;
        int fallbackOrdinal = -1;
        List<SelectedBranch> matched = new ArrayList<>();
        for (int ordinal = 0; ordinal < transitions.size(); ordinal++) {
            ProcessSemanticPlan.TransitionPlan transition = transitions.get(ordinal);
            if (transition.defaultFlow() || transition.condition() == null) {
                if (fallback == null) {
                    fallback = transition;
                    fallbackOrdinal = ordinal;
                }
                continue;
            }
            if (evaluateCondition(gateway.id(), transition.condition(), state, lexicalBindings)) {
                matched.add(new SelectedBranch(ordinal, transition));
                if (gateway.kind() == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY) {
                    return List.copyOf(matched);
                }
            }
        }
        if (matched.isEmpty() && fallback != null) {
            matched.add(new SelectedBranch(fallbackOrdinal, fallback));
        }
        if (matched.isEmpty()) {
            throw GatewayExecutor.noBranchMatched(gateway.id());
        }
        return List.copyOf(matched);
    }

    private Signal executeActivity(ProcessSemanticPlan.NodePlan node, Invocation invocation, Map<String, Object> state,
            Map<String, Object> lexicalBindings) throws Exception {
        if (node.operation() instanceof AwaitPlan await) {
            if (invocation.consume(node.id())) {
                if (await.event() != null) {
                    TriggerValidation.requireEvent(node.id(), await.event(), invocation.event());
                }
            } else {
                return Signal.PAUSED;
            }
        }
        if (node.operation() instanceof TimerPlan) {
            throw new IllegalStateException("Timer reached the default ProcessRuntime");
        }
        if (node.iteration() instanceof IterationPlan.While loop) {
            return executeWhile(node, loop, invocation, state, lexicalBindings);
        }
        if (node.iteration() instanceof IterationPlan.ForEach loop) {
            return executeForEach(node, loop, invocation, state, lexicalBindings);
        }
        return executeUnit(node, invocation, state, lexicalBindings);
    }

    private Signal executeWhile(ProcessSemanticPlan.NodePlan owner, IterationPlan.While loop, Invocation invocation,
            Map<String, Object> state, Map<String, Object> parentLexical) throws Exception {
        LinkedHashMap<String, Object> lexical = new LinkedHashMap<>(parentLexical);
        int completed = 0;
        if (loop.indexVariable() != null) {
            lexical.put(loop.indexVariable(), completed);
        }
        boolean first = true;
        while (true) {
            if (loop.timing() == IterationPlan.ConditionTiming.BEFORE || !first) {
                if (!shouldRunWhile(owner.id(), loop, completed, state, lexical)) {
                    return Signal.NORMAL;
                }
            } else if (stopLimitReached(loop, completed)) {
                return Signal.NORMAL;
            }
            first = false;
            Signal signal = executeUnit(owner, invocation, state, lexical);
            if (signal == Signal.PAUSED) {
                return signal;
            }
            if (signal == Signal.BREAK) {
                return Signal.NORMAL;
            }
            completed++;
            if (loop.indexVariable() != null) {
                lexical.put(loop.indexVariable(), completed);
            }
        }
    }

    private boolean shouldRunWhile(String nodeId, IterationPlan.While loop, int completed, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        if (stopLimitReached(loop, completed)) {
            return false;
        }
        if (!evaluateLoopCondition(nodeId, loop.condition(), state, lexicalBindings)) {
            return false;
        }
        if (loop.limitBehavior() == IterationPlan.LimitBehavior.FAIL && loop.maxIterations() != null
                && completed >= loop.maxIterations()) {
            LoopSemantics.requireIterationAllowed(nodeId, completed, loop.maxIterations());
        }
        return true;
    }

    private boolean stopLimitReached(IterationPlan.While loop, int completed) {
        return loop.limitBehavior() == IterationPlan.LimitBehavior.STOP && loop.maxIterations() != null
                && completed >= loop.maxIterations();
    }

    private Signal executeForEach(ProcessSemanticPlan.NodePlan owner, IterationPlan.ForEach loop, Invocation invocation,
            Map<String, Object> state, Map<String, Object> parentLexical) throws Exception {
        Object collection = lookup(loop.collectionVariable(), state, parentLexical);
        Class<?> itemType = DataTypes.getJavaClass(loop.itemType());
        if (itemType.isPrimitive()) {
            itemType = DataTypes.getWrapperClass(itemType);
        }
        List<?> snapshot = LoopSemantics.snapshot(collection, owner.id(), itemType);
        List<Object> outputs = loop.outputTargetVariable() == null ? null : new ArrayList<>(snapshot.size());
        for (int index = 0; index < snapshot.size(); index++) {
            resetOutputElement(loop, state);
            LinkedHashMap<String, Object> lexical = new LinkedHashMap<>(parentLexical);
            lexical.put(loop.itemVariable(), snapshot.get(index));
            if (loop.indexVariable() != null) {
                lexical.put(loop.indexVariable(), index);
            }
            Signal signal = executeUnit(owner, invocation, state, lexical);
            if (signal == Signal.PAUSED) {
                return signal;
            }
            if (outputs != null) {
                outputs.add(lookup(loop.outputSourceVariable(), state, lexical));
            }
            if (signal == Signal.BREAK) {
                break;
            }
        }
        if (outputs != null) {
            state.put(loop.outputTargetVariable(), Collections.unmodifiableList(outputs));
        }
        return Signal.NORMAL;
    }

    private void resetOutputElement(IterationPlan.ForEach loop, Map<String, Object> state) {
        if (loop.outputSourceVariable() == null) {
            return;
        }
        ProcessSemanticPlan.VariablePlan output = semanticPlan.requireVariable(loop.outputSourceVariable());
        state.put(output.name(), initialValue(output));
    }

    private Object initialValue(ProcessSemanticPlan.VariablePlan variable) {
        Object initial = expressions.evaluate(expressionCatalog.defaultValue(variable.dataType(),
                        variable.defaultValue()), Map.of(), Map.of());
        return convert(initial, variable.dataType());
    }

    private Signal executeUnit(ProcessSemanticPlan.NodePlan owner, Invocation invocation, Map<String, Object> state,
            Map<String, Object> lexicalBindings) throws Exception {
        if (owner.operation() instanceof ActionPlan action) {
            executeAction(owner.id(), action, state, lexicalBindings);
        } else if (owner.operation() instanceof ProcessCallPlan call) {
            executeProcessCall(owner.id(), call, state, lexicalBindings);
        } else if (owner.operation() != null && !(owner.operation() instanceof AwaitPlan)
                && !(owner.operation() instanceof TimerPlan)) {
            throw new IllegalStateException("Unsupported Process operation: " + owner
                        .operation()
                        .getClass()
                        .getName());
        }
        if (owner.scopeBoundary() != null) {
            return executeThrough(owner.scopeBoundary().startNodeId(), owner.scopeBoundary().endNodeId(), invocation,
                    state, lexicalBindings);
        }
        return Signal.NORMAL;
    }

    private void executeAction(String nodeId, ActionPlan action, Map<String, Object> state,
            Map<String, Object> lexicalBindings) throws Exception {
        EffectiveInvocationPolicy policy = action.invocationPolicy();
        ActionExecutor.callAndCommit(nodeId,
                () -> actions.invoke(action, actionInputs(nodeId, action, state, lexicalBindings), null), policy,
                state::putAll);
    }

    private Map<String, Object> actionInputs(String nodeId, ActionPlan action, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        for (ActionPlan.Input mapping : action.inputs()) {
            Object value;
            if (mapping.source() instanceof ActionPlan.InputSource.Expression expression) {
                value = expressions.evaluate(expressionCatalog.value(nodeId, expression.value()), state, lexicalBindings);
            } else if (mapping.source() instanceof ActionPlan.InputSource.Literal literal) {
                value = expressions.evaluate(expressionCatalog.defaultValue(mapping.declaredType(), literal.value()),
                        state, lexicalBindings);
            } else {
                throw new IllegalStateException(
                        "ProcessRuntime cannot materialize Action input source " + mapping
                            .source()
                            .getClass()
                            .getSimpleName());
            }
            input.put(mapping.target(), convert(value, mapping.declaredType()));
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private void executeProcessCall(String nodeId, ProcessCallPlan call, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        for (ProcessCallPlan.Input mapping : call.inputs()) {
            if (mapping.sourceExpression() != null) {
                Object value =
                        expressions.evaluate(expressionCatalog.value(nodeId, mapping.sourceExpression()), state,
                                lexicalBindings);
                input.put(mapping.target(), value);
            }
        }
        EngineExecutionContext context = EngineExecutionContextHolder.requireCurrent();
        ProcessResult<Map<String, Object>> result = context.callProcess(nodeId, input);
        Map<String, Object> output = result.orElseThrow();
        LinkedHashMap<String, Object> staged = new LinkedHashMap<>();
        for (ProcessCallPlan.Output mapping : call.outputs()) {
            Object value = ProcessCallOutputs.requireOutput(output, call.code(), nodeId, mapping.source());
            staged.put(mapping.target(), convert(value, mapping.targetType()));
        }
        state.putAll(staged);
    }

    private boolean evaluateCondition(String nodeId, String source, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        return expressions.evaluateCondition(expressionCatalog.condition(nodeId, source), state, lexicalBindings);
    }

    private boolean evaluateLoopCondition(String nodeId, String source, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        return expressions.evaluateCondition(expressionCatalog.loopCondition(nodeId, source), state, lexicalBindings);
    }

    private Object lookup(String name, Map<String, Object> state, Map<String, Object> lexicalBindings) {
        if (lexicalBindings.containsKey(name)) {
            return lexicalBindings.get(name);
        }
        if (state.containsKey(name)) {
            return state.get(name);
        }
        throw new IllegalArgumentException("Process binding is unavailable: " + name);
    }

    private String onlyOutgoing(ProcessSemanticPlan.NodePlan node) {
        if (node.outgoingTransitions().size() != 1) {
            throw new IllegalStateException("Node '" + node.id() + "' must have exactly one outgoing transition");
        }
        return node.outgoingTransitions().get(0).targetId();
    }

    private Map<String, Object> result(Map<String, Object> state) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        semanticPlan
            .getVariables()
            .values()
            .stream()
            .filter(variable -> variable.role() == ProcessSemanticPlan.VariableRole.RETURN)
            .forEach(variable -> result.put(variable.name(), state.get(variable.name())));
        return result;
    }

    private Object convert(Object value, String typeName) {
        return actions.convertValue(value, typeName);
    }

    private Map<String, Object> invoke(String operation, InvocationCall call) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            return call.run();
        } catch (ProcessExecutionException | CompileFlowException classified) {
            throw classified;
        } catch (Exception failure) {
            LOGGER.debug("Interpreted Process invocation failed: operation={}, processCode={}, failureType={}",
                    operation, semanticPlan.getProcessCode(), failure.getClass().getName(), failure);
            throw new CompileFlowException(ErrorCode.CF_EXEC_001, "Failed to " + operation + " process", failure)
                .withContext("processCode", semanticPlan.getProcessCode())
                .withContext("actionName", operation);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private enum Signal {
        NORMAL,
        BREAK,
        CONTINUE,
        PAUSED
    }

    private record SelectedBranch(int ordinal, ProcessSemanticPlan.TransitionPlan transition) {}

    private record BranchFrame(Map<String, Object> state, Signal signal) {}

    private static final class Invocation {
        private final String triggerNodeId;
        private final String event;
        private boolean pending;

        private Invocation(String triggerNodeId, String event, boolean pending) {
            this.triggerNodeId = triggerNodeId;
            this.event = event;
            this.pending = pending;
        }

        static Invocation execute() {
            return new Invocation(null, null, false);
        }

        static Invocation trigger(ProcessTrigger trigger) {
            return new Invocation(trigger.nodeId(), trigger.event(), true);
        }

        boolean consume(String nodeId) {
            if (!pending || !Objects.equals(triggerNodeId, nodeId)) {
                return false;
            }
            pending = false;
            return true;
        }

        String event() {
            return event;
        }

        Invocation copy() {
            return new Invocation(triggerNodeId, event, pending);
        }
    }

    @FunctionalInterface
    private interface InvocationCall {
        Map<String, Object> run() throws Exception;
    }
}
