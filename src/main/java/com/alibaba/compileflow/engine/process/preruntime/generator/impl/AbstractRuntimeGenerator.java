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

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.common.util.VarUtils;
import com.alibaba.compileflow.engine.definition.common.EndElement;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.MethodConstants;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * @author yusu
 */
public abstract class AbstractRuntimeGenerator extends AbstractGenerator {

    protected AbstractProcessRuntime runtime;

    public AbstractRuntimeGenerator(AbstractProcessRuntime runtime) {
        this.runtime = runtime;
    }

    protected Class<?> getVarType(String varName) {
        List<IVar> vars = runtime.getVars();
        Class<?> clazz = vars.stream().filter(var -> var.getName().equals(varName))
            .findFirst().map(IVar::getDataType).map(DataType::getJavaClass)
            .orElse(null);
        return clazz == null ? Object.class : clazz;
    }

    protected boolean isExecuteMethod(CodeTargetSupport codeTargetSupport) {
        return MethodConstants.EXECUTE_METHOD_NAME.equals(codeTargetSupport.getName());
    }

    protected boolean isTriggerMethod(CodeTargetSupport codeTargetSupport) {
        return MethodConstants.TRIGGER_METHOD_NAME.equals(codeTargetSupport.getName());
    }

    protected void generateMethodCode(CodeTargetSupport codeTargetSupport, String methodName,
                                      Generator methodBodyGenerator) {
        generateMethodCode(codeTargetSupport, methodName, null, methodBodyGenerator);
    }

    protected void generateMethodCode(CodeTargetSupport codeTargetSupport, String methodName,
                                      List<IVar> paramVars, Generator methodBodyGenerator) {
        generateMethodCode(codeTargetSupport, methodName, paramVars, null, methodBodyGenerator);
    }

    protected void generateMethodCode(CodeTargetSupport codeTargetSupport, String methodName,
                                      List<IVar> paramVars, IVar returnVar, Generator methodBodyGenerator) {
        MethodTarget method = new MethodTarget();
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        method.setClassTarget(classTarget);
        method.setName(methodName);
        method.addException(ClassWrapper.of(Exception.class));

        if (CollectionUtils.isNotEmpty(paramVars)) {
            for (IVar v : paramVars) {
                ClassWrapper pType = ClassWrapper.of(v.getDataType());
                method.addParameter(ParamTarget.of(pType, getMethodParamName(v)));
            }
        }
        if (returnVar != null) {
            method.setReturnType(ClassWrapper.of(returnVar.getDataType()));
        }

        classTarget.addMethod(method);
        methodBodyGenerator.generateCode(method);
    }

    protected boolean isEndNode(String id) {
        return runtime.getNodeById(id) instanceof EndElement;
    }

    protected String getMethodParamName(IVar var) {
        if (isLegalVarName(var.getContextVarName())) {
            return var.getContextVarName();
        }
        if (isLegalVarName(var.getName())) {
            return var.getName();
        }
        return "p" + var.getName().hashCode();
    }

    private boolean isLegalVarName(String varName) {
        return VarUtils.isLegalVarName(varName);
    }

}
