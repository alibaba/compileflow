/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.source;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.model.AbruptCompletionElement;
import com.alibaba.compileflow.engine.core.model.EndElement;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.Node;
import com.alibaba.compileflow.engine.core.model.NodeContainer;
import com.alibaba.compileflow.engine.core.model.TransitionNode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Base {@link FlowModelReader} shared by model formats.
 *
 * @author yusu
 */
public abstract class AbstractFlowModelReader<T extends FlowModel<? extends TransitionNode<?>>>
        implements FlowModelReader<T> {
    @Override
    public T read(ProcessDefinitionSnapshot definition) {
        ProcessDefinitionSnapshot snapshot = Objects.requireNonNull(definition, "definition");
        FlowSource source = FlowSource.of(snapshot.getCode(), snapshot.getBytes());

        T flowModel = read(source);
        if (flowModel == null) {
            throw new CompileFlowException(ErrorCode.CF_COMPILE_001,
                    "Flow model parser returned null: code=" + snapshot.getCode() + ", modelType=" + getFlowModelType(),
                    null);
        }
        if (!snapshot.getCode().equals(flowModel.getCode())) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Process definition code does not match the code declared by the model: definition="
                    + snapshot.getCode() + ", model=" + flowModel.getCode(), null);
        }

        validateFlowModel(flowModel);
        return flowModel;
    }

    protected abstract ProcessModelType getFlowModelType();

    protected abstract T read(FlowSource source);

    protected void validateFlowModel(T flowModel) {
        Set<NodeContainer<?>> visitedContainers = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<NodeContainer<?>> pendingContainers = new ArrayDeque<>();
        pendingContainers.push(flowModel);

        while (!pendingContainers.isEmpty()) {
            NodeContainer<?> container = pendingContainers.pop();
            if (!visitedContainers.add(container)) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "Cyclic or multiply-owned node container found in flow " + flowModel.getCode(), null);
            }
            checkCycle(container, flowModel);
            checkContinuous(container, flowModel);

            List<? extends Node> nodes = container.getAllNodes();
            for (int i = nodes.size() - 1; i >= 0; i--) {
                Node node = nodes.get(i);
                if (node instanceof NodeContainer<?> childContainer) {
                    pendingContainers.push(childContainer);
                }
            }
        }
    }

    private void checkContinuous(NodeContainer<?> container, T flowModel) {
        Node start = container.getStartNode();
        Node end = container.getEndNode();
        if (!(start instanceof TransitionNode<?> startNode) || !(end instanceof TransitionNode<?> endNode)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Flow container boundaries must be transition nodes, container=" + container.getId() + ", flow="
                    + flowModel.getCode(), null);
        }
        Set<TransitionNode<?>> visitedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<TransitionNode<?>> pendingNodes = new ArrayDeque<>();
        pendingNodes.push(startNode);

        while (!pendingNodes.isEmpty()) {
            TransitionNode<?> node = pendingNodes.pop();
            if (!visitedNodes.add(node) || node == endNode || node instanceof EndElement) {
                continue;
            }
            List<TransitionNode<?>> outgoingNodes = node.getOutgoingNodes();
            if (CollectionUtils.isEmpty(outgoingNodes)) {
                if (node instanceof AbruptCompletionElement) {
                    pendingNodes.push(endNode);
                    continue;
                }
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "Flow path does not reach the configured end node, node=" + node.getId() + ", container="
                        + container.getId() + ", flow=" + flowModel.getCode(), null);
            }
            for (int i = outgoingNodes.size() - 1; i >= 0; i--) {
                pendingNodes.push(outgoingNodes.get(i));
            }
        }
    }

    private void checkCycle(NodeContainer<?> container, T flowModel) {
        DirectedGraph<TransitionNode<?>> directedGraph = buildDirectedGraph(container);
        List<TransitionNode<?>> cycle = directedGraph.findCycle();
        if (CollectionUtils.isNotEmpty(cycle)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cycle found in flow " + flowModel.getCode() + ", container=" + container.getId() + ", nodes=["
                    + cycle.stream().map(Node::getId).collect(Collectors.joining(",")) + "]", null);
        }
    }

    private DirectedGraph<TransitionNode<?>> buildDirectedGraph(NodeContainer<?> container) {
        DirectedGraph<TransitionNode<?>> directedGraph = new DirectedGraph<>();
        for (Node candidate : container.getAllNodes()) {
            if (!(candidate instanceof TransitionNode<?> node)) {
                continue;
            }
            List<TransitionNode<?>> outgoingNodes = node.getOutgoingNodes();
            if (CollectionUtils.isNotEmpty(outgoingNodes)) {
                outgoingNodes.forEach(outgoingNode -> directedGraph.addEdge(node, outgoingNode));
            }
        }
        return directedGraph;
    }
}
