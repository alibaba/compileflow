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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One logical Process continuation frontier stored inside the opaque Run continuation.
 *
 * @author yusu
 */
public record FrontierSnapshot(FrontierId frontierId, ResumePoint resumePoint, Map<String, Object> variables,
        List<ScopeFrame> scopeFrames, List<BranchFrame> branchFrames, MultiInstanceState multiInstanceController) {
    public FrontierSnapshot {
        frontierId = Objects.requireNonNull(frontierId, "frontierId");
        resumePoint = Objects.requireNonNull(resumePoint, "resumePoint");
        variables = DurableValueSnapshots.immutableMap(Objects.requireNonNull(variables, "variables"));
        scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        branchFrames = List.copyOf(Objects.requireNonNull(branchFrames, "branchFrames"));
        new SemanticCheckpoint(resumePoint, scopeFrames);
        FrontierId expected = FrontierId.ROOT;
        for (BranchFrame branch : branchFrames) {
            if (!branch.parentFrontierId().equals(expected)) {
                throw new IllegalArgumentException("Concurrent branch ancestry is not contiguous");
            }
            expected = branch.frontierId();
        }
        if (!frontierId.equals(expected)) {
            throw new IllegalArgumentException("Frontier identity does not match its concurrent branch ancestry");
        }
        if (resumePoint.isAtJoin()) {
            if (branchFrames.isEmpty()) {
                throw new IllegalArgumentException("Only a concurrent branch may be parked at a join");
            }
            BranchFrame top = branchFrames.get(branchFrames.size() - 1);
            if (!(top instanceof ConcurrentBranchFrame branch)) {
                throw new IllegalArgumentException("Only a structured concurrent branch may park at a join");
            }
            if (!branch.joinId().equals(resumePoint.elementId())) {
                throw new IllegalArgumentException("Frontier join coordinate does not match its branch lineage");
            }
        }
        if (multiInstanceController != null && !resumePoint.isBeforeElement()) {
            throw new IllegalArgumentException("A parallel foreach controller must park before its loop element");
        }
        if (multiInstanceController != null && !multiInstanceController.loopId().equals(resumePoint.elementId())) {
            throw new IllegalArgumentException("Parallel foreach controller does not match its loop coordinate");
        }
    }

    public FrontierSnapshot(FrontierId frontierId, ResumePoint resumePoint, Map<String, Object> variables,
            List<ScopeFrame> scopeFrames, List<BranchFrame> branchFrames) {
        this(frontierId, resumePoint, variables, scopeFrames, branchFrames, null);
    }

    public static FrontierSnapshot root(ResumePoint resumePoint, Map<String, Object> variables,
            List<ScopeFrame> scopeFrames) {
        return new FrontierSnapshot(FrontierId.ROOT, resumePoint, variables, scopeFrames, List.of(), null);
    }

    /**
     * Whether this frontier can execute without waiting for an external occurrence result.
     */
    public boolean runnable() {
        return multiInstanceController == null
                && (resumePoint.isStart() || resumePoint.isBeforeElement() || resumePoint.isBeforeIterationBody());
    }
}
