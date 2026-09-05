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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.observability;

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.RuntimeInstallReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Outcome;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.LocalReadyRoutingStateSnapshot;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Registers bounded deployment counters and aggregate node gauges.
 *
 * @author yusu
 */
public final class CompileFlowDeploymentMetricsBinder implements MeterBinder {
    private final ProcessDeploymentMetrics runtimeMetrics;
    private final ProcessDeploymentOperationMetrics controlMetrics;
    private final Supplier<RuntimeInstaller> installerSupplier;
    private final Supplier<LocalRoutingReconciler> localReadySupplier;
    private final Supplier<RoutingProjectionReconciler> reconciliationSupplier;

    public CompileFlowDeploymentMetricsBinder(ProcessDeploymentMetrics runtimeMetrics,
            ProcessDeploymentOperationMetrics controlMetrics, Supplier<RuntimeInstaller> installerSupplier,
            Supplier<LocalRoutingReconciler> localReadySupplier,
            Supplier<RoutingProjectionReconciler> reconciliationSupplier) {
        this.runtimeMetrics = Objects.requireNonNull(runtimeMetrics, "runtimeMetrics");
        this.controlMetrics = Objects.requireNonNull(controlMetrics, "controlMetrics");
        this.installerSupplier = Objects.requireNonNull(installerSupplier, "installerSupplier");
        this.localReadySupplier = Objects.requireNonNull(localReadySupplier, "localReadySupplier");
        this.reconciliationSupplier = Objects.requireNonNull(reconciliationSupplier, "reconciliationSupplier");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        bindRuntimeInstallCounters(registry);
        bindAliasConvergenceCounters(registry);
        bindErrorCounters(registry);
        bindOperationCounters(registry);
        bindReconciliationCounters(registry);
        bindNodeGauges(registry);
    }

    private void bindReconciliationCounters(MeterRegistry registry) {
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.runs",
                "Completed control-plane projection reconciliation cycles",
                RoutingProjectionReconciler::getTotalReconciliations);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.routing.mismatches",
                "Authoritative Alias states that differed from routing projections",
                RoutingProjectionReconciler::getTotalRoutingMismatches);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.routing.repairs",
                "Routing projection corrections enqueued by reconciliation",
                RoutingProjectionReconciler::getTotalRoutingRepairs);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.artifact.checks",
                "Active immutable artifact projections checked by reconciliation",
                RoutingProjectionReconciler::getTotalArtifactChecks);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.artifact.missing",
                "Missing active artifact projections observed by reconciliation",
                RoutingProjectionReconciler::getTotalArtifactMissing);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.artifact.repairs",
                "Missing active artifact projections recreated by reconciliation",
                RoutingProjectionReconciler::getTotalArtifactRepairs);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.artifact.conflicts",
                "Conflicting active artifact projections observed by reconciliation",
                RoutingProjectionReconciler::getTotalArtifactConflicts);
        bindReconciliationCounter(registry, "compileflow.deploy.reconciliation.artifact.failures",
                "Active artifact projection reconciliation failures",
                RoutingProjectionReconciler::getTotalArtifactFailures);
    }

    private void bindReconciliationCounter(MeterRegistry registry, String name, String description,
            ToDoubleFunction<RoutingProjectionReconciler> value) {
        Supplier<RoutingProjectionReconciler> source = reconciliationSupplier;
        FunctionCounter
            .builder(name, source, ignored -> {
                RoutingProjectionReconciler task = source.get();
                return task == null ? 0.0 : value.applyAsDouble(task);
            })
            .description(description)
            .register(registry);
    }

    private void bindRuntimeInstallCounters(MeterRegistry registry) {
        for (AttemptOutcome outcome : AttemptOutcome.values()) {
            for (RuntimeInstallReason reason : RuntimeInstallReason.values()) {
                if (!ProcessDeploymentMetrics.isValid(outcome, reason)) {
                    continue;
                }
                FunctionCounter
                    .builder("compileflow.deploy.runtime.install.attempts", runtimeMetrics, metrics -> metrics.runtimeInstallCount(outcome,
                            reason))
                    .description("Runtime installation attempts by terminal outcome")
                    .tags("outcome", outcome.getTagValue(), "reason", reason.getTagValue())
                    .register(registry);
            }
        }
    }

    private void bindAliasConvergenceCounters(MeterRegistry registry) {
        for (AttemptOutcome outcome : AttemptOutcome.values()) {
            for (AliasConvergenceReason reason : AliasConvergenceReason.values()) {
                if (!ProcessDeploymentMetrics.isValid(outcome, reason)) {
                    continue;
                }
                FunctionCounter
                    .builder("compileflow.deploy.alias.convergence", runtimeMetrics, metrics -> metrics.aliasConvergenceCount(outcome,
                            reason))
                    .description("Node-local alias convergence attempts by terminal outcome")
                    .tags("outcome", outcome.getTagValue(), "reason", reason.getTagValue())
                    .register(registry);
            }
        }
    }

    private void bindErrorCounters(MeterRegistry registry) {
        for (DeploymentErrorCode errorCode : DeploymentErrorCode.values()) {
            FunctionCounter
                .builder("compileflow.deploy.errors", runtimeMetrics, metrics -> metrics.getErrorCount(errorCode))
                .description("Deployment errors by stable error code")
                .tag("error.code", errorCode.name())
                .register(registry);
        }
    }

    private void bindOperationCounters(MeterRegistry registry) {
        for (Operation operation : Operation.values()) {
            for (Outcome outcome : Outcome.values()) {
                FunctionCounter
                    .builder("compileflow.deploy.operations", controlMetrics, metrics -> metrics.count(operation,
                            outcome))
                    .description("Deployment control-plane operations by outcome")
                    .tags("operation", operation.getTagValue(), "outcome", outcome.getTagValue())
                    .register(registry);
            }
        }
    }

    private void bindNodeGauges(MeterRegistry registry) {
        bindLocalReadyGauge(registry, "compileflow.deploy.alias.desired.count",
                LocalReadyRoutingStateSnapshot::getDesiredAliasCount);
        bindLocalReadyGauge(registry, "compileflow.deploy.alias.local_ready.count",
                LocalReadyRoutingStateSnapshot::getLocalReadyAliasCount);
        bindLocalReadyGauge(registry, "compileflow.deploy.alias.pending.count",
                LocalReadyRoutingStateSnapshot::getPendingAliasCount);
        Gauge
            .builder("compileflow.deploy.runtime.retained.count", installerSupplier, supplier -> {
                RuntimeInstaller installer = supplier.get();
                return installer == null ? 0.0 : installer.snapshot().getRetainedRuntimeCount();
            })
            .description("Node-local runtimes retained by deployment aliases")
            .strongReference(true)
            .register(registry);
    }

    private void bindLocalReadyGauge(MeterRegistry registry, String name,
            ToDoubleFunction<LocalReadyRoutingStateSnapshot> value) {
        Gauge
            .builder(name, localReadySupplier, supplier -> {
                LocalRoutingReconciler applier = supplier.get();
                return applier == null ? 0.0 : value.applyAsDouble(applier.snapshot());
            })
            .description("Aggregate node-local alias convergence state")
            .strongReference(true)
            .register(registry);
    }
}
