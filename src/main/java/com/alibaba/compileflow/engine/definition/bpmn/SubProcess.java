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
import com.alibaba.compileflow.engine.definition.common.ElementContainer;
import com.alibaba.compileflow.engine.definition.common.VarSupport;
import com.alibaba.compileflow.engine.definition.common.var.IVar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SubProcess extends Activity implements ElementContainer<FlowElement, FlowNode>, VarSupport {

    private List<LaneSet> laneSets;

    private List<FlowElement> flowElements = new ArrayList<>();

    private List<Artifact> artifacts;

    private Boolean triggeredByEvent;

    private String subProcessCode;

    private List<IVar> vars = new ArrayList<>(3);

    public List<LaneSet> getLaneSets() {
        return laneSets;
    }

    public void setLaneSets(List<LaneSet> laneSets) {
        this.laneSets = laneSets;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public Boolean getTriggeredByEvent() {
        return triggeredByEvent;
    }

    public void setTriggeredByEvent(Boolean triggeredByEvent) {
        this.triggeredByEvent = triggeredByEvent;
    }

    public String getSubProcessCode() {
        return subProcessCode;
    }

    public void setSubProcessCode(String subProcessCode) {
        this.subProcessCode = subProcessCode;
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
    public List<FlowElement> getAllElements() {
        return flowElements;
    }

    @Override
    public void addElement(FlowElement element) {
        flowElements.add(element);
    }

    @Override
    public FlowElement getElement(String id) {
        return flowElements.stream().filter(e -> e.getId().equals(id))
            .findFirst().orElseThrow(() -> new CompileFlowException("No element found, id is " + id));
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return flowElements.stream().filter(e -> e instanceof FlowNode)
            .map(e -> (FlowNode)e).collect(Collectors.toList());
    }

    @Override
    public void addNode(FlowNode node) {
        flowElements.add(node);
    }

    @Override
    public FlowNode getNode(String id) {
        return (FlowNode)getElement(id);
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
