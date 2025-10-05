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
package com.alibaba.compileflow.engine.core.builder.generator.node;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractRuntimeCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.job.HasJob;
import org.apache.commons.lang3.StringUtils;

/**
 * Abstract base class for node code generators.
 * Provides common functionality for generating code from flow nodes,
 * including node comment generation and node-specific processing.
 *
 * @param <N> The type of node this generator handles
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractNodeCodeGenerator<N extends Node>
        extends AbstractRuntimeCodeGenerator {

    protected N flowNode;

    public AbstractNodeCodeGenerator(GeneratorContext context, N flowNode) {
        super(context);
        this.flowNode = flowNode;
    }

    protected void generateNodeComment(CodeTargetSupport codeTargetSupport) {
        String comment = "//" + flowNode.getClass().getSimpleName();
        if (StringUtils.isNotEmpty(flowNode.getName())) {
            comment += ": " + flowNode.getName();
        }
        codeTargetSupport.addBodyLine(comment);
    }

    /**
     * Unified decision: whether the current node's logic should be extracted
     * into a separate method instead of being inlined at the call site.
     * <p>
     * Minimal, non-overengineered rule set:
     * 1) Inside trigger method -> extract
     * 2) Node has job policy (retry/failure/timeout etc.) -> extract
     */
    protected boolean shouldExtractMethod(CodeTargetSupport codeTargetSupport) {
        if (isTriggerMethod(codeTargetSupport)) {
            return true;
        }
        if (flowNode instanceof HasJob) {
            return ((HasJob) flowNode).getJobPolicy() != null;
        }
        return false;
    }

}
