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
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineAssemblyConstructionTest {
    private static final ScriptProgramSpec PROGRAM = new ScriptProgramSpec("qlexpress", "1 + 1", List.of(), null);

    @Test
    void closesOwnedDependenciesWhenFrontendDiscoveryFails(@TempDir Path root) throws Exception {
        Path descriptor = root.resolve(
                "META-INF/services/com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompilerProvider");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "does.not.Exist\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, getClass().getClassLoader())) {
            ProcessEngineConfig config =
                    ProcessEngineConfig.builder().classLoader(loader).discoverPlugins(false).build();
            EngineDependencies dependencies = EngineAssembly.assemble(config);
            ScriptExecutor executor = dependencies.scriptExecutors().getScriptExecutor("qlexpress");
            assertThatThrownBy(() -> EngineAssembly.create(config, dependencies))
                .isInstanceOf(CompileFlowException.ConfigurationException.class);
            assertThatThrownBy(() -> executor.compile(PROGRAM))
                .isInstanceOf(ScriptException.class)
                .hasMessage("QL script executor is closed");
        }
    }

    @Test
    void createdEngineOwnsTheAssembledResources() {
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).build();
        EngineDependencies dependencies = EngineAssembly.assemble(config);
        ScriptExecutor executor = dependencies.scriptExecutors().getScriptExecutor("qlexpress");
        ProcessEngine engine = EngineAssembly.create(config, dependencies);
        engine.close();
        engine.close();
        assertThatThrownBy(() -> executor.compile(PROGRAM)).isInstanceOf(ScriptException.class);
    }
}
