package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.common.util.ProcessUtils;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
@ExtensionRealization()
public class DefaultProcessGraphAnalyzer implements ProcessGraphAnalyzer {

    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        Map<String, List<TransitionNode>> followingGraph = new HashMap<>();
        Map<String, List<TransitionNode>> branchGraph = new HashMap<>();

        analyzeRecursively(nodeContainer, followingGraph, branchGraph);

        return new ProcessGraph(followingGraph, branchGraph);
    }

    private void analyzeRecursively(NodeContainer<Node> nodeContainer,
                                    Map<String, List<TransitionNode>> followingGraph,
                                    Map<String, List<TransitionNode>> branchGraph) {

        List<TransitionNode> nodes = getAllTransitionNodes(nodeContainer);

        // 步骤一：为所有节点计算followingGraph
        calculateAllFollowingNodes(nodes, followingGraph);

        // 步骤二：计算所有网关的branchGraph，并修正嵌套网关的followingGraph
        calculateAllBranchGraphsAndCorrectPaths(nodes, branchGraph, followingGraph);

        // 步骤三：递归处理子流程
        analyzeSubProcesses(nodes, followingGraph, branchGraph);
    }

    // =================================================================================
    //  阶段一：Following Graph 计算
    // =================================================================================
    private void calculateAllFollowingNodes(List<TransitionNode> nodes,
                                            Map<String, List<TransitionNode>> followingGraph) {
        nodes.forEach(node -> calculateFollowingNodesRecursively(node, followingGraph));
    }

    private List<TransitionNode> calculateFollowingNodesRecursively(TransitionNode node,
                                                                    Map<String, List<TransitionNode>> followingGraphCache) {
        if (followingGraphCache.containsKey(node.getId())) {
            return followingGraphCache.get(node.getId());
        }

        List<TransitionNode> path;
        if (node instanceof EndElement || node instanceof WaitElement) {
            path = Collections.emptyList();
        } else if (node instanceof GatewayElement) {
            path = calculateGatewayFollowingNodes(node, followingGraphCache);
        } else {
            path = new ArrayList<>();
            TransitionNode nextNode = getTheOnlyOutgoingNode(node);
            if (nextNode != null) {
                path.add(nextNode);
                path.addAll(calculateFollowingNodesRecursively(nextNode, followingGraphCache));
            }
        }

        followingGraphCache.put(node.getId(), path);
        return path;
    }

    private List<TransitionNode> calculateGatewayFollowingNodes(TransitionNode gateway,
                                                                Map<String, List<TransitionNode>> followingGraphCache) {
        List<TransitionNode> outgoingNodes = gateway.getOutgoingNodes();
        if (outgoingNodes.size() < 2) {
            return Collections.emptyList();
        }

        List<TransitionNode> commonPath = null;
        for (TransitionNode branchStartNode : outgoingNodes) {
            List<TransitionNode> currentBranchPath = new ArrayList<>();
            currentBranchPath.add(branchStartNode);
            currentBranchPath.addAll(calculateFollowingNodesRecursively(branchStartNode, followingGraphCache));

            if (commonPath == null) {
                commonPath = new ArrayList<>(currentBranchPath);
            } else {
                commonPath = findFirstCommonContinuousPath(commonPath, currentBranchPath);
            }
            if (CollectionUtils.isEmpty(commonPath)) {
                return Collections.emptyList();
            }
        }
        return commonPath == null ? Collections.emptyList() : commonPath;
    }

    /**
     * 寻找两条路径的第一个连续公共部分。
     */
    private List<TransitionNode> findFirstCommonContinuousPath(List<TransitionNode> basePath, List<TransitionNode> otherPath) {
        Set<TransitionNode> otherPathSet = new HashSet<>(otherPath);

        for (int i = 0; i < basePath.size(); i++) {
            TransitionNode candidateNode = basePath.get(i);
            // 核心逻辑：找到第一个共同点
            if (otherPathSet.contains(candidateNode)) {
                // 返回从这个共同点开始的、basePath的剩余部分
                return new ArrayList<>(basePath.subList(i, basePath.size()));
            }
        }

        return Collections.emptyList(); // 没有找到共同点
    }

    // =================================================================================
    //  阶段二：Branch Graph 计算 与 嵌套网关修正
    // =================================================================================
    private void calculateAllBranchGraphsAndCorrectPaths(List<TransitionNode> nodes,
                                                         Map<String, List<TransitionNode>> branchGraph,
                                                         Map<String, List<TransitionNode>> followingGraph) {
        Map<String, List<TransitionNode>> corrections = new HashMap<>();

        nodes.stream()
                .filter(node -> node instanceof GatewayElement)
                .forEach(gatewayNode -> {
                    final Set<TransitionNode> parentFollowingNodesSet = new HashSet<>(followingGraph.get(gatewayNode.getId()));

                    gatewayNode.getOutgoingNodes().forEach(outgoingNode -> {
                        List<TransitionNode> fullBranchPath = calculateBranchPath(outgoingNode);
                        List<TransitionNode> uniqueBranchNodes = fullBranchPath.stream()
                                .filter(node -> !parentFollowingNodesSet.contains(node))
                                .collect(Collectors.toList());

                        String branchKey = ProcessUtils.buildBranchKey(gatewayNode, outgoingNode);
                        branchGraph.put(branchKey, uniqueBranchNodes);

                        uniqueBranchNodes.stream()
                                .filter(branchNode -> branchNode instanceof GatewayElement)
                                .forEach(nestedGateway -> {
                                    List<TransitionNode> correctedPath = followingGraph.get(nestedGateway.getId())
                                            .stream()
                                            .filter(node -> !parentFollowingNodesSet.contains(node))
                                            .collect(Collectors.toList());
                                    corrections.put(nestedGateway.getId(), correctedPath);
                                });
                    });
                });

        followingGraph.putAll(corrections);
    }

    /**
     * 计算并返回分支的完整路径
     */
    private List<TransitionNode> calculateBranchPath(TransitionNode startNode) {
        List<TransitionNode> path = new ArrayList<>();
        if (startNode == null) {
            return path;
        }

        path.add(startNode);
        if (!(startNode instanceof EndElement) && !(startNode instanceof WaitElement) && !(startNode instanceof GatewayElement)) {
            TransitionNode nextNode = getTheOnlyOutgoingNode(startNode);
            if (nextNode != null) {
                path.addAll(calculateBranchPath(nextNode));
            }
        }
        return path;
    }

    // =================================================================================
    //  阶段三：子流程递归分析
    // =================================================================================
    private void analyzeSubProcesses(List<TransitionNode> nodes,
                                     Map<String, List<TransitionNode>> followingGraph,
                                     Map<String, List<TransitionNode>> branchGraph) {
        nodes.stream()
                .filter(node -> node instanceof NodeContainer)
                .map(node -> (NodeContainer<Node>) node)
                .forEach(subContainer -> analyzeRecursively(subContainer, followingGraph, branchGraph));
    }

    private List<TransitionNode> getAllTransitionNodes(NodeContainer<Node> nodeContainer) {
        return nodeContainer.getAllNodes().stream()
                .filter(node -> node instanceof TransitionNode)
                .map(e -> (TransitionNode) e)
                .collect(Collectors.toList());
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode node) {
        if (node.getOutgoingNodes().size() == 1) {
            return node.getOutgoingNodes().get(0);
        }
        return null;
    }

}
