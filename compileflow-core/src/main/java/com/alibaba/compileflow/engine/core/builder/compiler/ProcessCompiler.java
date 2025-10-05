package com.alibaba.compileflow.engine.core.builder.compiler;

import com.alibaba.compileflow.engine.core.builder.compiler.impl.DefaultCompiler;

/**
 * Facade over {@link Compiler}; compilation caching is handled by the engine layer.
 *
 * @author yusu
 */
public class ProcessCompiler {

    private final Compiler COMPILER = new DefaultCompiler();

    /**
     * Compile Java source to a loaded {@link Class}.
     */
    public Class<?> compile(String fullClassName, String javaSource, ClassLoader classLoader) {
        return COMPILER.compileJavaCode(fullClassName, javaSource, classLoader);
    }

}
