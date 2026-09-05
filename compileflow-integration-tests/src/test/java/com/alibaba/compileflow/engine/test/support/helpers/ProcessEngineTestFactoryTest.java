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
package com.alibaba.compileflow.engine.test.support.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessEngineTestFactoryTest {
    @TempDir
    Path temporaryDirectory;
    private String originalDebugEnabled;
    private String originalOutputDirectory;

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @BeforeEach
    void rememberSystemProperties() {
        originalDebugEnabled = System.getProperty(ProcessEngineTestFactory.DEBUG_ENABLED_PROPERTY);
        originalOutputDirectory = System.getProperty(ProcessEngineTestFactory.DEBUG_OUTPUT_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty(ProcessEngineTestFactory.DEBUG_ENABLED_PROPERTY, originalDebugEnabled);
        restoreProperty(ProcessEngineTestFactory.DEBUG_OUTPUT_DIRECTORY_PROPERTY, originalOutputDirectory);
    }

    @Test
    void keepsDebugArtifactExportDisabledByDefault() {
        System.clearProperty(ProcessEngineTestFactory.DEBUG_ENABLED_PROPERTY);
        System.clearProperty(ProcessEngineTestFactory.DEBUG_OUTPUT_DIRECTORY_PROPERTY);

        JavaDiagnosticsConfig config = ProcessEngineTestFactory.javaDiagnosticsConfig();

        assertThat(config.isDebugOutputEnabled()).isFalse();
        assertThat(config.isDebugBytecodeEnabled()).isFalse();
    }

    @Test
    void exportsGeneratedSourceAndBytecodeForAnOptInTestRun() throws Exception {
        System.setProperty(ProcessEngineTestFactory.DEBUG_ENABLED_PROPERTY, "true");
        System.setProperty(ProcessEngineTestFactory.DEBUG_OUTPUT_DIRECTORY_PROPERTY, temporaryDirectory.toString());

        JavaDiagnosticsConfig config = ProcessEngineTestFactory.javaDiagnosticsConfig();
        assertThat(config.getDebugSymbols()).isEqualTo(JavaDiagnosticsConfig.DebugSymbols.FULL);
        assertThat(config.getDebugOutputDirectory()).isEqualTo(temporaryDirectory.toAbsolutePath().normalize());
        assertThat(config.isDebugBytecodeEnabled()).isTrue();

        ProcessDefinition definition = ProcessDefinition.inline("test.compilation.debug-artifacts",
                """
            <bpm code="test.compilation.debug-artifacts">
                <start id="start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" g="80,0,32,32"/>
            </bpm>
            """);
        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            assertThat(engine.execute(definition, Map.of()).isSuccess()).isTrue();
        }

        List<String> artifacts;
        try (var paths = Files.walk(temporaryDirectory)) {
            artifacts = paths
                .filter(Files::isRegularFile)
                .map(temporaryDirectory::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .toList();
        }
        assertThat(artifacts)
            .containsExactlyInAnyOrder("source/com/alibaba/compileflow/generated/process/test/compilation/DebugArtifa"
                    + "ctsFlow.java",
                    "classes/com/alibaba/compileflow/generated/process/test/compilation/DebugArtifactsFlow.class",
                    "metadata/com/alibaba/compileflow/generated/process/test/compilation/DebugArtifactsFlow.properties");

        String metadata = Files.readString(temporaryDirectory.resolve(
                "metadata/com/alibaba/compileflow/generated/process/test/compilation/DebugArtifactsFlow.properties"));
        assertThat(metadata)
            .contains("primary-class=com.alibaba.compileflow.generated.process.test.compilation.DebugArtifactsFlow")
            .contains(
                    "source-file=source/com/alibaba/compileflow/generated/process/test/compilation/DebugArtifactsFlow.java");
    }
}
