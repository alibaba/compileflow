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

/**
 * Represents a directed connection (an edge) between two nodes in a process graph.
 *
 * <p>A transition may have an associated expression, which acts as a condition.
 * For a transition to be taken, its condition must evaluate to {@code true}.
 *
 * @author yusu
 */
public interface Transition extends HasExpression {

    /**
     * Gets the ID of the source node from which this transition originates.
     *
     * @return The ID of the source node.
     */
    String getSource();

    /**
     * Gets the ID of the target node to which this transition points.
     *
     * @return The ID of the target node.
     */
    String getTarget();

    /**
     * Gets the priority of this transition, used to determine the evaluation
     * order when multiple transitions originate from the same decision gateway.
     * <p>
     * Lower numbers indicate higher priority.
     *
     * @return The priority value.
     */
    int getPriority();

}
