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
package com.alibaba.compileflow.engine.process.builder.compiler;

/**
 * Represents a compiler interface responsible for compiling Java source code into classes.
 *
 * @author yusu
 */
public interface Compiler {

    /**
     * Compiles the given Java source code into a class, using the specified fully qualified class name and class loader.
     *
     * @param fullClassName The fully qualified name of the class to be compiled
     * @param sourceCode    The Java source code to compile
     * @param classLoader   The class loader used for loading the compiled class
     * @return The generated class after successful compilation
     */
    Class<?> compileJavaCode(String fullClassName, String sourceCode, ClassLoader classLoader);

}
