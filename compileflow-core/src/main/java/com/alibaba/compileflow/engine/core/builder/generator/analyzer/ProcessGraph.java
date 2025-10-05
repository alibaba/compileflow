package com.alibaba.compileflow.engine.core.builder.generator.analyzer;

import com.alibaba.compileflow.engine.core.definition.TransitionNode;

import java.util.List;
import java.util.Map;

/**
 * Results of process graph analysis.
 * <p>
 * Provides following and branch views for code generation.
 *
 * @author yusu
 */
public class ProcessGraph {

    /**
     * Map of node id to nodes that follow until the next merge.
     */
    private final Map<String, List<TransitionNode>> followingGraph;

    /**
     * Map of branch key (gatewayId:branchStartId) to nodes within that branch.
     */
    private final Map<String, List<TransitionNode>> branchGraph;

    public ProcessGraph(Map<String, List<TransitionNode>> followingGraph,
                        Map<String, List<TransitionNode>> branchGraph) {
        this.followingGraph = followingGraph;
        this.branchGraph = branchGraph;
    }

    public Map<String, List<TransitionNode>> getFollowingGraph() {
        return followingGraph;
    }

    public Map<String, List<TransitionNode>> getBranchGraph() {
        return branchGraph;
    }

}
