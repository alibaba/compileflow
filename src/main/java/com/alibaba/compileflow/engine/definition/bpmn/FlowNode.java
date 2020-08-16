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

import com.alibaba.compileflow.engine.definition.common.HasVar;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.var.IVar;

import java.util.ArrayList;
import java.util.List;

public abstract class FlowNode extends FlowElement implements TransitionNode, HasVar {

    private String tag;

    private List<SequenceFlow> incomingFlows = new ArrayList<>();

    private List<SequenceFlow> outgoingFlows = new ArrayList<>();

    private List<FlowNode> incomingNodes = new ArrayList<>();

    private List<FlowNode> outgoingNodes = new ArrayList<>();

    private List<IVar> vars = new ArrayList<>();

    @Override
    public String getTag() {
        if (tag == null) {
            return tag;
        }
        return getId();
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public List<SequenceFlow> getIncomingFlows() {
        return incomingFlows;
    }

    public List<SequenceFlow> getOutgoingFlows() {
        return outgoingFlows;
    }

    public void addIncomingFlow(SequenceFlow incoming) {
        incomingFlows.add(incoming);
    }

    public void addOutgoingFlow(SequenceFlow outgoing) {
        outgoingFlows.add(outgoing);
    }

    @Override
    public List<FlowNode> getIncomingNodes() {
        return incomingNodes;
    }

    @Override
    public List<FlowNode> getOutgoingNodes() {
        return outgoingNodes;
    }

    public void addIncomingNode(FlowNode incomingNode) {
        incomingNodes.add(incomingNode);
    }

    public void addOutgoingNode(FlowNode outgoingNode) {
        outgoingNodes.add(outgoingNode);
    }

    @Override
    public List<SequenceFlow> getTransitions() {
        return outgoingFlows;
    }

    @Override
    public List<IVar> getVars() {
        return vars;
    }

    @Override
    public void addVar(IVar var) {
        vars.add(var);
    }

}
