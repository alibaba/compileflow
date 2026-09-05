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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.EffectPolicyPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessRuntimeEligibilityCheckerTest {
    private final StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();

    @Test
    void acceptsDisjointConcurrentActions() {
        ProcessSemanticPlan semantics = parallel(action("leftResult"), action("rightResult"));

        assertThatCode(() -> ProcessRuntimeEligibilityChecker.validate(semantics, analyzer.analyze(semantics)))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsConflictingAndSuspendingConcurrentRegions() {
        ProcessSemanticPlan conflicting = parallel(action("shared"), action("shared"));
        assertThatThrownBy(() -> validate(conflicting)).hasMessageContaining(
                "conflicting process-variable access: shared");

        ProcessSemanticPlan suspending = parallel(new AwaitPlan("approved", null), null);
        assertThatThrownBy(() -> validate(suspending)).hasMessageContaining("Durable token correlation is required");
    }

    @Test
    void rejectsConflictsBetweenDistinctActivationsOfTheSameBranchBody() {
        assertThatThrownBy(() -> validate(duplicateTargetParallel()))
            .hasMessageContaining("conflicting process-variable access: shared");
    }

    @Test
    void acceptsScriptsAndProcessCallsWithDisjointConcurrentWrites() {
        ActionPlan script = new ActionPlan(null, new ActionInvocation.Script("qlexpress", "input"),
                List.of(ActionPlan.Input.expression("input", "input", Object.class.getName())),
                new ActionPlan.Output("leftResult", Object.class.getName(), Object.class.getName()),
                EffectiveInvocationPolicy.defaults(), null);
        assertThatCode(() -> validate(parallel(script, action("rightResult")))).doesNotThrowAnyException();

        ProcessCallPlan call = new ProcessCallPlan("child", new ProcessCallTarget.Classpath("child.bpm"),
                List.of(new ProcessCallPlan.Input("input", "input", null)),
                List.of(new ProcessCallPlan.Output("result", "leftResult", Object.class.getName())));
        assertThatCode(() -> validate(parallel(call, action("rightResult")))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNestedSuspensionAndConcurrentNestedScopes() {
        ProcessSemanticPlan nestedWait = nested(new AwaitPlan("approved", null), false);
        assertThatThrownBy(() -> validate(nestedWait))
            .hasMessageContaining("Nested scope 'scope' contains a suspending node 'work'");

        ProcessSemanticPlan nestedParallel = nested(null, true);
        assertThatThrownBy(() -> validate(nestedParallel))
            .hasMessageContaining("ProcessRuntime branch frames do not isolate nested scope state");
    }

    @Test
    void rejectsParallelForEach() {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = variables();
        variables.put("items",
                new ProcessSemanticPlan.VariablePlan("items", "java.util.List<java.lang.Object>",
                        ProcessSemanticPlan.VariableRole.PARAM, null));
        variables.put("results",
                new ProcessSemanticPlan.VariablePlan("results", "java.util.List<java.lang.Object>",
                        ProcessSemanticPlan.VariableRole.RETURN, null));
        variables.put("slot",
                new ProcessSemanticPlan.VariablePlan("slot", Object.class.getName(),
                        ProcessSemanticPlan.VariableRole.INNER, null));
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("loop")), null, null));
        nodes.put("loop",
                node("loop", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("end")), null,
                        new IterationPlan.ForEach("items", "item", Object.class.getName(), "index",
                                IterationPlan.Execution.PARALLEL, "slot", "results")));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));
        ProcessSemanticPlan semantics = new ProcessSemanticPlan("parallel.foreach", variables, nodes);

        assertThatThrownBy(() -> validate(semantics)).hasMessageContaining("requires Durable execution");
    }

    @Test
    void rejectsSuspendingIteration() {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("wait")), null, null));
        nodes.put("wait",
                node("wait", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("end")),
                        new AwaitPlan("approved", null),
                        new IterationPlan.While("true", IterationPlan.ConditionTiming.AFTER, 2,
                                IterationPlan.LimitBehavior.STOP, null)));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));

        assertThatThrownBy(() -> validate(new ProcessSemanticPlan("suspending.iteration", variables(), nodes)))
            .hasMessageContaining("Suspending iteration at node 'wait' requires Durable execution");
    }

    @Test
    void acceptsEffectActionsAsOneShotLocalInvocations() {
        ActionPlan effect = new ActionPlan(ActionExecution.EFFECT,
                new ActionInvocation.Java(Object.class.getName(), "toString"),
                List.of(ActionPlan.Input.expression("input", "input", Object.class.getName())),
                new ActionPlan.Output("leftResult", Object.class.getName(), Object.class.getName()),
                EffectiveInvocationPolicy.defaults(),
                new EffectPolicyPlan(null, EffectRecovery.MANUAL, 1, 0, null, null, null));

        assertThatCode(() -> validate(parallel(effect, action("rightResult")))).doesNotThrowAnyException();
    }

    @Test
    void rejectsKernelEffectMetadataOutsideDurableExecution() {
        ActionPlan effect = new ActionPlan(ActionExecution.EFFECT,
                new ActionInvocation.Java(Object.class.getName(), "toString"),
                List.of(ActionPlan.Input.effectId("effectId", String.class.getName())), null,
                EffectiveInvocationPolicy.defaults(),
                new EffectPolicyPlan(null, EffectRecovery.MANUAL, 1, 0, null, null, null));

        assertThatThrownBy(() -> validate(linear(effect)))
            .hasMessageContaining("Effect metadata input at node 'work' requires Durable execution");
    }

    @Test
    void acceptsTriggerEntriesAndRejectsTimedWaits() {
        assertThatCode(() -> validate(linear(new AwaitPlan("approved", null)))).doesNotThrowAnyException();

        assertThatThrownBy(() -> validate(linear(new AwaitPlan("approved", Duration.ofMinutes(30)))))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Timed Wait at node 'work' requires Durable execution");
    }

    private void validate(ProcessSemanticPlan semantics) {
        StructuredControlFlowPlan structured = analyzer.analyze(semantics);
        ProcessRuntimeEligibilityChecker.validate(semantics, structured);
    }

    private static ProcessSemanticPlan parallel(OperationPlan left, OperationPlan right) {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("split")), null, null));
        nodes.put("split",
                node("split", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, List.of(flow("left"), flow("right")), null,
                        null));
        nodes.put("left", node("left", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), left, null));
        nodes.put("right", node("right", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), right, null));
        nodes.put("join", node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, List.of(flow("end")), null, null));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));
        return new ProcessSemanticPlan("process.parallel", variables(), nodes);
    }

    private static ProcessSemanticPlan duplicateTargetParallel() {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("split")), null, null));
        nodes.put("split",
                node("split", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY,
                        List.of(flow("sharedBody"), flow("sharedBody"), flow("otherBody")), null, null));
        nodes.put("sharedBody",
                node("sharedBody", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), action("shared"), null));
        nodes.put("otherBody",
                node("otherBody", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), null, null));
        nodes.put("join", node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, List.of(flow("end")), null, null));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));
        return new ProcessSemanticPlan("process.duplicate-target-parallel", variables(), nodes);
    }

    private static ProcessSemanticPlan linear(OperationPlan operation) {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("work")), null, null));
        nodes.put("work", node("work", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("end")), operation, null));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));
        return new ProcessSemanticPlan("process.linear", variables(), nodes);
    }

    private static ProcessSemanticPlan nested(OperationPlan work, boolean concurrent) {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, List.of(flow("scope")), null, null));
        nodes.put("scope",
                new ProcessSemanticPlan.NodePlan("scope", ProcessSemanticPlan.NodeKind.ACTIVITY,
                        ProcessSemanticPlan.ROOT_SCOPE_ID,
                        new ProcessSemanticPlan.ScopeBoundary("nestedStart", "nestedEnd"), List.of(flow("end")), null,
                        null, null));
        nodes.put("end", node("end", ProcessSemanticPlan.NodeKind.END, List.of(), null, null));
        nodes.put("nestedStart",
                scopedNode("nestedStart", ProcessSemanticPlan.NodeKind.START,
                        List.of(flow(concurrent ? "split" : "work")), null));
        if (concurrent) {
            nodes.put("split",
                    scopedNode("split", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY,
                            List.of(flow("left"), flow("right")), null));
            nodes.put("left", scopedNode("left", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), null));
            nodes.put("right", scopedNode("right", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("join")), null));
            nodes.put("join",
                    scopedNode("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, List.of(flow("nestedEnd")), null));
        } else {
            nodes.put("work",
                    scopedNode("work", ProcessSemanticPlan.NodeKind.ACTIVITY, List.of(flow("nestedEnd")), work));
        }
        nodes.put("nestedEnd", scopedNode("nestedEnd", ProcessSemanticPlan.NodeKind.END, List.of(), null));
        return new ProcessSemanticPlan("process.nested", variables(), nodes);
    }

    private static ActionPlan action(String output) {
        return new ActionPlan(null, new ActionInvocation.Java(Object.class.getName(), "toString"),
                List.of(ActionPlan.Input.expression("input", "input", Object.class.getName())),
                new ActionPlan.Output(output, Object.class.getName(), Object.class.getName()),
                EffectiveInvocationPolicy.defaults(), null);
    }

    private static ProcessSemanticPlan.NodePlan node(String id, ProcessSemanticPlan.NodeKind kind,
            List<ProcessSemanticPlan.TransitionPlan> flows, OperationPlan operation, IterationPlan iteration) {
        return new ProcessSemanticPlan.NodePlan(id, kind, ProcessSemanticPlan.ROOT_SCOPE_ID, null, flows, null,
                operation, iteration);
    }

    private static ProcessSemanticPlan.NodePlan scopedNode(String id, ProcessSemanticPlan.NodeKind kind,
            List<ProcessSemanticPlan.TransitionPlan> flows, OperationPlan operation) {
        return new ProcessSemanticPlan.NodePlan(id, kind, "scope", null, flows, null, operation, null);
    }

    private static ProcessSemanticPlan.TransitionPlan flow(String target) {
        return new ProcessSemanticPlan.TransitionPlan(target, null, false);
    }

    private static Map<String, ProcessSemanticPlan.VariablePlan> variables() {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = new LinkedHashMap<>();
        variables.put("input", variable("input"));
        variables.put("leftResult", variable("leftResult"));
        variables.put("rightResult", variable("rightResult"));
        variables.put("shared", variable("shared"));
        return variables;
    }

    private static ProcessSemanticPlan.VariablePlan variable(String name) {
        return new ProcessSemanticPlan.VariablePlan(name, Object.class.getName(), ProcessSemanticPlan.VariableRole.INNER,
                null);
    }
}
