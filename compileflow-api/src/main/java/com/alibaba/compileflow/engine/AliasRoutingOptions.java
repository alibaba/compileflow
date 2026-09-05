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
package com.alibaba.compileflow.engine;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable inputs used only while admitting an {@link ProcessRef.Alias}.
 *
 * @param routingKey opaque deterministic cohort key, or {@code null}
 * @param attributes immutable request-scoped targeting attributes
 *
 * @author yusu
 */
public record AliasRoutingOptions(String routingKey, Map<String, String> attributes) {
    private static final int MAX_ROUTING_KEY_CHARACTERS = 512;
    private static final int MAX_ATTRIBUTE_ENTRIES = 32;
    private static final int MAX_ATTRIBUTE_NAME_CHARACTERS = 128;
    private static final int MAX_ATTRIBUTE_VALUE_CHARACTERS = 2_048;
    private static final int MAX_TOTAL_UTF8_BYTES = 32 * 1_024;
    private static final String ENGINE_METADATA_PREFIX = "__cf_";
    private static final Set<String> RESERVED_NAMES =
            Set.of("alias", "version", "namespace", "processCode", "routingKey");
    private static final AliasRoutingOptions DEFAULTS = new AliasRoutingOptions(null, Map.of());

    /**
     * Creates routing options without targeting attributes.
     *
     * @param routingKey opaque deterministic cohort key, or {@code null}
     */
    public AliasRoutingOptions(String routingKey) {
        this(routingKey, Map.of());
    }

    public AliasRoutingOptions {
        routingKey = routingKey == null || routingKey.isEmpty() ? null : routingKey;
        if (routingKey != null) {
            ProcessText.requireUnicode(routingKey, "routingKey");
        }
        requireMaximumCharacters(routingKey, "routingKey", MAX_ROUTING_KEY_CHARACTERS);
        attributes = immutableAttributes(attributes);
        requireWithinAggregateLimit(routingKey, attributes);
    }

    /**
     * Returns the shared value with no Alias routing inputs.
     */
    public static AliasRoutingOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns whether this value carries no Alias routing inputs.
     */
    public boolean isEmpty() {
        return routingKey == null && attributes.isEmpty();
    }

    private static Map<String, String> immutableAttributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_ATTRIBUTE_ENTRIES) {
            throw new IllegalArgumentException(
                    "Alias routing attributes must not contain more than " + MAX_ATTRIBUTE_ENTRIES + " entries");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            String exact =
                    ProcessIdentifiers.requireExactIdentity(name, "Alias routing attribute name",
                            MAX_ATTRIBUTE_NAME_CHARACTERS);
            if (RESERVED_NAMES.contains(exact) || exact.startsWith(ENGINE_METADATA_PREFIX)) {
                throw new IllegalArgumentException("Alias routing attribute name is reserved: " + exact);
            }
            String checked = Objects.requireNonNull(value, "Alias routing attribute value must not be null: " + exact);
            ProcessText.requireUnicode(checked, "Alias routing attribute value");
            requireMaximumCharacters(checked, "Alias routing attribute value", MAX_ATTRIBUTE_VALUE_CHARACTERS);
            if (copy.putIfAbsent(exact, checked) != null) {
                throw new IllegalArgumentException("Duplicate Alias routing attribute name: " + exact);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void requireMaximumCharacters(String value, String name, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " must not exceed " + maximum + " characters");
        }
    }

    private static void requireWithinAggregateLimit(String routingKey, Map<String, String> attributes) {
        long total = utf8Length(routingKey);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            total += utf8Length(attribute.getKey());
            total += utf8Length(attribute.getValue());
            if (total > MAX_TOTAL_UTF8_BYTES) {
                throw new IllegalArgumentException(
                        "Alias routing inputs must not exceed " + MAX_TOTAL_UTF8_BYTES + " UTF-8 bytes");
            }
        }
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Omits the routing value because it may contain a sensitive cohort input.
     */
    @Override
    public String toString() {
        return "AliasRoutingOptions[routingKeyPresent=" + (routingKey != null) + ", attributeNames="
                + attributes.keySet() + ']';
    }
}
