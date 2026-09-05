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
package com.alibaba.compileflow.durable.runtime.codec;

import com.alibaba.compileflow.engine.ProcessText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fixed, engine-owned JSON for command facts and Kernel Integration Events.
 *
 * @author yusu
 */
public final class DurableKernelJsonCodec {
    private static final BigInteger INTEGER_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INTEGER_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int DEFAULT_MAX_BYTES = 256 * 1024;
    private static final int ABSOLUTE_MAX_BYTES = 4 * 1024 * 1024;
    private static final Set<String> TYPE_KEYS = Set.of("@class", "@type", "$type");
    private final int maxBytes;
    private final ObjectMapper mapper;

    public DurableKernelJsonCodec() {
        this(DEFAULT_MAX_BYTES);
    }

    public DurableKernelJsonCodec(int maxBytes) {
        if (maxBytes <= 0 || maxBytes > ABSOLUTE_MAX_BYTES) {
            throw new IllegalArgumentException("maxBytes must be in (0, 4 MiB]");
        }
        this.maxBytes = maxBytes;
        JsonFactory factory = JsonFactory
            .builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints
                .builder()
                .maxDocumentLength(maxBytes)
                .maxNestingDepth(64)
                .maxTokenCount(200_000)
                .maxStringLength(maxBytes)
                .maxNameLength(512)
                .maxNumberLength(1_000)
                .build())
            .build();
        mapper = JsonMapper.builder(factory).build();
    }

    public byte[] encode(Map<String, ?> value) {
        Objects.requireNonNull(value, "value");
        try {
            ObjectNode root = mapper.createObjectNode();
            List<String> names = new ArrayList<>(value.keySet());
            if (names.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("JSON object keys must not be null");
            }
            Collections.sort(names);
            for (String name : names) {
                requireKey(name);
                root.set(name, encodeValue(value.get(name), new IdentityHashMap<>(), 0));
            }
            byte[] bytes = mapper.writeValueAsBytes(root);
            if (bytes.length == 0 || bytes.length > maxBytes) {
                throw new IllegalArgumentException("Kernel JSON exceeds the byte limit");
            }
            return bytes;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Kernel JSON encoding failed", failure);
        }
    }

    public Map<String, Object> decode(byte[] source) {
        byte[] bytes = Objects.requireNonNull(source, "source").clone();
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new IllegalArgumentException("Kernel JSON byte length is invalid");
        }
        try {
            JsonNode parsed = mapper.readTree(bytes);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("Kernel JSON root must be an object");
            }
            return Collections.unmodifiableMap(decodeObject(parsed.asObject(), 0));
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Kernel JSON decoding failed", failure);
        }
    }

    private JsonNode encodeValue(Object value, IdentityHashMap<Object, Boolean> active, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Kernel JSON exceeds maximum depth");
        }
        if (value == null) {
            return mapper.nullNode();
        }
        if (value instanceof String string) {
            return mapper.getNodeFactory().stringNode(string);
        }
        if (value instanceof Boolean bool) {
            return mapper.getNodeFactory().booleanNode(bool);
        }
        if (value instanceof Byte number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Short number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Integer number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Long number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof BigInteger number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof BigDecimal number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Float number && Float.isFinite(number)) {
            return mapper.getNodeFactory().numberNode(BigDecimal.valueOf(number.doubleValue()));
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return mapper.getNodeFactory().numberNode(BigDecimal.valueOf(number));
        }
        if (value instanceof Enum<?> enumeration) {
            return mapper.getNodeFactory().stringNode(enumeration.name());
        }
        if (value instanceof Map<?, ?> map) {
            enter(value, active);
            try {
                ObjectNode object = mapper.createObjectNode();
                List<String> names = new ArrayList<>();
                for (Object key : map.keySet()) {
                    if (!(key instanceof String text)) {
                        throw new IllegalArgumentException("Kernel JSON Map keys must be String");
                    }
                    requireKey(text);
                    names.add(text);
                }
                Collections.sort(names);
                for (String name : names) {
                    object.set(name, encodeValue(map.get(name), active, depth + 1));
                }
                return object;
            } finally {
                active.remove(value);
            }
        }
        if (value instanceof List<?> list) {
            enter(value, active);
            try {
                ArrayNode array = mapper.createArrayNode();
                for (Object item : list) {
                    array.add(encodeValue(item, active, depth + 1));
                }
                return array;
            } finally {
                active.remove(value);
            }
        }
        throw new IllegalArgumentException(
                "Kernel JSON supports only null, scalar, Map<String,?> and ordered List values: " + value
                    .getClass()
                    .getName());
    }

    private Map<String, Object> decodeObject(ObjectNode object, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Kernel JSON exceeds maximum depth");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : object.properties()) {
            requireKey(entry.getKey());
            result.put(entry.getKey(), decodeValue(entry.getValue(), depth + 1));
        }
        return result;
    }

    private Object decodeValue(JsonNode node, int depth) {
        if (node.isNull()) {
            return null;
        }
        if (node.isString()) {
            return node.stringValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            BigInteger integer = node.bigIntegerValue();
            if (integer.compareTo(INTEGER_MIN) >= 0 && integer.compareTo(INTEGER_MAX) <= 0) {
                return integer.intValue();
            }
            if (integer.compareTo(LONG_MIN) >= 0 && integer.compareTo(LONG_MAX) <= 0) {
                return integer.longValue();
            }
            return integer;
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isObject()) {
            return Collections.unmodifiableMap(decodeObject(node.asObject(), depth));
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                values.add(decodeValue(item, depth + 1));
            }
            return Collections.unmodifiableList(values);
        }
        throw new IllegalArgumentException("Unsupported Kernel JSON token");
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || !key.equals(ProcessText.strip(ProcessText.requireUnicode(key, "key")))
                || TYPE_KEYS.contains(key)) {
            throw new IllegalArgumentException("Kernel JSON key is invalid or reserved");
        }
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> active) {
        if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic Kernel JSON value graph");
        }
    }
}
