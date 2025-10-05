package com.alibaba.compileflow.engine;

/**
 * Provides tooling and introspection services for development and debugging.
 * <p>
 * This interface offers methods to access the internal representations of flows
 * without executing them. It is primarily intended for use in development tools,
 * IDE plugins, or testing environments to inspect, validate, and generate code.
 *
 * @param <T> The specific type of {@link FlowModel} this service operates on.
 * @author yusu
 */
public interface ProcessToolingService<T extends FlowModel> {

    /**
     * Loads and validates a flow model from the given source.
     * <p>
     * Returns the in-memory object representation without compiling or caching
     * it in the main engine runtime.
     *
     * @param source The {@link ProcessSource} of the process definition.
     * @return The fully loaded and validated {@link FlowModel}.
     */
    T loadFlowModel(ProcessSource source);

    /**
     * Generates the executable Java source code for a given process definition.
     * <p>
     * This allows developers to inspect the code that the engine will compile
     * and execute.
     *
     * @param source The {@link ProcessSource} of the process definition.
     * @return The generated Java code as a string.
     */
    String generateJavaCode(ProcessSource source);

}
