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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractProcessEngineProviderTest {
    private static final ScriptProgramSpec QL_PROGRAM = new ScriptProgramSpec("qlexpress", "1 + 1", List.of(), null);

    @Test
    void closesEngineOwnedDependenciesWhenConstructionFails() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        ScriptExecutor builtInQl = dependencies.scriptExecutors().getScriptExecutor("qlexpress");
        AbstractProcessEngineProvider provider = new AbstractProcessEngineProvider() {
            @Override
            public ProcessModelType getModelType() {
                return ProcessModelType.TBBPM;
            }

            @Override
            protected DefaultProcessEngine doCreateEngine(ProcessEngineConfig ignoredConfig,
                    EngineDependencies ignoredDependencies) {
                throw new IllegalStateException("construction failed");
            }
        };

        assertThatThrownBy(() -> provider.createEngine(config, dependencies))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("construction failed");
        assertThatThrownBy(() -> builtInQl.compile(QL_PROGRAM))
            .isInstanceOf(ScriptException.class)
            .hasMessage("QL script executor is closed");
    }

    @Test
    void rejectsNullEngineAndClosesEngineOwnedDependencies() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        ScriptExecutor builtInQl = dependencies.scriptExecutors().getScriptExecutor("qlexpress");
        AbstractProcessEngineProvider provider = new AbstractProcessEngineProvider() {
            @Override
            public ProcessModelType getModelType() {
                return ProcessModelType.TBBPM;
            }

            @Override
            protected DefaultProcessEngine doCreateEngine(ProcessEngineConfig ignoredConfig,
                    EngineDependencies ignoredDependencies) {
                return null;
            }
        };

        assertThatThrownBy(() -> provider.createEngine(config, dependencies))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("doCreateEngine must not return null");
        assertThatThrownBy(() -> builtInQl.compile(QL_PROGRAM))
            .isInstanceOf(ScriptException.class)
            .hasMessage("QL script executor is closed");
    }

    @Test
    void rejectsMismatchedModelTypeAndClosesEngineOwnedDependencies() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        ScriptExecutor builtInQl = dependencies.scriptExecutors().getScriptExecutor("qlexpress");
        AbstractProcessEngineProvider provider = new AbstractProcessEngineProvider() {
            @Override
            public ProcessModelType getModelType() {
                return ProcessModelType.BPMN;
            }

            @Override
            protected DefaultProcessEngine doCreateEngine(ProcessEngineConfig ignoredConfig,
                    EngineDependencies ignoredDependencies) {
                throw new AssertionError("mismatched provider must not create an engine");
            }
        };

        assertThatThrownBy(() -> provider.createEngine(config, dependencies))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("provider model type mismatch");
        assertThatThrownBy(() -> builtInQl.compile(QL_PROGRAM))
            .isInstanceOf(ScriptException.class)
            .hasMessage("QL script executor is closed");
    }
}
