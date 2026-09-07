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

import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ExamplesControllerTest {
    private ExamplesController controller;

    @BeforeEach
    void setUp() throws Exception {
        ExampleCatalogService catalog = new ExampleCatalogService();
        catalog.loadCatalog();
        controller = new ExamplesController(catalog);
    }

    @Test
    void listExamplesReturnsCatalog() {
        ResponseEntity<List<ExampleResponse>> response = controller.listExamples();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().isEmpty()).isFalse();
    }

    @Test
    void getExampleTrimsId() {
        ResponseEntity<ExampleResponse> response = controller.getExample(" learn.tbbpm.greeting ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().id()).isEqualTo("learn.tbbpm.greeting");
    }

    @Test
    void getExampleRejectsBlankId() {
        assertProblem(() -> controller.getExample(" "), org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "id is required");
    }

    @Test
    void filterRejectsNegativeLevel() {
        assertProblem(() -> controller.filterExamples(-1, null, null), org.springframework.http.HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "level must be non-negative");
    }

    @Test
    void filterTrimsTextFilters() {
        ResponseEntity<List<ExampleResponse>> response = controller.filterExamples(null, " basics ", " BPMN ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<ExampleResponse> body = response.getBody();
        assertThat(body.isEmpty()).isFalse();
        assertThat(body.get(0).category()).isEqualTo("basics");
    }
}
