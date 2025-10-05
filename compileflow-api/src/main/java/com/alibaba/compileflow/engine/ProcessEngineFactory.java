/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

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
     */
    public static ProcessEngine create(ProcessEngineConfig config) {
        return createEngine(config);
    }

    /**
     * Create an engine with default TBBPM configuration.
     */
    public static ProcessEngine createTbbpm() {
        return create(ProcessEngineConfig.tbbpm());
    }

    /**
     * Create an engine with default BPMN configuration.
     */
    public static ProcessEngine createBpmn() {
        return create(ProcessEngineConfig.bpmn());
    }

    /**
     * Internal SPI-backed creation.
     */
    private static ProcessEngine createEngine(ProcessEngineConfig config) {
        ProcessEngineProvider provider = findProvider(config.getModelType());
        return provider.createEngine(config);
    }

    /**
     * Resolve provider for a given model type via ServiceLoader.
     */
    private static ProcessEngineProvider findProvider(FlowModelType flowModelType) {
        ServiceLoader<ProcessEngineProvider> serviceLoader = ServiceLoader.load(ProcessEngineProvider.class);

        return StreamSupport.stream(serviceLoader.spliterator(), false)
                .filter(p -> p.support() == flowModelType)
                .findFirst()
                .orElseThrow(() -> new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_004,
                        "No provider found for flow model type: " + flowModelType +
                                ". Please ensure the corresponding module (e.g., compileflow-bpmn) is on the classpath."
                ));
    }

}
