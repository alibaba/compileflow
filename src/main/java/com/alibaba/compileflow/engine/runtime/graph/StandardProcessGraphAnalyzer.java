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

    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        final Map<TransitionNode, TransitionNode> immediateDominators = new HashMap<>();
        final Map<TransitionNode, TransitionNode> immediatePostDominators = new HashMap<>();
        TransitionNode startNode = findUniqueStartNode(nodeContainer);
        TransitionNode endNode = findUniqueEndNode(nodeContainer);

        List<TransitionNode> nodes = nodeContainer.getAllNodes().stream()
                .filter(n -> n instanceof TransitionNode)
                .map(n -> (TransitionNode) n)
                .collect(Collectors.toList());
        
        AnalysisContext context = new AnalysisContext(nodes, startNode, endNode, immediateDominators, immediatePostDominators);
        calculateDominatorTree(context);
        calculatePostDominatorTree(context);

        Map<String, List<TransitionNode>> followingGraph = new HashMap<>();
        Map<String, List<TransitionNode>> branchGraph = new HashMap<>();

        // 为所有节点计算followingGraph
        for (TransitionNode node : nodes) {
            calculateFollowingNodes(node, followingGraph, context);
        }

        // 只为网关计算branchGraph
        List<TransitionNode> gateways = nodes.stream()
                .filter(n -> n instanceof GatewayElement)
                .collect(Collectors.toList());
        for (TransitionNode gateway : gateways) {
            calculateBranchGraphForGateway(gateway, branchGraph, context);
        }


        nodes.stream()
                .filter(flowNode -> flowNode instanceof NodeContainer)
                .map(e -> (NodeContainer) e)
                .forEach(this::buildProcessGraph);

        return new ProcessGraph(followingGraph, branchGraph);
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
            // 对于普通节点，其后续路径就是其唯一出口的后续路径
            path = new ArrayList<>();
            TransitionNode nextNode = getTheOnlyOutgoingNode(node);
            if (nextNode != null) {
                path.add(nextNode);
                // 递归获取路径的剩余部分
                path.addAll(calculateFollowingNodes(nextNode, followingGraph, context));
            }
        }

        followingGraph.put(node.getId(), path);
        return path;
    }

    private void calculateBranchGraphForGateway(TransitionNode gateway, Map<String, List<TransitionNode>> branchGraph, AnalysisContext context) {
        TransitionNode joinNode = context.immediatePostDominators.get(gateway);
        if (joinNode == null) {
            joinNode = context.endNode;
        }

        for (TransitionNode branchStartNode : gateway.getOutgoingNodes()) {
            List<TransitionNode> uniquePath = findBranchPath(branchStartNode, joinNode, context);
            String branchKey = ProcessUtils.buildBranchKey(gateway, branchStartNode);
            branchGraph.put(branchKey, uniquePath);
        }
    }

    private List<TransitionNode> findBranchPath(TransitionNode branchStartNode, TransitionNode joinNode, AnalysisContext context) {
        List<TransitionNode> path = new ArrayList<>();
        TransitionNode currentNode = branchStartNode;
        while (!currentNode.equals(joinNode)) {
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

    /**
     * 专门为网关计算其共同后续路径。
     */
    private List<TransitionNode> calculateGatewayFollowingNodes(TransitionNode gateway, AnalysisContext context) {
        TransitionNode currentNode = gateway;
        List<TransitionNode> followingNodes = new ArrayList<>();
        int safetyCounter = 0;
        // 使用安全计数器防止在异常图中无限循环
        while (safetyCounter++ < context.allNodes.size()) {
            TransitionNode postNode = context.immediatePostDominators.get(currentNode);
            boolean isParentGatewayFollowingNode = isParentGatewayFollowingNode(gateway, postNode, context);
            if (isParentGatewayFollowingNode || postNode == null || postNode instanceof EndElement) {
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
        TransitionNode currentNode = gateway;
        int safetyCounter = 0;
        // 使用安全计数器防止在异常图中无限循环
        while (safetyCounter++ < context.allNodes.size()) {
            TransitionNode preNode = context.immediateDominators.get(currentNode);
            if (preNode == null || preNode.equals(context.startNode)) {
                return false;
            }
            if (preNode instanceof GatewayElement) {
                if (postNode.equals(context.immediatePostDominators.get(preNode))) {
                    return true;
                }
            }
            currentNode = preNode;
        }
        return false;
    }

    private void calculateDominatorTree(AnalysisContext context) {
        context.immediateDominators.put(context.startNode, context.startNode);
        for (TransitionNode node : context.allNodes) {
            if (!node.equals(context.startNode)) {
                context.immediateDominators.put(node, null);
            }
        }

        // 前支配树的计算，使用正向的拓扑排序
        List<TransitionNode> topologicalOrder = topologicalSort(context);

        boolean changed = true;
        while (changed) {
            changed = false;
            // 使用正向拓扑顺序进行迭代
            for (TransitionNode node : topologicalOrder) {
                if (node.equals(context.startNode)) {
                    continue;
                }

                // 核心：一个节点的直接支配点，是其所有【前驱节点】的支配点的“最近公共祖先”。
                List<TransitionNode> preds = node.getIncomingNodes();
                if (preds.isEmpty()) {
                    continue;
                }

                TransitionNode newIdom = null;
                for (TransitionNode p : preds) {
                    if (context.immediateDominators.get(p) != null) {
                        newIdom = p;
                        break;
                    }
                }
                if (newIdom == null) {
                    continue;
                }

                for (TransitionNode p : preds) {
                    if (!p.equals(newIdom) && context.immediateDominators.get(p) != null) {
                        newIdom = intersectDominatorPaths(p, newIdom, context.immediateDominators, context.startNode);
                    }
                }

                if (!Objects.equals(context.immediateDominators.get(node), newIdom)) {
                    context.immediateDominators.put(node, newIdom);
                    changed = true;
                }
            }
        }
    }

    /**
     * 计算后支配树，使用逆向拓扑排序提升性能。
     */
    private void calculatePostDominatorTree(AnalysisContext context) {
        context.immediatePostDominators.put(context.endNode, context.endNode);
        for (TransitionNode node : context.allNodes) {
            if (!node.equals(context.endNode)) {
                context.immediatePostDominators.put(node, null);
            }
        }

        List<TransitionNode> reversedTopologicalOrder = topologicalSort(context);
        Collections.reverse(reversedTopologicalOrder);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (TransitionNode node : reversedTopologicalOrder) {
                if (node.equals(context.endNode)) {
                    continue;
                }

                List<TransitionNode> succs = node.getOutgoingNodes();
                if (succs.isEmpty()) {
                    continue;
                }

                TransitionNode newIdom = null;
                // 找到第一个已经计算出支配点的后继，作为交集的初始值
                for (TransitionNode s : succs) {
                    if (context.immediatePostDominators.get(s) != null) {
                        newIdom = s;
                        break;
                    }
                }
                if (newIdom == null) {
                    continue;
                }

                // 与其他所有后继的支配点路径求交集
                for (TransitionNode s : succs) {
                    if (!s.equals(newIdom) && context.immediatePostDominators.get(s) != null) {
                        newIdom = intersectDominatorPaths(s, newIdom, context.immediatePostDominators, context.endNode);
                    }
                }

                if (!Objects.equals(context.immediatePostDominators.get(node), newIdom)) {
                    context.immediatePostDominators.put(node, newIdom);
                    changed = true;
                }
            }
        }
    }

    /**
     * 确保在任何情况下都返回一个有效的节点。
     */
    private TransitionNode intersectDominatorPaths(TransitionNode b1, TransitionNode b2,
                                                   Map<TransitionNode, TransitionNode> dominators,
                                                   TransitionNode root) {
        Set<TransitionNode> path1 = new HashSet<>();
        while (b1 != null && !b1.equals(root)) {
            path1.add(b1);
            b1 = dominators.get(b1);
        }
        if (b1 != null) {
            path1.add(b1);
        }

        while (b2 != null && !b2.equals(root)) {
            if (path1.contains(b2)) {
                return b2;
            }
            b2 = dominators.get(b2);
        }
        if (b2 != null && path1.contains(b2)) {
            return b2;
        }

        return root;
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode flowNode) {
        if (flowNode.getOutgoingNodes().size() == 1) {
            return flowNode.getOutgoingNodes().get(0);
        }
        return null;
    }

    /**
     * 拓扑排序
     */
    private List<TransitionNode> topologicalSort(AnalysisContext context) {
        LinkedList<TransitionNode> sorted = new LinkedList<>();
        Set<TransitionNode> visited = new HashSet<>();
        Set<TransitionNode> recursionStack = new HashSet<>();

        for (TransitionNode node : context.allNodes) {
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

        for (TransitionNode successor : node.getOutgoingNodes()) {
            if (!visited.contains(successor)) {
                topologicalSortUtil(successor, visited, recursionStack, sorted);
            } else if (recursionStack.contains(successor)) {
                throw new IllegalStateException("Cycle detected in the graph involving node " + successor.getId()
                        + ". A valid process graph must be a Directed Acyclic Graph (DAG).");
            }
        }
        recursionStack.remove(node);
        sorted.addFirst(node);
    }

    private TransitionNode findUniqueStartNode(NodeContainer<Node> nodeContainer) {
        return (TransitionNode) nodeContainer.getStartNode();
    }

    private TransitionNode findUniqueEndNode(NodeContainer<Node> nodeContainer) {
        return (TransitionNode) nodeContainer.getEndNode();
    }

    private static class AnalysisContext {
        private List<TransitionNode> allNodes;
        private TransitionNode startNode;
        private TransitionNode endNode;
        private Map<TransitionNode, TransitionNode> immediateDominators;
        private Map<TransitionNode, TransitionNode> immediatePostDominators;

        public AnalysisContext(List<TransitionNode> allNodes, TransitionNode startNode, TransitionNode endNode,
                               Map<TransitionNode, TransitionNode> immediateDominators,
                               Map<TransitionNode, TransitionNode> immediatePostDominators) {
            this.allNodes = allNodes;
            this.startNode = startNode;
            this.endNode = endNode;
            this.immediateDominators = immediateDominators;
            this.immediatePostDominators = immediatePostDominators;
        }
    }

}
