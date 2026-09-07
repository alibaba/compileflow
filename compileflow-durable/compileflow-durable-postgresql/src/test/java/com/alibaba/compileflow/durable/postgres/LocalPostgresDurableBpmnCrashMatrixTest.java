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
import com.alibaba.compileflow.durable.api.effect.EffectReconcileOutcome;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunResult;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.DefaultDurableProcessEngine;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.WaitTokenIssuer;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasState;
import com.alibaba.compileflow.durable.spi.admission.DurableAliasStateSource;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.store.DurableDigests;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * BPMN recovery proof across disposable Runtime instances and a real PostgreSQL authority.
 *
 * @author yusu
 */
@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_POSTGRES_URL", matches = ".+")
class LocalPostgresDurableBpmnCrashMatrixTest {
    private static final String NAMESPACE = "bpmn-crash";
    private static final Duration LEASE = Duration.ofSeconds(5);
    private static final Duration BACKOFF = Duration.ofMillis(20);
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();
    private static final AtomicInteger LEFT_CALLS = new AtomicInteger();
    private static final AtomicInteger RIGHT_CALLS = new AtomicInteger();
    private static final AtomicInteger EFFECT_DISPATCHES = new AtomicInteger();
    private static final AtomicInteger EFFECT_RECONCILES = new AtomicInteger();
    private static final AtomicReference<String> EFFECT_ID = new AtomicReference<>();
    private static final CopyOnWriteArrayList<String> ITERATION_CALLS = new CopyOnWriteArrayList<>();
    private final Map<ProcessRef.Version, DurableVersionDefinitionSource.VersionDefinition> definitions =
            new ConcurrentHashMap<>();
    private DataSource dataSource;
    private DurableStore store;
    private final List<DefaultDurableProcessEngine> engines = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void closeEngines() {
        engines.forEach(DefaultDurableProcessEngine::close);
    }

    @BeforeEach
    void resetDatabaseAndActions() {
        dataSource = new DriverManagerDataSource(System.getenv("COMPILEFLOW_DURABLE_POSTGRES_URL"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_USER", "postgres"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_PASSWORD", "postgres"));
        PostgresDurableStoreContractTest.migrate(dataSource);
        store = new PostgresDurableStore(dataSource);
        LEFT_CALLS.set(0);
        RIGHT_CALLS.set(0);
        EFFECT_DISPATCHES.set(0);
        EFFECT_RECONCILES.set(0);
        EFFECT_ID.set(null);
        ITERATION_CALLS.clear();
    }

    @Test
    void effectResponseLossBecomesUnknownThenReconcilesToOneContinuation() throws Exception {
        ProcessRef.Version process = process("effect");
        RuntimeContext first = runtime();
        register(first, process, effectProcess(process.code()));
        ProcessRun run = first.engine().start(ProcessRunId.random(), process, Map.of("request", "charge-42"));

        assertThat(runTurn(first, "unused-effect-token", 10_000)).isTrue();
        assertThat(runEffect(first)).isTrue();
        assertThat(EFFECT_DISPATCHES).hasValue(1);
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.WAITING);

        RuntimeContext recovered = restart(process);
        assertThat(runEffect(recovered)).isTrue();
        runUntilTerminal(recovered, run.runId());

        assertThat(EFFECT_DISPATCHES).hasValue(1);
        assertThat(EFFECT_RECONCILES).hasValue(1);
        assertThat(succeededOutput(recovered, run.runId())).containsEntry("result", "confirmed-charge");
    }

    @Test
    void messageCatchSurvivesRuntimeLossAndResumesExactlyOnce() throws Exception {
        ProcessRef.Version process = process("message");
        String token = "bpmn-message-crash-token";
        RuntimeContext first = runtime();
        register(first, process, messageCatchProcess(process.code()));
        ProcessRun run = first.engine().start(ProcessRunId.random(), process, Map.of());

        assertThat(runTurn(first, token, 10_000)).isTrue();
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.WAITING);

        RuntimeContext recovered = restart(process);
        recovered.engine().completeWait(new WaitToken(token), Map.of());
        runUntilTerminal(recovered, run.runId());

        assertThat(recovered.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.SUCCEEDED);
        assertThat(recovered.engine().completeWait(new WaitToken(token), Map.of()).status())
            .isEqualTo(ProcessRunStatus.SUCCEEDED);
    }

    @Test
    void timerCatchSurvivesRuntimeLossAndResolvesOnceWhenDue() throws Exception {
        ProcessRef.Version process = process("timer");
        RuntimeContext first = runtime();
        register(first, process, timerCatchProcess(process.code()));
        ProcessRun run = first.engine().start(ProcessRunId.random(), process, Map.of());

        assertThat(runTurn(first, "unused-timer-token", 10_000)).isTrue();
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.WAITING);

        RuntimeContext recovered = restart(process);
        runUntilTerminal(recovered, run.runId());

        assertThat(recovered.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.SUCCEEDED);
    }

    @Test
    void callActivityChildAndParentContinuationRecoverAsOneChain() throws Exception {
        ProcessRef.Version child = process("child");
        ProcessRef.Version parent = process("parent");
        RuntimeContext first = runtime();
        String token = "bpmn-child-crash-token";
        register(first, child, messageCatchProcess(child.code()));
        register(first, parent, callActivityProcess(parent.code(), child.code()), Map.of("child", child));
        ProcessRun run = first
            .engine()
            .start(ProcessRunId.random(), ProcessRef.alias(parent.namespace(), parent.code(), "prod"), Map.of());

        assertThat(runTurn(first, token, 10_000)).isTrue();
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.WAITING);

        RuntimeContext recovered = restart(parent, child);
        recovered.engine().completeWait(new WaitToken(token), Map.of());
        runUntilTerminal(recovered, run.runId());

        assertThat(recovered.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.SUCCEEDED);
    }

    @Test
    void parallelBranchesResumeFromPartialProgressAndConvergeExactly() throws Exception {
        ProcessRef.Version process = process("parallel");
        RuntimeContext first = runtime();
        register(first, process, parallelProcess(process.code()));
        ProcessRun run = first.engine().start(ProcessRunId.random(), process, Map.of());

        assertThat(runTurn(first, "unused-parallel-token", 2)).isTrue();
        assertThat(LEFT_CALLS.get() + RIGHT_CALLS.get()).isZero();
        assertThat(runTurn(first, "unused-parallel-token", 2)).isTrue();
        assertThat(LEFT_CALLS.get() + RIGHT_CALLS.get()).isEqualTo(1);
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.RUNNABLE);

        RuntimeContext recovered = restart(process);
        runUntilTerminal(recovered, run.runId());

        assertThat(LEFT_CALLS).hasValue(1);
        assertThat(RIGHT_CALLS).hasValue(1);
        assertThat(succeededOutput(recovered, run.runId()))
            .containsEntry("leftResult", "left")
            .containsEntry("rightResult", "right");
    }

    @Test
    void parallelMultiInstanceRecoversPartialIterationsWithStableOrderedMerge() throws Exception {
        ProcessRef.Version process = process("parallel-mi");
        RuntimeContext first = runtime();
        register(first, process, parallelMultiInstanceProcess(process.code()));
        List<String> items = List.of("c", "a", "b");
        ProcessRun run = first.engine().start(ProcessRunId.random(), process, Map.of("items", items));

        assertThat(runTurn(first, "unused-mi-token", 2)).isTrue();
        assertThat(ITERATION_CALLS).isEmpty();
        assertThat(runTurn(first, "unused-mi-token", 1)).isTrue();
        assertThat(ITERATION_CALLS).hasSize(1);
        assertThat(first.engine().getRun(run.runId()))
            .get()
            .extracting(ProcessRun::status)
            .isEqualTo(ProcessRunStatus.RUNNABLE);

        RuntimeContext recovered = restart(process);
        runUntilTerminal(recovered, run.runId());

        assertThat(ITERATION_CALLS).containsExactlyInAnyOrderElementsOf(items);
        assertThat(succeededOutput(recovered, run.runId())).containsEntry("results",
                List.of("done-c", "done-a", "done-b"));
    }

    private RuntimeContext runtime() {
        return runtime(com.alibaba.compileflow.engine.config.ProcessRuntimeMode.COMPILED, version -> java.util.Optional.ofNullable(definitions.get(
                version)));
    }

    private RuntimeContext runtime(com.alibaba.compileflow.engine.config.ProcessRuntimeMode mode,
            DurableVersionDefinitionSource versions) {
        DurableAliasStateSource aliases =
                alias -> java.util.Optional.of(
                        new DurableAliasState(alias, ProcessRef.version(alias.namespace(), alias.code(), "v1"), null, 0,
                                1));
        var config = com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig
            .builder(store)
            .runtimeMode(mode)
            .versionDefinitionSource(versions)
            .aliasStateSource(aliases)
            .worker(
                    new com.alibaba.compileflow.durable.runtime.DurableProcessEngineConfig.Worker(false, null, LEASE,
                            Duration.ofMillis(100), Duration.ofSeconds(1), 10000, 32, 2, 8))
            .build();
        DefaultDurableProcessEngine engine =
                com.alibaba.compileflow.durable.runtime.DurableProcessEngineFactory.create(config);
        engines.add(engine);
        return new RuntimeContext(engine.getRuntimeCache(), engine.getProcessRuntimeManager(), engine);
    }

    @Test
    void mixedFrontendChildWaitRecoversWithoutAdmissionSourcesInBothRealizations() throws Exception {
        for (var mode : com.alibaba.compileflow.engine.config.ProcessRuntimeMode.values()) {
            for (var parentType : ProcessModelType.values()) {
                ProcessRef.Version parent = process("mixed-parent-" + mode + "-" + parentType);
                ProcessRef.Version child = process("mixed-child-" + mode + "-" + parentType);
                ProcessModelType childType =
                        parentType == ProcessModelType.TBBPM ? ProcessModelType.BPMN : ProcessModelType.TBBPM;
                String parentXml = parentType == ProcessModelType.BPMN
                        ? callActivityProcess(parent.code(), child.code())
                        : """
                          <bpm code="%s">
                            <start id="start" g="0,0,32,32"><transition to="child"/></start>
                            <bpmCall id="child" code="%s" version="v1" g="40,0,80,32"><transition to="end"/></bpmCall>
                            <end id="end" g="140,0,32,32"/>
                          </bpm>
                          """
                    .formatted(parent.code(), child.code());
                String childXml = childType == ProcessModelType.BPMN
                        ? messageCatchProcess(child.code())
                        : """
                          <bpm code="%s">
                            <start id="start" g="0,0,32,32"><transition to="approval"/></start>
                            <waitEventTask id="approval" event="approved" g="60,0,100,40"><transition to="end"/></waitEventTask>
                            <end id="end" g="200,0,32,32"/>
                          </bpm>
                          """
                    .formatted(child.code());
                definitions.put(child,
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessDefinition.inline(childType,
                                child.code(), childXml)));
                definitions.put(parent,
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessDefinition.inline(parentType,
                                        parent.code(), parentXml), Map.of("child", child)));
                RuntimeContext first = runtime(mode, version -> java.util.Optional.ofNullable(definitions.get(version)));
                ProcessRun run = first.engine().start(ProcessRunId.random(), parent, Map.of());
                String token = "mixed-wait-" + mode + "-" + parentType;
                assertThat(runTurn(first, token, 10000)).isTrue();
                assertThat(first.engine().getRun(run.runId()).orElseThrow().status()).isEqualTo(
                        ProcessRunStatus.WAITING);
                first.engine().close();

                RuntimeContext recovered = runtime(mode, version -> {
                    throw new AssertionError("Recovery must not consult Version admission");
                });
                recovered.engine().completeWait(new WaitToken(token), Map.of());
                new com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker(store,
                        recovered.manager(), recovered.cache(), BACKOFF)
                    .loadDemanded(100);
                runUntilTerminal(recovered, run.runId());
                assertThat(recovered.engine().getRunResult(run.runId())).isInstanceOf(ProcessRunResult.Succeeded.class);
                recovered.engine().close();
            }
        }
    }

    private RuntimeContext restart(ProcessRef.Version... processes) {
        RuntimeContext recovered = runtime();
        for (ProcessRef.Version process : processes) {
            var stored = recovered.manager().register(process);
            assertThat(stored.processVersion()).isEqualTo(process);
            assertThat(recovered.manager().requireRuntime(stored.processId()).processId()).isEqualTo(stored.processId());
        }
        return recovered;
    }

    private void register(RuntimeContext runtime, ProcessRef.Version process, String xml) {
        register(runtime, process, xml, Map.of());
    }

    private void register(RuntimeContext runtime, ProcessRef.Version process, String xml,
            Map<String, ProcessRef.Version> callBindings) {
        definitions.put(process,
                new DurableVersionDefinitionSource.VersionDefinition(ProcessDefinition.inline(ProcessModelType.BPMN,
                                process.code(), xml), callBindings));
        var stored = runtime.manager().register(process);
        assertThat(stored.processVersion()).isEqualTo(process);
        assertThat(runtime.manager().requireRuntime(stored.processId()).processId()).isEqualTo(stored.processId());
    }

    private boolean runTurn(RuntimeContext runtime, String waitToken, int maxSteps) {
        String workerId = "bpmn-turn-" + WORKER_SEQUENCE.incrementAndGet();
        WaitTokenIssuer tokens = () -> new WaitTokenIssuer.IssuedWaitToken(waitToken, DurableDigests.sha256(waitToken));
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, LEASE)) {
            return new DurableTurnWorker(store, runtime.manager(), runtime.cache(), actions(),
                    DurableWaitDescriptionProvider.defaults(), tokens,
                    new DurableTurnWorkerOptions(workerId, BACKOFF, maxSteps, 8), renewer,
                    new com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics())
                .runOnce();
        }
    }

    private boolean runEffect(RuntimeContext runtime) {
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, LEASE)) {
            return new DurableEffectWorker(store, runtime.cache(), actions(),
                    new DurableEffectWorkerOptions("bpmn-effect-" + WORKER_SEQUENCE.incrementAndGet(), BACKOFF), renewer)
                .runOnce();
        }
    }

    private void runUntilTerminal(RuntimeContext runtime, ProcessRunId runId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            ProcessRunStatus status = runtime.engine().getRun(runId).orElseThrow().status();
            if (status.isTerminal()) {
                assertThat(status).isEqualTo(ProcessRunStatus.SUCCEEDED);
                return;
            }
            store.resolveDueWaits(100);
            if (!runTurn(runtime, "unused-recovery-token-" + attempt, 10_000)) {
                Thread.sleep(10);
            }
        }
        assertThat(runtime.engine().getRun(runId).orElseThrow().status())
            .as("Run must reach a terminal state after recovery")
            .isEqualTo(ProcessRunStatus.SUCCEEDED);
    }

    private static Map<String, Object> succeededOutput(RuntimeContext runtime, ProcessRunId runId) {
        assertThat(runtime.engine().getRunResult(runId)).isInstanceOf(ProcessRunResult.Succeeded.class);
        return ((ProcessRunResult.Succeeded) runtime.engine().getRunResult(runId)).output();
    }

    private DurableActionInvoker actions() {
        return new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                getClass().getClassLoader());
    }

    private static ProcessRef.Version process(String code) {
        return ProcessRef.version(NAMESPACE, "crash." + code, "v1");
    }

    private static String messageCatchProcess(String code) {
        return definitions(code, "<message id=\"message_order\" name=\"order-received\"/>",
                """
            <startEvent id="start"/>
            <intermediateCatchEvent id="message">
              <messageEventDefinition messageRef="message_order"/>
            </intermediateCatchEvent>
            <endEvent id="end"/>
            <sequenceFlow id="to_message" sourceRef="start" targetRef="message"/>
            <sequenceFlow id="to_end" sourceRef="message" targetRef="end"/>
            """);
    }

    private static String timerCatchProcess(String code) {
        return definitions(code, "",
                """
            <startEvent id="start"/>
            <intermediateCatchEvent id="timer">
              <timerEventDefinition><timeDuration>PT0S</timeDuration></timerEventDefinition>
            </intermediateCatchEvent>
            <endEvent id="end"/>
            <sequenceFlow id="to_timer" sourceRef="start" targetRef="timer"/>
            <sequenceFlow id="to_end" sourceRef="timer" targetRef="end"/>
            """);
    }

    private static String effectProcess(String code) {
        return definitions(code, "",
                """
            <extensionElements>
              <cf:var name="request" dataType="java.lang.String" inOutType="param"/>
              <cf:var name="result" dataType="java.lang.String" inOutType="return"/>
            </extensionElements>
            <startEvent id="start"/>
            <serviceTask id="effect">
              <extensionElements>
                <cf:action type="java" execution="effect" class="%s" method="dispatchEffect">
                    <cf:input target="request" dataType="java.lang.String"
                            source="request"/>
                    <cf:input target="effectId" dataType="java.lang.String"
                            source="__cf_effect_id"/>
                    <cf:output dataType="java.lang.String"
                            target="result"/>

                  <cf:effectPolicy recovery="reconcile" maxAttempts="2" maxReconcileAttempts="2"
                                   recoveryDelay="PT0.001S" maxRecoveryDuration="PT1M">
                    <cf:reconcileAction type="java" class="%s" method="reconcileEffect">
                        <cf:input target="effectId" dataType="java.lang.String"
                                source="__cf_effect_id"/>

                    </cf:reconcileAction>
                  </cf:effectPolicy>
                </cf:action>
              </extensionElements>
            </serviceTask>
            <endEvent id="end"/>
            <sequenceFlow id="to_effect" sourceRef="start" targetRef="effect"/>
            <sequenceFlow id="to_end" sourceRef="effect" targetRef="end"/>
            """
                    .formatted(CrashMatrixActions.class.getName(), CrashMatrixActions.class.getName()));
    }

    private static String callActivityProcess(String code, String childCode) {
        return definitions(code, "",
                """
            <startEvent id="start"/>
            <callActivity id="child" calledElement="%s" cf:version="v1"/>
            <endEvent id="end"/>
            <sequenceFlow id="to_child" sourceRef="start" targetRef="child"/>
            <sequenceFlow id="to_end" sourceRef="child" targetRef="end"/>
            """
                    .formatted(childCode));
    }

    private static String parallelProcess(String code) {
        return definitions(code, "",
                """
            <extensionElements>
              <cf:var name="leftResult" dataType="java.lang.String" inOutType="return"/>
              <cf:var name="rightResult" dataType="java.lang.String" inOutType="return"/>
            </extensionElements>
            <startEvent id="start"/>
            <parallelGateway id="fork"/>
            %s
            %s
            <parallelGateway id="join"/>
            <endEvent id="end"/>
            <sequenceFlow id="to_fork" sourceRef="start" targetRef="fork"/>
            <sequenceFlow id="to_left" sourceRef="fork" targetRef="left"/>
            <sequenceFlow id="to_right" sourceRef="fork" targetRef="right"/>
            <sequenceFlow id="left_join" sourceRef="left" targetRef="join"/>
            <sequenceFlow id="right_join" sourceRef="right" targetRef="join"/>
            <sequenceFlow id="to_end" sourceRef="join" targetRef="end"/>
            """
                    .formatted(replayableTask("left", "left", "leftResult"),
                            replayableTask("right", "right", "rightResult")));
    }

    private static String replayableTask(String id, String method, String output) {
        return """
            <serviceTask id="%s">
              <extensionElements>
                <cf:action type="java" execution="replayable" class="%s" method="%s">
                    <cf:output dataType="java.lang.String"
                            target="%s"/>

                </cf:action>
              </extensionElements>
            </serviceTask>
            """
            .formatted(id, CrashMatrixActions.class.getName(), method, output);
    }

    private static String parallelMultiInstanceProcess(String code) {
        return definitions(code, "",
                """
            <extensionElements>
              <cf:var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <cf:var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <cf:var name="result" dataType="java.lang.String" inOutType="inner"/>
            </extensionElements>
            <startEvent id="start"/>
            <serviceTask id="map">
              <extensionElements>
                <cf:action type="java" execution="replayable" class="%s" method="mapItem">
                    <cf:input target="item" dataType="java.lang.String"
                            source="item"/>
                    <cf:output dataType="java.lang.String"
                            target="result"/>

                </cf:action>
              </extensionElements>
              <multiInstanceLoopCharacteristics isSequential="false" cf:collection="items"
                  cf:item="item" cf:itemType="java.lang.String" cf:index="index"
                  cf:target="results" cf:source="result"/>
            </serviceTask>
            <endEvent id="end"/>
            <sequenceFlow id="to_map" sourceRef="start" targetRef="map"/>
            <sequenceFlow id="to_end" sourceRef="map" targetRef="end"/>
            """
                    .formatted(CrashMatrixActions.class.getName()));
    }

    private static String definitions(String code, String rootElements, String processBody) {
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="urn:compileflow:bpmn-crash">
              %s
              <process id="%s" isExecutable="true">
                %s
              </process>
            </definitions>
            """
            .formatted(rootElements, code, processBody);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record RuntimeContext(com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache cache,
            DurableProcessRuntimeManager manager, DefaultDurableProcessEngine engine) {}

    public static final class CrashMatrixActions {
        public String left() {
            LEFT_CALLS.incrementAndGet();
            return "left";
        }

        public String right() {
            RIGHT_CALLS.incrementAndGet();
            return "right";
        }

        public String mapItem(String item) {
            ITERATION_CALLS.add(item);
            return "done-" + item;
        }

        public String dispatchEffect(String request, String effectId) {
            assertThat(effectId).isNotBlank();
            EFFECT_ID.set(effectId);
            EFFECT_DISPATCHES.incrementAndGet();
            throw new IllegalStateException("simulated response loss after " + request);
        }

        public EffectReconcileOutcome<String> reconcileEffect(String effectId) {
            assertThat(effectId).isEqualTo(EFFECT_ID.get());
            EFFECT_RECONCILES.incrementAndGet();
            return EffectReconcileOutcome.confirmed("confirmed-charge");
        }
    }
}
