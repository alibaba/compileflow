/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.definition.bpmn;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.common.AbstractFlowModel;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class BpmnModel extends AbstractFlowModel<FlowNode> {

    private List<Process> processes = new ArrayList<>(1);

    public void addProcesses(Process process) {
        processes.add(process);
    }

    public Process getProcess() {
        return processes.get(0);
    }

    public <T extends FlowElement> T getFlowElement(String id) {
        return (T)processes.stream().map(process -> process.getElement(id))
            .filter(Objects::nonNull).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined element, element id is " + id));
    }

    @Override
    public List<TransitionNode> getTransitionNodes() {
        return processes.stream().map(Process::getFlowElements).flatMap(Collection::stream)
            .filter(flowElement -> flowElement instanceof TransitionNode)
            .map(flowElement -> (TransitionNode)flowElement)
            .collect(Collectors.toList());
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return processes.stream().map(Process::getAllNodes)
            .flatMap(Collection::stream).collect(Collectors.toList());
    }

    @Override
    public List<FlowNode> getRuntimeNodes() {
        return getAllNodes();
    }

    @Override
    public void addNode(FlowNode node) {
        getAllNodes().add(node);
    }

    @Override
    public FlowNode getNode(String id) {
        return getAllNodes().stream().filter(node -> id.equals(node.getId())).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined node, node id is " + id));
    }

    public FlowNode getNodeByTag(String tag) {
        return getAllNodes().stream().filter(node -> tag.equals(node.getTag())).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined node, node tag is " + tag));
    }

    @Override
    public FlowNode getStartNode() {
        return getAllNodes().stream().filter(node -> node instanceof StartEvent).findFirst()
            .orElseThrow(() -> new CompileFlowException("No start node found"));
    }

    @Override
    public FlowNode getEndNode() {
        return getAllNodes().stream().filter(node -> node instanceof EndEvent).findFirst()
            .orElseThrow(() -> new CompileFlowException("No end node found"));
    }

}
