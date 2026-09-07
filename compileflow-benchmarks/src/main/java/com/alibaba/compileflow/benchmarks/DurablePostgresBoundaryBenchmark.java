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
package com.alibaba.compileflow.benchmarks;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.postgres.PostgresDurableStore;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * PostgreSQL transaction latency for the current seven-table Durable Kernel layout.
 *
 * @author yusu
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class DurablePostgresBoundaryBenchmark {
    /**
     * Measures one exact-Version Run creation transaction.
     */
    @Benchmark
    public ProcessRun startRun(DatabaseState database) {
        return database.startNewRun();
    }

    /**
     * Measures the atomic Snapshot, Wait, Journal, and Outbox commit.
     */
    @Benchmark
    public boolean commitWaitBoundary(ClaimedRunState claimed) {
        return requireSuccess(claimed.database.store.commitTurn(claimed.claim.lease(),
                        claimed.database.waitCommit(claimed.claim, claimed.waitToken)), "Wait commit");
    }

    /**
     * Measures the atomic Snapshot, Timer occurrence, and Journal commit.
     */
    @Benchmark
    public boolean commitTimerBoundary(ClaimedRunState claimed) {
        return requireSuccess(claimed.database.store.commitTurn(claimed.claim.lease(),
                        claimed.database.timerCommit(claimed.claim)), "Timer commit");
    }

    /**
     * Measures idempotent external completion acceptance against an active Wait.
     */
    @Benchmark
    public ProcessRun completeWait(WaitingRunState waiting) {
        return waiting.database.completeWait(waiting.runId, waiting.waitToken);
    }

    /**
     * Measures the atomic Snapshot, Effect, and Journal commit.
     */
    @Benchmark
    public boolean commitEffectBoundary(EffectRunState effect) {
        return requireSuccess(effect.database.store.commitTurn(effect.claim.lease(),
                        effect.database.effectCommit(effect.claim)), "Effect commit");
    }

    /**
     * Measures acquisition of one exact-Version runnable Run.
     */
    @Benchmark
    public DurableStore.RunClaim claimRun(RunClaimState run) {
        run.claim = run.database.claimExistingRun();
        return run.claim;
    }

    /**
     * Measures one fenced terminal Run commit.
     */
    @Benchmark
    public boolean completeRun(CompleteRunState run) {
        return requireSuccess(run.database.store.commitTurn(run.claim.lease(),
                        run.database.succeededTurn(run.claim, run.database.resultEnvelope("run-complete"))),
                "Run completion");
    }

    /**
     * Measures one fenced Effect completion and Run reactivation.
     */
    @Benchmark
    public boolean completeEffect(ClaimedEffectState effect) {
        return requireSuccess(effect.database.store.completeEffect(effect.claim.lease(),
                        effect.database.resultEnvelope("effect-complete")), "Effect completion");
    }

    /**
     * Measures the conservative transition from an invoked Effect to UNKNOWN.
     */
    @Benchmark
    public boolean markEffectUnknown(ClaimedEffectState effect) {
        return requireSuccess(effect.database.store.markEffectUnknown(effect.claim.lease(), Duration.ZERO,
                        "benchmark-unknown"), "Effect UNKNOWN transition");
    }

    /**
     * Measures one UNKNOWN Effect claim on the reconciliation lane.
     */
    @Benchmark
    public DurableStore.EffectClaim claimEffectReconcile(ReconcileReadyState effect) {
        effect.claim = effect.database.claimEffect(DurableStore.EffectOperation.RECONCILE);
        return effect.claim;
    }

    /**
     * Measures one Outbox delivery claim; completion runs outside the sample.
     */
    @Benchmark
    public DurableStore.OutboxClaim claimOutbox(OutboxReadyState outbox) {
        outbox.claim = outbox.database.store
            .claimOutbox(new DurableStore.OutboxClaimRequest("benchmark-outbox", DatabaseState.LEASE))
            .orElseThrow();
        return outbox.claim;
    }

    private static boolean requireSuccess(boolean committed, String operation) {
        if (!committed) {
            throw new IllegalStateException(operation + " lost its fence; benchmark sample is invalid");
        }
        return true;
    }

    /**
     * Trial-scoped dedicated PostgreSQL database.
     */
    @State(Scope.Benchmark)
    public static class DatabaseState {
        private static final ProcessRef.Version VERSION = ProcessRef.version("benchmark", "durable-boundary", "v1");
        private static final UUID PROCESS_ID = UUID.fromString("ce0b8ebf-38a0-8dc1-92a9-5fa9cdfef728");
        private static final long ROOT_INVOCATION_ID = 0;
        private static final DurableStore.RunProcess PROCESS =
                new DurableStore.RunProcess(PROCESS_ID, VERSION.namespace(), VERSION.code(), VERSION);
        private static final Duration LEASE = Duration.ofSeconds(30);
        private static final byte[] DEFINITION =
                "<bpm code=\"durable-boundary\" version=\"v1\"/>".getBytes(StandardCharsets.UTF_8);
        private final AtomicLong sequence = new AtomicLong();
        /**
         * Snapshot payload size used by every state-writing boundary.
         */
        @Param({"16384", "65536", "262144", "1048576"})
        public int snapshotBytes;
        private Flyway flyway;
        private PostgresDurableStore store;

        private static String environment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " must be configured");
            }
            return value;
        }

        private static void requireCleanOptIn() {
            if (!"true".equals(environment("COMPILEFLOW_DURABLE_BENCHMARK_ALLOW_CLEAN"))) {
                throw new IllegalStateException(
                        "COMPILEFLOW_DURABLE_BENCHMARK_ALLOW_CLEAN must equal true; the dedicated database is cleaned");
            }
        }

        /**
         * Cleans, migrates, and seeds the dedicated benchmark database.
         */
        @Setup(Level.Trial)
        public void setup() {
            requireCleanOptIn();
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setUrl(environment("COMPILEFLOW_DURABLE_BENCHMARK_JDBC_URL"));
            dataSource.setUser(environment("COMPILEFLOW_DURABLE_BENCHMARK_USERNAME"));
            dataSource.setPassword(environment("COMPILEFLOW_DURABLE_BENCHMARK_PASSWORD"));
            flyway = Flyway
                .configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .locations("classpath:db/compileflow-durable/postgres/migration")
                .load();
            flyway.clean();
            flyway.migrate();
            store = new PostgresDurableStore(dataSource);
            store.registerProcess(
                    new DurableStore.ProcessRegistration(PROCESS_ID, VERSION.code(), ProcessModelType.TBBPM, DEFINITION,
                            ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, VERSION.code(), DEFINITION)));
        }

        /**
         * Cleans the dedicated benchmark database.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (flyway != null) {
                flyway.clean();
            }
        }

        private ProcessRun startNewRun() {
            long id = sequence.incrementAndGet();
            return store.start(
                    new DurableStore.NewRun(ProcessRunId.random(), PROCESS, Set.of(PROCESS.processId()),
                            snapshotEnvelope("initial-state-" + id), null));
        }

        private DurableStore.RunClaim claimExistingRun() {
            return store
                .claimRun(new DurableStore.RunClaimRequest("benchmark-turn", Set.of(PROCESS.processId()), LEASE))
                .orElseThrow();
        }

        private DurableStore.RunClaim claimOrCreate() {
            DurableStore.RunClaimRequest request =
                    new DurableStore.RunClaimRequest("benchmark-turn", Set.of(PROCESS.processId()), LEASE);
            return store.claimRun(request).orElseGet(() -> {
                startNewRun();
                return store.claimRun(request).orElseThrow();
            });
        }

        private DurableStore.TurnCommit waitCommit(DurableStore.RunClaim claim, String waitToken) {
            long occurrence = claim.occurrenceSequence() + 1;
            DurableStore.WaitCommit wait = new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                            UUID.randomUUID()), occurrence, PROCESS_ID, ROOT_INVOCATION_ID, "root",
                    "approval-" + occurrence, "approved", DurableDigests.sha256(waitToken), null,
                    envelope("wait-committed-" + occurrence));
            return waitingTurn(claim, wait, snapshotEnvelope("waiting-state-" + sequence.incrementAndGet()));
        }

        private DurableStore.TurnCommit timerCommit(DurableStore.RunClaim claim) {
            long occurrence = claim.occurrenceSequence() + 1;
            DurableStore.TimerCommit timer = new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                            UUID.randomUUID()), occurrence, PROCESS_ID, ROOT_INVOCATION_ID, "root",
                    "timer-" + occurrence, Duration.ofMinutes(5), null);
            return waitingTurn(claim, timer, snapshotEnvelope("timer-state-" + occurrence));
        }

        private DurableStore.TurnCommit effectCommit(DurableStore.RunClaim claim) {
            return effectCommit(claim, EffectRecoveryPlan.manual());
        }

        private DurableStore.TurnCommit effectCommit(DurableStore.RunClaim claim, EffectRecoveryPlan recoveryPlan) {
            long occurrence = claim.occurrenceSequence() + 1;
            DurableStore.EffectCommit effect = new DurableStore.EffectCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.EFFECT,
                            UUID.randomUUID()), occurrence, PROCESS_ID, ROOT_INVOCATION_ID, "root",
                    "effect-" + occurrence, recoveryPlan, envelope("effect-input-" + sequence.incrementAndGet()));
            return waitingTurn(claim, effect, snapshotEnvelope("effect-state-" + occurrence));
        }

        private DurableStore.EffectClaim createEffectClaim(EffectRecoveryPlan recoveryPlan) {
            DurableStore.RunClaim run = claimOrCreate();
            if (!store.commitTurn(run.lease(), effectCommit(run, recoveryPlan))) {
                throw new IllegalStateException("Unable to create Effect boundary");
            }
            return claimEffect(DurableStore.EffectOperation.DISPATCH);
        }

        private DurableStore.EffectClaim claimEffect(DurableStore.EffectOperation operation) {
            return store
                .claimEffect(
                        new DurableStore.EffectClaimRequest("benchmark-effect-"
                                + operation.name().toLowerCase(Locale.ROOT), operation, LEASE))
                .orElseThrow();
        }

        private ProcessRun completeWait(ProcessRunId runId, String waitToken) {
            return store.completeWait(
                    new DurableStore.WaitCompletion(runId, DurableDigests.sha256(waitToken),
                            envelope("approved-" + sequence.incrementAndGet())));
        }

        private String waitToken(DurableStore.RunClaim claim) {
            return "wait-" + claim.lease().runId().value() + '-' + (claim.occurrenceSequence() + 1);
        }

        private DurableStore.TurnCommit waitingTurn(DurableStore.RunClaim claim,
                DurableStore.OccurrenceCommit occurrence, DurableStore.Envelope continuation) {
            return new DurableStore.TurnCommit(consumedOccurrences(claim), List.of(occurrence),
                    new DurableStore.WaitingTurn(continuation));
        }

        private DurableStore.TurnCommit succeededTurn(DurableStore.RunClaim claim, DurableStore.Envelope result) {
            return new DurableStore.TurnCommit(consumedOccurrences(claim), List.of(),
                    new DurableStore.SucceededTurn(result));
        }

        private static List<DurableStore.OccurrenceKey> consumedOccurrences(DurableStore.RunClaim claim) {
            return claim.occurrenceResults().stream().map(DurableStore.OccurrenceResult::occurrence).toList();
        }

        private static DurableStore.Envelope envelope(String value) {
            return new DurableStore.Envelope(value.getBytes(StandardCharsets.UTF_8));
        }

        private DurableStore.Envelope resultEnvelope(String value) {
            return envelope(value + '-' + sequence.incrementAndGet());
        }

        private DurableStore.Envelope snapshotEnvelope(String marker) {
            byte[] snapshot = new byte[snapshotBytes];
            byte[] prefix = marker.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(prefix, 0, snapshot, 0, Math.min(prefix.length, snapshot.length));
            return new DurableStore.Envelope(snapshot);
        }
    }

    /**
     * Invocation fixture for Wait commit.
     */
    @State(Scope.Thread)
    public static class ClaimedRunState {
        private DatabaseState database;
        private DurableStore.RunClaim claim;
        private String waitToken;

        /**
         * Claims a Run outside the measured transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            claim = database.claimOrCreate();
            waitToken = database.waitToken(claim);
        }
    }

    /**
     * Invocation fixture for Wait completion acceptance.
     */
    @State(Scope.Thread)
    public static class WaitingRunState {
        private DatabaseState database;
        private ProcessRunId runId;
        private String waitToken;

        /**
         * Creates an active Wait outside the measured transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            DurableStore.RunClaim claim = database.claimOrCreate();
            waitToken = database.waitToken(claim);
            DurableStore.TurnCommit wait = database.waitCommit(claim, waitToken);
            if (!database.store.commitTurn(claim.lease(), wait)) {
                throw new IllegalStateException("Unable to create active Wait");
            }
            runId = claim.lease().runId();
        }
    }

    /**
     * Invocation fixture for Effect commit.
     */
    @State(Scope.Thread)
    public static class EffectRunState {
        private DatabaseState database;
        private DurableStore.RunClaim claim;

        /**
         * Claims a Run outside the measured transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            claim = database.claimOrCreate();
        }
    }

    /**
     * Invocation fixture for a measured Run claim.
     */
    @State(Scope.Thread)
    public static class RunClaimState {
        private DatabaseState database;
        private DurableStore.RunClaim claim;

        /**
         * Creates an exact-Version runnable Run outside the measured claim.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            database.startNewRun();
        }

        /**
         * Settles a successfully measured claim outside the sample.
         */
        @TearDown(Level.Invocation)
        public void tearDown() {
            if (claim != null) {
                requireSuccess(database.store.commitTurn(claim.lease(),
                                database.succeededTurn(claim, database.resultEnvelope("claimed-run"))),
                        "Run claim settlement");
                claim = null;
            }
        }
    }

    /**
     * Invocation fixture for terminal Run completion.
     */
    @State(Scope.Thread)
    public static class CompleteRunState {
        private DatabaseState database;
        private DurableStore.RunClaim claim;

        /**
         * Claims a Run outside the measured completion transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            claim = database.claimOrCreate();
        }
    }

    /**
     * Invocation fixture for Effect completion or UNKNOWN transition.
     */
    @State(Scope.Thread)
    public static class ClaimedEffectState {
        private DatabaseState database;
        private DurableStore.EffectClaim claim;

        /**
         * Creates and dispatch-claims one Effect outside the measured transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            claim = database.createEffectClaim(EffectRecoveryPlan.manual());
        }
    }

    /**
     * Invocation fixture for reconciliation-lane acquisition.
     */
    @State(Scope.Thread)
    public static class ReconcileReadyState {
        private DatabaseState database;
        private DurableStore.EffectClaim claim;

        /**
         * Creates one immediately eligible UNKNOWN Effect outside the measured claim.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            DurableStore.EffectClaim dispatch =
                    database.createEffectClaim(EffectRecoveryPlan.reconcile(1, 1, Duration.ofMillis(1),
                            Duration.ofMinutes(1)));
            if (!database.store.markEffectUnknown(dispatch.lease(), Duration.ZERO, "benchmark-reconcile")) {
                throw new IllegalStateException("Unable to create UNKNOWN Effect");
            }
        }

        /**
         * Settles a successfully measured reconciliation claim outside the sample.
         */
        @TearDown(Level.Invocation)
        public void tearDown() {
            if (claim != null) {
                requireSuccess(database.store.requireEffectReview(claim.lease(), "benchmark-complete"),
                        "Reconciliation settlement");
                claim = null;
            }
        }
    }

    /**
     * Invocation fixture for Outbox claim and completion.
     */
    @State(Scope.Thread)
    public static class OutboxReadyState {
        private DatabaseState database;
        private DurableStore.OutboxClaim claim;

        /**
         * Creates a terminal event outside the measured transaction.
         */
        @Setup(Level.Invocation)
        public void setup(DatabaseState shared) {
            database = shared;
            DurableStore.RunClaim run = database.claimOrCreate();
            if (!database.store.commitTurn(run.lease(), database.succeededTurn(run, DatabaseState.envelope("done")))) {
                throw new IllegalStateException("Unable to create Outbox event");
            }
        }

        /**
         * Settles the measured claim outside the sample.
         */
        @TearDown(Level.Invocation)
        public void tearDown() {
            if (claim != null) {
                requireSuccess(database.store.completeOutbox(claim.lease()), "Outbox settlement");
                claim = null;
            }
        }
    }
}
