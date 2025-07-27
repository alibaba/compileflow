package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.common.util.ProcessUtils;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程图（Process Graph）分析器默认实现。
 * 主要职责是分析流程定义中的所有节点和转换关系，构建一个结构化的图对象（ProcessGraph）。
 * 这个图对象将流程路径分为两部分：
 * 1. followingGraph: 描述从一个节点出发，后续共通的、必然会经过的节点序列。
 * 2. branchGraph: 描述从一个网关（Gateway）的不同分支出去后，各自独有的节点路径。
 *
 * @author yusu
 */
@ExtensionRealization()
public class DefaultProcessGraphAnalyzer implements ProcessGraphAnalyzer {

    /**
     * 构建流程图的公共入口方法。
     * 它创建一个ProcessGraph对象，然后调用递归辅助方法来完成所有分析和填充工作。
     */
    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        ProcessGraph processGraph = new ProcessGraph(new HashMap<>(), new HashMap<>());
        analyzeRecursively(nodeContainer, processGraph);
        return processGraph;
    }

    /**
     * 递归分析的核心方法。它接收一个ProcessGraph实例，确保所有分析结果（包括子流程）都聚合到该实例中。
     * @param nodeContainer 当前要分析的节点容器（主流程或子流程）
     * @param processGraph  用于聚合所有分析结果的图对象
     */
    private void analyzeRecursively(NodeContainer<Node> nodeContainer, ProcessGraph processGraph) {
        final Map<String, List<TransitionNode>> followingGraph = processGraph.getFollowingGraph();
        final Map<String, List<TransitionNode>> branchGraph = processGraph.getBranchGraph();

        final List<TransitionNode> nodes = nodeContainer.getAllNodes()
                .stream()
                .filter(node -> node instanceof TransitionNode)
                .map(e -> (TransitionNode) e)
                .collect(Collectors.toList());

        // 步骤一: 全局计算快照
        // 为图中每一个节点计算其未经修正的、局部的followingGraph。
        // 这个过程是递归的，会从流程末端向上计算，结果被缓存，为后续的修正提供一个完整的原始数据视图。
        nodes.forEach(node -> this.buildFollowingNodes(node, followingGraph));

        // 步骤二: 分析网关，定义分支并修正嵌套网关的followingGraph
        final Map<String, List<TransitionNode>> branchGatewayFollowingGraph = new HashMap<>();
        nodes.stream()
                .filter(flowNode -> flowNode instanceof GatewayElement)
                .forEach(gatewayNode -> {
                    // 获取当前父网关的公共后续节点集合，这是判断分支是否汇合、路径是否独有的依据。
                    final Set<TransitionNode> followingNodesSet = new HashSet<>(followingGraph.get(gatewayNode.getId()));

                    gatewayNode.getOutgoingNodes().forEach(outgoingNode -> {
                        // **计算用于修正的路径**
                        // 这个路径是分支的独有路径，它会探索到遇见父网关的公共节点（stopNodes）为止。
                        // 它被用来准确地寻找所有需要被当前父网关“裁剪”的嵌套网关。
                        final List<TransitionNode> fullBranchPathForCorrection = calculateFullBranchPath(outgoingNode, followingNodesSet, followingGraph);

                        // **计算用于定义分支的路径**
                        // 这个路径在遇到任何类型的网关时都会被截断，它的职责是定义branchGraph的内容。
                        final List<TransitionNode> truncatedBranchNodes = buildBranchNodes(gatewayNode, outgoingNode, branchGraph);

                        // 从截断路径中，过滤掉父网关的公共节点，得到纯粹的分支独有节点。
                        final List<TransitionNode> uniqueBranchNodes = truncatedBranchNodes
                                .stream()
                                .filter(node -> !followingNodesSet.contains(node))
                                .collect(Collectors.toList());

                        // 将分支独有节点存入branchGraph
                        final String branchKey = ProcessUtils.buildBranchKey(gatewayNode, outgoingNode);
                        branchGraph.put(branchKey, uniqueBranchNodes);

                        // **执行修正**
                        // 遍历修正路径，找到所有嵌套的网关。
                        fullBranchPathForCorrection.stream()
                                .filter(branchNode -> branchNode instanceof GatewayElement)
                                .forEach(nestedGateway -> {
                                    // 计算嵌套网关修正后的followingGraph：即从其原始followingGraph中，移除父网关的公共部分。
                                    final List<TransitionNode> gatewayFollowingNodes = followingGraph.get(nestedGateway.getId())
                                            .stream()
                                            .filter(node -> !followingNodesSet.contains(node))
                                            .collect(Collectors.toList());
                                    // 将修正结果暂存到临时Map中
                                    branchGatewayFollowingGraph.put(nestedGateway.getId(), gatewayFollowingNodes);
                                });
                    });
                });
        // 将所有暂存的修正一次性应用到主followingGraph中。
        followingGraph.putAll(branchGatewayFollowingGraph);

        // 步骤三: 递归处理子流程
        nodes.stream()
                .filter(flowNode -> flowNode instanceof NodeContainer)
                .map(e -> (NodeContainer) e)
                .forEach(subContainer -> this.analyzeRecursively(subContainer, processGraph));
    }

    /**
     * 计算一个分支的“独有”路径，用于寻找所有需要被修正的嵌套网关。
     * 路径的遍历会从startNode开始，直到遇到stopNodes集合中的任何一个节点（即公共汇合点）为止。
     * 特别地，当遇到嵌套的网关时，它会沿着该网关的第一个后续节点继续探索。
     *
     * @param startNode      分支的起始节点
     * @param stopNodes      公共节点集合，作为路径遍历的停止条件
     * @param followingGraph 用于查询嵌套网关的后续节点
     * @return 该分支的独有节点列表（包含路径上的嵌套网关及其后续节点）
     */
    private List<TransitionNode> calculateFullBranchPath(final TransitionNode startNode,
                                                         final Set<TransitionNode> stopNodes,
                                                         final Map<String, List<TransitionNode>> followingGraph) {
        final List<TransitionNode> path = new ArrayList<>();
        TransitionNode currentNode = startNode;

        while (currentNode != null && !stopNodes.contains(currentNode)) {
            path.add(currentNode);

            TransitionNode nextNode;
            if (currentNode instanceof GatewayElement) {
                // 如果是网关，则从followingGraph中获取其第一个后续节点作为路径的下一步
                final List<TransitionNode> nestedFollowing = followingGraph.get(currentNode.getId());
                nextNode = CollectionUtils.isNotEmpty(nestedFollowing) ? nestedFollowing.get(0) : null;
            } else {
                // 如果是普通节点，则获取其唯一的出向节点
                nextNode = getTheOnlyOutgoingNode(currentNode);
            }
            currentNode = nextNode;
        }
        return path;
    }

    /**
     * 计算一个节点的公共后续节点列表（following-graph）。
     * 这是一个带缓存（记忆化）的递归函数。
     * @param flowNode       要计算的节点
     * @param followingGraph 用于缓存和查询结果的Map
     * @return 节点的公共后续节点列表
     */
    private List<TransitionNode> buildFollowingNodes(final TransitionNode flowNode, final Map<String, List<TransitionNode>> followingGraph) {
        if (followingGraph.containsKey(flowNode.getId())) {
            return followingGraph.get(flowNode.getId());
        }

        List<TransitionNode> followingNodes;
        if (flowNode instanceof EndElement || flowNode instanceof WaitElement) {
            // 结束节点或等待节点没有后续
            followingNodes = Collections.emptyList();
        } else if (flowNode instanceof GatewayElement) {
            // 网关节点的后续是其所有分支的公共路径
            followingNodes = buildGatewayFollowingNodes(flowNode, followingGraph);
        } else {
            // 普通节点的后续是其唯一出口指向的路径
            followingNodes = new ArrayList<>();
            final TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(flowNode);
            if (theOnlyOutgoingNode != null) {
                followingNodes.add(theOnlyOutgoingNode);
                followingNodes.addAll(buildFollowingNodes(theOnlyOutgoingNode, followingGraph));
            }
        }

        followingGraph.put(flowNode.getId(), followingNodes);
        return followingNodes;
    }

    /**
     * 获取一个节点的唯一出向节点，如果出向超过一个或为零，则返回null。
     */
    private TransitionNode getTheOnlyOutgoingNode(final TransitionNode flowNode) {
        if (flowNode.getOutgoingNodes().size() == 1) {
            return flowNode.getOutgoingNodes().get(0);
        }
        return null;
    }

    /**
     * 计算网关节点所有分支的公共后续路径。
     * @param flowNode       必须是一个网关节点
     * @param followingGraph 用于递归查询分支的后续路径
     * @return 公共后续路径列表
     */
    private List<TransitionNode> buildGatewayFollowingNodes(final TransitionNode flowNode, final Map<String, List<TransitionNode>> followingGraph) {
        final List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        if (outgoingNodes.size() < 2) {
            return Collections.emptyList();
        }

        List<TransitionNode> commonFollowingNodes = null;
        for (final TransitionNode branchNode : outgoingNodes) {
            // 计算当前分支的完整路径（包含分支起点）
            final List<TransitionNode> currentBranchFollowingNodes = new ArrayList<>();
            currentBranchFollowingNodes.add(branchNode);
            currentBranchFollowingNodes.addAll(buildFollowingNodes(branchNode, followingGraph));

            if (commonFollowingNodes == null) {
                // 第一条分支的路径作为比较的基准
                commonFollowingNodes = currentBranchFollowingNodes;
            } else {
                // 用后续分支的路径来“裁剪”基准路径，找到第一个共同节点
                final Set<TransitionNode> currentBranchNodeSet = new HashSet<>(currentBranchFollowingNodes);
                final Iterator<TransitionNode> flowNodeIterator = commonFollowingNodes.iterator();
                while (flowNodeIterator.hasNext()) {
                    final TransitionNode followingNode = flowNodeIterator.next();
                    if (currentBranchNodeSet.contains(followingNode)) {
                        // 找到了第一个共同节点，停止裁剪
                        break;
                    } else {
                        // 在找到共同节点之前，所有不共同的节点都从基准路径中移除
                        flowNodeIterator.remove();
                    }
                }
            }

            // 如果在任何一步裁剪后，公共路径变为空，则说明没有公共路径，提前返回
            if (CollectionUtils.isEmpty(commonFollowingNodes)) {
                return Collections.emptyList();
            }
        }

        return commonFollowingNodes == null ? Collections.emptyList() : commonFollowingNodes;
    }

    /**
     * 计算一个分支被下一个网关或结束节点“截断”前的路径。
     * 此方法的结果用于定义branchGraph的内容。这是一个带缓存的递归函数。
     *
     * @param node           父网关节点
     * @param branchNode     分支的起始节点
     * @param branchGraph    用于缓存和查询结果的Map
     * @return 分支的截断路径
     */
    private List<TransitionNode> buildBranchNodes(final TransitionNode node, final TransitionNode branchNode, final Map<String, List<TransitionNode>> branchGraph) {
        final String branchKey = ProcessUtils.buildBranchKey(node, branchNode);
        if (branchGraph.containsKey(branchKey)) {
            return branchGraph.get(branchKey);
        }

        final List<TransitionNode> branchNodes = new ArrayList<>();
        branchNodes.add(branchNode);
        // 如果分支节点不是结束、等待或网关节点，则继续递归
        if (!(branchNode instanceof EndElement) && !(branchNode instanceof WaitElement) && !(branchNode instanceof GatewayElement)) {
            final TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(branchNode);
            if (theOnlyOutgoingNode != null) {
                branchNodes.addAll(buildBranchNodes(branchNode, theOnlyOutgoingNode, branchGraph));
            }
        }

        branchGraph.put(branchKey, branchNodes);
        return branchNodes;
    }

}
