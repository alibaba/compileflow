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
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * JDBC-boundary checks only; these do not execute or validate a real MySQL database.
 */
class MySqlDurableStoreSqlTest {
    private static final ProcessRunId RUN_ID = new ProcessRunId("00000000-0000-0000-0000-000000000001");

    @Test
    void schemaAllowsPastAbsoluteTimersAndRequiresResolvedKind() throws Exception {
        try (var resource =
                getClass().getResourceAsStream("/db/compileflow-durable/mysql/migration/V1__durable_kernel.sql")) {
            assertThat(resource).isNotNull();
            String schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(schema)
                .contains("kind = 'TIMER' OR due_at IS NULL OR due_at >= created_at",
                        "status = 'RESOLVED' AND resolution_kind IS NOT NULL",
                        "resolved_at IS NULL OR resolved_at >= created_at",
                        "resolution_kind <> 'FIRED' OR (due_at IS NOT NULL AND resolved_at >= due_at)");
        }
    }

    @ParameterizedTest
    @MethodSource("wakeupOperations")
    void computesWakeupTimeFromTheOriginalWaitingStatus(Consumer<DurableStore> operation) {
        List<String> statements = new ArrayList<>();
        assertThatThrownBy(() -> operation.accept(store(statements, true, true))).isInstanceOf(WakeupCaptured.class);
        String wakeup = statements.get(statements.size() - 1);
        assertThat(wakeup).contains("available_at = CASE WHEN status = 'WAITING'", "status = CASE");
        assertThat(wakeup.indexOf("available_at =")).isLessThan(wakeup.indexOf("status = CASE"));
    }

    static Stream<Consumer<DurableStore>> wakeupOperations() {
        return Stream.of(store -> store.completeWait(
                new DurableStore.WaitCompletion(RUN_ID, "a".repeat(64), new DurableStore.Envelope(new byte[] {1}))), store -> store.resolveDueWaits(
                1), store -> store.completeEffect(new DurableStore.EffectLease(RUN_ID, UUID.randomUUID(),
                        UUID.randomUUID()), new DurableStore.Envelope(new byte[] {1})));
    }

    @ParameterizedTest
    @MethodSource("boundedOperations")
    void putsLimitBeforeTheLockingClause(Consumer<DurableStore> operation) {
        List<String> statements = new ArrayList<>();
        operation.accept(store(statements, false));
        List<String> selects = statements
            .stream()
            .filter(sql -> sql.startsWith("SELECT"))
            .toList();
        assertThat(selects).isNotEmpty();
        for (String sql : selects) {
            assertThat(sql).contains("LIMIT", "FOR UPDATE", "SKIP LOCKED");
            assertThat(sql.indexOf("LIMIT")).as(sql).isLessThan(sql.indexOf("FOR UPDATE"));
        }
    }

    static Stream<Consumer<DurableStore>> boundedOperations() {
        return Stream.of(store -> store.claimRun(
                new DurableStore.RunClaimRequest("worker", Set.of(UUID.randomUUID()), Duration.ofSeconds(10))), store -> store.claimEffect(
                new DurableStore.EffectClaimRequest("worker", DurableStore.EffectOperation.DISPATCH,
                        Duration.ofSeconds(10))), store -> store.claimEffect(
                new DurableStore.EffectClaimRequest("worker", DurableStore.EffectOperation.RECONCILE,
                        Duration.ofSeconds(10))), store -> store.claimOutbox(
                new DurableStore.OutboxClaimRequest("worker", Duration.ofSeconds(10))), store -> store.reclaimExpiredRuns(
                10), store -> store.purgeTerminalRuns(Duration.ZERO, 10), store -> store.purgeConsumedOccurrences(Duration.ZERO,
                10), store -> store.purgeUnusedProcesses(Duration.ZERO, 10));
    }

    @Test
    void joinedClaimsAcquireOnlyTheRunLockBeforeTheChildLock() {
        List<String> statements = new ArrayList<>();
        DurableStore store = store(statements, false);
        store.claimEffect(
                new DurableStore.EffectClaimRequest("worker", DurableStore.EffectOperation.DISPATCH,
                        Duration.ofSeconds(10)));
        store.claimOutbox(new DurableStore.OutboxClaimRequest("worker", Duration.ofSeconds(10)));
        assertThat(statements)
            .hasSize(2)
            .allSatisfy(sql -> assertThat(sql).contains("FOR UPDATE OF r SKIP LOCKED"));
    }

    @Test
    void cancellationClearsAuthorityBeforeChangingTheStatusItTests() {
        List<String> statements = new ArrayList<>();
        DurableStore store = store(statements, true);
        assertThat(store.commitRunCancelled(new DurableStore.RunLease(ProcessRunId.random(), UUID.randomUUID()))).isTrue();
        String cancellation = statements
            .stream()
            .filter(sql -> sql.startsWith("UPDATE cf_durable_run") && sql.contains("THEN 'WAITING'"))
            .findFirst()
            .orElseThrow();
        assertThat(cancellation.indexOf("lease_until =")).isLessThan(cancellation.indexOf("status = CASE"));
        assertThat(cancellation).contains("turn_fault_streak = 0", "retry_code = NULL", "retry_observed_at = NULL");
        assertThat(cancellation).contains("lease_owner = NULL", "lease_token = NULL", "lease_until = NULL");
    }

    @Test
    void runLeaseTimeIsCheckedInAStatementAfterTheRowLock() {
        List<String> statements = new ArrayList<>();
        DurableStore store = store(statements, true);
        store.releaseRunAfterCapabilityLoss(new DurableStore.RunLease(ProcessRunId.random(), UUID.randomUUID()),
                Duration.ZERO);
        List<String> selects =
                statements
            .stream()
            .filter(sql -> sql.startsWith("SELECT") && sql.contains("FROM cf_durable_run"))
            .toList();
        assertThat(selects).hasSizeGreaterThanOrEqualTo(2);
        assertThat(selects.get(0)).contains("FOR UPDATE").doesNotContain("CURRENT_TIMESTAMP");
        assertThat(selects.get(1)).contains("lease_until > CURRENT_TIMESTAMP(3)");
    }

    private static DurableStore store(List<String> statements, boolean rowPresent) {
        return store(statements, rowPresent, false);
    }

    private static DurableStore store(List<String> statements, boolean rowPresent, boolean stopAtWakeup) {
        InvocationHandler connectionHandler =
                (proxy, method, args) -> switch (method.getName()) {
            case "getAutoCommit" -> true;
            case "prepareStatement" -> ((String) args[0]).equals("SELECT @@session.time_zone")
                    ? timeZoneStatement()
                    : statement(statements, (String) args[0], rowPresent, stopAtWakeup);
            case "setAutoCommit", "commit", "rollback", "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, connectionHandler);
        InvocationHandler sourceHandler =
                (proxy, method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            throw new UnsupportedOperationException(method.getName());
        };
        DataSource source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, sourceHandler);
        return new MySqlDurableStore(source);
    }

    private static PreparedStatement timeZoneStatement() {
        InvocationHandler rows =
                (proxy, method, args) -> switch (method.getName()) {
            case "next" -> true;
            case "getString" -> "+00:00";
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        InvocationHandler statement =
                (proxy, method, args) -> switch (method.getName()) {
            case "executeQuery" -> Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                    new Class<?>[] {ResultSet.class}, rows);
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, statement);
    }

    private static PreparedStatement statement(List<String> statements, String sql, boolean rowPresent,
            boolean stopAtWakeup) {
        statements.add(sql.strip().replaceAll("\\s+", " "));
        if (stopAtWakeup && sql.contains("UPDATE cf_durable_run") && sql.contains("THEN 'RUNNABLE'")) {
            throw new WakeupCaptured();
        }
        InvocationHandler handler =
                (proxy, method, args) -> {
            if (method.getName().startsWith("set") || method.getName().equals("close")) {
                return null;
            }
            if (method.getName().equals("executeUpdate")) {
                return 1;
            }
            if (method.getName().equals("executeQuery")) {
                return rows(rowPresent, sql);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, handler);
    }

    private static ResultSet rows(boolean rowPresent, String sql) {
        boolean[] next = {rowPresent};
        InvocationHandler handler =
                (proxy, method, args) -> switch (method.getName()) {
            case "next" -> {
                boolean result = next[0];
                next[0] = false;
                yield result;
            }
            case "getBoolean" -> !sql.contains("cancel_requested");
            case "getLong" -> 0L;
            case "getBytes" -> null;
            case "getString" -> {
                if (sql.contains("SELECT status FROM")) {
                    yield "WAITING";
                }
                yield switch (String.valueOf(args[0])) {
                    case "status" -> "ACTIVE";
                    case "kind" -> "TIMER";
                    case "running_operation" -> "DISPATCH";
                    default -> RUN_ID.value();
                };
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class},
                handler);
    }

    private static final class WakeupCaptured extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
