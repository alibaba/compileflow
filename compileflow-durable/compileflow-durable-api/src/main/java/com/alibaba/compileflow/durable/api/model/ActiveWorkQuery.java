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

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.util.Objects;

/**
 * Bounded operator query for unresolved occurrences of one exact Run.
 *
 * @param runId exact Run identity
 * @param cursor optional keyset continuation
 * @param limit maximum page size
 *
 * @author yusu
 */
public record ActiveWorkQuery(ProcessRunId runId, ActiveWorkCursor cursor, int limit) {
    public ActiveWorkQuery {
        runId = Objects.requireNonNull(runId, "runId");
        limit = DurableNumbers.requireRange(limit, 1, 200, "limit");
    }

    public static ActiveWorkQuery firstPage(ProcessRunId runId, int limit) {
        return new ActiveWorkQuery(runId, null, limit);
    }
}
