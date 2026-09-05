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
package com.alibaba.compileflow.durable.runtime.machine;

import static com.alibaba.compileflow.engine.core.semantic.CanonicalEncoding.append;
import static com.alibaba.compileflow.engine.core.semantic.CanonicalEncoding.sha256;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateField;
import com.alibaba.compileflow.durable.runtime.state.ProcessStateSchema;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable, source-format-neutral Durable state machine.
 *
 * <p>Steps describe only how Durable execution advances. Operation payload remains in the exact
 * embedded {@link ProcessSemanticPlan}, avoiding a third copy of action, wait, timer, and Process call
 * semantics.
 *
 * @author yusu
 */
public final class DurableMachinePlan {
    private static final String CANONICAL_FORMAT = "durable-machine-v2";
    private final ProcessSemanticPlan semanticPlan;
    private final String entryNodeId;
    private final ProcessStateSchema stateSchema;
    private final Map<String, Step> steps;
    private final Map<String, Iteration> iterations;
    private final Map<String, ResumeDescriptor> resumes;
    private final Set<String> concurrentJoinIds;
    private final String digest;

    public DurableMachinePlan(ProcessSemanticPlan semanticPlan, String entryNodeId, ProcessStateSchema stateSchema,
            Map<String, Step> steps, Map<String, Iteration> iterations) {
        this.semanticPlan = Objects.requireNonNull(semanticPlan, "semanticPlan");
        this.entryNodeId = requireText(entryNodeId, "entryNodeId");
        this.stateSchema = Objects.requireNonNull(stateSchema, "stateSchema");
        this.steps = immutableMap(steps, "steps");
        this.iterations = immutableMap(iterations, "iterations");
        this.concurrentJoinIds = deriveConcurrentJoinIds(this.steps);
        validate();
        this.resumes = resumeDescriptors(this.semanticPlan, this.stateSchema, this.steps);
        this.digest = sha256(canonicalForm());
    }

    public ProcessSemanticPlan semanticPlan() {
        return semanticPlan;
    }

    public String entryNodeId() {
        return entryNodeId;
    }

    public ProcessStateSchema stateSchema() {
        return stateSchema;
    }

    public Map<String, Step> steps() {
        return steps;
    }

    public Step requireStep(String nodeId) {
        Step step = steps.get(Objects.requireNonNull(nodeId, "nodeId"));
        if (step == null) {
            throw new IllegalArgumentException("Unknown Durable step '" + nodeId + "'");
        }
        return step;
    }

    public Map<String, Iteration> iterations() {
        return iterations;
    }

    public Iteration requireIteration(String iterationId) {
        Iteration iteration = iterations.get(Objects.requireNonNull(iterationId, "iterationId"));
        if (iteration == null) {
            throw new IllegalArgumentException("Unknown Durable iteration '" + iterationId + "'");
        }
        return iteration;
    }

    public Map<String, ResumeDescriptor> resumes() {
        return resumes;
    }

    public ResumeDescriptor requireResume(String resumeKey) {
        ResumeDescriptor resume = resumes.get(Objects.requireNonNull(resumeKey, "resumeKey"));
        if (resume == null) {
            throw new IllegalArgumentException("Unknown Durable resume point '" + resumeKey + "'");
        }
        return resume;
    }

    /**
     * Returns whether a branch reaching this node must park at a concurrent join.
     */
    public boolean isConcurrentJoin(String nodeId) {
        return concurrentJoinIds.contains(Objects.requireNonNull(nodeId, "nodeId"));
    }

    /**
     * Returns the already-lowered continuation of a boundary step.
     */
    public Next nextAfterBoundary(String nodeId) {
        Step step = requireStep(nodeId);
        if (step instanceof Step.Await await) {
            return await.next();
        }
        if (step instanceof Step.Timer timer) {
            return timer.next();
        }
        if (step instanceof Step.Effect effect) {
            return effect.next();
        }
        if (step instanceof Step.ProcessCall processCall) {
            return processCall.next();
        }
        if (step instanceof Step.EnterIteration enter && isBoundaryOperation(semanticPlan
            .requireNode(nodeId)
            .operation())) {
            return new Next.AdvanceIteration(enter.iterationId());
        }
        throw new IllegalArgumentException("Step '" + nodeId + "' is not a Durable boundary");
    }

    public String digest() {
        return digest;
    }

    /**
     * Stable serialization used for digesting and golden tests only.
     */
    public String canonicalForm() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "format", CANONICAL_FORMAT);
        append(canonical, "semantic.digest", semanticPlan.getDigest());
        for (ProcessStateField field : stateSchema.fields()) {
            append(canonical, "state.name", field.name());
            append(canonical, "state.type", field.declaredType());
            append(canonical, "state.nullable", Boolean.toString(field.nullable()));
            append(canonical, "state.startInput", Boolean.toString(field.startInput()));
        }
        sorted(steps).forEach(entry -> appendStep(canonical, entry.getKey(), entry.getValue()));
        sorted(iterations).forEach(entry -> appendIteration(canonical, entry.getKey(), entry.getValue()));
        sorted(resumes).forEach(entry -> appendResume(canonical, entry.getKey(), entry.getValue()));
        return canonical.toString();
    }

    private void validate() {
        validateStateSchema();
        String semanticEntry = rootNodeId(semanticPlan, ProcessSemanticPlan.NodeKind.START);
        if (!entryNodeId.equals(semanticEntry)) {
            throw new IllegalArgumentException(
                    "Durable entry node '" + entryNodeId + "' does not match semantic root start '" + semanticEntry + "'");
        }
        if (!steps.keySet().equals(semanticPlan.getNodes().keySet())) {
            throw new IllegalArgumentException("Durable steps must cover every semantic node exactly once");
        }
        Set<String> expectedIterations = new LinkedHashSet<>();
        semanticPlan
            .getNodes()
            .values()
            .stream()
            .filter(node -> node.iteration() != null)
            .forEach(node -> expectedIterations.add(node.id()));
        if (!iterations.keySet().equals(expectedIterations)) {
            throw new IllegalArgumentException("Durable iterations must cover every semantic iteration exactly once");
        }
        steps.forEach(this::validateStep);
        iterations.forEach(this::validateIteration);
    }

    private void validateStateSchema() {
        if (stateSchema.fields().size() != semanticPlan.getVariables().size()) {
            throw new IllegalArgumentException("Durable state schema must cover every Process variable exactly once");
        }
        for (ProcessSemanticPlan.VariablePlan variable : semanticPlan.getVariables().values()) {
            ProcessStateField field = stateSchema.requireField(variable.name());
            if (!field.declaredType().equals(variable.dataType()) || !field.nullable()
                    || field.startInput() != (variable.role() == ProcessSemanticPlan.VariableRole.PARAM)) {
                throw new IllegalArgumentException(
                        "Durable state field does not match Process variable '" + variable.name() + "'");
            }
        }
    }

    private void validateStep(String nodeId, Step step) {
        ProcessSemanticPlan.NodePlan node = semanticPlan.requireNode(nodeId);
        Objects.requireNonNull(step, "step");
        if (step instanceof Step.Advance advance) {
            requireNoOperationOrIteration(node, "advance");
            validateNext(advance.next());
        } else if (step instanceof Step.Complete) {
            if (node.kind() != ProcessSemanticPlan.NodeKind.END
                    || !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(node.scopeId())) {
                throw incompatible(nodeId, "complete");
            }
        } else if (step instanceof Step.Replayable replayable) {
            requireAction(node, ActionExecution.REPLAYABLE);
            validateNext(replayable.next());
        } else if (step instanceof Step.Await await) {
            if (!(node.operation() instanceof AwaitPlan)) {
                throw incompatible(nodeId, "await");
            }
            validateNext(await.next());
        } else if (step instanceof Step.Timer timer) {
            if (!(node.operation() instanceof TimerPlan semanticTimer)) {
                throw incompatible(nodeId, "timer");
            }
            if (semanticTimer.isLiteral() != (timer.scheduleExpression() == null)) {
                throw new IllegalArgumentException(
                        "Timer expression shape does not match semantic node '" + nodeId + "'");
            }
            validateBoundExpression(nodeId, timer.scheduleExpression());
            validateNext(timer.next());
        } else if (step instanceof Step.Effect effect) {
            requireAction(node, ActionExecution.EFFECT);
            validateNext(effect.next());
        } else if (step instanceof Step.ProcessCall processCall) {
            if (!(node.operation() instanceof ProcessCallPlan)) {
                throw incompatible(nodeId, "process call");
            }
            validateNext(processCall.next());
        } else if (step instanceof Step.ChooseOne choose) {
            requireKind(node, ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY, "exclusive decision");
            choose.branches().forEach(branch -> {
                validateBoundExpression(nodeId, branch.condition());
                requireStep(branch.targetNodeId());
            });
            requireOptionalStep(choose.defaultTargetNodeId());
        } else if (step instanceof Step.ForkAll fork) {
            requireKind(node, ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, "parallel fork");
            requireStep(fork.joinNodeId());
            fork
                .activations()
                .forEach(activation -> requireStep(activation.branchStartId()));
        } else if (step instanceof Step.ForkSelected fork) {
            requireKind(node, ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY, "inclusive fork");
            requireStep(fork.joinNodeId());
            fork.branches().forEach(branch -> {
                validateBoundExpression(nodeId, branch.condition());
                requireStep(branch.targetNodeId());
            });
            if (fork.defaultActivation() != null) {
                requireStep(fork.defaultActivation().branchStartId());
            }
        } else if (step instanceof Step.EnterIteration enter) {
            if (!nodeId.equals(enter.iterationId()) || node.iteration() == null) {
                throw incompatible(nodeId, "iteration entry");
            }
            requireIteration(enter.iterationId());
        } else if (step instanceof Step.BreakIteration control) {
            requireKind(node, ProcessSemanticPlan.NodeKind.BREAK, "break");
            requireIteration(control.iterationId());
            validateBoundExpression(nodeId, control.condition());
            validateOptionalNext(control.whenNotTaken());
        } else if (step instanceof Step.ContinueIteration control) {
            requireKind(node, ProcessSemanticPlan.NodeKind.CONTINUE, "continue");
            requireIteration(control.iterationId());
            validateBoundExpression(nodeId, control.condition());
            validateOptionalNext(control.whenNotTaken());
        } else {
            throw unsupported(step);
        }
    }

    private void validateIteration(String iterationId, Iteration iteration) {
        ProcessSemanticPlan.NodePlan owner = semanticPlan.requireNode(iterationId);
        if (iteration instanceof Iteration.While loop) {
            if (!(owner.iteration() instanceof IterationPlan.While semantic)) {
                throw incompatible(iterationId, "while iteration");
            }
            if (loop.timing() != semantic.timing() || !Objects.equals(loop.maxIterations(), semantic.maxIterations())
                    || !Objects.equals(loop.indexVariable(), semantic.indexVariable())) {
                throw new IllegalArgumentException("While iteration does not match semantic node '" + iterationId + "'");
            }
            validateBoundExpression(iterationId, loop.condition());
            validateBody(iterationId, owner, loop.body());
            validateNext(loop.exit());
        } else if (iteration instanceof Iteration.ForEach loop) {
            if (!(owner.iteration() instanceof IterationPlan.ForEach semantic)
                    || !loop.collectionVariable().equals(semantic.collectionVariable())
                    || !Objects.equals(loop.collectionOwnerIterationId(),
                            collectionOwnerIterationId(semanticPlan, owner, semantic.collectionVariable()))
                    || !loop.itemVariable().equals(semantic.itemVariable())
                    || !loop.itemType().equals(semantic.itemType())
                    || !Objects.equals(loop.indexVariable(), semantic.indexVariable())
                    || loop.execution() != semantic.execution()
                    || !Objects.equals(loop.outputTargetVariable(), semantic.outputTargetVariable())
                    || !Objects.equals(loop.outputSourceVariable(), semantic.outputSourceVariable())) {
                throw new IllegalArgumentException(
                        "Foreach iteration does not match semantic node '" + iterationId + "'");
            }
            validateBody(iterationId, owner, loop.body());
            validateNext(loop.exit());
        } else {
            throw unsupported(iteration);
        }
    }

    static String collectionOwnerIterationId(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodePlan node,
            String collectionVariable) {
        for (
                String scopeId = node.scopeId();
                !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scopeId);
                scopeId = semantics.parentScope(scopeId)) {
            IterationPlan iteration = semantics.requireNode(scopeId).iteration();
            if (iteration instanceof IterationPlan.ForEach owner && owner.itemVariable().equals(collectionVariable)) {
                return scopeId;
            }
        }
        return null;
    }

    private static String rootNodeId(ProcessSemanticPlan semantics, ProcessSemanticPlan.NodeKind kind) {
        List<String> matches = semantics
            .nodesInScope(ProcessSemanticPlan.ROOT_SCOPE_ID)
            .stream()
            .filter(node -> node.kind() == kind)
            .map(ProcessSemanticPlan.NodePlan::id)
            .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Durable machine must declare exactly one root " + kind);
        }
        return matches.get(0);
    }

    private void validateBody(String iterationId, ProcessSemanticPlan.NodePlan owner, Body body) {
        if (body instanceof Body.Scope scope) {
            if (owner.scopeBoundary() == null || !owner.scopeBoundary().startNodeId().equals(scope.startNodeId())) {
                throw new IllegalArgumentException(
                        "Iteration '" + iterationId + "' scope body does not match its semantic boundary");
            }
            requireStep(scope.startNodeId());
        } else if (body instanceof Body.Operation operation) {
            if (owner.scopeBoundary() != null || owner.operation() == null) {
                throw new IllegalArgumentException(
                        "Iteration '" + iterationId + "' operation body has no owning semantic operation");
            }
            if (owner.operation() instanceof TimerPlan timer) {
                boolean literal = timer.isLiteral();
                if (literal != (operation.scheduleExpression() == null)) {
                    throw new IllegalArgumentException(
                            "Iteration '" + iterationId + "' timer expression does not match its semantic operation");
                }
                validateBoundExpression(iterationId, operation.scheduleExpression());
            } else if (operation.scheduleExpression() != null) {
                throw new IllegalArgumentException(
                        "Iteration '" + iterationId + "' declares a timer expression for a non-timer operation");
            }
        } else {
            throw unsupported(body);
        }
    }

    private static Map<String, ResumeDescriptor> resumeDescriptors(ProcessSemanticPlan semantics,
            ProcessStateSchema stateSchema, Map<String, Step> steps) {
        List<String> liveFields = stateSchema.fields().stream().map(ProcessStateField::name).toList();
        Set<String> joins = deriveConcurrentJoinIds(steps);
        Map<String, ResumeDescriptor> result = new LinkedHashMap<>();
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            List<ResumeDescriptor.FrameDescriptor> frames = framePath(semantics, node);
            putResume(result, new ResumeDescriptor(ResumePoint.beforeElement(node.id()), null, frames, liveFields));
            if (node.scopeBoundary() == null && node.iteration() instanceof IterationPlan.ForEach loop
                    && loop.execution() == IterationPlan.Execution.PARALLEL) {
                putResume(result,
                        new ResumeDescriptor(ResumePoint.beforeIterationBody(node.id()), null,
                                appendOwnFrame(frames, node), liveFields));
            }
            if (joins.contains(node.id())) {
                putResume(result, new ResumeDescriptor(ResumePoint.atJoin(node.id()), null, frames, liveFields));
            }
            BoundaryKind boundary = boundaryKind(node, steps.get(node.id()));
            if (boundary != null) {
                List<ResumeDescriptor.FrameDescriptor> boundaryFrames = frames;
                if (steps.get(node.id()) instanceof Step.EnterIteration && node.scopeBoundary() == null) {
                    boundaryFrames = appendOwnFrame(frames, node);
                }
                putResume(result,
                        new ResumeDescriptor(ResumePoint.afterElement(node.id()), boundary, boundaryFrames, liveFields));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void putResume(Map<String, ResumeDescriptor> target, ResumeDescriptor descriptor) {
        String key = descriptor.resumePoint().key();
        if (target.putIfAbsent(key, descriptor) != null) {
            throw new IllegalArgumentException("Duplicate Durable resume point '" + key + "'");
        }
    }

    private static List<ResumeDescriptor.FrameDescriptor> framePath(ProcessSemanticPlan semantics,
            ProcessSemanticPlan.NodePlan node) {
        List<String> scopes = new ArrayList<>();
        for (
                String scope = node.scopeId();
                !ProcessSemanticPlan.ROOT_SCOPE_ID.equals(scope);
                scope = semantics.parentScope(scope)) {
            scopes.add(scope);
        }
        Collections.reverse(scopes);
        List<ResumeDescriptor.FrameDescriptor> result = new ArrayList<>();
        for (String scope : scopes) {
            IterationPlan iteration = semantics.requireNode(scope).iteration();
            if (iteration instanceof IterationPlan.While loop) {
                result.add(
                        new ResumeDescriptor.FrameDescriptor(scope, ResumeDescriptor.FrameKind.WHILE, null,
                                loop.maxIterations()));
            } else if (iteration instanceof IterationPlan.ForEach loop) {
                ResumeDescriptor.FrameKind kind = loop.execution() == IterationPlan.Execution.PARALLEL
                        ? ResumeDescriptor.FrameKind.PARALLEL_FOR_EACH
                        : ResumeDescriptor.FrameKind.FOR_EACH;
                result.add(new ResumeDescriptor.FrameDescriptor(scope, kind, loop.itemType(), null));
            }
        }
        return List.copyOf(result);
    }

    private static List<ResumeDescriptor.FrameDescriptor> appendOwnFrame(List<ResumeDescriptor.FrameDescriptor> frames,
            ProcessSemanticPlan.NodePlan owner) {
        List<ResumeDescriptor.FrameDescriptor> result = new ArrayList<>(frames);
        if (owner.iteration() instanceof IterationPlan.While loop) {
            result.add(
                    new ResumeDescriptor.FrameDescriptor(owner.id(), ResumeDescriptor.FrameKind.WHILE, null,
                            loop.maxIterations()));
        } else if (owner.iteration() instanceof IterationPlan.ForEach loop) {
            ResumeDescriptor.FrameKind kind = loop.execution() == IterationPlan.Execution.PARALLEL
                    ? ResumeDescriptor.FrameKind.PARALLEL_FOR_EACH
                    : ResumeDescriptor.FrameKind.FOR_EACH;
            result.add(new ResumeDescriptor.FrameDescriptor(owner.id(), kind, loop.itemType(), null));
        }
        return List.copyOf(result);
    }

    private static BoundaryKind boundaryKind(ProcessSemanticPlan.NodePlan node, Step step) {
        if (step instanceof Step.Await) {
            return BoundaryKind.WAIT;
        }
        if (step instanceof Step.Timer) {
            return BoundaryKind.TIMER;
        }
        if (step instanceof Step.Effect) {
            return BoundaryKind.EFFECT;
        }
        if (step instanceof Step.ProcessCall) {
            return BoundaryKind.PROCESS_CALL;
        }
        if (step instanceof Step.EnterIteration) {
            if (node.operation() instanceof AwaitPlan) {
                return BoundaryKind.WAIT;
            }
            if (node.operation() instanceof TimerPlan) {
                return BoundaryKind.TIMER;
            }
            if (node.operation() instanceof ActionPlan action && action.execution() == ActionExecution.EFFECT) {
                return BoundaryKind.EFFECT;
            }
            if (node.operation() instanceof ProcessCallPlan) {
                return BoundaryKind.PROCESS_CALL;
            }
        }
        return null;
    }

    private static boolean isBoundaryOperation(Object operation) {
        return operation instanceof AwaitPlan || operation instanceof TimerPlan || operation instanceof ProcessCallPlan
                || operation instanceof ActionPlan action && action.execution() == ActionExecution.EFFECT;
    }

    private void validateBoundExpression(String nodeId, BoundExpression expression) {
        if (expression == null) {
            return;
        }
        Map<String, ProcessSemanticPlan.VisibleVariable> visible = semanticPlan.visibleVariables(nodeId);
        for (BoundExpression.Binding binding : expression.bindings()) {
            ProcessSemanticPlan.VisibleVariable variable = visible.get(binding.name());
            if (variable == null || !variable.typeName().equals(binding.typeName())
                    || binding.source() != (variable.origin() == ProcessSemanticPlan.VisibleVariable.Origin.PROCESS
                    ? BoundExpression.Binding.Source.STATE
                    : BoundExpression.Binding.Source.FRAME)) {
                throw new IllegalArgumentException(
                        "Expression binding '" + binding.name() + "' does not match visibility at node '" + nodeId + "'");
            }
        }
    }

    private void validateNext(Next next) {
        Objects.requireNonNull(next, "next");
        if (next instanceof Next.Node node) {
            requireStep(node.nodeId());
        } else if (next instanceof Next.AdvanceIteration iteration) {
            requireIteration(iteration.iterationId());
        } else {
            throw unsupported(next);
        }
    }

    private void validateOptionalNext(Next next) {
        if (next != null) {
            validateNext(next);
        }
    }

    private void requireOptionalStep(String nodeId) {
        if (nodeId != null) {
            requireStep(nodeId);
        }
    }

    private static void requireNoOperationOrIteration(ProcessSemanticPlan.NodePlan node, String expected) {
        if (node.operation() != null || node.iteration() != null || node.kind() == ProcessSemanticPlan.NodeKind.BREAK
                || node.kind() == ProcessSemanticPlan.NodeKind.CONTINUE) {
            throw incompatible(node.id(), expected);
        }
    }

    private static ActionPlan requireAction(ProcessSemanticPlan.NodePlan node, ActionExecution execution) {
        if (!(node.operation() instanceof ActionPlan action) || action.execution() != execution) {
            throw incompatible(node.id(), execution.name().toLowerCase(Locale.ROOT) + " action");
        }
        return action;
    }

    private static void requireKind(ProcessSemanticPlan.NodePlan node, ProcessSemanticPlan.NodeKind kind,
            String expected) {
        if (node.kind() != kind) {
            throw incompatible(node.id(), expected);
        }
    }

    private static IllegalArgumentException incompatible(String nodeId, String expected) {
        return new IllegalArgumentException("Semantic node '" + nodeId + "' is not a Durable " + expected + " step");
    }

    private static IllegalStateException unsupported(Object value) {
        return new IllegalStateException("Unsupported Durable Machine variant " + value.getClass().getName());
    }

    private static Set<String> deriveConcurrentJoinIds(Map<String, Step> steps) {
        Set<String> result = new LinkedHashSet<>();
        for (Step step : steps.values()) {
            if (step instanceof Step.ForkAll fork) {
                result.add(fork.joinNodeId());
            } else if (step instanceof Step.ForkSelected fork) {
                result.add(fork.joinNodeId());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source, String name) {
        Objects.requireNonNull(source, name);
        Map<String, T> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            String exact = requireText(key, name + " key");
            if (copy.putIfAbsent(exact, Objects.requireNonNull(value, name + " value")) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " key '" + exact + "'");
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    private static <T> List<Map.Entry<String, T>> sorted(Map<String, T> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }

    private static void appendStep(StringBuilder target, String nodeId, Step step) {
        append(target, "step.node", nodeId);
        if (step instanceof Step.Advance value) {
            append(target, "step.kind", "advance");
            appendNext(target, value.next());
        } else if (step instanceof Step.Complete) {
            append(target, "step.kind", "complete");
        } else if (step instanceof Step.Replayable value) {
            append(target, "step.kind", "replayable");
            appendNext(target, value.next());
        } else if (step instanceof Step.Await value) {
            append(target, "step.kind", "await");
            appendNext(target, value.next());
        } else if (step instanceof Step.Timer value) {
            append(target, "step.kind", "timer");
            appendExpression(target, value.scheduleExpression());
            appendNext(target, value.next());
        } else if (step instanceof Step.Effect value) {
            append(target, "step.kind", "effect");
            appendNext(target, value.next());
        } else if (step instanceof Step.ProcessCall value) {
            append(target, "step.kind", "process-call");
            appendNext(target, value.next());
        } else if (step instanceof Step.ChooseOne value) {
            append(target, "step.kind", "choose-one");
            value
                .branches()
                .forEach(branch -> appendBranch(target, branch));
            append(target, "step.default", value.defaultTargetNodeId());
        } else if (step instanceof Step.ForkAll value) {
            append(target, "step.kind", "fork-all");
            append(target, "step.join", value.joinNodeId());
            value
                .activations()
                .forEach(activation -> appendActivation(target, activation));
        } else if (step instanceof Step.ForkSelected value) {
            append(target, "step.kind", "fork-selected");
            append(target, "step.join", value.joinNodeId());
            value
                .branches()
                .forEach(branch -> appendBranch(target, branch));
            appendActivation(target, value.defaultActivation());
        } else if (step instanceof Step.EnterIteration value) {
            append(target, "step.kind", "enter-iteration");
            append(target, "step.iteration", value.iterationId());
        } else if (step instanceof Step.BreakIteration value) {
            append(target, "step.kind", "break-iteration");
            append(target, "step.iteration", value.iterationId());
            appendExpression(target, value.condition());
            appendNext(target, value.whenNotTaken());
        } else if (step instanceof Step.ContinueIteration value) {
            append(target, "step.kind", "continue-iteration");
            append(target, "step.iteration", value.iterationId());
            appendExpression(target, value.condition());
            appendNext(target, value.whenNotTaken());
        } else {
            throw unsupported(step);
        }
    }

    private static void appendIteration(StringBuilder target, String iterationId, Iteration iteration) {
        append(target, "iteration.id", iterationId);
        if (iteration instanceof Iteration.While loop) {
            append(target, "iteration.kind", "while");
            appendExpression(target, loop.condition());
            append(target, "iteration.timing", loop.timing().name());
            append(target, "iteration.max", loop.maxIterations() == null ? null : loop.maxIterations().toString());
            append(target, "iteration.limitBehavior", loop.limitBehavior().name());
            append(target, "iteration.index", loop.indexVariable());
            appendBody(target, loop.body());
            appendNext(target, loop.exit());
        } else if (iteration instanceof Iteration.ForEach loop) {
            append(target, "iteration.kind", "foreach");
            append(target, "iteration.collection", loop.collectionVariable());
            append(target, "iteration.collectionOwner", loop.collectionOwnerIterationId());
            append(target, "iteration.item", loop.itemVariable());
            append(target, "iteration.itemType", loop.itemType());
            append(target, "iteration.index", loop.indexVariable());
            append(target, "iteration.execution", loop.execution().name());
            append(target, "iteration.output.source", loop.outputSourceVariable());
            append(target, "iteration.output.target", loop.outputTargetVariable());
            appendBody(target, loop.body());
            appendNext(target, loop.exit());
        } else {
            throw unsupported(iteration);
        }
    }

    private static void appendBody(StringBuilder target, Body body) {
        if (body instanceof Body.Scope scope) {
            append(target, "iteration.body", "scope");
            append(target, "iteration.body.start", scope.startNodeId());
        } else if (body instanceof Body.Operation operation) {
            append(target, "iteration.body", "operation");
            appendExpression(target, operation.scheduleExpression());
        } else {
            throw unsupported(body);
        }
    }

    private static void appendBranch(StringBuilder target, ConditionalBranch branch) {
        append(target, "step.branch.ordinal", Integer.toString(branch.ordinal()));
        appendExpression(target, branch.condition());
        append(target, "step.branch.target", branch.targetNodeId());
    }

    private static void appendActivation(StringBuilder target, BranchActivation activation) {
        append(target, "step.activation.ordinal", activation == null ? null : Integer.toString(activation.ordinal()));
        append(target, "step.activation.target", activation == null ? null : activation.branchStartId());
    }

    private static void appendExpression(StringBuilder target, BoundExpression expression) {
        if (expression == null) {
            append(target, "expression", null);
            return;
        }
        append(target, "expression.source", expression.source());
        append(target, "expression.result", expression.resultKind().name());
        expression.bindings().forEach(binding -> {
            append(target, "expression.binding.name", binding.name());
            append(target, "expression.binding.type", binding.typeName());
            append(target, "expression.binding.source", binding.source().name());
        });
    }

    private static void appendNext(StringBuilder target, Next next) {
        if (next == null) {
            append(target, "next.kind", null);
        } else if (next instanceof Next.Node node) {
            append(target, "next.kind", "node");
            append(target, "next.target", node.nodeId());
        } else if (next instanceof Next.AdvanceIteration iteration) {
            append(target, "next.kind", "advance-iteration");
            append(target, "next.target", iteration.iterationId());
        } else {
            throw unsupported(next);
        }
    }

    private static void appendResume(StringBuilder target, String key, ResumeDescriptor resume) {
        append(target, "resume.key", key);
        append(target, "resume.kind", resume.resumePoint().kind().name());
        append(target, "resume.node", resume.resumePoint().elementId());
        append(target, "resume.boundary", resume.boundaryKind() == null ? null : resume.boundaryKind().name());
        resume.expectedFramePath().forEach(frame -> {
            append(target, "resume.frame.loop", frame.loopId());
            append(target, "resume.frame.kind", frame.kind().name());
            append(target, "resume.frame.itemType", frame.itemType());
            append(target, "resume.frame.max", frame.maxIterations() == null ? null : frame.maxIterations().toString());
        });
        resume
            .liveStateFields()
            .forEach(field -> append(target, "resume.state", field));
    }

    private static String requireText(String value, String name) {
        return SemanticText.requireIdentity(value, name);
    }

    public sealed interface Step
            permits Step.Advance, Step.Complete, Step.Replayable, Step.Await, Step.Timer, Step.Effect,
            Step.ProcessCall, Step.ChooseOne, Step.ForkAll, Step.ForkSelected, Step.EnterIteration,
            Step.BreakIteration, Step.ContinueIteration {
        record Advance(Next next) implements Step {
            public Advance {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record Complete() implements Step {}

        record Replayable(Next next) implements Step {
            public Replayable {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record Await(Next next) implements Step {
            public Await {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record Timer(BoundExpression scheduleExpression, Next next) implements Step {
            public Timer {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record Effect(Next next) implements Step {
            public Effect {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record ProcessCall(Next next) implements Step {
            public ProcessCall {
                next = Objects.requireNonNull(next, "next");
            }
        }

        record ChooseOne(List<ConditionalBranch> branches, String defaultTargetNodeId) implements Step {
            public ChooseOne {
                branches = immutableBranches(branches);
                defaultTargetNodeId = optionalText(defaultTargetNodeId, "defaultTargetNodeId");
                if (branches.isEmpty() && defaultTargetNodeId == null) {
                    throw new IllegalArgumentException("ChooseOne requires a conditional or default branch");
                }
            }
        }

        record ForkAll(String joinNodeId, List<BranchActivation> activations) implements Step {
            public ForkAll {
                joinNodeId = requireText(joinNodeId, "joinNodeId");
                activations = immutableActivations(activations, 2);
            }
        }

        record ForkSelected(String joinNodeId, List<ConditionalBranch> branches, BranchActivation defaultActivation)
                implements Step {
            public ForkSelected {
                joinNodeId = requireText(joinNodeId, "joinNodeId");
                branches = immutableBranches(branches);
                if (branches.isEmpty() && defaultActivation == null) {
                    throw new IllegalArgumentException("ForkSelected requires a conditional or default branch");
                }
            }
        }

        record EnterIteration(String iterationId) implements Step {
            public EnterIteration {
                iterationId = requireText(iterationId, "iterationId");
            }
        }

        record BreakIteration(String iterationId, BoundExpression condition, Next whenNotTaken) implements Step {
            public BreakIteration {
                iterationId = requireText(iterationId, "iterationId");
                if ((condition == null) != (whenNotTaken == null)) {
                    throw new IllegalArgumentException("whenNotTaken is required exactly when break has a condition");
                }
            }
        }

        record ContinueIteration(String iterationId, BoundExpression condition, Next whenNotTaken) implements Step {
            public ContinueIteration {
                iterationId = requireText(iterationId, "iterationId");
                if ((condition == null) != (whenNotTaken == null)) {
                    throw new IllegalArgumentException("whenNotTaken is required exactly when continue has a condition");
                }
            }
        }
    }

    public sealed interface Next permits Next.Node, Next.AdvanceIteration {
        record Node(String nodeId) implements Next {
            public Node {
                nodeId = requireText(nodeId, "nodeId");
            }
        }

        record AdvanceIteration(String iterationId) implements Next {
            public AdvanceIteration {
                iterationId = requireText(iterationId, "iterationId");
            }
        }
    }

    public record ConditionalBranch(int ordinal, BoundExpression condition, String targetNodeId) {
        public ConditionalBranch {
            if (ordinal < 0) {
                throw new IllegalArgumentException("conditional branch ordinal must be non-negative");
            }
            condition = Objects.requireNonNull(condition, "condition");
            targetNodeId = requireText(targetNodeId, "targetNodeId");
        }

        public BranchActivation activation() {
            return new BranchActivation(ordinal, targetNodeId);
        }
    }

    public sealed interface Iteration permits Iteration.While, Iteration.ForEach {
        record While(BoundExpression condition, IterationPlan.ConditionTiming timing, Integer maxIterations,
                IterationPlan.LimitBehavior limitBehavior, String indexVariable, Body body, Next exit)
                implements Iteration {
            public While {
                condition = Objects.requireNonNull(condition, "condition");
                timing = Objects.requireNonNull(timing, "timing");
                if (maxIterations != null && maxIterations <= 0) {
                    throw new IllegalArgumentException("maxIterations must be positive when declared");
                }
                limitBehavior = Objects.requireNonNull(limitBehavior, "limitBehavior");
                indexVariable = optionalText(indexVariable, "indexVariable");
                body = Objects.requireNonNull(body, "body");
                exit = Objects.requireNonNull(exit, "exit");
            }
        }

        record ForEach(String collectionVariable, String collectionOwnerIterationId, String itemVariable,
                String itemType, String indexVariable, IterationPlan.Execution execution, String outputSourceVariable,
                String outputTargetVariable, Body body, Next exit) implements Iteration {
            public ForEach {
                collectionVariable = requireText(collectionVariable, "collectionVariable");
                collectionOwnerIterationId = optionalText(collectionOwnerIterationId, "collectionOwnerIterationId");
                itemVariable = requireText(itemVariable, "itemVariable");
                itemType = requireText(itemType, "itemType");
                indexVariable = optionalText(indexVariable, "indexVariable");
                execution = Objects.requireNonNull(execution, "execution");
                outputTargetVariable = optionalText(outputTargetVariable, "outputTargetVariable");
                outputSourceVariable = optionalText(outputSourceVariable, "outputSourceVariable");
                if ((outputTargetVariable == null) != (outputSourceVariable == null)) {
                    throw new IllegalArgumentException(
                            "outputTargetVariable and outputSourceVariable must be declared together");
                }
                body = Objects.requireNonNull(body, "body");
                exit = Objects.requireNonNull(exit, "exit");
            }
        }
    }

    public sealed interface Body permits Body.Scope, Body.Operation {
        record Scope(String startNodeId) implements Body {
            public Scope {
                startNodeId = requireText(startNodeId, "startNodeId");
            }
        }

        /**
         * Owning activity operation; only a timer's target-specific expression binding is retained.
         */
        record Operation(BoundExpression scheduleExpression) implements Body {}
    }

    private static List<ConditionalBranch> immutableBranches(List<ConditionalBranch> source) {
        return List.copyOf(Objects.requireNonNull(source, "branches"));
    }

    private static List<BranchActivation> immutableActivations(List<BranchActivation> source, int minimumSize) {
        return BranchActivation.immutableSorted(source, "activations", minimumSize, 256);
    }

    private static String optionalText(String value, String name) {
        return SemanticText.optionalIdentity(value, name);
    }
}
