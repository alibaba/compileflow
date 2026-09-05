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

import com.alibaba.compileflow.durable.runtime.program.DurableExecutionContext;
import com.alibaba.compileflow.durable.runtime.program.DurableProgram;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction-shaped runner for compiler conformance tests.
 *
 * @author yusu
 */
public final class DurableProgramTestRunner {
    private final String runId;
    private final DurableProgram program;
    private final DurableExecutionContext context;
    private Status status = Status.NEW;
    private long nextBoundarySequence = 1;
    private ActiveWait activeWait;
    private Map<String, Object> completedOutput;

    public DurableProgramTestRunner(String runId, DurableProgram program, DurableExecutionContext context) {
        this.runId = requireText(runId, "runId");
        this.program = Objects.requireNonNull(program, "program");
        this.context = Objects.requireNonNull(context, "context");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    public void start(Map<String, Object> input) throws Exception {
        requireStatus(Status.NEW);
        commit(program
            .advance(ContinuationSnapshot.start(DurableValueSnapshots.immutableMap(Objects.requireNonNull(input, "input"))),
                    List.of(), TurnBudget.defaults(), context)
            .outcome());
    }

    public void trigger(TriggerToken token, String event, Map<String, Object> payload) throws Exception {
        requireStatus(Status.WAITING);
        if (!activeWait.triggerToken().equals(token)) {
            throw new IllegalArgumentException("Stale or invalid trigger token");
        }
        BoundaryCompletion.WaitCompleted result =
                new BoundaryCompletion.WaitCompleted(token.occurrenceSequence(), token.boundaryId(), event, payload);
        ActiveWait committedWait = activeWait;
        FrontierStepResult frontierStep = program
            .advance(new ContinuationSnapshot(committedWait.checkpoint().resumePoint(), committedWait.state(),
                            committedWait.checkpoint().scopeFrames()),
                    List.of(DurableProgramTestSupport.resolved(result)), TurnBudget.defaults(), context)
            .outcome();
        commit(frontierStep);
    }

    public Status getStatus() {
        return status;
    }

    public ActiveWait requireActiveWait() {
        requireStatus(Status.WAITING);
        return activeWait;
    }

    public Map<String, Object> requireCompletedOutput() {
        requireStatus(Status.COMPLETED);
        return completedOutput;
    }

    private void commit(FrontierStepResult result) {
        if (result instanceof FrontierStepResult.Completed completed) {
            completedOutput = completed.output();
            activeWait = null;
            status = Status.COMPLETED;
            return;
        }
        if (result instanceof FrontierStepResult.TimerWaiting) {
            throw new IllegalStateException(
                    "The conformance runner does not advance wall-clock" + " timers; resume the compiled program with"
                    + " a committed TimerFired result");
        }
        FrontierStepResult.Waiting waiting = (FrontierStepResult.Waiting) result;
        long sequence = nextBoundarySequence++;
        TriggerToken token =
                new TriggerToken(runId, sequence, waiting.waitRequest().boundaryId(), UUID.randomUUID().toString());
        activeWait = new ActiveWait(token, waiting.waitRequest(), waiting.checkpoint(), waiting.state());
        status = Status.WAITING;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected runner status " + expected + ", found " + status);
        }
    }

    public enum Status {
        NEW,
        WAITING,
        COMPLETED
    }

    public record TriggerToken(String runId, long occurrenceSequence, String boundaryId, String secret) {
        public TriggerToken {
            runId = requireText(runId, "runId");
            if (occurrenceSequence <= 0) {
                throw new IllegalArgumentException("occurrenceSequence must be positive");
            }
            boundaryId = requireText(boundaryId, "boundaryId");
            secret = requireText(secret, "secret");
        }

        @Override
        public String toString() {
            return "TriggerToken{runId=" + runId + ", occurrenceSequence=" + occurrenceSequence + ", boundaryId="
                    + boundaryId + ", secret=<redacted>}";
        }
    }

    public record ActiveWait(TriggerToken triggerToken, WaitRequest request, SemanticCheckpoint checkpoint,
            Map<String, Object> state) {
        public ActiveWait {
            triggerToken = Objects.requireNonNull(triggerToken, "triggerToken");
            request = Objects.requireNonNull(request, "request");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            state = DurableValueSnapshots.immutableMap(Objects.requireNonNull(state, "state"));
        }

        @Override
        public String toString() {
            return "ActiveWait{triggerToken=" + triggerToken + ", request=" + request + ", checkpoint=<present>"
                    + ", state=<redacted>}";
        }
    }
}
