package com.alibaba.compileflow.engine.core.builder.compiler.impl;

import com.alibaba.compileflow.engine.core.classloader.DynamicFlowClassLoader;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A {@link URLClassLoader} specifically designed for loading dynamically compiled
 * process classes.
 * <p>
 * This classloader is typically instantiated for each compilation, providing a
 * degree of isolation. It loads classes from a given set of URLs, which usually
 * point to the directory where the compiled classes were written. It also exposes
 * the {@code defineClass} method publicly via the {@link DynamicFlowClassLoader}
 * interface, although this specific implementation does not use it.
 *
 * @author yusu
 */
public class FlowUrlClassLoader extends URLClassLoader implements DynamicFlowClassLoader {

    public FlowUrlClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * A convenience method to define a class from its raw byte array.
     * This method is part of the {@link DynamicFlowClassLoader} interface but is
     * also a standard part of {@link ClassLoader}.
     *
     * @param name       The expected binary name of the class.
     * @param classBytes The bytes that make up the class data.
     * @return The resulting {@code Class} object.
     */
    public Class<?> defineClass(String name, byte[] classBytes) {
        return defineClass(name, classBytes, 0, classBytes.length);
    }

}
