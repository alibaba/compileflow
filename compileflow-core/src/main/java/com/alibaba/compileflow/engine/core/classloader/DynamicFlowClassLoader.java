package com.alibaba.compileflow.engine.core.classloader;

/**
 * A marker interface for {@link ClassLoader} implementations that are specifically
 * created by CompileFlow to load dynamically generated process classes.
 * <p>
 * This interface allows components within the engine, particularly those involved in
 * caching and lifecycle management, to reliably identify classloaders that are
 * managed by CompileFlow. This can be useful for tasks such as cache invalidation
 * or resource cleanup without relying on class name heuristics.
 *
 * @author yusu
 */
public interface DynamicFlowClassLoader {
}


