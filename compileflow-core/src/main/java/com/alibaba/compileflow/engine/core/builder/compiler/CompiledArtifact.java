package com.alibaba.compileflow.engine.core.builder.compiler;

import java.net.URL;
import java.util.Map;

/**
 * A unified abstraction that represents compiled outputs for a flow.
 * Implementations may wrap disk-based artifacts (URLs) or in-memory
 * class bytes. Unsupported accessors may return {@code null}.
 *
 * @author yusu
 */
public interface CompiledArtifact extends AutoCloseable {

    /**
     * Returns URL entries that can be used by a URLClassLoader to load classes.
     * Implementations that do not rely on URLs may return {@code null}.
     */
    URL[] getUrls();

    /**
     * Returns a map of fully qualified class name to class byte array for
     * in-memory class loading scenarios. Disk-based implementations may return {@code null}.
     */
    Map<String, byte[]> getClassBytes();

    /**
     * Releases any resources held by this artifact. Disk-based implementations
     * may be no-op if the output is intended to persist.
     */
    @Override
    void close();

}


