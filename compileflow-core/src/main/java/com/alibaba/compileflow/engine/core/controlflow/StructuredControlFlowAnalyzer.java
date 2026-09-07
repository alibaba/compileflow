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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.java.expression.JavaExpressionInspector;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives structured control-flow facts from immutable Process semantics.
 *
 * <p>This is deliberately independent of source AST classes and target execution modes.
 *
 * @author yusu
 */
public final class StructuredControlFlowAnalyzer {
    /**
     * Derives immutable graph facts from one complete Process semantic plan.
     *
     * @param semanticPlan complete target-neutral Process semantics
     * @return validated structured control-flow plan
     */
    public StructuredControlFlowPlan analyze(ProcessSemanticPlan semanticPlan) {
        Objects.requireNonNull(semanticPlan, "semanticPlan");
        Map<String, GatewayPlan> gatewayPlans = new LinkedHashMap<>();
        String entryNodeId = null;
        for (String scopeId : scopeIds(semanticPlan)) {
            ScopeGraph graph = new ScopeGraph(semanticPlan, scopeId);
            analyzeScope(semanticPlan, graph, gatewayPlans);
            if (ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scopeId)) {
                entryNodeId = graph.start();
            }
        }
        return new StructuredControlFlowPlan(Objects.requireNonNull(entryNodeId, "root Process entry"), gatewayPlans);
    }

    private List<String> scopeIds(ProcessSemanticPlan semanticPlan) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(ProcessSemanticPlan.ROOT_SCOPE_ID);
        semanticPlan
            .getNodes()
            .values()
            .forEach(node -> ids.add(node.scopeId()));
        return List.copyOf(ids);
    }

    private void analyzeScope(ProcessSemanticPlan semanticPlan, ScopeGraph graph, Map<String, GatewayPlan> destination) {
        List<String> nodes = graph.nodes();
        String start = graph.start();
        String end = graph.end();
        Set<String> reachable = reachableNodes(graph, start);
        if (!reachable.contains(end)) {
            throw invalid("End node '" + end + "' is not reachable from start node '" + start + "'");
        }
        if (reachable.size() != nodes.size()) {
            List<String> unreachable = nodes
                .stream()
                .filter(node -> !reachable.contains(node))
                .toList();
            throw invalid(
                    "Process scope '" + graph.scopeId() + "' contains unreachable executable nodes: " + String.join(",",
                            unreachable));
        }
        validateTerminals(graph, reachable, end);
        validateExplicitBranching(graph, reachable);

        GraphTrees trees = calculateTrees(graph, start, end);
        List<String> gateways = reachable.stream().filter(graph::isGateway).toList();
        if (gateways.isEmpty()) {
            return;
        }

        List<String> splits = new ArrayList<>();
        List<String> joins = new ArrayList<>();
        for (String gateway : gateways) {
            if (gatewayShape(graph, gateway, start) == GatewayShape.SPLIT) {
                splits.add(gateway);
            } else {
                joins.add(gateway);
            }
        }

        Map<String, SplitRegion> regions = new LinkedHashMap<>();
        for (String split : splits) {
            String convergence = trees.immediatePostDominators().get(split);
            if (convergence == null || convergence.equals(split)) {
                throw invalid("Gateway '" + split + "' has no deterministic convergence node");
            }
            validateConvergence(graph, split, convergence);
            regions.put(split, analyzeRegion(semanticPlan, graph, split, convergence, trees));
        }

        validateJoinPairing(graph, joins, regions);
        validateRegionNesting(regions);

        for (String split : splits) {
            SplitRegion region = regions.get(split);
            ContinuationBoundary boundary = findParentBoundary(split, regions, end);
            Map<GatewayBranchKey, List<String>> branches = buildBranchPaths(graph, split, region.convergence());
            List<String> continuation = buildContinuationPath(graph, region.convergence(), boundary);
            boolean concurrent = graph.isConcurrentGateway(split);
            boolean suspending = region.nodes().stream().anyMatch(graph::isSuspending);
            putPlan(destination,
                    GatewayPlan.split(split, region.convergence(), buildBranchPlans(branches, region.variableAccesses()),
                            continuation, concurrent, suspending));
        }

        for (String join : joins) {
            if (graph
                .outgoingTransitions(join)
                .stream()
                .anyMatch(transition -> transition.condition() != null)) {
                throw invalid(
                        "Join gateway '" + join
                        + "' cannot declare a conditional outgoing transition; place routing on a subsequent split gateway");
            }
            putPlan(destination, GatewayPlan.join(join));
        }
        validateSingleOwnership(graph, destination);
    }

    private Set<String> reachableNodes(ScopeGraph graph, String start) {
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (!graph.contains(current)) {
                throw invalid("Control flow reaches node '" + current + "' outside its Process scope");
            }
            if (!reachable.add(current)) {
                continue;
            }
            List<String> outgoing = graph.outgoing(current);
            for (int index = outgoing.size() - 1; index >= 0; index--) {
                pending.push(outgoing.get(index));
            }
        }
        for (String node : reachable) {
            for (String incoming : graph.incoming(node)) {
                if (!reachable.contains(incoming)) {
                    throw invalid(
                            "Reachable node '" + node + "' has an external entry from unreachable node '" + incoming + "'");
                }
            }
        }
        LinkedHashSet<String> ordered =
                graph
            .nodes()
            .stream()
            .filter(reachable::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(ordered);
    }

    private void validateTerminals(ScopeGraph graph, Set<String> reachable, String end) {
        if (!graph.outgoingTransitions(end).isEmpty()) {
            throw invalid("Configured end node '" + end + "' must not have outgoing transitions");
        }
        List<String> invalidTerminals = reachable
            .stream()
            .filter(node -> graph.outgoingTransitions(node).isEmpty())
            .filter(node -> !node.equals(end) && !graph.isAbrupt(node))
            .toList();
        if (!invalidTerminals.isEmpty()) {
            throw invalid(
                    "Process scope '" + graph.scopeId()
                    + "' has terminal nodes that are neither its configured end nor abrupt-completion nodes: ["
                    + String.join(",", invalidTerminals) + "]");
        }
    }

    private void validateExplicitBranching(ScopeGraph graph, Set<String> nodes) {
        for (String node : nodes) {
            if (graph.isGateway(node)) {
                continue;
            }
            if (graph.outgoingTransitions(node).size() > 1) {
                throw invalid(
                        "Node '" + node + "' has " + graph.outgoingTransitions(node).size()
                        + " outgoing transitions; use an explicit gateway");
            }
            if (graph
                .outgoingTransitions(node)
                .stream()
                .anyMatch(transition -> transition.condition() != null)) {
                throw invalid(
                        "Node '" + node + "' has a conditional outgoing transition; conditions belong on explicit split gateways");
            }
        }
    }

    private GraphTrees calculateTrees(ScopeGraph graph, String start, String end) {
        List<String> topologicalOrder = topologicalOrder(graph);
        Map<String, String> dominators = calculateTree(topologicalOrder, start, graph::incoming);
        List<String> reverseOrder = new ArrayList<>(topologicalOrder);
        Collections.reverse(reverseOrder);
        Map<String, String> postDominators = calculateTree(reverseOrder, end, graph::outgoing);
        return new GraphTrees(dominators, postDominators);
    }

    private List<String> topologicalOrder(ScopeGraph graph) {
        Map<String, Integer> order = new HashMap<>();
        Map<String, Integer> remainingIncoming = new LinkedHashMap<>();
        for (int index = 0; index < graph.nodes().size(); index++) {
            String node = graph.nodes().get(index);
            order.put(node, index);
            remainingIncoming.put(node, graph.incoming(node).size());
        }
        Queue<String> ready = new PriorityQueue<>(Comparator.comparingInt(order::get));
        remainingIncoming.forEach((node, incoming) -> {
            if (incoming == 0) {
                ready.add(node);
            }
        });
        List<String> result = new ArrayList<>(graph.nodes().size());
        while (!ready.isEmpty()) {
            String node = ready.remove();
            result.add(node);
            for (String target : graph.outgoing(node)) {
                int remaining = remainingIncoming.compute(target, (ignored, count) -> {
                    if (count == null) {
                        throw new IllegalStateException("Transition from node '" + node + "' leaves its Process scope");
                    }
                    return count - 1;
                });
                if (remaining == 0) {
                    ready.add(target);
                }
            }
        }
        if (result.size() != graph.nodes().size()) {
            throw invalid(
                    "Cycle detected in Process scope; sorted " + result.size() + " of " + graph.nodes().size() + " nodes");
        }
        return result;
    }

    private Map<String, String> calculateTree(List<String> rootFirstOrder, String root, AdjacentNodes adjacentNodes) {
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < rootFirstOrder.size(); index++) {
            order.put(rootFirstOrder.get(index), index);
        }
        Map<String, String> immediate = new LinkedHashMap<>();
        immediate.put(root, root);
        for (String node : rootFirstOrder) {
            if (node.equals(root)) {
                continue;
            }
            String newDominator = null;
            for (String adjacent : adjacentNodes.get(node)) {
                if (!immediate.containsKey(adjacent)) {
                    continue;
                }
                newDominator = newDominator == null ? adjacent : intersect(newDominator, adjacent, immediate, order);
            }
            if (newDominator == null) {
                throw invalid("Node '" + node + "' is not connected to root '" + root + "'");
            }
            immediate.put(node, newDominator);
        }
        return Map.copyOf(immediate);
    }

    private String intersect(String first, String second, Map<String, String> immediate, Map<String, Integer> order) {
        String firstFinger = first;
        String secondFinger = second;
        while (!firstFinger.equals(secondFinger)) {
            while (order.get(firstFinger) > order.get(secondFinger)) {
                firstFinger = immediate.get(firstFinger);
            }
            while (order.get(secondFinger) > order.get(firstFinger)) {
                secondFinger = immediate.get(secondFinger);
            }
        }
        return firstFinger;
    }

    private GatewayShape gatewayShape(ScopeGraph graph, String gateway, String start) {
        int incoming = graph.incoming(gateway).size();
        if (gateway.equals(start) && incoming == 0) {
            incoming = 1;
        }
        int outgoing = graph.outgoing(gateway).size();
        if (incoming == 1 && outgoing > 1) {
            return GatewayShape.SPLIT;
        }
        if (incoming > 1 && outgoing == 1) {
            return GatewayShape.JOIN;
        }
        if (incoming > 1 && outgoing > 1) {
            throw invalid(
                    "Mixed gateway '" + gateway + "' has " + incoming + " incoming and " + outgoing
                    + " outgoing transitions; use separate join and split gateways");
        }
        throw invalid(
                "Gateway '" + gateway + "' must be either a split or a join, found " + incoming + " incoming and "
                + outgoing + " outgoing transitions");
    }

    private GatewayShape gatewayShape(ScopeGraph graph, String gateway) {
        return gatewayShape(graph, gateway, "");
    }

    private void validateConvergence(ScopeGraph graph, String split, String convergence) {
        if (graph.kind(convergence) == ProcessSemanticPlan.NodeKind.END) {
            return;
        }
        ProcessSemanticPlan.NodeKind kind = graph.kind(split);
        if (kind == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY) {
            requireMatchingJoin(graph, split, convergence, ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, "parallel");
            return;
        }
        if (kind == ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY) {
            requireMatchingJoin(graph, split, convergence, ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY, "inclusive");
            return;
        }
        if (kind == ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY) {
            if (graph.isGateway(convergence)) {
                requireMatchingJoin(graph, split, convergence, ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY,
                        "exclusive");
            }
            return;
        }
        throw invalid("Unsupported gateway type at node '" + split + "'");
    }

    private void requireMatchingJoin(ScopeGraph graph, String split, String convergence,
            ProcessSemanticPlan.NodeKind expectedKind, String typeName) {
        if (graph.kind(convergence) != expectedKind || gatewayShape(graph, convergence) != GatewayShape.JOIN) {
            throw invalid(
                    "Structured " + typeName + " split '" + split
                    + "' must converge at a matching join or the end node, found '" + convergence + "'");
        }
    }

    private SplitRegion analyzeRegion(ProcessSemanticPlan semanticPlan, ScopeGraph graph, String split,
            String convergence, GraphTrees trees) {
        Set<String> allRegionNodes = new LinkedHashSet<>();
        Map<String, String> branchOwners = new LinkedHashMap<>();
        List<BranchRegion> branches = new ArrayList<>();
        for (String branchStart : graph.outgoing(split)) {
            Set<String> branchNodes = collectUntil(graph, branchStart, convergence);
            branches.add(new BranchRegion(branchStart, branchNodes));
            for (String branchNode : branchNodes) {
                if (!dominates(split, branchNode, trees.immediateDominators())) {
                    throw invalid("Gateway region '" + split + "' has an external entry at node '" + branchNode + "'");
                }
                String previousOwner = branchOwners.putIfAbsent(branchNode, branchStart);
                if (previousOwner != null && !previousOwner.equals(branchStart)) {
                    throw invalid(
                            "Branches '" + previousOwner + "' and '" + branchStart + "' of gateway '" + split
                            + "' reconverge before the declared convergence node at '" + branchNode
                            + "'; introduce a nested structured gateway region");
                }
                allRegionNodes.add(branchNode);
            }
        }
        Map<GatewayBranchKey, VariableAccess> accesses =
                graph.isConcurrentGateway(split) ? collectBranchAccess(semanticPlan, graph, split, branches) : Map.of();
        return new SplitRegion(convergence, Set.copyOf(allRegionNodes), accesses);
    }

    private Set<String> collectUntil(ScopeGraph graph, String start, String convergence) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(start);
        boolean reachedConvergence = false;
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (current.equals(convergence)) {
                reachedConvergence = true;
                continue;
            }
            if (!visited.add(current)) {
                continue;
            }
            if (graph.kind(current) == ProcessSemanticPlan.NodeKind.END) {
                throw invalid(
                        "Branch starting at '" + start + "' terminates at end node '" + current
                        + "' before convergence node '" + convergence + "'");
            }
            List<String> outgoing = graph.outgoing(current);
            if (outgoing.isEmpty()) {
                throw invalid(
                        "Branch starting at '" + start + "' stops at node '" + current + "' before convergence node '" + convergence + "'");
            }
            for (int index = outgoing.size() - 1; index >= 0; index--) {
                pending.push(outgoing.get(index));
            }
        }
        if (!reachedConvergence) {
            throw invalid("Branch starting at '" + start + "' cannot reach convergence node '" + convergence + "'");
        }
        return Set.copyOf(visited);
    }

    private Map<GatewayBranchKey, VariableAccess> collectBranchAccess(ProcessSemanticPlan semanticPlan, ScopeGraph graph,
            String split, List<BranchRegion> branches) {
        Map<GatewayBranchKey, VariableAccess> result = new LinkedHashMap<>();
        for (BranchRegion branch : branches) {
            Set<String> reads = new LinkedHashSet<>();
            Set<String> writes = new LinkedHashSet<>();
            Set<String> visitedScopes = new LinkedHashSet<>();
            for (String node : graph.nodes()) {
                if (branch.nodes().contains(node)) {
                    collectNodeAccess(semanticPlan, node, reads, writes, visitedScopes);
                }
            }
            result.put(GatewayBranchKey.of(split, branch.start()),
                    new VariableAccess(immutableOrderedSet(reads), immutableOrderedSet(writes)));
        }
        return Collections.unmodifiableMap(result);
    }

    private void collectNodeAccess(ProcessSemanticPlan semanticPlan, String nodeId, Set<String> reads,
            Set<String> writes, Set<String> visitedScopes) {
        ProcessSemanticPlan.NodePlan node = semanticPlan.requireNode(nodeId);
        addExpressionReads(node.controlCondition(), semanticPlan.getVariables().keySet(), reads);
        node
            .outgoingTransitions()
            .forEach(transition -> addExpressionReads(transition.condition(), semanticPlan.getVariables().keySet(),
                    reads));
        addIterationAccess(node.iteration(), semanticPlan.getVariables().keySet(), reads, writes);
        addOperationAccess(node.operation(), semanticPlan.getVariables().keySet(), reads, writes);
        if (semanticPlan.ownsScope(nodeId) && visitedScopes.add(nodeId)) {
            for (ProcessSemanticPlan.NodePlan nested : semanticPlan.nodesInScope(nodeId)) {
                collectNodeAccess(semanticPlan, nested.id(), reads, writes, visitedScopes);
            }
        }
    }

    private void addIterationAccess(IterationPlan iteration, Set<String> variables, Set<String> reads,
            Set<String> writes) {
        if (iteration instanceof IterationPlan.While whilePlan) {
            addExpressionReads(whilePlan.condition(), variables, reads);
        } else if (iteration instanceof IterationPlan.ForEach forEach) {
            addMappedVariableAccess(forEach.collectionVariable(), variables, reads);
            if (forEach.outputTargetVariable() != null) {
                writes.add(forEach.outputSourceVariable());
                writes.add(forEach.outputTargetVariable());
            }
        }
    }

    private void addOperationAccess(OperationPlan operation, Set<String> variables, Set<String> reads,
            Set<String> writes) {
        if (operation instanceof ActionPlan action) {
            addActionAccess(action, variables, reads, writes);
        } else if (operation instanceof ProcessCallPlan call) {
            call
                .inputs()
                .forEach(input -> addMappedVariableAccess(input.sourceExpression(), variables, reads));
            call
                .outputs()
                .forEach(output -> writes.add(output.target()));
        }
    }

    private void addActionAccess(ActionPlan action, Set<String> variables, Set<String> reads, Set<String> writes) {
        action
            .inputs()
            .stream()
            .map(ActionPlan.Input::source)
            .filter(ActionPlan.InputSource.Expression.class::isInstance)
            .map(ActionPlan.InputSource.Expression.class::cast)
            .forEach(source -> addMappedVariableAccess(source.value(), variables, reads));
        if (action.output() != null) {
            writes.add(action.output().target());
        }
    }

    private void addMappedVariableAccess(String mapping, Set<String> variables, Set<String> destination) {
        if (mapping == null || mapping.isBlank()) {
            return;
        }
        int before = destination.size();
        addExpressionReads(mapping, variables, destination);
        if (destination.size() == before && ProcessNames.isIdentifier(mapping)) {
            destination.add(mapping);
        }
    }

    private void addExpressionReads(String expression, Set<String> variables, Set<String> destination) {
        if (expression != null && !expression.isBlank()) {
            destination.addAll(JavaExpressionInspector.referencedIdentifiers(expression, variables));
        }
    }

    private Set<String> immutableOrderedSet(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private boolean dominates(String dominator, String node, Map<String, String> immediateDominators) {
        String current = node;
        int remaining = immediateDominators.size() + 1;
        while (current != null && remaining-- > 0) {
            if (current.equals(dominator)) {
                return true;
            }
            String next = immediateDominators.get(current);
            if (current.equals(next)) {
                break;
            }
            current = next;
        }
        return false;
    }

    private void validateJoinPairing(ScopeGraph graph, List<String> joins, Map<String, SplitRegion> regions) {
        Map<String, List<String>> owners = new LinkedHashMap<>();
        regions.forEach((split, region) -> {
            if (graph.isGateway(region.convergence())) {
                owners
                    .computeIfAbsent(region.convergence(), ignored -> new ArrayList<>())
                    .add(split);
            }
        });
        for (String join : joins) {
            List<String> pairedSplits = owners.getOrDefault(join, List.of());
            if (pairedSplits.size() != 1) {
                throw invalid(
                        "Join gateway '" + join + "' must pair with exactly one split gateway, found "
                        + pairedSplits.size()
                        + (pairedSplits.isEmpty() ? "" : " [" + String.join(",", pairedSplits) + "]"));
            }
        }
    }

    private void validateRegionNesting(Map<String, SplitRegion> regions) {
        List<Map.Entry<String, SplitRegion>> entries = new ArrayList<>(regions.entrySet());
        for (int firstIndex = 0; firstIndex < entries.size(); firstIndex++) {
            Map.Entry<String, SplitRegion> first = entries.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < entries.size(); secondIndex++) {
                Map.Entry<String, SplitRegion> second = entries.get(secondIndex);
                boolean firstContainsSecond = first.getValue().nodes().contains(second.getKey());
                boolean secondContainsFirst = second.getValue().nodes().contains(first.getKey());
                if (firstContainsSecond && secondContainsFirst) {
                    throw invalid(
                            "Gateway regions '" + first.getKey() + "' and '" + second.getKey() + "' overlap without proper nesting");
                }
                if (firstContainsSecond) {
                    requireNestedRegion(first.getKey(), first.getValue(), second.getKey(), second.getValue());
                } else if (secondContainsFirst) {
                    requireNestedRegion(second.getKey(), second.getValue(), first.getKey(), first.getValue());
                } else if (!Collections.disjoint(first.getValue().nodes(), second.getValue().nodes())) {
                    throw invalid(
                            "Gateway regions '" + first.getKey() + "' and '" + second.getKey() + "' overlap without one containing the other");
                }
            }
        }
    }

    private void requireNestedRegion(String outerSplit, SplitRegion outer, String innerSplit, SplitRegion inner) {
        if (!outer.nodes().containsAll(inner.nodes())
                || !inner.convergence().equals(outer.convergence()) && !outer.nodes().contains(inner.convergence())) {
            throw invalid(
                    "Gateway region '" + innerSplit + "' crosses the boundary of gateway region '" + outerSplit + "'");
        }
    }

    private ContinuationBoundary findParentBoundary(String split, Map<String, SplitRegion> regions, String endNode) {
        SplitRegion parent = null;
        for (Map.Entry<String, SplitRegion> candidate : regions.entrySet()) {
            if (candidate.getKey().equals(split) || !candidate.getValue().nodes().contains(split)) {
                continue;
            }
            if (parent == null || candidate.getValue().nodes().size() < parent.nodes().size()) {
                parent = candidate.getValue();
            }
        }
        return parent == null
                ? new ContinuationBoundary(endNode, true)
                : new ContinuationBoundary(parent.convergence(), false);
    }

    private Map<GatewayBranchKey, List<String>> buildBranchPaths(ScopeGraph graph, String split, String convergence) {
        Map<GatewayBranchKey, List<String>> result = new LinkedHashMap<>();
        for (String branchStart : graph.outgoing(split)) {
            GatewayBranchKey key = GatewayBranchKey.of(split, branchStart);
            List<String> path = linearPath(graph, branchStart, convergence, "branch of '" + split + "'", false);
            List<String> existing = result.putIfAbsent(key, path);
            if (existing != null && !existing.equals(path)) {
                throw invalid(
                        "Outgoing transitions from gateway '" + split + "' to node '" + branchStart + "' resolve to different branch paths");
            }
        }
        return result;
    }

    private Map<GatewayBranchKey, GatewayBranchPlan> buildBranchPlans(Map<GatewayBranchKey, List<String>> paths,
            Map<GatewayBranchKey, VariableAccess> accesses) {
        Map<GatewayBranchKey, GatewayBranchPlan> result = new LinkedHashMap<>();
        for (Map.Entry<GatewayBranchKey, List<String>> branch : paths.entrySet()) {
            VariableAccess access = accesses.getOrDefault(branch.getKey(), VariableAccess.NONE);
            result.put(branch.getKey(), new GatewayBranchPlan(branch.getValue(), access.reads(), access.writes()));
        }
        return result;
    }

    private List<String> buildContinuationPath(ScopeGraph graph, String convergence, ContinuationBoundary boundary) {
        if (graph.kind(convergence) == ProcessSemanticPlan.NodeKind.END) {
            return List.of();
        }
        if (convergence.equals(boundary.node())) {
            return boundary.inclusive() && !graph.isGateway(convergence) ? List.of(convergence) : List.of();
        }
        String continuationStart = graph.isGateway(convergence)
                ? onlyOutgoing(graph, convergence, "join gateway '" + convergence + "'")
                : convergence;
        return linearPath(graph, continuationStart, boundary.node(), "continuation after '" + convergence + "'",
                boundary.inclusive());
    }

    private List<String> linearPath(ScopeGraph graph, String start, String boundary, String description,
            boolean includeBoundary) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = start;
        while (!current.equals(boundary)) {
            if (!visited.add(current)) {
                throw invalid("Cycle detected while building " + description + " at node '" + current + "'");
            }
            path.add(current);
            if (graph.kind(current) == ProcessSemanticPlan.NodeKind.END || graph.isAbrupt(current)
                    || graph.isSuspending(current)) {
                break;
            }
            if (graph.isGateway(current)) {
                if (gatewayShape(graph, current) != GatewayShape.SPLIT) {
                    throw invalid("Unexpected join gateway '" + current + "' inside " + description);
                }
                break;
            }
            current = onlyOutgoing(graph, current, description);
        }
        if (includeBoundary && graph.kind(boundary) != ProcessSemanticPlan.NodeKind.END && !graph.isGateway(boundary)) {
            path.add(boundary);
        }
        return List.copyOf(path);
    }

    private String onlyOutgoing(ScopeGraph graph, String node, String description) {
        List<String> outgoing = graph.outgoing(node);
        if (outgoing.size() != 1) {
            throw invalid(
                    "Node '" + node + "' in " + description + " must have exactly one outgoing transition, found " + outgoing.size());
        }
        return outgoing.get(0);
    }

    private void validateSingleOwnership(ScopeGraph graph, Map<String, GatewayPlan> plans) {
        Map<String, String> owners = new HashMap<>();
        for (GatewayPlan plan : plans.values()) {
            for (Map.Entry<GatewayBranchKey, GatewayBranchPlan> branch : plan.getBranches().entrySet()) {
                claimNodes(graph, branch.getValue().getNodeIds(), "branch " + branch.getKey(), owners);
            }
            claimNodes(graph, plan.getContinuationNodeIds(), "continuation of " + plan.getGatewayId(), owners);
        }
    }

    private void claimNodes(ScopeGraph graph, List<String> nodeIds, String owner, Map<String, String> owners) {
        for (String nodeId : nodeIds) {
            if (!graph.contains(nodeId) || graph.isGateway(nodeId)
                    || graph.kind(nodeId) == ProcessSemanticPlan.NodeKind.END) {
                continue;
            }
            String previous = owners.putIfAbsent(nodeId, owner);
            if (previous != null && !previous.equals(owner)) {
                throw invalid(
                        "Executable node '" + nodeId + "' is owned by both " + previous + " and " + owner
                        + "; shared code must have one structured continuation");
            }
        }
    }

    private void putPlan(Map<String, GatewayPlan> destination, GatewayPlan plan) {
        if (destination.putIfAbsent(plan.getGatewayId(), plan) != null) {
            throw invalid("Duplicate gateway id across Process scopes: " + plan.getGatewayId());
        }
    }

    private CompileFlowException invalid(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_005,
                "Unsupported process control-flow topology: " + message, null);
    }

    @FunctionalInterface
    private interface AdjacentNodes {
        List<String> get(String nodeId);
    }

    private enum GatewayShape {
        SPLIT,
        JOIN
    }

    private record GraphTrees(Map<String, String> immediateDominators, Map<String, String> immediatePostDominators) {}

    private record SplitRegion(String convergence, Set<String> nodes,
            Map<GatewayBranchKey, VariableAccess> variableAccesses) {}

    private record BranchRegion(String start, Set<String> nodes) {}

    private record ContinuationBoundary(String node, boolean inclusive) {}

    private record VariableAccess(Set<String> reads, Set<String> writes) {
        private static final VariableAccess NONE = new VariableAccess(Set.of(), Set.of());
    }

    private static final class ScopeGraph {
        private final String scopeId;
        private final String start;
        private final String end;
        private final List<String> nodes;
        private final Map<String, ProcessSemanticPlan.NodePlan> nodesById;
        private final Map<String, List<String>> outgoing;
        private final Map<String, List<String>> incoming;

        ScopeGraph(ProcessSemanticPlan semanticPlan, String scopeId) {
            this.scopeId = scopeId;
            List<ProcessSemanticPlan.NodePlan> members = semanticPlan.nodesInScope(scopeId);
            if (members.isEmpty()) {
                throw new IllegalArgumentException("Semantic scope '" + scopeId + "' has no executable nodes");
            }
            this.nodes = members.stream().map(ProcessSemanticPlan.NodePlan::id).toList();
            this.nodesById = new LinkedHashMap<>();
            members.forEach(node -> nodesById.put(node.id(), node));
            if (ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scopeId)) {
                this.start = singleNode(ProcessSemanticPlan.NodeKind.START, "start");
                this.end = singleNode(ProcessSemanticPlan.NodeKind.END, "end");
            } else {
                ProcessSemanticPlan.ScopeBoundary boundary = semanticPlan.requireNode(scopeId).scopeBoundary();
                if (boundary == null) {
                    throw new IllegalArgumentException("Semantic scope '" + scopeId + "' has no configured boundary");
                }
                this.start = boundary.startNodeId();
                this.end = boundary.endNodeId();
            }
            this.outgoing = buildOutgoing(nodesById);
            this.incoming = buildIncoming(nodes, outgoing);
        }

        String scopeId() {
            return scopeId;
        }

        List<String> nodes() {
            return nodes;
        }

        String start() {
            return start;
        }

        String end() {
            return end;
        }

        boolean contains(String nodeId) {
            return nodesById.containsKey(nodeId);
        }

        ProcessSemanticPlan.NodeKind kind(String nodeId) {
            return require(nodeId).kind();
        }

        boolean isGateway(String nodeId) {
            return switch (kind(nodeId)) {
                case EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY -> true;
                default -> false;
            };
        }

        boolean isConcurrentGateway(String nodeId) {
            return kind(nodeId) == ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY
                    || kind(nodeId) == ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY;
        }

        boolean isAbrupt(String nodeId) {
            return kind(nodeId) == ProcessSemanticPlan.NodeKind.BREAK
                    || kind(nodeId) == ProcessSemanticPlan.NodeKind.CONTINUE;
        }

        boolean isSuspending(String nodeId) {
            OperationPlan operation = require(nodeId).operation();
            return operation instanceof AwaitPlan || operation instanceof TimerPlan;
        }

        List<String> outgoing(String nodeId) {
            return outgoing.getOrDefault(nodeId, List.of());
        }

        List<String> incoming(String nodeId) {
            return incoming.getOrDefault(nodeId, List.of());
        }

        List<ProcessSemanticPlan.TransitionPlan> outgoingTransitions(String nodeId) {
            return require(nodeId).outgoingTransitions();
        }

        private String singleNode(ProcessSemanticPlan.NodeKind kind, String description) {
            List<String> matches = nodes
                .stream()
                .filter(node -> kind(node) == kind)
                .toList();
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        "Semantic scope '" + scopeId + "' must declare exactly one " + description + " node, found " + matches.size());
            }
            return matches.get(0);
        }

        private ProcessSemanticPlan.NodePlan require(String nodeId) {
            ProcessSemanticPlan.NodePlan node = nodesById.get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("Node '" + nodeId + "' is outside semantic scope '" + scopeId + "'");
            }
            return node;
        }

        private Map<String, List<String>> buildOutgoing(Map<String, ProcessSemanticPlan.NodePlan> localNodes) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (ProcessSemanticPlan.NodePlan node : localNodes.values()) {
                List<String> targets = new ArrayList<>();
                for (ProcessSemanticPlan.TransitionPlan transition : node.outgoingTransitions()) {
                    if (!localNodes.containsKey(transition.targetId())) {
                        throw new IllegalArgumentException(
                                "Transition from node '" + node.id() + "' leaves semantic scope '" + scopeId + "' for '"
                                + transition.targetId() + "'");
                    }
                    targets.add(transition.targetId());
                }
                if (!node.id().equals(end) && isAbrupt(node.id()) && !targets.contains(end)) {
                    targets.add(end);
                }
                result.put(node.id(), List.copyOf(targets));
            }
            return Collections.unmodifiableMap(result);
        }

        private static Map<String, List<String>> buildIncoming(List<String> nodes, Map<String, List<String>> outgoing) {
            Map<String, List<String>> mutable = new LinkedHashMap<>();
            nodes.forEach(node -> mutable.put(node, new ArrayList<>()));
            outgoing.forEach((source, targets) -> targets.forEach(target -> mutable.get(target).add(source)));
            Map<String, List<String>> result = new LinkedHashMap<>();
            mutable.forEach((node, sources) -> result.put(node, List.copyOf(sources)));
            return Collections.unmodifiableMap(result);
        }
    }
}
