package com.alibaba.compileflow.engine.core.builder.generator.analyzer;

import com.alibaba.compileflow.engine.core.definition.TransitionNode;

import java.util.*;

/**
 * @author yusu
 */
public class DominatorTreeCalculator {

    /**
     * Compute immediate dominators.
     */
    public void calculateDominatorTree(AnalysisContext context) {
        context.getImmediateDominators().put(context.getStartNode(), context.getStartNode());
        for (TransitionNode node : context.getAllNodes()) {
            if (!node.equals(context.getStartNode())) {
                context.getImmediateDominators().put(node, null);
            }
        }
        List<TransitionNode> topologicalOrder = topologicalSort(context);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TransitionNode node : topologicalOrder) {
                if (node.equals(context.getStartNode())) {
                    continue;
                }
                List<TransitionNode> preds = node.getIncomingNodes();
                if (preds.isEmpty()) {
                    continue;
                }

                TransitionNode newIdom = preds.stream()
                        .filter(p -> context.getImmediateDominators().get(p) != null)
                        .findFirst().orElse(null);
                if (newIdom == null) {
                    continue;
                }

                for (TransitionNode p : preds) {
                    if (!p.equals(newIdom) && context.getImmediateDominators().get(p) != null) {
                        newIdom = intersectDominatorPaths(p, newIdom, context);
                    }
                }
                if (!Objects.equals(context.getImmediateDominators().get(node), newIdom)) {
                    context.getImmediateDominators().put(node, newIdom);
                    changed = true;
                }
            }
        }
    }

    /**
     * Compute immediate post-dominators.
     */
    public void calculatePostDominatorTree(AnalysisContext context) {
        context.getImmediatePostDominators().put(context.getEndNode(), context.getEndNode());
        for (TransitionNode node : context.getAllNodes()) {
            if (!node.equals(context.getEndNode())) {
                context.getImmediatePostDominators().put(node, null);
            }
        }
        List<TransitionNode> reversedTopologicalOrder = topologicalSort(context);
        Collections.reverse(reversedTopologicalOrder);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TransitionNode node : reversedTopologicalOrder) {
                if (node.equals(context.getEndNode())) {
                    continue;
                }
                List<TransitionNode> succs = node.getOutgoingNodes();
                if (succs.isEmpty()) {
                    continue;
                }

                TransitionNode newIdom = succs.stream()
                        .filter(s -> context.getImmediatePostDominators().get(s) != null)
                        .findFirst().orElse(null);
                if (newIdom == null) {
                    continue;
                }

                for (TransitionNode s : succs) {
                    if (!s.equals(newIdom) && context.getImmediatePostDominators().get(s) != null) {
                        newIdom = intersectDominatorPaths(s, newIdom, context);
                    }
                }
                if (!Objects.equals(context.getImmediatePostDominators().get(node), newIdom)) {
                    context.getImmediatePostDominators().put(node, newIdom);
                    changed = true;
                }
            }
        }
    }

    /**
     * Intersect cached dominator paths to find the nearest common ancestor.
     */
    private TransitionNode intersectDominatorPaths(TransitionNode b1, TransitionNode b2,
                                                   AnalysisContext context) {
        boolean isPostDominator = context.getImmediatePostDominators().containsKey(b1) && context.getImmediatePostDominators().get(b1) != null;
        Map<TransitionNode, TransitionNode> dominators = isPostDominator ? context.getImmediatePostDominators() : context.getImmediateDominators();
        TransitionNode root = isPostDominator ? context.getEndNode() : context.getStartNode();
        Map<TransitionNode, List<TransitionNode>> cache = isPostDominator ? context.getPostDominatorPathsCache() : context.getDominatorPathsCache();

        List<TransitionNode> path1 = getPathToRoot(b1, dominators, cache, root);
        List<TransitionNode> path2 = getPathToRoot(b2, dominators, cache, root);
        Set<TransitionNode> path1Set = new HashSet<>(path1);

        for (TransitionNode nodeInPath2 : path2) {
            if (path1Set.contains(nodeInPath2)) {
                return nodeInPath2;
            }
        }
        return root;
    }

    /**
     * Compute and cache the path from a node to the root.
     */
    public List<TransitionNode> getPathToRoot(TransitionNode node,
                                              Map<TransitionNode, TransitionNode> dominators,
                                              Map<TransitionNode, List<TransitionNode>> cache,
                                              TransitionNode root) {
        return cache.computeIfAbsent(node, n -> {
            List<TransitionNode> path = new ArrayList<>();
            TransitionNode currentNode = n;
            while (currentNode != null && !path.contains(currentNode)) {
                path.add(currentNode);
                if (currentNode.equals(root)) {
                    break;
                }
                currentNode = dominators.get(currentNode);
            }
            return path;
        });
    }

    /**
     * Topological sort with cycle detection.
     */
    private List<TransitionNode> topologicalSort(AnalysisContext context) {
        LinkedList<TransitionNode> sorted = new LinkedList<>();
        Set<TransitionNode> visited = new HashSet<>();
        Set<TransitionNode> recursionStack = new HashSet<>();

        if (context.getStartNode() != null) {
            topologicalSortUtil(context.getStartNode(), visited, recursionStack, sorted);
        }
        for (TransitionNode node : context.getAllNodes()) {
            if (!visited.contains(node)) {
                topologicalSortUtil(node, visited, recursionStack, sorted);
            }
        }
        return sorted;
    }

    private void topologicalSortUtil(TransitionNode node, Set<TransitionNode> visited, Set<TransitionNode> recursionStack,
                                     LinkedList<TransitionNode> sorted) {
        visited.add(node);
        recursionStack.add(node);
        List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
        if (outgoingNodes != null) {
            for (TransitionNode successor : outgoingNodes) {
                if (!visited.contains(successor)) {
                    topologicalSortUtil(successor, visited, recursionStack, sorted);
                } else if (recursionStack.contains(successor)) {
                    throw new IllegalStateException("Cycle detected in graph. Node: " + successor.getId());
                }
            }
        }
        recursionStack.remove(node);
        sorted.addFirst(node);
    }

}
