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
package com.alibaba.compileflow.durable.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ActiveWorkSummary;
import com.alibaba.compileflow.durable.api.model.ProcessRunControlState;
import com.alibaba.compileflow.durable.api.model.ProcessRunControl;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunResult;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasState;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.DeterministicAliasSelector;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDurableProcessEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final ProcessRef.Version VERSION = ProcessRef.version("sales", "approval", "v1");
    private static final ProcessRef.Alias ALIAS = ProcessRef.alias("sales", "approval", "prod");
    private static final UUID PROCESS_ID = UUID.fromString("61846629-a649-8778-8efb-05a805fbcfaa");
    private static final DurableStore.RunProcess PROCESS =
            new DurableStore.RunProcess(PROCESS_ID, VERSION.namespace(), "approval", VERSION);
    private static final ProcessRunId RUN_ID = new ProcessRunId("3ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final ProcessRunId OTHER_RUN_ID = new ProcessRunId("4ef19fe9-65b7-443f-818f-8a61e1b70cd7");
    private static final byte[] DEFINITION = """
        <bpm code="approval">
          <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
          <var name="result" dataType="java.lang.String" inOutType="return"/>
          <var name="cursor" dataType="java.lang.Integer" inOutType="inner"/>
          <start id="start" g="0,0,32,32"><transition to="end"/></start>
          <end id="end" g="80,0,32,32"/>
        </bpm>
        """
        .getBytes(StandardCharsets.UTF_8);
    private static final byte[] BPMN_DEFINITION = """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="urn:compileflow:test">
          <process id="approval" isExecutable="true">
            <startEvent id="start"/>
            <endEvent id="end"/>
            <sequenceFlow id="to_end" sourceRef="start" targetRef="end"/>
          </process>
        </definitions>
        """
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void exactVersionStartBypassesAliasAdmission() {
        CapturingStore store = new CapturingStore();
        AliasAdmission aliases = new AliasAdmission(alias -> {
            throw new AssertionError("Exact Version Start must not resolve an Alias");
        });
        DefaultDurableProcessEngine engine = engine(store, aliases);

        ProcessRun run = engine.start(RUN_ID, VERSION, Map.of());

        assertThat(run.runId()).isEqualTo(RUN_ID);
        assertThat(run.processVersion()).isEqualTo(VERSION);
        assertThat(store.started.get().rootProcess()).isEqualTo(PROCESS);
        assertThat(store.started.get().recoveryProcessIds()).containsExactly(PROCESS.processId());
        assertThat(store.started.get().continuation().bytes()).isNotEmpty();
        assertThat(store.started.get().admissionFact()).isNull();
    }

    @Test
    void callerAllocatedRunIdIsPersistedAsTheOccurrenceIdentity() {
        CapturingStore store = new CapturingStore();
        DefaultDurableProcessEngine engine = engine(store, null);

        ProcessRun run = engine.start(OTHER_RUN_ID, VERSION, Map.of());

        assertThat(run.runId()).isEqualTo(OTHER_RUN_ID);
        assertThat(store.started.get().runId()).isEqualTo(OTHER_RUN_ID);
    }

    @Test
    void explicitStartUsesTheBoundLocalModelType() {
        CapturingStore store = new CapturingStore(false);
        ProcessDefinition.Inline requested =
                ProcessDefinition.inline("approval", new String(DEFINITION, StandardCharsets.UTF_8));
        DefaultDurableProcessEngine engine = engine(store, null, DurableVersionDefinitionSource.empty());

        ProcessRun run = engine.start(OTHER_RUN_ID, requested, Map.of("approved", true));

        assertThat(run.runId()).isEqualTo(OTHER_RUN_ID);
        assertThat(run.processVersion()).isNull();
        assertThat(store.process.get().modelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(store.process.get().definitionBytes()).isEqualTo(DEFINITION);
        assertThat(store.started.get().rootProcess().processVersion()).isNull();
    }

    @Test
    void startRejectsReturnInnerAndUndeclaredVariablesBeforeStoreMutation() {
        CapturingStore store = new CapturingStore();
        DefaultDurableProcessEngine engine = engine(store, null);

        assertThatThrownBy(() -> engine.start(RUN_ID, VERSION, Map.of("result", "forged")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Start input");
        assertThat(store.started.get()).isNull();

        assertThatThrownBy(() -> engine.start(RUN_ID, VERSION, Map.of("cursor", 7)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Start input");
        assertThat(store.started.get()).isNull();

        assertThatThrownBy(() -> engine.start(RUN_ID, VERSION, Map.of("unknown", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Undeclared");
        assertThat(store.started.get()).isNull();
    }

    @Test
    void duplicateCallerRunIdIsRejectedBeforeMutableAliasResolution() {
        CapturingStore store = new CapturingStore();
        DefaultDurableProcessEngine engine = engine(store, null);
        engine.start(RUN_ID, VERSION, Map.of());
        AtomicInteger resolutions = new AtomicInteger();
        AliasAdmission aliases = new AliasAdmission(alias -> {
            resolutions.incrementAndGet();
            return Optional.of(new DurableAliasState(alias, VERSION, null, 0, 7));
        });
        DefaultDurableProcessEngine aliasService = engine(store, aliases);

        assertThatThrownBy(() -> aliasService.start(RUN_ID, ALIAS, Map.of()))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.RUN_ALREADY_EXISTS));
        assertThat(resolutions).hasValue(0);
    }

    @Test
    void startRejectsMismatchedStoreIdentity() {
        CapturingStore store = new CapturingStore();
        store.startResult.set(store.view(OTHER_RUN_ID, PROCESS));
        DefaultDurableProcessEngine engine = engine(store, null);

        assertThatThrownBy(() -> engine.start(RUN_ID, VERSION, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Run");
    }

    @Test
    void aliasStartResolvesOnceAndPersistsOnlyTheSelectedVersion() {
        CapturingStore store = new CapturingStore();
        AtomicInteger resolutions = new AtomicInteger();
        AliasRoutingOptions routing = new AliasRoutingOptions("customer-42");
        AliasAdmission aliases = new AliasAdmission(alias -> {
            resolutions.incrementAndGet();
            assertThat(alias).isEqualTo(ALIAS);
            return Optional.of(new DurableAliasState(alias, VERSION, null, 0, 7));
        });
        DefaultDurableProcessEngine engine = engine(store, aliases);

        ProcessRun run = engine.start(RUN_ID, ALIAS, Map.of(), routing);

        assertThat(resolutions).hasValue(1);
        assertThat(run.processVersion()).isEqualTo(VERSION);
        assertThat(store.started.get().rootProcess()).isEqualTo(PROCESS);
        assertThat(new DurableKernelJsonCodec().decode(store.started.get().admissionFact().payload()))
            .containsEntry("alias", "prod")
            .containsEntry("aliasRevision", 7)
            .containsEntry("aliasTarget", "STABLE")
            .containsEntry("aliasSelectionReason", "STABLE_ONLY")
            .containsEntry("processVersion", "v1");
    }

    @Test
    void missingRoutingKeyUsesRunIdAsTheCohort() {
        ProcessRef.Version candidateVersion = ProcessRef.version("sales", "approval", "v2");
        DurableAliasState state = new DurableAliasState(ALIAS, VERSION, candidateVersion, 5_000, 7);
        ProcessAliasRoute route = new ProcessAliasRoute(ALIAS, VERSION, candidateVersion, 5_000, null, 7);
        ProcessAliasTarget expectedTarget = DeterministicAliasSelector.select(route, RUN_ID.value());
        ProcessRef.Version expectedVersion = route.versionFor(expectedTarget);
        DurableStore.RunProcess expectedProcess = new DurableStore.RunProcess(PROCESS_ID, expectedVersion.namespace(),
                expectedVersion.code(), expectedVersion);
        CapturingStore store = new CapturingStore();
        store.startResult.set(store.view(RUN_ID, expectedProcess));
        DurableVersionDefinitionSource versions =
                version -> Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM,
                                ProcessDefinition.inline(version.code(), new String(DEFINITION, StandardCharsets.UTF_8))));
        DefaultDurableProcessEngine engine = engine(store, new AliasAdmission(alias -> Optional.of(state)), versions);

        ProcessRun run = engine.start(RUN_ID, ALIAS, Map.of());

        assertThat(run.processVersion()).isEqualTo(expectedVersion);
        assertThat(store.started.get().rootProcess().processVersion()).isEqualTo(expectedVersion);
        assertThat(new DurableKernelJsonCodec().decode(store.started.get().admissionFact().payload()))
            .containsEntry("aliasTarget", expectedTarget.name());
    }

    @Test
    void laterAliasChangeCannotChangeAnExistingRunVersion() {
        CapturingStore store = new CapturingStore();
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<DurableAliasState> state =
                new AtomicReference<>(new DurableAliasState(ALIAS, VERSION, null, 0, 7));
        AliasAdmission aliases = new AliasAdmission(alias -> {
            resolutions.incrementAndGet();
            return Optional.of(state.get());
        });
        DefaultDurableProcessEngine engine = engine(store, aliases);

        ProcessRun started = engine.start(RUN_ID, ALIAS, Map.of());
        state.set(new DurableAliasState(ALIAS, ProcessRef.version("sales", "approval", "v2"), null, 0, 8));
        ProcessRun recoveredView = engine.getRun(started.runId()).orElseThrow();

        assertThat(resolutions).hasValue(1);
        assertThat(started.processVersion()).isEqualTo(VERSION);
        assertThat(recoveredView.processVersion()).isEqualTo(VERSION);
        assertThat(store.started.get().rootProcess()).isEqualTo(PROCESS);
    }

    @Test
    void bpmnModelTypeIsPreservedByTheSharedBackend() {
        CapturingStore store = new CapturingStore(false);
        DurableVersionDefinitionSource bpmnSource =
                version -> Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.BPMN,
                                ProcessDefinition.inline(version.code(),
                                        new String(BPMN_DEFINITION, StandardCharsets.UTF_8))));

        ProcessRun run = engine(store, null, bpmnSource).start(RUN_ID, VERSION, Map.of());

        assertThat(run.processVersion()).isEqualTo(VERSION);
        assertThat(store.process.get().modelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(store.process.get().definitionBytes()).isEqualTo(BPMN_DEFINITION);
        assertThat(store.started.get().rootProcess().processVersion()).isEqualTo(VERSION);
    }

    @Test
    void aliasAdmissionRejectsAStateForAnotherAlias() {
        CapturingStore store = new CapturingStore();
        AliasAdmission aliases = new AliasAdmission(alias -> Optional.of(
                new DurableAliasState(ProcessRef.alias("other", "approval", "prod"),
                        ProcessRef.version("other", "approval", "v1"), null, 0, 1)));
        DefaultDurableProcessEngine engine = engine(store, aliases);

        assertThatThrownBy(() -> engine.start(RUN_ID, ALIAS, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Alias");
        assertThat(store.started.get()).isNull();
    }

    @Test
    void completeWaitValidatesTypedProcessStateBeforeStoreMutation() {
        CapturingStore store = new CapturingStore();
        DefaultDurableProcessEngine engine = engine(store, null);
        WaitToken token = new WaitToken("opaque-wait-token");

        engine.completeWait(token, Map.of("approved", true));

        assertThat(store.completion.get()).isNotNull();
        assertThat(store.completion.get().runId()).isEqualTo(RUN_ID);
        assertThat(store.completion.get().tokenDigest()).isEqualTo(DurableDigests.sha256(token.value()));
        assertThat(new String(store.completion.get().result().payload(), StandardCharsets.UTF_8))
            .isEqualTo("{\"approved\":true}");
        assertThatThrownBy(() -> engine.completeWait(token, Map.of("undeclared", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("undeclared");
        assertThat(store.completionCount.get()).isOne();
    }

    @Test
    void getRunResultDistinguishesMissingActiveAndSucceededRuns() {
        CapturingStore store = new CapturingStore();
        DefaultDurableProcessEngine engine = engine(store, null);

        assertThat(engine.getRunResult(RUN_ID)).isInstanceOf(ProcessRunResult.NotFound.class);

        store.result.set(
                new DurableStore.RunResultProjection(RUN_ID, PROCESS, ProcessRunStatus.RUNNABLE, null, null, null, null));
        assertThat(engine.getRunResult(RUN_ID))
            .isEqualTo(
                    new ProcessRunResult.NotCompleted(RUN_ID, VERSION.namespace(), "approval", VERSION,
                            ProcessRunStatus.RUNNABLE));

        store.result.set(
                new DurableStore.RunResultProjection(RUN_ID, PROCESS, ProcessRunStatus.SUCCEEDED,
                        new DurableStore.Envelope("{\"approved\":true}".getBytes(StandardCharsets.UTF_8)), null, null,
                        NOW));
        assertThat(engine.getRunResult(RUN_ID))
            .isInstanceOfSatisfying(ProcessRunResult.Succeeded.class, succeeded -> assertThat(succeeded.output())
                .containsEntry("approved", true));
    }

    private DefaultDurableProcessEngine engine(CapturingStore store, AliasAdmission aliasAdmission) {
        DurableVersionDefinitionSource versionSource =
                version -> version.equals(VERSION)
                ? Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM,
                                ProcessDefinition.inline(version.code(), new String(DEFINITION, StandardCharsets.UTF_8))))
                : Optional.empty();
        return engine(store, aliasAdmission, versionSource);
    }

    private DefaultDurableProcessEngine engine(CapturingStore store, AliasAdmission aliasAdmission,
            DurableVersionDefinitionSource versionSource) {
        ProcessEngineConfig config =
                ProcessEngineConfig
            .tbbpmBuilder()
            .classLoader(getClass().getClassLoader())
            .discoverPlugins(false)
            .build();
        DurableProcessRuntimeManager processManager = new DurableProcessRuntimeManager(store.proxy(),
                new InMemoryDurableProcessRuntimeCache(),
                new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults()), config, versionSource);
        return new DefaultDurableProcessEngine(store.proxy(), processManager, aliasAdmission);
    }

    private static final class CapturingStore {
        private final AtomicReference<DurableStore.NewRun> started = new AtomicReference<>();
        private final AtomicReference<ProcessRun> startResult = new AtomicReference<>();
        private final AtomicReference<DurableStore.WaitCompletion> completion = new AtomicReference<>();
        private final AtomicInteger completionCount = new AtomicInteger();
        private final AtomicReference<DurableStore.StoredProcess> process = new AtomicReference<>();
        private final AtomicReference<DurableStore.RunResultProjection> result = new AtomicReference<>();
        private final DurableStore proxy;

        private CapturingStore() {
            this(true);
        }

        private CapturingStore(boolean registered) {
            if (registered) {
                process.set(
                        new DurableStore.StoredProcess(PROCESS_ID, VERSION.code(), ProcessModelType.TBBPM, DEFINITION,
                                ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, VERSION.code(), DEFINITION), NOW));
            }
            startResult.set(view());
            InvocationHandler handler =
                    (instance, method, arguments) -> switch (method.getName()) {
                case "registerProcess" -> register((DurableStore.ProcessRegistration) arguments[0]);
                case "findProcess" -> Optional.ofNullable(process.get());
                case "findRun" -> Optional
                    .ofNullable(started.get())
                    .filter(value -> value.runId().equals(arguments[0]))
                    .map(value -> view(value.runId(), value.rootProcess()));
                case "findRunResult" -> Optional.ofNullable(result.get());
                case "findWaitTarget" -> Optional.of(
                        new DurableStore.WaitTarget(RUN_ID, PROCESS, PROCESS_ID, (String) arguments[0]));
                case "start" -> start((DurableStore.NewRun) arguments[0]);
                case "completeWait" -> completeWait((DurableStore.WaitCompletion) arguments[0]);
                case "toString" -> "CapturingDurableStore";
                default -> throw new AssertionError("Unexpected Store call: " + method.getName());
            };
            proxy = (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(),
                    new Class<?>[] {DurableStore.class}, handler);
        }

        private DurableStore proxy() {
            return proxy;
        }

        private ProcessRun start(DurableStore.NewRun command) {
            started.set(command);
            ProcessRun configured = startResult.get();
            if (configured.runId().equals(RUN_ID) && !command.runId().equals(RUN_ID)) {
                return view(command.runId(), command.rootProcess());
            }
            return configured;
        }

        private ProcessRun completeWait(DurableStore.WaitCompletion value) {
            completion.set(value);
            completionCount.incrementAndGet();
            return view();
        }

        private DurableStore.StoredProcess register(DurableStore.ProcessRegistration registration) {
            DurableStore.StoredProcess candidate = new DurableStore.StoredProcess(registration.processId(),
                    registration.processCode(), registration.modelType(), registration.definitionBytes(),
                    registration.definitionDigest(), NOW);
            process.compareAndSet(null, candidate);
            return process.get();
        }

        private ProcessRun view() {
            return view(RUN_ID, PROCESS);
        }

        private ProcessRun view(ProcessRunId runId, DurableStore.RunProcess process) {
            return new ProcessRun(runId, process.namespace(), process.processCode(), process.processVersion(),
                    ProcessRunStatus.RUNNABLE, new ProcessRunControl(ProcessRunControlState.ACTIVE, 0),
                    ActiveWorkSummary.NONE, NOW, null, null, null, null, NOW, NOW, null);
        }
    }
}
