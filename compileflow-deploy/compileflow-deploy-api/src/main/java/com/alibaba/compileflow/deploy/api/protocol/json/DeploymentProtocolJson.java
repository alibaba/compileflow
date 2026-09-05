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
package com.alibaba.compileflow.deploy.api.protocol.json;

import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import java.util.List;
import java.util.Map;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bounded JSON codec shared by deployment protocol implementations.
 *
 * <p>This type is public only because protocol implementations span deployment modules. It keeps
 * the mutable Jackson mapper and all Jackson types outside method signatures and is not part of
 * the supported {@code com.alibaba.compileflow.deploy.api} surface.
 *
 * @author yusu
 */
public final class DeploymentProtocolJson {
    private static final int MAX_NESTING_DEPTH = 64;
    private static final long MAX_TOKEN_COUNT = 200_000;
    private static final int MAX_NAME_CHARACTERS = 512;
    private static final int MAX_NUMBER_CHARACTERS = 1_000;
    private static final long MAX_DOCUMENT_CHARACTERS = 6L * ProcessDefinitionConfig.MAX_BYTES + 1024L * 1024L;
    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE =
            new TypeReference<Map<String, String>>() {
    };
    private static final TypeReference<List<Map<String, String>>> STRING_MAP_LIST_TYPE =
            new TypeReference<List<Map<String, String>>>() {
    };
    private static final ObjectMapper MAPPER = createMapper();

    private DeploymentProtocolJson() {
    }

    /**
     * Serializes one deployment protocol value.
     *
     * @param value value to serialize
     * @return compact JSON
     */
    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /**
     * Reads one JSON object whose values are not yet typed.
     *
     * @param json JSON object
     * @return decoded object fields
     */
    public static Map<String, Object> readObject(String json) {
        return MAPPER.readValue(json, OBJECT_MAP_TYPE);
    }

    /**
     * Reads one string-valued JSON object.
     *
     * @param json JSON object
     * @return decoded string fields
     */
    public static Map<String, String> readStringMap(String json) {
        return MAPPER.readValue(json, STRING_MAP_TYPE);
    }

    /**
     * Reads a JSON array of string-valued objects.
     *
     * @param json JSON array
     * @return decoded object list
     */
    public static List<Map<String, String>> readStringMapList(String json) {
        return MAPPER.readValue(json, STRING_MAP_LIST_TYPE);
    }

    static Object readTreeForTest(String json) {
        return MAPPER.readTree(json);
    }

    private static ObjectMapper createMapper() {
        return JsonMapper
            .builder(JsonFactory
                .builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints
                    .builder()
                    .maxDocumentLength(MAX_DOCUMENT_CHARACTERS)
                    .maxNestingDepth(MAX_NESTING_DEPTH)
                    .maxTokenCount(MAX_TOKEN_COUNT)
                    .maxStringLength(ProcessDefinitionConfig.MAX_BYTES)
                    .maxNameLength(MAX_NAME_CHARACTERS)
                    .maxNumberLength(MAX_NUMBER_CHARACTERS)
                    .build())
                .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    }
}
