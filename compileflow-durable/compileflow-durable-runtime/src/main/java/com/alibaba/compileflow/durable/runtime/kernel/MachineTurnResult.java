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
import java.util.Objects;
import java.util.Set;

/**
 * Closed result of one bounded Machine Turn, including exact continuation and occurrence consumption.
 *
 * @author yusu
 */
public record MachineTurnResult(FrontierStepResult outcome, FrontierId frontierId, ContinuationSnapshot continuation,
        List<OccurrenceKey> consumedOccurrences) {
    public MachineTurnResult(FrontierStepResult outcome, List<OccurrenceKey> consumedOccurrences) {
        this(outcome, FrontierId.ROOT, continuationOf(outcome), consumedOccurrences);
    }

    public MachineTurnResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        frontierId = Objects.requireNonNull(frontierId, "frontierId");
        boolean terminal =
                outcome instanceof FrontierStepResult.Completed || outcome instanceof FrontierStepResult.Failed;
        if (terminal != (continuation == null)) {
            throw new IllegalArgumentException("Only a terminal Machine Turn omits its next continuation");
        }
        consumedOccurrences = List.copyOf(Objects.requireNonNull(consumedOccurrences, "consumedOccurrences"));
        if (Set.copyOf(consumedOccurrences).size() != consumedOccurrences.size()) {
            throw new IllegalArgumentException("consumedOccurrences must be unique");
        }
    }

    private static ContinuationSnapshot continuationOf(FrontierStepResult outcome) {
        if (outcome instanceof FrontierStepResult.Completed || outcome instanceof FrontierStepResult.Failed) {
            return null;
        }
        if (outcome instanceof FrontierStepResult.Yielded yielded) {
            return new ContinuationSnapshot(yielded.checkpoint().resumePoint(), yielded.state(),
                    yielded.checkpoint().scopeFrames());
        }
        if (outcome instanceof FrontierStepResult.Waiting waiting) {
            return new ContinuationSnapshot(waiting.checkpoint().resumePoint(), waiting.state(),
                    waiting.checkpoint().scopeFrames());
        }
        if (outcome instanceof FrontierStepResult.TimerWaiting waiting) {
            return new ContinuationSnapshot(waiting.checkpoint().resumePoint(), waiting.state(),
                    waiting.checkpoint().scopeFrames());
        }
        if (outcome instanceof FrontierStepResult.EffectWaiting waiting) {
            return new ContinuationSnapshot(waiting.checkpoint().resumePoint(), waiting.state(),
                    waiting.checkpoint().scopeFrames());
        }
        if (outcome instanceof FrontierStepResult.ProcessCallRequested requested) {
            return new ContinuationSnapshot(requested.checkpoint().resumePoint(), requested.state(),
                    requested.checkpoint().scopeFrames());
        }
        throw new IllegalArgumentException("Internal topology outcomes require an explicit complete continuation");
    }
}
