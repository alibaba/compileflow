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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * One bundled learning example.
 *
 * @param id               stable example identifier
 * @param name             display name
 * @param modelType        process model format
 * @param level            learning progression level
 * @param duration         human-readable completion estimate
 * @param difficulty       difficulty from one through five
 * @param description      short description
 * @param category         catalog category
 * @param tags             immutable search tags
 * @param whatYouWillLearn immutable learning outcomes
 * @param keyConcepts      immutable key concepts
 * @param code             executable process definition
 * @param overview         overview content
 * @param explanation      explanation content
 * @param nextSteps        suggested next steps
 * @param documentation    related documentation
 * @param bpmFile          optional bundled process filename
 * @author yusu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExampleResponse(@JsonProperty(required = true) String id, @JsonProperty(required = true) String name,
        @JsonProperty(required = true) ProcessModelType modelType, @JsonProperty(required = true) int level,
        @JsonProperty(required = true) String duration, @JsonProperty(required = true) int difficulty,
        @JsonProperty(required = true) String description, @JsonProperty(required = true) String category,
        @JsonProperty(required = true) List<String> tags, @JsonProperty(required = true) List<String> whatYouWillLearn,
        @JsonProperty(required = true) List<String> keyConcepts, @JsonProperty(required = true) String code,
        @JsonProperty(required = true) String overview, @JsonProperty(required = true) String explanation,
        @JsonProperty(required = true) String nextSteps, @JsonProperty(required = true) String documentation,
        String bpmFile) {
    public ExampleResponse {
        requireText(id, "id");
        requireText(name, "name");
        Objects.requireNonNull(modelType, "modelType");
        if (level < 0) {
            throw new IllegalArgumentException("level must be greater than or equal to 0");
        }
        requireText(duration, "duration");
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        requireText(description, "description");
        requireText(category, "category");
        tags = immutableStrings(tags, "tags");
        whatYouWillLearn = immutableStrings(whatYouWillLearn, "whatYouWillLearn");
        keyConcepts = immutableStrings(keyConcepts, "keyConcepts");
        requireText(code, "code");
        requireText(overview, "overview");
        requireText(explanation, "explanation");
        requireText(nextSteps, "nextSteps");
        requireText(documentation, "documentation");
        if (bpmFile != null) {
            requireText(bpmFile, "bpmFile");
        }
    }

    private static List<String> immutableStrings(List<String> values, String name) {
        List<String> result = List.copyOf(Objects.requireNonNull(values, name));
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must contain at least one value");
        }
        for (String value : result) {
            requireText(value, name + " item");
        }
        return result;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
    }
}
