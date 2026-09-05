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
package com.alibaba.compileflow.engine.test.feature.deployment.control;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.AssembledProcessEngineFactory;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.DeterministicAliasSelector;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentOperationMetrics;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.DefaultProcessDeploymentService;
import com.alibaba.compileflow.deploy.control.RolloutControlService;
import com.alibaba.compileflow.deploy.control.VersionPublicationService;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidation;
import com.alibaba.compileflow.deploy.control.projection.ArtifactProjectionCoordinator;
import com.alibaba.compileflow.deploy.control.routing.ChannelRoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatchPolicy;
import com.alibaba.compileflow.deploy.control.routing.RoutingOutboxDispatcher;
import com.alibaba.compileflow.deploy.control.projection.RoutingProjectionReconciler;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcRolloutRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcRoutingOutboxRepository;
import com.alibaba.compileflow.deploy.control.repository.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.control.test.H2TestDatabase;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.test.support.helpers.Awaiter;
import com.alibaba.compileflow.engine.test.support.helpers.DeployRuntimeTestSupport;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.postgresql.ds.PGSimpleDataSource;

@Tag("integration")
@Tag("deploy")
@Execution(ExecutionMode.SAME_THREAD)
class DeploymentRuntimeChainIntegrationTest {
    private static final String NAMESPACE = "default";
    private static final String ROUTE = "prod";
    private static final String PREFIX = "compileflow.test.jdbc.multinode.";
    private static final String POSTGRES_REQUIRED_PROPERTY = "compileflow.test.deploy.postgres.required";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private TestDatabase database;
    private DataSource dataSource;
    private JdbcProcessVersionRepository versionRepository;
    private JdbcProcessAliasRepository aliasRepository;
    private JdbcRoutingOutboxRepository outboxRepository;
    private InMemoryDeploymentSyncChannel channel;
    private ProcessDeploymentService deploymentService;
    private RoutingOutboxDispatcher dispatcher;
    private ProcessEngine publicationValidationEngine;
    private RuntimeNode nodeA;
    private RuntimeNode nodeB;

    private static PGSimpleDataSource createPostgresDataSource(String url, String username, String password,
            String currentSchema) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(username);
        source.setPassword(password);
        if (currentSchema != null) {
            source.setCurrentSchema(currentSchema);
        }
        return source;
    }

    private static void executeSql(DataSource source, String sql) throws SQLException {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the PostgreSQL runtime-chain contract");
        }
        return value;
    }

    private static String findUserIdForTarget(String code, ProcessAliasTarget expectedTarget) {
        for (int index = 0; index < 200_000; index++) {
            String candidate = "jdbc_multinode_user_" + index;
            ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, code, ROUTE);
            ProcessAliasRoute route = ProcessAliasRoute.canary(alias, "v1", "v2", 1_000, 1L);
            if (DeterministicAliasSelector.select(route, candidate) == expectedTarget) {
                return candidate;
            }
        }
        throw new IllegalStateException("No userId found for routing target " + expectedTarget);
    }

    private static String tbbpmMarkerFlow(String code, String marker) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <var name=\"version_marker\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"calc\"/>\n"
                + "    </start>\n" + "    <scriptTask id=\"calc\" name=\"Calc\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"java\">\n"
                + "            <output target=\"version_marker\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[return \"" + marker + "\";]]></code>\n"
                + "        </action><transition to=\"end\"/>\n" + "    </scriptTask>\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";
    }

    private static String tbbpmChildFlow(String parentCode, String childCode, String childVersion) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="%s">
                <var name="version_marker" dataType="java.lang.String"
                     inOutType="return"/>
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="child"/>
                </start>
                <bpmCall id="child" code="%s" version="%s" g="150,40,88,48">
                    <output source="version_marker" target="version_marker"/>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" name="End" g="300,50,32,32"/>
            </bpm>
            """
            .formatted(parentCode, parentCode, childCode, childVersion);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Setup rollback must preserve the original initialization failure.
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        database = TestDatabase.open();
        dataSource = database.dataSource();
        versionRepository = new JdbcProcessVersionRepository(dataSource);
        aliasRepository = new JdbcProcessAliasRepository(dataSource);
        outboxRepository = new JdbcRoutingOutboxRepository(dataSource);
        channel = new InMemoryDeploymentSyncChannel();
        publicationValidationEngine = ProcessEngineTestFactory.createTbbpm();
        deploymentService = createJdbcAdminService();
        dispatcher = createDispatcher();
    }

    @AfterEach
    void tearDown() throws SQLException {
        closeQuietly(nodeA);
        closeQuietly(nodeB);
        closeQuietly(publicationValidationEngine);
        if (database != null) {
            database.close();
        }
    }

    @Test
    void transactionalOutboxConvergesTwoNodesAcrossCanaryPromotionAndRollback() {
        String code = "jdbc.multinode.release";
        publish(code, "v1");
        publish(code, "v2");
        assertThat(versionRepository.find(NAMESPACE, code, "v2"))
            .map(record -> record.getModelType())
            .contains(ProcessModelType.TBBPM);

        nodeA = RuntimeNode.start("node-a", channel, versionRepository, code);
        nodeB = RuntimeNode.start("node-b", channel, versionRepository, code);

        ProcessRollout initial = createRollout("initial-v1", code, "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        dispatchPending();
        awaitNodesRoutedAndOnlyDeployed(code, "v1");
        executeOnAllNodes(code, "initial-user", "v1");

        ProcessRollout canary =
                createRollout("canary-v2", code, "v2", initial.getAliasRevision(), RolloutStrategy.CANARY, 1_000);
        dispatchPending();
        awaitNodesDeployed(code, "v1", "v2");
        executeOnAllNodes(code, findUserIdForTarget(code, ProcessAliasTarget.STABLE), "v1");
        executeOnAllNodes(code, findUserIdForTarget(code, ProcessAliasTarget.CANDIDATE), "v2");

        ProcessRollout promoted = deploymentService.promoteRollout(
                new PromoteRolloutCommand(canary.getId(), canary.getRolloutRevision(), "tester"));
        dispatchPending();
        awaitNodesRoutedAndOnlyDeployed(code, "v2");
        executeOnAllNodes(code, "post-promote-user", "v2");

        ProcessRollout rollback = deploymentService.rollbackRollout(
                new RollbackRolloutCommand(promoted.getId(), "rollback-v1", promoted.getAliasRevision(), "tester"));
        assertThat(rollback.getOperationKind()).isEqualTo(RolloutOperationKind.ROLLBACK);
        assertThat(rollback.getTargetVersion()).isEqualTo(ProcessRef.version(NAMESPACE, code, "v1"));
        dispatchPending();
        awaitNodesRoutedAndOnlyDeployed(code, "v1");
        executeOnAllNodes(code, "post-rollback-user", "v1");
    }

    @Test
    void reconcilerRepublishesTheAuthoritativeAliasForLateNodes() {
        String code = "jdbc.reconcile.late";
        publish(code, "v1");
        createRollout("initial-v1", code, "v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        dispatchPending();

        channel = new InMemoryDeploymentSyncChannel();
        dispatcher = createDispatcher();
        RoutingProjectionReconciler reconciler = new RoutingProjectionReconciler(aliasRepository, versionRepository,
                ArtifactProjectionCoordinator.database(), channel, outboxRepository, PREFIX, true, Duration.ofSeconds(2));

        RoutingProjectionReconciler.ReconciliationResult repair = reconciler.reconcile();

        assertThat(repair.getMismatchesFound()).isEqualTo(1);
        assertThat(repair.getAutoFixedCount()).isEqualTo(1);
        dispatchPending();

        nodeA = RuntimeNode.start("late-node-a", channel, versionRepository, code);
        nodeB = RuntimeNode.start("late-node-b", channel, versionRepository, code);
        awaitNodesRoutedAndOnlyDeployed(code, "v1");
        executeOnAllNodes(code, "late-user", "v1");
    }

    @Test
    void processCallRemainsBoundToThePublishedChildVersionOnBothNodes() {
        String parentCode = "jdbc.dependency.parent";
        String childCode = "jdbc.dependency.child";
        publish(childCode, "child-v1");
        publish(childCode, "child-v2");
        publishDefinition(parentCode, "parent-v1", tbbpmChildFlow(parentCode, childCode, "child-v1"));

        nodeA = RuntimeNode.start("dependency-node-a", channel, versionRepository, parentCode, childCode);
        nodeB = RuntimeNode.start("dependency-node-b", channel, versionRepository, parentCode, childCode);
        ProcessRollout childRollout =
                createRollout("child-initial", childCode, "child-v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        createRollout("parent-initial", parentCode, "parent-v1", 0L, RolloutStrategy.ALL_AT_ONCE, null);
        dispatchPending();

        awaitNodesRoutedAndOnlyDeployed(parentCode, "parent-v1");
        awaitNodesRoutedAndOnlyDeployed(childCode, "child-v1");
        executeOnAllNodes(parentCode, "dependency-user", "child-v1");

        createRollout("child-v2", childCode, "child-v2", childRollout.getAliasRevision(), RolloutStrategy.ALL_AT_ONCE,
                null);
        dispatchPending();
        awaitNodesDeployed(childCode, "child-v1", "child-v2");
        Awaiter.await("both runtime nodes route to child-v2", WAIT_TIMEOUT, POLL_INTERVAL, () -> nodeA.isRoutedTo(childCode,
                        "child-v2") && nodeB.isRoutedTo(childCode, "child-v2"), () -> dumpNodes(childCode));
        executeOnAllNodes(parentCode, "dependency-user", "child-v1");
    }

    private void publish(String code, String version) {
        publishDefinition(code, version, tbbpmMarkerFlow(code, version));
    }

    private void publishDefinition(String code, String version, String definition) {
        deploymentService.publish(
                new PublishProcessVersionCommand(ProcessRef.version(NAMESPACE, code, version), ProcessModelType.TBBPM,
                        ProcessDefinition.inline(code, definition), "tester", Collections.emptyMap()));
    }

    private ProcessRollout createRollout(String idempotencyKey, String code, String version, long expectedRouteRevision,
            RolloutStrategy strategy, Integer canaryWeightBps) {
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, code, ROUTE);
        ProcessRef.Version target = ProcessRef.version(NAMESPACE, code, version);
        CreateRolloutCommand command = strategy == RolloutStrategy.CANARY
                ? CreateRolloutCommand.canary(idempotencyKey, alias, target, expectedRouteRevision, canaryWeightBps,
                        "tester", null)
                : CreateRolloutCommand.allAtOnce(idempotencyKey, alias, target, expectedRouteRevision, "tester", null);
        return deploymentService.createRollout(command);
    }

    private ProcessDeploymentService createJdbcAdminService() {
        ProcessDeploymentOperationMetrics metrics = new ProcessDeploymentOperationMetrics();
        ArtifactProjectionCoordinator coordinator = ArtifactProjectionCoordinator.database();
        VersionPublicationService publication =
                new VersionPublicationService(versionRepository, this::validatePublicationDefinition, 1_048_576);
        RolloutControlService rollouts = new RolloutControlService(new JdbcRolloutRepository(dataSource, PREFIX),
                versionRepository, coordinator, metrics);
        return new DefaultProcessDeploymentService(publication, coordinator, metrics, rollouts, versionRepository, aliasRepository, ignored -> {});
    }

    private ProcessPublicationValidation validatePublicationDefinition(ProcessRef.Version ref,
            ProcessModelType modelType, ProcessDefinition.Inline definition) {
        if (modelType != ProcessModelType.TBBPM) {
            throw new IllegalArgumentException("This test control plane supports only TBBPM definitions");
        }
        ProcessPreflightReport report =
                publicationValidationEngine.tooling().preflight(definition, ProcessPreflightOptions.fast());
        ProcessCallInspector inspection = (ProcessCallInspector) publicationValidationEngine.tooling();
        List<ProcessCallBinding> bindings = inspection
            .inspectProcessCalls(definition)
            .stream()
            .map(call -> new ProcessCallBinding(call.callSiteId(),
                    ProcessRef.version(ref.namespace(), call.code(),
                            ((ProcessCallTarget.Version) call.target()).version())))
            .toList();
        return new ProcessPublicationValidation(report, bindings);
    }

    private void dispatchPending() {
        assertThat(outboxRepository.countByStatus(RoutingOutboxRecord.Status.PENDING)).isGreaterThan(0);
        assertThat(dispatcher.dispatch().delivered()).isGreaterThan(0);
    }

    private RoutingOutboxDispatcher createDispatcher() {
        Duration operationTimeout = Duration.ofSeconds(5);
        return new RoutingOutboxDispatcher(outboxRepository,
                new ChannelRoutingStateDeliveryTarget(channel, operationTimeout),
                new RoutingOutboxDispatchPolicy(50, Duration.ofMinutes(1), 10, Duration.ofSeconds(5),
                        Duration.ofMinutes(5)));
    }

    private void awaitNodesRoutedAndOnlyDeployed(String code, String version) {
        Awaiter.await("both runtime nodes route only to " + version, WAIT_TIMEOUT, POLL_INTERVAL, () -> nodeA.isRoutedAndOnlyDeployed(code,
                        version) && nodeB.isRoutedAndOnlyDeployed(code, version), () -> dumpNodes(code));
    }

    private void awaitNodesDeployed(String code, String... versions) {
        Awaiter.await("both runtime nodes deploy " + Arrays.toString(versions), WAIT_TIMEOUT, POLL_INTERVAL, () -> nodeA.hasDeployed(code,
                        versions) && nodeB.hasDeployed(code, versions), () -> dumpNodes(code));
    }

    private void executeOnAllNodes(String code, String userId, String expectedVersion) {
        nodeA.executeAndAssert(code, userId, expectedVersion);
        nodeB.executeAndAssert(code, userId, expectedVersion);
    }

    private String dumpNodes(String code) {
        return nodeA.dump(code) + "\n" + nodeB.dump(code);
    }

    private interface TestDatabase {
        static TestDatabase open() throws SQLException {
            if (Boolean.getBoolean(POSTGRES_REQUIRED_PROPERTY)) {
                return new PostgreSqlTestDatabase();
            }
            return new H2Database();
        }

        DataSource dataSource();

        default void close() throws SQLException {}
    }

    private static final class H2Database implements TestDatabase {
        private final DataSource dataSource;

        private H2Database() {
            dataSource = H2TestDatabase.createInMemoryDataSource(
                    "runtime_chain_" + UUID.randomUUID().toString().replace("-", ""));
        }

        @Override
        public DataSource dataSource() {
            return dataSource;
        }
    }

    private static final class PostgreSqlTestDatabase implements TestDatabase {
        private final String schema;
        private final PGSimpleDataSource adminDataSource;
        private final PGSimpleDataSource dataSource;

        private PostgreSqlTestDatabase() throws SQLException {
            String url = requireEnvironment("SPRING_DATASOURCE_URL");
            if (!url.startsWith("jdbc:postgresql:")) {
                throw new IllegalStateException("SPRING_DATASOURCE_URL must select PostgreSQL");
            }
            String username = requireEnvironment("SPRING_DATASOURCE_USERNAME");
            String password = requireEnvironment("SPRING_DATASOURCE_PASSWORD");
            schema = "cf_runtime_chain_" + UUID.randomUUID().toString().replace("-", "");
            adminDataSource = createPostgresDataSource(url, username, password, null);
            dataSource = createPostgresDataSource(url, username, password, schema);
            executeSql(adminDataSource, "CREATE SCHEMA " + schema);
            try {
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .locations("classpath:db/compileflow-deploy/migration")
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            } catch (RuntimeException failure) {
                try {
                    close();
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public DataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() throws SQLException {
            executeSql(adminDataSource, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static final class RuntimeNode implements AutoCloseable {
        private final String name;
        private final ProcessEngine engine;
        private final LocalRoutingState localRoutingState;
        private final AliasAdmission aliasAdmission;
        private final ExecutorService executor;
        private final DeployRuntime runtime;

        private RuntimeNode(String name, ProcessEngine engine, LocalRoutingState localRoutingState,
                AliasAdmission aliasAdmission, ExecutorService executor, DeployRuntime runtime) {
            this.name = name;
            this.engine = engine;
            this.localRoutingState = localRoutingState;
            this.aliasAdmission = aliasAdmission;
            this.executor = executor;
            this.runtime = runtime;
        }

        private static RuntimeNode start(String name, InMemoryDeploymentSyncChannel channel,
                JdbcProcessVersionRepository versionRepository, String... codes) {
            LocalRoutingState localRoutingState = new LocalRoutingState();
            AliasAdmission aliasAdmission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState());
            ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmConfig();
            ProcessEngine engine = AssembledProcessEngineFactory.create(config,
                    EngineAssembly.assemble(config, localRoutingState, aliasAdmission));
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "test-jdbc-runtime-" + name);
                thread.setDaemon(true);
                return thread;
            });
            List<String> keys =
                    Arrays
                .stream(codes)
                .map(code -> RoutingStateKeys.aliasState(PREFIX, NAMESPACE, code, ROUTE))
                .toList();
            DeployRuntime runtime = DeployRuntimeTestSupport.dbRuntime(channel, keys, engine, ProcessModelType.TBBPM,
                    versionRepository, Duration.ofSeconds(2), executor, localRoutingState);
            runtime.start();
            return new RuntimeNode(name, engine, localRoutingState, aliasAdmission, executor, runtime);
        }

        private boolean isRoutedAndOnlyDeployed(String code, String version) {
            return version.equals(route(code))
                    && localRoutingState
                        .getInstalledVersionState()
                        .list(NAMESPACE, code)
                        .map(versions -> versions.size() == 1 && versions.contains(version))
                        .orElse(false);
        }

        private boolean hasDeployed(String code, String... versions) {
            for (String version : versions) {
                if (!localRoutingState.getInstalledVersionState().contains(NAMESPACE, code, version)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isRoutedTo(String code, String version) {
            return version.equals(route(code));
        }

        private void executeAndAssert(String code, String userId, String expectedVersion) {
            ProcessExecutionOptions options =
                    ProcessExecutionOptions.builder().aliasRouting(new AliasRoutingOptions(userId)).build();
            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.alias(NAMESPACE, code, ROUTE), Map.of(), options);
            assertThat(result.isSuccess()).as(name + " execution should succeed").isTrue();
            assertThat(result.getOutput()).isNotNull();
            assertThat(result.getOutput().get("version_marker")).as(name + " marker").isEqualTo(expectedVersion);
        }

        private String route(String code) {
            try {
                return aliasAdmission
                    .admit(ProcessRef.alias(NAMESPACE, code, ROUTE), new AliasRoutingOptions("probe"))
                    .version()
                    .version();
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private String dump(String code) {
            return name + " route=" + route(code) + " deployed=" + localRoutingState.getInstalledVersionState();
        }

        @Override
        public void close() {
            closeQuietly(runtime);
            closeQuietly(engine);
            executor.shutdownNow();
            localRoutingState.clear();
        }
    }
}
