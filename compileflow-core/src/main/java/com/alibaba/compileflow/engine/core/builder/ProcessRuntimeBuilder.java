package com.alibaba.compileflow.engine.core.builder;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;

/**
 * A self-contained, independent build toolchain for creating ProcessRuntime instances.
 * It takes all necessary components (parser, generator, compiler) as inputs and
 * orchestrates the build pipeline.
 *
 * @author yusu
 * @see ProcessRuntime
 */
public interface ProcessRuntimeBuilder {

    /**
     * Builds a complete, executable {@link ProcessRuntime} from a given process source.
     * <p>
     * This method orchestrates the entire compilation pipeline:
     * <ol>
     *   <li>Parsing the {@link ProcessSource} into an in-memory {@code FlowModel}.</li>
     *   <li>Generating the Java source code from the model.</li>
     *   <li>Compiling the Java source into a {@code .class} file.</li>
     *   <li>Loading the compiled class and packaging it with the model into a {@link ProcessRuntime}.</li>
     * </ol>
     *
     * @param processSource The source of the process definition.
     * @param classLoader   The {@link ClassLoader} to use for compiling the generated
     *                      Java code and loading the final class.
     * @return A new, executable {@link ProcessRuntime} instance.
     */
    ProcessRuntime buildProcessRuntime(ProcessSource processSource, ClassLoader classLoader);

}
