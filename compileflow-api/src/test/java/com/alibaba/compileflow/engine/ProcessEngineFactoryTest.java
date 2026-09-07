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
    private static ProcessEngineProvider provider() {
        return config -> {
            throw new UnsupportedOperationException("selection test only");
        };
    }

    @Test
    void selectsTheSingleBootstrapProvider() {
        ProcessEngineProvider provider = provider();
        assertThat(ProcessEngineFactory.selectProvider(List.of(provider))).isSameAs(provider);
    }

    @Test
    void rejectsMissingAndAmbiguousProviders() {
        assertThatThrownBy(() -> ProcessEngineFactory.selectProvider(List.of()))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("No ProcessEngineProvider found");

        assertThatThrownBy(() -> ProcessEngineFactory.selectProvider(List.of(provider(), provider())))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Multiple ProcessEngineProvider implementations");
    }

    @Test
    void rejectsMalformedProviderContractsAtTheFactoryBoundary() {
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).build();

        ProcessEngineProvider nullEngineProvider = ignored -> null;
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
                    ProcessEngineConfig.builder().classLoader(classLoader).discoverPlugins(false).build();

            assertThatThrownBy(() -> ProcessEngineFactory.create(config))
                .isInstanceOf(CompileFlowException.ConfigurationException.class)
                .hasMessageContaining("Failed to discover ProcessEngineProvider implementations")
                .hasCauseInstanceOf(java.util.ServiceConfigurationError.class);
        }
    }
}
