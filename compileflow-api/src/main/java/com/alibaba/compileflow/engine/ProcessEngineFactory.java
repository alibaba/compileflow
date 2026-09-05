/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.ProcessEngineProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Factory for creating {@link ProcessEngine} instances via SPI.
 * Engine creation allocates resources; prefer a singleton per model type.
 *
 * @author yusu
 * @see ProcessEngine
 * @see ProcessEngineConfig
 * @see ProcessEngineProvider
 */
public final class ProcessEngineFactory {
    private ProcessEngineFactory() {
    }

    /**
     * Create an engine from the given configuration.
     *
     * @param config engine configuration, including model type and execution settings
     * @return process engine created by the matching provider
     */
    public static ProcessEngine create(ProcessEngineConfig config) {
        return createEngine(Objects.requireNonNull(config, "config"));
    }

    /**
     * Create an engine with default TBBPM configuration.
     *
     * @return TBBPM process engine
     */
    public static ProcessEngine createTbbpm() {
        return create(ProcessEngineConfig.tbbpm());
    }

    /**
     * Create an engine with default BPMN configuration.
     *
     * @return BPMN process engine
     */
    public static ProcessEngine createBpmn() {
        return create(ProcessEngineConfig.bpmn());
    }

    /**
     * Internal SPI-backed creation.
     */
    private static ProcessEngine createEngine(ProcessEngineConfig config) {
        ProcessEngineProvider provider = findProvider(config);
        return createEngine(config, provider);
    }

    static ProcessEngine createEngine(ProcessEngineConfig config, ProcessEngineProvider provider) {
        ProcessEngine engine = Objects.requireNonNull(provider, "provider").createEngine(config);
        if (engine == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "ProcessEngineProvider returned null: " + provider.getClass().getName());
        }
        return engine;
    }

    /**
     * Resolve provider for a given model type via ServiceLoader.
     */
    private static ProcessEngineProvider findProvider(ProcessEngineConfig config) {
        ClassLoader classLoader = config.getClassLoader();
        try {
            ServiceLoader<ProcessEngineProvider> providers =
                    ServiceLoader.load(ProcessEngineProvider.class, classLoader);
            return selectProvider(config.getModelType(), providers);
        } catch (ServiceConfigurationError error) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Failed to discover ProcessEngineProvider implementations using class loader: " + classLoader, error);
        }
    }

    static ProcessEngineProvider selectProvider(ProcessModelType processModelType,
            Iterable<ProcessEngineProvider> providers) {
        Objects.requireNonNull(processModelType, "processModelType");
        Objects.requireNonNull(providers, "providers");
        List<ProcessEngineProvider> matches = new ArrayList<>();
        for (ProcessEngineProvider provider : providers) {
            ProcessEngineProvider candidate = Objects.requireNonNull(provider, "provider must not be null");
            if (requireModelType(candidate) == processModelType) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "No provider found for process model type: " + processModelType
                    + ". Ensure the corresponding format module is on the classpath.");
        }
        if (matches.size() > 1) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Multiple providers found for process model type " + processModelType + ": " + providerNames(
                            matches));
        }
        return matches.get(0);
    }

    private static ProcessModelType requireModelType(ProcessEngineProvider provider) {
        ProcessModelType modelType = provider.getModelType();
        if (modelType == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "ProcessEngineProvider returned null model type: " + provider.getClass().getName());
        }
        return modelType;
    }

    private static String providerNames(List<ProcessEngineProvider> providers) {
        return providers
            .stream()
            .map(provider -> provider.getClass().getName())
            .sorted()
            .collect(Collectors.joining(", "));
    }
}
