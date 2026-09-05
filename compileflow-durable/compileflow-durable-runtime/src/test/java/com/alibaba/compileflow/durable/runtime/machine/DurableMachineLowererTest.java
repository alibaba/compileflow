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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurableMachineLowererTest {
    private final StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();
    private final DurableMachineLowerer lowerer = new DurableMachineLowerer();

    @Test
    void lowersBoundaryStateResumeAndDeterministicDigest() {
        ProcessSemanticPlan first = awaitProcess(false);
        ProcessSemanticPlan reordered = awaitProcess(true);

        DurableMachinePlan machine = lower(first);
        DurableMachinePlan sameMeaning = lower(reordered);

        assertThat(machine.requireStep("start"))
            .isEqualTo(new DurableMachinePlan.Step.Advance(new DurableMachinePlan.Next.Node("wait")));
        assertThat(machine.requireStep("wait"))
            .isEqualTo(new DurableMachinePlan.Step.Await(new DurableMachinePlan.Next.Node("end")));
        assertThat(machine.requireStep("end")).isEqualTo(new DurableMachinePlan.Step.Complete());
        assertThat(machine.nextAfterBoundary("wait")).isEqualTo(new DurableMachinePlan.Next.Node("end"));
        assertThat(machine.resumes())
            .containsKeys(ResumePoint.beforeElement("start").key(), ResumePoint.beforeElement("wait").key(),
                    ResumePoint.afterElement("wait").key(), ResumePoint.beforeElement("end").key());
        assertThat(machine.requireResume(ResumePoint.afterElement("wait").key()).boundaryKind()).isEqualTo(
                BoundaryKind.WAIT);
        assertThat(machine.stateSchema().fields())
            .extracting(field -> field.name() + ":" + field.startInput())
            .containsExactly("input:true", "result:false");
        assertThat(machine.digest())
            .hasSize(64)
            .isEqualTo(sameMeaning.digest())
            .isEqualTo("78b0b68a7ca2794ca41cb5d1bdc3f6314ea5be43018ff83cd037bd66c777d879");
        assertThat(machine.canonicalForm()).isEqualTo(sameMeaning.canonicalForm());
    }

    @Test
    void lowersExclusiveAndInclusiveSelectionsWithExactBindings() {
        DurableMachinePlan exclusive = lower(decisionProcess(ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY));
        DurableMachinePlan inclusive = lower(decisionProcess(ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY));

        DurableMachinePlan.Step.ChooseOne choose = (DurableMachinePlan.Step.ChooseOne) exclusive.requireStep("choice");
        assertThat(choose.defaultTargetNodeId()).isEqualTo("no");
        assertThat(choose.branches())
            .singleElement()
            .satisfies(branch -> {
                assertThat(branch.targetNodeId()).isEqualTo("yes");
                assertThat(branch.condition().source()).isEqualTo("approved");
                assertThat(branch.condition().bindings())
                    .containsExactly(
                            new BoundExpression.Binding("approved", "java.lang.Boolean",
                                    BoundExpression.Binding.Source.STATE));
            });

        DurableMachinePlan.Step.ForkSelected selected =
                (DurableMachinePlan.Step.ForkSelected) inclusive.requireStep("choice");
        assertThat(selected.joinNodeId()).isEqualTo("end");
        assertThat(selected.defaultActivation().branchStartId()).isEqualTo("no");
        assertThat(selected.branches())
            .singleElement()
            .extracting(DurableMachinePlan.ConditionalBranch::targetNodeId)
            .isEqualTo("yes");
        assertThat(inclusive.isConcurrentJoin("end")).isTrue();
        assertThat(inclusive.resumes()).containsKey(ResumePoint.atJoin("end").key());
    }

    @Test
    void lowersParallelForkAndDerivesJoinWithoutDuplicatedFlags() {
        DurableMachinePlan machine = lower(parallelProcess());

        assertThat(machine.requireStep("fork"))
            .isEqualTo(
                    new DurableMachinePlan.Step.ForkAll("join",
                            List.of(new BranchActivation(0, "left"), new BranchActivation(1, "right"))));
        assertThat(machine.requireStep("join"))
            .isEqualTo(new DurableMachinePlan.Step.Advance(new DurableMachinePlan.Next.Node("end")));
        assertThat(machine.isConcurrentJoin("join")).isTrue();
        assertThat(machine.requireResume(ResumePoint.atJoin("join").key()).boundaryKind()).isNull();
    }

    @Test
    void preservesDistinctActivationsThatShareOneBranchBody() {
        DurableMachinePlan machine = lower(duplicateTargetParallelProcess());

        assertThat(machine.requireStep("fork"))
            .isEqualTo(
                    new DurableMachinePlan.Step.ForkAll("join",
                            List.of(new BranchActivation(0, "shared"), new BranchActivation(1, "shared"),
                                    new BranchActivation(2, "other"))));
    }

    @Test
    void normalizesNestedScopeExitAndLoopControl() {
        DurableMachinePlan machine = lower(loopScopeProcess());

        assertThat(machine.requireStep("loop")).isEqualTo(new DurableMachinePlan.Step.EnterIteration("loop"));
        DurableMachinePlan.Iteration.While loop = (DurableMachinePlan.Iteration.While) machine.requireIteration("loop");
        assertThat(loop.body()).isEqualTo(new DurableMachinePlan.Body.Scope("nestedStart"));
        assertThat(loop.exit()).isEqualTo(new DurableMachinePlan.Next.Node("end"));
        assertThat(loop.condition().bindings())
            .containsExactly(
                    new BoundExpression.Binding("count", "java.lang.Integer", BoundExpression.Binding.Source.STATE));

        DurableMachinePlan.Step.BreakIteration control =
                (DurableMachinePlan.Step.BreakIteration) machine.requireStep("break");
        assertThat(control.iterationId()).isEqualTo("loop");
        assertThat(control.whenNotTaken()).isEqualTo(new DurableMachinePlan.Next.AdvanceIteration("loop"));
        assertThat(control.condition().bindings())
            .containsExactly(new BoundExpression.Binding("i", "int", BoundExpression.Binding.Source.FRAME));
        assertThat(machine.requireResume(ResumePoint.beforeElement("break").key()).expectedFramePath())
            .containsExactly(new ResumeDescriptor.FrameDescriptor("loop", ResumeDescriptor.FrameKind.WHILE, null, 5));
    }

    @Test
    void retainsTimerBindingAndBoundaryFrameForOperationIteration() {
        DurableMachinePlan machine = lower(iteratedTimerProcess());

        DurableMachinePlan.Iteration.While iteration =
                (DurableMachinePlan.Iteration.While) machine.requireIteration("timerLoop");
        DurableMachinePlan.Body.Operation body = (DurableMachinePlan.Body.Operation) iteration.body();
        assertThat(body.scheduleExpression().resultKind()).isEqualTo(BoundExpression.ResultKind.DURATION);
        assertThat(body.scheduleExpression().bindings())
            .containsExactly(
                    new BoundExpression.Binding("delay", "java.time.Duration", BoundExpression.Binding.Source.STATE));
        assertThat(machine.nextAfterBoundary("timerLoop")).isEqualTo(
                new DurableMachinePlan.Next.AdvanceIteration("timerLoop"));
        assertThat(machine.requireResume(ResumePoint.afterElement("timerLoop").key()).expectedFramePath())
            .containsExactly(
                    new ResumeDescriptor.FrameDescriptor("timerLoop", ResumeDescriptor.FrameKind.WHILE, null, 3));
    }

    @Test
    void machineInvariantsAreFailClosedAndSealedVariantsStayExhaustive() {
        DurableMachinePlan machine = lower(awaitProcess(false));
        Map<String, DurableMachinePlan.Step> missing = new LinkedHashMap<>(machine.steps());
        missing.remove("wait");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new DurableMachinePlan(machine.semanticPlan(), machine.entryNodeId(),
                    machine.stateSchema(), missing, machine.iterations()))
            .withMessageContaining("cover every semantic node");
        assertThat(DurableMachinePlan.Step.class.getPermittedSubclasses())
            .extracting(Class::getSimpleName)
            .containsExactlyInAnyOrder("Advance", "Complete", "Replayable", "Await", "Timer", "Effect", "ProcessCall",
                    "ChooseOne", "ForkAll", "ForkSelected", "EnterIteration", "BreakIteration", "ContinueIteration");
        assertThat(DurableMachinePlan.Next.class.getPermittedSubclasses())
            .extracting(Class::getSimpleName)
            .containsExactlyInAnyOrder("Node", "AdvanceIteration");
        assertThat(DurableMachinePlan.Iteration.class.getPermittedSubclasses())
            .extracting(Class::getSimpleName)
            .containsExactlyInAnyOrder("While", "ForEach");
        assertThat(DurableMachinePlan.Body.class.getPermittedSubclasses())
            .extracting(Class::getSimpleName)
            .containsExactlyInAnyOrder("Scope", "Operation");
    }

    private DurableMachinePlan lower(ProcessSemanticPlan semantics) {
        return lowerer.lower(semantics, analyzer.analyze(semantics));
    }

    private ProcessSemanticPlan awaitProcess(boolean reverseOrder) {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        ProcessSemanticPlan.NodePlan start = node("start", ProcessSemanticPlan.NodeKind.START, "wait");
        ProcessSemanticPlan.NodePlan wait = node("wait", ProcessSemanticPlan.NodeKind.ACTIVITY,
                ProcessSemanticPlan.ROOT_SCOPE_ID, List.of(transition("end")), null, new AwaitPlan("approved", null),
                null, null);
        ProcessSemanticPlan.NodePlan end = terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID);
        if (reverseOrder) {
            put(nodes, end, wait, start);
        } else {
            put(nodes, start, wait, end);
        }
        Map<String, ProcessSemanticPlan.VariablePlan> variables = new LinkedHashMap<>();
        variables.put("result",
                new ProcessSemanticPlan.VariablePlan("result", "java.lang.String",
                        ProcessSemanticPlan.VariableRole.RETURN, null));
        variables.put("input",
                new ProcessSemanticPlan.VariablePlan("input", "java.lang.String", ProcessSemanticPlan.VariableRole.PARAM,
                        null));
        return new ProcessSemanticPlan("machine.await", variables, nodes);
    }

    private ProcessSemanticPlan decisionProcess(ProcessSemanticPlan.NodeKind gatewayKind) {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        put(nodes, node("start", ProcessSemanticPlan.NodeKind.START, "choice"),
                node("choice", gatewayKind, ProcessSemanticPlan.ROOT_SCOPE_ID,
                        List.of(new ProcessSemanticPlan.TransitionPlan("yes", "approved", false),
                                new ProcessSemanticPlan.TransitionPlan("no", null, true)), null, null, null, null),
                node("yes", ProcessSemanticPlan.NodeKind.ACTIVITY, "end"),
                node("no", ProcessSemanticPlan.NodeKind.ACTIVITY, "end"),
                terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID));
        return new ProcessSemanticPlan("machine.decision",
                Map.of("approved",
                        new ProcessSemanticPlan.VariablePlan("approved", "java.lang.Boolean",
                                ProcessSemanticPlan.VariableRole.PARAM, null)), nodes);
    }

    private ProcessSemanticPlan parallelProcess() {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        put(nodes, node("start", ProcessSemanticPlan.NodeKind.START, "fork"),
                node("fork", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                        List.of(transition("left"), transition("right")), null, null, null, null),
                node("left", ProcessSemanticPlan.NodeKind.ACTIVITY, "join"),
                node("right", ProcessSemanticPlan.NodeKind.ACTIVITY, "join"),
                node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, "end"),
                terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID));
        return new ProcessSemanticPlan("machine.parallel", Map.of(), nodes);
    }

    private ProcessSemanticPlan duplicateTargetParallelProcess() {
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        put(nodes, node("start", ProcessSemanticPlan.NodeKind.START, "fork"),
                node("fork", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                        List.of(transition("shared"), transition("shared"), transition("other")), null, null, null, null),
                node("shared", ProcessSemanticPlan.NodeKind.ACTIVITY, "join"),
                node("other", ProcessSemanticPlan.NodeKind.ACTIVITY, "join"),
                node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, "end"),
                terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID));
        return new ProcessSemanticPlan("machine.duplicate-target-parallel", Map.of(), nodes);
    }

    private ProcessSemanticPlan loopScopeProcess() {
        IterationPlan.While iteration = new IterationPlan.While("count < 3", IterationPlan.ConditionTiming.BEFORE, 5,
                IterationPlan.LimitBehavior.FAIL, "i");
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        put(nodes, node("start", ProcessSemanticPlan.NodeKind.START, "loop"),
                node("loop", ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                        List.of(transition("end")), new ProcessSemanticPlan.ScopeBoundary("nestedStart", "break"), null,
                        iteration, null), terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID),
                node("nestedStart", ProcessSemanticPlan.NodeKind.START, "loop", List.of(transition("break")), null, null,
                        null, null),
                node("break", ProcessSemanticPlan.NodeKind.BREAK, "loop", List.of(), null, null, null, "i > 1"));
        return new ProcessSemanticPlan("machine.loop",
                Map.of("count",
                        new ProcessSemanticPlan.VariablePlan("count", "java.lang.Integer",
                                ProcessSemanticPlan.VariableRole.PARAM, null)), nodes);
    }

    private ProcessSemanticPlan iteratedTimerProcess() {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = Map.of("count",
                new ProcessSemanticPlan.VariablePlan("count", "java.lang.Integer",
                        ProcessSemanticPlan.VariableRole.PARAM, null), "delay",
                new ProcessSemanticPlan.VariablePlan("delay", "java.time.Duration",
                        ProcessSemanticPlan.VariableRole.PARAM, null));
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        put(nodes, node("start", ProcessSemanticPlan.NodeKind.START, "timerLoop"),
                node("timerLoop", ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                        List.of(transition("end")), null, new TimerPlan(TimerPlan.Kind.DURATION_EXPRESSION, "delay"),
                        new IterationPlan.While("count < 3", IterationPlan.ConditionTiming.BEFORE, 3,
                                IterationPlan.LimitBehavior.FAIL, null), null),
                terminal("end", ProcessSemanticPlan.ROOT_SCOPE_ID));
        return new ProcessSemanticPlan("machine.timer-loop", variables, nodes);
    }

    private static ProcessSemanticPlan.NodePlan node(String id, ProcessSemanticPlan.NodeKind kind, String target) {
        return node(id, kind, ProcessSemanticPlan.ROOT_SCOPE_ID, List.of(transition(target)), null, null, null, null);
    }

    private static ProcessSemanticPlan.NodePlan terminal(String id, String scopeId) {
        return node(id, ProcessSemanticPlan.NodeKind.END, scopeId, List.of(), null, null, null, null);
    }

    private static ProcessSemanticPlan.NodePlan node(String id, ProcessSemanticPlan.NodeKind kind, String scopeId,
            List<ProcessSemanticPlan.TransitionPlan> transitions, ProcessSemanticPlan.ScopeBoundary boundary,
            OperationPlan operation, IterationPlan iteration, String controlCondition) {
        return new ProcessSemanticPlan.NodePlan(id, kind, scopeId, boundary, transitions, controlCondition, operation,
                iteration);
    }

    private static ProcessSemanticPlan.TransitionPlan transition(String target) {
        return new ProcessSemanticPlan.TransitionPlan(target, null, false);
    }

    private static void put(Map<String, ProcessSemanticPlan.NodePlan> destination,
            ProcessSemanticPlan.NodePlan... nodes) {
        for (ProcessSemanticPlan.NodePlan node : nodes) {
            destination.put(node.id(), node);
        }
    }
}
