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
package com.alibaba.compileflow.deploy.control.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessKey;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentProjectionReconcilerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String PREFIX = "compileflow.deployment.";
    private final ProcessAliasStore aliases = mock(ProcessAliasStore.class);
    private final RoutingOutboxStore outbox = mock(RoutingOutboxStore.class);
    private final InMemoryDeploymentProjectionStore projections = new InMemoryDeploymentProjectionStore();

    private DeploymentProjectionReconciler reconciler() {
        ProcessAliasRecord alias = ProcessAliasRecord
            .builder()
            .namespace("tenant-a")
            .code("order.flow")
            .alias("production")
            .stableVersion("v2")
            .revision(2L)
            .updatedBy("alice")
            .updatedAt(2_000L)
            .build();
        when(aliases.listDistinctProcesses()).thenReturn(List.of(new ProcessKey("tenant-a", "order.flow")));
        when(aliases.listAll("tenant-a", "order.flow")).thenReturn(List.of(alias));
        when(outbox.ensurePending(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(true);
        return new DeploymentProjectionReconciler(aliases, mock(ProcessVersionStore.class),
                ArtifactProjectionCoordinator.source(), projections, outbox, PREFIX, true, TIMEOUT);
    }

    @Test
    void olderProjectionWithAnotherIdentityIsAConflictAndDoesNotEnqueueAnImpossibleRepair() throws Exception {
        DeploymentProjectionReconciler reconciler = reconciler();
        String key = RoutingStateKeys.aliasState(PREFIX, "tenant-a", "order.flow", "production");
        String foreignPayload = RoutingStateCodec.aliasStateJson("tenant-b", "order.flow", "production", "v1", null,
                null, 1L, "alice", 1_000L);
        assertThat(projections.compareAndSet(key, null, foreignPayload, "application/json", TIMEOUT)).isTrue();

        DeploymentProjectionReconciler.ReconciliationResult result = reconciler.reconcile();

        assertThat(result.getMismatchesFound()).isOne();
        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(result.getMismatchDetails())
            .singleElement()
            .satisfies(mismatch -> {
                assertThat(mismatch.isAutoFixed()).isFalse();
                assertThat(mismatch.getReason()).contains("identity");
            });
        verifyNoInteractions(outbox);
        assertThat(projections.read(key, TIMEOUT)).isEqualTo(foreignPayload);
    }

    @Test
    void olderProjectionOfTheSameIdentityStillEnqueuesCorrection() throws Exception {
        DeploymentProjectionReconciler reconciler = reconciler();
        String key = RoutingStateKeys.aliasState(PREFIX, "tenant-a", "order.flow", "production");
        String olderPayload = RoutingStateCodec.aliasStateJson("tenant-a", "order.flow", "production", "v1", null, null,
                1L, "alice", 1_000L);
        assertThat(projections.compareAndSet(key, null, olderPayload, "application/json", TIMEOUT)).isTrue();

        DeploymentProjectionReconciler.ReconciliationResult result = reconciler.reconcile();

        assertThat(result.getMismatchesFound()).isOne();
        assertThat(result.getAutoFixedCount()).isOne();
        verify(outbox)
            .ensurePending("ALIAS_STATE", "tenant-a", "order.flow", "production", key,
                    RoutingStateCodec.aliasStateJson("tenant-a", "order.flow", "production", "v2", null, null, 2L,
                            "alice", 2_000L));
    }
}
