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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssembledProcessEngineFactoryTest {
    private static AssembledProcessEngineProvider provider(ProcessModelType modelType, ProcessEngine engine) {
        return new AssembledProcessEngineProvider() {
            @Override
            public ProcessEngine createEngine(ProcessEngineConfig config) {
                return engine;
            }

            @Override
            public ProcessEngine createEngine(ProcessEngineConfig config, EngineDependencies dependencies) {
                return engine;
            }

            @Override
            public ProcessModelType getModelType() {
                return modelType;
            }
        };
    }

    @Test
    void rejectsProviderWithNullModelType() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();

        assertThatThrownBy(() -> AssembledProcessEngineFactory.selectProvider(config, List.of(provider(null, null))))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("returned null model type");
    }

    @Test
    void rejectsProviderWithNullEngine() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();

        assertThatThrownBy(() -> AssembledProcessEngineFactory.createEngine(config, EngineAssembly.assemble(config),
                provider(ProcessModelType.TBBPM, null)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("returned null");
    }
}
