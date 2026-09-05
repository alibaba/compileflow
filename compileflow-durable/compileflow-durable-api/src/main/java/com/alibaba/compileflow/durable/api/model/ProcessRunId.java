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

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical UUID identity of exactly one Durable Process Run.
 *
 * <p>A value is an occurrence identity, not a reusable name. A caller-supplied value must never be reused.
 * This remains true after retention has removed the original Run. Stores are
 * not required to retain permanent tombstones, and stale Run-addressed commands cannot distinguish
 * an intentionally reused value from the original occurrence.</p>
 *
 * @param value lowercase canonical UUID
 * @author yusu
 */
public record ProcessRunId(String value) {
    public ProcessRunId {
        String source = Objects.requireNonNull(value, "runId");
        try {
            String canonical = UUID.fromString(source).toString();
            if (!canonical.equals(source)) {
                throw new IllegalArgumentException("runId must be a lowercase canonical UUID");
            }
            value = canonical;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("runId must be a lowercase canonical UUID", failure);
        }
    }

    /**
     * Creates a fresh random Run identity.
     *
     * @return new Run identity
     */
    public static ProcessRunId random() {
        return new ProcessRunId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
