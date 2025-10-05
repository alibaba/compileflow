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
package com.alibaba.compileflow.engine.core.builder.generator.action;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractRuntimeCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.IActionHandle;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractActionGenerator extends AbstractRuntimeCodeGenerator
        implements ActionGenerator, ActionMethodGenerator {

    public static final boolean GENERATE_ACTION_METHOD_WITH_RETURN_VAR = false;

    protected IActionHandle actionHandle;

    public AbstractActionGenerator(GeneratorContext context, IAction action) {
        super(context);
        this.actionHandle = action.getActionHandle();
    }

    protected String generateParameterCode(CodeTargetSupport codeTargetSupport) {
        List<IVar> methodParameters = getMethodParameters();
        if (CollectionUtils.isNotEmpty(methodParameters)) {
            List<String> params = new ArrayList<>(methodParameters.size());
            for (IVar v : methodParameters) {
                addImportedType(codeTargetSupport, DataType.getJavaClass(v.getDataType()));
                if (v.getContextVarName() != null) {
                    String param = DataType.generateTypeConversionCode(getVarType(v.getContextVarName()),
                            DataType.getJavaClass(v.getDataType()), v.getContextVarName());
                    params.add(param);
                } else {
                    String param = DataType.getDefaultValueString(DataType.getJavaClass(v.getDataType()),
                            v.getDefaultValue());
                    params.add(param);
                }
            }
            return String.join(", ", params);
        }

        return "";
    }

    @Override
    public void generateActionMethodCode(String methodName, CodeTargetSupport codeTargetSupport) {
        if (GENERATE_ACTION_METHOD_WITH_RETURN_VAR) {
            MethodTarget methodTarget = generateMethodCode(codeTargetSupport, methodName,
                    actionHandle.getParamVars(), actionHandle.getReturnVar(), this);
            String returnVarName = getReturnVarName();
            if (StringUtils.isNotBlank(returnVarName)) {
                methodTarget.addBodyLine("return " + returnVarName + ";");
            }
        } else {
            generateMethodCode(codeTargetSupport, methodName,
                    actionHandle.getParamVars(), null, this);
        }

    }

    protected List<IVar> getMethodParameters() {
        return actionHandle.getParamVars();
    }

    protected IVar getReturnVar() {
        return actionHandle.getReturnVar();
    }

    protected String getReturnVarCode() {
        String returnVarName = getReturnVarName();
        if (StringUtils.isNotBlank(returnVarName)) {
            return returnVarName + " = ";
        }
        return "";
    }

    protected String getReturnVarName() {
        IVar returnVar = getReturnVar();
        if (returnVar == null || returnVar.getContextVarName() == null) {
            return "";
        }
        return returnVar.getContextVarName();
    }

}
