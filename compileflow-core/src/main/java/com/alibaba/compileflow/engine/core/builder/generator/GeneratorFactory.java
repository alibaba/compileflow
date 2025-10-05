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
package com.alibaba.compileflow.engine.core.builder.generator;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.constants.ActionType;
import com.alibaba.compileflow.engine.core.builder.generator.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.java.JavaActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.java.JavaInlineActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.java.JavaSourceActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.script.GroovyActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.script.MVELActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.script.QLActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.action.spring.SpringActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.container.NodeContainerCodeGenerator;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.action.IAction;

/**
 * @author yusu
 */
public class GeneratorFactory {

    public static GeneratorFactory getInstance() {
        return Holder.INSTANCE;
    }

    public NodeContainerCodeGenerator getContainerGenerator(NodeContainer nodeContainer, GeneratorContext runtime) {
        return new NodeContainerCodeGenerator(runtime, nodeContainer);
    }

    public AbstractActionGenerator getActionGenerator(IAction action, GeneratorContext context) {
        if (ActionType.JAVA.getValue().equals(action.getType())) {
            return new JavaActionGenerator(context, action);
        }
        if (ActionType.SPRING_BEAN.getValue().equals(action.getType())) {
            return new SpringActionGenerator(context, action);
        }
        if (ActionType.QL.getValue().equals(action.getType())) {
            return new QLActionGenerator(context, action);
        }
        if (ActionType.MVEL.getValue().equals(action.getType())) {
            return new MVELActionGenerator(context, action);
        }
        if (ActionType.GROOVY.getValue().equals(action.getType())) {
            return new GroovyActionGenerator(context, action);
        }
        if (ActionType.JAVA_INLINE.getValue().equals(action.getType())) {
            return new JavaInlineActionGenerator(context, action);
        }
        if (ActionType.JAVA_SOURCE.getValue().equals(action.getType())) {
            return new JavaSourceActionGenerator(context, action);
        }
        String supported = String.join(", ",
                ActionType.JAVA.getValue(),
                ActionType.SPRING_BEAN.getValue(),
                ActionType.QL.getValue(),
                ActionType.MVEL.getValue(),
                ActionType.GROOVY.getValue(),
                ActionType.JAVA_INLINE.getValue(),
                ActionType.JAVA_SOURCE.getValue());
        throw new CompileFlowException.BusinessException(
                ErrorCode.CF_VALIDATION_001,
                "Action not supported, action type is " + action.getType() + ". Supported types: [" + supported + "]"
        );
    }

    private static class Holder {
        private static final GeneratorFactory INSTANCE = new GeneratorFactory();
    }

}
