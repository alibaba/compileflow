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
package com.alibaba.compileflow.durable.runtime.worker;

import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.codec.RunContinuationCodec;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.ProcessCallRequest;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.EffectRequest;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation.ProcessInvocation;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation.ReturnAddress;
import com.alibaba.compileflow.durable.runtime.kernel.TimerRequest;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.kernel.WaitRequest;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableExecutionContext;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spi.store.DurableTurnStore;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Claims and token-fences one bounded same-Run Process turn.
 *
 * @author yusu
 */
public final class DurableTurnWorker {
    private final DurableTurnStore store;
    private final DurableProcessRuntimeManager processes;
    private final DurableProcessRuntimeCache programs;
    private final DurableActionInvoker actions;
    private final DurableWaitDescriptionProvider waits;
    private final WaitTokenIssuer tokens;
    private final DurableTurnWorkerOptions options;
    private final DurableLeaseRenewer leases;
    private final DurableRuntimeMetrics metrics;
    private final DurableKernelJsonCodec json = new DurableKernelJsonCodec();
    private final RunContinuationCodec continuations = new RunContinuationCodec();

    public DurableTurnWorker(DurableTurnStore store, DurableProcessRuntimeManager processes,
            DurableProcessRuntimeCache programs, DurableActionInvoker actions, DurableWaitDescriptionProvider waits,
            WaitTokenIssuer tokens, DurableTurnWorkerOptions options, DurableLeaseRenewer leases) {
        this(store, processes, programs, actions, waits, tokens, options, leases, new DurableRuntimeMetrics());
    }

    public DurableTurnWorker(DurableTurnStore store, DurableProcessRuntimeManager processes,
            DurableProcessRuntimeCache programs, DurableActionInvoker actions, DurableWaitDescriptionProvider waits,
            WaitTokenIssuer tokens, DurableTurnWorkerOptions options, DurableLeaseRenewer leases,
            DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.programs = Objects.requireNonNull(programs, "programs");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.waits = Objects.requireNonNull(waits, "waits");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.options = Objects.requireNonNull(options, "options");
        this.leases = Objects.requireNonNull(leases, "leases");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public boolean runOnce() {
        Set<UUID> readyRootProcessIds =
                programs
            .snapshot()
            .stream()
            .map(DurableProcessRuntime::processId)
            .collect(Collectors.toUnmodifiableSet());
        if (readyRootProcessIds.isEmpty()) {
            return false;
        }
        Optional<DurableStore.RunClaim> claimed = store.claimRun(
                new DurableStore.RunClaimRequest(options.workerId(), readyRootProcessIds, leases.leaseDuration()));
        if (claimed.isEmpty()) {
            return false;
        }
        metrics.record(Operation.TURN, Outcome.CLAIMED);
        DurableStore.RunClaim claim = claimed.orElseThrow();
        if (claim.cancelRequested()) {
            record(store.commitRunCancelled(claim.lease()), Outcome.SUCCESS);
            return true;
        }
        if (programs.get(claim.rootProcess().processId()).isEmpty()) {
            record(store.releaseRunAfterCapabilityLoss(claim.lease(), options.turnFaultBackoff()),
                    Outcome.READINESS_BACKOFF);
            return true;
        }
        DurableLeaseRenewer.Handle heartbeat = leases.trackRun(claim.lease());
        try {
            DurableProcessRuntime rootProgram = programs
                .get(claim.rootProcess().processId())
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed root Process is unavailable for the current turn: " + claim.rootProcess().processId()));
            record(executeTurn(claim, rootProgram), Outcome.SUCCESS);
        } catch (Exception | LinkageError fault) {
            metrics.record(Operation.TURN, Outcome.FAULT);
            if (!store.releaseRunFault(claim.lease(), options.turnFaultBackoff(), RunRetryCode.TURN_EXECUTION_FAULT)) {
                metrics.record(Operation.TURN, Outcome.LEASE_LOST);
            }
        } finally {
            heartbeat.close();
        }
        return true;
    }

    private boolean executeTurn(DurableStore.RunClaim claim, DurableProcessRuntime rootProgram) throws Exception {
        Map<UUID, DurableProcessRuntime> turnPrograms = new LinkedHashMap<>();
        turnPrograms.put(rootProgram.processId(), rootProgram);
        Function<UUID, DurableProcessRuntime> programResolver = processId -> loadTurnProgram(turnPrograms, processId);
        RunContinuation continuation = continuations.decode(claim.continuation().payload(), programResolver);
        continuation
            .processCallTargets()
            .values()
            .forEach(processId -> loadTurnProgram(turnPrograms, processId));
        Map<Long, List<DurableStore.OccurrenceResult>> storedResults = indexResults(claim.occurrenceResults());
        List<DurableStore.OccurrenceKey> consumedResults = new ArrayList<>();
        PendingReturn pendingReturn = null;
        TurnBudget budget = new TurnBudget(options.turnMaxSteps(), options.maxActiveIterations());

        while (budget.hasRemainingSteps() || pendingReturn != null) {
            ProcessInvocation invocation =
                    selectInvocation(continuation, storedResults, pendingReturn, options.maxActiveIterations());
            DurableProcessRuntime program = requireTurnProgram(turnPrograms, invocation.processId());
            List<DurableStore.OccurrenceResult> invocationResults =
                    storedResults.getOrDefault(invocation.invocationId(), List.of());
            List<OccurrenceResult> availableResults =
                    availableResults(pendingReturn, invocationResults, invocation, program);
            pendingReturn = null;
            DurableExecutionContext context = new DurableExecutionContext(program.machinePlan(),
                    actions.withScriptPrograms(program.scriptPrograms()), waits, program.valueSerializer());
            MachineTurnResult turn =
                    program.program().advance(invocation.continuation(), availableResults, budget, context);
            collectConsumedResults(turn, invocationResults, consumedResults);

            FrontierStepResult outcome = turn.outcome();
            if (outcome instanceof FrontierStepResult.Completed completed) {
                if (invocation.invocationId() == 0) {
                    if (continuation.invocations().size() != 1) {
                        throw new IllegalStateException("Root Process completed with active Process calls");
                    }
                    DurableStore.Envelope output =
                            new DurableStore.Envelope(program
                        .valueSerializer()
                        .encodeProcessResult(completed.output()));
                    return store.commitTurn(claim.lease(),
                            new DurableStore.TurnCommit(consumedResults, List.of(),
                                    new DurableStore.SucceededTurn(output)));
                }
                ReturnAddress returnAddress =
                        Objects.requireNonNull(invocation.returnAddress(), "Non-root invocation has no return address");
                continuation = continuation.remove(invocation.invocationId());
                ProcessInvocation parent = continuation.requireInvocation(returnAddress.invocationId());
                parent = parent.withContinuation(prioritize(parent.continuation(), returnAddress.frontierId()));
                continuation = continuation.replace(parent);
                pendingReturn = new PendingReturn(parent.invocationId(),
                        new OccurrenceResult(new OccurrenceKey(BoundaryKind.PROCESS_CALL,
                                        new UUID(0, invocation.invocationId())), returnAddress.frontierId(),
                                new BoundaryCompletion.ProcessReturned(invocation.invocationId(),
                                        returnAddress.elementId(), completed.output())));
                continue;
            }
            if (outcome instanceof FrontierStepResult.Failed failed) {
                return store.commitTurn(claim.lease(),
                        new DurableStore.TurnCommit(consumedResults, List.of(),
                                new DurableStore.FailedTurn(failed.code(), failed.message())));
            }

            ProcessInvocation advanced = invocation.withContinuation(Objects.requireNonNull(turn.continuation(),
                    "Non-terminal turn omitted its continuation"));
            continuation = continuation.replace(advanced);
            if (outcome instanceof FrontierStepResult.ProcessCallRequested requested) {
                ProcessCallRequest call = requested.request();
                UUID calledProcessId = continuation.requireProcessCallTarget(invocation.processId(), call.elementId());
                DurableProcessRuntime calledProgram = requireTurnProgram(turnPrograms, calledProcessId);
                var operation = program.machinePlan().semanticPlan().requireNode(call.elementId()).operation();
                if (!(operation instanceof ProcessCallPlan processCall)) {
                    throw new IllegalStateException("Requested boundary is not a Process call: " + call.elementId());
                }
                Map<String, Object> input =
                        calledProgram.valueSerializer().normalizeProcessCallInput(processCall, call.input());
                continuation = continuation.call(advanced, calledProcessId, ContinuationSnapshot.start(input),
                        turn.frontierId(), call.elementId());
                continue;
            }
            if (outcome instanceof FrontierStepResult.Advanced) {
                continue;
            }
            if (outcome instanceof FrontierStepResult.Yielded) {
                return commitContinuation(claim, continuation, consumedResults, turnPrograms);
            }
            return commitBoundary(claim, continuation, consumedResults, invocation, program, turn, turnPrograms);
        }
        return commitContinuation(claim, continuation, consumedResults, turnPrograms);
    }

    private ProcessInvocation selectInvocation(RunContinuation continuation,
            Map<Long, List<DurableStore.OccurrenceResult>> storedResults, PendingReturn pendingReturn,
            int maxActiveIterations) {
        if (pendingReturn != null) {
            return continuation.requireInvocation(pendingReturn.targetInvocationId());
        }
        for (ProcessInvocation invocation : continuation.invocations()) {
            if (!storedResults.getOrDefault(invocation.invocationId(), List.of()).isEmpty()
                    || invocation.continuation().hasRunnableFrontier(maxActiveIterations)) {
                return invocation;
            }
        }
        throw new IllegalArgumentException("Run continuation has no executable Process invocation");
    }

    private OccurrenceResult occurrenceResult(DurableStore.OccurrenceResult result, ProcessInvocation invocation,
            DurableProcessRuntime program) {
        if (!invocation.processId().equals(result.processId())
                || invocation.invocationId() != result.processInvocationId()) {
            throw new IllegalArgumentException("Occurrence result targets a different Process invocation");
        }
        OccurrenceKey occurrence = occurrenceKey(result.occurrence());
        if (result instanceof DurableStore.WaitResult wait) {
            BoundaryCompletion completion = wait.resolution() == DurableStore.WaitResolution.EXPIRED
                    ? new BoundaryCompletion.WaitExpired(wait.occurrenceSequence(), wait.elementId(), wait.deadline(),
                            wait.resolvedAt())
                    : new BoundaryCompletion.WaitCompleted(wait.occurrenceSequence(), wait.elementId(), wait.event(),
                            program.valueSerializer().decodeWaitPayload(wait.result().payload()));
            return new OccurrenceResult(occurrence, new FrontierId(wait.frontierId()), completion);
        }
        if (result instanceof DurableStore.TimerResult timer) {
            return new OccurrenceResult(occurrence, new FrontierId(timer.frontierId()),
                    new BoundaryCompletion.TimerFired(timer.occurrenceSequence(), timer.elementId(), timer.scheduledAt(),
                            timer.dueAt(), timer.resolvedAt()));
        }
        if (result instanceof DurableStore.EffectResult effect) {
            Map<String, Object> output =
                    program.valueSerializer().decodeEffectOutput(effect.elementId(), effect.result().payload());
            return new OccurrenceResult(occurrence, new FrontierId(effect.frontierId()),
                    new BoundaryCompletion.EffectSucceeded(effect.occurrenceSequence(), effect.elementId(), output));
        }
        throw new IllegalArgumentException("Unsupported occurrence result: " + result.getClass().getName());
    }

    private List<OccurrenceResult> availableResults(PendingReturn pendingReturn,
            List<DurableStore.OccurrenceResult> storedResults, ProcessInvocation invocation,
            DurableProcessRuntime program) {
        if (pendingReturn != null) {
            return List.of(pendingReturn.result());
        }
        return storedResults
            .stream()
            .map(result -> occurrenceResult(result, invocation, program))
            .toList();
    }

    private boolean commitBoundary(DurableStore.RunClaim claim, RunContinuation continuation,
            List<DurableStore.OccurrenceKey> consumedResults, ProcessInvocation invocation,
            DurableProcessRuntime program, MachineTurnResult turn, Map<UUID, DurableProcessRuntime> turnPrograms) {
        long occurrenceSequence = claim.occurrenceSequence() + 1;
        DurableStore.OccurrenceCommit issued = occurrenceCommit(claim, invocation, program, turn, occurrenceSequence);
        DurableStore.TurnState state = continuationTurn(continuation, turnPrograms);
        return store.commitTurn(claim.lease(), new DurableStore.TurnCommit(consumedResults, List.of(issued), state));
    }

    private DurableStore.OccurrenceCommit occurrenceCommit(DurableStore.RunClaim claim, ProcessInvocation invocation,
            DurableProcessRuntime program, MachineTurnResult turn, long occurrenceSequence) {
        FrontierStepResult outcome = turn.outcome();
        if (outcome instanceof FrontierStepResult.Waiting waiting) {
            return waitCommit(claim, invocation, turn, occurrenceSequence, waiting.waitRequest());
        }
        if (outcome instanceof FrontierStepResult.TimerWaiting waiting) {
            TimerRequest timer = waiting.timerRequest();
            return new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                            UUID.randomUUID()), occurrenceSequence, invocation.processId(), invocation.invocationId(),
                    turn.frontierId().value(), timer.boundaryId(),
                    timer.scheduleKind() == TimerRequest.ScheduleKind.AFTER ? timer.duration() : null,
                    timer.scheduleKind() == TimerRequest.ScheduleKind.AT ? timer.wakeAt() : null);
        }
        if (outcome instanceof FrontierStepResult.EffectWaiting waiting) {
            EffectRequest effect = waiting.effectRequest();
            byte[] input = program.valueSerializer().encodeEffectInput(effect.elementId(), effect.input());
            return new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                            UUID.randomUUID()), occurrenceSequence, invocation.processId(), invocation.invocationId(),
                    turn.frontierId().value(), effect.elementId(), effect.recoveryPlan(),
                    new DurableStore.Envelope(input));
        }
        throw new IllegalStateException("Unsupported Process boundary outcome: " + outcome.getClass().getName());
    }

    private DurableStore.WaitCommit waitCommit(DurableStore.RunClaim claim, ProcessInvocation invocation,
            MachineTurnResult turn, long occurrenceSequence, WaitRequest wait) {
        WaitTokenIssuer.IssuedWaitToken token = tokens.issue();
        UUID waitId = UUID.randomUUID();
        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("waitId", waitId.toString());
        event.put("runId", claim.lease().runId().value());
        event.put("occurrenceSequence", occurrenceSequence);
        event.put("elementId", wait.boundaryId());
        event.put("event", wait.event());
        event.put("waitToken", token.value());
        event.put("attributes", wait.attributes());
        return new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT, waitId),
                occurrenceSequence, invocation.processId(), invocation.invocationId(), turn.frontierId().value(),
                wait.boundaryId(), wait.event(), token.digest(), wait.deadlineAfter(),
                new DurableStore.Envelope(json.encode(event)));
    }

    private boolean commitContinuation(DurableStore.RunClaim claim, RunContinuation continuation,
            List<DurableStore.OccurrenceKey> consumedResults, Map<UUID, DurableProcessRuntime> turnPrograms) {
        return store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(consumedResults, List.of(), continuationTurn(continuation, turnPrograms)));
    }

    private DurableStore.TurnState continuationTurn(RunContinuation continuation,
            Map<UUID, DurableProcessRuntime> turnPrograms) {
        Function<UUID, DurableProcessRuntime> programResolver = processId -> requireTurnProgram(turnPrograms, processId);
        DurableStore.Envelope encoded = new DurableStore.Envelope(continuations.encode(continuation, programResolver));
        return continuation.hasRunnableInvocation(options.maxActiveIterations())
                ? new DurableStore.RunnableTurn(encoded)
                : new DurableStore.WaitingTurn(encoded);
    }

    private DurableProcessRuntime loadTurnProgram(Map<UUID, DurableProcessRuntime> turnPrograms, UUID processId) {
        return turnPrograms.computeIfAbsent(processId, processes::requireRuntime);
    }

    private static DurableProcessRuntime requireTurnProgram(Map<UUID, DurableProcessRuntime> turnPrograms,
            UUID processId) {
        DurableProcessRuntime program = turnPrograms.get(processId);
        if (program == null) {
            throw new IllegalStateException("Loaded Process is unavailable for the current turn: " + processId);
        }
        return program;
    }

    private static ContinuationSnapshot prioritize(ContinuationSnapshot continuation, FrontierId frontierId) {
        List<FrontierSnapshot> ordered = new ArrayList<>(continuation.frontiers().size());
        FrontierSnapshot target = null;
        for (FrontierSnapshot frontier : continuation.frontiers()) {
            if (frontier.frontierId().equals(frontierId)) {
                target = frontier;
            } else {
                ordered.add(frontier);
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Process return targets an unknown parent frontier");
        }
        ordered.add(0, target);
        return new ContinuationSnapshot(ordered);
    }

    private static Map<Long, List<DurableStore.OccurrenceResult>> indexResults(
            List<DurableStore.OccurrenceResult> results) {
        Map<Long, List<DurableStore.OccurrenceResult>> indexed = new HashMap<>();
        for (DurableStore.OccurrenceResult result : results) {
            indexed
                .computeIfAbsent(result.processInvocationId(), ignored -> new ArrayList<>())
                .add(result);
        }
        return indexed;
    }

    static void collectConsumedResults(MachineTurnResult turn, List<DurableStore.OccurrenceResult> available,
            List<DurableStore.OccurrenceKey> consumed) {
        for (OccurrenceKey occurrence : turn.consumedOccurrences()) {
            if (occurrence.kind() == BoundaryKind.PROCESS_CALL) {
                continue;
            }
            DurableStore.OccurrenceKey matched = null;
            for (DurableStore.OccurrenceResult result : available) {
                if (!occurrence.equals(occurrenceKey(result.occurrence()))) {
                    continue;
                }
                if (matched != null) {
                    throw new IllegalStateException("Consumed occurrence resolved more than once: " + occurrence);
                }
                matched = result.occurrence();
            }
            if (matched == null) {
                throw new IllegalStateException("Consumed occurrence was not supplied by the Store: " + occurrence);
            }
            consumed.add(matched);
        }
    }

    private static OccurrenceKey occurrenceKey(DurableStore.OccurrenceKey occurrence) {
        BoundaryKind kind = switch (occurrence.kind()) {
            case WAIT -> BoundaryKind.WAIT;
            case TIMER -> BoundaryKind.TIMER;
            case EFFECT -> BoundaryKind.EFFECT;
        };
        return new OccurrenceKey(kind, occurrence.id());
    }

    private void record(boolean applied, Outcome outcome) {
        metrics.record(Operation.TURN, applied ? outcome : Outcome.LEASE_LOST);
    }

    private record PendingReturn(long targetInvocationId, OccurrenceResult result) {}
}
