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
package com.alibaba.compileflow.engine.tbbpm.model;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a flow node that owns a single-entry, single-exit local scope.
 */
public abstract class StructuredScopeNode extends FlowNode implements TbbpmNodeContainer {
    private final List<FlowNode> flowNodes = new ArrayList<>();

    @Override
    public final void addNode(FlowNode node) {
        flowNodes.add(node);
    }

    @Override
    public final List<FlowNode> getAllNodes() {
        return flowNodes;
    }

    @Override
    public final FlowNode getNode(String id) {
        return flowNodes
            .stream()
            .filter(node -> id.equals(node.getId()))
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_RESOURCE_001, "Undefined node, node id is " + id));
    }

    @Override
    public final FlowNode getStartNode() {
        return uniqueBoundary(StartNode.class, "start");
    }

    @Override
    public final FlowNode getEndNode() {
        return uniqueBoundary(EndNode.class, "end");
    }

    protected abstract String elementName();

    private FlowNode uniqueBoundary(Class<? extends FlowNode> type, String name) {
        List<FlowNode> boundaries = flowNodes.stream().filter(type::isInstance).toList();
        if (boundaries.size() != 1) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    elementName() + " '" + getId() + "' must contain exactly one " + name + " node");
        }
        return boundaries.get(0);
    }
}
