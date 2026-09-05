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
package com.alibaba.compileflow.deploy.api.rollout;

import com.alibaba.compileflow.engine.ProcessIdentifiers;

/**
 * Filters and keyset traversal for rollout history.
 *
 * @author yusu
 */
public final class RolloutQuery {
    private final String namespace;
    private final String code;
    private final String keyword;
    private final String alias;
    private final RolloutPhase phase;
    private final RolloutCursor cursor;
    private final int limit;

    /**
     * Creates a normalized rollout-history query.
     *
     * @param namespace optional process namespace filter
     * @param code      optional process code filter
     * @param keyword   optional case-insensitive process-code or rollout-id search term
     * @param alias     optional published Alias filter; requires {@code code}
     * @param phase     optional rollout phase filter
     * @param cursor    optional opaque continuation cursor
     * @param limit     requested result limit in {@code 1..100}
     */
    public RolloutQuery(String namespace, String code, String keyword, String alias, RolloutPhase phase,
            RolloutCursor cursor, int limit) {
        if (namespace != null && namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must be absent or non-blank");
        }
        String normalizedNamespace = namespace;
        String normalizedCode = code;
        String normalizedAlias = alias;
        if (code != null || alias != null) {
            if (code == null) {
                throw new IllegalArgumentException("code is required when alias is provided");
            }
            normalizedNamespace = ProcessIdentifiers.requireNamespace(namespace);
            normalizedCode = ProcessIdentifiers.requireCode(code);
            normalizedAlias = alias == null ? null : ProcessIdentifiers.requireAlias(alias);
        } else if (namespace != null && !namespace.isBlank()) {
            normalizedNamespace = ProcessIdentifiers.requireNamespace(namespace);
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        this.namespace = normalizedNamespace;
        this.code = normalizedCode;
        this.keyword = keyword == null ? null : keyword.trim();
        if (this.keyword != null && this.keyword.isEmpty()) {
            throw new IllegalArgumentException("keyword must be absent or non-blank");
        }
        if (this.keyword != null && this.keyword.length() > 128) {
            throw new IllegalArgumentException("keyword must not exceed 128 characters");
        }
        this.alias = normalizedAlias;
        this.phase = phase;
        this.cursor = cursor;
        this.limit = limit;
    }

    /**
     * Returns the namespace filter.
     *
     * @return normalized namespace, or {@code null}
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the process code filter.
     *
     * @return normalized code, or {@code null}
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the case-insensitive process-code or rollout-id search term.
     *
     * @return normalized keyword, or {@code null}
     */
    public String getKeyword() {
        return keyword;
    }

    /**
     * Returns the published Alias filter.
     *
     * @return normalized alias, or {@code null}
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns the rollout phase filter.
     *
     * @return phase, or {@code null}
     */
    public RolloutPhase getPhase() {
        return phase;
    }

    /**
     * Returns the optional opaque continuation cursor.
     *
     * @return cursor, or {@code null} for the first page
     */
    public RolloutCursor getCursor() {
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
