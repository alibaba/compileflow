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
package com.alibaba.compileflow.engine.definition.tbbpm;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.common.AbstractFlowModel;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmModel extends AbstractFlowModel<FlowNode> {

    private int version;

    private String bizCode;

    private String tenantId;

    private String type;

    private String description;

    private List<FlowNode> allNodes = new ArrayList<>();

    private List<FlowNode> runtimeNodes = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return allNodes;
    }

    public void setAllNodes(List<FlowNode> allNodes) {
        this.allNodes = allNodes;
    }

    @Override
    public void addNode(FlowNode node) {
        allNodes.add(node);
    }

    @Override
    public List<FlowNode> getRuntimeNodes() {
        return runtimeNodes;
    }

    public void setRuntimeNodes(List<FlowNode> runtimeNodes) {
        this.runtimeNodes = runtimeNodes;
    }

    @Override
    public List<TransitionNode> getTransitionNodes() {
        return getRuntimeNodes().stream().filter(Objects::nonNull)
            .collect(Collectors.toList());
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
        return getAllNodes().stream().filter(node -> node instanceof StartNode).findFirst()
            .orElseThrow(() -> new CompileFlowException("No start node found"));
    }

    @Override
    public FlowNode getEndNode() {
        return getAllNodes().stream().filter(node -> node instanceof EndNode).findFirst()
            .orElseThrow(() -> new CompileFlowException("No end node found"));
    }

}