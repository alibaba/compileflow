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
package com.alibaba.compileflow.workbench.server.api.validation;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses primitive JSON request values at REST controller boundaries.
 *
 * @author yusu
 */
public final class RequestValueParser {
    /**
     * Largest page size accepted by Server REST list endpoints.
     */
    public static final int MAX_LIST_PAGE_SIZE = 100;

    private RequestValueParser() {
    }

    /**
     * Returns an immutable shallow copy of a dynamic request object.
     *
     * <p>Dynamic process values are validated by the process engine; the HTTP boundary only
     * prevents callers from mutating the request object after deserialization.</p>
     */
    public static <V> Map<String, V> immutableMap(Map<String, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Parses an optional integer field from a JSON object.
     *
     * @param body request body
     * @param key  field name
     * @return parsed integer, or {@code null} when the field is absent
     */
    public static Integer optionalInteger(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) {
            return null;
        }
        return integerValue(value, key);
    }

    /**
     * Parses an optional integer field from a JSON object.
     *
     * @param body         request body
     * @param key          field name
     * @param defaultValue default value used when the field is absent
     * @return parsed integer or default value
     */
    public static int optionalInteger(Map<String, Object> body, String key, int defaultValue) {
        Integer value = optionalInteger(body, key);
        return value == null ? defaultValue : value;
    }

    /**
     * Requires one-based pagination values.
     *
     * @param page     one-based page number
     * @param pageSize requested page size
     */
    public static void requireOneBasedPage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
    }

    /**
     * Requires one-based pagination values within a bounded page size.
     *
     * @param page        one-based page number
     * @param pageSize    requested page size
     * @param maxPageSize largest accepted page size
     */
    public static void requireOneBasedPage(int page, int pageSize, int maxPageSize) {
        requireOneBasedPage(page, pageSize);
        if (pageSize > maxPageSize) {
            throw new IllegalArgumentException("pageSize must be less than or equal to " + maxPageSize);
        }
    }

    /**
     * Requires a positive bounded result limit.
     *
     * @param limit accepted result count
     * @param name request field name
     * @param maximum largest accepted value
     */
    public static void requireLimit(int limit, String name, int maximum) {
        if (limit < 1 || limit > maximum) {
            throw new IllegalArgumentException(name + " must be between 1 and " + maximum);
        }
    }

    /**
     * Validates an optional log-safe invocation identifier without rewriting it.
     *
     * @param value caller-provided invocation identifier
     * @return the unchanged identifier, or {@code null} when absent
     * @throws IllegalArgumentException when blank, surrounded by whitespace, or otherwise invalid
     */
    public static String optionalInvocationId(String value) {
        return ProcessIdentifiers.optionalInvocationId(value);
    }

    /**
     * Validates a required log-safe invocation identifier without rewriting it.
     *
     * @param value caller-provided invocation identifier
     * @return the unchanged non-null identifier
     * @throws IllegalArgumentException when absent, blank, surrounded by whitespace, or otherwise invalid
     */
    public static String requiredInvocationId(String value) {
        return ProcessIdentifiers.requireInvocationId(value);
    }

    private static int integerValue(Object value, String key) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return toInt(((Number) value).longValue(), key);
        }
        if (value instanceof BigInteger) {
            return toInt((BigInteger) value, key);
        }
        if (value instanceof BigDecimal) {
            return toInt(toBigInteger((BigDecimal) value, key), key);
        }
        throw new IllegalArgumentException(key + " must be an integer");
    }

    private static BigInteger toBigInteger(BigDecimal value, String key) {
        try {
            return value.toBigIntegerExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(key + " must be an integer", failure);
        }
    }

    private static int toInt(BigInteger value, String key) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(key + " must fit in a 32-bit integer", failure);
        }
    }

    private static int toInt(long value, String key) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(key + " must fit in a 32-bit integer", failure);
        }
    }
}
