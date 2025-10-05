package com.alibaba.compileflow.engine.core.builder.generator.analyzer;

import com.alibaba.compileflow.engine.core.definition.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ProcessUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 1000)
public class TopDownCorrectionGraphAnalyzer implements ProcessGraphAnalyzer {

    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        ProcessGraph processGraph = new ProcessGraph(new HashMap<>(), new HashMap<>());
        analyzeRecursively(nodeContainer, processGraph);
        return processGraph;
    }

    private void analyzeRecursively(NodeContainer<Node> nodeContainer, ProcessGraph processGraph) {
        final Map<String, List<TransitionNode>> followingGraph = processGraph.getFollowingGraph();
        final Map<String, List<TransitionNode>> branchGraph = processGraph.getBranchGraph();

        List<TransitionNode> nodes = nodeContainer.getAllNodes().stream()
                .filter(n -> n instanceof TransitionNode).map(n -> (TransitionNode) n).collect(Collectors.toList());

        TransitionNode startNode = findUniqueStartNode(nodeContainer);
        TransitionNode endNode = findUniqueEndNode(nodeContainer);
        AnalysisContext context = new AnalysisContext(nodes, startNode, endNode,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());

        DominatorTreeCalculator calculator = new DominatorTreeCalculator();
        calculator.calculateDominatorTree(context);
        calculator.calculatePostDominatorTree(context);

        // Step 1: compute local followingGraph snapshots
        for (TransitionNode node : nodes) {
            calculateLocalFollowingNodes(node, followingGraph, context);
        }

        // Step 2: correct by dominator order (top-down) and compute branchGraph
        Map<TransitionNode, List<TransitionNode>> dominatorChildren = buildDominatorChildren(context);
        processGatewaysTopDown(startNode, dominatorChildren, followingGraph, branchGraph, context);

        // Recurse into subflows
        nodes.stream()
                .filter(node -> node instanceof NodeContainer)
                .map(node -> (NodeContainer<Node>) node)
                .forEach(subContainer -> analyzeRecursively(subContainer, processGraph));
    }

    private void processGatewaysTopDown(TransitionNode node,
                                        Map<TransitionNode, List<TransitionNode>> dominatorChildren,
                                        Map<String, List<TransitionNode>> followingGraph,
                                        Map<String, List<TransitionNode>> branchGraph,
                                        AnalysisContext context) {
        if (node instanceof GatewayElement) {
            calculateBranchGraphForGateway(node, branchGraph, context);

            final Set<TransitionNode> parentFollowingNodes = new HashSet<>(followingGraph.get(node.getId()));
            if (!parentFollowingNodes.isEmpty()) {
                List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
                for (TransitionNode branchStart : outgoingNodes) {
                    TransitionNode joinNode = context.getImmediatePostDominators().get(node);
                    if (joinNode == null) {
                        joinNode = context.getEndNode();
                    }
                    findNestedGatewaysAndCorrect(branchStart, joinNode, parentFollowingNodes, followingGraph, context);
                }
            }
        }

        dominatorChildren.getOrDefault(node, Collections.emptyList())
                .forEach(child -> processGatewaysTopDown(child, dominatorChildren, followingGraph, branchGraph, context));
    }

    private void findNestedGatewaysAndCorrect(TransitionNode startNode, TransitionNode endNode,
                                              Set<TransitionNode> parentFollowingNodes,
                                              Map<String, List<TransitionNode>> followingGraph,
                                              AnalysisContext context) {
        Set<TransitionNode> visitedNodes = new HashSet<>();
        recursiveCorrection(startNode, endNode, parentFollowingNodes, followingGraph, context, visitedNodes);
    }

    private void recursiveCorrection(TransitionNode currentNode, TransitionNode endNode,
                                     Set<TransitionNode> parentFollowingNodes,
                                     Map<String, List<TransitionNode>> followingGraph,
                                     AnalysisContext context, Set<TransitionNode> visitedNodes) {
        if (currentNode == null || !visitedNodes.add(currentNode)) {
            return;
        }

        if (currentNode instanceof GatewayElement) {
            List<TransitionNode> currentPath = followingGraph.get(currentNode.getId());
            if (currentPath != null) {
                List<TransitionNode> correctedPath = currentPath.stream()
                        .filter(n -> !parentFollowingNodes.contains(n))
                        .collect(Collectors.toList());
                followingGraph.put(currentNode.getId(), correctedPath);
            }
        }

        if (currentNode.equals(endNode) || currentNode instanceof EndElement || currentNode instanceof StatefulElement) {
            return;
        }

        List<TransitionNode> outgoingNodes = currentNode.getOutgoingNodes();
        for (TransitionNode nextNode : outgoingNodes) {
            recursiveCorrection(nextNode, endNode, parentFollowingNodes, followingGraph, context, visitedNodes);
        }
    }

    private Map<TransitionNode, List<TransitionNode>> buildDominatorChildren(AnalysisContext context) {
        Map<TransitionNode, List<TransitionNode>> children = new HashMap<>();
        for (Map.Entry<TransitionNode, TransitionNode> entry : context.getImmediateDominators().entrySet()) {
            TransitionNode child = entry.getKey();
            TransitionNode parent = entry.getValue();
            if (parent != null && !child.equals(parent)) {
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
            }
        }
        return children;
    }

    private List<TransitionNode> calculateLocalFollowingNodes(TransitionNode node,
                                                              Map<String, List<TransitionNode>> followingGraph,
                                                              AnalysisContext context) {
        if (followingGraph.containsKey(node.getId())) {
            return followingGraph.get(node.getId());
        }

        List<TransitionNode> path;
        if (node instanceof EndElement || node instanceof StatefulElement) {
            path = Collections.emptyList();
        } else if (node instanceof GatewayElement) {
            path = new ArrayList<>();
            TransitionNode current = node;
            while (true) {
                TransitionNode postDom = context.getImmediatePostDominators().get(current);
                if (postDom == null || postDom.equals(context.getEndNode()) || postDom instanceof EndElement) {
                    break;
                }
                path.add(postDom);
                if (postDom instanceof GatewayElement || postDom instanceof StatefulElement) {
                    break;
                }
                current = postDom;
            }
        } else {
            path = new ArrayList<>();
            TransitionNode nextNode = getTheOnlyOutgoingNode(node);
            if (nextNode != null) {
                path.add(nextNode);
                path.addAll(calculateLocalFollowingNodes(nextNode, followingGraph, context));
            }
        }

        followingGraph.put(node.getId(), path);
        return path;
    }

    private void calculateBranchGraphForGateway(TransitionNode gateway, Map<String, List<TransitionNode>> branchGraph, AnalysisContext context) {
        TransitionNode joinNode = context.getImmediatePostDominators().get(gateway);
        if (joinNode == null) {
            joinNode = context.getEndNode();
        }

        List<TransitionNode> outgoingNodes = gateway.getOutgoingNodes();
        for (TransitionNode branchStartNode : outgoingNodes) {
            List<TransitionNode> uniquePath = findBranchPath(branchStartNode, joinNode);
            branchGraph.computeIfAbsent(ProcessUtils.buildBranchKey(gateway, branchStartNode), k -> uniquePath);
        }
    }

    private List<TransitionNode> findBranchPath(TransitionNode branchStartNode, TransitionNode joinNode) {
        List<TransitionNode> path = new ArrayList<>();
        TransitionNode currentNode = branchStartNode;
        while (currentNode != null && !currentNode.equals(joinNode)) {
            path.add(currentNode);
            if (currentNode instanceof EndElement || currentNode instanceof StatefulElement || currentNode instanceof GatewayElement) {
                break;
            }
            currentNode = getTheOnlyOutgoingNode(currentNode);
        }
        return path;
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode flowNode) {
        List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        if (outgoingNodes != null && outgoingNodes.size() == 1) {
            return outgoingNodes.get(0);
        }
        return null;
    }

    private TransitionNode findUniqueStartNode(NodeContainer<Node> nodeContainer) {
        return (TransitionNode) nodeContainer.getStartNode();
    }

    private TransitionNode findUniqueEndNode(NodeContainer<Node> nodeContainer) {
        return (TransitionNode) nodeContainer.getEndNode();
    }

}
