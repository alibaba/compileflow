package com.alibaba.compileflow.engine.core.builder.compiler;

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * An extension point for creating a {@link ClassLoader} capable of loading
 * the classes of a dynamically compiled process flow.
 * <p>
 * Implementations of this factory are responsible for constructing a ClassLoader
 * that can find and load all classes contained within a {@link CompiledArtifact}.
 * This allows for custom classloading strategies, such as loading from memory,
 * a specific directory, or a remote location.
 *
 * @author yusu
 */
public interface FlowClassLoaderFactory extends Extension {

    String EXT_FLOW_CLASS_LOADER_CODE = "com.alibaba.compileflow.engine.core.builder.compiler.FlowClassLoaderFactory.getFlowClassLoader";

    /**
     * Creates a new ClassLoader for a given compiled artifact.
     *
     * @param artifact The compiled artifact, which contains the bytecode and location
     *                 of the compiled classes.
     * @param parent   The parent ClassLoader that the new ClassLoader should delegate to
     *                 for resolving any dependencies not present in the artifact.
     * @return A new {@link ClassLoader} instance capable of loading the process class.
     */
    @ExtensionPoint(code = EXT_FLOW_CLASS_LOADER_CODE)
    ClassLoader getFlowClassLoader(CompiledArtifact artifact, ClassLoader parent);

}
