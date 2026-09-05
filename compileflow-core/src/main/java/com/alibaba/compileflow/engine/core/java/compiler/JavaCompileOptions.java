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

import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.util.Objects;

/**
 * Immutable options for one generated-Java compilation.
 *
 * @param debugSymbols      emitted debug information
 * @param parentClassLoader application class loader used to resolve dependencies
 * @author yusu
 */
public record JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols debugSymbols, ClassLoader parentClassLoader) {
    /**
     * Java language and bytecode level used for generated code.
     */
    public static final String JAVA_RELEASE = "17";

    public JavaCompileOptions {
        Objects.requireNonNull(debugSymbols, "debugSymbols");
    }

    /**
     * Returns the standard generated-code compilation options.
     *
     * @return default options
     */
    public static JavaCompileOptions defaults() {
        return new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, null);
    }
}
