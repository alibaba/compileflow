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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactPayloads;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcRolloutRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcRoutingOutboxRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.control.repository.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.control.test.H2TestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingProjectionReconcilerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String NAMESPACE = ProcessRef.DEFAULT_NAMESPACE;
    private static final String CODE = "reconcile.flow";
    private static final String ROUTE = "production";
    private static final String ARTIFACT_PREFIX = "test.reconcile.artifact.";
    private JdbcProcessAliasRepository aliasRepository;
    private JdbcProcessVersionRepository versionRepository;
    private JdbcRoutingOutboxRepository outboxRepository;
    private InMemoryDeploymentSyncChannel channel;
    private DataSource dataSource;

    private static String artifactKey() {
        return ProcessArtifactKeys.versioned(ARTIFACT_PREFIX, NAMESPACE, CODE, "v1");
    }

    @BeforeEach
    void setUp() {
        dataSource = H2TestDatabase.createInMemoryDataSource();
        versionRepository = new JdbcProcessVersionRepository(dataSource);
        versionRepository.save(ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .version("v1")
            .modelType(ProcessModelType.TBBPM)
            .processDefinition(ProcessDefinition.inline(CODE, "<xml/>"))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessModelType.TBBPM,
                    ProcessDefinition.inline(CODE, "<xml/>"), Map.of()))
            .actor("test")
            .createdAt(1L)
            .build());
        new JdbcRolloutRepository(dataSource, null)
            .create(RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("initial-route",
                    ProcessRef.alias(NAMESPACE, CODE, ROUTE), ProcessRef.version(NAMESPACE, CODE, "v1"), 0L, "test",
                    null)));
        aliasRepository = new JdbcProcessAliasRepository(dataSource);
        outboxRepository = new JdbcRoutingOutboxRepository(dataSource);
        channel = new InMemoryDeploymentSyncChannel();
    }

    @Test
    void matchingRouteNeedsNoCorrection() {
        ProcessAliasRecord route = aliasRepository.resolve(NAMESPACE, CODE, ROUTE).orElseThrow();
        String key = RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE);
        assertThat(channel.compareAndSet(key, null,
                RoutingStatePayloads.aliasStateJson(NAMESPACE, CODE, ROUTE, route.getStableVersion(),
                        route.getCandidateVersion(),
                        route.getCandidateWeightBps() == null ? null : route.getCandidateWeightBps().intValue(),
                        route.getRevision(), route.getUpdatedBy(), route.getUpdatedAt()), "application/json", TIMEOUT))
            .isTrue();
        RoutingProjectionReconciler task = task(false);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isZero();
    }

    @Test
    void missingChannelRouteIsReportedWithTheRouteRevision() {
        RoutingProjectionReconciler task = task(false);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getMismatchDetails().get(0).getRepositoryRevision()).isEqualTo(1L);
        assertThat(result.getMismatchDetails().get(0).getChannelRevision()).isZero();
        assertThat(result.getAutoFixedCount()).isZero();
    }

    @Test
    void existingDeliveryAlreadyRepresentsTheRequiredCorrection() {
        RoutingProjectionReconciler task = task(true);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();
        List<RoutingOutboxRecord> pending = outboxRepository.claimPending(10, "test", 10_000L);

        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(pending).hasSize(1);
        assertThat(pending)
            .extracting(record -> RoutingStateParser.parse(record.getPayload()).getAliasRevision())
            .containsOnly(1L);
        assertThat(pending)
            .extracting(record -> RoutingStateParser.parse(record.getPayload()).getActor())
            .containsOnly("test");
    }

    @Test
    void completedDeliveryCanBeReactivatedWhenTheChannelDriftsAgain() {
        RoutingOutboxRecord initial = outboxRepository.claimPending(1, "initial-worker", 10_000L).get(0);
        assertThat(outboxRepository.markDelivered(initial.getId(), initial.getLeaseToken())).isOne();

        RoutingProjectionReconciler.ReconciliationResult result = task(true).reconcile();
        List<RoutingOutboxRecord> pending = outboxRepository.claimPending(10, "repair-worker", 10_000L);

        assertThat(result.getAutoFixedCount()).isEqualTo(1);
        assertThat(pending).singleElement().extracting(RoutingOutboxRecord::getId).isEqualTo(initial.getId());
    }

    @Test
    void sameRevisionConflictIsReportedButNotAutoFixed() {
        String key = RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE);
        assertThat(channel.compareAndSet(key, null,
                RoutingStatePayloads.aliasStateJson(NAMESPACE, CODE, ROUTE, "conflicting-version", null, null, 1L,
                        "test", 1L), "application/json", TIMEOUT))
            .isTrue();
        RoutingProjectionReconciler task = task(true);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();
        List<RoutingOutboxRecord> pending = outboxRepository.claimPending(10, "test", 10_000L);

        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(result.getMismatchDetails().get(0).getReason()).contains("conflicts at the same or a newer revision");
        assertThat(pending).hasSize(1);
    }

    @Test
    void discoversAuthoritativeRoutesAfterOutboxHistoryIsDeleted() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM cf_routing_outbox")) {
            statement.executeUpdate();
        }

        RoutingProjectionReconciler.ReconciliationResult result = task(false).reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isEqualTo(1);
    }

    @Test
    void reportsChannelReadFailuresAsProcessFailures() throws Exception {
        DeploymentSyncChannel failingChannel = mock(DeploymentSyncChannel.class);
        when(failingChannel.read(RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE), TIMEOUT))
            .thenThrow(new IllegalStateException("channel unavailable"));
        RoutingProjectionReconciler task = new RoutingProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.database(), failingChannel, outboxRepository, null, false, TIMEOUT);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isZero();
        assertThat(result.getFailedProcessesCount()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isZero();
    }

    @Test
    void failedAliasDoesNotBlockReconciliationOfSiblingProcessAliases() throws Exception {
        new JdbcRolloutRepository(dataSource, null)
            .create(RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("preview-route",
                    ProcessRef.alias(NAMESPACE, CODE, "preview"), ProcessRef.version(NAMESPACE, CODE, "v1"), 0L, "test",
                    null)));
        DeploymentSyncChannel partiallyFailingChannel = mock(DeploymentSyncChannel.class);
        when(partiallyFailingChannel.read(RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE), TIMEOUT))
            .thenThrow(new IllegalStateException("production route unavailable"));
        RoutingProjectionReconciler task = new RoutingProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.database(), partiallyFailingChannel, outboxRepository, null, false,
                TIMEOUT);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getFailedProcessesCount()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getMismatchDetails())
            .extracting(RoutingProjectionReconciler.ReconciliationMismatch::getAliasName)
            .containsExactly("preview");
    }

    @Test
    void missingActiveArtifactProjectionIsRebuiltIdempotently() throws Exception {
        RoutingProjectionReconciler task = channelArtifactTask(true);

        RoutingProjectionReconciler.ReconciliationResult first = task.reconcile();

        assertThat(first.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(first.getMissingArtifactProjections()).isEqualTo(1);
        assertThat(first.getRepairedArtifactProjections()).isEqualTo(1);
        assertThat(first.getConflictingArtifactProjections()).isZero();
        assertThat(first.getFailedArtifactProjections()).isZero();
        assertThat(channel.read(artifactKey(), TIMEOUT)).isNotNull();

        RoutingProjectionReconciler.ReconciliationResult second = task.reconcile();

        assertThat(second.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(second.getMissingArtifactProjections()).isZero();
        assertThat(second.getRepairedArtifactProjections()).isZero();
        assertThat(second.getFailedArtifactProjections()).isZero();
        assertThat(task.getStatistics())
            .containsEntry("totalArtifactChecks", 2L)
            .containsEntry("totalArtifactMissing", 1L)
            .containsEntry("totalArtifactRepairs", 1L);
    }

    @Test
    void conflictingArtifactProjectionFailsClosedWithoutBlockingRoutingRepair() throws Exception {
        String conflictingContent = "<different/>";
        ProcessDefinition.Inline conflictingDefinition = ProcessDefinition.inline(CODE, conflictingContent);
        String conflictingPayload = ProcessArtifactPayloads.artifactJson(NAMESPACE, CODE, "v1", ProcessModelType.TBBPM,
                conflictingContent,
                ProcessArtifactDigest.compute(ProcessModelType.TBBPM, conflictingDefinition, Map.of()));
        assertThat(channel.compareAndSet(artifactKey(), null, conflictingPayload, "application/json", TIMEOUT)).isTrue();
        RoutingProjectionReconciler task = channelArtifactTask(true);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(result.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(result.getMissingArtifactProjections()).isZero();
        assertThat(result.getRepairedArtifactProjections()).isZero();
        assertThat(result.getConflictingArtifactProjections()).isEqualTo(1);
        assertThat(result.getFailedArtifactProjections()).isEqualTo(1);
        assertThat(channel.read(artifactKey(), TIMEOUT)).isEqualTo(conflictingPayload);
    }

    @Test
    void disabledAutoFixDoesNotWriteArtifactProjections() throws Exception {
        RoutingProjectionReconciler task = channelArtifactTask(false);

        RoutingProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(result.getMissingArtifactProjections()).isEqualTo(1);
        assertThat(result.getRepairedArtifactProjections()).isZero();
        assertThat(channel.read(artifactKey(), TIMEOUT)).isNull();
    }

    @Test
    void scheduledReconcilerRunsImmediatelyOnStart() throws Exception {
        RoutingProjectionReconciler task = mock(RoutingProjectionReconciler.class);
        CountDownLatch reconciled = new CountDownLatch(1);
        when(task.reconcile()).thenAnswer(invocation -> {
            reconciled.countDown();
            return null;
        });

        try (RoutingProjectionReconciler.ScheduledReconciler reconciler =
                new RoutingProjectionReconciler.ScheduledReconciler(task, Duration.ofDays(1))) {
            reconciler.start();

            assertThat(reconciled.await(1L, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void scheduledReconcilerCanResumeAfterStop() throws Exception {
        RoutingProjectionReconciler task = mock(RoutingProjectionReconciler.class);
        AtomicInteger phase = new AtomicInteger(1);
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch resumedRun = new CountDownLatch(1);
        when(task.reconcile()).thenAnswer(invocation -> {
            if (phase.get() == 1) {
                firstRun.countDown();
            } else {
                resumedRun.countDown();
            }
            return null;
        });

        try (RoutingProjectionReconciler.ScheduledReconciler reconciler =
                new RoutingProjectionReconciler.ScheduledReconciler(task, Duration.ofDays(1))) {
            reconciler.start();
            assertThat(firstRun.await(1L, TimeUnit.SECONDS)).isTrue();
            reconciler.stop();

            phase.set(2);
            reconciler.start();
            assertThat(resumedRun.await(1L, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RoutingProjectionReconciler task(boolean autoFix) {
        return new RoutingProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.database(), channel, outboxRepository, null, autoFix, TIMEOUT);
    }

    private RoutingProjectionReconciler channelArtifactTask(boolean autoFix) {
        return new RoutingProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.channel(new ProcessArtifactProjector(channel, ARTIFACT_PREFIX, TIMEOUT)),
                channel, outboxRepository, null, autoFix, TIMEOUT);
    }
}
