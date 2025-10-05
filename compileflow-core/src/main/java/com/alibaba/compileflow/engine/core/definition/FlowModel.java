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

import java.util.List;

/**
 * Represents the root of a parsed and validated process definition.
 *
 * <p>This interface provides access to all the elements of a process, including its
 * nodes, transitions, and variables. It is the in-memory object graph that serves
 * as the input for the {@link com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator}.
 *
 * @param <T> The base type of the nodes contained within this model (e.g., BpmnNode, TbbpmNode).
 * @author yusu
 */
public interface FlowModel<T extends Node> extends com.alibaba.compileflow.engine.FlowModel, NodeContainer<T> {

    /**
     * A unique identifier for the process definition itself, distinct from the code.
     */
    @Override
    String getId();

    /**
     * The primary business-facing identifier for the process, used for execution
     * and caching.
     */
    String getCode();

    /**
     * A human-readable name for the process.
     */
    @Override
    String getName();

    /**
     * Gets all nodes in the process that are executed at runtime (e.g., tasks, gateways).
     *
     * @return A list of all runtime nodes.
     */
    List<T> getRuntimeNodes();

    /**
     * Gets all transition nodes, which represent the connections between runtime nodes.
     *
     * @return A list of all transition nodes.
     */
    List<TransitionNode> getTransitionNodes();

    /**
     * Gets all variables defined in the process scope.
     *
     * @return A list of all variables.
     */
    List<IVar> getVars();

    /**
     * Gets the variables that are defined as input parameters to the process.
     *
     * @return A list of parameter variables.
     */
    List<IVar> getParamVars();

    /**
     * Gets the variables that are used for internal calculations within the process.
     *
     * @return A list of inner variables.
     */
    List<IVar> getInnerVars();

    /**
     * Gets the variables that are designated as the return values of the process.
     *
     * @return A list of return variables.
     */
    List<IVar> getReturnVars();

}
