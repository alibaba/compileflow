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
package com.alibaba.compileflow.engine.core.java.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationDebugExporterTest {
    private static final String CLASS_NAME = "com.example.DebugProbe";
    private static final String SOURCE = "package com.example; public class DebugProbe {}";
    @TempDir
    Path outputDirectory;

    private static void exportDummyBytecode(CompilationDebugExporter.DebugArtifacts artifacts) {
        MemoryClassOutput output = new MemoryClassOutput(CLASS_NAME);
        output.writeClass(CLASS_NAME, new byte[] {1, 2, 3});
        artifacts.exportBytecode(output.finish());
    }

    @Test
    void exportsSourceButNotBytecodeByDefault() throws Exception {
        CompilationDebugExporter.DebugArtifacts artifacts =
                openArtifacts(JavaDiagnosticsConfig.builder().debugOutputDirectory(outputDirectory).build());

        exportDummyBytecode(artifacts);

        Path debugRoot = artifacts.getOutputDirectory();
        assertThat(Files.readString(debugRoot.resolve("source/com/example/DebugProbe.java"))).isEqualTo(SOURCE);
        assertThat(Files.readString(debugRoot.resolve("metadata/com/example/DebugProbe.properties")))
            .contains("primary-class=" + CLASS_NAME, "java-release=17", "debug-symbols=LINES",
                    "source-file=source/com/example/DebugProbe.java");
        assertThat(debugRoot.resolve("classes/com/example/DebugProbe.class")).doesNotExist();
    }

    @Test
    void exportsBytecodeOnlyWhenExplicitlyEnabled() throws Exception {
        CompilationDebugExporter.DebugArtifacts artifacts = openArtifacts(JavaDiagnosticsConfig
            .builder()
            .debugOutputDirectory(outputDirectory)
            .debugBytecode(true)
            .build());

        exportDummyBytecode(artifacts);

        assertThat(Files.readAllBytes(artifacts.getOutputDirectory().resolve("classes/com/example/DebugProbe.class")))
            .containsExactly(new byte[] {1, 2, 3});
    }

    @Test
    void repeatedCompilationsReuseStableArtifactPaths() throws Exception {
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.builder().debugOutputDirectory(outputDirectory).build();

        CompilationDebugExporter.DebugArtifacts first = openArtifacts(config);
        CompilationDebugExporter.DebugArtifacts second = openArtifacts(config);

        assertThat(first.getOutputDirectory()).isEqualTo(outputDirectory.toRealPath());
        assertThat(second.getOutputDirectory()).isEqualTo(first.getOutputDirectory());
        assertThat(first.getArtifactId()).isEqualTo("com.example.DebugProbe");
        assertThat(outputDirectory.resolve("source/com/example/DebugProbe.java")).hasContent(SOURCE);
    }

    @Test
    void exportsTopLevelClassIntoTheStableSourceDirectory() throws Exception {
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.builder().debugOutputDirectory(outputDirectory).build();
        JavaCompileOptions options = new JavaCompileOptions(config.getDebugSymbols(), null);

        CompilationDebugExporter.DebugArtifacts artifacts = new CompilationDebugExporter(config)
            .open(JavaSource.of("public class DebugProbe {}", "DebugProbe"), options);

        assertThat(artifacts.getOutputDirectory().resolve("source/DebugProbe.java")).hasContent(
                "public class DebugProbe {}");
    }

    @Test
    void exportsStructuredSourceMetadata() throws Exception {
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.builder().debugOutputDirectory(outputDirectory).build();
        JavaCompileOptions options = new JavaCompileOptions(config.getDebugSymbols(), null);
        JavaSource source = JavaSource.of(SOURCE, CLASS_NAME,
                Map.of("process-code", "order.payment=approval", "program-digest", "abc123"));

        new CompilationDebugExporter(config).open(source, options);

        assertThat(outputDirectory.resolve("metadata/com/example/DebugProbe.properties"))
            .hasContent(
                    "primary-class=com.example.DebugProbe\n"
                    + "source-sha256=ce125ac44c03d24b84b80b1e25049232f7782bdc469a420a461230001b36314e\n"
                    + "source-count=1\n" + "java-release=17\n" + "debug-symbols=LINES\n"
                    + "source-file=source/com/example/DebugProbe.java\n" + "process-code=order.payment\\=approval\n"
                    + "program-digest=abc123\n");
    }

    @Test
    void exportsCompanionSourcesAndTheirUnitCount() throws Exception {
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.builder().debugOutputDirectory(outputDirectory).build();
        JavaCompileOptions options = new JavaCompileOptions(config.getDebugSymbols(), null);
        JavaSource companion =
                JavaSource.of("package com.example.source; public final class Rule {}", "com.example.source.Rule");

        new CompilationDebugExporter(config).open(JavaSource.of(SOURCE, CLASS_NAME), List.of(companion), options);

        assertThat(outputDirectory.resolve("source/com/example/source/Rule.java")).hasContent(companion.getJavaSourceCode());
        assertThat(Files.readString(outputDirectory.resolve("metadata/com/example/DebugProbe.properties")))
            .contains("source-count=2\n");
    }

    @Test
    void preservesTheDebugExportFailureCause() throws Exception {
        Path debugOutput = outputDirectory.resolve("debug-output");
        JavaDiagnosticsConfig config = JavaDiagnosticsConfig.builder().debugOutputDirectory(debugOutput).build();
        Files.writeString(debugOutput, "not a directory");

        assertThatThrownBy(() -> openArtifacts(config))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Failed to export compilation debug source")
            .hasCauseInstanceOf(FileAlreadyExistsException.class);
    }

    private CompilationDebugExporter.DebugArtifacts openArtifacts(JavaDiagnosticsConfig config) {
        JavaCompileOptions options = new JavaCompileOptions(config.getDebugSymbols(), null);
        return new CompilationDebugExporter(config).open(JavaSource.of(SOURCE, CLASS_NAME), options);
    }
}
