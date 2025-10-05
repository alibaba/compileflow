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
package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.definition.BaseElement;
import com.alibaba.compileflow.engine.core.definition.BaseFlowModel;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The in-memory object representation of a BPMN 2.0 process definition.
 *
 * <p>This class is the root of the object graph created by the
 * {@link com.alibaba.compileflow.engine.bpmn.builder.converter.BpmnModelConverter}
 * when parsing a {@code .bpmn} or {@code .bpmn20.xml} file. A BPMN model can
 * contain one or more {@link Process} definitions, though CompileFlow typically
 * operates on the first one.
 *
 * @author yusu
 */
public class BpmnModel extends BaseFlowModel<FlowNode> {

    private final List<Process> processes = new ArrayList<>(1);

    private List<Message> messages = new ArrayList<>();

    /**
     * Adds a {@link Process} to this model.
     *
     * @param process The process to add.
     */
    public void addProcess(Process process) {
        processes.add(process);
    }

    /**
     * Gets the primary (usually the first) {@link Process} definition from the model.
     *
     * @return The process definition.
     */
    public Process getProcess() {
        if (processes.isEmpty()) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_002,
                    "BPMN model contains no process definition.",
                    null
            );
        }
        return processes.get(0);
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    /**
     * Finds any flow element (e.g., a node, sequence flow) within any process
     * in this model by its unique ID.
     *
     * @param id  The ID of the element to find.
     * @param <T> The expected type of the element.
     * @return The found element.
     * @throws CompileFlowException if no element with the given ID is found.
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseElement> T getFlowElement(String id) {
        Element element = processes.stream().map(process -> process.getFlowElements())
                .flatMap(Collection::stream)
                .filter(flowElement -> flowElement.getId().equals(id))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (element != null) {
            return (T) element;
        }
        return (T) messages.stream().filter(message -> message.getId().equals(id)).findFirst()
                .orElseThrow(() -> new CompileFlowException.BusinessException(
                        ErrorCode.CF_RESOURCE_001,
                        "Undefined element, element id is " + id
                ));
    }

    @Override
    public List<TransitionNode> getTransitionNodes() {
        return processes.stream().map(Process::getFlowElements).flatMap(Collection::stream)
                .filter(flowElement -> flowElement instanceof TransitionNode)
                .map(flowElement -> (TransitionNode) flowElement)
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
        getProcess().addNode(node);
    }

}
