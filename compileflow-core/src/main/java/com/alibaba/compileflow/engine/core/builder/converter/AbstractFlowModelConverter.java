package com.alibaba.compileflow.engine.core.builder.converter;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.builder.converter.loader.FlowSourceLoader;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.core.definition.EndElement;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.definition.Transition;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.infrastructure.DirectedGraph;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public abstract class AbstractFlowModelConverter<T extends FlowModel<? extends TransitionNode>> implements FlowModelConverter<T> {

    @Override
    public T convertToModel(ProcessSource processSource) {
        FlowStreamSource flowStreamSource = ExtensionInvoker.getInstance().invokeFirst(FlowSourceLoader.EXT_LOAD_FLOW_SOURCE_CODE,
                (FlowSourceLoader e) -> e.loadFlowSource(processSource, getFlowModelType()));

        T flowModel = convertToModel(flowStreamSource);
        if (flowModel == null) {
            throw new RuntimeException("No valid flow model found, code is " + processSource.getCode());
        }

        validateFlowModel(flowModel);
        sortTransition(flowModel);

        return flowModel;
    }

    protected abstract FlowModelType getFlowModelType();

    protected abstract T convertToModel(FlowStreamSource flowStreamSource);

    protected void validateFlowModel(T flowModel) {
        checkCycle(flowModel);
        checkContinuous(flowModel);
    }

    private void checkContinuous(T flowModel) {
        Set<TransitionNode> visitedNodes = new HashSet<>();
        checkContinuous(flowModel.getStartNode(), visitedNodes, flowModel);
    }

    private void checkContinuous(TransitionNode node, Set<TransitionNode> visitedNodes, FlowModel flowModel) {
        visitedNodes.add(node);
        if (node instanceof EndElement) {
            return;
        }
        List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
        if (CollectionUtils.isEmpty(outgoingNodes)) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_002,
                    "Flow should end with an end node " + flowModel,
                    null
            );
        }

        for (TransitionNode outgoingNode : outgoingNodes) {
            if (!visitedNodes.contains(outgoingNode)) {
                checkContinuous(outgoingNode, visitedNodes, flowModel);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkCycle(T flowModel) {
        DirectedGraph<TransitionNode> directedGraph = buildDirectedGraph(flowModel);
        List<TransitionNode> cyclicVertexList = directedGraph.findCyclicVertexList();
        if (CollectionUtils.isNotEmpty(cyclicVertexList)) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_002,
                    "Cyclic nodes found in flow " + flowModel.getCode()
                            + " check node [" + cyclicVertexList.stream().map(TransitionNode::getId)
                            .collect(Collectors.joining(",")) + "]",
                    null
            );
        }
    }

    private DirectedGraph<TransitionNode> buildDirectedGraph(T flowModel) {
        DirectedGraph<TransitionNode> directedGraph = new DirectedGraph<>();
        for (TransitionNode node : flowModel.getAllNodes()) {
            List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
            if (CollectionUtils.isNotEmpty(outgoingNodes)) {
                outgoingNodes.forEach(outgoingNode -> directedGraph.add(DirectedGraph.Edge.of(node, outgoingNode)));
            }
        }
        return directedGraph;
    }

    private void sortTransition(T flowModel) {
        flowModel.getAllNodes().forEach(node -> node.getOutgoingTransitions()
                .sort(Comparator.comparing(Transition::getPriority).reversed()));
    }

}
