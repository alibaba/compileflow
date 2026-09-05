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

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.List;
import java.util.Objects;

/**
 * Disposable SemanticPlan metadata used to validate one Machine resume coordinate.
 *
 * @author yusu
 */
public record ResumeDescriptor(ResumePoint resumePoint, BoundaryKind boundaryKind,
        List<FrameDescriptor> expectedFramePath, List<String> liveStateFields) {
    public ResumeDescriptor {
        resumePoint = Objects.requireNonNull(resumePoint, "resumePoint");
        if (resumePoint.isStart()) {
            throw new IllegalArgumentException("A resume descriptor cannot target START");
        }
        if (resumePoint.isAfterElement()) {
            boundaryKind = Objects.requireNonNull(boundaryKind, "boundaryKind");
        } else if (boundaryKind != null) {
            throw new IllegalArgumentException("Only AFTER_ELEMENT may declare a boundary kind");
        }
        expectedFramePath = List.copyOf(Objects.requireNonNull(expectedFramePath, "expectedFramePath"));
        liveStateFields = List.copyOf(Objects.requireNonNull(liveStateFields, "liveStateFields"));
    }

    public enum FrameKind {
        FOR_EACH,
        PARALLEL_FOR_EACH,
        WHILE
    }

    public record FrameDescriptor(String loopId, FrameKind kind, String itemType, Integer maxIterations) {
        public FrameDescriptor {
            loopId = requireText(loopId);
            kind = Objects.requireNonNull(kind, "kind");
            itemType = DurableIdentifiers.optionalIdentity(itemType, "itemType", 2_048);
            boolean invalid = switch (kind) {
                case FOR_EACH, PARALLEL_FOR_EACH -> itemType == null || maxIterations != null;
                case WHILE -> itemType != null || maxIterations != null && maxIterations <= 0;
            };
            if (invalid) {
                throw new IllegalArgumentException("Frame descriptor bound does not match its kind");
            }
        }

        private static String requireText(String value) {
            return DurableIdentifiers.requireIdentity(value, "loopId", 128);
        }
    }
}
