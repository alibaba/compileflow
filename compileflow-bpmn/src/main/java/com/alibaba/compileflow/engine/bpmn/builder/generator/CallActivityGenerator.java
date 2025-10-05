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
package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.CallActivity;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Generator for BPMN CallActivity elements.
 * <p>
 * CallActivity enables modular process design by calling external processes.
 * This implementation generates efficient external process invocation code that
 * follows the same pattern as TBBPM's SubBpmGenerator, with proper parameter
 * mapping and return value handling.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Precise parameter mapping (not full context copying)</li>
 *   <li>Proper return value handling</li>
 *   <li>Type-safe variable conversion using DataType utilities</li>
 * </ul>
 *
 * <p><b>Implementation Strategy:</b></p>
 * <ul>
 *   <li>Use dedicated context for external process (isolated execution)</li>
 *   <li>Map only specified input parameters (not entire context)</li>
 *   <li>Handle return values with proper type conversion</li>
 * </ul>
 *
 * @author yusu
 */
public class CallActivityGenerator extends AbstractBpmnNodeGenerator<CallActivity> {

    public CallActivityGenerator(GeneratorContext context, CallActivity flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);

        String calledElement = flowNode.getCalledElement();

        if (StringUtils.isBlank(calledElement)) {
            throw new IllegalStateException("CallActivity '" + flowNode.getId() + "' must specify calledElement");
        }

        List<IVar> params = flowNode.getParamVars();
        IVar returnVar = flowNode.getReturnVar();

        codeTargetSupport.addBodyLine("{");
        codeTargetSupport.addBodyLine("Map<String, Object> _callActivityContext = new HashMap<>();");

        if (CollectionUtils.isNotEmpty(params)) {
            for (IVar param : params) {
                String var = param.getContextVarName() != null ?
                        DataType.generateTypeConversionCode(getVarType(param.getContextVarName()),
                                DataType.getJavaClass(param.getDataType()), param.getContextVarName())
                        : DataType.getDefaultValueString(DataType.getJavaClass(param.getDataType()),
                        param.getDefaultValue());

                codeTargetSupport.addBodyLine("_callActivityContext.put(\"" + param.getName() + "\", " + var + ");");
            }
        }

        String executeCode = "EngineExecutionContextHolder.engine().execute(ProcessSource.fromCode(\""
                + calledElement + "\"), _callActivityContext)";

        if (returnVar != null) {
            String returnCode = returnVar.getContextVarName() + " = ("
                    + returnVar.getDataType() + ")" + "((java.util.Map<String, Object>)"
                    + executeCode + ".getData()).get(\""
                    + returnVar.getName() + "\");";
            codeTargetSupport.addBodyLine(returnCode);
        } else {
            codeTargetSupport.addBodyLine(executeCode + ";");
        }

        codeTargetSupport.addBodyLine("}");
    }

}
