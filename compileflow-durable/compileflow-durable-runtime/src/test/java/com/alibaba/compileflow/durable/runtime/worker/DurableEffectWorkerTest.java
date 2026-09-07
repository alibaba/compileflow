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

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.api.effect.EffectReconcileOutcome;
import com.alibaba.compileflow.durable.api.model.EffectReadinessCode;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.program.DurableCompilerTestSupport;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.EffectPolicyPlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurableEffectWorkerTest {
    private static final ProcessRef.Version PROCESS = ProcessRef.version("test", "effect.worker", "v1");
    private static final UUID PROCESS_ID = UUID.fromString("88d56466-3649-4cdf-a85e-5bf75c9c273c");
    private static final DurableStore.RunProcess RUN_PROCESS =
            new DurableStore.RunProcess(PROCESS_ID, PROCESS.namespace(), PROCESS.code(), PROCESS);
    private static final ProcessRunId RUN_ID = new ProcessRunId("3ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T00:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofHours(1);
    private static final Duration READINESS_BACKOFF = Duration.ofSeconds(5);

    @Test
    void returnsFalseAfterCheckingBothEffectLanesWhenNoWorkExists() {
        CapturingStore store = new CapturingStore(null);

        assertThat(run(store, new InMemoryDurableProcessRuntimeCache())).isFalse();
        assertThat(store.claimOperations)
            .containsExactly(DurableStore.EffectOperation.DISPATCH, DurableStore.EffectOperation.RECONCILE);
        assertThat(store.transition).isNull();
    }

    @Test
    void alternatesThePreferredLaneSoReconciliationCannotStarve() {
        CapturingStore store = new CapturingStore(null);
        DurableActionInvoker actions = new DurableActionInvoker(ProcessComponentResolver.disabled(),
                ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader());
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store.proxy(), LEASE_DURATION)) {
            DurableEffectWorker worker = new DurableEffectWorker(store.proxy(), new InMemoryDurableProcessRuntimeCache(),
                    actions, new DurableEffectWorkerOptions("effect-worker", READINESS_BACKOFF), renewer);

            assertThat(worker.runOnce()).isFalse();
            assertThat(worker.runOnce()).isFalse();
        }

        assertThat(store.claimOperations)
            .containsExactly(DurableStore.EffectOperation.DISPATCH, DurableStore.EffectOperation.RECONCILE,
                    DurableStore.EffectOperation.RECONCILE, DurableStore.EffectOperation.DISPATCH);
    }

    @Test
    void successfulDispatchCompletesTheClaimedOccurrence() {
        DurableProcessRuntime program = loadProgram("dispatchSuccess", "");
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0));

        assertThat(run(store, cache(program))).isTrue();
        assertThat(store.transition).isEqualTo(Transition.COMPLETED);
        assertThat(store.result.bytes()).isNotEmpty();
    }

    @Test
    void anyPostInvocationFailureBecomesUnknown() {
        DurableProcessRuntime program = loadProgram("dispatchFailure", "");
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0));

        assertThat(run(store, cache(program))).isTrue();
        assertThat(store.transition).isEqualTo(Transition.UNKNOWN);
        assertThat(store.delay).isZero();
        assertThat(store.reason).isEqualTo(IllegalStateException.class.getName());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationAfterDispatchUsesFencedUnknownCompletion(boolean leaseCurrent) {
        DurableProcessRuntime program = loadProgram("dispatchCancelled", "");
        CapturingStore store =
                new CapturingStore(claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0), leaseCurrent);
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();

        assertThat(run(store, cache(program), metrics)).isTrue();
        assertThat(store.transition).isEqualTo(Transition.UNKNOWN);
        assertThat(store.delay).isZero();
        assertThat(store.reason).isEqualTo(ScriptException.class.getName());
        assertThat(metrics.count(Operation.EFFECT_DISPATCH, leaseCurrent ? Outcome.UNKNOWN : Outcome.LEASE_LOST)).isOne();
        assertThat(metrics.count(Operation.EFFECT_DISPATCH, Outcome.SUCCESS)).isZero();
    }

    @Test
    void springResolutionIsBeyondPossibleDispatchAndFailureBecomesUnknown() {
        DurableProcessRuntime program = loadSpringProgram();
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0));
        AtomicInteger resolutions = new AtomicInteger();
        DurableActionInvoker actions = new DurableActionInvoker(new ProcessComponentResolver() {
                    @Override
                    public <T> T resolve(String name, Class<T> requiredType) {
                        resolutions.incrementAndGet();
                        throw new IllegalStateException("lazy bean construction failed");
                    }
                }, ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader());

        assertThat(actions.isStructurallyReady(
                (ActionPlan) program.machinePlan().semanticPlan().requireNode("effect").operation()))
            .isTrue();
        assertThat(resolutions).hasValue(0);
        assertThat(run(store, cache(program), new DurableRuntimeMetrics(), actions)).isTrue();

        assertThat(resolutions).hasValue(1);
        assertThat(store.transition).isEqualTo(Transition.UNKNOWN);
        assertThat(store.reason).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void missingCapabilityBacksOffButInvalidPersistedInputRequiresReview() {
        DurableProcessRuntime program = loadProgram("dispatchSuccess", "");
        DurableStore.EffectClaim validClaim = claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0);
        CapturingStore missingProgram = new CapturingStore(validClaim);

        assertThat(run(missingProgram, new InMemoryDurableProcessRuntimeCache())).isTrue();
        assertThat(missingProgram.transition).isEqualTo(Transition.RELEASED);
        assertThat(missingProgram.readinessCode).isEqualTo(EffectReadinessCode.PROCESS_RUNTIME_NOT_READY);
        assertThat(missingProgram.delay).isEqualTo(READINESS_BACKOFF);

        DurableStore.EffectClaim invalidInput = new DurableStore.EffectClaim(validClaim.lease(), validClaim.runId(),
                validClaim.rootProcess(), validClaim.processId(), validClaim.frontierId(), validClaim.elementId(),
                validClaim.operation(), validClaim.dispatchAttempts(), validClaim.reconcileAttempts(),
                new DurableStore.Envelope("not-json".getBytes(StandardCharsets.UTF_8)), validClaim.recoveryPlan(),
                validClaim.createdAt(), validClaim.unknownSince(), validClaim.authorityTime(), validClaim.leaseUntil());
        CapturingStore malformed = new CapturingStore(invalidInput);

        assertThat(run(malformed, cache(program))).isTrue();
        assertThat(malformed.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(malformed.reason).isEqualTo("EFFECT_INPUT_INVALID");
        assertThat(malformed.readinessCode).isNull();
    }

    @Test
    void manualRecoveryRequiresOperatorReviewWithoutCallingApplicationCode() {
        DurableProcessRuntime program = loadProgram("dispatchFailure", "");
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, 1, 1));

        assertThat(run(store, cache(program))).isTrue();
        assertThat(store.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(store.reason).isEqualTo("MANUAL_EFFECT_OUTCOME_UNKNOWN");
    }

    @Test
    void retryRecoveryRedispatchesOnlyWhileAttemptsRemain() {
        String policy = "<effectPolicy recovery=\"retry\" maxAttempts=\"3\" recoveryDelay=\"PT2S\"/>";
        DurableProcessRuntime program = loadProgram("dispatchFailure", policy);
        CapturingStore retry = new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, 2, 1));

        assertThat(run(retry, cache(program))).isTrue();
        assertThat(retry.transition).isEqualTo(Transition.REDISPATCHED);
        assertThat(retry.delay).isEqualTo(Duration.ofSeconds(2));

        CapturingStore exhausted = new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, 3, 1));
        assertThat(run(exhausted, cache(program))).isTrue();
        assertThat(exhausted.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(exhausted.reason).isEqualTo("EFFECT_DISPATCH_ATTEMPTS_EXHAUSTED");
    }

    @Test
    void committedRecoveryPlanOverridesCurrentProgramPolicy() {
        String currentPolicy = "<effectPolicy recovery=\"retry\" maxAttempts=\"3\" recoveryDelay=\"PT2S\"/>";
        DurableProcessRuntime program = loadProgram("dispatchFailure", currentPolicy);
        DurableStore.EffectClaim current = claim(program, DurableStore.EffectOperation.RECONCILE, 1, 1);
        DurableStore.EffectClaim frozenManual = new DurableStore.EffectClaim(current.lease(), current.runId(),
                current.rootProcess(), current.processId(), current.frontierId(), current.elementId(),
                current.operation(), current.dispatchAttempts(), current.reconcileAttempts(), current.input(),
                EffectRecoveryPlan.manual(), current.createdAt(), current.unknownSince(), current.authorityTime(),
                current.leaseUntil());
        CapturingStore store = new CapturingStore(frozenManual);

        assertThat(run(store, cache(program))).isTrue();
        assertThat(store.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(store.reason).isEqualTo("MANUAL_EFFECT_OUTCOME_UNKNOWN");
    }

    @Test
    void unavailableReconcileActionReleasesBeforeInvocation() {
        DurableProcessRuntime program = loadProgram("dispatchFailure", reconcilePolicy("reconcileUnknown"));
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, 1, 1));
        DurableProcessRuntime withoutReconcileAction = withoutReconcileCapability(program);

        assertThat(run(store, cache(withoutReconcileAction))).isTrue();
        assertThat(store.transition).isEqualTo(Transition.RELEASED);
        assertThat(store.delay).isEqualTo(READINESS_BACKOFF);
        assertThat(store.readinessCode).isEqualTo(EffectReadinessCode.RECONCILE_ACTION_NOT_READY);
    }

    @Test
    void reconcileEvidenceMapsToCompletionRedispatchOrContinuedUnknown() {
        assertReconcile("reconcileConfirmed", 1, Transition.COMPLETED, null);
        assertReconcile("reconcileNotExecuted", 1, Transition.REDISPATCHED, null);
        assertReconcile("reconcileUnknown", 1, Transition.UNKNOWN, "RECONCILE_STILL_UNKNOWN");
        assertReconcile("reconcileInvalid", 1, Transition.UNKNOWN, "INVALID_RECONCILE_RESULT");
        assertReconcile("reconcileFailure", 1, Transition.UNKNOWN, IllegalArgumentException.class.getName());
    }

    @Test
    void recoveryDeadlineAndAttemptLimitFailClosedToReview() {
        DurableProcessRuntime program = loadProgram("dispatchFailure", reconcilePolicy("reconcileUnknown"));
        DurableStore.EffectClaim activeClaim = claim(program, DurableStore.EffectOperation.RECONCILE, 1, 1);
        DurableStore.EffectClaim expired = new DurableStore.EffectClaim(activeClaim.lease(), activeClaim.runId(),
                activeClaim.rootProcess(), activeClaim.processId(), activeClaim.frontierId(), activeClaim.elementId(),
                activeClaim.operation(), activeClaim.dispatchAttempts(), activeClaim.reconcileAttempts(),
                activeClaim.input(), activeClaim.recoveryPlan(), activeClaim.createdAt(), activeClaim.unknownSince(),
                activeClaim.createdAt().plus(Duration.ofHours(1)), activeClaim.leaseUntil());
        CapturingStore deadline = new CapturingStore(expired);

        assertThat(run(deadline, cache(program))).isTrue();
        assertThat(deadline.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(deadline.reason).isEqualTo("EFFECT_DEADLINE_EXCEEDED");

        CapturingStore attempts = new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, 1, 3));
        assertThat(run(attempts, cache(program))).isTrue();
        assertThat(attempts.transition).isEqualTo(Transition.REVIEW_REQUIRED);
        assertThat(attempts.reason).isEqualTo("EFFECT_RECONCILE_ATTEMPTS_EXHAUSTED");
    }

    @Test
    void staleCompletionIsObservedAsLeaseLossInsteadOfSuccess() {
        DurableProcessRuntime program = loadProgram("dispatchSuccess", "");
        CapturingStore store = new CapturingStore(claim(program, DurableStore.EffectOperation.DISPATCH, 1, 0), false);
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();

        assertThat(run(store, cache(program), metrics)).isTrue();

        assertThat(metrics.count(Operation.EFFECT_DISPATCH, Outcome.CLAIMED)).isOne();
        assertThat(metrics.count(Operation.EFFECT_DISPATCH, Outcome.LEASE_LOST)).isOne();
        assertThat(metrics.count(Operation.EFFECT_DISPATCH, Outcome.SUCCESS)).isZero();
    }

    private void assertReconcile(String method, int dispatchAttempts, Transition expectedTransition,
            String expectedReason) {
        DurableProcessRuntime program = loadProgram("dispatchFailure", reconcilePolicy(method));
        CapturingStore store =
                new CapturingStore(claim(program, DurableStore.EffectOperation.RECONCILE, dispatchAttempts, 1));

        assertThat(run(store, cache(program))).isTrue();
        assertThat(store.transition).isEqualTo(expectedTransition);
        if (expectedReason != null) {
            assertThat(store.reason).isEqualTo(expectedReason);
        }
    }

    private boolean run(CapturingStore store, InMemoryDurableProcessRuntimeCache cache) {
        return run(store, cache, new DurableRuntimeMetrics());
    }

    private boolean run(CapturingStore store, InMemoryDurableProcessRuntimeCache cache, DurableRuntimeMetrics metrics) {
        DurableActionInvoker actions = new DurableActionInvoker(ProcessComponentResolver.disabled(),
                ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader());
        return run(store, cache, metrics, actions);
    }

    private boolean run(CapturingStore store, InMemoryDurableProcessRuntimeCache cache, DurableRuntimeMetrics metrics,
            DurableActionInvoker actions) {
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store.proxy(), LEASE_DURATION)) {
            return new DurableEffectWorker(store.proxy(), cache, actions,
                    new DurableEffectWorkerOptions("effect-worker", READINESS_BACKOFF), renewer, metrics)
                .runOnce();
        }
    }

    private InMemoryDurableProcessRuntimeCache cache(DurableProcessRuntime program) {
        InMemoryDurableProcessRuntimeCache cache = new InMemoryDurableProcessRuntimeCache();
        cache.put(program);
        return cache;
    }

    private DurableProcessRuntime withoutReconcileCapability(DurableProcessRuntime program) {
        DurableMachinePlan original = program.machinePlan();
        ProcessSemanticPlan semantics = original.semanticPlan();
        ProcessSemanticPlan.NodePlan effectNode = semantics.requireNode("effect");
        ActionPlan effect = (ActionPlan) effectNode.operation();
        EffectPolicyPlan policy = effect.effectPolicy();
        ReconcilePlan reconcile = policy.reconcileAction();
        ReconcilePlan unavailableReconcile =
                new ReconcilePlan(new ActionInvocation.Script("unavailable-reconcile", "result"), reconcile.inputs());
        ActionPlan unavailableEffect = new ActionPlan(effect.execution(), effect.invocation(), effect.inputs(),
                effect.output(), effect.invocationPolicy(),
                new EffectPolicyPlan(policy.recoveryPlanVariable(), policy.recovery(), policy.maxAttempts(),
                        policy.maxReconcileAttempts(), policy.recoveryDelay(), policy.maxRecoveryDuration(),
                        unavailableReconcile));
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new java.util.LinkedHashMap<>(semantics.getNodes());
        nodes.put(effectNode.id(),
                new ProcessSemanticPlan.NodePlan(effectNode.id(), effectNode.kind(), effectNode.scopeId(),
                        effectNode.scopeBoundary(), effectNode.outgoingTransitions(), effectNode.controlCondition(),
                        unavailableEffect, effectNode.iteration()));
        ProcessSemanticPlan unavailableSemantics =
                new ProcessSemanticPlan(semantics.getProcessCode(), semantics.getVariables(), nodes);
        DurableMachinePlan unavailableMachine = new DurableMachinePlan(unavailableSemantics, original.entryNodeId(),
                original.stateSchema(), original.steps(), original.iterations());
        return new DurableProcessRuntime(program.processId(), program.processCode(), program.modelType(),
                program.definitionDigest(), unavailableMachine, program.program(),
                new DurableValueSerializer(unavailableMachine), program.scriptPrograms());
    }

    private DurableProcessRuntime loadProgram(String dispatchMethod, String policy) {
        String effectIdInput = policy.isBlank()
                ? ""
                : """
                    <input target="effectId" dataType="java.lang.String"
                         source="__cf_effect_id"/>
                    """;
        String xml = """
            <bpm code="effect.worker">
              <start id="start" g="0,0,32,32"><transition to="effect"/></start>
              <autoTask id="effect" g="60,0,100,40">
                <action type="java" execution="effect" class="%s" method="%s">
                    %s

                  %s
                </action>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(WorkerActions.class.getName(), dispatchMethod, effectIdInput, policy);
        return compileProgramXml(xml, "effect-worker");
    }

    private DurableProcessRuntime loadSpringProgram() {
        String xml = """
            <bpm code="effect.worker">
              <start id="start" g="0,0,32,32"><transition to="effect"/></start>
              <autoTask id="effect" g="60,0,100,40">
                <action type="spring-bean" execution="effect" bean="effectBean" class="%s" method="dispatchSuccess"/>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(WorkerActions.class.getName());
        return compileProgramXml(xml, "effect-worker-spring");
    }

    private DurableProcessRuntime compileProgramXml(String xml, String sourceName) {
        var model = TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of(sourceName, xml.getBytes(StandardCharsets.UTF_8)));
        DurableMachinePlan machinePlan = DurableCompilerTestSupport.lower(model);
        var program = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machinePlan, getClass().getClassLoader());
        return new DurableProcessRuntime(PROCESS_ID, PROCESS.code(), ProcessModelType.TBBPM, "0".repeat(64), machinePlan,
                program, new DurableValueSerializer(machinePlan), Map.of());
    }

    private String reconcilePolicy(String method) {
        return """
            <effectPolicy recovery="reconcile" maxAttempts="3" maxReconcileAttempts="2"
                          recoveryDelay="PT2S" maxRecoveryDuration="PT30M">
              <reconcileAction type="java" class="%s" method="%s"/>
            </effectPolicy>
            """
            .formatted(WorkerActions.class.getName(), method);
    }

    private DurableStore.EffectClaim claim(DurableProcessRuntime program, DurableStore.EffectOperation operation,
            int dispatchAttempts, int reconcileAttempts) {
        DurableStore.EffectLease lease = new DurableStore.EffectLease(RUN_ID, UUID.randomUUID(), UUID.randomUUID());
        EffectPolicyPlan policy = effectPolicy(program);
        return new DurableStore.EffectClaim(lease, RUN_ID, RUN_PROCESS, program.processId(), "root", "effect", operation,
                dispatchAttempts, reconcileAttempts,
                new DurableStore.Envelope(program.valueSerializer().encodeEffectInput("effect", Map.of())),
                recoveryPlan(policy), CREATED_AT,
                operation == DurableStore.EffectOperation.RECONCILE ? CREATED_AT : null, CREATED_AT.plusSeconds(60),
                CREATED_AT.plus(LEASE_DURATION));
    }

    private static EffectPolicyPlan effectPolicy(DurableProcessRuntime program) {
        return ((ActionPlan) program.machinePlan().semanticPlan().requireNode("effect").operation()).effectPolicy();
    }

    private static EffectRecoveryPlan recoveryPlan(EffectPolicyPlan policy) {
        return switch (policy.recovery()) {
            case MANUAL -> EffectRecoveryPlan.manual();
            case RETRY -> EffectRecoveryPlan.retry(policy.maxAttempts(), policy.recoveryDelay(),
                    policy.maxRecoveryDuration());
            case RECONCILE -> EffectRecoveryPlan.reconcile(policy.maxAttempts(), policy.maxReconcileAttempts(),
                    policy.recoveryDelay(), policy.maxRecoveryDuration());
        };
    }

    enum Transition {
        COMPLETED,
        UNKNOWN,
        RELEASED,
        REDISPATCHED,
        REVIEW_REQUIRED
    }

    private static final class CapturingStore {
        private final DurableStore.EffectClaim claim;
        private final List<DurableStore.EffectOperation> claimOperations = new ArrayList<>();
        private final DurableStore proxy;
        private final boolean transitionApplied;
        private boolean claimed;
        private Transition transition;
        private Duration delay;
        private EffectReadinessCode readinessCode;
        private String reason;
        private DurableStore.Envelope result;

        private CapturingStore(DurableStore.EffectClaim claim) {
            this(claim, true);
        }

        private CapturingStore(DurableStore.EffectClaim claim, boolean transitionApplied) {
            this.claim = claim;
            this.transitionApplied = transitionApplied;
            InvocationHandler handler =
                    (instance, method, arguments) -> switch (method.getName()) {
                case "claimEffect" -> claim((DurableStore.EffectClaimRequest) arguments[0]);
                case "renewEffectLeases" -> arguments[0];
                case "completeEffect" -> transition(Transition.COMPLETED, null, null, arguments[1]);
                case "markEffectUnknown" -> transition(Transition.UNKNOWN, arguments[1], arguments[2], null);
                case "releaseEffectBeforeInvocation" -> release((Duration) arguments[1],
                        (EffectReadinessCode) arguments[2]);
                case "scheduleEffectRedispatch" -> transition(Transition.REDISPATCHED, arguments[1], null, null);
                case "requireEffectReview" -> transition(Transition.REVIEW_REQUIRED, null, arguments[1], null);
                case "toString" -> "CapturingEffectStore";
                default -> throw new AssertionError("Unexpected Store call: " + method.getName());
            };
            proxy = (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(),
                    new Class<?>[] {DurableStore.class}, handler);
        }

        private DurableStore proxy() {
            return proxy;
        }

        private Optional<DurableStore.EffectClaim> claim(DurableStore.EffectClaimRequest request) {
            claimOperations.add(request.operation());
            if (!claimed && claim != null && claim.operation() == request.operation()) {
                claimed = true;
                return Optional.of(claim);
            }
            return Optional.empty();
        }

        private boolean transition(Transition value, Object delay, Object reason, Object result) {
            if (transition != null) {
                throw new AssertionError("Effect claim completed more than once");
            }
            transition = value;
            this.delay = (Duration) delay;
            this.reason = (String) reason;
            this.result = (DurableStore.Envelope) result;
            return transitionApplied;
        }

        private boolean release(Duration delay, EffectReadinessCode readinessCode) {
            this.readinessCode = readinessCode;
            return transition(Transition.RELEASED, delay, null, null);
        }
    }

    public static final class WorkerActions {
        public void dispatchSuccess() {}

        public void dispatchSuccess(String effectId) {}

        public void dispatchFailure() {
            throw new IllegalStateException("unknown outcome");
        }

        public void dispatchCancelled() {
            throw new ScriptException(ScriptException.Kind.CANCELLED, "response handling cancelled after dispatch");
        }

        public void dispatchFailure(String effectId) {
            throw new IllegalStateException("unknown outcome");
        }

        public EffectReconcileOutcome<Void> reconcileConfirmed() {
            return EffectReconcileOutcome.confirmed(null);
        }

        public EffectReconcileOutcome<Void> reconcileNotExecuted() {
            return EffectReconcileOutcome.notExecuted();
        }

        public EffectReconcileOutcome<Void> reconcileUnknown() {
            return EffectReconcileOutcome.unknown();
        }

        public Object reconcileInvalid() {
            return "invalid";
        }

        public EffectReconcileOutcome<Void> reconcileFailure() {
            throw new IllegalArgumentException("still unknown");
        }
    }
}
