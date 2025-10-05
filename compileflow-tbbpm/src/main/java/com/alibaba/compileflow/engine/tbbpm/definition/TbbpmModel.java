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
package com.alibaba.compileflow.engine.tbbpm.definition;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.definition.BaseFlowModel;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The in-memory object representation of a TBBPM process definition.
 *
 * <p>This class serves as the root of the object graph that is created by the
 * {@link com.alibaba.compileflow.engine.tbbpm.builder.converter.TbbpmModelConverter}
 * when parsing a {@code .bpm} file. It holds all the nodes, transitions, and
 * metadata associated with a TBBPM process.
 *
 * @author wuxiang
 * @author yusu
 */
public class TbbpmModel extends BaseFlowModel<FlowNode> {

    /**
     * The version of the process definition.
     */
    private int version;

    /**
     * A business-specific code or category for the process.
     */
    private String bizCode;

    /**
     * The tenant identifier, used in multi-tenant environments.
     */
    private String tenantId;

    /**
     * The type of the process.
     */
    private String type;

    /**
     * A human-readable description of the process.
     */
    private String description;

    /**
     * A list containing every node defined in the process.
     */
    private List<FlowNode> allNodes = new ArrayList<>();

    /**
     * A list of nodes that are part of the executable runtime graph.
     */
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
                .orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_004, "Undefined node, node id is " + id));
    }

}
