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
package com.alibaba.compileflow.workbench.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
    @Test
    void h2DeployAdapterRejectsNullableStatesRejectedByProductionV1() throws Exception {
        String url = "jdbc:h2:mem:deploy-null-checks-" + UUID.randomUUID() + ";MODE=PostgreSQL";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM 'classpath:db/compileflow-deploy-h2/schema.sql'");
            for (String version : new String[] {"v1", "v2"}) {
                statement.executeUpdate(
                        "INSERT INTO cf_process_version "
                        + "(namespace, code, version, model_type, content, digest, actor, created_at) VALUES "
                        + "('default', 'checks.flow', '" + version + "', 'TBBPM', '<bpm/>', '" + "a".repeat(64)
                        + "', 'test', 1)");
            }
            String alias =
                    """
                INSERT INTO cf_process_alias
                    (namespace, code, alias, stable_version, candidate_version, candidate_weight_bps,
                     revision, created_by, updated_by, created_at, updated_at)
                VALUES ('default', 'checks.flow', 'production', 'v1', 'v2', NULL, 1, 'test', 'test', 1, 1)
                """;
            String rollout = """
                INSERT INTO cf_rollout
                    (id, namespace, code, alias, baseline_version, target_version, base_alias_revision,
                     alias_revision, strategy, phase, canary_weight_bps, revision, active_marker,
                     idempotency_key, operation_kind, request_fingerprint, created_by, created_at, updated_at)
                VALUES ('rollout', 'default', 'checks.flow', 'production', 'v1', 'v2', 1, 2,
                        'CANARY', 'IN_PROGRESS', 1000, 1, NULL, 'key', 'DEPLOY', '%s', 'test', 1, 1)
                """
                .formatted("a".repeat(64));
            assertThatThrownBy(() -> statement.executeUpdate(alias))
                .isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(failure.getMessage().toLowerCase(Locale.ROOT))
                    .contains("ck_alias_candidate"));
            assertThatThrownBy(() -> statement.executeUpdate(rollout))
                .isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(failure.getMessage().toLowerCase(Locale.ROOT))
                    .contains("ck_rollout_active_marker"));
            statement.executeUpdate(rollout.replace("1000, 1, NULL", "1000, 1, 1"));
            assertThatThrownBy(() -> statement.executeUpdate(
                    """
                INSERT INTO cf_rollout_event
                    (rollout_id, sequence, event_type, from_phase, to_phase, actor, created_at)
                VALUES ('rollout', 2, 'CANARY_UPDATED', NULL, 'IN_PROGRESS', 'test', 2)
                """))
                .isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(failure.getMessage().toLowerCase(Locale.ROOT))
                    .contains("ck_rollout_event_transition"));
        }
    }

    private static final Set<String> APPLICATION_TABLES = Set.of("CF_PROCESS_DRAFT", "CF_PROCESS_VERSION",
            "CF_PROCESS_ALIAS", "CF_ROLLOUT", "CF_ROLLOUT_EVENT", "CF_EXECUTION_LOG", "CF_ROUTING_OUTBOX",
            "CF_ASYNC_INVOCATION", "CF_ASYNC_INVOCATION_ATTEMPT");

    private static void assertInvalidRouteAttributionIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_execution_log "
                + "(id, process_code, status, duration_ms, invocation_id, trace_id, namespace, "
                + "model_type, route_alias, logged_at, call_depth) VALUES "
                + "('partial-route', 'order.flow', 'success', 1, 'inv-1', 'trace-1', "
                + "'default', 'TBBPM', 'production', 1, 0)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidExecutionOutcomeIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_execution_log "
                + "(id, process_code, status, duration_ms, invocation_id, trace_id, namespace, "
                + "model_type, logged_at, call_depth) VALUES "
                + "('missing-error', 'order.flow', 'failed', 1, 'inv-1', 'trace-1', " + "'default', 'TBBPM', 1, 0)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidSourceDigestIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_execution_log "
                + "(id, process_code, status, duration_ms, invocation_id, trace_id, namespace, "
                + "model_type, source_digest, logged_at, call_depth) VALUES "
                + "('bad-digest', 'order.flow', 'success', 1, 'inv-1', 'trace-1', "
                + "'default', 'TBBPM', 'not-a-sha-256', 1, 0)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidOutboxStateIsRejected(Connection connection) {
        String sql = "INSERT INTO cf_routing_outbox "
                + "(event_type, namespace, code, routing_key, payload, status, attempt_count, "
                + "created_at, updated_at, delivery_key, alias) VALUES "
                + "('ALIAS_STATE', 'default', 'order.flow', 'routing.key', '{}', " + "'PROCESSING', 0, 1, 1, '"
                + "a".repeat(64) + "', 'production')";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidAsyncInvocationStatusIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, "
                + "available_at, params_json, routing_json, created_at, updated_at) VALUES "
                + "('invalid-status', 'order.flow', 'unknown', 0, 0, 0, 1, 0, 1, " + "'{}', '{}', 1, 1)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertRunningAsyncInvocationWithoutLeaseIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, "
                + "available_at, params_json, routing_json, created_at, updated_at, started_at) VALUES "
                + "('missing-lease', 'order.flow', 'running', 1, 1, 0, 3, 0, 1, " + "'{}', '{}', 1, 1, 1)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidAsyncInvocationLifecycleIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, available_at, params_json, routing_json, "
                + "created_at, updated_at, completed_at) VALUES "
                + "('zero-attempt-success', 'order.flow', 'succeeded', 0, 0, 0, 1, 0, 1, " + "'{}', '{}', 1, 1, 1)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidAsyncInvocationTerminalErrorIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, available_at, params_json, routing_json, "
                + "created_at, updated_at, started_at, completed_at) VALUES "
                + "('errorless-dead-letter', 'order.flow', 'dead_letter', 1, 1, 0, 1, 0, 1, "
                + "'{}', '{}', 1, 2, 1, 2)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidAsyncInvocationTimelineIsRejected(Connection connection) {
        String sql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, available_at, params_json, routing_json, "
                + "created_at, updated_at) VALUES "
                + "('premature-availability', 'order.flow', 'queued', 0, 0, 0, 1, 0, 1, " + "'{}', '{}', 2, 2)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static void assertInvalidAsyncAttemptStateIsRejected(Connection connection) {
        String invocationSql =
                "INSERT INTO cf_async_invocation "
                + "(invocation_id, process_code, status, attempts, total_attempts, redrive_count, "
                + "max_attempts, retry_delay_ms, available_at, params_json, routing_json, "
                + "created_at, updated_at) VALUES " + "('attempt-parent', 'order.flow', 'queued', 0, 0, 0, 1, 0, 1, "
                + "'{}', '{}', 1, 1)";
        String attemptSql =
                "INSERT INTO cf_async_invocation_attempt "
                + "(attempt_id, invocation_id, sequence_no, redrive_count, attempt_number, "
                + "worker_id, lease_token, outcome, disposition, started_at) VALUES "
                + "('invalid-attempt', 'attempt-parent', 1, 0, 1, 'worker-1', 'lease-1', "
                + "'running', 'succeeded', 1)";
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(invocationSql);
                statement.executeUpdate(attemptSql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private static Set<String> readTableNames(DatabaseMetaData metadata) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (rows.next()) {
                names.add(normalize(rows.getString("TABLE_NAME")));
            }
        }
        return names;
    }

    private static void assertTerminalAsyncAttemptsRequireDisposition(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                INSERT INTO cf_async_invocation
                    (invocation_id, process_code, status, attempts, total_attempts, redrive_count,
                     max_attempts, retry_delay_ms, available_at, params_json, routing_json, created_at, updated_at)
                VALUES ('terminal-parent', 'order.flow', 'queued', 0, 0, 0, 1, 0, 1, '{}', '{}', 1, 1)
                """);
        }
        for (String outcome : new String[] {"succeeded", "failed", "lease_expired"}) {
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO cf_async_invocation_attempt
                        (attempt_id, invocation_id, sequence_no, redrive_count, attempt_number, worker_id,
                         lease_token, outcome, disposition, trace_id, duration_ms, started_at, finished_at,
                         error_code, error_message)
                    VALUES (?, 'terminal-parent', 1, 0, 1, 'worker', ?, ?, NULL, 'trace', 1, 1, 2, ?, ?)
                    """)) {
                statement.setString(1, outcome);
                statement.setString(2, outcome);
                statement.setString(3, outcome);
                statement.setString(4, "succeeded".equals(outcome) ? null : "E");
                statement.setString(5, "succeeded".equals(outcome) ? null : "Failed");
                assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .satisfies(failure -> assertThat(failure.getMessage().toLowerCase(Locale.ROOT))
                        .contains("ck_async_attempt_state"));
            }
        }
    }

    private static Set<String> readIndexNames(DatabaseMetaData metadata, String tableName) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                if (name != null) {
                    names.add(normalize(name));
                }
            }
        }
        return names;
    }

    private static Set<String> readColumnNames(DatabaseMetaData metadata, String tableName) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getColumns(null, null, tableName, null)) {
            while (rows.next()) {
                names.add(normalize(rows.getString("COLUMN_NAME")));
            }
        }
        return names;
    }

    private static String normalize(String identifier) {
        return identifier.toUpperCase(Locale.ROOT);
    }

    @Test
    void baselineCreatesTheCompleteApplicationSchema() throws SQLException {
        String url = "jdbc:h2:mem:compileflow-migration-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway deployFlyway = Flyway
            .configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/compileflow-deploy-h2/migration")
            .table("cf_deploy_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load();
        Flyway workbenchFlyway = Flyway
            .configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/compileflow-workbench-server/postgres/migration")
            .table("cf_workbench_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load();

        MigrateResult deployMigration = deployFlyway.migrate();
        MigrateResult workbenchMigration = workbenchFlyway.migrate();
        assertThat(deployMigration.success).isTrue();
        assertThat(deployMigration.migrationsExecuted).isOne();
        assertThat(workbenchMigration.success).isTrue();
        assertThat(workbenchMigration.migrationsExecuted).isOne();
        assertThat(deployFlyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(workbenchFlyway.info().current().getVersion().getVersion()).isEqualTo("1");
        deployFlyway.validate();
        workbenchFlyway.validate();

        try (Connection connection = workbenchFlyway.getConfiguration().getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(readTableNames(metadata)).containsAll(APPLICATION_TABLES);
            assertThat(readTableNames(metadata))
                .doesNotContain("CF_DEPLOYMENT", "CF_SERVER_FLOW_VERSION", "CF_PROCESS_DRAFT_VERSION",
                        "CF_PROCESS_DRAFT_ALIAS");
            assertThat(readColumnNames(metadata, "CF_ASYNC_INVOCATION"))
                .contains("AVAILABLE_AT", "LEASE_TOKEN", "LEASE_UNTIL", "TOTAL_ATTEMPTS", "REDRIVE_COUNT", "ERROR_CODE")
                .doesNotContain("LOCK_OWNER");
            assertThat(readColumnNames(metadata, "CF_ASYNC_INVOCATION_ATTEMPT"))
                .contains("ATTEMPT_ID", "INVOCATION_ID", "SEQUENCE_NO", "REDRIVE_COUNT", "ATTEMPT_NUMBER", "OUTCOME",
                        "DISPOSITION");
            assertThat(readColumnNames(metadata, "CF_ROUTING_OUTBOX"))
                .contains("STATUS", "NEXT_ATTEMPT_AT", "LEASE_TOKEN", "LEASE_UNTIL");
            assertThat(readColumnNames(metadata, "CF_EXECUTION_LOG"))
                .contains("ERROR_CODE", "NAMESPACE", "EFFECTIVE_VERSION", "ROUTE_ALIAS", "ROUTE_REVISION");
            assertThat(readColumnNames(metadata, "CF_PROCESS_DRAFT"))
                .contains("TAGS_JSON", "REVISION")
                .doesNotContain("TAGS_CSV", "STATUS", "VERSION");
            assertThat(readIndexNames(metadata, "CF_ASYNC_INVOCATION"))
                .contains("IDX_ASYNC_INVOCATION_CREATED", "IDX_ASYNC_INVOCATION_STATUS_AVAILABLE",
                        "IDX_ASYNC_INVOCATION_STATUS_LEASE", "IDX_ASYNC_INVOCATION_PROCESS_CREATED",
                        "IDX_ASYNC_INVOCATION_PROCESS_STATUS_CREATED");
            assertThat(readIndexNames(metadata, "CF_ASYNC_INVOCATION_ATTEMPT"))
                .contains("UQ_ASYNC_ATTEMPT_INVOCATION_SEQUENCE", "UQ_ASYNC_ATTEMPT_REDRIVE_NUMBER",
                        "UQ_ASYNC_ATTEMPT_LEASE_TOKEN", "IDX_ASYNC_ATTEMPT_OUTCOME_FINISHED");
            assertThat(readIndexNames(metadata, "CF_ROUTING_OUTBOX"))
                .contains("IDX_OUTBOX_STATUS_NEXT", "IDX_OUTBOX_STATUS_LEASE", "IDX_OUTBOX_PROCESS");
            assertThat(readIndexNames(metadata, "CF_EXECUTION_LOG"))
                .contains("IDX_EXEC_LOG_TS", "IDX_EXEC_LOG_EFFECTIVE_VERSION", "IDX_EXEC_LOG_PROCESS_TS",
                        "IDX_EXEC_LOG_ROUTE_TS");
            assertInvalidRouteAttributionIsRejected(connection);
            assertInvalidExecutionOutcomeIsRejected(connection);
            assertInvalidSourceDigestIsRejected(connection);
            assertInvalidAsyncInvocationStatusIsRejected(connection);
            assertRunningAsyncInvocationWithoutLeaseIsRejected(connection);
            assertInvalidAsyncInvocationLifecycleIsRejected(connection);
            assertInvalidAsyncInvocationTerminalErrorIsRejected(connection);
            assertInvalidAsyncInvocationTimelineIsRejected(connection);
            assertInvalidAsyncAttemptStateIsRejected(connection);
            assertTerminalAsyncAttemptsRequireDisposition(connection);
            assertInvalidOutboxStateIsRejected(connection);
        }

        assertThat(deployFlyway.migrate().migrationsExecuted).isZero();
        assertThat(workbenchFlyway.migrate().migrationsExecuted).isZero();
    }
}
