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
package com.alibaba.compileflow.engine.core.semantic;

import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.EffectPolicyPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectiveEffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectMetadata;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared frontend-only normalization of the common Action authoring contract.
 *
 * <p>Only semantic frontends call this type. Backends consume {@link ActionPlan} and never reopen
 * an authoring-layer Action.
 *
 * @author yusu
 */
public final class SemanticActionNormalizer {
    private SemanticActionNormalizer() {
    }

    /**
     * Normalizes one source Action owned by a node.
     */
    public static ActionPlan normalize(Action action, Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(variables, "variables");
        if (action.getEffectPolicy() != null && action.getExecution() != ActionExecution.EFFECT) {
            throw new IllegalArgumentException("effectPolicy requires execution=\"effect\"");
        }
        if (action.getExecution() == ActionExecution.EFFECT && action.getInvocationPolicy() != null) {
            throw new IllegalArgumentException("execution=\"effect\" must not declare invocationPolicy");
        }
        List<ActionPlan.Input> inputs = action
            .getInputMappings()
            .stream()
            .map(SemanticActionNormalizer::input)
            .toList();
        ActionPlan.Output output = output(action.getOutputMappings(), variables);
        EffectiveInvocationPolicy invocationPolicy = action.getInvocationPolicy() == null
                ? EffectiveInvocationPolicy.defaults()
                : EffectiveInvocationPolicy.from(action.getInvocationPolicy());
        EffectPolicyPlan effectPolicy =
                action.getExecution() == ActionExecution.EFFECT ? effectPolicy(action, inputs) : null;
        return new ActionPlan(action.getExecution(), invocation(action), inputs, output, invocationPolicy, effectPolicy);
    }

    private static ActionPlan.Input input(InputMapping mapping) {
        if (mapping.getDefaultValue() != null) {
            return ActionPlan.Input.literal(mapping.getDefaultValue(), mapping.getTarget(), mapping.getDataType());
        }
        if (EffectMetadata.isSource(mapping.getSource())) {
            return ActionPlan.Input.effectId(mapping.getTarget(), mapping.getDataType());
        }
        if (EffectMetadata.isReservedSource(mapping.getSource())) {
            throw new IllegalArgumentException("Unsupported Effect metadata input source '" + mapping.getSource() + "'");
        }
        return ActionPlan.Input.expression(mapping.getSource(), mapping.getTarget(), mapping.getDataType());
    }

    private static ActionPlan.Output output(List<OutputMapping> mappings,
            Map<String, ProcessSemanticPlan.VariablePlan> variables) {
        if (mappings.isEmpty()) {
            return null;
        }
        if (mappings.size() > 1) {
            throw new IllegalArgumentException("Action must declare at most one output mapping");
        }
        OutputMapping mapping = mappings.get(0);
        String targetVariable = mapping.getTarget();
        ProcessSemanticPlan.VariablePlan target = variables.get(targetVariable);
        if (target == null) {
            throw new IllegalArgumentException(
                    "Action output references unknown Process variable '" + targetVariable + "'");
        }
        return new ActionPlan.Output(targetVariable, mapping.getDataType(), target.dataType());
    }

    private static EffectPolicyPlan effectPolicy(Action action, List<ActionPlan.Input> effectInputs) {
        EffectiveEffectPolicy resolved = action.getEffectPolicy() == null
                ? EffectiveEffectPolicy.manualDefault()
                : EffectiveEffectPolicy.from(action.getEffectPolicy());
        ReconcilePlan reconcileAction =
                resolved.reconcileAction() == null ? null : reconcile(resolved.reconcileAction(), effectInputs);
        return new EffectPolicyPlan(resolved.recoveryPlanVariable(), resolved.recovery(), resolved.maxAttempts(),
                resolved.maxReconcileAttempts(), resolved.recoveryDelay(), resolved.maxRecoveryDuration(),
                reconcileAction);
    }

    private static ReconcilePlan reconcile(ReconcileAction action, List<ActionPlan.Input> effectInputs) {
        Set<String> requestFields = effectInputs
            .stream()
            .filter(input -> !(input.source() instanceof ActionPlan.InputSource.EffectId))
            .map(ActionPlan.Input::target)
            .collect(Collectors.toUnmodifiableSet());
        List<ReconcilePlan.Input> inputs = action.getInputs().stream().map(input -> {
            String source = input.getSource();
            if (EffectMetadata.isReservedSource(source) && !EffectMetadata.isSource(source)) {
                throw new IllegalArgumentException("Unsupported Effect metadata input source '" + source + "'");
            }
            if (!EffectMetadata.isSource(source) && !requestFields.contains(source)) {
                throw new IllegalArgumentException(
                        "Reconcile input references unknown Effect request field '" + source + "'");
            }
            return EffectMetadata.isSource(source)
                    ? ReconcilePlan.Input.effectId(input.getTarget(), input.getDataType())
                    : ReconcilePlan.Input.requestField(source, input.getTarget(), input.getDataType());
        }).toList();
        return new ReconcilePlan(invocation(action), inputs);
    }

    private static ActionInvocation invocation(Action action) {
        return switch (Objects.requireNonNull(action.getType(), "action.type")) {
            case JAVA -> new ActionInvocation.Java(action.getClassName(), action.effectiveMethod());
            case SPRING_BEAN -> new ActionInvocation.SpringBean(action.getBean(), action.getClassName(),
                    action.effectiveMethod());
            case SCRIPT -> new ActionInvocation.Script(action.getLanguage(), action.getSource());
        };
    }

    private static ActionInvocation invocation(ReconcileAction action) {
        return switch (Objects.requireNonNull(action.getType(), "reconcileAction.type")) {
            case JAVA -> new ActionInvocation.Java(action.getClassName(), action.effectiveMethod());
            case SPRING_BEAN -> new ActionInvocation.SpringBean(action.getBean(), action.getClassName(),
                    action.effectiveMethod());
            case SCRIPT -> new ActionInvocation.Script(action.getLanguage(), action.getSource());
        };
    }
}
