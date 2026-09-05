/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract flow node that owns incoming and outgoing transitions.
 *
 * @author yusu
 */
public abstract class AbstractFlowNode<T extends Transition> extends AbstractFlowElement implements TransitionNode<T> {
    private List<T> incomingTransitions = new ArrayList<>();
    private List<T> outgoingTransitions = new ArrayList<>();
    private List<TransitionNode<?>> incomingNodes = new ArrayList<>();
    private List<TransitionNode<?>> outgoingNodes = new ArrayList<>();

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
    public List<TransitionNode<?>> getIncomingNodes() {
        return incomingNodes;
    }

    public void addIncomingNode(TransitionNode<?> incomingNode) {
        this.incomingNodes.add(incomingNode);
    }

    @Override
    public List<TransitionNode<?>> getOutgoingNodes() {
        return outgoingNodes;
    }

    public void addOutgoingNode(TransitionNode<?> outgoingNode) {
        this.outgoingNodes.add(outgoingNode);
    }
}
