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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl;

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractGenerator implements Generator {

    protected void addImportedType(CodeTargetSupport codeTargetSupport, Class clazz) {
        ClassWrapper classWrapper = ClassWrapper.of(clazz);
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        classTarget.addImportedType(classWrapper);
    }

    protected ClassTarget getClassTarget(CodeTargetSupport codeTargetSupport) {
        return (ClassTarget)codeTargetSupport.getClassTarget();
    }

    protected void addImportedType(CodeTargetSupport codeTargetSupport, String className) {
        ClassWrapper classWrapper = ClassWrapper.of(className);
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        classTarget.addImportedType(classWrapper);
    }

}
