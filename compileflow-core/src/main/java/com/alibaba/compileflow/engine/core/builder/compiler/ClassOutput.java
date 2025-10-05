package com.alibaba.compileflow.engine.core.builder.compiler;

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * An abstraction for handling the output of a Java compilation.
 * <p>
 * Implementations of this interface act as a destination for the compiled
 * {@code .class} files, which can be written to disk, stored in memory, or
 * handled in any other custom way.
 *
 * @author yusu
 * @see com.alibaba.compileflow.engine.core.builder.compiler.impl.MemoryClassOutput
 * @see com.alibaba.compileflow.engine.core.builder.compiler.impl.DiskClassOutput
 */
public interface ClassOutput extends Extension {

    String EXT_CLASS_OUTPUT_CODE = "com.alibaba.compileflow.engine.core.builder.compiler.ClassOutput.open";

    /**
     * Prepares the output destination for a new compilation session.
     * This method is called before any classes are written.
     *
     * @param fullClassName The fully qualified name of the primary class being compiled.
     * @param option        The compilation options for this session.
     * @throws Exception if an error occurs during initialization.
     */
    @ExtensionPoint(code = EXT_CLASS_OUTPUT_CODE)
    void open(String fullClassName, CompileOption option) throws Exception;

    /**
     * Writes the bytecode of a compiled class to the destination.
     *
     * @param className The fully qualified name of the class.
     * @param bytes     The raw bytecode of the class.
     * @throws Exception if an error occurs during writing.
     */
    void writeClass(String className, byte[] bytes) throws Exception;

    /**
     * Finalizes the compilation session and returns a {@link CompiledArtifact}
     * that provides access to the compiled classes.
     *
     * @return A {@link CompiledArtifact} representing the result of the compilation.
     * @throws Exception if an error occurs during finalization.
     */
    CompiledArtifact finish() throws Exception;
}


