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

import com.alibaba.compileflow.engine.core.builder.compiler.impl.DefaultCompiler;

/**
 * An interface for a generic Java source code compiler.
 * <p>
 * Implementations of this interface are responsible for the low-level task of
 * transforming a Java source string into a loaded {@link Class} object.
 *
 * @author yusu
 * @see ProcessCompiler
 * @see DefaultCompiler
 */
public interface Compiler {

    /**
     * Compiles a Java source string into a loaded {@link Class}.
     *
     * @param fullClassName The fully qualified name of the class to be compiled (e.g., "com.mycompany.MyProcess").
     * @param sourceCode    The Java source code to compile.
     * @param classLoader   The parent {@link ClassLoader} to use for loading the
     *                      newly compiled class and its dependencies.
     * @return The loaded {@link Class} object.
     */
    Class<?> compileJavaCode(String fullClassName, String sourceCode, ClassLoader classLoader);

}
