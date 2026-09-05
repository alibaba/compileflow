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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.ProcessEngineProvider;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessEngineFactoryTest {
    private static ProcessEngineProvider provider(ProcessModelType type) {
        return provider(type, null, true);
    }

    private static ProcessEngineProvider provider(ProcessModelType type, ProcessEngine engine) {
        return provider(type, engine, false);
    }

    private static ProcessEngineProvider provider(ProcessModelType type, ProcessEngine engine, boolean selectionOnly) {
        return new ProcessEngineProvider() {
            @Override
            public ProcessModelType getModelType() {
                return type;
            }

            @Override
            public ProcessEngine createEngine(ProcessEngineConfig config) {
                if (selectionOnly) {
                    throw new UnsupportedOperationException("selection test only");
                }
                return engine;
            }
        };
    }

    @Test
    void selectsTheOnlyMatchingProvider() {
        ProcessEngineProvider tbbpm = provider(ProcessModelType.TBBPM);
        ProcessEngineProvider bpmn = provider(ProcessModelType.BPMN);

        assertThat(ProcessEngineFactory.selectProvider(ProcessModelType.TBBPM, List.of(bpmn, tbbpm))).isSameAs(tbbpm);
    }

    @Test
    void rejectsMissingAndAmbiguousProviders() {
        assertThatThrownBy(() -> ProcessEngineFactory.selectProvider(ProcessModelType.TBBPM,
                List.of(provider(ProcessModelType.BPMN))))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("No provider found");

        assertThatThrownBy(() -> ProcessEngineFactory.selectProvider(ProcessModelType.TBBPM,
                List.of(provider(ProcessModelType.TBBPM), provider(ProcessModelType.TBBPM))))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Multiple providers found");
    }

    @Test
    void rejectsMalformedProviderContractsAtTheFactoryBoundary() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();

        assertThatThrownBy(() -> ProcessEngineFactory.selectProvider(ProcessModelType.TBBPM, List.of(provider(null))))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("returned null model type");

        ProcessEngineProvider nullEngineProvider = provider(ProcessModelType.TBBPM, null);
        assertThatThrownBy(() -> ProcessEngineFactory.createEngine(config, nullEngineProvider))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("returned null");
    }

    @Test
    void translatesMalformedServiceMetadataIntoAConfigurationFailure(@TempDir Path tempDir) throws IOException {
        String serviceResource = "META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEngineProvider";
        Path descriptor = tempDir.resolve(serviceResource);
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "com.example.DoesNotExist\n");

        try (URLClassLoader classLoader =
                new URLClassLoader(new java.net.URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            ProcessEngineConfig config =
                    ProcessEngineConfig.tbbpmBuilder().classLoader(classLoader).discoverPlugins(false).build();

            assertThatThrownBy(() -> ProcessEngineFactory.create(config))
                .isInstanceOf(CompileFlowException.ConfigurationException.class)
                .hasMessageContaining("Failed to discover ProcessEngineProvider implementations")
                .hasCauseInstanceOf(java.util.ServiceConfigurationError.class);
        }
    }
}
