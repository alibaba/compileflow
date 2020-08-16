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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.tbbpm.LoopProcessNode;
import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang.StringUtils;

/**
 * @author pin
 * @author yusu
 */
public class LoopProcessGenerator extends AbstractTbbpmNodeGenerator<LoopProcessNode> {

    private static final String LOOP_FOR = "loop";
    private static final String LOOP_WHILE = "while";

    public LoopProcessGenerator(AbstractProcessRuntime runtime,
                                LoopProcessNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        String loopType = flowNode.getLoopType();
        String index = flowNode.getIndexVarName();
        if (StringUtils.isEmpty(loopType) || loopType.equals(LOOP_FOR)) {
            String collectionVarName = flowNode.getCollectionVarName();
            String varName = flowNode.getVariableName();
            String varClass = flowNode.getVariableClass();
            if (varClass != null) {
                addImportedType(codeTargetSupport, varClass);
                ClassWrapper classWrapper = ClassWrapper.of(varClass);
                varClass = classWrapper.getShortName();
            } else {
                varClass = "Object";
            }
            String foreachCondition = "for (" + varClass + " " + varName + " : " + collectionVarName + ") {";
            if (StringUtils.isNotEmpty(index)) {
                codeTargetSupport.addBodyLine("int " + index + " = -1;");
                codeTargetSupport.addBodyLine(foreachCondition);
                codeTargetSupport.addBodyLine(index + "++;");
            } else {
                codeTargetSupport.addBodyLine(foreachCondition);
            }
        } else if (loopType.equals(LOOP_WHILE)) {
            String loopExpr = flowNode.getWhileExpression();
            if (StringUtils.isNotEmpty(index)) {
                codeTargetSupport.addBodyLine("int " + index + " = 0;");
                String whileCondition = "for (; " + loopExpr + "; " + index + "++) {";
                codeTargetSupport.addBodyLine(whileCondition);
            } else {
                String whileCondition = "for (; " + loopExpr + ";) {";
                codeTargetSupport.addBodyLine(whileCondition);
            }
        } else {
            throw new CompileFlowException("Unsupported loop type: " + loopType);
        }

        GeneratorFactory.getInstance().getContainerGenerator(flowNode, runtime).generateCode(codeTargetSupport);
        codeTargetSupport.addBodyLine("}");
    }

}
