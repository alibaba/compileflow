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

/**
 * One committed occurrence result offered to its exact logical frontier in a bounded Machine Turn.
 *
 * @author yusu
 */
public record OccurrenceResult(OccurrenceKey occurrence, FrontierId frontierId, BoundaryCompletion completion) {
    public OccurrenceResult {
        occurrence = Objects.requireNonNull(occurrence, "occurrence");
        frontierId = Objects.requireNonNull(frontierId, "frontierId");
        completion = Objects.requireNonNull(completion, "completion");
        if (occurrence.kind() != completion.kind()) {
            throw new IllegalArgumentException("Occurrence identity and completion kind must match");
        }
    }
}
