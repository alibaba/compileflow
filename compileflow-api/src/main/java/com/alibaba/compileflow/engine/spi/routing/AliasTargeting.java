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
package com.alibaba.compileflow.engine.spi.routing;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration of one named Alias targeting policy.
 *
 * @param policy stable semantic policy name
 * @param parameters bounded policy configuration owned by the Alias route
 * @author yusu
 */
public record AliasTargeting(String policy, Map<String, String> parameters) {
    private static final int MAX_PARAMETER_ENTRIES = 32;
    private static final int MAX_PARAMETER_NAME_CHARACTERS = 128;
    private static final int MAX_PARAMETER_VALUE_CHARACTERS = 2_048;
    private static final int MAX_TOTAL_UTF8_BYTES = 32 * 1_024;

    /**
     * Creates targeting configuration without parameters.
     *
     * @param policy stable semantic policy name
     */
    public AliasTargeting(String policy) {
        this(policy, Map.of());
    }

    public AliasTargeting {
        policy = ProcessAliasTargetingPolicy.requireCanonicalName(policy);
        parameters = immutableParameters(parameters);
    }

    private static Map<String, String> immutableParameters(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_PARAMETER_ENTRIES) {
            throw new IllegalArgumentException(
                    "Alias targeting parameters must not contain more than " + MAX_PARAMETER_ENTRIES + " entries");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        long totalBytes = 0L;
        for (Map.Entry<String, String> parameter : source.entrySet()) {
            String name = ProcessIdentifiers.requireExactIdentity(parameter.getKey(), "Alias targeting parameter name",
                    MAX_PARAMETER_NAME_CHARACTERS);
            String value =
                    Objects.requireNonNull(parameter.getValue(),
                            "Alias targeting parameter value must not be null: " + name);
            ProcessText.requireUnicode(value, "Alias targeting parameter value");
            requireMaximumCharacters(value, "Alias targeting parameter value", MAX_PARAMETER_VALUE_CHARACTERS);
            if (copy.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate Alias targeting parameter name: " + name);
            }
            totalBytes += utf8Length(name) + utf8Length(value);
            if (totalBytes > MAX_TOTAL_UTF8_BYTES) {
                throw new IllegalArgumentException(
                        "Alias targeting parameters must not exceed " + MAX_TOTAL_UTF8_BYTES + " UTF-8 bytes");
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireMaximumCharacters(String value, String name, int maximum) {
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " must not exceed " + maximum + " characters");
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Omits parameter values because route configuration may be sensitive.
     */
    @Override
    public String toString() {
        return "AliasTargeting[policy=" + policy + ", parameterNames=" + parameters.keySet() + ']';
    }
}
