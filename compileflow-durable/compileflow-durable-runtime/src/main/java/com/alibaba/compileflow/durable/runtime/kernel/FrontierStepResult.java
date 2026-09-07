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
import java.util.Map;
import java.util.Objects;

/**
 * Closed result set of one compiled frontier step inside a Machine Turn.
 *
 * @author yusu
 */
public sealed interface FrontierStepResult
        permits FrontierStepResult.Completed, FrontierStepResult.CheckpointOutcome, FrontierStepResult.Failed,
        FrontierStepResult.Advanced, FrontierStepResult.Forked, FrontierStepResult.AtJoin,
        FrontierStepResult.ForkedEach, FrontierStepResult.AtIterationEnd {
    String WHILE_FAILURE_PREFIX = "\u0000failure:while-max-iterations:";
    String WHILE_MAX_ITERATIONS_EXCEEDED = "WHILE_MAX_ITERATIONS_EXCEEDED";

    /**
     * Creates the process-owned terminal failure for a deterministic While limit.
     *
     * @param loopId stable loop identity
     * @return terminal process failure
     */
    static Failed whileMaxIterationsExceeded(String loopId) {
        return new Failed(WHILE_MAX_ITERATIONS_EXCEEDED,
                "While loop maxIterations exceeded: " + requireText(loopId, "loopId"));
    }

    sealed interface CheckpointOutcome extends FrontierStepResult
            permits Yielded, Waiting, TimerWaiting, EffectWaiting, ProcessCallRequested {
        SemanticCheckpoint checkpoint();

        Map<String, Object> state();
    }

    record Completed(Map<String, Object> output, Map<String, Object> state) implements FrontierStepResult {
        public Completed {
            output = DurableValueSnapshots.immutableMap(Objects.requireNonNull(output, "output"));
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
        }

        public Completed(Map<String, Object> output) {
            this(output, output);
        }

        @Override
        public String toString() {
            return "Completed{output=<redacted>, state=<redacted>}";
        }
    }

    /**
     * Checkpoint-only progress when one operational Turn budget is exhausted.
     */
    record Yielded(SemanticCheckpoint checkpoint, Map<String, Object> state) implements CheckpointOutcome {
        public Yielded {
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            if (!checkpoint.resumePoint().isBeforeElement()) {
                throw new IllegalArgumentException("Yield must resume before one exact Process element");
            }
        }

        @Override
        public String toString() {
            return "Yielded{checkpoint=<present>, state=<redacted>}";
        }
    }

    record Waiting(WaitRequest waitRequest, SemanticCheckpoint checkpoint, Map<String, Object> state)
            implements CheckpointOutcome {
        public Waiting {
            waitRequest = Objects.requireNonNull(waitRequest, "waitRequest");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            if (!checkpoint.resumePoint().isAfterElement()
                    || !waitRequest.boundaryId().equals(checkpoint.resumePoint().elementId())) {
                throw new IllegalArgumentException("Wait request and SemanticCheckpoint must match");
            }
        }

        @Override
        public String toString() {
            return "Waiting{waitRequest=" + waitRequest + ", checkpoint=<present>, state=<redacted>}";
        }
    }

    record TimerWaiting(TimerRequest timerRequest, SemanticCheckpoint checkpoint, Map<String, Object> state)
            implements CheckpointOutcome {
        public TimerWaiting {
            timerRequest = Objects.requireNonNull(timerRequest, "timerRequest");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            if (!checkpoint.resumePoint().isAfterElement()
                    || !timerRequest.boundaryId().equals(checkpoint.resumePoint().elementId())) {
                throw new IllegalArgumentException("Timer request and SemanticCheckpoint must match");
            }
        }

        @Override
        public String toString() {
            return "TimerWaiting{timerRequest=" + timerRequest + ", checkpoint=<present>, state=<redacted>}";
        }
    }

    record EffectWaiting(EffectRequest effectRequest, SemanticCheckpoint checkpoint, Map<String, Object> state)
            implements CheckpointOutcome {
        public EffectWaiting {
            effectRequest = Objects.requireNonNull(effectRequest, "effectRequest");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            if (!checkpoint.resumePoint().isAfterElement()
                    || !effectRequest.elementId().equals(checkpoint.resumePoint().elementId())) {
                throw new IllegalArgumentException("Effect request and SemanticCheckpoint must match");
            }
        }

        @Override
        public String toString() {
            return "EffectWaiting{effectRequest=" + effectRequest + ", checkpoint=<present>, state=<redacted>}";
        }
    }

    record ProcessCallRequested(ProcessCallRequest request, SemanticCheckpoint checkpoint, Map<String, Object> state)
            implements CheckpointOutcome {
        public ProcessCallRequested {
            request = Objects.requireNonNull(request, "request");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            if (!checkpoint.resumePoint().isAfterElement()
                    || !request.elementId().equals(checkpoint.resumePoint().elementId())) {
                throw new IllegalArgumentException("Process call request and SemanticCheckpoint must match");
            }
        }

        @Override
        public String toString() {
            return "ProcessCallRequested{request=" + request + ", checkpoint=<present>, state=<redacted>}";
        }
    }

    /**
     * Process-owned terminal failure, distinct from a retryable Runtime/Turn fault.
     */
    record Failed(String code, String message) implements FrontierStepResult {
        public Failed {
            code = requireText(code, "code");
            message = DurableIdentifiers.requireHumanText(message, "message", 2048);
        }
    }

    /**
     * Internal topology progress with no external occurrence issued in this Turn.
     */
    record Advanced() implements FrontierStepResult {}

    /**
     * Internal structured split result consumed by the generated multi-frontier coordinator.
     */
    record Forked(String splitId, String joinId, List<BranchActivation> selectedActivations, Map<String, Object> state,
            List<ScopeFrame> scopeFrames) implements FrontierStepResult {
        public Forked {
            splitId = requireText(splitId, "splitId");
            joinId = requireText(joinId, "joinId");
            selectedActivations = BranchActivation.immutableSorted(selectedActivations, "selectedActivations", 1, 256);
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        }
    }

    /**
     * Internal branch arrival consumed by the generated multi-frontier coordinator.
     */
    record AtJoin(String joinId, Map<String, Object> state, List<ScopeFrame> scopeFrames)
            implements FrontierStepResult {
        public AtJoin {
            joinId = requireText(joinId, "joinId");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        }
    }

    /**
     * Internal dynamic collection split consumed by the generated frontier coordinator.
     */
    record ForkedEach(String loopId, List<Object> snapshot, Map<String, Object> state, List<ScopeFrame> scopeFrames)
            implements FrontierStepResult {
        public ForkedEach {
            loopId = requireText(loopId, "loopId");
            snapshot = DurableValueSnapshots.immutableList(Objects.requireNonNull(snapshot, "snapshot"));
            if (snapshot.isEmpty()) {
                throw new IllegalArgumentException("A parallel foreach split requires a non-empty snapshot");
            }
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        }
    }

    /**
     * Internal completion of one isolated parallel collection iteration.
     */
    record AtIterationEnd(String loopId, Map<String, Object> state, List<ScopeFrame> scopeFrames)
            implements FrontierStepResult {
        public AtIterationEnd {
            loopId = requireText(loopId, "loopId");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
            scopeFrames = List.copyOf(Objects.requireNonNull(scopeFrames, "scopeFrames"));
        }
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }
}
