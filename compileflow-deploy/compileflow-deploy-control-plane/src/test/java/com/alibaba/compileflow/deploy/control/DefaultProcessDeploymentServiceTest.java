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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Operation;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics.Outcome;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultProcessDeploymentServiceTest {
    @Test
    void publishesAndProjectsThroughTheFacade() {
        VersionPublicationService publication = mock(VersionPublicationService.class);
        ArtifactProjectionCoordinator artifacts = mock(ArtifactProjectionCoordinator.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        ProcessVersionRepository versions = mock(ProcessVersionRepository.class);
        ProcessAliasRepository aliases = mock(ProcessAliasRepository.class);
        ProcessRef.Version ref = ProcessRef.version("default", "order.process", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.process", "<process/>");
        PublishProcessVersionCommand command = new PublishProcessVersionCommand(ref, ProcessModelType.TBBPM, definition,
                "release-bot", Map.of("owner", "payments"));
        ProcessVersionRecord record = mock(ProcessVersionRecord.class);
        when(publication.publish(ref, ProcessModelType.TBBPM, definition, command.getExpectedArtifactDigest(),
                command.getMetadata(), "release-bot"))
            .thenReturn(record);
        when(record.getRef()).thenReturn(ref);
        when(record.getModelType()).thenReturn(ProcessModelType.TBBPM);
        when(record.getArtifactDigest()).thenReturn("a".repeat(64));
        when(record.getMetadata()).thenReturn(command.getMetadata());
        when(record.getActor()).thenReturn("release-bot");
        when(record.getCreatedAt()).thenReturn(10L);
        DefaultProcessDeploymentService service = new DefaultProcessDeploymentService(publication, artifacts, metrics, mock(
                RolloutControlService.class), versions, aliases, ignored -> {});

        PublishedProcessVersion published = service.publish(command);

        assertThat(published.getRef()).isEqualTo(ref);
        verify(artifacts).ensureProjected(record);
        assertThat(metrics.count(Operation.PUBLISH, Outcome.SUCCESS)).isEqualTo(1L);
        assertThat(metrics.count(Operation.PUBLISH, Outcome.FAILURE)).isZero();
    }

    @Test
    void recordsUnexpectedPublicationFailure() {
        VersionPublicationService publication = mock(VersionPublicationService.class);
        ArtifactProjectionCoordinator artifacts = mock(ArtifactProjectionCoordinator.class);
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        ProcessRef.Version ref = ProcessRef.version("default", "order.process", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.process", "<process/>");
        PublishProcessVersionCommand command =
                new PublishProcessVersionCommand(ref, ProcessModelType.TBBPM, definition, "release-bot", Map.of());
        when(publication.publish(ref, ProcessModelType.TBBPM, definition, command.getExpectedArtifactDigest(),
                command.getMetadata(), "release-bot"))
            .thenThrow(new IllegalStateException("database unavailable"));
        DefaultProcessDeploymentService service = new DefaultProcessDeploymentService(publication, artifacts, metrics, mock(
                RolloutControlService.class), mock(ProcessVersionRepository.class), mock(ProcessAliasRepository.class), ignored -> {});

        assertThatThrownBy(() -> service.publish(command))
            .isInstanceOf(com.alibaba.compileflow.deploy.api.error.DeploymentException.class)
            .hasMessageContaining("Unexpected error during publish");
        assertThat(metrics.count(Operation.PUBLISH, Outcome.SUCCESS)).isZero();
        assertThat(metrics.count(Operation.PUBLISH, Outcome.FAILURE)).isEqualTo(1L);
    }

    @Test
    void activatesTheLatestAuthoritativeAliasAfterEveryRouteMutation() {
        VersionPublicationService publication = mock(VersionPublicationService.class);
        RolloutControlService rollouts = mock(RolloutControlService.class);
        ProcessVersionRepository versions = mock(ProcessVersionRepository.class);
        ProcessAliasRepository aliases = mock(ProcessAliasRepository.class);
        CreateRolloutCommand create = mock(CreateRolloutCommand.class);
        UpdateCanaryWeightCommand update = mock(UpdateCanaryWeightCommand.class);
        PromoteRolloutCommand promote = mock(PromoteRolloutCommand.class);
        AbortRolloutCommand abort = mock(AbortRolloutCommand.class);
        RollbackRolloutCommand rollback = mock(RollbackRolloutCommand.class);
        ProcessRollout committed = mock(ProcessRollout.class);
        when(committed.getId()).thenReturn("rollout-1");
        when(committed.getAlias()).thenReturn(ProcessRef.alias("default", "order.process", "production"));
        when(rollouts.create(create)).thenReturn(committed);
        when(rollouts.updateCanary(update)).thenReturn(committed);
        when(rollouts.promote(promote)).thenReturn(committed);
        when(rollouts.abort(abort)).thenReturn(committed);
        when(rollouts.rollback(rollback)).thenReturn(committed);
        when(aliases.resolve("default", "order.process", "production"))
            .thenReturn(Optional.of(ProcessAliasRecord
                .builder()
                .namespace("default")
                .code("order.process")
                .alias("production")
                .stableVersion("v1")
                .candidateVersion("v2")
                .candidateWeightBps(2_500)
                .revision(3L)
                .updatedBy("release-bot")
                .updatedAt(10L)
                .build()));
        List<ProcessAliasState> activated = new ArrayList<>();
        DefaultProcessDeploymentService service = new DefaultProcessDeploymentService(publication,
                ArtifactProjectionCoordinator.database(), new ProcessDeploymentOperationMetrics(), rollouts, versions,
                aliases, activated::add);

        assertThat(service.createRollout(create)).isSameAs(committed);
        assertThat(service.updateCanaryWeight(update)).isSameAs(committed);
        assertThat(service.promoteRollout(promote)).isSameAs(committed);
        assertThat(service.abortRollout(abort)).isSameAs(committed);
        assertThat(service.rollbackRollout(rollback)).isSameAs(committed);
        assertThat(activated).hasSize(5).allSatisfy(alias -> {
            assertThat(alias.getRef().namespace()).isEqualTo("default");
            assertThat(alias.getRef().code()).isEqualTo("order.process");
            assertThat(alias.getRef().alias()).isEqualTo("production");
            assertThat(alias.getStableVersion().version()).isEqualTo("v1");
            assertThat(alias.getCandidateVersion().version()).isEqualTo("v2");
            assertThat(alias.getCandidateWeightBps()).isEqualTo(2_500);
            assertThat(alias.getAliasRevision()).isEqualTo(3L);
        });
    }

    @Test
    void exposesActivationFailureAfterTheRouteTransactionCommits() {
        VersionPublicationService publication = mock(VersionPublicationService.class);
        RolloutControlService rollouts = mock(RolloutControlService.class);
        ProcessVersionRepository versions = mock(ProcessVersionRepository.class);
        ProcessAliasRepository aliases = mock(ProcessAliasRepository.class);
        CreateRolloutCommand command = mock(CreateRolloutCommand.class);
        ProcessRollout committed = mock(ProcessRollout.class);
        when(committed.getId()).thenReturn("rollout-1");
        when(committed.getAlias()).thenReturn(ProcessRef.alias("default", "order.process", "production"));
        when(rollouts.create(command)).thenReturn(committed);
        when(aliases.resolve("default", "order.process", "production"))
            .thenReturn(Optional.of(ProcessAliasRecord
                .builder()
                .namespace("default")
                .code("order.process")
                .alias("production")
                .stableVersion("v1")
                .revision(1L)
                .updatedBy("release-bot")
                .updatedAt(10L)
                .build()));
        IllegalStateException failure = new IllegalStateException("local activation failed");
        RoutingActivation activation = ignored -> {
            throw failure;
        };
        DefaultProcessDeploymentService service = new DefaultProcessDeploymentService(publication,
                ArtifactProjectionCoordinator.database(), new ProcessDeploymentOperationMetrics(), rollouts, versions,
                aliases, activation);

        assertThatThrownBy(() -> service.createRollout(command)).isSameAs(failure);
    }
}
