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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl;

import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.action.HasAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.ActionMethodGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 */
public abstract class AbstractActionNodeGenerator<N extends Node>
    extends AbstractNodeGenerator<N> {

    public AbstractActionNodeGenerator(AbstractProcessRuntime runtime, N flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        HasAction hasAction = (HasAction)flowNode;
        IAction action = hasAction.getAction();
        generateActionCode(codeTargetSupport, action);
    }

    protected void generateActionCode(CodeTargetSupport codeTargetSupport, IAction action) {
        if (action != null) {
            Generator actionGenerator = GeneratorFactory.getInstance().getActionGenerator(action, runtime);
            actionGenerator.generateCode(codeTargetSupport);
        }
    }

    protected void generateActionMethodCode(CodeTargetSupport codeTargetSupport, IAction action) {
        if (action != null) {
            ActionMethodGenerator actionMethodGenerator = GeneratorFactory.getInstance()
                .getActionGenerator(action, runtime);
            actionMethodGenerator.generateActionMethodCode(codeTargetSupport);

            codeTargetSupport.addBodyLine(
                actionMethodGenerator.generateActionMethodName(codeTargetSupport) + "();");
        }
    }

}
