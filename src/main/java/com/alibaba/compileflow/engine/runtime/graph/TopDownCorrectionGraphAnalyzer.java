package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.common.util.ProcessUtils;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;

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

        // 第一步：计算所有节点的“局部”followingGraph（原始快照）
        for (TransitionNode node : nodes) {
            calculateLocalFollowingNodes(node, followingGraph, context);
        }

        // 第二步：严格按支配顺序（自上而下）进行修正并计算branchGraph
        Map<TransitionNode, List<TransitionNode>> dominatorChildren = buildDominatorChildren(context);
        processGatewaysTopDown(startNode, dominatorChildren, followingGraph, branchGraph, context);

        // 递归处理子流程
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
                for (TransitionNode branchStart : node.getOutgoingNodes()) {
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

        if (currentNode.equals(endNode) || currentNode instanceof EndElement || currentNode instanceof WaitElement) {
            return;
        }

        for (TransitionNode nextNode : currentNode.getOutgoingNodes()) {
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
        if (node instanceof EndElement || node instanceof WaitElement) {
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
                if (postDom instanceof GatewayElement || postDom instanceof WaitElement) {
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

        for (TransitionNode branchStartNode : gateway.getOutgoingNodes()) {
            List<TransitionNode> uniquePath = findBranchPath(branchStartNode, joinNode);
            branchGraph.computeIfAbsent(ProcessUtils.buildBranchKey(gateway, branchStartNode), k -> uniquePath);
        }
    }

    private List<TransitionNode> findBranchPath(TransitionNode branchStartNode, TransitionNode joinNode) {
        List<TransitionNode> path = new ArrayList<>();
        TransitionNode currentNode = branchStartNode;
        while (currentNode != null && !currentNode.equals(joinNode)) {
            path.add(currentNode);
            if (currentNode instanceof EndElement || currentNode instanceof WaitElement || currentNode instanceof GatewayElement) {
                break;
            }
            currentNode = getTheOnlyOutgoingNode(currentNode);
        }
        return path;
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode flowNode) {
        if (flowNode.getOutgoingNodes() != null && flowNode.getOutgoingNodes().size() == 1) {
            return flowNode.getOutgoingNodes().get(0);
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
