package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.process.builder.compiler.Compiler;
import com.alibaba.compileflow.engine.process.builder.compiler.impl.CompilerImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程编译器 (Process Compiler)
 * 职责：
 * 1. 调用底层编译器将 Java 源代码字符串编译成 Class 对象。
 * 2. 缓存编译结果。
 * @author yusu
 */
public class ProcessCompiler {

    private static final Compiler COMPILER = new CompilerImpl();

    private final static Map<String, Class<?>> COMPILED_CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * @param processCode   流程编码，用作缓存的 key
     * @param fullClassName 要编译的类的完整名称
     * @param javaSource    Java 源代码
     * @param classLoader   类加载器
     * @return 编译好的 Class 对象
     */
    public Class<?> getOrCompile(String processCode, String fullClassName, String javaSource, ClassLoader classLoader) {
        return COMPILED_CLASS_CACHE.computeIfAbsent(processCode,
                c -> COMPILER.compileJavaCode(fullClassName, javaSource, classLoader));
    }

    /**
     * 强制重新编译。
     */
    public Class<?> recompile(String processCode, String fullClassName, String javaSource, ClassLoader classLoader) {
        Class<?> compiledClass = COMPILER.compileJavaCode(fullClassName, javaSource, classLoader);
        COMPILED_CLASS_CACHE.put(processCode, compiledClass);
        return compiledClass;
    }

    /**
     * 从缓存中获取已编译的 Class，如果不存在则返回 null。
     */
    public Class<?> getCompiledClass(String processCode) {
        return COMPILED_CLASS_CACHE.get(processCode);
    }

}
