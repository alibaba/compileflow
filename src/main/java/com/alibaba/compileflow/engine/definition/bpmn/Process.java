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

public class Process extends CallableElement implements ElementContainer<FlowElement, FlowNode>, VarSupport {

    private Auditing auditing;

    private Monitoring monitoring;

    private List<Property> properties = new ArrayList<>();

    private List<LaneSet> laneSets = new ArrayList<>();

    private List<FlowElement> flowElements = new ArrayList<>();

    private List<Artifact> artifacts = new ArrayList<>();

    private List<ResourceRole> resourceRoles = new ArrayList<>();

    private List<CorrelationSubscription> correlationSubscriptions = new ArrayList<>();

    private List<String> supports = new ArrayList<>();

    private ProcessType processType;

    private Boolean isClosed;

    private Boolean isExecutable;

    private String definitionalCollaborationRef;

    private List<IVar> vars = new ArrayList<>();

    public Auditing getAuditing() {
        return auditing;
    }

    public void setAuditing(Auditing auditing) {
        this.auditing = auditing;
    }

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(Monitoring monitoring) {
        this.monitoring = monitoring;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<LaneSet> getLaneSets() {
        return laneSets;
    }

    public void setLaneSets(List<LaneSet> laneSets) {
        this.laneSets = laneSets;
    }

    public List<FlowElement> getFlowElements() {
        return flowElements;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<ResourceRole> getResourceRoles() {
        return resourceRoles;
    }

    public void setResourceRoles(List<ResourceRole> resourceRoles) {
        this.resourceRoles = resourceRoles;
    }

    public List<CorrelationSubscription> getCorrelationSubscriptions() {
        return correlationSubscriptions;
    }

    public void setCorrelationSubscriptions(
        List<CorrelationSubscription> correlationSubscriptions) {
        this.correlationSubscriptions = correlationSubscriptions;
    }

    public List<String> getSupports() {
        return supports;
    }

    public void setSupports(List<String> supports) {
        this.supports = supports;
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

    public String getDefinitionalCollaborationRef() {
        return definitionalCollaborationRef;
    }

    public void setDefinitionalCollaborationRef(String definitionalCollaborationRef) {
        this.definitionalCollaborationRef = definitionalCollaborationRef;
    }

    public void addArtifact(Artifact artifact) {
        artifacts.add(artifact);
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
        return "UNDEFINED";
    }
}
