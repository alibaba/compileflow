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
package com.alibaba.compileflow.deploy.jdbc;

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
 * JDBC provider-owned JSON persistence codec.
 */
final class JdbcDeployJson {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, String>>> STRING_MAP_LIST = new TypeReference<>() {
    };
    private static final ObjectMapper MAPPER = JsonMapper
        .builder(JsonFactory
            .builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints
                .builder()
                .maxDocumentLength(6L * ProcessDefinitionConfig.MAX_BYTES + 1024L * 1024L)
                .maxNestingDepth(64)
                .maxTokenCount(200_000)
                .maxStringLength(ProcessDefinitionConfig.MAX_BYTES)
                .maxNameLength(512)
                .maxNumberLength(1_000)
                .build())
            .build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private JdbcDeployJson() {
    }

    static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    static Map<String, String> readStringMap(String json) {
        return MAPPER.readValue(json, STRING_MAP);
    }

    static List<Map<String, String>> readStringMapList(String json) {
        return MAPPER.readValue(json, STRING_MAP_LIST);
    }
}
