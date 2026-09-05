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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessEngineRegistryTest {
    private static final String TBBPM_CODE = "registry.tbbpm";
    private static final String TBBPM_CHILD_CODE = "registry.tbbpm.child";
    private static final String BPMN_CODE = "registry.bpmn";

    private static void applyStableRoute(LocalRoutingState localRoutingState, String code, String version,
            long revision) {
        ProcessRef.Alias alias = ProcessRef.alias("default", code, "production");
        localRoutingState.applyAliasRoute(ProcessAliasRoute.stable(alias, version, revision));
    }

    private static ProcessEngineConfig configuration(ProcessModelType modelType) {
        return ProcessEngineConfig.builder(modelType).discoverPlugins(false).build();
    }

    private static ProcessModelType modelTypeOf(String code) {
        return TBBPM_CODE.equals(code) ? ProcessModelType.TBBPM : ProcessModelType.BPMN;
    }

    private static String tbbpmFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="registry.tbbpm" name="Registry TBBPM">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" name="End" g="180,50,32,32"/>
            </bpm>
            """;
    }

    private static String versionProbeFlow(String version) {
        return versionProbeFlow(TBBPM_CODE, version);
    }

    private static String versionProbeFlow(String code, String version) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Registry Version Probe">
                <var name="result" dataType="java.lang.String" inOutType="return"/>
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="probe"/>
                </start>
                <scriptTask id="probe" name="Version Probe" g="140,42,120,48">
                    <action type="script" language="version-probe">
                            <output dataType="java.lang.String"
                                 target="result"/>
                            <code>%s</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" name="End" g="320,50,32,32"/>
            </bpm>
            """
            .formatted(code, version);
    }

    private static String parentWithChildFlow(String childCode) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="registry.tbbpm" name="Registry Parent Probe">
                <var name="phase" dataType="java.lang.String" inOutType="inner"/>
                <var name="result" dataType="java.lang.String" inOutType="return"/>
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="pause"/>
                </start>
                <scriptTask id="pause" name="Pause" g="130,42,100,48">
                    <action type="script" language="version-probe">
                            <output dataType="java.lang.String"
                                 target="phase"/>
                            <code>v1</code>

                    </action>
                    <transition to="child"/>
                </scriptTask>
                <bpmCall id="child" name="Exact Child" g="270,42,100,48"
                        code="%s" version="child-v1">
                    <output source="result" target="result"/>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" name="End" g="420,50,32,32"/>
            </bpm>
            """
            .formatted(childCode);
    }

    private static ScriptExecutor versionProbe(CountDownLatch v1Started, CountDownLatch finishV1) {
        return TestScriptExecutors.of("version-probe", (source, context) -> {
            if ("v1".equals(source)) {
                v1Started.countDown();
                try {
                    if (!finishV1.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to finish the v1 execution");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Version probe execution was interrupted", interrupted);
                }
            }
            return source;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static ScriptExecutor countingVersionProbe(AtomicInteger executions) {
        return TestScriptExecutors.of("version-probe", (source, context) -> {
            executions.incrementAndGet();
            return source;
        });
    }

    private static String bpmnFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://www.compileflow.org/test">
                <process id="registry.bpmn" name="Registry BPMN" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
                    <endEvent id="end"/>
                </process>
            </definitions>
            """;
    }

    @Test
    void dispatchesPublishedVersionsToTheirFormatBoundEngines() {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        ProcessEngineConfig bpmn = configuration(ProcessModelType.BPMN);
        ProcessEngineConfigurationFactory factory = mock(ProcessEngineConfigurationFactory.class);
        when(factory.create(ProcessModelType.BPMN)).thenReturn(bpmn);

        try (ProcessEngineRegistry registry =
                new ProcessEngineRegistry(factory, tbbpm, new LocalRoutingState(), EnumSet.allOf(ProcessModelType.class))) {
            ProcessRef.Version tbbpmRef = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Version bpmnRef = ProcessRef.version("default", BPMN_CODE, "v1");
            registry.get(ProcessModelType.TBBPM).runtime().load(tbbpmRef,
                    ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            registry.get(ProcessModelType.BPMN).runtime().load(bpmnRef, ProcessDefinition.inline(BPMN_CODE, bpmnFlow()));

            ProcessResult<Map<String, Object>> tbbpmResult =
                    registry.execute(tbbpmRef, ref -> modelTypeOf(ref.code()), Map.of(),
                            ProcessExecutionOptions.defaults());
            ProcessResult<Map<String, Object>> bpmnResult =
                    registry.execute(bpmnRef, ref -> modelTypeOf(ref.code()), Map.of(),
                            ProcessExecutionOptions.defaults());

            assertThat(tbbpmResult.isSuccess()).isTrue();
            assertThat(bpmnResult.isSuccess()).isTrue();
            assertThat(tbbpmResult.getExecution().getProcessVersion()).isEqualTo(tbbpmRef);
            assertThat(bpmnResult.getExecution().getProcessVersion()).isEqualTo(bpmnRef);

            ProcessPreflightReport report = registry.preflight(ProcessModelType.BPMN,
                    ProcessDefinition.inline(BPMN_CODE, bpmnFlow()), ProcessPreflightOptions.fast());
            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
        }
    }

    @Test
    void admitsAnAliasBeforeFormatDispatch() {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        ProcessEngineConfigurationFactory factory = mock(ProcessEngineConfigurationFactory.class);

        try (ProcessEngineRegistry registry =
                new ProcessEngineRegistry(factory, tbbpm, localRoutingState, EnumSet.of(ProcessModelType.TBBPM))) {
            ProcessRef.Version version = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry.get(ProcessModelType.TBBPM).runtime().load(version,
                    ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v1", 1L);

            ProcessEngineRegistry.AliasExecution execution = registry.executeAliasWithSelection(alias,
                    ref -> ProcessModelType.TBBPM, Map.of(),
                    ProcessExecutionOptions.builder().aliasRouting(new AliasRoutingOptions("user-42")).build());
            ProcessResult<Map<String, Object>> result = execution.result();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExecution().getProcessVersion()).isEqualTo(version);
            assertThat(execution.selection().version()).isEqualTo(version);
            assertThat(execution.selection().target()).isEqualTo(ProcessAliasTarget.STABLE);
            assertThat(execution.selection().aliasRevision()).isEqualTo(1L);
        }
    }

    @Test
    void executesPersistedAliasSelectionWithoutReroutingToTheCurrentRevision() {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();

        try (ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class),
                tbbpm, localRoutingState, EnumSet.of(ProcessModelType.TBBPM))) {
            ProcessRef.Version v1 = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Version v2 = ProcessRef.version("default", TBBPM_CODE, "v2");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry.get(ProcessModelType.TBBPM).runtime().load(v1, ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            registry.get(ProcessModelType.TBBPM).runtime().load(v2, ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v2", 2L);

            ProcessResult<Map<String, Object>> result = registry.executeAliasSelection(alias,
                    new AliasSelection(ProcessRef.version("default", TBBPM_CODE, "v1"), ProcessAliasTarget.STABLE, 1L),
                    ignored -> ProcessModelType.TBBPM, Map.of(), ProcessExecutionOptions.defaults());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getExecution().getProcessVersion()).isEqualTo(v1);
        }
    }

    @Test
    void routeSwitchBeforeRuntimeHandoffRetriesTheLatestReadyVersion() {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        ProcessEngineConfigurationFactory factory = mock(ProcessEngineConfigurationFactory.class);

        try (ProcessEngineRegistry registry =
                new ProcessEngineRegistry(factory, tbbpm, localRoutingState, EnumSet.of(ProcessModelType.TBBPM))) {
            ProcessRef.Version v1 = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Version v2 = ProcessRef.version("default", TBBPM_CODE, "v2");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry.get(ProcessModelType.TBBPM).runtime().load(v1, ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            registry.get(ProcessModelType.TBBPM).runtime().load(v2, ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v1", 1L);
            AtomicInteger formatLookups = new AtomicInteger();

            ProcessResult<Map<String, Object>> result =
                    registry.execute(alias, ref -> {
                if (formatLookups.incrementAndGet() == 1) {
                    applyStableRoute(localRoutingState, TBBPM_CODE, "v2", 2L);
                    registry.get(ProcessModelType.TBBPM).runtime().unload(v1);
                }
                return ProcessModelType.TBBPM;
            }, Map.of(), ProcessExecutionOptions.defaults());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getExecution().getProcessVersion()).isEqualTo(v2);
            assertThat(formatLookups).hasValue(2);
        }
    }

    @Test
    void admittedGraphKeepsItsExactChildAfterRoutingAndRuntimeChanges() throws Exception {
        CountDownLatch v1Started = new CountDownLatch(1);
        CountDownLatch finishV1 = new CountDownLatch(1);
        ProcessEngineConfig tbbpm = ProcessEngineConfig
            .builder(ProcessModelType.TBBPM)
            .discoverPlugins(false)
            .scriptExecutor(versionProbe(v1Started, finishV1))
            .build();
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class),
                tbbpm, localRoutingState, EnumSet.of(ProcessModelType.TBBPM))) {
            ProcessRef.Version v1 = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Version v2 = ProcessRef.version("default", TBBPM_CODE, "v2");
            ProcessRef.Version childV1 = ProcessRef.version("default", TBBPM_CHILD_CODE, "child-v1");
            ProcessRef.Version childV2 = ProcessRef.version("default", TBBPM_CHILD_CODE, "child-v2");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry
                .get(ProcessModelType.TBBPM)
                .runtime()
                .load(childV1,
                        ProcessDefinition.inline(TBBPM_CHILD_CODE, versionProbeFlow(TBBPM_CHILD_CODE, "child-v1")));
            registry
                .get(ProcessModelType.TBBPM)
                .runtime()
                .load(childV2,
                        ProcessDefinition.inline(TBBPM_CHILD_CODE, versionProbeFlow(TBBPM_CHILD_CODE, "child-v2")));
            registry
                .get(ProcessModelType.TBBPM)
                .runtime()
                .load(v1, ProcessDefinition.inline(TBBPM_CODE, parentWithChildFlow(TBBPM_CHILD_CODE)));
            registry.get(ProcessModelType.TBBPM).runtime().load(v2,
                    ProcessDefinition.inline(TBBPM_CODE, versionProbeFlow("v2")));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v1", 1L);

            Future<ProcessResult<Map<String, Object>>> selectedV1 = executor.submit(() -> registry
                .get(ProcessModelType.TBBPM)
                .execute(alias, Map.of(), ProcessExecutionOptions.defaults()));
            assertThat(v1Started.await(10, TimeUnit.SECONDS)).isTrue();

            applyStableRoute(localRoutingState, TBBPM_CODE, "v2", 2L);
            registry.get(ProcessModelType.TBBPM).runtime().unload(v1);
            registry.get(ProcessModelType.TBBPM).runtime().unload(childV1);

            ProcessResult<Map<String, Object>> selectedV2 =
                    registry.execute(alias, ignored -> ProcessModelType.TBBPM, Map.of(),
                            ProcessExecutionOptions.defaults());
            assertThat(selectedV2.isSuccess()).as(String.valueOf(selectedV2.getError())).isTrue();
            assertThat(selectedV2.getOutput()).containsEntry("result", "v2");
            assertThat(selectedV2.getExecution().getProcessVersion()).isEqualTo(v2);

            finishV1.countDown();
            ProcessResult<Map<String, Object>> completedV1 = selectedV1.get(10, TimeUnit.SECONDS);
            assertThat(completedV1.isSuccess()).as(String.valueOf(completedV1.getError())).isTrue();
            assertThat(completedV1.getOutput()).containsEntry("result", "child-v1");
            assertThat(completedV1.getExecution().getProcessVersion()).isEqualTo(v1);

            ProcessResult<Map<String, Object>> releasedV1 =
                    registry.execute(v1, ignored -> ProcessModelType.TBBPM, Map.of(), ProcessExecutionOptions.defaults());
            assertThat(releasedV1.isFailure()).isTrue();
            assertThat(releasedV1.getError().getCode()).isEqualTo("CF_EXEC_012");
            ProcessResult<Map<String, Object>> releasedChild =
                    registry.execute(childV1, ignored -> ProcessModelType.TBBPM, Map.of(),
                            ProcessExecutionOptions.defaults());
            assertThat(releasedChild.isFailure()).isTrue();
            assertThat(releasedChild.getError().getCode()).isEqualTo("CF_EXEC_012");
        } finally {
            finishV1.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeWaitsForAnAdmittedRegistryExecution() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch finishExecution = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        ProcessEngineConfig tbbpm = ProcessEngineConfig
            .builder(ProcessModelType.TBBPM)
            .discoverPlugins(false)
            .scriptExecutor(versionProbe(executionStarted, finishExecution))
            .build();
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class), tbbpm,
                localRoutingState, EnumSet.of(ProcessModelType.TBBPM));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ProcessRef.Version v1 = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry.get(ProcessModelType.TBBPM).runtime().load(v1,
                    ProcessDefinition.inline(TBBPM_CODE, versionProbeFlow("v1")));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v1", 1L);

            Future<ProcessResult<Map<String, Object>>> execution = executor.submit(() -> registry.execute(alias,
                    ignored -> ProcessModelType.TBBPM, Map.of(), ProcessExecutionOptions.defaults()));
            assertThat(executionStarted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = executor.submit(() -> {
                closeStarted.countDown();
                registry.close();
            });
            assertThat(closeStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(closing.isDone()).isFalse();

            finishExecution.countDown();

            assertThat(execution.get(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThatCode(() -> closing.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThatThrownBy(() -> registry.execute(alias, ignored -> ProcessModelType.TBBPM, Map.of(),
                    ProcessExecutionOptions.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ProcessEngineRegistry is closed");
        } finally {
            finishExecution.countDown();
            registry.close();
            executor.shutdownNow();
        }
    }

    @Test
    void admittedExecutionMayFinishRegistryLookupWhileCloseDrains() throws Exception {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class), tbbpm,
                localRoutingState, EnumSet.of(ProcessModelType.TBBPM));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch resolverEntered = new CountDownLatch(1);
        CountDownLatch finishResolver = new CountDownLatch(1);
        try {
            ProcessRef.Version v1 = ProcessRef.version("default", TBBPM_CODE, "v1");
            registry.get(ProcessModelType.TBBPM).runtime().load(v1, ProcessDefinition.inline(TBBPM_CODE, tbbpmFlow()));

            Future<ProcessResult<Map<String, Object>>> execution =
                    executor.submit(() -> registry.execute(v1,
                    ignored -> {
                        resolverEntered.countDown();
                        await(finishResolver);
                        return ProcessModelType.TBBPM;
                    }, Map.of(), ProcessExecutionOptions.defaults()));
            assertThat(resolverEntered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = executor.submit(registry::close);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (true) {
                try {
                    registry.getConfigurations();
                    if (System.nanoTime() >= deadline) {
                        fail("Registry close did not cut off admission");
                    }
                    Thread.onSpinWait();
                } catch (IllegalStateException expected) {
                    break;
                }
            }
            finishResolver.countDown();

            assertThat(execution.get(10, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThatCode(() -> closing.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
        } finally {
            finishResolver.countDown();
            registry.close();
            executor.shutdownNow();
        }
    }

    @Test
    void unresolvedProcessCallFailsBeforeTheFirstAction() {
        AtomicInteger scriptExecutions = new AtomicInteger();
        ProcessEngineConfig tbbpm = ProcessEngineConfig
            .builder(ProcessModelType.TBBPM)
            .discoverPlugins(false)
            .scriptExecutor(countingVersionProbe(scriptExecutions))
            .build();
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();

        try (ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class),
                tbbpm, localRoutingState, EnumSet.of(ProcessModelType.TBBPM))) {
            ProcessRef.Version parent = ProcessRef.version("default", TBBPM_CODE, "v1");
            ProcessRef.Alias alias = ProcessRef.alias("default", TBBPM_CODE, "production");
            registry
                .get(ProcessModelType.TBBPM)
                .runtime()
                .load(parent, ProcessDefinition.inline(TBBPM_CODE, parentWithChildFlow(TBBPM_CHILD_CODE)));
            applyStableRoute(localRoutingState, TBBPM_CODE, "v1", 1L);

            ProcessResult<Map<String, Object>> result =
                    registry.execute(alias, ignored -> ProcessModelType.TBBPM, Map.of(),
                            ProcessExecutionOptions.defaults());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo("CF_EXEC_012");
            assertThat(scriptExecutions).hasValue(0);

            registry.get(ProcessModelType.TBBPM).runtime().unload(parent);
            assertThat(localRoutingState.getInstalledVersionState().contains("default", TBBPM_CODE, "v1")).isFalse();
        }
    }

    @Test
    void rejectsEngineAccessAfterClose() {
        ProcessEngineConfig tbbpm = configuration(ProcessModelType.TBBPM);
        ProcessEngineRegistry registry = new ProcessEngineRegistry(mock(ProcessEngineConfigurationFactory.class), tbbpm,
                new LocalRoutingState(), EnumSet.of(ProcessModelType.TBBPM));

        registry.close();

        assertThatIllegalStateException()
            .isThrownBy(() -> registry.get(ProcessModelType.TBBPM))
            .withMessage("ProcessEngineRegistry is closed");
        assertThatIllegalStateException()
            .isThrownBy(() -> registry.getConfiguration(ProcessModelType.TBBPM))
            .withMessage("ProcessEngineRegistry is closed");
        assertThatIllegalStateException().isThrownBy(registry::getEngines).withMessage(
                "ProcessEngineRegistry is closed");
        registry.close();
    }
}
