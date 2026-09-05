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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer.LaneHealth;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer.RenewalHealth;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class DurableHealthIndicatorTest {
    @Test
    void optionalCapabilitiesDoNotFailDurableAuthorityHealth() {
        Health health = new DurableHealthIndicator(healthyStore(), new InMemoryDurableProcessRuntimeCache(), () -> null,
                () -> null, false)
            .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("durableAuthority", "up")
            .containsEntry("workerRuntime", "disabled")
            .containsEntry("effectCapability", "process_scoped")
            .containsEntry("outboxSink", "not_configured");
    }

    @Test
    void storeFailureIsDownWithoutLeakingProviderDetails() {
        DurableStore store = mock(DurableStore.class);
        when(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 1)))
            .thenThrow(new IllegalStateException("secret-provider-location"));

        Health health = new DurableHealthIndicator(store, new InMemoryDurableProcessRuntimeCache(), () -> null,
                () -> null, false)
            .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("durableAuthority", "down")
            .containsEntry("error", IllegalStateException.class.getName())
            .doesNotContainValue("secret-provider-location");
    }

    @Test
    void persistentWorkerMachineryFaultIsDegradedRatherThanGloballyDown() {
        DurableWorkerHealth workerHealth = new DurableWorkerHealth(List.of(Operation.TURN));
        for (int attempt = 0; attempt < 3; attempt++) {
            workerHealth.fault(Operation.TURN, new IllegalStateException("secret-process-state"));
        }
        DurableWorkerCoordinator coordinator = mock(DurableWorkerCoordinator.class);
        when(coordinator.isRunning()).thenReturn(true);
        when(coordinator.workerHealthSnapshot()).thenReturn(workerHealth.snapshot());

        Health health = new DurableHealthIndicator(healthyStore(), new InMemoryDurableProcessRuntimeCache(),
                () -> coordinator, () -> null, false)
            .health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails())
            .containsEntry("durableAuthority", "up")
            .containsEntry("workerRuntime", "running")
            .containsEntry("workerMachinery", "degraded")
            .doesNotContainValue("secret-process-state");
    }

    @Test
    void persistentLeaseRenewalFaultIsDegradedWithoutLeakingFailureDetails() {
        LaneHealth inactive = new LaneHealth(0, 0, false, null, null, null, null);
        LaneHealth faulting =
                new LaneHealth(1, 2, false, null, null, Instant.now(), IllegalStateException.class.getName());
        DurableLeaseRenewer renewer = mock(DurableLeaseRenewer.class);
        when(renewer.renewalHealth()).thenReturn(new RenewalHealth(faulting, inactive, inactive));

        Health health = new DurableHealthIndicator(healthyStore(), new InMemoryDurableProcessRuntimeCache(), () -> null,
                () -> renewer, false)
            .health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("durableAuthority", "up").containsEntry("leaseRenewal", "degraded");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> lanes =
                (Map<String, Map<String, Object>>) health.getDetails().get("leaseRenewalLanes");
        assertThat(lanes.get("run"))
            .containsEntry("state", "degraded")
            .containsEntry("activeAuthorities", 1)
            .containsEntry("consecutiveFaults", 2)
            .containsEntry("lastFailureType", IllegalStateException.class.getName());
        assertThat(health.getDetails().toString()).doesNotContain("secret-provider-location");
    }

    private static DurableStore healthyStore() {
        DurableStore store = mock(DurableStore.class);
        when(store.listProcessRuntimeDemand(new DurableStore.ProcessRuntimeDemandQuery(null, 1)))
            .thenReturn(new DurableStore.ProcessRuntimeDemandPage(List.of(), null));
        return store;
    }
}
