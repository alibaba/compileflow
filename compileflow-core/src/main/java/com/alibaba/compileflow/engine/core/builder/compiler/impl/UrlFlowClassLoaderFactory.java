package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.core.builder.compiler.CompiledArtifact;
import com.alibaba.compileflow.engine.core.builder.compiler.FlowClassLoaderFactory;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.apache.commons.lang3.ArrayUtils;

import java.net.URL;

/**
 * @author yusu
 */
@ExtensionRealization(priority = 600)
public class UrlFlowClassLoaderFactory implements FlowClassLoaderFactory {

    @Override
    public ClassLoader getFlowClassLoader(CompiledArtifact artifact, ClassLoader parent) {
        URL[] urls = artifact.getUrls();
        if (ArrayUtils.isEmpty(urls)) {
            return null;
        }
        ClassLoader effectiveParent = ProcessClassLoaderResolver.resolveEffectiveClassLoader(parent);
        return new FlowUrlClassLoader(urls, effectiveParent);
    }

}
