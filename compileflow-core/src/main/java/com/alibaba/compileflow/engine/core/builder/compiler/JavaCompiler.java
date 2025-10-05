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
package com.alibaba.compileflow.engine.core.builder.compiler;

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * Represents an extension point for compiling Java source code into bytecode.
 * Implementations of this interface are responsible for transforming Java source
 * into class files according to the specified options.
 *
 * @author yusu
 */
public interface JavaCompiler extends Extension {

    String EXT_COMPILE_CODE = "com.alibaba.compileflow.engine.core.builder.compiler.JavaCompiler.compile";

    /**
     * Compiles the given Java source and emits compiled output through the provided ClassOutput.
     *
     * @param javaSource    The Java source object containing source code and meta-data
     * @param classOutput   The receiver for compiled classes (disk or in-memory)
     * @param compileOption Options for the compilation process
     * @throws Exception Any exception that occurs during the compilation process
     */
    @ExtensionPoint(code = EXT_COMPILE_CODE)
    void compile(JavaSource javaSource, ClassOutput classOutput, CompileOption compileOption) throws Exception;

}
