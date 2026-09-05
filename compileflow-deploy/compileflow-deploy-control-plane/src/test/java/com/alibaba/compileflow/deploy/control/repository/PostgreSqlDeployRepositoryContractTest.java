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
package com.alibaba.compileflow.deploy.control.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.routing.ChannelRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatchPolicy;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatcher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
class PostgreSqlDeployRepositoryContractTest {
    private static final String NAMESPACE = ProcessRef.DEFAULT_NAMESPACE;
    private static final String CODE = "postgres-contract.flow";
    private static final String ROUTE = "production";
    private String schema;
    private PGSimpleDataSource adminDataSource;
    private DataSource dataSource;

    private static PGSimpleDataSource dataSource(String url, String username, String password, String currentSchema) {
        PGSimpleDataSource result = new PGSimpleDataSource();
        result.setUrl(url);
        result.setUser(username);
        result.setPassword(password);
        if (currentSchema != null) {
            result.setCurrentSchema(currentSchema);
        }
        return result;
    }

    private static void execute(DataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for PostgreSQL contract tests");
        }
        return value;
    }

    @BeforeAll
    void createIsolatedSchema() throws SQLException {
        String url = requireEnvironment("SPRING_DATASOURCE_URL");
        String username = requireEnvironment("SPRING_DATASOURCE_USERNAME");
        String password = requireEnvironment("SPRING_DATASOURCE_PASSWORD");
        schema = "cf_contract_" + UUID.randomUUID().toString().replace("-", "");
        adminDataSource = dataSource(url, username, password, null);
        execute(adminDataSource, "CREATE SCHEMA " + schema);
        PGSimpleDataSource schemaDataSource = dataSource(url, username, password, schema);
        Flyway
            .configure()
            .dataSource(schemaDataSource)
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/compileflow-deploy/migration")
            .cleanDisabled(true)
            .load()
            .migrate();
        dataSource = schemaDataSource;
    }

    @AfterAll
    void dropIsolatedSchema() throws SQLException {
        if (adminDataSource != null && schema != null) {
            execute(adminDataSource, "DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(dataSource,
                "TRUNCATE TABLE cf_rollout_event, cf_rollout, cf_process_alias,"
                + " cf_routing_outbox, cf_process_version RESTART IDENTITY CASCADE");
    }

    @Test
    void concurrentImmutableVersionWritesPreserveOneExactIdentity() throws Exception {
        JdbcProcessVersionRepository firstRepository = new JdbcProcessVersionRepository(dataSource);
        JdbcProcessVersionRepository secondRepository = new JdbcProcessVersionRepository(dataSource);
        ProcessVersionRecord first = version("v1", "<flow id=\"first\"/>");
        ProcessVersionRecord second = version("v1", "<flow id=\"second\"/>");

        List<Object> outcomes = concurrently(() -> firstRepository.save(first), () -> secondRepository.save(second));

        assertThat(outcomes).filteredOn(ProcessVersionRecord.class::isInstance).hasSize(1);
        assertThat(outcomes)
            .filteredOn(DeploymentException.class::isInstance)
            .singleElement()
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
        assertThat(firstRepository.find(NAMESPACE, CODE, "v1"))
            .get()
            .extracting(ProcessVersionRecord::getArtifactDigest)
            .isIn(first.getArtifactDigest(), second.getArtifactDigest());
    }

    @Test
    void databaseRejectsMutationOfImmutableVersionAndAppendOnlyRolloutEvent() throws Exception {
        new JdbcProcessVersionRepository(dataSource).save(version("v1", "<flow/>"));
        new JdbcRolloutRepository(dataSource, null).create(rollout("immutable-facts"));

        assertThatThrownBy(() -> execute(dataSource,
                "UPDATE cf_process_version SET actor = 'mutated'"
                + " WHERE namespace = 'default' AND code = 'postgres-contract.flow'" + " AND version = 'v1'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("cf_process_version is immutable");
        assertThatThrownBy(() -> execute(dataSource,
                "DELETE FROM cf_process_version" + " WHERE namespace = 'default' AND code = 'postgres-contract.flow'"
                + " AND version = 'v1'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("cf_process_version is immutable");
        assertThatThrownBy(() -> execute(dataSource, "UPDATE cf_rollout_event SET actor = 'mutated'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("cf_rollout_event is immutable");
        assertThatThrownBy(() -> execute(dataSource, "DELETE FROM cf_rollout_event"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("cf_rollout_event is immutable");
    }

    @Test
    void concurrentRolloutsApplyCasAndKeepIdempotentReplayStable() throws Exception {
        new JdbcProcessVersionRepository(dataSource).save(version("v1", "<flow/>"));
        JdbcRolloutRepository firstRepository = new JdbcRolloutRepository(dataSource, null);
        JdbcRolloutRepository secondRepository = new JdbcRolloutRepository(dataSource, null);
        RolloutCreateRequest first = rollout("first-key");
        RolloutCreateRequest second = rollout("second-key");

        List<Object> outcomes = concurrently(() -> firstRepository.create(first), () -> secondRepository.create(second));

        ProcessRollout winner =
                (ProcessRollout) outcomes
            .stream()
            .filter(ProcessRollout.class::isInstance)
            .findFirst()
            .orElseThrow();
        assertThat(outcomes)
            .filteredOn(DeploymentException.class::isInstance)
            .singleElement()
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
        RolloutCreateRequest winningRequest =
                winner.getIdempotencyKey().equals(first.getIdempotencyKey()) ? first : second;
        assertThat(firstRepository.create(winningRequest).getId()).isEqualTo(winner.getId());
        assertThat(new JdbcProcessAliasRepository(dataSource).resolve(NAMESPACE, CODE, ROUTE))
            .get()
            .extracting(alias -> alias.getRevision())
            .isEqualTo(1L);
    }

    @Test
    void competingCanaryMutationAndCreateDoNotDeadlock() throws Exception {
        JdbcProcessVersionRepository versionRepository = new JdbcProcessVersionRepository(dataSource);
        versionRepository.save(version("v1", "<flow id=\"v1\"/>"));
        versionRepository.save(version("v2", "<flow id=\"v2\"/>"));
        versionRepository.save(version("v3", "<flow id=\"v3\"/>"));
        JdbcRolloutRepository firstRepository = new JdbcRolloutRepository(dataSource, null);
        JdbcRolloutRepository secondRepository = new JdbcRolloutRepository(dataSource, null);
        firstRepository.create(deploy("deploy-v1", "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null));
        ProcessRollout canary = firstRepository.create(deploy("canary-v2", "v2", 1L, RolloutStrategy.CANARY, 1_000));

        List<Object> outcomes = concurrently(() -> firstRepository.promote(
                new PromoteRolloutCommand(canary.getId(), canary.getRolloutRevision(), "postgres-contract")), () -> secondRepository.create(
                deploy("deploy-v3", "v3", canary.getAliasRevision(), RolloutStrategy.ALL_AT_ONCE, null)));

        assertThat(outcomes).filteredOn(ProcessRollout.class::isInstance).hasSize(1);
        assertThat(outcomes)
            .filteredOn(DeploymentException.class::isInstance)
            .singleElement()
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isIn(DeploymentErrorCode.ROLLOUT_CONFLICT, DeploymentErrorCode.CONCURRENT_MODIFICATION));
        assertThat(new JdbcProcessAliasRepository(dataSource).resolve(NAMESPACE, CODE, ROUTE))
            .get()
            .extracting(ProcessAliasRecord::getStableVersion)
            .isEqualTo("v2");
    }

    @Test
    void concurrentOutboxClaimsSkipLockedRowsAndMakeParallelProgress() throws Exception {
        JdbcRoutingOutboxRepository firstRepository = new JdbcRoutingOutboxRepository(dataSource);
        JdbcRoutingOutboxRepository secondRepository = new JdbcRoutingOutboxRepository(dataSource);
        for (int index = 0; index < 20; index++) {
            firstRepository.insertPendingStandalone("ALIAS_STATE", NAMESPACE, CODE, ROUTE, "routing.key." + index,
                    "{\"revision\":" + (index + 1) + "}");
        }

        List<Object> outcomes = concurrently(() -> firstRepository.claimPending(10, "worker-a", 30_000L), () -> secondRepository.claimPending(10,
                "worker-b", 30_000L));

        @SuppressWarnings("unchecked")
        List<RoutingOutboxRecord> firstClaim = (List<RoutingOutboxRecord>) outcomes.get(0);
        @SuppressWarnings("unchecked")
        List<RoutingOutboxRecord> secondClaim = (List<RoutingOutboxRecord>) outcomes.get(1);
        assertThat(firstClaim).hasSize(10);
        assertThat(secondClaim).hasSize(10);
        List<RoutingOutboxRecord> combined = new ArrayList<>(firstClaim);
        combined.addAll(secondClaim);
        assertThat(combined).extracting(RoutingOutboxRecord::getId).doesNotHaveDuplicates();
    }

    @Test
    void expiredOutboxClaimIsReclaimedAndFencesThePreviousWorker() throws Exception {
        JdbcRoutingOutboxRepository repository = new JdbcRoutingOutboxRepository(dataSource);
        long id = repository.insertPendingStandalone(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, NAMESPACE, CODE, ROUTE,
                "routing.expired-lease", "{\"revision\":1}");
        RoutingOutboxRecord previous = repository.claimPending(1, "worker-old", 30_000L).get(0);
        expireOutboxLease(id);

        assertThat(repository.countExpiredClaims()).isOne();
        RoutingOutboxRecord current = repository.claimPending(1, "worker-new", 30_000L).get(0);

        assertThat(current.getLeaseToken()).isNotEqualTo(previous.getLeaseToken());
        assertThat(repository.markDelivered(id, previous.getLeaseToken())).isZero();
        assertThat(repository.markFailed(id, previous.getLeaseToken(), "late failure from expired worker", 10, 1_000L))
            .isZero();
        assertThat(readOutboxAttemptCount(id)).isZero();
        assertThat(repository.markDelivered(id, current.getLeaseToken())).isOne();
        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.DELIVERED)).isOne();
        assertThat(repository.countExpiredClaims()).isZero();
    }

    @Test
    void successfulDeliveryBeforeCrashIsReplayedIdempotentlyAfterLeaseExpiry() throws Exception {
        JdbcRoutingOutboxRepository repository = new JdbcRoutingOutboxRepository(dataSource);
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        Duration operationTimeout = Duration.ofSeconds(1);
        ChannelRoutingStateDeliveryTarget target = new ChannelRoutingStateDeliveryTarget(channel, operationTimeout);
        String routingKey = RoutingStateKeys.aliasState("compileflow.test.", NAMESPACE, CODE, ROUTE);
        String payload =
                RoutingStatePayloads.aliasStateJson(NAMESPACE, CODE, ROUTE, "v1", null, null, 1L, "postgres-contract",
                        1L);
        long id = repository.insertPendingStandalone(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, NAMESPACE, CODE, ROUTE,
                routingKey, payload);
        RoutingOutboxRecord interrupted = repository.claimPending(1, "worker-interrupted", 30_000L).get(0);

        target.deliver(interrupted.getRoutingKey(), interrupted.getPayload());
        expireOutboxLease(id);

        RoutingOutboxDispatcher replacement = new RoutingOutboxDispatcher(repository, target,
                new RoutingOutboxDispatchPolicy(1, Duration.ofSeconds(30), 10, Duration.ofSeconds(1),
                        Duration.ofMinutes(1)));
        assertThat(replacement.dispatch().delivered()).isOne();
        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.DELIVERED)).isOne();
        assertThat(RoutingStateParser.parse(channel.read(routingKey, operationTimeout))).satisfies(state -> {
            assertThat(state.getStableVersion()).isEqualTo("v1");
            assertThat(state.getAliasRevision()).isEqualTo(1L);
        });
    }

    @Test
    void deliveredOutboxRetentionIsBoundedAndUsesOrderedIndex() throws Exception {
        JdbcRoutingOutboxRepository repository = new JdbcRoutingOutboxRepository(dataSource);
        for (int index = 0; index < 3; index++) {
            repository.insertPendingStandalone("ALIAS_STATE", NAMESPACE, CODE, ROUTE, "routing.retention.key." + index,
                    "{\"revision\":" + (index + 1) + "}");
        }
        for (RoutingOutboxRecord record : repository.claimPending(3, "retention-worker", 30_000L)) {
            assertThat(repository.markDelivered(record.getId(), record.getLeaseToken())).isOne();
        }
        execute(dataSource,
                "UPDATE cf_routing_outbox SET created_at = 1, updated_at = 1" + " WHERE status = 'DELIVERED'");

        assertThat(repository.deleteDeliveredOlderThan(0L, 1)).isOne();
        assertThat(repository.deleteDeliveredOlderThan(0L, 1)).isOne();
        assertThat(repository.deleteDeliveredOlderThan(0L, 1)).isOne();
        assertThat(repository.deleteDeliveredOlderThan(0L, 1)).isZero();

        assertBoundedRetentionPlan();
    }

    @Test
    void concurrentReconciliationEnsuresCoalesceToOneDelivery() throws Exception {
        JdbcRoutingOutboxRepository firstRepository = new JdbcRoutingOutboxRepository(dataSource);
        JdbcRoutingOutboxRepository secondRepository = new JdbcRoutingOutboxRepository(dataSource);

        List<Object> outcomes = concurrently(() -> firstRepository.ensurePendingStandalone("ALIAS_STATE", NAMESPACE,
                CODE, ROUTE, "routing.reconciliation.key", "{\"revision\":42}"), () -> secondRepository.ensurePendingStandalone("A"
                + "LIAS_STATE", NAMESPACE, CODE, ROUTE, "routing.reconciliation.key", "{\"revision\":42}"));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(firstRepository.countByStatus(RoutingOutboxRecord.Status.PENDING)).isOne();
    }

    private ProcessVersionRecord version(String version, String content) {
        return ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .version(version)
            .modelType(ProcessModelType.TBBPM)
            .processDefinition(ProcessDefinition.inline(CODE, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessModelType.TBBPM,
                    ProcessDefinition.inline(CODE, content), Map.of()))
            .actor("postgres-contract")
            .createdAt(1L)
            .build();
    }

    private RolloutCreateRequest rollout(String idempotencyKey) {
        return deploy(idempotencyKey, "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
    }

    private RolloutCreateRequest deploy(String idempotencyKey, String targetVersion, long expectedRouteRevision,
            RolloutStrategy strategy, Integer canaryWeightBps) {
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, CODE, ROUTE);
        ProcessRef.Version target = ProcessRef.version(NAMESPACE, CODE, targetVersion);
        CreateRolloutCommand command = strategy == RolloutStrategy.CANARY
                ? CreateRolloutCommand.canary(idempotencyKey, alias, target, expectedRouteRevision, canaryWeightBps,
                        "postgres-contract", null)
                : CreateRolloutCommand.allAtOnce(idempotencyKey, alias, target, expectedRouteRevision,
                        "postgres-contract", null);
        return RolloutCreateRequest.deploy(command);
    }

    private List<Object> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstResult = executor.submit(() -> outcome(first, ready, start));
            Future<Object> secondResult = executor.submit(() -> outcome(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object outcome(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return action.call();
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private void expireOutboxLease(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                connection.prepareStatement("UPDATE cf_routing_outbox SET lease_until = 0 WHERE id = ?")) {
            statement.setLong(1, id);
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    private long readOutboxAttemptCount(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                connection.prepareStatement("SELECT attempt_count FROM cf_routing_outbox WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private void assertBoundedRetentionPlan() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement settings = connection.createStatement()) {
                settings.execute("SET LOCAL enable_seqscan = off");
                settings.execute("SET LOCAL enable_bitmapscan = off");
            }
            StringBuilder plan = new StringBuilder();
            try (PreparedStatement statement = connection.prepareStatement(
                    "EXPLAIN (FORMAT TEXT, COSTS OFF) " + JdbcRoutingOutboxRepository.deleteDeliveredOlderThanSql())) {
                statement.setLong(1, Long.MAX_VALUE);
                statement.setInt(2, 10);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        plan.append(rows.getString(1)).append('\n');
                    }
                }
            } finally {
                connection.rollback();
            }
            assertThat(plan.toString()).contains("idx_outbox_delivered_retention").doesNotContain("Sort");
        }
    }
}
