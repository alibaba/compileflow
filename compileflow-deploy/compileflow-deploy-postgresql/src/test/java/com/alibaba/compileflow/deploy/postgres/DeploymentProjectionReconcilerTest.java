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
package com.alibaba.compileflow.deploy.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.projection.DeploymentProjectionReconciler;
import com.alibaba.compileflow.deploy.control.projection.ProcessArtifactProjector;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactCodec;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
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

class DeploymentProjectionReconcilerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String NAMESPACE = ProcessRef.DEFAULT_NAMESPACE;
    private static final String CODE = "reconcile.flow";
    private static final String ROUTE = "production";
    private static final String ARTIFACT_PREFIX = "test.reconcile.artifact.";
    private PostgresDeployStore aliasRepository;
    private PostgresDeployStore versionRepository;
    private PostgresDeployStore outboxRepository;
    private InMemoryDeploymentProjectionStore projectionStore;
    private DataSource dataSource;

    private static String artifactKey() {
        return ProcessArtifactKeys.versioned(ARTIFACT_PREFIX, NAMESPACE, CODE, "v1");
    }

    @BeforeEach
    void setUp() {
        dataSource = H2TestDatabase.createInMemoryDataSource();
        versionRepository = new PostgresDeployStore(dataSource, null);
        versionRepository.save(ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .version("v1")
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, "<xml/>"))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, CODE,
                            "<xml/>"), Map.of()))
            .actor("test")
            .createdAt(1L)
            .build());
        new PostgresDeployStore(dataSource, null)
            .create(RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("initial-route",
                    ProcessRef.alias(NAMESPACE, CODE, ROUTE), ProcessRef.version(NAMESPACE, CODE, "v1"), 0L, "test",
                    null)));
        aliasRepository = new PostgresDeployStore(dataSource, null);
        outboxRepository = new PostgresDeployStore(dataSource, null);
        projectionStore = new InMemoryDeploymentProjectionStore();
    }

    @Test
    void matchingRouteNeedsNoCorrection() {
        ProcessAliasRecord route = aliasRepository.resolve(NAMESPACE, CODE, ROUTE).orElseThrow();
        String key = RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE);
        assertThat(projectionStore.compareAndSet(key, null,
                RoutingStateCodec.aliasStateJson(NAMESPACE, CODE, ROUTE, route.getStableVersion(),
                        route.getCandidateVersion(),
                        route.getCandidateWeightBps() == null ? null : route.getCandidateWeightBps().intValue(),
                        route.getRevision(), route.getUpdatedBy(), route.getUpdatedAt()), "application/json", TIMEOUT))
            .isTrue();
        DeploymentProjectionReconciler task = task(false);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isZero();
    }

    @Test
    void missingProjectionStoreRouteIsReportedWithTheRouteRevision() {
        DeploymentProjectionReconciler task = task(false);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getMismatchDetails().get(0).getRepositoryRevision()).isEqualTo(1L);
        assertThat(result.getMismatchDetails().get(0).getProjectionRevision()).isZero();
        assertThat(result.getAutoFixedCount()).isZero();
    }

    @Test
    void existingDeliveryAlreadyRepresentsTheRequiredCorrection() {
        DeploymentProjectionReconciler task = task(true);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();
        List<RoutingOutboxRecord> pending = outboxRepository.claimPending(10, "test", 10_000L);

        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(pending).hasSize(1);
        assertThat(pending)
            .extracting(record -> RoutingStateCodec.parse(record.getPayload()).getAliasRevision())
            .containsOnly(1L);
        assertThat(pending)
            .extracting(record -> RoutingStateCodec.parse(record.getPayload()).getActor())
            .containsOnly("test");
    }

    @Test
    void completedDeliveryCanBeReactivatedWhenTheProjectionStoreDriftsAgain() {
        RoutingOutboxRecord initial = outboxRepository.claimPending(1, "initial-worker", 10_000L).get(0);
        assertThat(outboxRepository.markDelivered(initial.getId(), initial.getLeaseToken())).isOne();

        DeploymentProjectionReconciler.ReconciliationResult result = task(true).reconcile();
        List<RoutingOutboxRecord> pending = outboxRepository.claimPending(10, "repair-worker", 10_000L);

        assertThat(result.getAutoFixedCount()).isEqualTo(1);
        assertThat(pending).singleElement().extracting(RoutingOutboxRecord::getId).isEqualTo(initial.getId());
    }

    @Test
    void sameRevisionConflictIsReportedButNotAutoFixed() {
        String key = RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE);
        assertThat(projectionStore.compareAndSet(key, null,
                RoutingStateCodec.aliasStateJson(NAMESPACE, CODE, ROUTE, "conflicting-version", null, null, 1L, "test",
                        1L), "application/json", TIMEOUT))
            .isTrue();
        DeploymentProjectionReconciler task = task(true);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();
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

        DeploymentProjectionReconciler.ReconciliationResult result = task(false).reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isEqualTo(1);
    }

    @Test
    void reportsProjectionStoreReadFailuresAsProcessFailures() throws Exception {
        DeploymentProjectionStore failingProjectionStore = mock(DeploymentProjectionStore.class);
        when(failingProjectionStore.read(RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE), TIMEOUT))
            .thenThrow(new IllegalStateException("projection store unavailable"));
        DeploymentProjectionReconciler task = new DeploymentProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.source(), failingProjectionStore, outboxRepository, null, false, TIMEOUT);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isZero();
        assertThat(result.getFailedProcessesCount()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isZero();
    }

    @Test
    void failedAliasDoesNotBlockReconciliationOfSiblingProcessAliases() throws Exception {
        new PostgresDeployStore(dataSource, null)
            .create(RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("preview-route",
                    ProcessRef.alias(NAMESPACE, CODE, "preview"), ProcessRef.version(NAMESPACE, CODE, "v1"), 0L, "test",
                    null)));
        DeploymentProjectionStore partiallyFailingProjectionStore = mock(DeploymentProjectionStore.class);
        when(partiallyFailingProjectionStore.read(RoutingStateKeys.aliasState(null, NAMESPACE, CODE, ROUTE), TIMEOUT))
            .thenThrow(new IllegalStateException("production route unavailable"));
        DeploymentProjectionReconciler task = new DeploymentProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.source(), partiallyFailingProjectionStore, outboxRepository, null, false,
                TIMEOUT);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedProcesses()).isEqualTo(1);
        assertThat(result.getCheckedItems()).isEqualTo(1);
        assertThat(result.getFailedProcessesCount()).isEqualTo(1);
        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getMismatchDetails())
            .extracting(DeploymentProjectionReconciler.ReconciliationMismatch::getAliasName)
            .containsExactly("preview");
    }

    @Test
    void missingActiveArtifactProjectionIsRebuiltIdempotently() throws Exception {
        DeploymentProjectionReconciler task = channelArtifactTask(true);

        DeploymentProjectionReconciler.ReconciliationResult first = task.reconcile();

        assertThat(first.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(first.getMissingArtifactProjections()).isEqualTo(1);
        assertThat(first.getRepairedArtifactProjections()).isEqualTo(1);
        assertThat(first.getConflictingArtifactProjections()).isZero();
        assertThat(first.getFailedArtifactProjections()).isZero();
        assertThat(projectionStore.read(artifactKey(), TIMEOUT)).isNotNull();

        DeploymentProjectionReconciler.ReconciliationResult second = task.reconcile();

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
        ProcessDefinition.Inline conflictingDefinition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, conflictingContent);
        String conflictingPayload = ProcessArtifactCodec.artifactJson(NAMESPACE, CODE, "v1", ProcessModelType.TBBPM,
                conflictingContent, ProcessArtifactDigest.compute(conflictingDefinition, Map.of()));
        assertThat(projectionStore.compareAndSet(artifactKey(), null, conflictingPayload, "application/json", TIMEOUT))
            .isTrue();
        DeploymentProjectionReconciler task = channelArtifactTask(true);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getMismatchesFound()).isEqualTo(1);
        assertThat(result.getAutoFixedCount()).isZero();
        assertThat(result.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(result.getMissingArtifactProjections()).isZero();
        assertThat(result.getRepairedArtifactProjections()).isZero();
        assertThat(result.getConflictingArtifactProjections()).isEqualTo(1);
        assertThat(result.getFailedArtifactProjections()).isEqualTo(1);
        assertThat(projectionStore.read(artifactKey(), TIMEOUT)).isEqualTo(conflictingPayload);
    }

    @Test
    void disabledAutoFixDoesNotWriteArtifactProjections() throws Exception {
        DeploymentProjectionReconciler task = channelArtifactTask(false);

        DeploymentProjectionReconciler.ReconciliationResult result = task.reconcile();

        assertThat(result.getCheckedArtifactVersions()).isEqualTo(1);
        assertThat(result.getMissingArtifactProjections()).isEqualTo(1);
        assertThat(result.getRepairedArtifactProjections()).isZero();
        assertThat(projectionStore.read(artifactKey(), TIMEOUT)).isNull();
    }

    @Test
    void scheduledReconcilerRunsImmediatelyOnStart() throws Exception {
        DeploymentProjectionReconciler task = mock(DeploymentProjectionReconciler.class);
        CountDownLatch reconciled = new CountDownLatch(1);
        when(task.reconcile()).thenAnswer(invocation -> {
            reconciled.countDown();
            return null;
        });

        try (DeploymentProjectionReconciler.ScheduledReconciler reconciler =
                new DeploymentProjectionReconciler.ScheduledReconciler(task, Duration.ofDays(1))) {
            reconciler.start();

            assertThat(reconciled.await(1L, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void scheduledReconcilerCanResumeAfterStop() throws Exception {
        DeploymentProjectionReconciler task = mock(DeploymentProjectionReconciler.class);
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

        try (DeploymentProjectionReconciler.ScheduledReconciler reconciler =
                new DeploymentProjectionReconciler.ScheduledReconciler(task, Duration.ofDays(1))) {
            reconciler.start();
            assertThat(firstRun.await(1L, TimeUnit.SECONDS)).isTrue();
            reconciler.stop();

            phase.set(2);
            reconciler.start();
            assertThat(resumedRun.await(1L, TimeUnit.SECONDS)).isTrue();
        }
    }

    private DeploymentProjectionReconciler task(boolean autoFix) {
        return new DeploymentProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.source(), projectionStore, outboxRepository, null, autoFix, TIMEOUT);
    }

    private DeploymentProjectionReconciler channelArtifactTask(boolean autoFix) {
        return new DeploymentProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.projectionStore(
                        new ProcessArtifactProjector(projectionStore, ARTIFACT_PREFIX, TIMEOUT)), projectionStore,
                outboxRepository, null, autoFix, TIMEOUT);
    }
}
