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

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.ClassWrapper;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import com.alibaba.compileflow.engine.tbbpm.definition.SubBpmNode;

import java.util.List;
import java.util.Map;

/**
 * @author yusu
 */
public class SubBpmGenerator extends AbstractTbbpmNodeGenerator<SubBpmNode> {

    private static final String SUB_BPM_METHOD_NAME_PREFIX = "subBpm";

    public SubBpmGenerator(GeneratorContext context,
                           SubBpmNode flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        if (flowNode.isWaitForTrigger()) {
            if (isTriggerMethod(codeTargetSupport)) {
                codeTargetSupport.addBodyLine("if (trigger) {");
                generateSubBpmMethodCode(codeTargetSupport);
                codeTargetSupport.addBodyLine("} else {");
                codeTargetSupport.addBodyLine("running = false;");
                codeTargetSupport.addBodyLine("} ");
                return;
            }
            return;
        }

        generateSubBpmMethodCode(codeTargetSupport);
    }

    private void generateSubBpmMethodCode(CodeTargetSupport codeTargetSupport) {
        String subBpmMethodName = generateSubBpmMethodName();
        generateSubBpmMethodCode(codeTargetSupport, subBpmMethodName);
        codeTargetSupport.addBodyLine(subBpmMethodName + "();");
    }

    private String generateSubBpmMethodName() {
        String subBpmCode = flowNode.getSubBpmCode();
        subBpmCode = subBpmCode.substring(subBpmCode.lastIndexOf(".") + 1);
        return SUB_BPM_METHOD_NAME_PREFIX + JavaIdentifierUtils.toMethodSuffix(subBpmCode + flowNode.getId());
    }

    private void generateSubBpmMethodCode(CodeTargetSupport codeTargetSupport,
                                          String subBpmMethodName) {
        MethodTarget method = new MethodTarget();
        method.setClassTarget(getClassTarget(codeTargetSupport));
        method.setName(subBpmMethodName);
        method.addException(ClassWrapper.of(Exception.class));
        doGenerateSubBpmMethodCode(method);
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        classTarget.addMethod(method);
    }

    protected void doGenerateSubBpmMethodCode(CodeTargetSupport codeTargetSupport) {
        List<IVar> params = flowNode.getParamVars();
        IVar returnVar = flowNode.getReturnVar();
        generateNodeComment(codeTargetSupport);
        codeTargetSupport.addBodyLine("{");
        codeTargetSupport.addBodyLine("Map<String, Object> _spContext = new HashMap<>();");
        for (IVar param : params) {
            String var = param.getContextVarName() != null ?
                    DataType.generateTypeConversionCode(getVarType(param.getContextVarName()),
                            DataType.getJavaClass(param.getDataType()), param.getContextVarName())
                    : DataType.getDefaultValueString(DataType.getJavaClass(param.getDataType()),
                    param.getDefaultValue());

            codeTargetSupport.addBodyLine("_spContext.put(\"" + param.getName() + "\", " + var + ");");
        }

        String executeCode = "EngineExecutionContextHolder.engine().execute(ProcessSource.fromCode(\""
                + flowNode.getSubBpmCode() + "\"), _spContext)";

        if (returnVar != null) {
            String code = returnVar.getContextVarName() + " = ("
                    + DataType.getJavaClass(returnVar.getDataType()).getName() + ")" + "((" + Map.class.getName()
                    + ")" + executeCode + ").get(\""
                    + returnVar.getName() + "\");";
            codeTargetSupport.addBodyLine(code);
        } else {
            codeTargetSupport.addBodyLine(executeCode + ";");
        }

        codeTargetSupport.addBodyLine("}");
    }

}
