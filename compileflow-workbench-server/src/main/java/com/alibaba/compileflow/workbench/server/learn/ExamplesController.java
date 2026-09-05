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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Workbench Learn example catalog.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/examples")
public class ExamplesController {
    private final ExampleCatalogService catalog;

    @Autowired
    public ExamplesController(ExampleCatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Lists all learning examples.
     *
     * @return all examples in catalog order
     */
    @GetMapping
    public ResponseEntity<List<ExampleResponse>> listExamples() {
        return ResponseEntity.ok(catalog.listAll());
    }

    /**
     * Filters learning examples.
     *
     * @param level    optional difficulty level
     * @param category optional example category
     * @param tag      optional tag
     * @return matching examples, or {@code 400} when the level is invalid
     */
    @GetMapping("/filter")
    public ResponseEntity<List<ExampleResponse>> filterExamples(@RequestParam(required = false) Integer level,
            @RequestParam(required = false) String category, @RequestParam(required = false) String tag) {
        if (level != null && level < 0) {
            throw ApiProblemException.invalidRequest("level must be non-negative");
        }
        return ResponseEntity.ok(catalog.filter(level, StringUtils.trimToNull(category), StringUtils.trimToNull(tag)));
    }

    /**
     * Returns a learning example by id.
     *
     * @param id example id
     * @return example details, or {@code 404} when the example does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExampleResponse> getExample(@PathVariable String id) {
        String normalizedId = StringUtils.trimToNull(id);
        if (normalizedId == null) {
            throw ApiProblemException.invalidRequest("id is required");
        }
        ExampleResponse example = catalog
            .findById(normalizedId)
            .orElseThrow(() -> ApiProblemException.notFound("Example '" + normalizedId + "' was not found"));
        return ResponseEntity.ok(example);
    }
}
