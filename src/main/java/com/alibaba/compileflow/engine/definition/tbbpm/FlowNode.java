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

import com.alibaba.compileflow.engine.definition.common.TransitionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class FlowNode extends FlowElementNode implements TransitionNode {

    private String tag;

    private String description;

    private List<Transition> incomingTransitions = new ArrayList<>();

    private List<Transition> outgoingTransitions = new ArrayList<>();

    private List<FlowNode> incomingNodes = new ArrayList<>();

    private List<FlowNode> outgoingNodes = new ArrayList<>();

    @Override
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Transition> getIncomingTransitions() {
        return incomingTransitions;
    }

    public void addIncomingTransition(Transition incomingTransition) {
        incomingTransitions.add(incomingTransition);
    }

    public List<Transition> getOutgoingTransitions() {
        return outgoingTransitions;
    }

    public void addOutgoingTransition(Transition outgoingTransition) {
        outgoingTransitions.add(outgoingTransition);
    }

    @Override
    public List<FlowNode> getIncomingNodes() {
        return incomingNodes;
    }

    public void addIncomingNodes(FlowNode incomingNode) {
        incomingNodes.add(incomingNode);
    }

    @Override
    public List<FlowNode> getOutgoingNodes() {
        return outgoingNodes;
    }

    public void addOutgoingNode(FlowNode outgoingNode) {
        outgoingNodes.add(outgoingNode);
    }

    public FlowNode getOutgoingNode() {
        return outgoingNodes.get(0);
    }

    @Override
    public List<Transition> getTransitions() {
        return outgoingTransitions;
    }

}
