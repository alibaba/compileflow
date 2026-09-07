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
package com.alibaba.compileflow.durable.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.testkit.DurableStoreContract;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Destructive contract suite for an explicitly supplied disposable local MySQL database.
 */
@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_MYSQL_URL", matches = ".+")
class LocalMySqlDurableStoreContractTest extends DurableStoreContract {
    private DataSource dataSource;

    @Override
    protected DurableStore createEmptyStore() {
        String url = System.getenv("COMPILEFLOW_DURABLE_MYSQL_URL");
        String user = System.getenv().getOrDefault("COMPILEFLOW_DURABLE_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("COMPILEFLOW_DURABLE_MYSQL_PASSWORD", "");
        DataSource delegate = new DriverManagerDataSource(url, user, password);
        DataSource migration = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("COMPILEFLOW_DURABLE_MYSQL_MIGRATION_USER", user),
                System.getenv().getOrDefault("COMPILEFLOW_DURABLE_MYSQL_MIGRATION_PASSWORD", password));
        // Deliberately disagree with the Store's UTC representation on every borrow.
        dataSource = new NonUtcDataSource(delegate);
        Flyway flyway = Flyway
            .configure()
            .dataSource(migration)
            .locations("classpath:db/compileflow-durable/mysql/migration")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/compileflow-durable/mysql/migration")
            .load()
            .validate();
        return new MySqlDurableStore(dataSource);
    }

    @Test
    void authorityTimesMatchTheDatabaseUtcClockDespiteNonUtcSessions() throws Exception {
        Instant before = databaseUtcNow();
        ProcessRun run = startTimer(before.minusSeconds(60));
        Instant after = databaseUtcNow();
        assertThat(run.createdAt()).isBetween(before, after);
        assertThat(run.availableAt()).isBetween(before, after);
        assertThat(store().resolveDueWaits(10)).isOne();
    }

    @Test
    void schemaRejectsResolvedOccurrenceWithoutResolutionKind() throws Exception {
        ProcessRun run = startTimer(databaseUtcNow());
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolved_at = UTC_TIMESTAMP(3), result_envelope = ?
                 WHERE run_id = ?
                """)) {
            update.setBytes(1, envelope().bytes());
            update.setString(2, run.runId().value());
            assertThatThrownBy(update::executeUpdate)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cf_durable_wait_result");
        }
    }

    @Test
    void schemaRejectsTimerResolutionBeforeScheduling() throws Exception {
        ProcessRun run = startTimer(databaseUtcNow());
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolution_kind = 'FIRED', result_envelope = ?,
                       due_at = TIMESTAMPADD(SECOND, -60, created_at),
                       resolved_at = TIMESTAMPADD(SECOND, -1, created_at)
                 WHERE run_id = ?
                """)) {
            update.setBytes(1, envelope().bytes());
            update.setString(2, run.runId().value());
            assertThatThrownBy(update::executeUpdate)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cf_durable_wait_lifetime");
        }
    }

    private Instant databaseUtcNow() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT UTC_TIMESTAMP(3)")) {
            assertThat(rows.next()).isTrue();
            return rows.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant();
        }
    }

    private ProcessRun startTimer(Instant dueAt) {
        UUID processId = UUID.randomUUID();
        byte[] definition = "<bpm code=\"mysql-time\"/>".getBytes(StandardCharsets.UTF_8);
        store()
            .registerProcess(
                    new DurableStore.ProcessRegistration(processId, "mysql-time", ProcessModelType.TBBPM, definition,
                            ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, "mysql-time", definition)));
        ProcessRun run = store()
            .start(
                    new DurableStore.NewRun(ProcessRunId.random(),
                            new DurableStore.RunProcess(processId, "test", "mysql-time", null), Set.of(processId),
                            envelope(), null));
        var claim = store()
            .claimRun(new DurableStore.RunClaimRequest("worker", Set.of(processId), Duration.ofSeconds(30)))
            .orElseThrow();
        assertThat(store()
            .commitTurn(claim.lease(),
                    new DurableStore.TurnCommit(List.of(),
                            List.of(
                                    new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                                                    UUID.randomUUID()), 1, processId, 0, "timer", "timer", null, dueAt)),
                            new DurableStore.WaitingTurn(envelope()))))
            .isTrue();
        return run;
    }

    private static DurableStore.Envelope envelope() {
        return new DurableStore.Envelope("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void assertWaitCommittedAuthoritySecretDisposed(ProcessRunId runId) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT payload_envelope FROM cf_durable_outbox WHERE run_id = ? AND event_type = 'WAIT_COMMITTED'")) {
            statement.setString(1, runId.value());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(
                        new String(DurableStore.Envelope.fromStoredBytes(rows.getBytes(1)).payload(),
                                StandardCharsets.UTF_8))
                    .isEqualTo("{}");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private record NonUtcDataSource(DataSource delegate) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return configure(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String user, String password) throws SQLException {
            return configure(delegate.getConnection(user, password));
        }

        private static Connection configure(Connection connection) throws SQLException {
            try (var statement = connection.createStatement()) {
                statement.execute("SET time_zone = '+08:00'");
                return connection;
            } catch (SQLException failure) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            return delegate.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return delegate.isWrapperFor(type);
        }
    }
}
