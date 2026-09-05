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
package com.alibaba.compileflow.engine.test.quality.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.bpmn.semantic.BpmnSemanticFrontend;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineWorkbenchProtocolGoldenTest {
    private static final Path REPOSITORY_ROOT =
            Path.of(System.getProperty("maven.multiModuleProjectDirectory", "..")).toAbsolutePath().normalize();
    private static final Path GOLDEN =
            REPOSITORY_ROOT.resolve("docs/specs/fixtures/engine-workbench-protocol-golden-v1.json");

    @Test
    void javaAndWorkbenchShareOneProtocolGolden() throws Exception {
        JsonNode golden = new ObjectMapper().readTree(Files.readString(GOLDEN));

        assertThat(golden.path("formatVersion").asInt()).isEqualTo(1);
        assertFixture(golden.path("tbbpm"), true);
        assertFixture(golden.path("bpmn"), false);
    }

    private static void assertFixture(JsonNode fixture, boolean tbbpm) throws IOException {
        Path sourcePath = REPOSITORY_ROOT.resolve(fixture.path("fixture").asText());
        FlowSource source = FlowSource.of(sourcePath.getFileName().toString(), Files.readAllBytes(sourcePath));
        ProcessSemanticPlan plan = tbbpm
                ? new TbbpmSemanticFrontend().compile(TbbpmXmlParser.getInstance().parse(source))
                : new BpmnSemanticFrontend().compile(BpmnXmlParser.getInstance().parse(source));
        ProcessCallPlan call =
                ProcessCallPlan.class.cast(plan.requireNode(fixture.path("callSiteId").asText()).operation());

        assertThat(callMappings(call)).containsExactlyElementsOf(strings(fixture.path("callMappings")));
    }

    private static List<String> callMappings(ProcessCallPlan call) {
        List<String> mappings = new ArrayList<>();
        call
            .inputs()
            .forEach(input -> mappings.add("input:" + input.sourceExpression() + ":" + input.target()));
        call
            .outputs()
            .forEach(output -> mappings.add("output:" + output.source() + ":" + output.target()));
        return mappings;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
