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
package com.alibaba.compileflow.engine.core.mapping;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDataMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thread-safe process data mapper backed by a configured Jackson object mapper.
 *
 * @author yusu
 */
public final class JacksonProcessDataMapper implements ProcessDataMapper {
    private static final TypeReference<Map<String, Object>> VARIABLES_TYPE =
            new TypeReference<Map<String, Object>>() {
    };
    private final ObjectMapper objectMapper;

    /**
     * Creates a mapper using the supplied configured Jackson instance.
     *
     * @param objectMapper thread-safe Jackson object mapper
     */
    public JacksonProcessDataMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Creates a mapper with CompileFlow's dependency-free Jackson defaults.
     *
     * @return process data mapper backed by a new Jackson mapper
     */
    public static JacksonProcessDataMapper createDefault() {
        return new JacksonProcessDataMapper(JsonMapper.builder().build());
    }

    @Override
    public Map<String, Object> toVariables(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> variables = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new CompileFlowException.ValidationException("input",
                            "Process variable map keys must be strings");
                }
                variables.put(key, entry.getValue());
            }
            return variables;
        }
        try {
            return objectMapper.convertValue(value, VARIABLES_TYPE);
        } catch (RuntimeException failure) {
            throw new CompileFlowException.ValidationException("input",
                    "Failed to map application input to process variables", failure);
        }
    }

    @Override
    public <T> T fromVariables(Map<String, Object> variables, Class<T> targetType) {
        Objects.requireNonNull(variables, "variables");
        Class<T> type = Objects.requireNonNull(targetType, "targetType");
        if (type == Map.class) {
            return type.cast(variables);
        }
        return objectMapper.convertValue(variables, type);
    }
}
