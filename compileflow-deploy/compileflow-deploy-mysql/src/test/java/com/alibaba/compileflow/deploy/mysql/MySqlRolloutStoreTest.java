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
package com.alibaba.compileflow.deploy.mysql;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.control.observability.DeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.deploy.control.RolloutService;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.spi.store.RolloutStoreQuery;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MySqlRolloutStoreTest {
    private static final String NAMESPACE = ProcessRef.DEFAULT_NAMESPACE;
    private static final String CODE = "checkout.flow";
    private static final String ROUTE = "production";
    private DataSource dataSource;
    private MySqlDeployStore versionRepository;
    private MySqlDeployStore aliasRepository;
    private MySqlDeployStore repository;

    @BeforeEach
    void setUp() {
        dataSource = H2TestDatabase.createInMemoryDataSource();
        versionRepository = new MySqlDeployStore(dataSource, null);
        aliasRepository = new MySqlDeployStore(dataSource, null);
        repository = new MySqlDeployStore(dataSource, null);
    }

    @Test
    void schemaRejectsNullCanaryWeight() throws Exception {
        assertInvalidNullState("UPDATE cf_process_alias SET candidate_weight_bps = NULL", "ck_alias_candidate");
    }

    @Test
    void schemaRejectsNullActiveRolloutMarker() throws Exception {
        assertInvalidNullState("UPDATE cf_rollout SET active_marker = NULL WHERE phase = 'IN_PROGRESS'",
                "ck_rollout_active_marker");
    }

    @Test
    void schemaRejectsNullSourcePhaseAfterTheFirstEvent() throws Exception {
        assertInvalidNullState("INSERT INTO cf_rollout_event"
                + " (rollout_id, sequence, event_type, from_phase, to_phase, actor, created_at)"
                + " SELECT id, 2, 'CANARY_WEIGHT_UPDATED', NULL, 'IN_PROGRESS', 'test', 1"
                + " FROM cf_rollout WHERE phase = 'IN_PROGRESS'", "ck_rollout_event_transition");
    }

    private void assertInvalidNullState(String sql, String constraint) throws Exception {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("baseline", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        repository.create(create("canary", "v2", RolloutStrategy.CANARY, 1_000));
        try (Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                .isInstanceOf(java.sql.SQLException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).containsIgnoringCase(constraint));
        }
    }

    @Test
    void allAtOnceCreatesExplicitRouteAndAuditHistory() {
        saveVersion("v1");

        ProcessRollout rollout = repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));

        assertThat(rollout.getPhase()).isEqualTo(RolloutPhase.COMPLETED);
        assertThat(rollout.getBaselineVersion()).isNull();
        assertThat(rollout.getTargetWeightBps()).isEqualTo(10_000);
        assertThat(rollout.getRolloutRevision()).isEqualTo(1L);
        assertThat(rollout.getBaseAliasRevision()).isZero();
        assertThat(rollout.getAliasRevision()).isEqualTo(1L);
        assertRoute("v1", null, null);
        assertThat(repository.listEvents(rollout.getId()))
            .extracting(event -> event.getType())
            .containsExactly("COMPLETED");
        assertThat(versionRepository.find(NAMESPACE, CODE, "v1")).isPresent();
        assertThat(new MySqlDeployStore(dataSource, null).countByStatus(RoutingOutboxRecord.Status.PENDING)).isEqualTo(
                1L);
    }

    @Test
    void canaryCapturesBaselineThenPromotesWithRevisionCas() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));

        ProcessRollout canary = repository.create(create("canary-v2", "v2", RolloutStrategy.CANARY, 1_000));
        assertThat(canary.getPhase()).isEqualTo(RolloutPhase.IN_PROGRESS);
        assertThat(canary.getBaselineVersion()).isEqualTo(ProcessRef.version(NAMESPACE, CODE, "v1"));
        assertThat(canary.getBaseAliasRevision()).isEqualTo(1L);
        assertThat(canary.getAliasRevision()).isEqualTo(2L);
        assertRoute("v1", "v2", 1_000);

        ProcessRollout shifted = repository.updateCanary(
                new UpdateCanaryWeightCommand(canary.getId(), 3_500, canary.getRolloutRevision(), "operator"));
        assertThat(shifted.getRolloutRevision()).isEqualTo(2L);
        assertThat(shifted.getAliasRevision()).isEqualTo(3L);
        assertRoute("v1", "v2", 3_500);

        ProcessRollout promoted =
                repository.promote(new PromoteRolloutCommand(shifted.getId(), shifted.getRolloutRevision(), "operator"));
        assertThat(promoted.getPhase()).isEqualTo(RolloutPhase.COMPLETED);
        assertThat(promoted.getRolloutRevision()).isEqualTo(3L);
        assertThat(promoted.getAliasRevision()).isEqualTo(4L);
        assertRoute("v2", null, null);
        assertThat(repository.listEvents(promoted.getId()))
            .extracting(event -> event.getType())
            .containsExactly("CANARY_STARTED", "CANARY_WEIGHT_UPDATED", "PROMOTED");
    }

    @Test
    void canaryTargetingIsPersistedAuditedAndIdempotentByContent() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, CODE, ROUTE);
        ProcessRef.Version target = ProcessRef.version(NAMESPACE, CODE, "v2");
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("region", "eu");
        parameters.put("tier", "gold");
        AliasTargeting targeting = new AliasTargeting("enterprise-cohort", parameters);

        CreateRolloutCommand command =
                CreateRolloutCommand.canary("targeted-v2", alias, target, 1L, 1_000, targeting, "operator",
                        "release notes");
        ProcessRollout created = repository.create(RolloutCreateRequest.deploy(command));

        assertThat(created.getTargeting()).isEqualTo(targeting);
        assertThat(aliasRepository.resolve(NAMESPACE, CODE, ROUTE).orElseThrow().getTargeting()).isEqualTo(targeting);

        LinkedHashMap<String, String> reordered = new LinkedHashMap<>();
        reordered.put("tier", "gold");
        reordered.put("region", "eu");
        ProcessRollout replay = repository.create(RolloutCreateRequest.deploy(CreateRolloutCommand.canary("targeted-v2",
                alias, target, 1L, 1_000, new AliasTargeting("enterprise-cohort", reordered), "operator",
                "release notes")));
        assertThat(replay.getId()).isEqualTo(created.getId());

        assertThatThrownBy(() -> repository.create(RolloutCreateRequest.deploy(CreateRolloutCommand.canary("targeted-v2",
                alias, target, 1L, 1_000, new AliasTargeting("enterprise-cohort", Map.of("region", "us")), "operator",
                "release notes"))))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.IDEMPOTENCY_CONFLICT));

        ProcessRollout promoted =
                repository.promote(new PromoteRolloutCommand(created.getId(), created.getRolloutRevision(), "operator"));
        assertThat(promoted.getTargeting()).isEqualTo(targeting);
        assertThat(aliasRepository.resolve(NAMESPACE, CODE, ROUTE).orElseThrow().getTargeting()).isNull();
    }

    @Test
    void abortRestoresCapturedBaseline() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout canary = repository.create(create("canary-v2", "v2", RolloutStrategy.CANARY, 2_000));

        ProcessRollout aborted = repository.abort(
                new AbortRolloutCommand(canary.getId(), canary.getRolloutRevision(), "operator",
                        "error threshold breached"));

        assertThat(aborted.getPhase()).isEqualTo(RolloutPhase.ABORTED);
        assertRoute("v1", null, null);
        assertThat(repository.listEvents(aborted.getId()).get(1).getReason()).isEqualTo("error threshold breached");
    }

    @Test
    void idempotencyKeyReturnsSameRolloutAndRejectsDifferentRequest() {
        saveVersion("v1");
        saveVersion("v2");
        RolloutCreateRequest firstCommand = create("stable-key", "v1", RolloutStrategy.ALL_AT_ONCE, null);

        ProcessRollout first = repository.create(firstCommand);
        ProcessRollout replay = repository.create(firstCommand);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(repository.listEvents(first.getId())).hasSize(1);
        assertThatThrownBy(() -> repository.create(create("stable-key", "v2", RolloutStrategy.ALL_AT_ONCE, null)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void idempotencyKeyIsScopedByRoute() {
        saveVersion("v1");

        ProcessRollout production = repository.create(
                createAtRevision("shared-key", RolloutOperationKind.DEPLOY, ROUTE, "v1", 0L, RolloutStrategy.ALL_AT_ONCE,
                        null, "alice"));
        ProcessRollout staging = repository.create(
                createAtRevision("shared-key", RolloutOperationKind.DEPLOY, "staging", "v1", 0L,
                        RolloutStrategy.ALL_AT_ONCE, null, "alice"));

        assertThat(staging.getId()).isNotEqualTo(production.getId());
        assertThat(aliasRepository.resolve(NAMESPACE, CODE, ROUTE)).isPresent();
        assertThat(aliasRepository.resolve(NAMESPACE, CODE, "staging")).isPresent();
    }

    @Test
    void idempotencyKeyIsScopedByOperationKind() {
        saveVersion("v1");
        saveVersion("v2");

        ProcessRollout deploy = repository.create(
                createAtRevision("shared-key", RolloutOperationKind.DEPLOY, ROUTE, "v2", 0L, RolloutStrategy.ALL_AT_ONCE,
                        null, "alice"));
        ProcessRollout rollback = repository.create(
                createAtRevision("shared-key", RolloutOperationKind.ROLLBACK, ROUTE, "v1", deploy.getAliasRevision(),
                        RolloutStrategy.ALL_AT_ONCE, null, "alice"));

        assertThat(rollback.getId()).isNotEqualTo(deploy.getId());
        assertThat(rollback.getOperationKind()).isEqualTo(RolloutOperationKind.ROLLBACK);
        assertRoute("v1", null, null);
    }

    @Test
    void listSearchesProcessCodeAndRolloutIdCaseInsensitively() {
        saveVersion("v1");
        ProcessRollout rollout = repository.create(create("search-key", "v1", RolloutStrategy.ALL_AT_ONCE, null));

        List<ProcessRollout> byCode =
                repository.list(new RolloutStoreQuery(NAMESPACE, null, "CHECKOUT", null, null, null, 20));
        List<ProcessRollout> byId = repository.list(
                new RolloutStoreQuery(NAMESPACE, null, rollout.getId().substring(0, 8).toUpperCase(), null, null, null,
                        20));
        List<ProcessRollout> literalWildcard =
                repository.list(new RolloutStoreQuery(NAMESPACE, null, "%", null, null, null, 20));

        assertThat(byCode).extracting(ProcessRollout::getId).containsExactly(rollout.getId());
        assertThat(byId).extracting(ProcessRollout::getId).containsExactly(rollout.getId());
        assertThat(literalWildcard).isEmpty();
    }

    @Test
    void idempotentReplayRejectsADifferentActor() {
        saveVersion("v1");
        RolloutCreateRequest alice = createAtRevision("actor-key", RolloutOperationKind.DEPLOY, ROUTE, "v1", 0L,
                RolloutStrategy.ALL_AT_ONCE, null, "alice");
        repository.create(alice);

        assertThatThrownBy(() -> repository.create(
                createAtRevision("actor-key", RolloutOperationKind.DEPLOY, ROUTE, "v1", 0L, RolloutStrategy.ALL_AT_ONCE,
                        null, "bob")))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void mutationRequiresARealActor() {
        saveVersion("v1");

        assertThatThrownBy(() -> repository.create(
                createAtRevision("missing-actor", RolloutOperationKind.DEPLOY, ROUTE, "v1", 0L,
                        RolloutStrategy.ALL_AT_ONCE, null, " ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("actor must not be blank");
        assertThat(aliasRepository.resolve(NAMESPACE, CODE, ROUTE)).isEmpty();
    }

    @Test
    void rollbackCreatesANewIdempotentRolloutWithoutRewritingHistory() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout source = repository.create(create("deploy-v2", "v2", RolloutStrategy.ALL_AT_ONCE, null));
        RolloutService service = new RolloutService(repository, versionRepository,
                ArtifactProjectionCoordinator.source(), new DeploymentOperationMetrics());
        RollbackRolloutCommand command =
                new RollbackRolloutCommand(source.getId(), "rollback-v2", source.getAliasRevision(), "alice");

        ProcessRollout rollback = service.rollback(command);
        ProcessRollout replay = service.rollback(command);

        assertThat(rollback.getId()).isNotEqualTo(source.getId());
        assertThat(replay.getId()).isEqualTo(rollback.getId());
        assertThat(rollback.getOperationKind()).isEqualTo(RolloutOperationKind.ROLLBACK);
        assertThat(rollback.getTargetVersion()).isEqualTo(ProcessRef.version(NAMESPACE, CODE, "v1"));
        assertThat(rollback.getBaselineVersion()).isEqualTo(ProcessRef.version(NAMESPACE, CODE, "v2"));
        assertThat(repository.find(source.getId()))
            .get()
            .extracting(ProcessRollout::getTargetVersion, ProcessRollout::getBaselineVersion, ProcessRollout::getPhase)
            .containsExactly(ProcessRef.version(NAMESPACE, CODE, "v2"), ProcessRef.version(NAMESPACE, CODE, "v1"),
                    RolloutPhase.COMPLETED);
        assertRoute("v1", null, null);
    }

    @Test
    void staleRevisionDoesNotOverwriteRoute() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout canary = repository.create(create("canary-v2", "v2", RolloutStrategy.CANARY, 1_000));
        repository.updateCanary(
                new UpdateCanaryWeightCommand(canary.getId(), 2_500, canary.getRolloutRevision(), "operator"));

        assertThatThrownBy(() -> repository.promote(
                new PromoteRolloutCommand(canary.getId(), canary.getRolloutRevision(), "stale-operator")))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
        assertRoute("v1", "v2", 2_500);
    }

    @Test
    void routeOwnershipChangeIsReportedAsConcurrentModification() throws Exception {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout canary = repository.create(create("canary-v2", "v2", RolloutStrategy.CANARY, 1_000));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cf_process_alias SET revision = revision + 1 " + "WHERE namespace = ? AND code = ? AND alias = ?")) {
            statement.setString(1, NAMESPACE);
            statement.setString(2, CODE);
            statement.setString(3, ROUTE);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(() -> repository.promote(
                new PromoteRolloutCommand(canary.getId(), canary.getRolloutRevision(), "operator")))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
        assertRoute("v1", "v2", 1_000);
    }

    @Test
    void staleRouteRevisionRejectsACompetingRolloutBeforeMutation() {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));

        assertThatThrownBy(() -> repository.create(
                createAtRevision("stale-deploy-v2", "v2", 0L, RolloutStrategy.ALL_AT_ONCE, null)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
        assertRoute("v1", null, null);
    }

    @Test
    void exhaustedAliasRevisionFailsWithoutWrapping() throws Exception {
        saveVersion("v1");
        saveVersion("v2");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cf_process_alias SET revision = ? " + "WHERE namespace = ? AND code = ? AND alias = ?")) {
            statement.setLong(1, Long.MAX_VALUE);
            statement.setString(2, NAMESPACE);
            statement.setString(3, CODE);
            statement.setString(4, ROUTE);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(() -> repository.create(
                createAtRevision("exhausted-revision", "v2", Long.MAX_VALUE, RolloutStrategy.ALL_AT_ONCE, null)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ROLLOUT_CONFLICT));
        assertRoute("v1", null, null);
    }

    @Test
    void concurrentIdempotentCreatesReturnTheSameRollout() throws Exception {
        saveVersion("v1");
        RolloutCreateRequest command = create("concurrent-key", "v1", RolloutStrategy.ALL_AT_ONCE, null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProcessRollout> first = executor.submit(() -> {
                start.await();
                return repository.create(command);
            });
            Future<ProcessRollout> second = executor.submit(() -> {
                start.await();
                return repository.create(command);
            });
            start.countDown();

            ProcessRollout firstResult = first.get(10, TimeUnit.SECONDS);
            ProcessRollout secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(secondResult.getId()).isEqualTo(firstResult.getId());
            assertThat(repository.listEvents(firstResult.getId())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentFirstRouteCreatesDoNotMergeDifferentIntent() throws Exception {
        saveVersion("v1");
        RolloutCreateRequest firstCommand = createAtRevision("first-key", "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        RolloutCreateRequest secondCommand = createAtRevision("second-key", "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> createOutcome(firstCommand, start));
            Future<Object> second = executor.submit(() -> createOutcome(secondCommand, start));
            start.countDown();

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(Arrays.asList(firstResult, secondResult)).filteredOn(ProcessRollout.class::isInstance).hasSize(1);
            assertThat(Arrays.asList(firstResult, secondResult))
                .filteredOn(DeploymentException.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                    .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
            assertRoute("v1", null, null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void competingCanaryMutationAndCreateUseDomainConflictsInsteadOfDeadlocks() throws Exception {
        saveVersion("v1");
        saveVersion("v2");
        saveVersion("v3");
        repository.create(create("deploy-v1", "v1", RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout canary = repository.create(create("canary-v2", "v2", RolloutStrategy.CANARY, 1_000));
        RolloutCreateRequest competing =
                createAtRevision("deploy-v3", "v3", canary.getAliasRevision(), RolloutStrategy.ALL_AT_ONCE, null);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> promotion = executor.submit(() -> rolloutOutcome(() -> repository.promote(
                            new PromoteRolloutCommand(canary.getId(), canary.getRolloutRevision(), "operator")), start));
            Future<Object> creation = executor.submit(() -> rolloutOutcome(() -> repository.create(competing), start));
            start.countDown();

            Object promotionResult = promotion.get(10, TimeUnit.SECONDS);
            Object creationResult = creation.get(10, TimeUnit.SECONDS);
            assertThat(Arrays.asList(promotionResult, creationResult)).filteredOn(ProcessRollout.class::isInstance).hasSize(
                    1);
            assertThat(Arrays.asList(promotionResult, creationResult))
                .filteredOn(DeploymentException.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                    .isIn(DeploymentErrorCode.ROLLOUT_CONFLICT, DeploymentErrorCode.CONCURRENT_MODIFICATION));
            assertRoute("v2", null, null);
        } finally {
            executor.shutdownNow();
        }
    }

    private RolloutCreateRequest create(String key, String target, RolloutStrategy strategy, Integer weightBps) {
        long expectedRouteRevision =
                aliasRepository.resolve(NAMESPACE, CODE, ROUTE).map(ProcessAliasRecord::getRevision).orElse(0L);
        return createAtRevision(key, target, expectedRouteRevision, strategy, weightBps);
    }

    private RolloutCreateRequest createAtRevision(String key, String target, long expectedRouteRevision,
            RolloutStrategy strategy, Integer weightBps) {
        return createAtRevision(key, RolloutOperationKind.DEPLOY, ROUTE, target, expectedRouteRevision, strategy,
                weightBps, "operator");
    }

    private RolloutCreateRequest createAtRevision(String key, RolloutOperationKind operationKind, String route,
            String target, long expectedRouteRevision, RolloutStrategy strategy, Integer weightBps, String actor) {
        if (operationKind == RolloutOperationKind.ROLLBACK) {
            if (strategy != RolloutStrategy.ALL_AT_ONCE || weightBps != null) {
                throw new IllegalArgumentException("Rollback test request must be all-at-once");
            }
            return RolloutCreateRequest.rollback(key, ProcessRef.alias(NAMESPACE, CODE, route),
                    ProcessRef.version(NAMESPACE, CODE, target), expectedRouteRevision, actor, "release notes");
        }
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, CODE, route);
        ProcessRef.Version version = ProcessRef.version(NAMESPACE, CODE, target);
        CreateRolloutCommand command = strategy == RolloutStrategy.CANARY
                ? CreateRolloutCommand.canary(key, alias, version, expectedRouteRevision,
                        Objects.requireNonNull(weightBps, "weightBps"), actor, "release notes")
                : CreateRolloutCommand.allAtOnce(key, alias, version, expectedRouteRevision, actor, "release notes");
        return RolloutCreateRequest.deploy(command);
    }

    private void saveVersion(String version) {
        versionRepository.save(ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .version(version)
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, "<xml/>"))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, CODE,
                            "<xml/>"), Map.of()))
            .actor("test")
            .createdAt(1L)
            .build());
    }

    private Object createOutcome(RolloutCreateRequest request, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            return repository.create(request);
        } catch (DeploymentException failure) {
            return failure;
        }
    }

    private Object rolloutOutcome(Callable<ProcessRollout> action, CountDownLatch start) throws Exception {
        start.await();
        try {
            return action.call();
        } catch (DeploymentException failure) {
            return failure;
        }
    }

    private void assertRoute(String stableVersion, String candidateVersion, Integer candidateWeightBps) {
        ProcessAliasRecord route = aliasRepository.resolve(NAMESPACE, CODE, ROUTE).orElseThrow();
        assertThat(route.getStableVersion()).isEqualTo(stableVersion);
        assertThat(route.getCandidateVersion()).isEqualTo(candidateVersion);
        assertThat(route.getCandidateWeightBps()).isEqualTo(candidateWeightBps);
    }
}
