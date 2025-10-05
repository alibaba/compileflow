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
package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractGatewayNodeCodeGenerator;
import com.alibaba.compileflow.engine.tbbpm.definition.InclusiveNode;
import com.alibaba.compileflow.engine.tbbpm.definition.Transition;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Generates code for a TBBPM InclusiveNode.
 * Evaluates conditional transitions, executes matched branches (possibly in parallel),
 * and uses the default transition when no conditions match.
 *
 * @author yusu
 */
public class InclusiveGenerator extends AbstractGatewayNodeCodeGenerator<InclusiveNode> {

    public InclusiveGenerator(GeneratorContext context, InclusiveNode flowNode) {
        super(context, flowNode);
    }

    @Override
    protected void generateBranchNodeCode(CodeTargetSupport codeTargetSupport) {
        List<Transition> outgoingFlows = flowNode.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }

        List<Transition> conditionalFlows = getConditionalFlows(outgoingFlows);
        Transition defaultFlow = flowNode.getDefaultTransition();

        generateConditionalBranchLogic(codeTargetSupport, "GatewayExecutor.inclusive",
                conditionalFlows, defaultFlow, outgoingFlows);
    }

}
