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
import com.alibaba.compileflow.engine.core.definition.BaseFlowElement;
import com.alibaba.compileflow.engine.core.definition.ElementContainer;
import com.alibaba.compileflow.engine.core.definition.VarSupport;
import com.alibaba.compileflow.engine.core.definition.var.IVar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class Process extends CallableElement implements ElementContainer<BaseFlowElement, FlowNode>, VarSupport {

    private final List<BaseFlowElement> flowElements = new ArrayList<>();
    private final List<IVar> vars = new ArrayList<>();
    private ProcessType processType;
    private Boolean isClosed;
    private Boolean isExecutable;

    public List<BaseFlowElement> getFlowElements() {
        return flowElements;
    }

    public ProcessType getProcessType() {
        return processType;
    }

    public void setProcessType(ProcessType processType) {
        this.processType = processType;
    }

    public Boolean getClosed() {
        return isClosed;
    }

    public void setClosed(Boolean closed) {
        isClosed = closed;
    }

    public Boolean getExecutable() {
        return isExecutable;
    }

    public void setExecutable(Boolean executable) {
        isExecutable = executable;
    }

    @Override
    public List<BaseFlowElement> getAllElements() {
        return flowElements;
    }

    @Override
    public void addElement(BaseFlowElement element) {
        flowElements.add(element);
    }

    @Override
    public BaseFlowElement getElement(String id) {
        return flowElements.stream().filter(e -> e.getId().equals(id))
                .findFirst().orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_004, "No element found, id is " + id));
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return flowElements.stream().filter(e -> e instanceof FlowNode)
                .map(e -> (FlowNode) e).collect(Collectors.toList());
    }

    @Override
    public void addNode(FlowNode node) {
        flowElements.add(node);
    }

    @Override
    public FlowNode getNode(String id) {
        return (FlowNode) getElement(id);
    }

    @Override
    public List<IVar> getVars() {
        return vars;
    }

    @Override
    public void addVar(IVar var) {
        vars.add(var);
    }

    @Override
    public String getTag() {
        return getId();
    }

}
