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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Durable Process-semantic recovery coordinate.
 *
 * @author yusu
 */
public record SemanticCheckpoint(ResumePoint resumePoint, List<ScopeFrame> scopeFrames) {
    public SemanticCheckpoint {
        resumePoint = Objects.requireNonNull(resumePoint, "resumePoint");
        scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        Set<String> loopIds = new HashSet<>();
        for (ScopeFrame frame : scopeFrames) {
            if (frame == null || !loopIds.add(frame.loopId())) {
                throw new IllegalArgumentException("Scope frame path must contain distinct non-null loops");
            }
        }
        if (resumePoint.isStart() && !scopeFrames.isEmpty()) {
            throw new IllegalArgumentException("START must not carry active scope frames");
        }
    }

    public static SemanticCheckpoint start() {
        return new SemanticCheckpoint(ResumePoint.start(), List.of());
    }

    public static SemanticCheckpoint beforeElement(String elementId, List<ScopeFrame> frames) {
        return new SemanticCheckpoint(ResumePoint.beforeElement(elementId), frames);
    }

    public static SemanticCheckpoint afterElement(String elementId, List<ScopeFrame> frames) {
        return new SemanticCheckpoint(ResumePoint.afterElement(elementId), frames);
    }
}
