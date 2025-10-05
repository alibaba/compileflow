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

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.impl.ScriptActionHandle;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;

import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractScriptActionGenerator extends AbstractActionGenerator {

    public AbstractScriptActionGenerator(GeneratorContext context,
                                         IAction action) {
        super(context, action);
    }

    protected void generateScriptExecuteCode(CodeTargetSupport codeTargetSupport) {
        List<IVar> methodParameters = getMethodParameters();
        for (IVar param : methodParameters) {
            String var = param.getContextVarName() != null ?
                    DataType.generateTypeConversionCode(getVarType(param.getContextVarName()),
                            DataType.getJavaClass(param.getDataType()), param.getContextVarName())
                    : DataType.getDefaultValueString(DataType.getJavaClass(param.getDataType()),
                    param.getDefaultValue());

            codeTargetSupport.addBodyLine("_ScriptContext.put(\"" + param.getName() + "\", " + var + ");");
        }

        String noReturnCode = "ScriptExecutorProvider.getInstance()"
                + ".getScriptExecutor(" + "\"" + getScriptExecutorName() + "\"" + ")"
                + ".execute(\"" + getExpression() + "\", _ScriptContext)";
        IVar returnVar = getReturnVar();
        if (returnVar != null && returnVar.getContextVarName() != null) {
            codeTargetSupport.addBodyLine(getReturnVarCode() + "(" + returnVar.getDataType()
                    + ")" + noReturnCode + ";");
        } else {
            codeTargetSupport.addBodyLine(noReturnCode + ";");
        }
    }

    protected String getExpression() {
        return ((ScriptActionHandle) actionHandle).getExpression();
    }

    protected abstract String getScriptExecutorName();

}
