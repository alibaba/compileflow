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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.action;

import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.IActionHandle;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.AbstractRuntimeGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractActionGenerator extends AbstractRuntimeGenerator
    implements ActionGenerator, ActionMethodGenerator {

    protected IActionHandle actionHandle;

    public AbstractActionGenerator(AbstractProcessRuntime runtime, IAction action) {
        super(runtime);
        this.actionHandle = action.getActionHandle();
    }

    protected String generateParameterCode(CodeTargetSupport codeTargetSupport) {
        List<IVar> methodParameters = getMethodParameters();
        if (CollectionUtils.isNotEmpty(methodParameters)) {
            List<String> params = new ArrayList<>(methodParameters.size());
            for (IVar v : methodParameters) {
                addImportedType(codeTargetSupport, DataType.getJavaClass(v.getDataType()));
                if (v.getContextVarName() != null) {
                    String param = DataType.getVarTransferString(getVarType(v.getContextVarName()),
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
    public void generateActionMethodCode(CodeTargetSupport codeTargetSupport) {
        generateMethodCode(codeTargetSupport, generateActionMethodName(codeTargetSupport), this);
    }

    protected List<IVar> getMethodParameters() {
        return actionHandle.getParamVars();
    }

    protected IVar getReturnVar() {
        return actionHandle.getReturnVar();
    }

    protected String getReturnVarCode() {
        IVar returnVar = getReturnVar();
        if (returnVar == null || returnVar.getContextVarName() == null) {
            return "";
        }
        return returnVar.getContextVarName() + " = ";
    }

}
