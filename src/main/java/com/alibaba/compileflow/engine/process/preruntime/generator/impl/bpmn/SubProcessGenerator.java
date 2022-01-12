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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn;

import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.definition.bpmn.SubProcess;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class SubProcessGenerator extends AbstractBpmnNodeGenerator<SubProcess> {

    public SubProcessGenerator(AbstractProcessRuntime runtime,
                               SubProcess flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        addImportedType(codeTargetSupport, ProcessEngineFactory.class);

        List<IVar> params = flowNode.getParamVars();
        IVar returnVar = flowNode.getReturnVar();
        generateNodeComment(codeTargetSupport);
        codeTargetSupport.addBodyLine("{");

        if (StringUtils.isNotEmpty(flowNode.getSubProcessCode())
            && CollectionUtils.isNotEmpty(flowNode.getAllNodes())) {
            codeTargetSupport.addBodyLine("Map<String, Object> nfSubProcessContext = new HashMap<>();");
            for (IVar param : params) {
                String var = param.getContextVarName() != null ?
                    DataType.getVarTransferString(getVarType(param.getContextVarName()),
                        DataType.getJavaClass(param.getDataType()), param.getContextVarName())
                    : DataType.getDefaultValueString(DataType.getJavaClass(param.getDataType()),
                        param.getDefaultValue());

                codeTargetSupport.addBodyLine("nfSubProcessContext.put(\"" + param.getName() + "\", " + var + ");");
            }
            String noReturnCode = "(ProcessEngineFactory.getProcessEngine()).start(\""
                + flowNode.getSubProcessCode() + "\", nfSubProcessContext)";

            if (returnVar != null) {
                String code = returnVar.getContextVarName()
                    + " = (" + DataType.getJavaObjectType(returnVar.getDataType()) + ")"
                    + "(" + noReturnCode + ").get(\"" + returnVar.getName() + "\");";
                codeTargetSupport.addBodyLine(code);
            } else {
                codeTargetSupport.addBodyLine(noReturnCode + ";");
            }
        } else {
            GeneratorFactory.getInstance().getContainerGenerator(flowNode, runtime).generateCode(codeTargetSupport);
        }

        codeTargetSupport.addBodyLine("}");
    }

}
