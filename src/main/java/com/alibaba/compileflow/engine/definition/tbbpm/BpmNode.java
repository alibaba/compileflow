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
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.HasVar;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.var.IVar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmNode implements NodeContainer<FlowNode>, HasVar, Element {

    private String id;

    private String code;

    private int version;

    private String bizCode;

    private String tenantId;

    private String name;

    private String type;

    private String description;

    private List<IVar> vars = new ArrayList<>();

    private List<FlowNode> allNodes = new ArrayList<>();

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

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

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getTag() {
        return "UNDEFINED";
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
    public List<IVar> getVars() {
        return vars;
    }

    @Override
    public void addVar(IVar var) {
        vars.add(var);
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return allNodes;
    }

    @Override
    public void addNode(FlowNode node) {
        allNodes.add(node);
    }

    @Override
    public FlowNode getNode(String id) {
        return allNodes.stream().filter(node -> id.equals(node.getId())).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined node, node id is " + id));
    }

    @Override
    public FlowNode getStartNode() {
        return allNodes.stream().filter(node -> node instanceof StartNode).findFirst()
            .orElseThrow(() -> new CompileFlowException("No start node found"));
    }

    @Override
    public FlowNode getEndNode() {
        return allNodes.stream().filter(node -> node instanceof EndNode).findFirst()
            .orElseThrow(() -> new CompileFlowException("No end node found"));
    }

}
