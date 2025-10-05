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

import com.alibaba.compileflow.engine.bpmn.definition.*;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.provider.AbstractNodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;

/**
 * @author yusu
 */
public class BpmnNodeGeneratorProvider extends AbstractNodeGeneratorProvider {

    @Override
    protected CodeGenerator preProcess(Node node, CodeGenerator generator, GeneratorContext context) {
        if (node instanceof Activity) {
            LoopCharacteristics loopCharacteristics = ((Activity) node).getLoopCharacteristics();
            if (loopCharacteristics instanceof StandardLoopCharacteristics) {
                return new StandardLoopCharacteristicsGenerator(context,
                        (StandardLoopCharacteristics) loopCharacteristics, generator);
            }
            if (loopCharacteristics instanceof MultiInstanceLoopCharacteristics) {
                return new MultiInstanceLoopCharacteristicsGenerator(context,
                        (MultiInstanceLoopCharacteristics) loopCharacteristics, generator);
            }
        }
        return generator;
    }

    @Override
    protected void registerNodeGenerator(TransitionNode node, GeneratorContext context) {
        if (node instanceof ServiceTask) {
            registerGenerator(node, new ServiceTaskGenerator(context, (ServiceTask) node), context);
        } else if (node instanceof ScriptTask) {
            registerGenerator(node, new ScriptTaskGenerator(context, (ScriptTask) node), context);
        } else if (node instanceof ExclusiveGateway) {
            registerGenerator(node, new ExclusiveGatewayGenerator(context, (ExclusiveGateway) node), context);
        } else if (node instanceof InclusiveGateway) {
            registerGenerator(node, new InclusiveGatewayGenerator(context, (InclusiveGateway) node), context);
        } else if (node instanceof ParallelGateway) {
            registerGenerator(node, new ParallelGatewayGenerator(context, (ParallelGateway) node), context);
        } else if (node instanceof StartEvent) {
            registerGenerator(node, new StartEventGenerator(context, (StartEvent) node), context);
        } else if (node instanceof EndEvent) {
            registerGenerator(node, new EndEventGenerator(context, (EndEvent) node), context);
        } else if (node instanceof SubProcess) {
            registerGenerator(node, new SubProcessGenerator(context, (SubProcess) node), context);
        } else if (node instanceof ReceiveTask) {
            registerGenerator(node, new ReceiveTaskGenerator(context, (ReceiveTask) node), context);
        } else if (node instanceof CallActivity) {
            registerGenerator(node, new CallActivityGenerator(context, (CallActivity) node), context);
        } else if (node instanceof Message) {
            registerGenerator(node, new MessageGenerator(context), context);
        } else {
            throw new IllegalStateException(
                    "Unsupported BPMN node type: " + node.getClass().getName() +
                            ". Supported node types are: ServiceTask, ScriptTask, ExclusiveGateway, " +
                            "InclusiveGateway, ParallelGateway, StartEvent, EndEvent, SubProcess, " +
                            "ReceiveTask, CallActivity, and Message. " +
                            "Please check your BPMN model or implement a custom generator for this node type."
            );
        }
    }

}
