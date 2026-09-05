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
 * Bounded operator query for redaction-safe Outbox delivery states.
 *
 * @param namespace optional process namespace
 * @param code      optional process code; requires namespace
 * @param statuses  optional delivery lifecycle filter
 * @param cursor    optional typed keyset cursor
 * @param limit     requested page size in {@code 1..200}
 * @author yusu
 */
public record OutboxEventQuery(String namespace, String code, Set<OutboxEventStatus> statuses, OutboxEventCursor cursor,
        int limit) {
    public OutboxEventQuery {
        if (namespace != null) {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
        }
        if (code != null) {
            code = ProcessIdentifiers.requireCode(code);
        }
        if (code != null && namespace == null) {
            throw new IllegalArgumentException("code filter requires namespace");
        }
        statuses = DurableEnumSets.immutableCopy(statuses, OutboxEventStatus.class);
        limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
    }

    /**
     * Creates a first-page query restricted to abandoned deliveries.
     *
     * @param limit requested page size in {@code 1..200}
     * @return failed-delivery first-page query
     */
    public static OutboxEventQuery abandonedFirstPage(int limit) {
        return new OutboxEventQuery(null, null, Set.of(OutboxEventStatus.ABANDONED), null, limit);
    }
}
