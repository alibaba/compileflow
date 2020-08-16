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

import com.alibaba.compileflow.engine.definition.bpmn.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.AbstractWrapperGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang.StringUtils;

/**
 * @author yusu
 */
public class StandardLoopCharacteristicsGenerator extends AbstractWrapperGenerator {

    private StandardLoopCharacteristics loopCharacteristics;

    public StandardLoopCharacteristicsGenerator(AbstractProcessRuntime runtime,
                                                StandardLoopCharacteristics loopCharacteristics,
                                                Generator generator) {
        super(runtime, generator);
        this.loopCharacteristics = loopCharacteristics;
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        String index = loopCharacteristics.getIndexVar();
        String collectionVarName = loopCharacteristics.getCollection();
        String varName = loopCharacteristics.getElementVar();
        String varClass = loopCharacteristics.getElementVarClass();
        if (varClass != null) {
            addImportedType(codeTargetSupport, varClass);
            ClassWrapper classWrapper = ClassWrapper.of(varClass);
            varClass = classWrapper.getShortName();
        } else {
            varClass = "Object";
        }
        String foreachCondition = "for (" + varClass + " " + varName + " : " + collectionVarName + ") {";
        String loopCondition = loopCharacteristics.getLoopCondition();
        if (Boolean.TRUE.equals(loopCharacteristics.getTestBefore()) && StringUtils.isNotEmpty(loopCondition)) {
            generateLoopConditionCode(codeTargetSupport, loopCondition);
        }
        if (StringUtils.isNotEmpty(index)) {
            codeTargetSupport.addBodyLine("int " + index + " = -1;");
            codeTargetSupport.addBodyLine(foreachCondition);
            codeTargetSupport.addBodyLine(index + "++;");
        } else {
            codeTargetSupport.addBodyLine(foreachCondition);
        }

        generator.generateCode(codeTargetSupport);
        if (Boolean.FALSE.equals(loopCharacteristics.getTestBefore()) && StringUtils.isNotEmpty(loopCondition)) {
            generateLoopConditionCode(codeTargetSupport, loopCondition);
        }

        codeTargetSupport.addBodyLine("}");
    }

    private void generateLoopConditionCode(CodeTargetSupport codeTargetSupport, String loopCondition) {
        codeTargetSupport.addBodyLine("if (!(" + loopCondition + ")) {");
        codeTargetSupport.addBodyLine("return;");
    }

}