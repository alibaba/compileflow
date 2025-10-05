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
package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.FlowNode;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractNodeCodeGenerator;

/**
 * The abstract base class for all BPMN 2.0-specific node code generators.
 * <p>
 * This class provides a common foundation for generators that translate a
 * BPMN {@link FlowNode} into executable Java code.
 *
 * @param <N> The specific type of {@link FlowNode} this generator handles.
 * @author yusu
 */
public abstract class AbstractBpmnNodeGenerator<N extends FlowNode> extends AbstractNodeCodeGenerator<N> {

    public AbstractBpmnNodeGenerator(GeneratorContext context, N flowNode) {
        super(context, flowNode);
    }

}
