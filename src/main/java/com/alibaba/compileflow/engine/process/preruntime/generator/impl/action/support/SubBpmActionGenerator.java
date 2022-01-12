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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support;

import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.impl.SubBpmActionHandle;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author yusu
 */
public class SubBpmActionGenerator extends AbstractActionGenerator {

    private static final String SUB_BPM_METHOD_NAME_PREFIX = "subBpm";

    public SubBpmActionGenerator(AbstractProcessRuntime runtime,
                                 IAction action) {
        super(runtime, action);
    }

    @Override
    public String getActionType() {
        return "subBpm";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (actionHandle == null) {
            codeTargetSupport.addBodyLine("//TODO");
        }

        addImportedType(codeTargetSupport, ProcessEngineFactory.class);

        List<IVar> params = getMethodParameters();
        IVar returnVar = getReturnVar();
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

        String noReturnCode = "ProcessEngineFactory.getProcessEngine().start(\"" + getSubBpmCode()
            + "\", _spContext)";

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

    private String getSubBpmCode() {
        return ((SubBpmActionHandle)actionHandle).getSubBpmCode();
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        String subBpmCode = getSubBpmCode();
        subBpmCode = subBpmCode.substring(subBpmCode.lastIndexOf(".") + 1);
        if (subBpmCode.chars().allMatch(c -> Character.isLetterOrDigit(c) || '_' == c)) {
            return SUB_BPM_METHOD_NAME_PREFIX + StringUtils.capitalize(subBpmCode);
        }
        return SUB_BPM_METHOD_NAME_PREFIX + Math.abs(subBpmCode.hashCode() % Integer.MAX_VALUE);
    }

}
