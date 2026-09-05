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
package com.alibaba.compileflow.durable.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableTestCompilationTest {
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
        originalDebugEnabled = System.getProperty(DurableTestCompilation.DEBUG_ENABLED_PROPERTY);
        originalOutputDirectory = System.getProperty(DurableTestCompilation.DEBUG_OUTPUT_DIRECTORY_PROPERTY);
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty(DurableTestCompilation.DEBUG_ENABLED_PROPERTY, originalDebugEnabled);
        restoreProperty(DurableTestCompilation.DEBUG_OUTPUT_DIRECTORY_PROPERTY, originalOutputDirectory);
    }

    @Test
    void keepsDebugArtifactExportDisabledByDefault() {
        System.clearProperty(DurableTestCompilation.DEBUG_ENABLED_PROPERTY);
        System.clearProperty(DurableTestCompilation.DEBUG_OUTPUT_DIRECTORY_PROPERTY);

        JavaDiagnosticsConfig config = DurableTestCompilation.config();

        assertThat(config.isDebugOutputEnabled()).isFalse();
        assertThat(config.isDebugBytecodeEnabled()).isFalse();
    }

    @Test
    void enablesFullSourceAndBytecodeExportForLocalTests() {
        System.setProperty(DurableTestCompilation.DEBUG_ENABLED_PROPERTY, "true");
        System.setProperty(DurableTestCompilation.DEBUG_OUTPUT_DIRECTORY_PROPERTY, temporaryDirectory.toString());

        JavaDiagnosticsConfig config = DurableTestCompilation.config();

        assertThat(config.getDebugSymbols()).isEqualTo(JavaDiagnosticsConfig.DebugSymbols.FULL);
        assertThat(config.getDebugOutputDirectory()).isEqualTo(temporaryDirectory.toAbsolutePath().normalize());
        assertThat(config.isDebugBytecodeEnabled()).isTrue();
    }
}
