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
package com.alibaba.compileflow.durable.runtime.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Disposable state schema derived from the exact immutable Process Definition.
 *
 * @author yusu
 */
public final class ProcessStateSchema {
    private final List<ProcessStateField> fields;
    private final Map<String, ProcessStateField> fieldsByName;

    public ProcessStateSchema(List<ProcessStateField> source) {
        List<ProcessStateField> canonical = new ArrayList<>(Objects.requireNonNull(source, "fields"));
        canonical.sort(Comparator.comparing(ProcessStateField::name));
        Map<String, ProcessStateField> index = new LinkedHashMap<>();
        for (ProcessStateField field : canonical) {
            ProcessStateField value = Objects.requireNonNull(field, "field");
            if (index.put(value.name(), value) != null) {
                throw new IllegalArgumentException("Duplicate process state field: " + value.name());
            }
        }
        this.fields = List.copyOf(canonical);
        this.fieldsByName = Collections.unmodifiableMap(index);
    }

    public List<ProcessStateField> fields() {
        return fields;
    }

    public ProcessStateField requireField(String name) {
        ProcessStateField field = fieldsByName.get(Objects.requireNonNull(name, "name"));
        if (field == null) {
            throw new IllegalArgumentException("Undeclared process state field: " + name);
        }
        return field;
    }

    public Map<String, Object> validate(Map<String, Object> source) {
        Map<String, Object> state = Objects.requireNonNull(source, "state");
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "state field name");
            ProcessStateField field = requireField(name);
            if (entry.getValue() == null && !field.nullable()) {
                throw new IllegalArgumentException("Process state field is not nullable: " + name);
            }
            copy.put(name, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Validates caller-owned Run admission input against Process parameter variables only.
     */
    public Map<String, Object> validateStartInput(Map<String, Object> source) {
        Map<String, Object> input = validate(source);
        for (String name : input.keySet()) {
            if (!requireField(name).startInput()) {
                throw new IllegalArgumentException("Process variable is not a Start input: " + name);
            }
        }
        return input;
    }
}
