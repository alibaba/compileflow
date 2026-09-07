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
package com.alibaba.compileflow.durable.runtime.program;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.ProcessCallRequest;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ParallelForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.EffectRequest;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.ScopeFrame;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionContext;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.EffectPolicyPlan;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-local capabilities visible to generated frontier code.
 *
 * @author yusu
 */
public final class DurableExecutionContext {
    private final DurableMachinePlan machinePlan;
    private final DurableActionInvoker actions;
    private final DurableWaitDescriptionProvider waits;
    private final DurableValueSerializer serializer;

    public DurableExecutionContext(DurableMachinePlan machinePlan, DurableActionInvoker actions,
            DurableWaitDescriptionProvider waits, DurableValueSerializer serializer) {
        this.machinePlan = Objects.requireNonNull(machinePlan, "machinePlan");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.waits = Objects.requireNonNull(waits, "waits");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public Map<String, Object> invokeReplayable(String elementId, Map<String, Object> state,
            Map<String, Object> lexicalBindings) throws Exception {
        ActionPlan action = requireAction(elementId);
        if (action.execution() != ActionExecution.REPLAYABLE) {
            throw new IllegalArgumentException("Action is not replayable: " + elementId);
        }
        Map<String, Object> input = actions.materializeInput(action, state, lexicalBindings);
        return actions.invoke(action, serializer.detachActionInput(elementId, input), null);
    }

    public EffectRequest materializeEffectRequest(String elementId, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        ActionPlan action = requireAction(elementId);
        if (action.execution() != ActionExecution.EFFECT) {
            throw new IllegalArgumentException("Action is not an Effect: " + elementId);
        }
        Map<String, Object> input = actions.materializeInput(action, state, lexicalBindings);
        EffectRecoveryPlan recoveryPlan = recoveryPlan(action.effectPolicy(), state, lexicalBindings);
        if (recoveryPlan.mode() == EffectRecoveryPlan.Mode.RECONCILE && action.effectPolicy().reconcileAction() == null) {
            throw new IllegalArgumentException("Dynamic RECONCILE recovery requires a reconcileAction: " + elementId);
        }
        return new EffectRequest(elementId, recoveryPlan, serializer.detachActionInput(elementId, input));
    }

    private static EffectRecoveryPlan recoveryPlan(EffectPolicyPlan policy, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        if (policy.dynamic()) {
            String source = policy.recoveryPlanVariable();
            Object value = lexicalBindings.containsKey(source) ? lexicalBindings.get(source) : state.get(source);
            if (!(value instanceof EffectRecoveryPlan plan)) {
                throw new IllegalArgumentException(
                        "Effect recoveryPlanVariable '" + source + "' does not contain an EffectRecoveryPlan");
            }
            return plan;
        }
        return switch (policy.recovery()) {
            case MANUAL -> EffectRecoveryPlan.manual();
            case RETRY -> EffectRecoveryPlan.retry(policy.maxAttempts(), policy.recoveryDelay(),
                    policy.maxRecoveryDuration());
            case RECONCILE -> EffectRecoveryPlan.reconcile(policy.maxAttempts(), policy.maxReconcileAttempts(),
                    policy.recoveryDelay(), policy.maxRecoveryDuration());
        };
    }

    public Map<String, Object> describeWait(String nodeId, Map<String, Object> state, List<ScopeFrame> frames)
            throws Exception {
        String boundaryId = Objects.requireNonNull(nodeId, "nodeId");
        ContinuationSnapshot detached = serializer.detachSnapshot(ResumePoint.afterElement(boundaryId),
                Objects.requireNonNull(state, "state"), Objects.requireNonNull(frames, "frames"));
        return waits.describeWait(
                new DurableWaitDescriptionContext(machinePlan.semanticPlan().getProcessCode(),
                        machinePlan.semanticPlan().getDigest(), boundaryId, detached.variables(),
                        lexicalBindings(detached.scopeFrames())));
    }

    public ProcessCallRequest materializeProcessCallRequest(String elementId, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        ProcessCallPlan processCall = requireProcessCall(elementId);
        Map<String, Object> input = new LinkedHashMap<>();
        for (ProcessCallPlan.Input binding : processCall.inputs()) {
            if (binding.sourceExpression() != null) {
                input.put(binding.target(), resolveProcessCallInput(binding, state, lexicalBindings));
            }
        }
        return new ProcessCallRequest(elementId, input);
    }

    private static Object resolveProcessCallInput(ProcessCallPlan.Input binding, Map<String, Object> state,
            Map<String, Object> lexicalBindings) {
        String sourceExpression = binding.sourceExpression();
        if (lexicalBindings.containsKey(sourceExpression)) {
            return lexicalBindings.get(sourceExpression);
        }
        if (state.containsKey(sourceExpression)) {
            return state.get(sourceExpression);
        }
        throw new IllegalArgumentException("Process call input source is unavailable: " + sourceExpression);
    }

    public Map<String, Object> mapProcessCallOutput(String elementId, Map<String, Object> output) {
        ProcessCallPlan processCall = requireProcessCall(elementId);
        Map<String, Object> source = Objects.requireNonNull(output, "output");
        Map<String, Object> updates = new LinkedHashMap<>();
        for (ProcessCallPlan.Output binding : processCall.outputs()) {
            if (!source.containsKey(binding.source())) {
                throw new IllegalArgumentException(
                        "Called Process result is missing declared output '" + binding.source() + "'");
            }
            Class<?> targetType = serializer.resolveJavaClass(binding.targetType());
            Object value = source.get(binding.source());
            updates.put(binding.target(), value == null ? null : DataTypes.transfer(value, targetType));
        }
        return DurablePayload.immutablePayload(updates, "Durable");
    }

    private Map<String, Object> lexicalBindings(List<ScopeFrame> frames) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ScopeFrame frame : frames) {
            DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(frame.loopId());
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
                Object currentValue =
                        frame instanceof ParallelForEachFrame parallel
                        ? parallel.currentValue()
                        : ((ForEachFrame) frame).currentValue();
                result.put(loop.itemVariable(), currentValue);
                if (loop.indexVariable() != null) {
                    result.put(loop.indexVariable(), frame.position());
                }
            } else if (iteration instanceof DurableMachinePlan.Iteration.While loop && loop.indexVariable() != null) {
                result.put(loop.indexVariable(), frame.position());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private ActionPlan requireAction(String elementId) {
        if (!(machinePlan.semanticPlan().requireNode(elementId).operation() instanceof ActionPlan action)) {
            throw new IllegalArgumentException("Action is unavailable: " + elementId);
        }
        return action;
    }

    private ProcessCallPlan requireProcessCall(String elementId) {
        if (!(machinePlan.semanticPlan().requireNode(elementId).operation() instanceof ProcessCallPlan processCall)) {
            throw new IllegalArgumentException("Process call is unavailable: " + elementId);
        }
        return processCall;
    }
}
