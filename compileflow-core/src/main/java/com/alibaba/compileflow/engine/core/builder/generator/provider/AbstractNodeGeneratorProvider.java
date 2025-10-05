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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorFactory;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yusu
 */
public abstract class AbstractNodeGeneratorProvider implements NodeGeneratorProvider {

    private final Map<String, CodeGenerator> generatorMap = new HashMap<>();

    @Override
    public CodeGenerator getGenerator(Node node) {
        if (StringUtils.isEmpty(node.getId())) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_001,
                    "No generator found, node id is empty",
                    null
            );
        }
        return getGenerator(node.getId());
    }

    @Override
    public void registerGenerator(NodeContainer<TransitionNode> nodeContainer, GeneratorContext context) {
        registerGenerator(nodeContainer, GeneratorFactory.getInstance().getContainerGenerator(nodeContainer, context), context);
        registerGeneratorInContainer(nodeContainer, context);
    }

    private void registerGeneratorInContainer(NodeContainer<TransitionNode> nodeContainer, GeneratorContext context) {
        for (TransitionNode node : nodeContainer.getAllNodes()) {
            registerNodeGenerator(node, context);

            if (node instanceof NodeContainer) {
                registerGeneratorInContainer((NodeContainer<TransitionNode>) node, context);
            }
        }
    }

    protected abstract void registerNodeGenerator(TransitionNode node, GeneratorContext context);

    protected void registerGenerator(Node node, CodeGenerator generator, GeneratorContext context) {
        if (StringUtils.isEmpty(node.getId())) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_001,
                    "No generator found, node id is empty",
                    null
            );
        }
        generator = preProcess(node, generator, context);
        registerGenerator(node.getId(), generator);
    }

    protected abstract CodeGenerator preProcess(Node node, CodeGenerator generator, GeneratorContext context);

    private CodeGenerator getGenerator(String id) {
        return Optional.ofNullable(generatorMap.get(id))
                .orElseThrow(() -> new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_003,
                        "No generator found, node is " + id
                ));
    }

    private void registerGenerator(String id, CodeGenerator generator) {
        generatorMap.put(id, generator);
    }

}
