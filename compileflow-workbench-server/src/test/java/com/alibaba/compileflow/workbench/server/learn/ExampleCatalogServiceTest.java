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
package com.alibaba.compileflow.workbench.server.learn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExampleCatalogServiceTest {
    private ExampleCatalogService catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ExampleCatalogService(new ObjectMapper());
        catalog.loadCatalog();
    }

    @Test
    void listAllReturnsBundledExamples() {
        List<ExampleResponse> all = catalog.listAll();
        assertThat(all.isEmpty()).isFalse();
        assertThat(all.stream().map(ExampleResponse::modelType)).contains(ProcessModelType.BPMN, ProcessModelType.TBBPM);
    }

    @Test
    void findByIdReturnsExampleWithCode() {
        Optional<ExampleResponse> example = catalog.findById("learn.tbbpm.greeting");
        assertThat(example.isPresent()).isTrue();
        assertThat(example.get().code()).isNotNull();
        assertThat(example.get().name()).isEqualTo("TBBPM Greeting");
    }

    @Test
    void catalogAndNestedCollectionsAreImmutable() {
        List<ExampleResponse> all = catalog.listAll();

        assertThatThrownBy(() -> all.add(all.get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> all.get(0).tags().add("mutated")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void filterByLevel() {
        List<ExampleResponse> beginners = catalog.filter(0, null, null);
        assertThat(beginners.isEmpty()).isFalse();
        assertThat(beginners).allMatch(example -> example.level() == 0);
    }

    @Test
    void bundledExamplesPassStrictPreflightAndExecute() {
        try (ProcessEngine bpmn = ProcessEngineFactory.createBpmn();
                ProcessEngine tbbpm = ProcessEngineFactory.createTbbpm()) {
            for (ExampleResponse example : catalog.listAll()) {
                String id = example.id();
                String code = example.code();
                ProcessDefinition definition = ProcessDefinition.inline(id, code);
                ProcessEngine engine = example.modelType() == ProcessModelType.BPMN ? bpmn : tbbpm;

                ProcessPreflightReport report = engine
                    .tooling()
                    .preflight(definition, ProcessPreflightOptions.strict());
                assertThat(report.getOverallStatus())
                    .as("strict preflight report for %s: %s", id,
                            report
                                .getItems()
                                .stream()
                                .map(item -> item.getType() + "=" + item.getStatus() + "(" + item.getMessage() + ")")
                                .toList())
                    .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);

                ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of());
                assertThat(result.isSuccess()).as("execution result for %s: %s", id, result).isTrue();
            }
        }
    }
}
