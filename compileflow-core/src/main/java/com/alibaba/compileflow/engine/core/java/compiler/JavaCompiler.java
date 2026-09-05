/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.java.compiler;

import java.util.List;

/**
 * Thread-safe strategy for compiling generated Java source into a caller-provided output.
 *
 * @author yusu
 */
public interface JavaCompiler {
    /**
     * Compiles one generated Java source unit.
     *
     * @param javaSource    generated source and target class name
     * @param classOutput   compilation output receiver
     * @param options       immutable options for this compilation
     * @throws Exception when compilation or output fails
     */
    default void compile(JavaSource javaSource, ClassOutput classOutput, JavaCompileOptions options) throws Exception {
        compile(List.of(javaSource), classOutput, options);
    }

    /**
     * Compiles one primary source and any definition-owned companion units in one compiler task.
     *
     * @param javaSources  non-empty compilation units, primary first
     * @param classOutput  compilation output receiver
     * @param options      immutable options for this compilation
     * @throws Exception when compilation or output fails
     */
    void compile(List<JavaSource> javaSources, ClassOutput classOutput, JavaCompileOptions options) throws Exception;
}
