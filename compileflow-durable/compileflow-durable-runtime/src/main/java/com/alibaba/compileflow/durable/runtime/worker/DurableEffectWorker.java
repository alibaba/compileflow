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

import com.alibaba.compileflow.durable.api.effect.EffectReconcileOutcome;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.model.EffectReadinessCode;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.store.DurableEffectStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.ProcessText;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes already-committed Effect occurrences outside database transactions.
 *
 * @author yusu
 */
public final class DurableEffectWorker {
    private final DurableEffectStore store;
    private final DurableProcessRuntimeCache runtimeCache;
    private final DurableActionInvoker actions;
    private final DurableEffectWorkerOptions options;
    private final DurableLeaseRenewer leases;
    private final DurableRuntimeMetrics metrics;
    private final AtomicLong claimSequence = new AtomicLong();

    public DurableEffectWorker(DurableEffectStore store, DurableProcessRuntimeCache runtimeCache,
            DurableActionInvoker actions, DurableEffectWorkerOptions options, DurableLeaseRenewer leases) {
        this(store, runtimeCache, actions, options, leases, new DurableRuntimeMetrics());
    }

    public DurableEffectWorker(DurableEffectStore store, DurableProcessRuntimeCache runtimeCache,
            DurableActionInvoker actions, DurableEffectWorkerOptions options, DurableLeaseRenewer leases,
            DurableRuntimeMetrics metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.options = Objects.requireNonNull(options, "options");
        this.leases = Objects.requireNonNull(leases, "leases");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public boolean runOnce() {
        DurableStore.EffectOperation preferred = (claimSequence.getAndIncrement() & 1L) == 0L
                ? DurableStore.EffectOperation.DISPATCH
                : DurableStore.EffectOperation.RECONCILE;
        Optional<DurableStore.EffectClaim> claimed = store.claimEffect(
                new DurableStore.EffectClaimRequest(options.workerId(), preferred, leases.leaseDuration()));
        if (claimed.isEmpty()) {
            DurableStore.EffectOperation fallback = preferred == DurableStore.EffectOperation.DISPATCH
                    ? DurableStore.EffectOperation.RECONCILE
                    : DurableStore.EffectOperation.DISPATCH;
            claimed = store.claimEffect(
                    new DurableStore.EffectClaimRequest(options.workerId(), fallback, leases.leaseDuration()));
        }
        if (claimed.isEmpty()) {
            return false;
        }
        DurableStore.EffectClaim claim = claimed.orElseThrow();
        metrics.record(operation(claim), Outcome.CLAIMED);
        DurableLeaseRenewer.Handle heartbeat = leases.trackEffect(claim.lease());
        try {
            processEffectClaim(claim);
        } finally {
            heartbeat.close();
        }
        return true;
    }

    private void processEffectClaim(DurableStore.EffectClaim claim) {
        DurableProcessRuntime loaded = runtimeCache.get(claim.processId()).orElse(null);
        if (loaded == null) {
            releaseNotReady(claim, EffectReadinessCode.PROCESS_RUNTIME_NOT_READY);
            return;
        }
        DurableActionInvoker programActions = actions.withScriptPrograms(loaded.scriptPrograms());
        ActionPlan action = action(loaded, claim.elementId());
        if (action == null || !programActions.isStructurallyReady(action)) {
            releaseNotReady(claim, EffectReadinessCode.ACTION_NOT_READY);
            return;
        }
        final Map<String, Object> input;
        try {
            input = loaded.valueSerializer().decodeEffectInput(claim.elementId(), claim.input().payload());
        } catch (RuntimeException invalid) {
            record(operation(claim), store.requireEffectReview(claim.lease(), "EFFECT_INPUT_INVALID"),
                    Outcome.REVIEW_REQUIRED);
            return;
        }
        if (claim.operation() == DurableStore.EffectOperation.DISPATCH) {
            dispatch(claim, loaded, programActions, action, input);
        } else {
            reconcile(claim, loaded, programActions, action, input);
        }
    }

    private void dispatch(DurableStore.EffectClaim claim, DurableProcessRuntime loaded, DurableActionInvoker actions,
            ActionPlan action, Map<String, Object> input) {
        try {
            Map<String, Object> output = actions.invoke(action, input, claim.lease().effectId().toString());
            record(Operation.EFFECT_DISPATCH,
                    store.completeEffect(claim.lease(),
                            new DurableStore.Envelope(loaded
                                .valueSerializer()
                                .encodeEffectOutput(claim.elementId(), output))), Outcome.SUCCESS);
        } catch (Exception | LinkageError uncertain) {
            record(Operation.EFFECT_DISPATCH,
                    store.markEffectUnknown(claim.lease(), recoveryDelay(claim.recoveryPlan()), boundedReason(uncertain)),
                    Outcome.UNKNOWN);
        }
    }

    private void reconcile(DurableStore.EffectClaim claim, DurableProcessRuntime loaded, DurableActionInvoker actions,
            ActionPlan action, Map<String, Object> input) {
        EffectRecoveryPlan policy = claim.recoveryPlan();
        if (deadlineExceeded(claim, policy)) {
            record(Operation.EFFECT_RECONCILE, store.requireEffectReview(claim.lease(), "EFFECT_DEADLINE_EXCEEDED"),
                    Outcome.REVIEW_REQUIRED);
            return;
        }
        if (policy.mode() == EffectRecoveryPlan.Mode.MANUAL) {
            record(Operation.EFFECT_RECONCILE, store.requireEffectReview(claim.lease(), "MANUAL_EFFECT_OUTCOME_UNKNOWN"),
                    Outcome.REVIEW_REQUIRED);
            return;
        }
        if (policy.mode() == EffectRecoveryPlan.Mode.RETRY) {
            if (claim.dispatchAttempts() >= policy.maxAttempts()) {
                record(Operation.EFFECT_RECONCILE,
                        store.requireEffectReview(claim.lease(), "EFFECT_DISPATCH_ATTEMPTS_EXHAUSTED"),
                        Outcome.REVIEW_REQUIRED);
            } else {
                record(Operation.EFFECT_RECONCILE, store.scheduleEffectRedispatch(claim.lease(), recoveryDelay(policy)),
                        Outcome.RETRY_SCHEDULED);
            }
            return;
        }
        if (claim.reconcileAttempts() > policy.maxReconcileAttempts()) {
            record(Operation.EFFECT_RECONCILE,
                    store.requireEffectReview(claim.lease(), "EFFECT_RECONCILE_ATTEMPTS_EXHAUSTED"),
                    Outcome.REVIEW_REQUIRED);
            return;
        }
        ReconcilePlan reconcile = action.effectPolicy().reconcileAction();
        if (reconcile == null || !actions.isStructurallyReady(reconcile)) {
            releaseNotReady(claim, EffectReadinessCode.RECONCILE_ACTION_NOT_READY);
            return;
        }
        try {
            Object raw = actions.invokeRaw(reconcile, input, claim.lease().effectId().toString());
            if (raw instanceof EffectReconcileOutcome.ConfirmedResult<?> confirmed) {
                Map<String, Object> output = actions.mapResult(action, confirmed.value());
                record(Operation.EFFECT_RECONCILE,
                        store.completeEffect(claim.lease(),
                                new DurableStore.Envelope(loaded
                                    .valueSerializer()
                                    .encodeEffectOutput(claim.elementId(), output))), Outcome.SUCCESS);
            } else if (raw instanceof EffectReconcileOutcome.ConfirmedNotExecuted<?>) {
                if (claim.dispatchAttempts() >= policy.maxAttempts()) {
                    record(Operation.EFFECT_RECONCILE,
                            store.requireEffectReview(claim.lease(), "EFFECT_DISPATCH_ATTEMPTS_EXHAUSTED"),
                            Outcome.REVIEW_REQUIRED);
                } else {
                    record(Operation.EFFECT_RECONCILE,
                            store.scheduleEffectRedispatch(claim.lease(), recoveryDelay(policy)),
                            Outcome.RETRY_SCHEDULED);
                }
            } else if (raw instanceof EffectReconcileOutcome.StillUnknown<?>) {
                record(Operation.EFFECT_RECONCILE,
                        store.markEffectUnknown(claim.lease(), recoveryDelay(policy), "RECONCILE_STILL_UNKNOWN"),
                        Outcome.UNKNOWN);
            } else {
                record(Operation.EFFECT_RECONCILE,
                        store.markEffectUnknown(claim.lease(), recoveryDelay(policy), "INVALID_RECONCILE_RESULT"),
                        Outcome.UNKNOWN);
            }
        } catch (Exception | LinkageError stillUnknown) {
            record(Operation.EFFECT_RECONCILE,
                    store.markEffectUnknown(claim.lease(), recoveryDelay(policy), boundedReason(stillUnknown)),
                    Outcome.UNKNOWN);
        }
    }

    private void releaseNotReady(DurableStore.EffectClaim claim, EffectReadinessCode readinessCode) {
        Operation operation = operation(claim);
        record(operation, store.releaseEffectBeforeInvocation(claim.lease(), options.readinessBackoff(), readinessCode),
                Outcome.READINESS_BACKOFF);
    }

    private void record(Operation operation, boolean applied, Outcome outcome) {
        metrics.record(operation, applied ? outcome : Outcome.LEASE_LOST);
    }

    private static Operation operation(DurableStore.EffectClaim claim) {
        return claim.operation() == DurableStore.EffectOperation.DISPATCH
                ? Operation.EFFECT_DISPATCH
                : Operation.EFFECT_RECONCILE;
    }

    private static ActionPlan action(DurableProcessRuntime loaded, String elementId) {
        var node = loaded.machinePlan().semanticPlan().getNodes().get(elementId);
        if (node == null) {
            return null;
        }
        var operation = node.operation();
        return operation instanceof ActionPlan action && action.effectPolicy() != null ? action : null;
    }

    private Duration recoveryDelay(EffectRecoveryPlan policy) {
        return policy.recoveryDelay() == null ? Duration.ZERO : policy.recoveryDelay();
    }

    private boolean deadlineExceeded(DurableStore.EffectClaim claim, EffectRecoveryPlan policy) {
        return policy.maxRecoveryDuration() != null
                && !claim.authorityTime().isBefore(claim.unknownSince().plus(policy.maxRecoveryDuration()));
    }

    private static String boundedReason(Throwable failure) {
        String value = failure.getClass().getName();
        return ProcessText.truncateCodePoints(value, 2048);
    }
}
