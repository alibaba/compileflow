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

import com.alibaba.compileflow.engine.core.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.core.builder.generator.constants.MethodConstants;
import com.alibaba.compileflow.engine.core.definition.EndElement;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.ClassWrapper;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for runtime code generators.
 * Provides runtime-specific functionality like variable type inference, method generation,
 * and context management for code generation.
 *
 * @author yusu
 */
public abstract class AbstractRuntimeCodeGenerator extends AbstractCodeGenerator {

    protected GeneratorContext context;

    public AbstractRuntimeCodeGenerator(GeneratorContext context) {
        this.context = context;
    }

    protected Class<?> getVarType(String varName) {
        List<IVar> vars = context.getVars();
        Class<?> clazz = vars.stream().filter(var -> var.getName().equals(varName))
                .findFirst().map(IVar::getDataType).map(DataType::getJavaClass)
                .orElse(null);
        return clazz == null ? Object.class : clazz;
    }

    protected boolean isTriggerMethod(CodeTargetSupport codeTargetSupport) {
        return MethodConstants.TRIGGER_METHOD_NAME.equals(codeTargetSupport.getName());
    }

    protected MethodTarget generateMethodCode(CodeTargetSupport codeTargetSupport, String methodName,
                                              List<IVar> paramVars, IVar returnVar, CodeGenerator methodBodyGenerator) {
        MethodTarget method = new MethodTarget();
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        method.setClassTarget(classTarget);
        method.setName(methodName);
        method.addException(ClassWrapper.of(Exception.class));

        if (CollectionUtils.isNotEmpty(paramVars)) {
            Set<String> usedParamNames = new HashSet<>();
            for (IVar v : paramVars) {
                ClassWrapper pType = ClassWrapper.of(v.getDataType());
                String paramName = generateUniqueParamName(v, usedParamNames);
                usedParamNames.add(paramName);
                method.addParameter(ParamTarget.of(pType, paramName));
            }
        }
        if (returnVar != null) {
            method.setReturnType(ClassWrapper.of(returnVar.getDataType()));
        }

        classTarget.addMethod(method);
        methodBodyGenerator.generateCode(method);
        return method;
    }

    protected boolean isEndNode(String id) {
        return context.getNodeById(id) instanceof EndElement;
    }

    protected String getMethodParamName(IVar var) {
        String raw = StringUtils.isNotBlank(var.getContextVarName())
                ? var.getContextVarName()
                : var.getName();
        if (StringUtils.isBlank(raw)) {
            raw = "p";
        }
        return JavaIdentifierUtils.toFieldName(raw);
    }

    private String generateUniqueParamName(IVar var, Set<String> usedParamNames) {
        String base = getMethodParamName(var);
        String name = base;
        int suffix = 2;
        while (usedParamNames.contains(name)) {
            name = base + suffix++;
        }
        return name;
    }

}
