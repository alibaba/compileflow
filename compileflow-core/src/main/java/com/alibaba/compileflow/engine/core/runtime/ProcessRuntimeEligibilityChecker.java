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
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchPlan;
import com.alibaba.compileflow.engine.core.controlflow.GatewayBranchKey;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Enforces capabilities of the default {@code ProcessRuntime} after target-neutral analysis.
 *
 * @author yusu
 */
public final class ProcessRuntimeEligibilityChecker {
    private ProcessRuntimeEligibilityChecker() {
    }

    /**
     * Fails when valid Process semantics require capabilities unavailable to the default runtime.
     */
    public static void validate(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        Objects.requireNonNull(semanticPlan, "semanticPlan");
        Objects.requireNonNull(structuredPlan, "structuredPlan");
        validateNestedScopes(semanticPlan, structuredPlan);
        validateIterations(semanticPlan);
        validateOperations(semanticPlan);
        structuredPlan
            .getGatewayPlans()
            .values()
            .stream()
            .filter(GatewayPlan::isSplit)
            .filter(GatewayPlan::isConcurrent)
            .forEach(gateway -> validateConcurrentRegion(semanticPlan, gateway));
    }

    private static void validateOperations(ProcessSemanticPlan semanticPlan) {
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.operation() instanceof AwaitPlan await && await.timeout() != null) {
                throw invalid("Timed Wait at node '" + node.id() + "' requires Durable execution");
            }
            if (node.operation() instanceof TimerPlan) {
                throw invalid("Timer at node '" + node.id() + "' requires Durable execution");
            }
            if (node.operation() instanceof ActionPlan action
                    && action
                        .inputs()
                        .stream()
                        .map(ActionPlan.Input::source)
                        .anyMatch(ActionPlan.InputSource.EffectId.class::isInstance)) {
                throw invalid("Effect metadata input at node '" + node.id() + "' requires Durable execution");
            }
        }
    }

    private static void validateIterations(ProcessSemanticPlan semanticPlan) {
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.iteration() != null && node.operation() instanceof AwaitPlan) {
                throw invalid("Suspending iteration at node '" + node.id() + "' requires Durable execution");
            }
        }
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (node.iteration() instanceof IterationPlan.ForEach forEach
                    && forEach.execution() == IterationPlan.Execution.PARALLEL) {
                throw invalid("Parallel foreach at node '" + node.id() + "' requires Durable execution");
            }
        }
    }

    private static void validateNestedScopes(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        for (ProcessSemanticPlan.NodePlan node : semanticPlan.getNodes().values()) {
            if (ProcessSemanticPlan.ROOT_SCOPE_ID.equals(node.scopeId())) {
                continue;
            }
            if (node.operation() instanceof AwaitPlan || node.operation() instanceof TimerPlan) {
                throw invalid(
                        "Nested scope '" + node.scopeId() + "' contains a suspending node '" + node.id()
                        + "'. Suspending inside a loop or subprocess requires Durable execution");
            }
            GatewayPlan gateway = structuredPlan.getGatewayPlan(node.id());
            if (gateway != null && gateway.isSplit() && gateway.isConcurrent()) {
                throw invalid(
                        "Nested scope '" + node.scopeId() + "' contains concurrent gateway '" + node.id()
                        + "'. ProcessRuntime branch frames do not isolate nested scope state");
            }
        }
    }

    private static void validateConcurrentRegion(ProcessSemanticPlan semanticPlan, GatewayPlan gateway) {
        if (gateway.isSuspending()) {
            throw invalid(
                    "Gateway '" + gateway.getGatewayId()
                    + "' contains a suspending node in a concurrent region. Durable token correlation is required");
        }
        List<GatewayBranchPlan> branches = semanticPlan
            .requireNode(gateway.getGatewayId())
            .outgoingTransitions()
            .stream()
            .map(transition -> gateway.requireBranch(GatewayBranchKey.of(gateway.getGatewayId(), transition.targetId())))
            .toList();
        for (int left = 0; left < branches.size(); left++) {
            for (int right = left + 1; right < branches.size(); right++) {
                Set<String> conflicts = conflicts(branches.get(left), branches.get(right));
                if (!conflicts.isEmpty()) {
                    throw invalid(
                            "Concurrent branches of gateway '" + gateway.getGatewayId()
                            + "' have conflicting process-variable access: " + String.join(",", conflicts));
                }
            }
        }
    }

    private static Set<String> conflicts(GatewayBranchPlan left, GatewayBranchPlan right) {
        Set<String> conflicts = new LinkedHashSet<>(left.getWrites());
        conflicts.retainAll(right.getWrites());
        return conflicts;
    }

    private static CompileFlowException invalid(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_005, message);
    }
}
