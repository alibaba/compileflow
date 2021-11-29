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
package com.alibaba.compileflow.engine.process.preruntime.compiler.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.extension.annotation.Extensions;
import com.alibaba.compileflow.engine.common.util.FileUtils;
import com.alibaba.compileflow.engine.process.preruntime.compiler.FlowClassLoader;

import java.io.File;

/**
 * @author yusu
 */
@Extensions(priority = 500)
public class DefaultFlowClassLoader implements FlowClassLoader {

    @Override
    public Class<?> loadClass(String fullClassName) {
        String dirPath = CompileConstants.FLOW_COMPILE_CLASS_DIR;
        File dirFile = new File(dirPath);
        File classFile = new File(dirFile, fullClassName.replace('.', File.separatorChar) + ".class");
        try {
            byte[] classBytes = FileUtils.readFileToByteArray(classFile);
            return FlowUrlClassLoader.getInstance().defineClass(fullClassName, classBytes);
        } catch (Exception e) {
            throw new CompileFlowException(e);
        }
    }

}
