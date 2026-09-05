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
package com.alibaba.compileflow.durable.api.validation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable copy law for bounded enum filters in Durable queries.
 *
 * @author yusu
 */
public final class DurableEnumSets {
    private DurableEnumSets() {
    }

    /**
     * Copies a bounded enum filter into an immutable type-safe set.
     *
     * @param values source values
     * @param type enum type
     * @param <T> enum type
     * @return immutable enum set
     */
    public static <T extends Enum<T>> Set<T> immutableCopy(Set<T> values, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        EnumSet<T> copy = EnumSet.noneOf(type);
        for (T value : values) {
            copy.add(Objects.requireNonNull(value, "values contains null"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
