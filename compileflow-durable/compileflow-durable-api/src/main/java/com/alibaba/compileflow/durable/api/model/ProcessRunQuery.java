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
package com.alibaba.compileflow.durable.api.model;

import com.alibaba.compileflow.durable.api.validation.DurableEnumSets;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.Set;

/**
 * Bounded query for redaction-safe Runs.
 *
 * @param namespace optional process namespace
 * @param code      optional process code
 * @param statuses  optional lifecycle filter
 * @param cursor    optional typed keyset cursor
 * @param limit     requested page size in {@code 1..200}
 * @author yusu
 */
public record ProcessRunQuery(String namespace, String code, Set<ProcessRunStatus> statuses, ProcessRunCursor cursor,
        int limit) {
    public ProcessRunQuery {
        if (namespace != null) {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
        }
        if (code != null) {
            if (namespace == null) {
                throw new IllegalArgumentException("namespace is required when code is specified");
            }
            code = ProcessIdentifiers.requireCode(code);
        }
        statuses = DurableEnumSets.immutableCopy(statuses, ProcessRunStatus.class);
        limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
    }

    public static ProcessRunQuery firstPage(int limit) {
        return new ProcessRunQuery(null, null, Set.of(), null, limit);
    }
}
