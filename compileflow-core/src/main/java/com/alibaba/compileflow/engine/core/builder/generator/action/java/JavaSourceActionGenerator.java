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
package com.alibaba.compileflow.engine.core.builder.generator.action.java;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaSourceActionHandle;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;

/**
 * Generator for Java class source code actions.
 * This generator uses DynamicClassExecutor to compile and execute Java classes at runtime.
 *
 * @author yusu
 */
public class JavaSourceActionGenerator extends AbstractActionGenerator {

    public JavaSourceActionGenerator(GeneratorContext context, IAction action) {
        super(context, action);
    }

    @Override
    public String getActionType() {
        return "java-source";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        String sourceCode = getSourceCode();
        String method = getMethod();

        String sourceCodeLiteral = generateSourceCodeLiteral(sourceCode);
        String paramTypesArray = generateParameterTypesArray(codeTargetSupport);
        String paramArgsArray = generateParameterArgsArray();

        IVar returnVar = getReturnVar();
        if (returnVar != null && returnVar.getContextVarName() != null) {
            String returnType = DataType.getJavaClass(returnVar.getDataType()).getName();
            codeTargetSupport.addBodyLine(getReturnVarCode() + "(" + returnType + ") " +
                    "EngineExecutionContextHolder.executor().execute(");
        } else {
            codeTargetSupport.addBodyLine("EngineExecutionContextHolder.executor().execute(");
        }

        codeTargetSupport.addBodyLine(sourceCodeLiteral + ",");
        codeTargetSupport.addBodyLine("\"" + method + "\",");
        codeTargetSupport.addBodyLine(paramTypesArray + ",");
        codeTargetSupport.addBodyLine(paramArgsArray + ");");
    }

    private String getSourceCode() {
        return ((JavaSourceActionHandle) actionHandle).getCode();
    }

    private String getMethod() {
        return ((JavaSourceActionHandle) actionHandle).getMethod();
    }

    private String generateSourceCodeLiteral(String sourceCode) {
        return "\"" + StringEscapeUtils.escapeJava(StringEscapeUtils.unescapeJson(sourceCode)) + "\"";
    }

    private String generateParameterTypesArray(CodeTargetSupport codeTargetSupport) {
        List<IVar> params = getMethodParameters();
        if (params.isEmpty()) {
            return "new Class<?>[0]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("new Class<?>[]{");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            IVar param = params.get(i);
            Class<?> paramClass = DataType.getJavaClass(param.getDataType());
            addImportedType(codeTargetSupport, paramClass);
            sb.append(paramClass.getSimpleName()).append(".class");
        }
        sb.append("}");
        return sb.toString();
    }

    private String generateParameterArgsArray() {
        List<IVar> params = getMethodParameters();
        if (params.isEmpty()) {
            return "new Object[0]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("new Object[]{");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            IVar param = params.get(i);
            if (param.getContextVarName() != null) {
                String paramValue = DataType.generateTypeConversionCode(
                        getVarType(param.getContextVarName()),
                        DataType.getJavaClass(param.getDataType()),
                        param.getContextVarName()
                );
                sb.append(paramValue);
            } else {
                String defaultValue = DataType.getDefaultValueString(
                        DataType.getJavaClass(param.getDataType()),
                        param.getDefaultValue()
                );
                sb.append(defaultValue);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        String sourceCodeHash = String.valueOf(Integer.toUnsignedLong(getSourceCode().hashCode()));
        String left = JavaIdentifierUtils.toFieldName(getMethod());
        return left + "_" + sourceCodeHash;
    }

}
