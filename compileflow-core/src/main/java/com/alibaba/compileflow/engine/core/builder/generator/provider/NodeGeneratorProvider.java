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
package com.alibaba.compileflow.engine.core.builder.generator.provider;

import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;

/**
 * Interface defining a provider for generators associated with specific nodes.
 * This provider manages the mapping between nodes and their corresponding code generation logic.
 *
 * @author yusu
 */
public interface NodeGeneratorProvider {

    /**
     * Retrieves the generator responsible for generating code for the given node.
     *
     * @param node The node for which a generator is needed
     * @return The generator instance capable of handling the given node
     */
    CodeGenerator getGenerator(Node node);

    void registerGenerator(NodeContainer<TransitionNode> nodeContainer, GeneratorContext context);

}
