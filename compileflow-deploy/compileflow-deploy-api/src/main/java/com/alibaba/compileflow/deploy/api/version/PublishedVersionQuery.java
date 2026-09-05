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
package com.alibaba.compileflow.deploy.api.version;

import com.alibaba.compileflow.engine.ProcessIdentifiers;

/**
 * Filters and keyset traversal for immutable published versions of one process.
 *
 * @author yusu
 */
public final class PublishedVersionQuery {
    private final String namespace;
    private final String code;
    private final String versionPrefix;
    private final PublishedVersionCursor cursor;
    private final int limit;

    /**
     * Creates a normalized published-version query.
     *
     * @param namespace     process namespace
     * @param code          process code
     * @param versionPrefix optional exact-prefix filter
     * @param cursor        optional opaque continuation cursor
     * @param limit         requested result limit in {@code 1..100}
     */
    public PublishedVersionQuery(String namespace, String code, String versionPrefix, PublishedVersionCursor cursor,
            int limit) {
        String normalizedNamespace = ProcessIdentifiers.requireNamespace(namespace);
        String normalizedCode = ProcessIdentifiers.requireCode(code);
        String normalizedPrefix = versionPrefix == null ? null : versionPrefix.trim();
        if (normalizedPrefix != null && normalizedPrefix.isEmpty()) {
            normalizedPrefix = null;
        }
        if (normalizedPrefix != null && normalizedPrefix.length() > 64) {
            throw new IllegalArgumentException("versionPrefix must not exceed 64 characters");
        }
        if (normalizedPrefix != null) {
            normalizedPrefix = ProcessIdentifiers.requireVersion(normalizedPrefix);
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        this.namespace = normalizedNamespace;
        this.code = normalizedCode;
        this.versionPrefix = normalizedPrefix;
        this.cursor = cursor;
        this.limit = limit;
    }

    /**
     * Returns the normalized process namespace.
     *
     * @return process namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the normalized process code.
     *
     * @return process code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the optional version prefix.
     *
     * @return normalized prefix, or {@code null}
     */
    public String getVersionPrefix() {
        return versionPrefix;
    }

    /**
     * Returns the optional opaque continuation cursor.
     *
     * @return cursor, or {@code null} for the first page
     */
    public PublishedVersionCursor getCursor() {
        return cursor;
    }

    /**
     * Returns the requested result limit.
     *
     * @return limit in {@code 1..100}
     */
    public int getLimit() {
        return limit;
    }
}
