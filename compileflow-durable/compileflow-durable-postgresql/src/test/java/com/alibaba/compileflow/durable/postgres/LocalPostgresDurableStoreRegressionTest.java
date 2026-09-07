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
package com.alibaba.compileflow.durable.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_POSTGRES_URL", matches = ".+")
class LocalPostgresDurableStoreRegressionTest {
    private DataSource dataSource;
    private DurableStore store;
    private UUID processId;
    private ProcessRunId runId;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(System.getenv("COMPILEFLOW_DURABLE_POSTGRES_URL"),
                System.getenv().getOrDefault("COMPILEFLOW_DURABLE_POSTGRES_USER", "postgres"),
                System.getenv().getOrDefault("COMPILEFLOW_DURABLE_POSTGRES_PASSWORD", "postgres"));
        PostgresDurableStoreContractTest.migrate(dataSource);
        store = new PostgresDurableStore(dataSource);
        processId = UUID.randomUUID();
        byte[] definition = "<bpm code=\"regression\"/>".getBytes(StandardCharsets.UTF_8);
        store.registerProcess(
                new DurableStore.ProcessRegistration(processId, "regression", ProcessModelType.TBBPM, definition,
                        ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, "regression", definition)));
        runId = ProcessRunId.random();
        store.start(
                new DurableStore.NewRun(runId, new DurableStore.RunProcess(processId, "test", "regression", null),
                        Set.of(processId), envelope(), null));
    }

    @Test
    void rejectsRunCommitWhenLeaseExpiresWhileWaitingForAnUnchangedRowLock() throws Exception {
        DurableStore.RunClaim claim = claim(Duration.ofSeconds(2));
        var worker = Executors.newSingleThreadExecutor();
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT run_id FROM cf_durable_run WHERE run_id = ? FOR UPDATE")) {
                lock.setObject(1, UUID.fromString(runId.value()));
                try (var rows = lock.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            var commit = worker.submit(() -> store.commitTurn(claim.lease(),
                    new DurableStore.TurnCommit(List.of(), List.of(), new DurableStore.SucceededTurn(envelope()))));
            try {
                awaitBlockedRunStatement(blocker);
                try (var wait = blocker.createStatement()) {
                    wait.execute("SELECT pg_sleep(2.1)");
                }
            } finally {
                blocker.commit();
            }
            assertThat(commit.get(10, TimeUnit.SECONDS)).isFalse();
            assertThat(store.findRun(runId).orElseThrow().status()).isEqualTo(ProcessRunStatus.RUNNING);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cancellationWithUncertainEffectClearsRunLeaseAndFaultBackoff() throws Exception {
        var first = claim(Duration.ofSeconds(10));
        var effectId = UUID.randomUUID();
        assertThat(store.commitTurn(first.lease(),
                new DurableStore.TurnCommit(List.of(),
                        List.of(
                                new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                                                effectId), 1, processId, 0, "effect", "effect",
                                        EffectRecoveryPlan.manual(), envelope())),
                        new DurableStore.RunnableTurn(envelope()))))
            .isTrue();
        var effect = store
            .claimEffect(
                    new DurableStore.EffectClaimRequest("effect-worker", DurableStore.EffectOperation.DISPATCH,
                            Duration.ofSeconds(10)))
            .orElseThrow();
        var faulted = claim(Duration.ofSeconds(10));
        assertThat(store.releaseRunFault(faulted.lease(), Duration.ZERO, RunRetryCode.TURN_EXECUTION_FAULT)).isTrue();
        var running = claim(Duration.ofSeconds(10));
        store.requestCancel(new DurableStore.CancelCommand(runId));
        assertThat(store.commitRunCancelled(running.lease())).isTrue();
        assertThat(store.findRun(runId).orElseThrow().status()).isEqualTo(ProcessRunStatus.WAITING);
        assertThat(store.findRun(runId).orElseThrow().retry()).isNull();
        assertThat(store.completeEffect(effect.lease(), envelope())).isTrue();
        assertThat(store.findRun(runId).orElseThrow().status()).isEqualTo(ProcessRunStatus.CANCELLED);
    }

    @Test
    void drainsMoreThanOneTurnOfResolvedOccurrencesInBoundedBatches() {
        var first = claim(Duration.ofSeconds(10));
        assertThat(store.commitTurn(first.lease(),
                new DurableStore.TurnCommit(List.of(), timers(1, 256), new DurableStore.RunnableTurn(envelope()))))
            .isTrue();
        var second = claim(Duration.ofSeconds(10));
        assertThat(store.commitTurn(second.lease(),
                new DurableStore.TurnCommit(List.of(), timers(257, 1), new DurableStore.WaitingTurn(envelope()))))
            .isTrue();
        assertThat(store.resolveDueWaits(1000)).isEqualTo(257);
        var batch = claim(Duration.ofSeconds(10));
        assertThat(batch.occurrenceResults()).hasSize(256);
        assertThat(store.commitTurn(batch.lease(),
                new DurableStore.TurnCommit(batch
                            .occurrenceResults()
                            .stream()
                            .map(DurableStore.OccurrenceResult::occurrence)
                            .toList(), List.of(), new DurableStore.WaitingTurn(envelope()))))
            .isTrue();
        var last = claim(Duration.ofSeconds(10));
        assertThat(last.occurrenceResults()).hasSize(1);
        assertThat(last.occurrenceResults().get(0).occurrenceSequence()).isEqualTo(257);
        assertThat(store.commitTurn(last.lease(),
                new DurableStore.TurnCommit(last
                            .occurrenceResults()
                            .stream()
                            .map(DurableStore.OccurrenceResult::occurrence)
                            .toList(), List.of(), new DurableStore.SucceededTurn(envelope()))))
            .isTrue();
    }

    private DurableStore.RunClaim claim(Duration lease) {
        return store.claimRun(new DurableStore.RunClaimRequest("worker", Set.of(processId), lease)).orElseThrow();
    }

    @Test
    void schemaRejectsResolvedOccurrenceWithoutResolutionKind() throws Exception {
        var claim = claim(Duration.ofSeconds(10));
        assertThat(store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(List.of(), timers(1, 1), new DurableStore.WaitingTurn(envelope()))))
            .isTrue();
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolved_at = clock_timestamp(), result_envelope = ?
                 WHERE run_id = ?
                """)) {
            update.setBytes(1, envelope().bytes());
            update.setObject(2, UUID.fromString(runId.value()));
            assertThatThrownBy(update::executeUpdate)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cf_durable_wait_result");
        }
    }

    @Test
    void schemaRejectsTimerResolutionBeforeSchedulingOrDueTime() throws Exception {
        var claim = claim(Duration.ofSeconds(10));
        assertThat(store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(List.of(), timers(1, 1), new DurableStore.WaitingTurn(envelope()))))
            .isTrue();
        try (var connection = dataSource.getConnection();
                var update = connection.prepareStatement(
                        """
                UPDATE cf_durable_wait
                   SET status = 'RESOLVED', resolution_kind = 'FIRED', result_envelope = ?,
                       due_at = created_at - interval '1 minute',
                       resolved_at = created_at - interval '1 second'
                 WHERE run_id = ?
                """)) {
            update.setBytes(1, envelope().bytes());
            update.setObject(2, UUID.fromString(runId.value()));
            assertThatThrownBy(update::executeUpdate)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cf_durable_wait_lifetime");
        }
    }

    private List<DurableStore.OccurrenceCommit> timers(int start, int count) {
        List<DurableStore.OccurrenceCommit> timers = new ArrayList<>();
        for (int sequence = start; sequence < start + count; sequence++) {
            timers.add(
                    new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                                    UUID.randomUUID()), sequence, processId, 0, "frontier-" + sequence, "timer",
                            Duration.ZERO, null));
        }
        return timers;
    }

    private static DurableStore.Envelope envelope() {
        return new DurableStore.Envelope("{}".getBytes(StandardCharsets.UTF_8));
    }

    private static void awaitBlockedRunStatement(Connection observer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            try (var clear = observer.createStatement()) {
                clear.execute("SELECT pg_stat_clear_snapshot()");
            }
            try (var query = observer.createStatement();
                    var rows = query.executeQuery(
                            """
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                         WHERE datname = current_database() AND pid <> pg_backend_pid()
                           AND wait_event_type = 'Lock' AND query LIKE '%cf_durable_run%')
                        """)) {
                rows.next();
                if (rows.getBoolean(1)) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Run statement did not block before its lease deadline");
    }
}
