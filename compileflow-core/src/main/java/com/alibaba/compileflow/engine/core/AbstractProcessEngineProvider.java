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
package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.AssembledProcessEngineProvider;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base for SPI process-engine providers that assemble a configured engine.
 *
 * @author yusu
 */
public abstract class AbstractProcessEngineProvider implements AssembledProcessEngineProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessEngineProvider.class);

    @Override
    public final ProcessEngine createEngine(ProcessEngineConfig config) {
        return createEngine(config, EngineAssembly.assemble(config));
    }

    @Override
    public final ProcessEngine createEngine(ProcessEngineConfig config, EngineDependencies dependencies) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        EngineDependencies engineDependencies = Objects.requireNonNull(dependencies, "dependencies");
        DefaultProcessEngine engine;
        try {
            ProcessModelType providerModelType = getModelType();
            if (providerModelType == null || engineConfig.getModelType() != providerModelType) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                        "Process engine provider model type mismatch: provider=" + getClass().getName()
                        + ", providerModelType=" + providerModelType + ", configuredModelType="
                        + engineConfig.getModelType());
            }
            engine = Objects.requireNonNull(doCreateEngine(engineConfig, engineDependencies),
                    "doCreateEngine must not return null");
        } catch (RuntimeException | Error startupFailure) {
            try {
                engineDependencies.scriptExecutors().close();
            } catch (RuntimeException | Error closeFailure) {
                startupFailure.addSuppressed(closeFailure);
            }
            throw startupFailure;
        }

        LOGGER.info("ProcessEngine created: type={}", engineConfig.getModelType());

        return engine;
    }

    protected abstract DefaultProcessEngine doCreateEngine(ProcessEngineConfig config, EngineDependencies dependencies);
}
