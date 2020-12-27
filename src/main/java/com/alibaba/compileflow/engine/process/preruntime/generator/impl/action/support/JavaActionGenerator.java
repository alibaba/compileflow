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

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.impl.JavaActionHandle;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wuxiang
 * @author yusu
 */
public class JavaActionGenerator extends AbstractActionGenerator {

    public JavaActionGenerator(AbstractProcessRuntime runtime,
                               IAction action) {
        super(runtime, action);
    }

    @Override
    public String getActionType() {
        return "java";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (actionHandle == null) {
            codeTargetSupport.addBodyLine("//TODO");
        }

        String clazz = getClazz();
        addImportedType(codeTargetSupport, clazz);
        ClassWrapper classWrapper = ClassWrapper.of(clazz);
        codeTargetSupport.addBodyLine(
            getReturnVarCode() + "((" + classWrapper.getShortName()
                + ")ObjectFactory.getInstance(\"" + clazz + "\"))"
                + "." + getMethod() + "(" + generateParameterCode(codeTargetSupport) + ");");
    }

    private String getClazz() {
        return ((JavaActionHandle)actionHandle).getClazz();
    }

    private String getMethod() {
        return ((JavaActionHandle)actionHandle).getMethod();
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        String clazz = getClazz();
        String className = clazz.substring(clazz.lastIndexOf(".") + 1);
        return StringUtils.uncapitalize(className) + StringUtils.capitalize(getMethod());
    }

}
