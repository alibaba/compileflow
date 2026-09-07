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
import static org.assertj.core.api.Assertions.fail;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisher;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherOptions;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Process-level proof of the external-accept/Outbox-completion ACK-loss window.
 *
 * @author yusu
 */
@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_POSTGRES_URL", matches = ".+")
class LocalPostgresDurableOutboxAckLossTest {
    private static final ProcessRef.Version PROCESS = ProcessRef.version("contract", "ack-loss", "v1");
    private static final UUID PROCESS_ID = UUID.fromString("88d56466-3649-4cdf-a85e-5bf75c9c273c");
    @TempDir
    private Path temporary;

    @Test
    void sigkillAfterDurableAcceptanceReplaysTheSameCommittedEvent() throws Exception {
        DataSource dataSource = dataSource();
        PostgresDurableStoreContractTest.migrate(dataSource);
        DurableStore store = new PostgresDurableStore(dataSource);
        ProcessRunId runId = commitActiveWait(store);
        Path acceptedEvent = temporary.resolve("accepted-event.txt");
        Path acceptedMarker = temporary.resolve("accepted.marker");

        Process child = startCrashProcess(acceptedEvent, acceptedMarker);
        try {
            awaitDurableAcceptance(child, acceptedMarker);
            child.destroyForcibly();
            assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }

        List<String> accepted = Files.readAllLines(acceptedEvent, StandardCharsets.UTF_8);
        assertThat(accepted).hasSize(7);
        assertThat(accepted.get(1)).isEqualTo(runId.value());
        assertThat(accepted.get(5)).isEqualTo("WAIT_COMMITTED");
        assertThat(new String(Base64.getDecoder().decode(accepted.get(6)), StandardCharsets.UTF_8))
            .contains("secret-wait-capability");

        awaitExpiredClaimRecovery(store);
        AtomicReference<DurableOutboxSink.OutboundEvent> replay = new AtomicReference<>();
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, Duration.ofSeconds(5))) {
            DurableOutboxPublisher publisher = new DurableOutboxPublisher(store,
                    event -> {
                        assertSameAcceptedFact(accepted, event);
                        replay.set(event);
                    },
                    new DurableOutboxPublisherOptions("ack-loss-recovery", Duration.ofMillis(100), Duration.ofSeconds(1),
                            100), renewer);
            assertThat(publisher.runOnce()).isTrue();
        }

        UUID eventId = UUID.fromString(accepted.get(0));
        assertThat(replay.get()).isNotNull();
        assertThat(store.findOutbox(runId, eventId).orElseThrow().status()).isEqualTo(OutboxEventStatus.DELIVERED);
        PostgresDurableStoreContractTest.assertWaitCommittedAuthoritySecretDisposed(dataSource, runId);
    }

    private static ProcessRunId commitActiveWait(DurableStore store) {
        byte[] definition = "<bpm code=\"ack-loss\" version=\"v1\"/>".getBytes(StandardCharsets.UTF_8);
        String digest = ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, PROCESS.code(), definition);
        DurableStore.StoredProcess stored = store.registerProcess(
                new DurableStore.ProcessRegistration(PROCESS_ID, PROCESS.code(), ProcessModelType.TBBPM, definition,
                        digest));
        DurableStore.RunProcess process =
                new DurableStore.RunProcess(stored.processId(), PROCESS.namespace(), stored.processCode(), PROCESS);
        ProcessRunId runId = ProcessRunId.random();
        store.start(
                new DurableStore.NewRun(runId, process, Set.of(process.processId()), envelope("initial-state"), null));
        DurableStore.RunClaim claim = store
            .claimRun(
                    new DurableStore.RunClaimRequest("ack-loss-turn", Set.of(process.processId()), Duration.ofSeconds(5)))
            .orElseThrow();
        String waitToken = "secret-wait-capability";
        assertThat(store.commitTurn(claim.lease(),
                new DurableStore.TurnCommit(java.util.List.of(),
                        java.util.List.of(
                                new DurableStore.WaitCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT,
                                                UUID.randomUUID()), 1, PROCESS_ID, 0, "root", "approval", "approved",
                                        DurableDigests.sha256(waitToken), null,
                                        new DurableStore.Envelope(new DurableKernelJsonCodec()
                                            .encode(Map.of("waitToken", waitToken, "elementId", "approval"))))),
                        new DurableStore.WaitingTurn(envelope("wait-state")))))
            .isTrue();
        return runId;
    }

    private static Process startCrashProcess(Path acceptedEvent, Path acceptedMarker) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(java, "-cp", classpath, OutboxAckLossCrashProcess.class.getName(),
                acceptedEvent.toString(), acceptedMarker.toString())
            .redirectErrorStream(true)
            .start();
    }

    private static void awaitDurableAcceptance(Process child, Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(marker)) {
                return;
            }
            if (!child.isAlive()) {
                fail("ACK-loss child exited before durable acceptance:\n" + childOutput(child));
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for the external durable-acceptance marker");
    }

    private static void awaitExpiredClaimRecovery(DurableStore store) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (store.reclaimExpiredOutbox(10) == 1) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out reclaiming the SIGKILLed Outbox lease");
    }

    private static void assertSameAcceptedFact(List<String> accepted, DurableOutboxSink.OutboundEvent replay) {
        assertThat(replay.eventId().toString()).isEqualTo(accepted.get(0));
        assertThat(replay.runId().value()).isEqualTo(accepted.get(1));
        assertThat(replay.processVersion().namespace()).isEqualTo(accepted.get(2));
        assertThat(replay.processCode()).isEqualTo(accepted.get(3));
        assertThat(replay.processVersion().version()).isEqualTo(accepted.get(4));
        assertThat(replay.eventType()).isEqualTo(accepted.get(5));
        assertThat(Base64.getEncoder().encodeToString(new DurableKernelJsonCodec().encode(replay.payload())))
            .isEqualTo(accepted.get(6));
    }

    private static String childOutput(Process child) throws Exception {
        return new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static DurableStore.Envelope envelope(String value) {
        return new DurableStore.Envelope(value.getBytes(StandardCharsets.UTF_8));
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(System.getenv("COMPILEFLOW_DURABLE_POSTGRES_URL"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_USER", "postgres"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_PASSWORD", "postgres"));
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
