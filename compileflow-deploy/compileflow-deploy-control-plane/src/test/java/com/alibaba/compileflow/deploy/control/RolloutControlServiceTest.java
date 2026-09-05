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
package com.alibaba.compileflow.deploy.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Outcome;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.control.repository.RolloutRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RolloutControlServiceTest {
    private static RolloutControlService service(RolloutRepository repository,
            ProcessDeploymentOperationMetrics metrics) {
        ProcessVersionRepository versionRepository = mock(ProcessVersionRepository.class);
        when(versionRepository.find(any(), any(), any())).thenReturn(Optional.of(mock(ProcessVersionRecord.class)));
        return new RolloutControlService(repository, versionRepository, ArtifactProjectionCoordinator.database(),
                metrics);
    }

    @Test
    void recordsSuccessfulMutationWithoutAFalseFailure() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        RolloutControlService service = service(repository, metrics);
        CreateRolloutCommand command = mock(CreateRolloutCommand.class);
        ProcessRollout expected = mock(ProcessRollout.class);
        when(command.getAlias()).thenReturn(ProcessRef.alias("default", "order.flow", "production"));
        when(command.getTargetVersion()).thenReturn(ProcessRef.version("default", "order.flow", "v2"));
        when(repository.create(any(RolloutCreateRequest.class))).thenReturn(expected);

        assertThat(service.create(command)).isSameAs(expected);
        assertThat(metrics.count(Operation.CREATE_ROLLOUT, Outcome.SUCCESS)).isEqualTo(1L);
        assertThat(metrics.count(Operation.CREATE_ROLLOUT, Outcome.FAILURE)).isZero();
    }

    @Test
    void recordsFailedMutationAndPreservesTheOriginalException() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        RolloutControlService service = service(repository, metrics);
        PromoteRolloutCommand command = mock(PromoteRolloutCommand.class);
        IllegalStateException failure = new IllegalStateException("route conflict");
        when(repository.promote(command)).thenThrow(failure);

        assertThatThrownBy(() -> service.promote(command)).isSameAs(failure);
        assertThat(metrics.count(Operation.PROMOTE, Outcome.SUCCESS)).isZero();
        assertThat(metrics.count(Operation.PROMOTE, Outcome.FAILURE)).isEqualTo(1L);
    }

    @Test
    void rollbackCreatesANewScopedRolloutFromTheCapturedBaseline() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        RolloutControlService service = service(repository, metrics);
        ProcessRollout source = mock(ProcessRollout.class);
        ProcessRollout created = mock(ProcessRollout.class);
        when(source.getId()).thenReturn("rollout-1");
        when(source.getAlias()).thenReturn(ProcessRef.alias("default", "order.flow", "production"));
        when(source.getBaselineVersion()).thenReturn(ProcessRef.version("default", "order.flow", "v1"));
        when(source.getPhase()).thenReturn(RolloutPhase.COMPLETED);
        when(source.getAliasRevision()).thenReturn(7L);
        when(repository.find("rollout-1")).thenReturn(Optional.of(source));
        when(repository.create(any(RolloutCreateRequest.class))).thenReturn(created);

        ProcessRollout result = service.rollback(new RollbackRolloutCommand("rollout-1", "rollback-key", 7L, "alice"));

        assertThat(result).isSameAs(created);
        ArgumentCaptor<RolloutCreateRequest> request = ArgumentCaptor.forClass(RolloutCreateRequest.class);
        verify(repository).create(request.capture());
        assertThat(request.getValue().getOperationKind()).isEqualTo(RolloutOperationKind.ROLLBACK);
        assertThat(request.getValue().getTargetVersion()).isEqualTo("v1");
        assertThat(request.getValue().getExpectedAliasRevision()).isEqualTo(7L);
        assertThat(request.getValue().getActor()).isEqualTo("alice");
        assertThat(metrics.count(Operation.ROLLBACK, Outcome.SUCCESS)).isEqualTo(1L);
    }

    @Test
    void rollbackRejectsANonCompletedSourceWithoutImplicitMutation() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        RolloutControlService service = service(repository, metrics);
        ProcessRollout source = mock(ProcessRollout.class);
        when(source.getPhase()).thenReturn(RolloutPhase.IN_PROGRESS);
        when(repository.find("rollout-1")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.rollback(new RollbackRolloutCommand("rollout-1", "rollback-key", 7L, "alice")))
            .hasMessageContaining("Only a completed rollout");
        verify(repository, never()).create(any());
        assertThat(metrics.count(Operation.ROLLBACK, Outcome.FAILURE)).isEqualTo(1L);
    }

    @Test
    void rollbackRejectsASourceThatNoLongerOwnsTheExpectedRouteRevision() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        RolloutControlService service = service(repository, metrics);
        ProcessRollout source = mock(ProcessRollout.class);
        when(source.getId()).thenReturn("rollout-1");
        when(source.getPhase()).thenReturn(RolloutPhase.COMPLETED);
        when(source.getBaselineVersion()).thenReturn(ProcessRef.version("default", "order.flow", "v1"));
        when(source.getAliasRevision()).thenReturn(6L);
        when(repository.find("rollout-1")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.rollback(new RollbackRolloutCommand("rollout-1", "rollback-key", 7L, "alice")))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION))
            .hasMessageContaining("no longer owns the expected route revision");
        verify(repository, never()).create(any());
        assertThat(metrics.count(Operation.ROLLBACK, Outcome.FAILURE)).isEqualTo(1L);
    }

    @Test
    void failedArtifactProjectionDoesNotCreateARoute() {
        RolloutRepository repository = mock(RolloutRepository.class);
        ProcessVersionRepository versionRepository = mock(ProcessVersionRepository.class);
        ArtifactProjectionCoordinator artifactCoordinator = mock(ArtifactProjectionCoordinator.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        ProcessVersionRecord version = mock(ProcessVersionRecord.class);
        CreateRolloutCommand command = mock(CreateRolloutCommand.class);
        DeploymentException failure =
                DeploymentException.of(DeploymentErrorCode.ARTIFACT_PROJECTION_FAILED, "artifact transport unavailable");
        when(command.getAlias()).thenReturn(ProcessRef.alias("default", "order.flow", "production"));
        when(command.getTargetVersion()).thenReturn(ProcessRef.version("default", "order.flow", "v2"));
        when(versionRepository.find("default", "order.flow", "v2")).thenReturn(Optional.of(version));
        doThrow(failure).when(artifactCoordinator).ensureProjected(version);
        RolloutControlService service =
                new RolloutControlService(repository, versionRepository, artifactCoordinator, metrics);

        assertThatThrownBy(() -> service.create(command)).isSameAs(failure);
        verify(repository, never()).create(any());
        assertThat(metrics.count(Operation.CREATE_ROLLOUT, Outcome.FAILURE)).isEqualTo(1L);
    }
}
