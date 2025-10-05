package com.alibaba.compileflow.engine.core.builder.generator.analyzer;

import com.alibaba.compileflow.engine.core.definition.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ProcessUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of a process graph analyzer.
 * Builds a {@code ProcessGraph} with two views:
 * - followingGraph: nodes that are guaranteed to follow from a given node;
 * - branchGraph: per-branch paths from gateway nodes.
 *
 * @author yusu
 */
@ExtensionRealization()
public class DefaultProcessGraphAnalyzer implements ProcessGraphAnalyzer {

    /**
     * Build the process graph from a node container.
     */
    @Override
    public ProcessGraph buildProcessGraph(NodeContainer<Node> nodeContainer) {
        ProcessGraph processGraph = new ProcessGraph(new HashMap<>(), new HashMap<>());
        analyzeRecursively(nodeContainer, processGraph);
        return processGraph;
    }

    /**
     * Recursively populate the provided {@code ProcessGraph} from the given container and its subflows.
     */
    private void analyzeRecursively(NodeContainer<Node> nodeContainer, ProcessGraph processGraph) {
        final Map<String, List<TransitionNode>> followingGraph = processGraph.getFollowingGraph();
        final Map<String, List<TransitionNode>> branchGraph = processGraph.getBranchGraph();

        final List<TransitionNode> nodes = nodeContainer.getAllNodes()
                .stream()
                .filter(node -> node instanceof TransitionNode)
                .map(e -> (TransitionNode) e)
                .collect(Collectors.toList());

        // Step 1: compute uncorrected local followingGraph snapshots for each node
        nodes.forEach(node -> this.buildFollowingNodes(node, followingGraph));

        // Step 2: analyze gateways, define branchGraph and correct nested gateways' followingGraph
        final Map<String, List<TransitionNode>> branchGatewayFollowingGraph = new HashMap<>();
        nodes.stream()
                .filter(flowNode -> flowNode instanceof GatewayElement)
                .forEach(gatewayNode -> {
                    // Common following nodes under the parent gateway; used to detect join points and unique paths
                    final Set<TransitionNode> followingNodesSet = new HashSet<>(followingGraph.get(gatewayNode.getId()));

                    List<TransitionNode> outgoingNodes = gatewayNode.getOutgoingNodes();
                    outgoingNodes.forEach(outgoingNode -> {
                        // Build the path used for correction: the branch-unique path until reaching parent common nodes
                        final List<TransitionNode> fullBranchPathForCorrection = calculateFullBranchPath(outgoingNode, followingNodesSet, followingGraph);

                        // Build the truncated path used to define branchGraph (stops at next gateway)
                        final List<TransitionNode> truncatedBranchNodes = buildBranchNodes(gatewayNode, outgoingNode, branchGraph);

                        // Remove parent common nodes to get branch-unique nodes
                        final List<TransitionNode> uniqueBranchNodes = truncatedBranchNodes
                                .stream()
                                .filter(node -> !followingNodesSet.contains(node))
                                .collect(Collectors.toList());

                        // Store branch-unique nodes in branchGraph
                        final String branchKey = ProcessUtils.buildBranchKey(gatewayNode, outgoingNode);
                        branchGraph.put(branchKey, uniqueBranchNodes);

                        // Apply corrections for nested gateways along the correction path
                        fullBranchPathForCorrection.stream()
                                .filter(branchNode -> branchNode instanceof GatewayElement)
                                .forEach(nestedGateway -> {
                                    // Replace nested gateway followingGraph by removing parent common nodes
                                    final List<TransitionNode> gatewayFollowingNodes = followingGraph.get(nestedGateway.getId())
                                            .stream()
                                            .filter(node -> !followingNodesSet.contains(node))
                                            .collect(Collectors.toList());
                                    branchGatewayFollowingGraph.put(nestedGateway.getId(), gatewayFollowingNodes);
                                });
                    });
                });
        // Apply all pending corrections
        followingGraph.putAll(branchGatewayFollowingGraph);

        // Step 3: recurse into subflows
        nodes.stream()
                .filter(flowNode -> flowNode instanceof NodeContainer)
                .map(e -> (NodeContainer) e)
                .forEach(subContainer -> this.analyzeRecursively(subContainer, processGraph));
    }

    /**
     * Compute the branch-unique path for corrections from startNode until any node in stopNodes is reached.
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
                // If gateway, continue with its first followingGraph node
                final List<TransitionNode> nestedFollowing = followingGraph.get(currentNode.getId());
                nextNode = CollectionUtils.isNotEmpty(nestedFollowing) ? nestedFollowing.get(0) : null;
            } else {
                // Otherwise, proceed with the only outgoing node
                nextNode = getTheOnlyOutgoingNode(currentNode);
            }
            currentNode = nextNode;
        }
        return path;
    }

    /**
     * Compute and cache the followingGraph for a node.
     */
    private List<TransitionNode> buildFollowingNodes(final TransitionNode flowNode, final Map<String, List<TransitionNode>> followingGraph) {
        if (followingGraph.containsKey(flowNode.getId())) {
            return followingGraph.get(flowNode.getId());
        }

        List<TransitionNode> followingNodes;
        if (flowNode instanceof EndElement || flowNode instanceof StatefulElement) {
            // End/wait nodes have no following nodes
            followingNodes = Collections.emptyList();
        } else if (flowNode instanceof GatewayElement) {
            // For gateways, following nodes are the common path across branches
            followingNodes = buildGatewayFollowingNodes(flowNode, followingGraph);
        } else {
            // For normal nodes, follow the only outgoing edge
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
     * Return the only outgoing node, or null if there are zero or multiple.
     */
    private TransitionNode getTheOnlyOutgoingNode(final TransitionNode flowNode) {
        List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        if (outgoingNodes.size() == 1) {
            return outgoingNodes.get(0);
        }
        return null;
    }

    /**
     * Compute the common following path across all branches of a gateway node.
     */
    private List<TransitionNode> buildGatewayFollowingNodes(final TransitionNode flowNode, final Map<String, List<TransitionNode>> followingGraph) {
        final List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        if (outgoingNodes.size() < 2) {
            return Collections.emptyList();
        }

        List<TransitionNode> commonFollowingNodes = null;
        for (final TransitionNode branchNode : outgoingNodes) {
            // Full path for the current branch (including start)
            final List<TransitionNode> currentBranchFollowingNodes = new ArrayList<>();
            currentBranchFollowingNodes.add(branchNode);
            currentBranchFollowingNodes.addAll(buildFollowingNodes(branchNode, followingGraph));

            if (commonFollowingNodes == null) {
                // First branch becomes the baseline
                commonFollowingNodes = currentBranchFollowingNodes;
            } else {
                // Trim baseline until the first common node with current branch
                final Set<TransitionNode> currentBranchNodeSet = new HashSet<>(currentBranchFollowingNodes);
                final Iterator<TransitionNode> flowNodeIterator = commonFollowingNodes.iterator();
                while (flowNodeIterator.hasNext()) {
                    final TransitionNode followingNode = flowNodeIterator.next();
                    if (currentBranchNodeSet.contains(followingNode)) {
                        // First common node found
                        break;
                    } else {
                        // Remove non-common nodes until common node is reached
                        flowNodeIterator.remove();
                    }
                }
            }

            // Early-exit when no common path remains
            if (CollectionUtils.isEmpty(commonFollowingNodes)) {
                return Collections.emptyList();
            }
        }

        return commonFollowingNodes == null ? Collections.emptyList() : commonFollowingNodes;
    }

    /**
     * Build the truncated branch path used for branchGraph until next gateway/end.
     */
    private List<TransitionNode> buildBranchNodes(final TransitionNode node, final TransitionNode branchNode, final Map<String, List<TransitionNode>> branchGraph) {
        final String branchKey = ProcessUtils.buildBranchKey(node, branchNode);
        if (branchGraph.containsKey(branchKey)) {
            return branchGraph.get(branchKey);
        }

        final List<TransitionNode> branchNodes = new ArrayList<>();
        branchNodes.add(branchNode);
        // Recurse while not end/wait/gateway
        if (!(branchNode instanceof EndElement) && !(branchNode instanceof StatefulElement) && !(branchNode instanceof GatewayElement)) {
            final TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(branchNode);
            if (theOnlyOutgoingNode != null) {
                branchNodes.addAll(buildBranchNodes(branchNode, theOnlyOutgoingNode, branchGraph));
            }
        }

        branchGraph.put(branchKey, branchNodes);
        return branchNodes;
    }

}
