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
package com.alibaba.compileflow.engine.core.definition;

import com.alibaba.compileflow.engine.core.definition.var.IVar;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract base class for any element in a process that acts as a node in the
 * flow graph, capable of having incoming and outgoing transitions.
 * <p>
 * This class provides the core data structures for managing connections to other
 * nodes (via {@link Transition} objects) and for holding node-scoped variables.
 * It is the foundation for all concrete flow node types like tasks, gateways,
 * and events.
 *
 * @param <T> The specific type of {@link Transition} connected to this node.
 * @author yusu
 */
public abstract class BaseFlowNode<T extends Transition> extends BaseFlowElement implements TransitionNode<T>, VarSupport {

    /**
     * The list of transitions that flow into this node.
     */
    private List<T> incomingTransitions = new ArrayList<>();

    /**
     * The list of transitions that flow out of this node.
     */
    private List<T> outgoingTransitions = new ArrayList<>();

    private List<TransitionNode> incomingNodes = new ArrayList<>();

    private List<TransitionNode> outgoingNodes = new ArrayList<>();

    /**
     * A list of variables scoped to this node.
     */
    private List<IVar> vars = new ArrayList<>();

    public void addIncomingTransition(T incomingTransition) {
        incomingTransitions.add(incomingTransition);
    }

    public void addOutgoingTransition(T outgoingTransition) {
        outgoingTransitions.add(outgoingTransition);
    }

    @Override
    public List<T> getIncomingTransitions() {
        return incomingTransitions;
    }

    @Override
    public List<T> getOutgoingTransitions() {
        return outgoingTransitions;
    }

    @Override
    public List<TransitionNode> getIncomingNodes() {
        return incomingNodes;
    }

    public void addIncomingNode(TransitionNode incomingNode) {
        this.incomingNodes.add(incomingNode);
    }

    @Override
    public List<TransitionNode> getOutgoingNodes() {
        return outgoingNodes;
    }

    public void addOutgoingNode(TransitionNode outgoingNode) {
        this.outgoingNodes.add(outgoingNode);
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
