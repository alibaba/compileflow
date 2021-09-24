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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.definition.tbbpm.SubBpmNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author pin
 * @author yusu
 */
public class SubBpmGenerator extends AbstractTbbpmNodeGenerator<SubBpmNode> {

    private static final String SUB_BPM_METHOD_NAME_PREFIX = "subBpm";

    public SubBpmGenerator(AbstractProcessRuntime runtime,
                           SubBpmNode flowNode) {
        super(runtime, flowNode);
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
        if (subBpmCode.chars().allMatch(c -> Character.isLetterOrDigit(c) || '_' == c)) {
            return SUB_BPM_METHOD_NAME_PREFIX + StringUtils.capitalize(subBpmCode);
        }
        return SUB_BPM_METHOD_NAME_PREFIX + Math.abs(subBpmCode.hashCode());
    }

    private void generateSubBpmMethodCode(CodeTargetSupport codeTargetSupport,
                                          String subBpmMethodName) {
        MethodTarget method = new MethodTarget();
        method.setClassTarget(getClassTarget(codeTargetSupport));
        method.setName(subBpmMethodName);
        doGenerateSubBpmMethodCode(method);
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        classTarget.addMethod(method);
    }

    protected void doGenerateSubBpmMethodCode(CodeTargetSupport codeTargetSupport) {
        addImportedType(codeTargetSupport, ProcessEngineFactory.class);

        List<IVar> params = flowNode.getParamVars();
        IVar returnVar = flowNode.getReturnVar();
        generateNodeComment(codeTargetSupport);
        codeTargetSupport.addBodyLine("{");
        codeTargetSupport.addBodyLine("Map<String, Object> _spContext = new HashMap<>();");
        for (IVar param : params) {
            String var = param.getContextVarName() != null ?
                DataType.getVarTransferString(getVarType(param.getContextVarName()),
                    DataType.getJavaClass(param.getDataType()), param.getContextVarName())
                : DataType.getDefaultValueString(DataType.getJavaClass(param.getDataType()),
                param.getDefaultValue());

            codeTargetSupport.addBodyLine("_spContext.put(\"" + param.getName() + "\", " + var + ");");
        }

        String noReturnCode = "ProcessEngineFactory.getProcessEngine().start(\""
            + flowNode.getSubBpmCode() + "\", _spContext)";

        if (returnVar != null) {
            String code = returnVar.getContextVarName() + " = ("
                + DataType.getJavaObjectType(returnVar.getDataType()) + ")" + "(" + noReturnCode + ").get(\""
                + returnVar.getName() + "\");";
            codeTargetSupport.addBodyLine(code);
        } else {
            codeTargetSupport.addBodyLine(noReturnCode + ";");
        }

        codeTargetSupport.addBodyLine("}");
    }

}
