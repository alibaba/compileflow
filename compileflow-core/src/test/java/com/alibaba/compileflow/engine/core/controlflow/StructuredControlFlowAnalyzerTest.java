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
package com.alibaba.compileflow.engine.core.controlflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class StructuredControlFlowAnalyzerTest {
    private final StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();

    @Test
    void derivesBranchesConvergenceAndSingleOwnedContinuation() {
        Graph graph = parallelGraph(action("leftResult"), action("rightResult"));
        graph.node("after", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.replaceFlow("join", "end", "after");
        graph.flow("after", "end");

        StructuredControlFlowPlan plan = analyzer.analyze(graph.plan());
        GatewayPlan split = plan.requireGatewayPlan("split");

        assertThat(split.getConvergenceNodeId()).isEqualTo("join");
        assertThat(split.getBranches().values())
            .extracting(GatewayBranchPlan::getNodeIds)
            .containsExactly(List.of("left"), List.of("right"));
        assertThat(split.getContinuationNodeIds()).containsExactly("after");
        assertThat(plan.requireGatewayPlan("join").isJoin()).isTrue();
        assertThat(occurrences(plan, "after")).isEqualTo(1);
    }

    @Test
    void derivesProperlyNestedGatewayRegions() {
        Graph graph = new Graph("nested.gateways");
        graph.node("start", ProcessSemanticPlan.NodeKind.START);
        graph.node("outerSplit", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("prefix", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("innerSplit", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("innerLeft", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("innerRight", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("innerJoin", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("suffix", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("outerRight", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("outerJoin", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("after", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("end", ProcessSemanticPlan.NodeKind.END);
        graph.flow("start", "outerSplit");
        graph.flow("outerSplit", "prefix");
        graph.flow("outerSplit", "outerRight");
        graph.flow("prefix", "innerSplit");
        graph.flow("innerSplit", "innerLeft");
        graph.flow("innerSplit", "innerRight");
        graph.flow("innerLeft", "innerJoin");
        graph.flow("innerRight", "innerJoin");
        graph.flow("innerJoin", "suffix");
        graph.flow("suffix", "outerJoin");
        graph.flow("outerRight", "outerJoin");
        graph.flow("outerJoin", "after");
        graph.flow("after", "end");

        StructuredControlFlowPlan plan = analyzer.analyze(graph.plan());

        assertThat(plan.requireGatewayPlan("innerSplit").getContinuationNodeIds()).containsExactly("suffix");
        assertThat(plan.requireGatewayPlan("outerSplit").getContinuationNodeIds()).containsExactly("after");
        assertThat(occurrences(plan, "suffix")).isEqualTo(1);
    }

    @Test
    void rejectsPartialJoinAndMixedGatewayTopologies() {
        Graph partial = new Graph("partial.join");
        partial.node("start", ProcessSemanticPlan.NodeKind.START);
        partial.node("split", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY);
        partial.node("a", ProcessSemanticPlan.NodeKind.ACTIVITY);
        partial.node("b", ProcessSemanticPlan.NodeKind.ACTIVITY);
        partial.node("c", ProcessSemanticPlan.NodeKind.ACTIVITY);
        partial.node("partial", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY);
        partial.node("afterPartial", ProcessSemanticPlan.NodeKind.ACTIVITY);
        partial.node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY);
        partial.node("end", ProcessSemanticPlan.NodeKind.END);
        partial.flow("start", "split");
        partial.flow("split", "a");
        partial.flow("split", "b");
        partial.flow("split", "c");
        partial.flow("a", "partial");
        partial.flow("b", "partial");
        partial.flow("partial", "afterPartial");
        partial.flow("afterPartial", "join");
        partial.flow("c", "join");
        partial.flow("join", "end");

        assertThatThrownBy(() -> analyzer.analyze(partial.plan()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("reconverge before the declared convergence");

        Graph mixed = new Graph("mixed.gateway");
        mixed.node("start", ProcessSemanticPlan.NodeKind.START);
        mixed.node("split", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        mixed.node("a", ProcessSemanticPlan.NodeKind.ACTIVITY);
        mixed.node("b", ProcessSemanticPlan.NodeKind.ACTIVITY);
        mixed.node("mixed", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        mixed.node("left", ProcessSemanticPlan.NodeKind.ACTIVITY);
        mixed.node("right", ProcessSemanticPlan.NodeKind.ACTIVITY);
        mixed.node("end", ProcessSemanticPlan.NodeKind.END);
        mixed.flow("start", "split");
        mixed.flow("split", "a");
        mixed.flow("split", "b");
        mixed.flow("a", "mixed");
        mixed.flow("b", "mixed");
        mixed.flow("mixed", "left");
        mixed.flow("mixed", "right");
        mixed.flow("left", "end");
        mixed.flow("right", "end");

        assertThatThrownBy(() -> analyzer.analyze(mixed.plan()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("use separate join and split gateways");
    }

    @Test
    void rejectsUnreachableInvalidTerminalAndImplicitBranchingGraphs() {
        Graph unreachable = new Graph("unreachable");
        unreachable.node("start", ProcessSemanticPlan.NodeKind.START);
        unreachable.node("task", ProcessSemanticPlan.NodeKind.ACTIVITY);
        unreachable.node("orphan", ProcessSemanticPlan.NodeKind.ACTIVITY);
        unreachable.node("end", ProcessSemanticPlan.NodeKind.END);
        unreachable.flow("start", "task");
        unreachable.flow("task", "end");
        assertThatThrownBy(() -> analyzer.analyze(unreachable.plan()))
            .hasMessageContaining("unreachable executable nodes: orphan");

        Graph afterEnd = new Graph("after.end");
        afterEnd.node("start", ProcessSemanticPlan.NodeKind.START);
        afterEnd.node("end", ProcessSemanticPlan.NodeKind.END);
        afterEnd.node("trailing", ProcessSemanticPlan.NodeKind.ACTIVITY);
        afterEnd.flow("start", "end");
        afterEnd.flow("end", "trailing");
        assertThatThrownBy(() -> analyzer.analyze(afterEnd.plan()))
            .hasMessageContaining("Configured end node 'end' must not have outgoing transitions");

        Graph implicit = new Graph("implicit.branch");
        implicit.node("start", ProcessSemanticPlan.NodeKind.START);
        implicit.node("left", ProcessSemanticPlan.NodeKind.ACTIVITY);
        implicit.node("right", ProcessSemanticPlan.NodeKind.ACTIVITY);
        implicit.node("end", ProcessSemanticPlan.NodeKind.END);
        implicit.flow("start", "left");
        implicit.flow("start", "right");
        implicit.flow("left", "end");
        implicit.flow("right", "end");
        assertThatThrownBy(() -> analyzer.analyze(implicit.plan())).hasMessageContaining("use an explicit gateway");

        Graph conditional = new Graph("implicit.condition");
        conditional.variable("value");
        conditional.node("start", ProcessSemanticPlan.NodeKind.START);
        conditional.node("task", ProcessSemanticPlan.NodeKind.ACTIVITY);
        conditional.node("end", ProcessSemanticPlan.NodeKind.END);
        conditional.flow("start", "task");
        conditional.flow("task", "end", "value > 0");
        assertThatThrownBy(() -> analyzer.analyze(conditional.plan()))
            .hasMessageContaining("conditions belong on explicit split gateways");
    }

    @Test
    void rejectsConditionalJoinExitAndAbruptJoinBypass() {
        Graph conditionalJoin = decisionGraph();
        conditionalJoin.replaceFlow("join", "end", "end", "ignored");
        assertThatThrownBy(() -> analyzer.analyze(conditionalJoin.plan()))
            .hasMessageContaining("Join gateway 'join' cannot declare a conditional outgoing transition");

        Graph abrupt = new Graph("abrupt.bypass");
        abrupt.node("start", ProcessSemanticPlan.NodeKind.START);
        abrupt.node("split", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        abrupt.node("stop", ProcessSemanticPlan.NodeKind.BREAK);
        abrupt.node("normal", ProcessSemanticPlan.NodeKind.ACTIVITY);
        abrupt.node("join", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        abrupt.node("end", ProcessSemanticPlan.NodeKind.END);
        abrupt.flow("start", "split");
        abrupt.flow("split", "stop", "shouldStop");
        abrupt.flow("split", "normal");
        abrupt.flow("stop", "join");
        abrupt.flow("normal", "join");
        abrupt.flow("join", "end");
        assertThatThrownBy(() -> analyzer.analyze(abrupt.plan()))
            .hasMessageContaining("reconverge before the declared convergence node at 'join'");
    }

    @Test
    void derivesNestedScopeFactsFromConfiguredNonEndBoundary() {
        Graph graph = new Graph("nested.boundary");
        graph.node("start", ProcessSemanticPlan.NodeKind.START);
        graph.node("loop", ProcessSemanticPlan.NodeKind.ACTIVITY).boundary = new ProcessSemanticPlan.ScopeBoundary("f"
                + "ork", "stop");
        graph.node("end", ProcessSemanticPlan.NodeKind.END);
        graph.node("fork", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY).scopeId = "loop";
        graph.node("left", ProcessSemanticPlan.NodeKind.ACTIVITY).scopeId = "loop";
        graph.node("right", ProcessSemanticPlan.NodeKind.ACTIVITY).scopeId = "loop";
        graph.node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY).scopeId = "loop";
        graph.node("stop", ProcessSemanticPlan.NodeKind.BREAK).scopeId = "loop";
        graph.flow("start", "loop");
        graph.flow("loop", "end");
        graph.flow("fork", "left");
        graph.flow("fork", "right");
        graph.flow("left", "join");
        graph.flow("right", "join");
        graph.flow("join", "stop");

        StructuredControlFlowPlan plan = analyzer.analyze(graph.plan());

        assertThat(plan.requireGatewayPlan("fork").getConvergenceNodeId()).isEqualTo("join");
        assertThat(plan.requireGatewayPlan("join").isJoin()).isTrue();
    }

    @Test
    void recordsSuspensionAndBranchAccessWithoutApplyingTargetRules() {
        Graph graph = parallelGraph(new AwaitPlan("approved", null), action("shared"));
        graph.variable("shared");

        StructuredControlFlowPlan plan = analyzer.analyze(graph.plan());
        GatewayPlan split = plan.requireGatewayPlan("split");

        assertThat(split.isSuspending()).isTrue();
        assertThat(split.requireBranch(GatewayBranchKey.of("split", "right")).getWrites()).containsExactly("shared");
    }

    @Test
    @Timeout(5)
    void analyzesTenThousandNodeContinuationIteratively() {
        Graph graph = decisionGraph();
        graph.removeFlow("join", "end");
        String previous = "join";
        for (int index = 0; index < 10_000; index++) {
            String current = "task" + index;
            graph.node(current, ProcessSemanticPlan.NodeKind.ACTIVITY);
            graph.flow(previous, current);
            previous = current;
        }
        graph.flow(previous, "end");

        assertThat(analyzer.analyze(graph.plan()).requireGatewayPlan("split").getContinuationNodeIds()).hasSize(10_000);
    }

    private static Graph parallelGraph(OperationPlan left, OperationPlan right) {
        Graph graph = new Graph("parallel");
        graph.variable("input");
        graph.variable("leftResult");
        graph.variable("rightResult");
        graph.node("start", ProcessSemanticPlan.NodeKind.START);
        graph.node("split", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY);
        graph.node("left", ProcessSemanticPlan.NodeKind.ACTIVITY).operation = left;
        graph.node("right", ProcessSemanticPlan.NodeKind.ACTIVITY).operation = right;
        graph.node("join", ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY);
        graph.node("end", ProcessSemanticPlan.NodeKind.END);
        graph.flow("start", "split");
        graph.flow("split", "left");
        graph.flow("split", "right");
        graph.flow("left", "join");
        graph.flow("right", "join");
        graph.flow("join", "end");
        return graph;
    }

    private static Graph decisionGraph() {
        Graph graph = new Graph("decision");
        graph.node("start", ProcessSemanticPlan.NodeKind.START);
        graph.node("split", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("left", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("right", ProcessSemanticPlan.NodeKind.ACTIVITY);
        graph.node("join", ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY);
        graph.node("end", ProcessSemanticPlan.NodeKind.END);
        graph.flow("start", "split");
        graph.flow("split", "left");
        graph.flow("split", "right");
        graph.flow("left", "join");
        graph.flow("right", "join");
        graph.flow("join", "end");
        return graph;
    }

    private static ActionPlan action(String output) {
        return new ActionPlan(null, new ActionInvocation.Java(Object.class.getName(), "toString"),
                List.of(ActionPlan.Input.expression("input", "input", Object.class.getName())),
                new ActionPlan.Output(output, Object.class.getName(), Object.class.getName()),
                EffectiveInvocationPolicy.defaults(), null);
    }

    private static int occurrences(StructuredControlFlowPlan plan, String nodeId) {
        int result = 0;
        for (GatewayPlan gateway : plan.getGatewayPlans().values()) {
            for (GatewayBranchPlan branch : gateway.getBranches().values()) {
                result += java.util.Collections.frequency(branch.getNodeIds(), nodeId);
            }
            result += java.util.Collections.frequency(gateway.getContinuationNodeIds(), nodeId);
        }
        return result;
    }

    private static final class Graph {
        private final String code;
        private final Map<String, NodeSpec> nodes = new LinkedHashMap<>();
        private final Map<String, ProcessSemanticPlan.VariablePlan> variables = new LinkedHashMap<>();

        private Graph(String code) {
            this.code = code;
        }

        private NodeSpec node(String id, ProcessSemanticPlan.NodeKind kind) {
            NodeSpec node = new NodeSpec(kind);
            if (nodes.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("Duplicate test node " + id);
            }
            return node;
        }

        private void variable(String name) {
            variables.putIfAbsent(name,
                    new ProcessSemanticPlan.VariablePlan(name, Object.class.getName(),
                            ProcessSemanticPlan.VariableRole.INNER, null));
        }

        private void flow(String source, String target) {
            flow(source, target, null);
        }

        private void flow(String source, String target, String condition) {
            nodes.get(source).transitions.add(new ProcessSemanticPlan.TransitionPlan(target, condition, false));
        }

        private void removeFlow(String source, String target) {
            nodes.get(source).transitions.removeIf(flow -> flow.targetId().equals(target));
        }

        private void replaceFlow(String source, String oldTarget, String newTarget) {
            replaceFlow(source, oldTarget, newTarget, null);
        }

        private void replaceFlow(String source, String oldTarget, String newTarget, String condition) {
            removeFlow(source, oldTarget);
            flow(source, newTarget, condition);
        }

        private ProcessSemanticPlan plan() {
            Map<String, ProcessSemanticPlan.NodePlan> result = new LinkedHashMap<>();
            nodes.forEach((id, node) -> result.put(id,
                    new ProcessSemanticPlan.NodePlan(id, node.kind, node.scopeId, node.boundary, node.transitions, null,
                            node.operation, null)));
            return new ProcessSemanticPlan(code, variables, result);
        }
    }

    private static final class NodeSpec {
        private final ProcessSemanticPlan.NodeKind kind;
        private final List<ProcessSemanticPlan.TransitionPlan> transitions = new ArrayList<>();
        private String scopeId = ProcessSemanticPlan.ROOT_SCOPE_ID;
        private ProcessSemanticPlan.ScopeBoundary boundary;
        private OperationPlan operation;

        private NodeSpec(ProcessSemanticPlan.NodeKind kind) {
            this.kind = kind;
        }
    }
}
