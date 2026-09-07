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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.core.routing.AliasSelectionExecutor;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProcessEngineCompositionTest {
    private static ProcessDefinition.Inline tbbpm(String code) {
        return ProcessDefinition.inline(ProcessModelType.TBBPM, code,
                """
            <bpm code="%s" name="TBBPM">
                <start id="start" name="Start" g="50,50,32,32"><transition to="end"/></start>
                <end id="end" name="End" g="180,50,32,32"/>
            </bpm>
            """
                    .formatted(code));
    }

    private static ProcessDefinition.Inline bpmn(String code) {
        return ProcessDefinition.inline(ProcessModelType.BPMN, code,
                """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://www.compileflow.org/test">
                <process id="%s" name="BPMN" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
                    <endEvent id="end"/>
                </process>
            </definitions>
            """
                    .formatted(code));
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void oneEngineExecutesAndPreflightsBothDefinitionTypes(ProcessRuntimeMode mode) {
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineConfig
            .builder()
            .runtimeMode(mode)
            .build())) {
            for (ProcessDefinition definition : java.util.List.of(tbbpm("same.code"), bpmn("same.code"))) {
                var result = engine.execute(definition, Map.of());
                assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
                assertThat(engine.tooling().preflight(definition, ProcessPreflightOptions.fast()).getOverallStatus())
                    .isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void oneEngineLoadsBothFrontendsConcurrently(ProcessRuntimeMode mode) throws Exception {
        var callers = Executors.newFixedThreadPool(4);
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineConfig
            .builder()
            .runtimeMode(mode)
            .build())) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 24; i++) {
                ProcessDefinition definition = i % 2 == 0 ? tbbpm("shared.code") : bpmn("shared.code");
                results.add(callers.submit(() -> engine.execute(definition, Map.of()).isSuccess()));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            callers.shutdownNow();
            assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void differentVersionsMayHaveDifferentTypesButExactVersionsStayImmutable(ProcessRuntimeMode mode) {
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineConfig
            .builder()
            .runtimeMode(mode)
            .build())) {
            ProcessRef.Version v1 = ProcessRef.version("default", "order", "v1");
            ProcessRef.Version v2 = ProcessRef.version("default", "order", "v2");
            engine.runtime().load(v1, tbbpm("order"));
            engine.runtime().load(v2, bpmn("order"));
            assertThat(engine.execute(v1, Map.of()).isSuccess()).isTrue();
            assertThat(engine.execute(v2, Map.of()).isSuccess()).isTrue();
            assertThatThrownBy(() -> engine.runtime().load(v1, bpmn("order"))).isInstanceOf(RuntimeException.class);
            assertThat(engine.execute(v1, Map.of()).isSuccess()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void exactProcessCallsCrossFrontendBoundaries(ProcessRuntimeMode mode) {
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineConfig
            .builder()
            .runtimeMode(mode)
            .build())) {
            engine.runtime().load(ProcessRef.version("default", "grandchild", "v1"), tbbpm("grandchild"));
            ProcessDefinition child = ProcessDefinition.inline(ProcessModelType.BPMN, "child",
                    """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:cf="http://www.compileflow.org" targetNamespace="http://www.compileflow.org/test">
                    <process id="child" isExecutable="true">
                        <startEvent id="start"/>
                        <callActivity id="call" calledElement="grandchild" cf:version="v1"/>
                        <endEvent id="end"/>
                        <sequenceFlow id="to_call" sourceRef="start" targetRef="call"/>
                        <sequenceFlow id="to_end" sourceRef="call" targetRef="end"/>
                    </process>
                </definitions>
                """);
            engine.runtime().load(ProcessRef.version("default", "child", "v1"), child);
            ProcessDefinition parent = ProcessDefinition.inline(ProcessModelType.TBBPM, "parent",
                    """
                <bpm code="parent" name="Parent">
                    <start id="start" name="Start" g="50,50,32,32"><transition to="child"/></start>
                    <bpmCall id="child" name="Child" g="100,50,80,32" code="child" version="v1">
                        <transition to="end"/>
                    </bpmCall>
                    <end id="end" name="End" g="200,50,32,32"/>
                </bpm>
                """);
            var result = engine.execute(parent, Map.of());
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(engine.execute(child, Map.of()).isSuccess()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(ProcessRuntimeMode.class)
    void pinnedAliasExecutionDoesNotReadANewerRouteOrSelectAnotherFrontend(ProcessRuntimeMode mode) {
        ProcessEngineConfig config = ProcessEngineConfig.builder().runtimeMode(mode).build();
        LocalRoutingState routing = LocalRoutingState.requiringLocalInstallation();
        try (ProcessEngine engine = EngineAssembly.create(config, EngineAssembly.assemble(config, routing))) {
            ProcessRef.Version v1 = ProcessRef.version("default", "order", "v1");
            ProcessRef.Version v2 = ProcessRef.version("default", "order", "v2");
            ProcessRef.Alias alias = ProcessRef.alias("default", "order", "production");
            engine.runtime().load(v1, tbbpm("order"));
            engine.runtime().load(v2, bpmn("order"));
            routing.applyAliasRoute(ProcessAliasRoute.stable(alias, "v2", 2L));
            var pinned = ((AliasSelectionExecutor) engine)
                .execute(alias, new AliasSelection(v1, ProcessAliasTarget.STABLE, 1L), Map.of(),
                        ProcessExecutionOptions.defaults());
            assertThat(pinned.isSuccess()).as(String.valueOf(pinned.getError())).isTrue();
            assertThat(pinned.getExecution().getProcessVersion()).isEqualTo(v1);
            assertThat(engine.execute(alias, Map.of()).getExecution().getProcessVersion()).isEqualTo(v2);
        }
    }
}
