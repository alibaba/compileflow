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
package com.alibaba.compileflow.workbench.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.api.rollout.RolloutQuery;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeploymentServiceTest {
    private static DeploymentService service(ProcessDeploymentService runtimeManager) {
        ServerIdentity identity = mock(ServerIdentity.class);
        when(identity.principal()).thenReturn("bridge-service");
        return new DeploymentService(runtimeManager, identity);
    }

    private static ProcessRollout rollout(String id, String baseline, String target, long baseRouteRevision,
            long routeRevision, RolloutStrategy strategy, RolloutPhase phase, int weightBps, long revision) {
        return rollout(id, baseline, target, baseRouteRevision, routeRevision, strategy, phase, weightBps, revision,
                RolloutOperationKind.ROLLBACK);
    }

    @Test
    void listDeploymentsMapsMalformedOpaqueCursorToInvalidRequest() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        when(runtimeManager.listRollouts(any(RolloutQuery.class)))
            .thenThrow(new IllegalArgumentException("Invalid rollout cursor"));

        assertThatThrownBy(() -> service(runtimeManager).listDeployments(null, null, null, null, "not-a-cursor", 20))
            .isInstanceOf(InvalidDeploymentRequestException.class)
            .hasMessage("Invalid rollout cursor");
    }

    private static ProcessRollout rollout(String id, String baseline, String target, long baseRouteRevision,
            long routeRevision, RolloutStrategy strategy, RolloutPhase phase, int weightBps, long revision,
            RolloutOperationKind operation) {
        ProcessRef.Alias alias = ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.flow", "production");
        ProcessRollout.Builder builder = ProcessRollout
            .builder()
            .id(id)
            .alias(alias)
            .targetVersion(ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "order.flow", target))
            .baseAliasRevision(baseRouteRevision)
            .aliasRevision(routeRevision)
            .strategy(strategy)
            .phase(phase)
            .targetWeightBps(weightBps)
            .rolloutRevision(revision)
            .idempotencyKey(id + "-key")
            .operationKind(operation)
            .requestFingerprint("0".repeat(64))
            .createdBy("tester")
            .createdAt(Instant.ofEpochMilli(1_700_000_000_000L))
            .updatedAt(Instant.ofEpochMilli(1_700_000_001_000L));
        if (baseline != null) {
            builder.baselineVersion(ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "order.flow", baseline));
        }
        if (phase.isTerminal()) {
            builder.completedAt(Instant.ofEpochMilli(1_700_000_001_000L));
        }
        return builder.build();
    }

    @Test
    void createsDeploymentWithoutConsultingMutableDraftState() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        ProcessRollout created =
                rollout("rollout-1", "v1", "v1", 0L, 1L, RolloutStrategy.ALL_AT_ONCE, RolloutPhase.COMPLETED, 10_000, 1L);
        when(runtimeManager.createRollout(any())).thenReturn(created);
        DeploymentService service = service(runtimeManager);

        DeploymentView result = service.createDeployment(
                new CreateDeploymentCommand("deploy-1", "order.flow", "v1", "production", 0L, "all_at_once", null, null,
                        null));

        assertThat(result.processCode).isEqualTo("order.flow");
        verify(runtimeManager).createRollout(any());
    }

    @Test
    void rollbackDelegatesTheDomainCommandWithTheCurrentRouteRevision() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        ProcessRollout rollback =
                rollout("rollout-2", "v2", "v1", 9L, 10L, RolloutStrategy.ALL_AT_ONCE, RolloutPhase.COMPLETED, 10_000,
                        1L);
        when(runtimeManager.rollbackRollout(any(RollbackRolloutCommand.class))).thenReturn(rollback);
        DeploymentService service = service(runtimeManager);

        DeploymentView result = service.rollbackDeployment("rollout-1", "rollback-1", 9L);

        assertThat(result.id).isEqualTo("rollout-2");
        assertThat(result.version).isEqualTo("v1");
        assertThat(result.operation).isEqualTo("rollback");
        ArgumentCaptor<RollbackRolloutCommand> command = ArgumentCaptor.forClass(RollbackRolloutCommand.class);
        verify(runtimeManager).rollbackRollout(command.capture());
        assertThat(command.getValue().getSourceRolloutId()).isEqualTo("rollout-1");
        assertThat(command.getValue().getIdempotencyKey()).isEqualTo("rollback-1");
        assertThat(command.getValue().getExpectedAliasRevision()).isEqualTo(9L);
        assertThat(command.getValue().getActor()).isEqualTo("bridge-service");
    }

    @Test
    void exposesTypedOrderedRolloutEventsOnlyForExistingDeployments() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        ProcessRollout rollout = rollout("rollout-1", "v1", "v2", 3L, 4L, RolloutStrategy.CANARY,
                RolloutPhase.IN_PROGRESS, 2_000, 1L, RolloutOperationKind.DEPLOY);
        when(runtimeManager.getRollout("rollout-1")).thenReturn(Optional.of(rollout));
        when(runtimeManager.listRolloutEvents("rollout-1"))
            .thenReturn(List.of(
                    new RolloutEvent(7L, "rollout-1", 1L, "CANARY_STARTED", null, RolloutPhase.IN_PROGRESS,
                            "bridge-service", "Initial canary", Instant.ofEpochMilli(1_700_000_000_000L))));
        DeploymentService service = service(runtimeManager);

        Optional<List<DeploymentEventView>> existing = service.getDeploymentEvents("rollout-1");
        Optional<List<DeploymentEventView>> missing = service.getDeploymentEvents("missing");

        assertThat(existing).isPresent();
        assertThat(existing.orElseThrow())
            .containsExactly(
                    new DeploymentEventView(7L, 1L, "CANARY_STARTED", null, "in_progress", "bridge-service",
                            "Initial canary", "2023-11-14T22:13:20Z"));
        assertThat(missing).isEmpty();
        verify(runtimeManager).getRollout("missing");
    }

    @Test
    void classifiesOnlyRequestConstructionFailuresAsInvalidInput() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        DeploymentService service = service(runtimeManager);

        assertThatThrownBy(() -> service.createDeployment(
                new CreateDeploymentCommand("deploy-1", "order.flow", "v1", "production", 0L, "unsupported", null, null,
                        null)))
            .isInstanceOf(InvalidDeploymentRequestException.class)
            .hasMessage("strategy must be all_at_once or canary");
        verifyNoInteractions(runtimeManager);
    }

    @Test
    void preservesUnexpectedControlPlaneImplementationFailures() {
        ProcessDeploymentService runtimeManager = mock(ProcessDeploymentService.class);
        IllegalArgumentException failure = new IllegalArgumentException("internal invariant failed");
        when(runtimeManager.createRollout(any())).thenThrow(failure);
        DeploymentService service = service(runtimeManager);

        assertThatThrownBy(() -> service.createDeployment(
                new CreateDeploymentCommand("deploy-1", "order.flow", "v1", "production", 0L, "all_at_once", null, null,
                        null)))
            .isSameAs(failure);
    }
}
