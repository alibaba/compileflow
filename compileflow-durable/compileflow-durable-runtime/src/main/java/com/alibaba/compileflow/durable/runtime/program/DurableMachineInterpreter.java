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
package com.alibaba.compileflow.durable.runtime.program;

import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.BranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ProcessCallRequest;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentBranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperations;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.EffectRequest;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierExecutionOperations;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnScheduler;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceFrontierOperations;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.ParallelForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.ScopeFrame;
import com.alibaba.compileflow.durable.runtime.kernel.SemanticCheckpoint;
import com.alibaba.compileflow.durable.runtime.kernel.TimerRequest;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.kernel.WaitRequest;
import com.alibaba.compileflow.durable.runtime.kernel.WhileFrame;
import com.alibaba.compileflow.durable.runtime.machine.BoundExpression;
import com.alibaba.compileflow.durable.runtime.machine.DurableExpressionEvaluator;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Direct realization of one exact {@link DurableMachinePlan}.
 *
 * @author yusu
 */
public final class DurableMachineInterpreter implements DurableProgram {
    private static final String PARALLEL_END_PREFIX = "\0parallel-end:";
    private final DurableMachinePlan machinePlan;
    private final DurableExpressionEvaluator expressions;
    private final Set<String> stateFields;
    private final String entryNodeId;
    private final ClassLoader classLoader;

    public DurableMachineInterpreter(DurableMachinePlan machinePlan, DurableExpressionEvaluator expressions) {
        this(machinePlan, expressions, contextClassLoader());
    }

    DurableMachineInterpreter(DurableMachinePlan machinePlan, DurableExpressionEvaluator expressions,
            ClassLoader classLoader) {
        this.machinePlan = Objects.requireNonNull(machinePlan, "machinePlan");
        this.expressions = Objects.requireNonNull(expressions, "expressions");
        this.stateFields = Set.copyOf(machinePlan.semanticPlan().getVariables().keySet());
        this.entryNodeId = machinePlan.entryNodeId();
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? DurableMachineInterpreter.class.getClassLoader() : loader;
    }

    @Override
    public MachineTurnResult advance(ContinuationSnapshot continuation, List<OccurrenceResult> availableResults,
            TurnBudget budget, DurableExecutionContext context) throws Exception {
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(availableResults, "availableResults");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(context, "context");

        MachineTurnScheduler.Selection selection =
                MachineTurnScheduler.select(continuation, availableResults, budget.maxActiveIterations());
        FrontierSnapshot active = selection.frontier();
        OccurrenceResult resolved = selection.occurrenceResult();
        List<FrontierSnapshot> frontiers = new ArrayList<>(selection.remainingFrontiers());
        if (active.multiInstanceController() != null) {
            if (!budget.tryConsumeStep()) {
                return new MachineTurnResult(new FrontierStepResult.Yielded(new SemanticCheckpoint(active.resumePoint(),
                                        active.scopeFrames()), active.variables()), active.frontierId(), continuation,
                        List.of());
            }
            frontiers.add(active);
            List<FrontierSnapshot> issued = issueMultiInstance(frontiers, active, budget.maxActiveIterations());
            return new MachineTurnResult(new FrontierStepResult.Advanced(), active.frontierId(),
                    new ContinuationSnapshot(issued), List.of());
        }

        Map<String, Object> state = FrontierExecutionOperations.mutableState(active.variables());
        List<ScopeFrame> frames = FrontierExecutionOperations.mutableFrames(active.scopeFrames());
        Set<String> writes = new LinkedHashSet<>();
        FrontierStepResult outcome;
        List<OccurrenceKey> consumed = List.of();
        ResumePoint point = active.resumePoint();
        if (point.isStart()) {
            requireNoResolvedOccurrence(resolved, "START");
            initialize(state);
            outcome = advanceFrontier(entryNodeId, state, frames, writes, activeConcurrentJoin(active), budget, context);
        } else if (point.isBeforeElement() || point.isBeforeIterationBody()) {
            requireNoResolvedOccurrence(resolved, point.kind().name());
            outcome = advanceFrontier(point.elementId(), state, frames, writes, activeConcurrentJoin(active), budget,
                    context);
        } else if (point.isAfterElement()) {
            if (resolved == null) {
                throw new IllegalArgumentException("AFTER_ELEMENT requires its occurrence result");
            }
            BoundaryCompletion completion = resolved.completion();
            if (!completion.boundaryId().equals(point.elementId())) {
                throw new IllegalArgumentException("Occurrence result does not match continuation");
            }
            DurableMachinePlan.Next next = resumeBoundary(point, completion, context, state, frames, writes);
            String target = resolveNext(next, state, frames, writes);
            outcome = advanceFrontier(target, state, frames, writes, activeConcurrentJoin(active), budget, context);
            consumed = List.of(resolved.occurrence());
        } else {
            throw new IllegalArgumentException("A parked join frontier is not executable");
        }
        return completeTurn(active, frontiers, outcome, writes, consumed, budget);
    }

    private FrontierStepResult advanceFrontier(String initialNodeId, Map<String, Object> state, List<ScopeFrame> frames,
            Set<String> writes, String activeConcurrentJoin, TurnBudget budget, DurableExecutionContext context)
            throws Exception {
        String nodeId = initialNodeId;
        while (true) {
            if (nodeId.startsWith(FrontierStepResult.WHILE_FAILURE_PREFIX)) {
                return FrontierStepResult.whileMaxIterationsExceeded(nodeId.substring(FrontierStepResult.WHILE_FAILURE_PREFIX.length()));
            }
            if (nodeId.startsWith(PARALLEL_END_PREFIX)) {
                return new FrontierStepResult.AtIterationEnd(nodeId.substring(PARALLEL_END_PREFIX.length()), state,
                        frames);
            }
            if (!budget.tryConsumeStep()) {
                return new FrontierStepResult.Yielded(SemanticCheckpoint.beforeElement(nodeId, frames), state);
            }
            if (machinePlan.isConcurrentJoin(nodeId) && nodeId.equals(activeConcurrentJoin)) {
                return new FrontierStepResult.AtJoin(nodeId, state, frames);
            }

            DurableMachinePlan.Step step = machinePlan.requireStep(nodeId);
            if (step instanceof DurableMachinePlan.Step.Advance advance) {
                nodeId = resolveNext(advance.next(), state, frames, writes);
            } else if (step instanceof DurableMachinePlan.Step.Complete) {
                if (!frames.isEmpty()) {
                    throw new IllegalArgumentException("Root end reached with active control frames");
                }
                return new FrontierStepResult.Completed(output(state), state);
            } else if (step instanceof DurableMachinePlan.Step.Replayable replayable) {
                invokeReplayable(nodeId, state, frames, writes, context);
                nodeId = resolveNext(replayable.next(), state, frames, writes);
            } else if (step instanceof DurableMachinePlan.Step.Await) {
                return wait(nodeId, state, frames, context);
            } else if (step instanceof DurableMachinePlan.Step.Timer timer) {
                return timer(nodeId, timer.scheduleExpression(), state, frames);
            } else if (step instanceof DurableMachinePlan.Step.Effect) {
                return effect(nodeId, state, frames, context);
            } else if (step instanceof DurableMachinePlan.Step.ProcessCall) {
                return processCall(nodeId, state, frames, context);
            } else if (step instanceof DurableMachinePlan.Step.ChooseOne choose) {
                nodeId = choose(nodeId, choose, state, frames);
            } else if (step instanceof DurableMachinePlan.Step.ForkAll fork) {
                return new FrontierStepResult.Forked(nodeId, fork.joinNodeId(), fork.activations(), state, frames);
            } else if (step instanceof DurableMachinePlan.Step.ForkSelected fork) {
                return forkSelected(nodeId, fork, state, frames);
            } else if (step instanceof DurableMachinePlan.Step.EnterIteration enter) {
                DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(enter.iterationId());
                if (iterationBodyActive(enter.iterationId(), iteration, frames)) {
                    OperationResult result =
                            executeIterationOperation(enter.iterationId(), iteration, state, frames, writes, context);
                    if (result.outcome() != null) {
                        return result.outcome();
                    }
                    nodeId = result.nextNodeId();
                } else {
                    IterationEntry entry = enterIteration(enter.iterationId(), iteration, state, frames, writes);
                    if (entry.outcome() != null) {
                        return entry.outcome();
                    }
                    nodeId = entry.nextNodeId();
                }
            } else if (step instanceof DurableMachinePlan.Step.BreakIteration control) {
                if (control.condition() == null || evaluateBoolean(control.condition(), state, frames)) {
                    nodeId = breakIteration(control.iterationId(), state, frames, writes);
                } else {
                    nodeId = resolveNext(control.whenNotTaken(), state, frames, writes);
                }
            } else if (step instanceof DurableMachinePlan.Step.ContinueIteration control) {
                DurableMachinePlan.Next next = control.condition() == null
                        || evaluateBoolean(control.condition(), state, frames)
                        ? new DurableMachinePlan.Next.AdvanceIteration(control.iterationId())
                        : control.whenNotTaken();
                nodeId = resolveNext(next, state, frames, writes);
            } else {
                throw unsupported(step);
            }
        }
    }

    private MachineTurnResult completeTurn(FrontierSnapshot active, List<FrontierSnapshot> frontiers,
            FrontierStepResult outcome, Set<String> writes, List<OccurrenceKey> consumed, TurnBudget budget) {
        if (outcome instanceof FrontierStepResult.Completed || outcome instanceof FrontierStepResult.Failed) {
            if (!active.branchFrames().isEmpty() || !frontiers.isEmpty()) {
                throw new IllegalStateException("A concurrent branch cannot terminate the Process");
            }
            return new MachineTurnResult(outcome, active.frontierId(), null, consumed);
        }
        if (outcome instanceof FrontierStepResult.Forked forked) {
            FrontierSnapshot parent = ConcurrentFrontierOperations.progress(active,
                    ResumePoint.beforeElement(forked.splitId()), forked.state(), forked.scopeFrames(), writes);
            frontiers.addAll(ConcurrentFrontierOperations.fork(parent, forked.splitId(), forked.joinId(),
                    forked.selectedActivations()));
            return advanced(active, frontiers, consumed);
        }
        if (outcome instanceof FrontierStepResult.ForkedEach forked) {
            FrontierSnapshot parent = ConcurrentFrontierOperations.progress(active,
                    ResumePoint.beforeElement(forked.loopId()), forked.state(), forked.scopeFrames(), writes);
            frontiers = new ArrayList<>(startMultiInstance(frontiers, parent, forked, budget.maxActiveIterations()));
            return advanced(active, frontiers, consumed);
        }
        if (outcome instanceof FrontierStepResult.AtIterationEnd iteration) {
            frontiers = new ArrayList<>(
                    completeMultiInstance(frontiers, active, iteration, budget.maxActiveIterations()));
            return advanced(active, frontiers, consumed);
        }
        if (outcome instanceof FrontierStepResult.AtJoin atJoin) {
            FrontierSnapshot parked = ConcurrentFrontierOperations.parkAtJoin(active, atJoin.joinId(), atJoin.state(),
                    atJoin.scopeFrames(), writes);
            frontiers.add(parked);
            frontiers = new ArrayList<>(ConcurrentFrontierOperations.mergeReadyGroup(frontiers, parked, atJoin.joinId()));
            return advanced(active, frontiers, consumed);
        }
        if (outcome instanceof FrontierStepResult.Advanced) {
            throw new IllegalStateException("Machine realization returned an internal coordinator outcome");
        }

        if (!(outcome instanceof FrontierStepResult.CheckpointOutcome checkpointOutcome)) {
            throw new IllegalStateException("Unknown Durable Process outcome: " + outcome);
        }
        SemanticCheckpoint checkpoint = checkpointOutcome.checkpoint();
        frontiers.add(ConcurrentFrontierOperations.progress(active, checkpoint.resumePoint(), checkpointOutcome.state(),
                checkpoint.scopeFrames(), writes));
        return new MachineTurnResult(outcome, active.frontierId(), new ContinuationSnapshot(frontiers), consumed);
    }

    private MachineTurnResult advanced(FrontierSnapshot active, List<FrontierSnapshot> frontiers,
            List<OccurrenceKey> consumed) {
        return new MachineTurnResult(new FrontierStepResult.Advanced(), active.frontierId(),
                new ContinuationSnapshot(frontiers), consumed);
    }

    private DurableMachinePlan.Next resumeBoundary(ResumePoint point, BoundaryCompletion completion,
            DurableExecutionContext context, Map<String, Object> state, List<ScopeFrame> frames, Set<String> writes) {
        ResumeDescriptor descriptor = machinePlan.requireResume(point.key());
        validateFrames(descriptor, frames);
        if (descriptor.boundaryKind() != completion.kind()) {
            throw new IllegalArgumentException("Boundary completion kind does not match ResumePoint");
        }
        String nodeId = point.elementId();
        OperationPlan operation = machinePlan.semanticPlan().requireNode(nodeId).operation();
        if (completion instanceof BoundaryCompletion.WaitCompleted completed) {
            AwaitPlan await = (AwaitPlan) operation;
            if (await.event() != null && !await.event().equals(completed.event())) {
                throw new IllegalArgumentException("Wait event does not match boundary");
            }
            FrontierExecutionOperations.applyUpdates(state, completed.payload(), stateFields);
            writes.addAll(completed.payload().keySet());
        } else if (completion instanceof BoundaryCompletion.WaitExpired) {
            if (!(operation instanceof AwaitPlan await) || await.timeout() == null) {
                throw new IllegalArgumentException("Wait does not declare a timeout");
            }
        } else if (completion instanceof BoundaryCompletion.TimerFired) {
            if (!(operation instanceof TimerPlan)) {
                throw new IllegalArgumentException("AFTER_TIMER requires a Timer operation");
            }
        } else if (completion instanceof BoundaryCompletion.EffectSucceeded succeeded) {
            if (!(operation instanceof ActionPlan)) {
                throw new IllegalArgumentException("AFTER_EFFECT requires an Effect operation");
            }
            FrontierExecutionOperations.applyUpdates(state, succeeded.output(), stateFields);
            writes.addAll(succeeded.output().keySet());
        } else if (completion instanceof BoundaryCompletion.ProcessReturned returned) {
            if (!(operation instanceof ProcessCallPlan)) {
                throw new IllegalArgumentException("PROCESS_CALL completion requires a ProcessCall operation");
            }
            Map<String, Object> updates = context.mapProcessCallOutput(nodeId, returned.output());
            FrontierExecutionOperations.applyUpdates(state, updates, stateFields);
            writes.addAll(updates.keySet());
        } else {
            throw new IllegalArgumentException("Unsupported successful boundary completion: " + completion);
        }
        return machinePlan.nextAfterBoundary(nodeId);
    }

    private void validateFrames(ResumeDescriptor descriptor, List<ScopeFrame> frames) {
        List<ResumeDescriptor.FrameDescriptor> expected = descriptor.expectedFramePath();
        if (frames.size() != expected.size()) {
            throw new IllegalArgumentException("Scope-frame depth does not match ResumePoint");
        }
        for (int index = 0; index < expected.size(); index++) {
            ResumeDescriptor.FrameDescriptor frame = expected.get(index);
            ScopeFrame actual = frames.get(index);
            if (!frame.loopId().equals(actual.loopId())) {
                throw new IllegalArgumentException("Scope-frame loop does not match ResumePoint");
            }
            boolean valid = switch (frame.kind()) {
                case FOR_EACH -> actual instanceof ForEachFrame;
                case PARALLEL_FOR_EACH -> actual instanceof ParallelForEachFrame;
                case WHILE -> actual instanceof WhileFrame
                        && (frame.maxIterations() == null || actual.position() < frame.maxIterations());
            };
            if (!valid) {
                throw new IllegalArgumentException("Scope-frame kind does not match ResumePoint");
            }
        }
    }

    private FrontierStepResult.Waiting wait(String nodeId, Map<String, Object> state, List<ScopeFrame> frames,
            DurableExecutionContext context) throws Exception {
        AwaitPlan await = (AwaitPlan) machinePlan.semanticPlan().requireNode(nodeId).operation();
        WaitRequest request = new WaitRequest(nodeId, await.event(), await.timeout(),
                context.describeWait(nodeId, FrontierExecutionOperations.readOnlyState(state), frames));
        return new FrontierStepResult.Waiting(request, SemanticCheckpoint.afterElement(nodeId, frames), state);
    }

    private FrontierStepResult.TimerWaiting timer(String nodeId, BoundExpression schedule, Map<String, Object> state,
            List<ScopeFrame> frames) {
        TimerPlan timer = (TimerPlan) machinePlan.semanticPlan().requireNode(nodeId).operation();
        TimerRequest request = switch (timer.kind()) {
            case DURATION_LITERAL -> TimerRequest.after(nodeId, Duration.parse(timer.value()));
            case DURATION_EXPRESSION -> TimerRequest.after(nodeId, evaluateDuration(schedule, state, frames));
            case WAKE_AT_LITERAL -> TimerRequest.at(nodeId, Instant.parse(timer.value()));
            case WAKE_AT_EXPRESSION -> TimerRequest.at(nodeId,
                    expressions.evaluateInstant(schedule, state, lexical(frames)));
        };
        return new FrontierStepResult.TimerWaiting(request, SemanticCheckpoint.afterElement(nodeId, frames), state);
    }

    private FrontierStepResult.EffectWaiting effect(String nodeId, Map<String, Object> state, List<ScopeFrame> frames,
            DurableExecutionContext context) {
        EffectRequest request =
                context.materializeEffectRequest(nodeId, FrontierExecutionOperations.readOnlyState(state),
                        lexical(frames));
        return new FrontierStepResult.EffectWaiting(request, SemanticCheckpoint.afterElement(nodeId, frames), state);
    }

    private FrontierStepResult.ProcessCallRequested processCall(String nodeId, Map<String, Object> state,
            List<ScopeFrame> frames, DurableExecutionContext context) {
        ProcessCallRequest request = context.materializeProcessCallRequest(nodeId,
                FrontierExecutionOperations.readOnlyState(state), lexical(frames));
        return new FrontierStepResult.ProcessCallRequested(request, SemanticCheckpoint.afterElement(nodeId, frames),
                state);
    }

    private void invokeReplayable(String nodeId, Map<String, Object> state, List<ScopeFrame> frames, Set<String> writes,
            DurableExecutionContext context) throws Exception {
        FrontierExecutionOperations.applyUpdates(state,
                context.invokeReplayable(nodeId, FrontierExecutionOperations.readOnlyState(state), lexical(frames)),
                stateFields);
        ActionPlan action = (ActionPlan) machinePlan.semanticPlan().requireNode(nodeId).operation();
        if (action.output() != null) {
            writes.add(action.output().target());
        }
    }

    private String choose(String nodeId, DurableMachinePlan.Step.ChooseOne choose, Map<String, Object> state,
            List<ScopeFrame> frames) {
        for (DurableMachinePlan.ConditionalBranch branch : choose.branches()) {
            if (evaluateBoolean(branch.condition(), state, frames)) {
                return branch.targetNodeId();
            }
        }
        if (choose.defaultTargetNodeId() != null) {
            return choose.defaultTargetNodeId();
        }
        throw new IllegalStateException("No decision branch matched at " + nodeId);
    }

    private FrontierStepResult.Forked forkSelected(String nodeId, DurableMachinePlan.Step.ForkSelected fork,
            Map<String, Object> state, List<ScopeFrame> frames) {
        Set<BranchActivation> selected = new LinkedHashSet<>();
        for (DurableMachinePlan.ConditionalBranch branch : fork.branches()) {
            if (evaluateBoolean(branch.condition(), state, frames)) {
                selected.add(branch.activation());
            }
        }
        if (selected.isEmpty()) {
            if (fork.defaultActivation() == null) {
                throw new IllegalStateException("No inclusive branch matched at " + nodeId);
            }
            selected.add(fork.defaultActivation());
        }
        return new FrontierStepResult.Forked(nodeId, fork.joinNodeId(), List.copyOf(selected), state, frames);
    }

    private IterationEntry enterIteration(String iterationId, DurableMachinePlan.Iteration iteration,
            Map<String, Object> state, List<ScopeFrame> frames, Set<String> writes) {
        if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
            if (loop.timing() == IterationPlan.ConditionTiming.BEFORE
                    && !evaluateBoolean(loop.condition(), state, frames)) {
                return IterationEntry.next(resolveNext(loop.exit(), state, frames, writes));
            }
            frames.add(new WhileFrame(iterationId, 0));
            return IterationEntry.next(bodyStart(iterationId, loop.body()));
        }
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
            List<Object> snapshot = FrontierExecutionOperations.snapshot(FrontierExecutionOperations.loopCollection(state,
                            lexical(frames), loop.collectionVariable(), iterationId), iterationId);
            if (snapshot.isEmpty()) {
                if (loop.outputTargetVariable() != null) {
                    state.put(loop.outputTargetVariable(), List.of());
                    writes.add(loop.outputTargetVariable());
                }
                return IterationEntry.next(resolveNext(loop.exit(), state, frames, writes));
            }
            if (loop.execution() == IterationPlan.Execution.PARALLEL) {
                return IterationEntry.outcome(new FrontierStepResult.ForkedEach(iterationId, snapshot, state, frames));
            }
            frames.add(new ForEachFrame(iterationId, 0, snapshot));
            resetSequentialOutput(loop, state, writes);
            return IterationEntry.next(bodyStart(iterationId, loop.body()));
        }
        throw unsupported(iteration);
    }

    private OperationResult executeIterationOperation(String iterationId, DurableMachinePlan.Iteration iteration,
            Map<String, Object> state, List<ScopeFrame> frames, Set<String> writes, DurableExecutionContext context)
            throws Exception {
        ProcessSemanticPlan.NodePlan owner = machinePlan.semanticPlan().requireNode(iterationId);
        DurableMachinePlan.Body.Operation body = (DurableMachinePlan.Body.Operation) iterationBody(iteration);
        OperationPlan operation = owner.operation();
        if (operation instanceof ActionPlan action) {
            if (action.execution() == ActionExecution.EFFECT) {
                return OperationResult.outcome(effect(iterationId, state, frames, context));
            }
            invokeReplayable(iterationId, state, frames, writes, context);
            return OperationResult.next(
                    resolveNext(new DurableMachinePlan.Next.AdvanceIteration(iterationId), state, frames, writes));
        }
        if (operation instanceof AwaitPlan) {
            return OperationResult.outcome(wait(iterationId, state, frames, context));
        }
        if (operation instanceof TimerPlan) {
            return OperationResult.outcome(timer(iterationId, body.scheduleExpression(), state, frames));
        }
        if (operation instanceof ProcessCallPlan) {
            return OperationResult.outcome(processCall(iterationId, state, frames, context));
        }
        throw unsupported(operation);
    }

    private boolean iterationBodyActive(String iterationId, DurableMachinePlan.Iteration iteration,
            List<ScopeFrame> frames) {
        return iterationBody(iteration) instanceof DurableMachinePlan.Body.Operation && !frames.isEmpty()
                && frames.get(frames.size() - 1).loopId().equals(iterationId);
    }

    private String resolveNext(DurableMachinePlan.Next next, Map<String, Object> state, List<ScopeFrame> frames,
            Set<String> writes) {
        if (next instanceof DurableMachinePlan.Next.Node node) {
            return node.nodeId();
        }
        if (next instanceof DurableMachinePlan.Next.AdvanceIteration iteration) {
            return advanceIteration(iteration.iterationId(), state, frames, writes);
        }
        throw unsupported(next);
    }

    private String advanceIteration(String iterationId, Map<String, Object> state, List<ScopeFrame> frames,
            Set<String> writes) {
        DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(iterationId);
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
            ScopeFrame raw = FrontierExecutionOperations.requireTopFrame(frames, iterationId);
            if (loop.execution() == IterationPlan.Execution.PARALLEL) {
                if (!(raw instanceof ParallelForEachFrame)) {
                    throw new IllegalArgumentException("Parallel foreach frame does not match iteration");
                }
                return PARALLEL_END_PREFIX + iterationId;
            }
            ForEachFrame frame = (ForEachFrame) raw;
            if (loop.outputTargetVariable() != null) {
                frame = frame.recordResult(state.get(loop.outputSourceVariable()));
            }
            if (frame.position() + 1 < frame.snapshotSize()) {
                FrontierExecutionOperations.replaceTopFrame(frames, iterationId, frame.advance());
                resetSequentialOutput(loop, state, writes);
                return bodyStart(iterationId, loop.body());
            }
            FrontierExecutionOperations.popTopFrame(frames, iterationId);
            publishSequentialResults(loop, frame, state, writes);
            return resolveNext(loop.exit(), state, frames, writes);
        }
        if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
            WhileFrame frame = (WhileFrame) FrontierExecutionOperations.requireTopFrame(frames, iterationId);
            int nextPosition = frame.position() + 1;
            FrontierExecutionOperations.replaceTopFrame(frames, iterationId, new WhileFrame(iterationId, nextPosition));
            if (evaluateBoolean(loop.condition(), state, frames)) {
                if (loop.maxIterations() != null && nextPosition >= loop.maxIterations()) {
                    if (loop.limitBehavior() == IterationPlan.LimitBehavior.FAIL) {
                        return FrontierStepResult.WHILE_FAILURE_PREFIX + iterationId;
                    }
                    FrontierExecutionOperations.popTopFrame(frames, iterationId);
                    return resolveNext(loop.exit(), state, frames, writes);
                }
                return bodyStart(iterationId, loop.body());
            }
            FrontierExecutionOperations.popTopFrame(frames, iterationId);
            return resolveNext(loop.exit(), state, frames, writes);
        }
        throw unsupported(iteration);
    }

    private String breakIteration(String iterationId, Map<String, Object> state, List<ScopeFrame> frames,
            Set<String> writes) {
        DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(iterationId);
        ScopeFrame raw = FrontierExecutionOperations.requireTopFrame(frames, iterationId);
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop
                && loop.execution() == IterationPlan.Execution.SEQUENTIAL) {
            ForEachFrame frame = (ForEachFrame) raw;
            if (loop.outputTargetVariable() != null) {
                frame = frame.recordResult(state.get(loop.outputSourceVariable()));
            }
            FrontierExecutionOperations.popTopFrame(frames, iterationId);
            publishSequentialResults(loop, frame, state, writes);
        } else {
            FrontierExecutionOperations.popTopFrame(frames, iterationId);
        }
        return resolveNext(iterationExit(iteration), state, frames, writes);
    }

    private void publishSequentialResults(DurableMachinePlan.Iteration.ForEach loop, ForEachFrame frame,
            Map<String, Object> state, Set<String> writes) {
        if (loop.outputTargetVariable() == null) {
            return;
        }
        state.put(loop.outputTargetVariable(), frame.completedResults());
        writes.add(loop.outputTargetVariable());
    }

    private void resetSequentialOutput(DurableMachinePlan.Iteration.ForEach loop, Map<String, Object> state,
            Set<String> writes) {
        if (loop.outputSourceVariable() == null) {
            return;
        }
        state.put(loop.outputSourceVariable(), outputSourceInitialValue(loop));
        writes.add(loop.outputSourceVariable());
    }

    private Object outputSourceInitialValue(DurableMachinePlan.Iteration.ForEach loop) {
        return loop.outputSourceVariable() == null
                ? null
                : defaultValue(machinePlan.semanticPlan().requireVariable(loop.outputSourceVariable()));
    }

    private String bodyStart(String iterationId, DurableMachinePlan.Body body) {
        if (body instanceof DurableMachinePlan.Body.Scope scope) {
            return scope.startNodeId();
        }
        if (body instanceof DurableMachinePlan.Body.Operation) {
            return iterationId;
        }
        throw unsupported(body);
    }

    private DurableMachinePlan.Body iterationBody(DurableMachinePlan.Iteration iteration) {
        if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
            return loop.body();
        }
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
            return loop.body();
        }
        throw unsupported(iteration);
    }

    private DurableMachinePlan.Next iterationExit(DurableMachinePlan.Iteration iteration) {
        if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
            return loop.exit();
        }
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
            return loop.exit();
        }
        throw unsupported(iteration);
    }

    private List<FrontierSnapshot> startMultiInstance(List<FrontierSnapshot> frontiers, FrontierSnapshot parent,
            FrontierStepResult.ForkedEach forked, int maxActive) {
        DurableMachinePlan.Iteration.ForEach loop = requireParallelForEach(forked.loopId());
        return MultiInstanceFrontierOperations.start(frontiers, parent, forked.loopId(),
                bodyStart(forked.loopId(), loop.body()), loop.collectionVariable(), loop.collectionOwnerIterationId(),
                loop.outputSourceVariable(), () -> outputSourceInitialValue(loop), forked.snapshot(), maxActive);
    }

    private List<FrontierSnapshot> issueMultiInstance(List<FrontierSnapshot> frontiers, FrontierSnapshot controller,
            int maxActive) {
        String loopId = controller.multiInstanceController().loopId();
        DurableMachinePlan.Iteration.ForEach loop = requireParallelForEach(loopId);
        return MultiInstanceFrontierOperations.issueAvailable(frontiers, controller, bodyStart(loopId, loop.body()),
                loop.collectionVariable(), loop.collectionOwnerIterationId(), loop.outputSourceVariable(),
                () -> outputSourceInitialValue(loop), maxActive);
    }

    private List<FrontierSnapshot> completeMultiInstance(List<FrontierSnapshot> frontiers, FrontierSnapshot iteration,
            FrontierStepResult.AtIterationEnd outcome, int maxActive) {
        String loopId = outcome.loopId();
        DurableMachinePlan.Iteration.ForEach loop = requireParallelForEach(loopId);
        return MultiInstanceFrontierOperations.completeIteration(frontiers, iteration, loopId,
                bodyStart(loopId, loop.body()), loop.collectionVariable(), loop.collectionOwnerIterationId(),
                loop.outputSourceVariable(), loop.outputTargetVariable(), () -> outputSourceInitialValue(loop),
                outcome.state(), maxActive, completionTarget(loop.exit()));
    }

    private MultiInstanceFrontierOperations.CompletionTarget completionTarget(DurableMachinePlan.Next exit) {
        return (state, frames, writes) -> resolveNext(exit, state, frames, writes);
    }

    private DurableMachinePlan.Iteration.ForEach requireParallelForEach(String loopId) {
        DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(loopId);
        if (!(iteration instanceof DurableMachinePlan.Iteration.ForEach loop)
                || loop.execution() != IterationPlan.Execution.PARALLEL) {
            throw new IllegalArgumentException("Iteration is not parallel foreach: " + loopId);
        }
        return loop;
    }

    private boolean evaluateBoolean(BoundExpression expression, Map<String, Object> state, List<ScopeFrame> frames) {
        return expressions.evaluateBoolean(expression, state, lexical(frames));
    }

    private Duration evaluateDuration(BoundExpression expression, Map<String, Object> state, List<ScopeFrame> frames) {
        return expressions.evaluateDuration(expression, state, lexical(frames));
    }

    private Map<String, Object> lexical(List<ScopeFrame> frames) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ScopeFrame frame : frames) {
            DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(frame.loopId());
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
                result.put(loop.itemVariable(), currentValue(frame));
                if (loop.indexVariable() != null) {
                    result.put(loop.indexVariable(), frame.position());
                }
            } else if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
                if (!(frame instanceof WhileFrame)) {
                    throw new IllegalArgumentException("While control frame has the wrong kind");
                }
                if (loop.indexVariable() != null) {
                    result.put(loop.indexVariable(), frame.position());
                }
            } else {
                throw unsupported(iteration);
            }
        }
        return FrontierExecutionOperations.immutableLexical(result);
    }

    private void initialize(Map<String, Object> state) {
        for (ProcessSemanticPlan.VariablePlan variable : machinePlan.semanticPlan().getVariables().values()) {
            if (!state.containsKey(variable.name())) {
                state.put(variable.name(), defaultValue(variable));
            }
        }
    }

    private Object defaultValue(ProcessSemanticPlan.VariablePlan variable) {
        Class<?> declared = DataTypes.getJavaClass(variable.dataType(), classLoader);
        String value = variable.defaultValue();
        if (value == null
                || value.isBlank() && declared != String.class && declared != char.class && declared != Character.class) {
            if (!declared.isPrimitive()) {
                return null;
            }
            if (declared == boolean.class) {
                return false;
            }
            if (declared == char.class) {
                return '\0';
            }
            if (declared == byte.class) {
                return (byte) 0;
            }
            if (declared == short.class) {
                return (short) 0;
            }
            if (declared == int.class) {
                return 0;
            }
            if (declared == long.class) {
                return 0L;
            }
            if (declared == float.class) {
                return 0F;
            }
            return 0D;
        }
        if (declared == String.class) {
            return value;
        }
        if (declared == char.class || declared == Character.class) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("A char default must contain one character");
            }
            return value.charAt(0);
        }
        return DataTypes.transfer(value, DataTypes.getWrapperClass(declared));
    }

    private Map<String, Object> output(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        machinePlan
            .semanticPlan()
            .getVariables()
            .values()
            .stream()
            .filter(variable -> variable.role() == ProcessSemanticPlan.VariableRole.RETURN)
            .forEach(variable -> result.put(variable.name(), state.get(variable.name())));
        return result;
    }

    private static Object currentValue(ScopeFrame frame) {
        if (frame instanceof ForEachFrame sequential) {
            return sequential.currentValue();
        }
        if (frame instanceof ParallelForEachFrame parallel) {
            return parallel.currentValue();
        }
        throw new IllegalArgumentException("Foreach control frame has the wrong kind");
    }

    private static void requireNoResolvedOccurrence(OccurrenceResult resolved, String coordinate) {
        if (resolved != null) {
            throw new IllegalArgumentException(coordinate + " cannot consume an occurrence result");
        }
    }

    private static String activeConcurrentJoin(FrontierSnapshot frontier) {
        List<BranchFrame> ancestry = frontier.branchFrames();
        if (!ancestry.isEmpty() && ancestry.get(ancestry.size() - 1) instanceof ConcurrentBranchFrame concurrent) {
            return concurrent.joinId();
        }
        return null;
    }

    private static IllegalStateException unsupported(Object value) {
        return new IllegalStateException(
                "Unsupported Durable Machine variant " + (value == null ? "null" : value.getClass().getName()));
    }

    private record IterationEntry(String nextNodeId, FrontierStepResult outcome) {
        private static IterationEntry next(String nodeId) {
            return new IterationEntry(Objects.requireNonNull(nodeId, "nodeId"), null);
        }

        private static IterationEntry outcome(FrontierStepResult outcome) {
            return new IterationEntry(null, Objects.requireNonNull(outcome, "outcome"));
        }
    }

    private record OperationResult(String nextNodeId, FrontierStepResult outcome) {
        private static OperationResult next(String nodeId) {
            return new OperationResult(Objects.requireNonNull(nodeId, "nodeId"), null);
        }

        private static OperationResult outcome(FrontierStepResult outcome) {
            return new OperationResult(null, Objects.requireNonNull(outcome, "outcome"));
        }
    }
}
