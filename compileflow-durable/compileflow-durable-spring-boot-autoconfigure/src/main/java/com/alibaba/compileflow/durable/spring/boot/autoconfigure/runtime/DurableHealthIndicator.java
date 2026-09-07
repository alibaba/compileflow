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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableWorkerCoordinator;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer.LaneHealth;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer.RenewalHealth;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports Durable authority health without treating optional application capabilities as global readiness.
 *
 * @author yusu
 */
public final class DurableHealthIndicator implements HealthIndicator {
    private final DurableStore store;
    private final DurableProcessRuntimeCache runtimeCache;
    private final Supplier<DurableWorkerCoordinator> coordinator;
    private final Supplier<DurableLeaseRenewer> leaseRenewer;
    private final boolean outboxSinkConfigured;

    public DurableHealthIndicator(DurableStore store, DurableProcessRuntimeCache runtimeCache,
            Supplier<DurableWorkerCoordinator> coordinator, Supplier<DurableLeaseRenewer> leaseRenewer,
            boolean outboxSinkConfigured) {
        this.store = Objects.requireNonNull(store, "store");
        this.runtimeCache = Objects.requireNonNull(runtimeCache, "runtimeCache");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.leaseRenewer = Objects.requireNonNull(leaseRenewer, "leaseRenewer");
        this.outboxSinkConfigured = outboxSinkConfigured;
    }

    private static String workerRuntime(DurableWorkerCoordinator workers) {
        if (workers == null) {
            return "disabled";
        }
        return workers.isRunning() ? "running" : "stopped";
    }

    @Override
    public Health health() {
        try {
            Objects.requireNonNull(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 1)),
                    "authority probe");
        } catch (Exception failure) {
            return Health
                .down()
                .withDetail("durableAuthority", "down")
                .withDetail("error", failure.getClass().getName())
                .build();
        }
        DurableWorkerCoordinator workers = coordinator.get();
        DurableWorkerCoordinator.HealthSnapshot workerHealth = workers == null ? null : workers.workerHealthSnapshot();
        DurableLeaseRenewer renewer = leaseRenewer.get();
        RenewalHealth renewalHealth = renewer == null ? null : renewer.renewalHealth();
        boolean degraded =
                workerHealth != null && workerHealth.degraded() || renewalHealth != null && renewalHealth.degraded();
        Health.Builder health = Health.up().withDetail("degraded", degraded);
        health
            .withDetail("durableAuthority", "up")
            .withDetail("workerRuntime", workerRuntime(workers))
            .withDetail("loadedRuntimeCount", runtimeCache.snapshot().size())
            .withDetail("runtimeLoading", "demand_driven")
            .withDetail("turnCapability", "process_scoped")
            .withDetail("effectCapability", "process_scoped")
            .withDetail("outboxSink", outboxSinkConfigured ? "configured" : "not_configured")
            .withDetail("deployConvergence", "separate_control_plane");
        if (workerHealth != null) {
            health
                .withDetail("workerMachinery", workerHealth.degraded() ? "degraded" : "healthy")
                .withDetail("workerOperations", workerHealth.operations());
        }
        if (renewalHealth == null) {
            health.withDetail("leaseRenewal", "disabled");
        } else {
            health
                .withDetail("leaseRenewal", renewalHealth.degraded() ? "degraded" : "healthy")
                .withDetail("leaseRenewalLanes", renewalDetails(renewalHealth));
        }
        return health.build();
    }

    private static Map<String, Map<String, Object>> renewalDetails(RenewalHealth health) {
        LinkedHashMap<String, Map<String, Object>> details = new LinkedHashMap<>();
        details.put("run", laneDetails(health.run()));
        details.put("effect", laneDetails(health.effect()));
        details.put("outbox", laneDetails(health.outbox()));
        return Map.copyOf(details);
    }

    private static Map<String, Object> laneDetails(LaneHealth lane) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        String state = lane.activeAuthorities() == 0 ? "inactive" : lane.degraded() ? "degraded" : "healthy";
        details.put("state", state);
        details.put("activeAuthorities", lane.activeAuthorities());
        details.put("consecutiveFaults", lane.consecutiveFaults());
        details.put("stale", lane.stale());
        if (lane.currentCycleStarted() != null) {
            details.put("currentCycleStarted", lane.currentCycleStarted().toString());
        }
        if (lane.lastSuccessfulCycle() != null) {
            details.put("lastSuccessfulCycle", lane.lastSuccessfulCycle().toString());
        }
        if (lane.lastFault() != null) {
            details.put("lastFault", lane.lastFault().toString());
        }
        if (lane.lastFailureType() != null) {
            details.put("lastFailureType", lane.lastFailureType());
        }
        return Map.copyOf(details);
    }
}
