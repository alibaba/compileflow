package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

/**
 * Service Provider Interface (SPI) for {@link ProcessEngine} implementations.
 * <p>
 * This interface allows for the discovery of different process engine implementations
 * (e.g., for BPMN or TBBPM) at runtime via Java's {@link java.util.ServiceLoader} mechanism.
 * Each implementation of this interface is responsible for creating a {@link ProcessEngine}
 * that supports a specific {@link FlowModelType}.
 *
 * @author yusu
 * @see ProcessEngineFactory
 */
public interface ProcessEngineProvider {

    /**
     * Returns the specific {@link FlowModelType} that this provider supports.
     * This is used by the {@link ProcessEngineFactory} to find the correct provider
     * for a given configuration.
     *
     * @return The non-null flow model type supported by this provider.
     */
    FlowModelType support();

    /**
     * Creates a new {@link ProcessEngine} instance configured according to the provided
     * {@link ProcessEngineConfig}.
     *
     * @param config The configuration for the engine to be created.
     * @return A new, configured {@link ProcessEngine} instance.
     */
    ProcessEngine createEngine(ProcessEngineConfig config);

}
