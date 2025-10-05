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
package com.alibaba.compileflow.engine.core.builder.generator.container;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractRuntimeCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;

/**
 * Abstract base class for container code generators.
 * Provides functionality for generating code from container nodes,
 * including child node processing and container-specific logic.
 *
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractContainerCodeGenerator extends AbstractRuntimeCodeGenerator {

    protected NodeContainer nodeContainer;

    public AbstractContainerCodeGenerator(GeneratorContext context,
                                          NodeContainer nodeContainer) {
        super(context);
        this.nodeContainer = nodeContainer;
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {

    }

    protected CodeGenerator getGenerator(Node node) {
        return context.getGenerator(node);
    }

}
