package com.alibaba.compileflow.engine.core.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.analyzer.ProcessGraph;
import com.alibaba.compileflow.engine.core.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import com.alibaba.compileflow.engine.core.definition.var.IVar;

import java.util.List;
import java.util.Map;

/**
 * @author yusu
 */
public class GeneratorContext {

    private final FlowModel flowModel;

    private final ProcessGraph processGraph;

    private final NodeGeneratorProvider nodeGeneratorProvider;

    public GeneratorContext(FlowModel flowModel, ProcessGraph processGraph, NodeGeneratorProvider nodeGeneratorProvider) {
        this.flowModel = flowModel;
        this.processGraph = processGraph;
        this.nodeGeneratorProvider = nodeGeneratorProvider;
    }

    public FlowModel getFlowModel() {
        return flowModel;
    }

    public ProcessGraph getProcessGraph() {
        return processGraph;
    }

    public String getFlowId() {
        return flowModel.getId();
    }

    public String getFlowCode() {
        return flowModel.getCode();
    }

    public Map<String, List<TransitionNode>> getFollowingGraph() {
        return processGraph.getFollowingGraph();
    }

    public Map<String, List<TransitionNode>> getBranchGraph() {
        return processGraph.getBranchGraph();
    }

    public List<IVar> getVars() {
        return flowModel.getVars();
    }

    public List<IVar> getParamVars() {
        return flowModel.getParamVars();
    }

    public List<IVar> getInnerVars() {
        return flowModel.getInnerVars();
    }

    public List<IVar> getReturnVars() {
        return flowModel.getReturnVars();
    }

    public Node getNodeById(String id) {
        return flowModel.getNode(id);
    }

    public CodeGenerator getGenerator(Node node) {
        return nodeGeneratorProvider.getGenerator(node);
    }

}
