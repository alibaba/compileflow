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

import java.util.List;

/**
 * Represents a node in the process flow that can be connected by transitions.
 *
 * @author yusu
 */
public interface TransitionNode<T extends Transition> extends Node {

    /**
     * Gets all incoming transitions from this node.
     */
    List<T> getIncomingTransitions();

    /**
     * Gets all outgoing transitions from this node.
     */
    List<T> getOutgoingTransitions();

    /**
     * Gets all nodes that have transitions leading to this node.
     */
    List<TransitionNode> getIncomingNodes();

    /**
     * Gets all nodes that this node has transitions to.
     */
    List<TransitionNode> getOutgoingNodes();

}
