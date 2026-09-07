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
package com.alibaba.compileflow.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaDiagnosticsConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsKeepCompilationInMemoryAndBytecodeExportDisabled() {
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.defaults();

        assertThat(config.getDebugSymbols()).isEqualTo(JavaDiagnosticsConfig.DebugSymbols.LINES);
        assertThat(config.isDebugOutputEnabled()).isFalse();
        assertThat(config.isDebugBytecodeEnabled()).isFalse();
    }

    @Test
    void bytecodeExportRequiresAnExplicitOutputDirectory() {
        assertThatThrownBy(() -> JavaDiagnosticsConfig.builder().debugBytecode(true).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debugOutputDirectory is required");
    }

    @Test
    void validationDoesNotEchoTheConfiguredLocalPath() throws Exception {
        Path regularFile = Files.createTempFile(temporaryDirectory, "not-a-directory-", ".tmp");

        assertThatThrownBy(() -> JavaDiagnosticsConfig.builder().debugOutputDirectory(regularFile).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must reference a directory")
            .hasMessageNotContaining(regularFile.toString());
    }

    @Test
    void rejectsLossyOrOverflowingRuntimeDurations() {
        assertThatThrownBy(() -> ProcessEngineConfig
            .builder()
            .runtimeLoadTimeout(Duration.ofSeconds(Long.MAX_VALUE))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runtimeLoadTimeout")
            .hasMessageContaining("whole-millisecond");
    }

    @Test
    void engineConfigurationSnapshotsRemainIndependent() {
        JavaDiagnosticsConfig fast =
                JavaDiagnosticsConfig.builder().debugSymbols(JavaDiagnosticsConfig.DebugSymbols.NONE).build();
        JavaDiagnosticsConfig diagnostic = JavaDiagnosticsConfig
            .builder()
            .debugSymbols(JavaDiagnosticsConfig.DebugSymbols.FULL)
            .debugOutputDirectory(temporaryDirectory)
            .debugBytecode(true)
            .build();

        ProcessEngineConfig first =
                ProcessEngineConfig
            .builder()
            .runtimeLoadTimeout(Duration.ofSeconds(2))
            .javaDiagnostics(fast)
            .build();
        ProcessEngineConfig second =
                ProcessEngineConfig
            .builder()
            .runtimeLoadTimeout(Duration.ofSeconds(30))
            .javaDiagnostics(diagnostic)
            .build();

        assertThat(first.getRuntimeLoadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(first.getJavaDiagnostics().isDebugOutputEnabled()).isFalse();
        assertThat(second.getRuntimeLoadTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(second.getJavaDiagnostics().getDebugOutputDirectory())
            .isEqualTo(temporaryDirectory.toAbsolutePath().normalize());
        assertThat(first.getJavaDiagnostics()).isNotSameAs(second.getJavaDiagnostics());
    }
}
