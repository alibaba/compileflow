package com.alibaba.compileflow.engine.process.preruntime.compiler.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author yusu
 */
public class FlowUrlClassLoader extends URLClassLoader {

    private static volatile FlowUrlClassLoader instance = null;

    public FlowUrlClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public static FlowUrlClassLoader getInstance() {
        if (instance == null) {
            synchronized (FlowUrlClassLoader.class) {
                if (instance == null) {
                    try {
                        URL url = new URL("file:///" + CompileConstants.FLOW_COMPILE_CLASS_DIR);
                        instance = new FlowUrlClassLoader(new URL[]{url},
                            FlowUrlClassLoader.class.getClassLoader());
                    } catch (Exception e) {
                        throw new CompileFlowException(e);
                    }
                }
            }
        }
        return instance;
    }

    public void clearCache() {
        instance = null;
    }

    public Class<?> defineClass(String name, byte[] classBytes) {
        return defineClass(name, classBytes, 0, classBytes.length);
    }

}
