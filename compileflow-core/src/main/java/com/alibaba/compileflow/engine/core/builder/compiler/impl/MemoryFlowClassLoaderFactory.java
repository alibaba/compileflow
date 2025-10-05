package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.core.builder.compiler.CompiledArtifact;
import com.alibaba.compileflow.engine.core.builder.compiler.FlowClassLoaderFactory;
import com.alibaba.compileflow.engine.core.classloader.DynamicFlowClassLoader;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

import java.util.Map;

/**
 * @author yusu
 */
@ExtensionRealization()
public class MemoryFlowClassLoaderFactory implements FlowClassLoaderFactory {

    @Override
    public ClassLoader getFlowClassLoader(CompiledArtifact artifact, ClassLoader parent) {
        Map<String, byte[]> bytes = artifact.getClassBytes();
        if (bytes == null) {
            return null;
        }
        ClassLoader effectiveParent = ProcessClassLoaderResolver.resolveEffectiveClassLoader(parent);
        return new FlowMemoryClassLoader(effectiveParent, bytes);
    }

    static class FlowMemoryClassLoader extends ClassLoader implements DynamicFlowClassLoader {
        private final Map<String, byte[]> classBytes;

        FlowMemoryClassLoader(ClassLoader parent, Map<String, byte[]> classBytes) {
            super(parent);
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}


