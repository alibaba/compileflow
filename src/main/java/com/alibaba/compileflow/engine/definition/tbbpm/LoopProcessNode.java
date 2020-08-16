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
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.Stateless;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class LoopProcessNode extends FlowNode implements NodeContainer<FlowNode>, Stateless {

    private String loopType;

    private String variableClass;

    private String variableName;

    private String indexVarName;

    private String collectionVarName;

    private String whileExpression;

    private String startNodeId;

    private String endNodeId;

    private List<FlowNode> flowNodes = new ArrayList<>();

    public String getLoopType() {
        return loopType;
    }

    public void setLoopType(String loopType) {
        this.loopType = loopType;
    }

    public String getVariableClass() {
        return variableClass;
    }

    public void setVariableClass(String variableClass) {
        this.variableClass = variableClass;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getIndexVarName() {
        return indexVarName;
    }

    public void setIndexVarName(String indexVarName) {
        this.indexVarName = indexVarName;
    }

    public String getCollectionVarName() {
        return collectionVarName;
    }

    public void setCollectionVarName(String collectionVarName) {
        this.collectionVarName = collectionVarName;
    }

    public String getWhileExpression() {
        return whileExpression;
    }

    public void setWhileExpression(String whileExpression) {
        this.whileExpression = whileExpression;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public String getEndNodeId() {
        return endNodeId;
    }

    public void setEndNodeId(String endNodeId) {
        this.endNodeId = endNodeId;
    }

    @Override
    public FlowNode getNode(String id) {
        return flowNodes.stream().filter(node -> id.equals(node.getId())).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined node, node id is " + id));
    }

    @Override
    public void addNode(FlowNode node) {
        flowNodes.add(node);
    }

    public FlowNode getNodeByTag(String tag) {
        return flowNodes.stream().filter(node -> tag.equals(node.getTag())).findFirst()
            .orElseThrow(() -> new CompileFlowException("Undefined node, node tag is " + tag));
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return flowNodes;
    }

    @Override
    public FlowNode getStartNode() {
        return flowNodes.stream().filter(node -> node.getId().equals(startNodeId)).findFirst()
            .orElseThrow(() -> new CompileFlowException("No start node found"));
    }

    @Override
    public FlowNode getEndNode() {
        return flowNodes.stream().filter(node -> node.getId().equals(endNodeId)).findFirst()
            .orElseThrow(() -> new CompileFlowException("No end node found"));
    }

}
