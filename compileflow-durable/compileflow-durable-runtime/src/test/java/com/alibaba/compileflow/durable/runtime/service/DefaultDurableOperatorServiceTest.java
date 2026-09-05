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
package com.alibaba.compileflow.durable.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.command.EffectResolutionDecision;
import com.alibaba.compileflow.durable.api.command.ResolveEffectCommand;
import com.alibaba.compileflow.durable.api.model.ActiveEffect;
import com.alibaba.compileflow.durable.api.model.ActiveWait;
import com.alibaba.compileflow.durable.api.model.ActiveWorkPage;
import com.alibaba.compileflow.durable.api.model.ActiveWorkQuery;
import com.alibaba.compileflow.durable.api.model.ActiveWorkSummary;
import com.alibaba.compileflow.durable.api.model.ActiveWork;
import com.alibaba.compileflow.durable.api.model.EffectExecutionStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEventQuery;
import com.alibaba.compileflow.durable.api.model.OutboxEventStatus;
import com.alibaba.compileflow.durable.api.model.OutboxEvent;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunControlState;
import com.alibaba.compileflow.durable.api.model.ProcessRunControl;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunQuery;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineEvent;
import com.alibaba.compileflow.durable.api.model.ProcessTimelinePage;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineQuery;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultDurableOperatorServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final ProcessRef.Version VERSION = ProcessRef.version("sales", "approval", "v1");
    private static final UUID PROCESS_ID = UUID.fromString("88d56466-3649-4cdf-a85e-5bf75c9c273c");
    private static final DurableStore.RunProcess PROCESS =
            new DurableStore.RunProcess(PROCESS_ID, VERSION.namespace(), "approval", VERSION);
    private static final ProcessRunId RUN_ID = new ProcessRunId("3ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final ProcessRunId OTHER_RUN_ID = new ProcessRunId("4ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final UUID EFFECT_ID = UUID.fromString("75b32dc1-d76e-45e9-b681-f07a05332250");
    private static final UUID EVENT_ID = UUID.fromString("7b77d2f5-1e1b-40e0-b969-b732ee4e6cd4");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("8b77d2f5-1e1b-40e0-b969-b732ee4e6cd4");

    @Test
    void timelineMappingPreservesTheFrozenSnapshotAndTypedCursor() {
        AtomicReference<DurableStore.TimelineQuery> captured = new AtomicReference<>();
        DurableStore store = store((method, arguments) -> {
            if (method.getName().equals("listTimeline")) {
                captured.set((DurableStore.TimelineQuery) arguments[0]);
                return new DurableStore.TimelinePage(7,
                        List.of(new DurableStore.JournalFact(4, "WAIT_COMMITTED", null, NOW),
                                new DurableStore.JournalFact(5, "WAIT_COMPLETED", null, NOW.plusSeconds(1))), 5L);
            }
            throw unexpected(method);
        });
        DefaultDurableOperatorService service = service(store);

        ProcessTimelinePage page =
                service.listTimeline(new ProcessTimelineQuery(RUN_ID, DurableCursorCodec.timeline(7, 3), 2));

        assertThat(captured.get()).isEqualTo(new DurableStore.TimelineQuery(RUN_ID, 7, 3, 2));
        assertThat(page.runId()).isEqualTo(RUN_ID);
        assertThat(page.snapshotSequence()).isEqualTo(7);
        assertThat(page.items()).extracting(ProcessTimelineEvent::sequence).containsExactly(4L, 5L);
        assertThat(page.items())
            .extracting(event -> event.code().value())
            .containsExactly("WAIT_COMMITTED", "WAIT_COMPLETED");
        assertThat(page.nextCursor()).isEqualTo(DurableCursorCodec.timeline(7, 5));
    }

    @Test
    void activeWorkMappingUsesOneRunScopedAscendingKeyset() {
        AtomicReference<DurableStore.ActiveWorkQuery> captured = new AtomicReference<>();
        DurableStore store = store((method, arguments) -> {
            if (method.getName().equals("listActiveWork")) {
                captured.set((DurableStore.ActiveWorkQuery) arguments[0]);
                return new DurableStore.ActiveWorkPage(List.of(new ActiveWait("root", "approval", "approved", 4, NOW,
                                        null),
                                new ActiveEffect(EFFECT_ID.toString(), "root", "charge", EffectExecutionStatus.PENDING,
                                        5, 0, 0, null, NOW, null, null, 0)), 5L);
            }
            throw unexpected(method);
        });
        DefaultDurableOperatorService service = service(store);

        ActiveWorkPage page = service.listActiveWork(new ActiveWorkQuery(RUN_ID, DurableCursorCodec.activeWork(3), 2));

        assertThat(captured.get()).isEqualTo(new DurableStore.ActiveWorkQuery(RUN_ID, 3, 2));
        assertThat(page.items()).extracting(ActiveWork::occurrenceSequence).containsExactly(4L, 5L);
        assertThat(page.nextCursor()).isEqualTo(DurableCursorCodec.activeWork(5));
    }

    @Test
    void timelineContinuationRejectsStoreSnapshotDrift() {
        DurableStore store = store((method, arguments) -> {
            if (method.getName().equals("listTimeline")) {
                return new DurableStore.TimelinePage(8,
                        List.of(new DurableStore.JournalFact(4, "WAIT_COMMITTED", null, NOW)), null);
            }
            throw unexpected(method);
        });

        assertThatThrownBy(() -> service(store)
            .listTimeline(new ProcessTimelineQuery(RUN_ID, DurableCursorCodec.timeline(7, 3), 2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("frozen Timeline snapshot");
    }

    @Test
    void effectResolutionRejectsMismatchedTargetBeforeMutation() {
        AtomicInteger mutations = new AtomicInteger();
        DurableStore store = store((method, arguments) -> switch (method.getName()) {
            case "findEffectTarget" -> Optional.of(
                    new DurableStore.EffectTarget(OTHER_RUN_ID, EFFECT_ID, PROCESS, PROCESS_ID, "charge"));
            case "resolveEffect" -> {
                mutations.incrementAndGet();
                yield view(RUN_ID, NOW);
            }
            default -> throw unexpected(method);
        });

        assertThatThrownBy(() -> service(store)
            .resolveEffect(
                    new ResolveEffectCommand(RUN_ID, EFFECT_ID.toString(), 1, EffectResolutionDecision.CONFIRM_SUCCEEDED,
                            Map.of(), new AuditPrincipal("operator"), "verified", "audit-1")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Effect occurrence");
        assertThat(mutations).hasValue(0);
    }

    @Test
    void pointLookupsRejectMismatchedStoreIdentity() {
        DurableStore store = store((method, arguments) -> switch (method.getName()) {
            case "findRun" -> Optional.of(view(OTHER_RUN_ID, NOW));
            case "findOutbox" -> Optional.of(outbox(OTHER_RUN_ID, OTHER_EVENT_ID));
            default -> throw unexpected(method);
        });
        DefaultDurableOperatorService service = service(store);

        assertThatThrownBy(() -> service.getRun(RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Run");
        assertThatThrownBy(() -> service.getOutboxEvent(RUN_ID, EVENT_ID.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Outbox event");
    }

    @Test
    void runQueryRejectsOutOfOrderStorePage() {
        ProcessRun older = view(RUN_ID, NOW.minusSeconds(1));
        ProcessRun newer = view(OTHER_RUN_ID, NOW);
        DurableStore store = store((method, arguments) -> {
            if (method.getName().equals("listRuns")) {
                return new DurableStore.RunPage(List.of(older, newer), null, null);
            }
            throw unexpected(method);
        });

        assertThatThrownBy(() -> service(store).listRuns(ProcessRunQuery.firstPage(2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("descending keyset order");
    }

    @Test
    void outboxQueryValidatesFiltersAgainstTheOwningExactProcess() {
        ProcessRef.Version otherProcess = ProcessRef.version("other", "approval", "v1");
        DurableStore store = store((method, arguments) -> {
            if (method.getName().equals("listOutbox")) {
                return new DurableStore.OutboxPage(List.of(outbox(RUN_ID, EVENT_ID, otherProcess)), null, null);
            }
            throw unexpected(method);
        });

        assertThatThrownBy(() -> service(store)
            .listOutboxEvents(new OutboxEventQuery("sales", "approval", Set.of(), null, 10)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("namespace filter");
    }

    private DefaultDurableOperatorService service(DurableStore store) {
        DurableProcessRuntimeManager manager = new DurableProcessRuntimeManager(store,
                new InMemoryDurableProcessRuntimeCache(),
                new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults()),
                ProcessEngineConfig.tbbpmBuilder().classLoader(getClass().getClassLoader()).discoverPlugins(false).build(),
                DurableVersionDefinitionSource.empty());
        return new DefaultDurableOperatorService(store, manager);
    }

    private static DurableStore store(StoreCall call) {
        return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(), new Class<?>[] {DurableStore.class}, (
                                                                                                                                       proxy,
                                                                                                                                       method,
                                                                                                                                       arguments
                                                                                                                               ) -> switch (method.getName()) {
            case "toString" -> "TestDurableStore";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> call.invoke(method, arguments);
        });
    }

    private static ProcessRun view(ProcessRunId runId, Instant createdAt) {
        return new ProcessRun(runId, VERSION.namespace(), VERSION.code(), VERSION, ProcessRunStatus.RUNNABLE,
                new ProcessRunControl(ProcessRunControlState.ACTIVE, 0), ActiveWorkSummary.NONE, createdAt, null, null,
                null, null, createdAt, createdAt, null);
    }

    private static OutboxEvent outbox(ProcessRunId runId, UUID eventId) {
        return outbox(runId, eventId, VERSION);
    }

    private static OutboxEvent outbox(ProcessRunId runId, UUID eventId, ProcessRef.Version process) {
        return new OutboxEvent(runId, process.namespace(), process.code(), process, eventId.toString(), "RUN_SUCCEEDED",
                null, OutboxEventStatus.PENDING, 0, 0, NOW, NOW, null);
    }

    private static AssertionError unexpected(Method method) {
        return new AssertionError("Unexpected Store call: " + method.getName());
    }

    @FunctionalInterface
    private interface StoreCall {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
