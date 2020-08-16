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
package com.alibaba.compileflow.engine.process.preruntime.generator.factory;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.ActionType;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support.JavaActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support.MVELActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support.QLActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support.SpringActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.container.ContainerGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 */
public class GeneratorFactory {

    public static GeneratorFactory getInstance() {
        return GeneratorFactory.Holder.INSTANCE;
    }

    public ContainerGenerator getContainerGenerator(NodeContainer nodeContainer, AbstractProcessRuntime runtime) {
        return new ContainerGenerator(runtime, nodeContainer);
    }

    public AbstractActionGenerator getActionGenerator(IAction action, AbstractProcessRuntime runtime) {
        if (ActionType.JAVA.getValue().equals(action.getType())) {
            return new JavaActionGenerator(runtime, action);
        }
        if (ActionType.SPRING_BEAN.getValue().equals(action.getType())) {
            return new SpringActionGenerator(runtime, action);
        }
        if (ActionType.QL.getValue().equals(action.getType())) {
            return new QLActionGenerator(runtime, action);
        }
        if (ActionType.MVEL.getValue().equals(action.getType())) {
            return new MVELActionGenerator(runtime, action);
        }
        throw new CompileFlowException("Action not supported, action type is " + action.getType());
    }

    private static class Holder {
        private static final GeneratorFactory INSTANCE = new GeneratorFactory();
    }

}
