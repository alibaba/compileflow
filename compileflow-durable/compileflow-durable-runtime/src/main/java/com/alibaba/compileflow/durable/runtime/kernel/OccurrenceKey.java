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
package com.alibaba.compileflow.durable.runtime.kernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity of one committed external occurrence.
 *
 * @author yusu
 */
public record OccurrenceKey(BoundaryKind kind, UUID id) {
    public OccurrenceKey {
        kind = Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNull(id, "id");
    }
}
