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
package com.alibaba.compileflow.engine.bpmn.model;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BPMN process element that contains flow nodes, sequence flows, and variables.
 *
 * @author yusu
 */
public class Process extends CallableElement implements BpmnElementContainer, ProcessVariableContainer {
    private final List<AbstractFlowElement> flowElements = new ArrayList<>();
    private final List<Variable> vars = new ArrayList<>();
    private Boolean isExecutable;

    public List<AbstractFlowElement> getFlowElements() {
        return flowElements;
    }

    public Boolean getExecutable() {
        return isExecutable;
    }

    public void setExecutable(Boolean executable) {
        isExecutable = executable;
    }

    @Override
    public List<AbstractFlowElement> getAllElements() {
        return flowElements;
    }

    @Override
    public void addElement(AbstractFlowElement element) {
        flowElements.add(element);
    }

    @Override
    public AbstractFlowElement getElement(String id) {
        return flowElements
            .stream()
            .filter(e -> Objects.equals(e.getId(), id))
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_VALIDATION_004, "No element found, id is " + id));
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return flowElements
            .stream()
            .filter(e -> e instanceof FlowNode)
            .map(e -> (FlowNode) e)
            .collect(Collectors.toList());
    }

    @Override
    public void addNode(FlowNode node) {
        flowElements.add(node);
    }

    @Override
    public List<Variable> getVariables() {
        return vars;
    }

    @Override
    public void addVariable(Variable var) {
        vars.add(var);
    }
}
