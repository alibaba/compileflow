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

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads and serves bundled Learn example catalog entries.
 *
 * @author yusu
 */
@Service
public class ExampleCatalogService {
    private static final String CATALOG_PATH = "learn/examples-catalog.json";
    private static final TypeReference<List<ExampleResponse>> CATALOG_TYPE =
            new TypeReference<List<ExampleResponse>>() {
    };
    private static final ObjectMapper CATALOG_MAPPER = JsonMapper
        .builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
    private List<ExampleResponse> examples = List.of();

    public ExampleCatalogService() {
    }

    private static List<ExampleResponse> validateCatalog(List<ExampleResponse> loaded) {
        List<ExampleResponse> catalog = List.copyOf(Objects.requireNonNull(loaded, "loaded"));
        if (catalog.isEmpty()) {
            throw new IllegalStateException("Example catalog must contain at least one example");
        }
        Set<String> ids = new HashSet<>();
        for (ExampleResponse example : catalog) {
            if (!ids.add(example.id())) {
                throw new IllegalStateException("Duplicate example id: " + example.id());
            }
        }
        return catalog;
    }

    @PostConstruct
    void loadCatalog() throws IOException {
        ClassPathResource resource = new ClassPathResource(CATALOG_PATH);
        try (InputStream input = resource.getInputStream()) {
            List<ExampleResponse> loaded = CATALOG_MAPPER.readValue(input, CATALOG_TYPE);
            examples = validateCatalog(loaded);
        }
    }

    /**
     * Returns all examples in catalog order.
     *
     * @return immutable bundled example catalog
     */
    public List<ExampleResponse> listAll() {
        return examples;
    }

    /**
     * Returns a single example by id.
     *
     * @param id example id
     * @return matching example, or empty when no example uses the id
     */
    public Optional<ExampleResponse> findById(String id) {
        return examples
            .stream()
            .filter(example -> id.equals(example.id()))
            .findFirst();
    }

    /**
     * Filters examples by optional level, category, and tag.
     *
     * @param level    optional difficulty level
     * @param category optional example category
     * @param tag      optional tag
     * @return immutable list of matching examples
     */
    public List<ExampleResponse> filter(Integer level, String category, String tag) {
        return examples
            .stream()
            .filter(example -> level == null || level.intValue() == example.level())
            .filter(example -> category == null || category.equals(example.category()))
            .filter(example -> tag == null || example.tags().contains(tag))
            .toList();
    }
}
