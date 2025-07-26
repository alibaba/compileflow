package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.definition.common.TransitionNode;

import java.util.List;
import java.util.Map;

/**
 * @author yusu
 */
public class ProcessGraph {

    private final Map<String, List<TransitionNode>> followingGraph;
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
