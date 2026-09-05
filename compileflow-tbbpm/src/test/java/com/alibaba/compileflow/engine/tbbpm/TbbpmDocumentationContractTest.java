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
package com.alibaba.compileflow.engine.tbbpm;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TbbpmDocumentationContractTest {
    private static final Pattern XML_BLOCK = Pattern.compile("```xml\\R(.*?)\\R```", Pattern.DOTALL);
    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void canonicalExamplesPassTheExecutableContractInBothRuntimeModes() throws IOException {
        Set<String> specificationExamples =
                examples("docs/specs/tbbpm-specification.en.md", "docs/specs/tbbpm-specification.zh.md");
        Set<String> quickStartExamples = examples("docs/en/quick-start.md", "docs/zh/quick-start.md");

        assertThat(specificationExamples).hasSize(1);
        assertThat(quickStartExamples).hasSize(1);
        for (ProcessRuntimeMode mode : ProcessRuntimeMode.values()) {
            assertExecutable(specificationExamples.iterator().next(), Map.of("name", "Ada"), "greeting", "Hello, Ada",
                    mode);
            assertExecutable(quickStartExamples.iterator().next(), Map.of("value", 1), "result", 3, mode);
        }
    }

    private static Set<String> examples(String... paths) throws IOException {
        Set<String> examples = new LinkedHashSet<>();
        for (String path : paths) {
            String markdown = Files.readString(PROJECT_ROOT.resolve(path));
            Matcher blocks = XML_BLOCK.matcher(markdown);
            while (blocks.find()) {
                String xml = blocks.group(1).strip();
                if (xml.contains("<bpm ")) {
                    examples.add(xml);
                    break;
                }
            }
        }
        return examples;
    }

    private static Path projectRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("docs/specs"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Cannot locate the CompileFlow project root");
        }
        return directory;
    }

    private static void assertExecutable(String xml, Map<String, Object> input, String outputName, Object expectedOutput,
            ProcessRuntimeMode mode) {
        String code = xml.substring(xml.indexOf("code=\"") + 6, xml.indexOf('"', xml.indexOf("code=\"") + 6));
        ProcessDefinition definition = ProcessDefinition.inline(code, xml);
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().runtimeMode(mode).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessPreflightReport preflight = engine
                .tooling()
                .preflight(definition, ProcessPreflightOptions.strict());
            assertThat(preflight.getOverallStatus())
                .withFailMessage(() -> "Preflight failed: "
                        + preflight.getItems().stream().map(ProcessPreflightReport.Item::getMessage).toList())
                .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);

            ProcessResult<Map<String, Object>> result = engine.execute(definition, input);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry(outputName, expectedOutput);
        }
    }
}
