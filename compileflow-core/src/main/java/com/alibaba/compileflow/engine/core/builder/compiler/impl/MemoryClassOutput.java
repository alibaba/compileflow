package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.core.builder.compiler.ClassOutput;
import com.alibaba.compileflow.engine.core.builder.compiler.CompileOption;
import com.alibaba.compileflow.engine.core.builder.compiler.CompiledArtifact;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 200)
public class MemoryClassOutput implements ClassOutput {

    private Map<String, byte[]> classes;

    @Override
    public void open(String fullClassName, CompileOption option) {
        this.classes = new HashMap<>();
    }

    @Override
    public void writeClass(String className, byte[] bytes) {
        classes.put(className, bytes);
    }

    @Override
    public CompiledArtifact finish() {
        final Map<String, byte[]> snapshot = Collections.unmodifiableMap(new HashMap<>(classes));
        return new MemoryCompiledArtifact(snapshot);
    }

    private static class MemoryCompiledArtifact implements CompiledArtifact {
        private final Map<String, byte[]> classBytes;

        MemoryCompiledArtifact(Map<String, byte[]> classBytes) {
            this.classBytes = classBytes;
        }

        @Override
        public URL[] getUrls() {
            return null;
        }

        @Override
        public Map<String, byte[]> getClassBytes() {
            return classBytes;
        }

        @Override
        public void close() { /* GC managed */ }
    }
}


