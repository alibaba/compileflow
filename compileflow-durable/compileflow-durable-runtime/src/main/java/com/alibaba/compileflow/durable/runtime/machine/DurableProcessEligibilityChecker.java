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

import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchKey;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchPlan;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.EffectPolicyPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed capability check for lowering source-neutral Process semantics to Durable execution.
 *
 * @author yusu
 */
final class DurableProcessEligibilityChecker {
    private static final int MAX_PERSISTED_ELEMENT_ID_CHARACTERS = 128;
    private final ScriptExecutorRegistry scriptExecutors;
    private final DurableExpressionValidator expressionValidator;

    DurableProcessEligibilityChecker(ScriptExecutorRegistry scriptExecutors,
            DurableExpressionValidator expressionValidator) {
        this.scriptExecutors = Objects.requireNonNull(scriptExecutors, "scriptExecutors");
        this.expressionValidator = Objects.requireNonNull(expressionValidator, "expressionValidator");
    }

    /**
     * Checks Process-owned Durable structure without requiring deployment capabilities.
     */
    DurableModelEligibility checkStructure(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        return check(semanticPlan, structuredPlan, false, false);
    }

    /**
     * Checks both Durable structure and current deployment capabilities.
     */
    DurableModelEligibility check(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        return check(semanticPlan, structuredPlan, true, true);
    }

    /**
     * Checks deployment capability prerequisites, leaving expression validation and binding to lowering.
     */
    DurableModelEligibility checkForLowering(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        return check(semanticPlan, structuredPlan, true, false);
    }

    private DurableModelEligibility check(ProcessSemanticPlan semantics, StructuredControlFlowPlan structure,
            boolean deploymentCapabilities, boolean validateExpressions) {
        ProcessSemanticPlan semanticPlan = Objects.requireNonNull(semantics, "semanticPlan");
        StructuredControlFlowPlan structuredPlan = Objects.requireNonNull(structure, "structuredPlan");
        List<DurableModelEligibility.Problem> problems = new ArrayList<>();
        validateVariables(semanticPlan, problems);
        validateNodes(semanticPlan, deploymentCapabilities, validateExpressions, problems);
        validateConcurrency(semanticPlan, structuredPlan, problems);
        validateStaticFrontierCapacity(semanticPlan, structuredPlan, problems);
        return new DurableModelEligibility(problems);
    }

    private void validateVariables(ProcessSemanticPlan semantics, List<DurableModelEligibility.Problem> problems) {
        for (ProcessSemanticPlan.VariablePlan variable : semantics.getVariables().values()) {
            if (variable.name().startsWith("__cf_")) {
                problems.add(
                        problem("DURABLE_RESERVED_VARIABLE_PREFIX", null,
                                "Process variables must not use the Kernel-reserved __cf_ prefix: " + variable.name()));
            }
        }
    }

    private void validateNodes(ProcessSemanticPlan semantics, boolean deploymentCapabilities,
            boolean validateExpressions, List<DurableModelEligibility.Problem> problems) {
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            if (node.id().codePointCount(0, node.id().length()) > MAX_PERSISTED_ELEMENT_ID_CHARACTERS) {
                problems.add(
                        problem("DURABLE_ELEMENT_ID_TOO_LONG", node.id(),
                                "Durable element identifiers must not exceed " + MAX_PERSISTED_ELEMENT_ID_CHARACTERS + " characters"));
            }
            if (node.scopeBoundary() != null && node.operation() != null) {
                problems.add(
                        problem("DURABLE_SCOPED_OPERATION_UNSUPPORTED", node.id(),
                                "A scoped activity cannot also declare an operation in the Durable target"));
            }
            if (node.iteration() != null && node.scopeBoundary() == null && node.operation() == null) {
                problems.add(
                        problem("DURABLE_ITERATION_BODY_REQUIRED", node.id(),
                                "An iteration must own a scope or operation body"));
            }
            validateOperation(semantics, node, deploymentCapabilities, problems);
            validateIteration(semantics, node, problems);
            if (validateExpressions) {
                validateExpressions(semantics, node, problems);
            }
        }
    }

    private void validateOperation(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            boolean deploymentCapabilities, List<DurableModelEligibility.Problem> problems) {
        OperationPlan operation = node.operation();
        if (operation == null || operation instanceof AwaitPlan || operation instanceof TimerPlan) {
            return;
        }
        if (operation instanceof ActionPlan action) {
            validateAction(semantics, node.id(), action, deploymentCapabilities, problems);
            return;
        }
        if (operation instanceof ProcessCallPlan processCall) {
            validateProcessCall(semantics, node.id(), processCall, problems);
            return;
        }
        throw new IllegalStateException("Unclassified OperationPlan variant " + operation.getClass().getName());
    }

    private void validateAction(ProcessSemanticPlan semantics, String nodeId, ActionPlan action,
            boolean deploymentCapabilities, List<DurableModelEligibility.Problem> problems) {
        if (action.execution() == null) {
            problems.add(
                    problem("DURABLE_ACTION_EXECUTION_REQUIRED", nodeId,
                            "Action must explicitly declare execution=\"replayable|effect\""));
        }
        if (!defaultPolicy(action.invocationPolicy())) {
            problems.add(
                    problem("DURABLE_INVOCATION_POLICY_UNSUPPORTED", nodeId,
                            "Durable actions cannot use ProcessRuntime invocationPolicy"));
        }
        validateInvocation(nodeId, action.invocation(),
                action.invocation() instanceof ActionInvocation.Script ? action.scriptProgramSpec() : null,
                deploymentCapabilities, problems);
        validateActionInputs(semantics, nodeId, action, problems);
        if (action.execution() == ActionExecution.EFFECT) {
            if (action.effectPolicy().reconcileAction() != null) {
                validateReconcile(nodeId, action.effectPolicy().reconcileAction(), deploymentCapabilities, problems);
            }
            validateRecoveryPlanVariable(semantics, nodeId, action.effectPolicy(), problems);
        }
    }

    private void validateReconcile(String nodeId, ReconcilePlan reconcile, boolean deploymentCapabilities,
            List<DurableModelEligibility.Problem> problems) {
        validateInvocation(nodeId, reconcile.invocation(),
                reconcile.invocation() instanceof ActionInvocation.Script ? reconcile.scriptProgramSpec() : null,
                deploymentCapabilities, problems);
    }

    private static void validateRecoveryPlanVariable(ProcessSemanticPlan semantics, String nodeId,
            EffectPolicyPlan policy, List<DurableModelEligibility.Problem> problems) {
        if (!policy.dynamic()) {
            return;
        }
        ProcessSemanticPlan.VisibleVariable source =
                semantics.visibleVariables(nodeId).get(policy.recoveryPlanVariable());
        if (source == null) {
            problems.add(
                    problem("DURABLE_EFFECT_RECOVERY_PLAN_VARIABLE_UNKNOWN", nodeId,
                            "Effect recoveryPlanVariable references an unknown variable '" + policy.recoveryPlanVariable() + "'"));
        } else if (!EffectRecoveryPlan.class.getName().equals(source.typeName())) {
            problems.add(
                    problem("DURABLE_EFFECT_RECOVERY_PLAN_VARIABLE_TYPE", nodeId,
                            "Effect recoveryPlanVariable must declare " + EffectRecoveryPlan.class.getName()));
        }
    }

    private void validateInvocation(String nodeId, ActionInvocation invocation, ScriptProgramSpec scriptProgram,
            boolean deploymentCapabilities, List<DurableModelEligibility.Problem> problems) {
        if (invocation instanceof ActionInvocation.Java || invocation instanceof ActionInvocation.SpringBean) {
            return;
        }
        if (invocation instanceof ActionInvocation.Script script) {
            String language;
            try {
                language = ScriptExecutor.requireCanonicalName(script.language());
            } catch (RuntimeException failure) {
                problems.add(problem("DURABLE_SCRIPT_LANGUAGE_INVALID", nodeId, failure.getMessage()));
                return;
            }
            if (!deploymentCapabilities) {
                return;
            }
            if (!scriptExecutors.getLanguageNames().contains(language)) {
                problems.add(
                        problem("DURABLE_SCRIPT_EXECUTOR_NOT_REGISTERED", nodeId,
                                "No ScriptExecutor is registered for language '" + language + "'"));
                return;
            }
            try {
                scriptExecutors.validate(scriptProgram);
            } catch (RuntimeException failure) {
                String classification =
                        failure instanceof ScriptException scriptFailure ? scriptFailure.kind().name() : "PROVIDER_"
                        + "REJECTED";
                problems.add(
                        problem("DURABLE_SCRIPT_INVALID", nodeId,
                                "Script source is invalid for language '" + language + "' (" + classification + ")"));
            }
            return;
        }
        throw new IllegalStateException("Unclassified ActionInvocation variant " + invocation.getClass().getName());
    }

    private void validateActionInputs(ProcessSemanticPlan semantics, String nodeId, ActionPlan action,
            List<DurableModelEligibility.Problem> problems) {
        Set<String> visible = semantics.visibleVariables(nodeId).keySet();
        boolean script = action.invocation() instanceof ActionInvocation.Script;
        for (ActionPlan.Input input : action.inputs()) {
            if (input.source() instanceof ActionPlan.InputSource.Expression expression && !visible.contains(expression.value())) {
                problems.add(
                        problem(script ? "DURABLE_SCRIPT_INPUT_SOURCE_UNKNOWN" : "DURABLE_ACTION_INPUT_SOURCE_UNKNOWN",
                                nodeId,
                                "Action input '" + input.target() + "' references unknown variable '" + expression.value() + "'"));
            }
        }
    }

    private void validateProcessCall(ProcessSemanticPlan semantics, String nodeId, ProcessCallPlan processCall,
            List<DurableModelEligibility.Problem> problems) {
        Set<String> visible = semantics.visibleVariables(nodeId).keySet();
        for (ProcessCallPlan.Input input : processCall.inputs()) {
            String source = input.sourceExpression();
            if (source != null && (!ProcessNames.isIdentifier(source) || !visible.contains(source))) {
                problems.add(
                        problem("DURABLE_PROCESS_CALL_INPUT_SOURCE_UNSUPPORTED", nodeId,
                                "Process call input '" + input.target() + "' must reference a visible variable"));
            }
        }
    }

    private void validateIteration(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            List<DurableModelEligibility.Problem> problems) {
        IterationPlan iteration = node.iteration();
        if (iteration == null) {
            return;
        }
        if (iteration instanceof IterationPlan.While) {
            return;
        }
        if (!(iteration instanceof IterationPlan.ForEach forEach)) {
            throw new IllegalStateException("Unclassified IterationPlan variant " + iteration.getClass().getName());
        }
        if (forEach.execution() != IterationPlan.Execution.PARALLEL) {
            return;
        }
        ProcessSemanticPlan.VisibleVariable input =
                semantics.visibleVariables(node.id()).get(forEach.collectionVariable());
        if (input == null || !listCompatible(input.typeName())) {
            problems.add(
                    problem("DURABLE_MULTI_INSTANCE_INPUT_NOT_LIST", node.id(),
                            "Parallel foreach collection must reference a visible java.util.List-compatible variable"));
        }
    }

    private void validateExpressions(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            List<DurableModelEligibility.Problem> problems) {
        validateExpression(semantics, node, node.controlCondition(), DurableExpressionValidator.TargetType.BOOLEAN,
                problems);
        node
            .outgoingTransitions()
            .forEach(transition -> validateExpression(semantics, node, transition.condition(),
                    DurableExpressionValidator.TargetType.BOOLEAN, problems));
        if (node.iteration() instanceof IterationPlan.While loop) {
            validateExpression(semantics, node, loop.condition(), DurableExpressionValidator.TargetType.BOOLEAN,
                    problems);
        }
        if (node.operation() instanceof TimerPlan timer) {
            switch (timer.kind()) {
                case DURATION_LITERAL, WAKE_AT_LITERAL -> {
                }
                case DURATION_EXPRESSION -> validateExpression(semantics, node, timer.value(),
                        DurableExpressionValidator.TargetType.DURATION, problems);
                case WAKE_AT_EXPRESSION -> validateExpression(semantics, node, timer.value(),
                        DurableExpressionValidator.TargetType.INSTANT, problems);
                default -> throw new IllegalStateException("Unsupported timer kind: " + timer.kind());
            }
        }
    }

    private void validateExpression(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node, String expression,
            DurableExpressionValidator.TargetType targetType, List<DurableModelEligibility.Problem> problems) {
        if (expression == null) {
            return;
        }
        Map<String, String> visibleTypes = new LinkedHashMap<>();
        semantics
            .visibleVariables(node.id())
            .forEach((name, variable) -> visibleTypes.put(name, variable.typeName()));
        expressionValidator
            .validate(expression, visibleTypes, targetType)
            .forEach(message -> problems.add(problem("DURABLE_EXPRESSION_UNSAFE", node.id(), message)));
    }

    private void validateConcurrency(ProcessSemanticPlan semantics, StructuredControlFlowPlan structure,
            List<DurableModelEligibility.Problem> problems) {
        Set<String> concurrentRegion = new LinkedHashSet<>();
        structure
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isSplit)
            .filter(GatewayPlan::isConcurrent)
            .flatMap(gateway -> gateway.getBranches().values().stream())
            .map(GatewayBranchPlan::getNodeIds)
            .forEach(concurrentRegion::addAll);

        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            boolean concurrentAncestor = hasConcurrentAncestor(semantics, node, concurrentRegion);
            boolean parallelIteration =
                    node.iteration() instanceof IterationPlan.ForEach forEach
                    && forEach.execution() == IterationPlan.Execution.PARALLEL;
            if (node.operation() instanceof ProcessCallPlan && (concurrentAncestor || parallelIteration)) {
                problems.add(
                        problem("DURABLE_CONCURRENT_CHILD_FAILURE_UNSUPPORTED", node.id(),
                                "A concurrent execution scope cannot contain a Process call until sibling-drain and"
                                + " group-failure semantics are supported"));
            }
            if (parallelIteration && concurrentAncestor) {
                problems.add(
                        problem("DURABLE_NESTED_PARALLELISM_UNSUPPORTED", node.id(),
                                "A parallel foreach cannot enter an already-concurrent execution scope until Run-wide"
                                + " admission and group-cancellation semantics are supported"));
            }
            GatewayPlan gateway = structure.getGatewayPlan(node.id());
            if (gateway != null && gateway.isSplit() && gateway.isConcurrent()
                    && hasParallelIterationAncestor(semantics, node)) {
                problems.add(
                        problem("DURABLE_NESTED_PARALLELISM_UNSUPPORTED", node.id(),
                                "A parallel foreach iteration cannot fork another concurrent scope until Run-wide"
                                + " admission and group-cancellation semantics are supported"));
            }
        }
    }

    private boolean hasConcurrentAncestor(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            Set<String> concurrentRegion) {
        if (concurrentRegion.contains(node.id())) {
            return true;
        }
        for (
                String scope = node.scopeId();
                !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scope);
                scope = semantics.parentScope(scope)) {
            ProcessSemanticPlan.NodePlan owner = semantics.requireNode(scope);
            if (concurrentRegion.contains(scope)
                    || owner.iteration() instanceof IterationPlan.ForEach forEach
                    && forEach.execution() == IterationPlan.Execution.PARALLEL) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParallelIterationAncestor(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node) {
        for (
                String scope = node.scopeId();
                !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scope);
                scope = semantics.parentScope(scope)) {
            if (semantics.requireNode(scope).iteration() instanceof IterationPlan.ForEach forEach
                    && forEach.execution() == IterationPlan.Execution.PARALLEL) {
                return true;
            }
        }
        return false;
    }

    private void validateStaticFrontierCapacity(ProcessSemanticPlan semantics, StructuredControlFlowPlan structure,
            List<DurableModelEligibility.Problem> problems) {
        Map<String, Integer> capacities = new LinkedHashMap<>();
        structure
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isSplit)
            .forEach(gateway -> staticFrontierCapacity(gateway.getGatewayId(), semantics, structure, capacities,
                    new LinkedHashSet<>()));
        capacities
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > ContinuationSnapshot.MAX_FRONTIERS)
            .max(Map.Entry.comparingByValue())
            .ifPresent(entry -> problems.add(
                    problem("DURABLE_STATIC_FRONTIER_LIMIT_EXCEEDED", entry.getKey(),
                            "Static concurrent control flow may require " + entry.getValue()
                            + " frontiers, exceeding the Durable limit of " + ContinuationSnapshot.MAX_FRONTIERS)));
    }

    private int staticFrontierCapacity(String gatewayId, ProcessSemanticPlan semantics,
            StructuredControlFlowPlan structure, Map<String, Integer> capacities, Set<String> visiting) {
        Integer known = capacities.get(gatewayId);
        if (known != null) {
            return known;
        }
        if (!visiting.add(gatewayId)) {
            return ContinuationSnapshot.MAX_FRONTIERS + 1;
        }
        GatewayPlan gateway = structure.requireGatewayPlan(gatewayId);
        int continuation = pathCapacity(gateway.getContinuationNodeIds(), semantics, structure, capacities, visiting);
        int branches = 1;
        if (gateway.isSplit()) {
            if (!gateway.isConcurrent()) {
                branches = gateway
                    .getBranches()
                    .values()
                    .stream()
                    .mapToInt(branch -> pathCapacity(branch.getNodeIds(), semantics, structure, capacities, visiting))
                    .max()
                    .orElse(1);
            } else if (semantics.requireNode(gatewayId).kind() == ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY) {
                int conditional = 0;
                int fallback = 0;
                for (ProcessSemanticPlan.TransitionPlan transition : semantics
                    .requireNode(gatewayId)
                    .outgoingTransitions()) {
                    GatewayBranchPlan branch =
                            gateway.requireBranch(GatewayBranchKey.of(gatewayId, transition.targetId()));
                    int capacity = pathCapacity(branch.getNodeIds(), semantics, structure, capacities, visiting);
                    if (transition.condition() != null) {
                        conditional = saturatingAdd(conditional, capacity);
                    } else {
                        fallback = Math.max(fallback, capacity);
                    }
                }
                branches = Math.max(conditional, fallback);
            } else {
                branches = 0;
                for (ProcessSemanticPlan.TransitionPlan transition : semantics
                    .requireNode(gatewayId)
                    .outgoingTransitions()) {
                    GatewayBranchPlan branch =
                            gateway.requireBranch(GatewayBranchKey.of(gatewayId, transition.targetId()));
                    branches = saturatingAdd(branches,
                            pathCapacity(branch.getNodeIds(), semantics, structure, capacities, visiting));
                }
            }
        }
        visiting.remove(gatewayId);
        int result = Math.max(branches, continuation);
        capacities.put(gatewayId, result);
        return result;
    }

    private int pathCapacity(List<String> nodeIds, ProcessSemanticPlan semantics, StructuredControlFlowPlan structure,
            Map<String, Integer> capacities, Set<String> visiting) {
        int result = 1;
        for (String nodeId : nodeIds) {
            GatewayPlan nested = structure.getGatewayPlan(nodeId);
            if (nested != null && nested.isSplit()) {
                result = Math.max(result, staticFrontierCapacity(nodeId, semantics, structure, capacities, visiting));
            }
        }
        return result;
    }

    private static int saturatingAdd(int left, int right) {
        return Math.min(ContinuationSnapshot.MAX_FRONTIERS + 1, left + right);
    }

    private static boolean listCompatible(String typeName) {
        try {
            return List.class.isAssignableFrom(DataTypes.getJavaClass(typeName));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean defaultPolicy(EffectiveInvocationPolicy policy) {
        return EffectiveInvocationPolicy.defaults().equals(policy);
    }

    private static DurableModelEligibility.Problem problem(String code, String nodeId, String message) {
        return new DurableModelEligibility.Problem(code, nodeId, Objects.toString(message, "Durable target rejected"));
    }
}
