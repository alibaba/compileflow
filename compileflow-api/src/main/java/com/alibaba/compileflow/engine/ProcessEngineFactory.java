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
 * Engine creation allocates resources; reuse an engine within its application resource scope.
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
     * @param config engine resources and execution settings
     * @return process engine created by the installed implementation
     */
    public static ProcessEngine create(ProcessEngineConfig config) {
        Objects.requireNonNull(config, "config");
        return createEngine(config, findProvider(config));
    }

    /**
     * Creates an engine with production defaults and all installed semantic frontends.
     *
     * @return configured process engine
     */
    public static ProcessEngine create() {
        return create(ProcessEngineConfig.defaults());
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
     * Resolves the installed engine implementation via ServiceLoader.
     */
    private static ProcessEngineProvider findProvider(ProcessEngineConfig config) {
        ClassLoader classLoader = config.getClassLoader();
        try {
            ServiceLoader<ProcessEngineProvider> providers =
                    ServiceLoader.load(ProcessEngineProvider.class, classLoader);
            return selectProvider(providers);
        } catch (ServiceConfigurationError error) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Failed to discover ProcessEngineProvider implementations using class loader: " + classLoader, error);
        }
    }

    static ProcessEngineProvider selectProvider(Iterable<ProcessEngineProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<ProcessEngineProvider> matches = new ArrayList<>();
        for (ProcessEngineProvider provider : providers) {
            ProcessEngineProvider candidate = Objects.requireNonNull(provider, "provider must not be null");
            matches.add(candidate);
        }
        if (matches.isEmpty()) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "No ProcessEngineProvider found. Ensure compileflow-core is on the classpath.");
        }
        if (matches.size() > 1) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Multiple ProcessEngineProvider implementations found: " + providerNames(matches));
        }
        return matches.get(0);
    }

    private static String providerNames(List<ProcessEngineProvider> providers) {
        return providers
            .stream()
            .map(provider -> provider.getClass().getName())
            .sorted()
            .collect(Collectors.joining(", "));
    }
}
