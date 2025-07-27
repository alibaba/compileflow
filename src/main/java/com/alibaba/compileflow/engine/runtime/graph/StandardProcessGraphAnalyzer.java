package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.common.util.ProcessUtils;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 600)
public class StandardProcessGraphAnalyzer implements ProcessGraphAnalyzer {

    private static final DominatorTreeCalculator calculator = new DominatorTreeCalculator();

    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        ProcessGraph processGraph = new ProcessGraph(new HashMap<>(), new HashMap<>());
        analyzeRecursively(nodeContainer, processGraph);
        return processGraph;
    }

    private void analyzeRecursively(NodeContainer<Node> nodeContainer, ProcessGraph processGraph) {
        Map<String, List<TransitionNode>> followingGraph = processGraph.getFollowingGraph();
        Map<String, List<TransitionNode>> branchGraph = processGraph.getBranchGraph();

        List<TransitionNode> nodes = nodeContainer.getAllNodes().stream()
                .filter(n -> n instanceof TransitionNode)
                .map(n -> (TransitionNode) n)
                .collect(Collectors.toList());

        TransitionNode startNode = findUniqueStartNode(nodeContainer);
        TransitionNode endNode = findUniqueEndNode(nodeContainer);
        AnalysisContext context = new AnalysisContext(nodes, startNode, endNode,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        calculator.calculateDominatorTree(context);
        calculator.calculatePostDominatorTree(context);

        for (TransitionNode node : nodes) {
            calculateFollowingNodes(node, followingGraph, context);
        }

        nodes.stream()
                .filter(n -> n instanceof GatewayElement)
                .forEach(gateway -> calculateBranchGraphForGateway(gateway, branchGraph, context));

        nodes.stream()
                .filter(flowNode -> flowNode instanceof NodeContainer)
                .map(NodeContainer.class::cast)
                .forEach(subContainer -> analyzeRecursively(subContainer, processGraph));
    }

    private List<TransitionNode> calculateFollowingNodes(TransitionNode node,
                                                         Map<String, List<TransitionNode>> followingGraph,
                                                         AnalysisContext context) {
        if (followingGraph.containsKey(node.getId())) {
            return followingGraph.get(node.getId());
        }
        List<TransitionNode> path;
        if (node instanceof EndElement || node instanceof WaitElement) {
            path = Collections.emptyList();
        } else if (node instanceof GatewayElement) {
            path = calculateGatewayFollowingNodes(node, context);
        } else {
            path = new ArrayList<>();
            TransitionNode nextNode = getTheOnlyOutgoingNode(node);
            if (nextNode != null) {
                path.add(nextNode);
                path.addAll(calculateFollowingNodes(nextNode, followingGraph, context));
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
            String branchKey = ProcessUtils.buildBranchKey(gateway, branchStartNode);
            branchGraph.put(branchKey, uniquePath);
        }
    }

    private List<TransitionNode> findBranchPath(TransitionNode branchStartNode, TransitionNode joinNode) {
        List<TransitionNode> path = new ArrayList<>();
        TransitionNode currentNode = branchStartNode;
        while (currentNode != null && !currentNode.equals(joinNode)) {
            if (currentNode instanceof EndElement) {
                break;
            }
            path.add(currentNode);
            if (currentNode instanceof GatewayElement || currentNode instanceof WaitElement) {
                break;
            }
            currentNode = getTheOnlyOutgoingNode(currentNode);
        }
        return path;
    }

    private List<TransitionNode> calculateGatewayFollowingNodes(TransitionNode gateway, AnalysisContext context) {
        List<TransitionNode> followingNodes = new ArrayList<>();
        TransitionNode currentNode = gateway;

        while (true) {
            TransitionNode postNode = context.getImmediatePostDominators().get(currentNode);
            if (postNode == null || postNode instanceof EndElement) {
                break;
            }
            if (isParentGatewayFollowingNode(gateway, postNode, context)) {
                break;
            }
            followingNodes.add(postNode);
            if (postNode instanceof GatewayElement || postNode instanceof WaitElement) {
                break;
            }
            currentNode = postNode;
        }
        return followingNodes;
    }

    private boolean isParentGatewayFollowingNode(TransitionNode gateway, TransitionNode postNode, AnalysisContext context) {
        List<TransitionNode> dominatorPath = calculator.getPathToRoot(gateway, context.getImmediateDominators(),
                context.getDominatorPathsCache(), context.getStartNode());

        for (TransitionNode preNode : dominatorPath) {
            if (preNode.equals(gateway) || !(preNode instanceof GatewayElement)) {
                continue;
            }
            if (postNode.equals(context.getImmediatePostDominators().get(preNode))) {
                return true;
            }
        }
        return false;
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
