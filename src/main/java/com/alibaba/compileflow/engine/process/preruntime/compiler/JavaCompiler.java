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
package com.alibaba.compileflow.engine.process.preruntime.compiler;

import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;

import java.io.File;

/**
 * Represents an extension point for compiling Java source code into bytecode.
 * Implementations of this interface are responsible for transforming Java source
 * into class files according to the specified options.

 * @author yusu
 */
public interface JavaCompiler extends IExtensionPoint {

    String EXT_COMPILE_CODE = "com.alibaba.compileflow.engine.process.preruntime.compiler.JavaCompiler.compile";

    /**
     * Compiles the given Java source and writes the compiled output to a file.
     *
     * @param javaSource The Java source object containing source code and meta-data
     * @param outputFile The target file to write the compiled class
     * @param compileOption Options for the compilation process
     * @throws Exception Any exception that occurs during the compilation process
     */
    @ExtensionPoint(code = EXT_COMPILE_CODE, reducePolicy = ReducePolicy.FISRT_MATCH)
    void compile(JavaSource javaSource, File outputFile, CompileOption compileOption) throws Exception;

}
