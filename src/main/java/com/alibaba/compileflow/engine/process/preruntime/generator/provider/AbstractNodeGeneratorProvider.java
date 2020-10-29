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
package com.alibaba.compileflow.engine.process.preruntime.generator.provider;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yusu
 */
public abstract class AbstractNodeGeneratorProvider implements NodeGeneratorProvider {

    protected AbstractProcessRuntime runtime;
    private final Map<String, Generator> generatorMap = new HashMap<>();

    public AbstractNodeGeneratorProvider(AbstractProcessRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Generator getGenerator(Node node) {
        if (StringUtils.isEmpty(node.getId())) {
            throw new CompileFlowException("No generator found, node id is empty");
        }
        return getGenerator(node.getId());
    }

    @Override
    public void registerGenerator(Node node, Generator generator) {
        if (StringUtils.isEmpty(node.getId())) {
            throw new CompileFlowException("No generator found, node id is empty");
        }
        generator = preProcess(node, generator);
        registerGenerator(node.getId(), generator);
    }

    protected abstract Generator preProcess(Node node, Generator generator);

    private Generator getGenerator(String id) {
        return Optional.ofNullable(generatorMap.get(id))
            .orElseThrow(() -> new CompileFlowException("No generator found, node is " + id));
    }

    private void registerGenerator(String id, Generator generator) {
        generatorMap.put(id, generator);
    }

}
