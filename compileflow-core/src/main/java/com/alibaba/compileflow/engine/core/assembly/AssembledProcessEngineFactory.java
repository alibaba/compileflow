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
package com.alibaba.compileflow.engine.core.assembly;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Creates an official core engine from dependencies resolved by an application composition root.
 *
 * @author yusu
 */
public final class AssembledProcessEngineFactory {
    private AssembledProcessEngineFactory() {
    }

    /**
     * Creates an engine without mutating it after construction.
     *
     * @param config       validated engine configuration
     * @param dependencies complete immutable construction dependencies
     * @return newly created engine
     */
    public static ProcessEngine create(ProcessEngineConfig config, EngineDependencies dependencies) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        EngineDependencies engineDependencies = Objects.requireNonNull(dependencies, "dependencies");
        ClassLoader classLoader = engineConfig.getClassLoader();
        try {
            ServiceLoader<AssembledProcessEngineProvider> providers =
                    ServiceLoader.load(AssembledProcessEngineProvider.class, classLoader);
            AssembledProcessEngineProvider provider = selectProvider(engineConfig, providers);
            return createEngine(engineConfig, engineDependencies, provider);
        } catch (ServiceConfigurationError error) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Failed to discover assembled engine providers using class loader: " + classLoader, error);
        }
    }

    static AssembledProcessEngineProvider selectProvider(ProcessEngineConfig config,
            Iterable<AssembledProcessEngineProvider> providers) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(providers, "providers");
        List<AssembledProcessEngineProvider> matches = new ArrayList<>();
        for (AssembledProcessEngineProvider provider : providers) {
            AssembledProcessEngineProvider candidate = Objects.requireNonNull(provider, "provider must not be null");
            if (requireModelType(candidate) == engineConfig.getModelType()) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            throw missingProvider(engineConfig);
        }
        if (matches.size() > 1) {
            throw ambiguousProviders(engineConfig, matches);
        }
        return matches.get(0);
    }

    static ProcessEngine createEngine(ProcessEngineConfig config, EngineDependencies dependencies,
            AssembledProcessEngineProvider provider) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        EngineDependencies engineDependencies = Objects.requireNonNull(dependencies, "dependencies");
        AssembledProcessEngineProvider engineProvider = Objects.requireNonNull(provider, "provider");
        ProcessEngine engine = engineProvider.createEngine(engineConfig, engineDependencies);
        if (engine == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "AssembledProcessEngineProvider returned null: " + engineProvider.getClass().getName());
        }
        return engine;
    }

    private static ProcessModelType requireModelType(AssembledProcessEngineProvider provider) {
        ProcessModelType modelType = provider.getModelType();
        if (modelType == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "AssembledProcessEngineProvider returned null model type: " + provider.getClass().getName());
        }
        return modelType;
    }

    private static CompileFlowException.ConfigurationException missingProvider(ProcessEngineConfig config) {
        return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                "No provider found for flow model type: " + config.getModelType()
                + ". Ensure the corresponding format module is on the classpath.");
    }

    private static CompileFlowException.ConfigurationException ambiguousProviders(ProcessEngineConfig config,
            List<AssembledProcessEngineProvider> providers) {
        return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                "Multiple providers found for flow model type " + config.getModelType() + ": "
                + providers
                            .stream()
                            .map(provider -> provider.getClass().getName())
                            .sorted()
                            .collect(Collectors.joining(", ")));
    }
}
