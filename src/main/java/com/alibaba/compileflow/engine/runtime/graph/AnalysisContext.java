package com.alibaba.compileflow.engine.runtime.graph;

import com.alibaba.compileflow.engine.definition.common.TransitionNode;

import java.util.List;
import java.util.Map;

public class AnalysisContext {
    final List<TransitionNode> allNodes;
    final TransitionNode startNode;
    final TransitionNode endNode;
    final Map<TransitionNode, TransitionNode> immediateDominators;
    final Map<TransitionNode, TransitionNode> immediatePostDominators;
    final Map<TransitionNode, List<TransitionNode>> dominatorPathsCache;
    final Map<TransitionNode, List<TransitionNode>> postDominatorPathsCache;

    AnalysisContext(List<TransitionNode> allNodes, TransitionNode startNode, TransitionNode endNode,
                    Map<TransitionNode, TransitionNode> immediateDominators,
                    Map<TransitionNode, TransitionNode> immediatePostDominators,
                    Map<TransitionNode, List<TransitionNode>> dominatorPathsCache,
                    Map<TransitionNode, List<TransitionNode>> postDominatorPathsCache) {
        this.allNodes = allNodes;
        this.startNode = startNode;
        this.endNode = endNode;
        this.immediateDominators = immediateDominators;
        this.immediatePostDominators = immediatePostDominators;
        this.dominatorPathsCache = dominatorPathsCache;
        this.postDominatorPathsCache = postDominatorPathsCache;
    }

    public List<TransitionNode> getAllNodes() {
        return allNodes;
    }

    public TransitionNode getStartNode() {
        return startNode;
    }

    public TransitionNode getEndNode() {
        return endNode;
    }

    public Map<TransitionNode, TransitionNode> getImmediateDominators() {
        return immediateDominators;
    }

    public Map<TransitionNode, TransitionNode> getImmediatePostDominators() {
        return immediatePostDominators;
    }

    public Map<TransitionNode, List<TransitionNode>> getDominatorPathsCache() {
        return dominatorPathsCache;
    }

    public Map<TransitionNode, List<TransitionNode>> getPostDominatorPathsCache() {
        return postDominatorPathsCache;
    }

}
