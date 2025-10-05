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

import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.provider.AbstractNodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import com.alibaba.compileflow.engine.tbbpm.definition.*;

/**
 * @author yusu
 */
public class TbbpmNodeGeneratorProvider extends AbstractNodeGeneratorProvider {

    @Override
    protected CodeGenerator preProcess(Node node, CodeGenerator generator, GeneratorContext context) {
        return generator;
    }

    @Override
    protected void registerNodeGenerator(TransitionNode node, GeneratorContext context) {
        if (node instanceof AutoTaskNode) {
            registerGenerator(node, new AutoTaskGenerator(context, (AutoTaskNode) node), context);
        } else if (node instanceof ScriptTaskNode) {
            registerGenerator(node, new ScriptTaskGenerator(context, (ScriptTaskNode) node), context);
        } else if (node instanceof DecisionNode) {
            registerGenerator(node, new DecisionGenerator(context, (DecisionNode) node), context);
        } else if (node instanceof InclusiveNode) {
            registerGenerator(node, new InclusiveGenerator(context, (InclusiveNode) node), context);
        } else if (node instanceof ParallelNode) {
            registerGenerator(node, new ParallelGenerator(context, (ParallelNode) node), context);
        } else if (node instanceof StartNode) {
            registerGenerator(node, new StartGenerator(context, (StartNode) node), context);
        } else if (node instanceof EndNode) {
            registerGenerator(node, new EndGenerator(context, (EndNode) node), context);
        } else if (node instanceof LoopProcessNode) {
            registerGenerator(node, new LoopProcessGenerator(context, (LoopProcessNode) node), context);
        } else if (node instanceof BreakNode) {
            registerGenerator(node, new BreakGenerator(context, (BreakNode) node), context);
        } else if (node instanceof ContinueNode) {
            registerGenerator(node, new ContinueGenerator(context, (ContinueNode) node), context);
        } else if (node instanceof SubBpmNode) {
            registerGenerator(node, new SubBpmGenerator(context, (SubBpmNode) node), context);
        } else if (node instanceof NoteNode) {
            registerGenerator(node, new NoteGenerator(context, (NoteNode) node), context);
        } else if (node instanceof WaitTaskNode) {
            registerGenerator(node, new WaitTaskGenerator(context, (WaitTaskNode) node), context);
        } else if (node instanceof WaitEventTaskNode) {
            registerGenerator(node, new WaitEventTaskGenerator(context, (WaitEventTaskNode) node), context);
        } else {
            throw new IllegalStateException(
                    "Unsupported TBBPM node type: " + node.getClass().getName() +
                            ". Supported node types are: AutoTaskNode, ScriptTaskNode, DecisionNode, " +
                            "InclusiveNode, ParallelNode, StartNode, EndNode, LoopProcessNode, " +
                            "BreakNode, ContinueNode, SubBpmNode, NoteNode, WaitTaskNode, and WaitEventTaskNode. " +
                            "Please check your TBBPM model or implement a custom generator for this node type."
            );
        }
    }

}
