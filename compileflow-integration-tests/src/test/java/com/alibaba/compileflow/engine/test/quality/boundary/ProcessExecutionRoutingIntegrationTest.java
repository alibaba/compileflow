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
package com.alibaba.compileflow.engine.test.quality.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import com.alibaba.compileflow.engine.core.assembly.AssembledProcessEngineFactory;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.routing.DeterministicAliasSelector;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ProcessExecutionRoutingIntegrationTest {
    private static final String CODE = "test.execution-routing-boundary";
    private static final String FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.execution-routing-boundary" name="Routing Boundary">
            <var name="alias" dataType="java.lang.String" inOutType="param"/>
            <var name="observed_alias" dataType="java.lang.String" inOutType="return"/>
            <start id="start" name="Start" g="50,50,32,32">
                <transition to="copy"/>
            </start>
            <scriptTask id="copy" name="Copy" g="140,42,120,48">
                <action type="script" language="java">
                        <input target="alias" dataType="java.lang.String"
                             source="alias"/>
                        <output dataType="java.lang.String"
                             target="observed_alias"/>
                        <code><![CDATA[return alias;]]></code>

                </action>
                <transition to="end"/>
            </scriptTask>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """;

    @Test
    void routingUsesExplicitCohortsWithoutLeakingAndInvocationIdentityByDefault() {
        List<ProcessEvent> events = new CopyOnWriteArrayList<>();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .eventListener(events::add)
            .build();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRef.Alias alias = ProcessRef.alias("default", CODE, "prod");
        ProcessAliasRoute route = ProcessAliasRoute.canary(alias, "v1", "v2", 5_000, 1L);
        localRoutingState.applyAliasRoute(route);

        try (ProcessEngine engine =
                AssembledProcessEngineFactory.create(config, EngineAssembly.assemble(config, localRoutingState))) {
            engine.runtime().load(ProcessRef.version("default", CODE, "v1"), ProcessDefinition.inline(CODE, FLOW));
            engine.runtime().load(ProcessRef.version("default", CODE, "v2"), ProcessDefinition.inline(CODE, FLOW));

            String sensitiveRoutingKey = "customer-secret-routing-key";
            ProcessAliasTarget expectedTarget = DeterministicAliasSelector.select(route, sensitiveRoutingKey);
            ProcessExecutionOptions options = ProcessExecutionOptions
                .builder()
                .invocationId("request-42")
                .aliasRouting(new AliasRoutingOptions(sensitiveRoutingKey))
                .build();
            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.alias("default", CODE, "prod"), Map.of("alias", "business-value"), options);

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("observed_alias", "business-value");
            assertThat(result.getExecution().getInvocationId()).isEqualTo("request-42");
            assertThat(result.getExecution().getProcessVersion()).isEqualTo(route.versionFor(expectedTarget));
            assertThat(result.getExecution().getTraceId()).isNotBlank();
            assertThat(result.toString()).doesNotContain(sensitiveRoutingKey);
            assertThat(result.getExecution().toString()).doesNotContain(sensitiveRoutingKey);
            assertThat(events)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.toString()).doesNotContain(sensitiveRoutingKey));
            ProcessEvent.ExecutionCompleted completed = events
                .stream()
                .filter(ProcessEvent.ExecutionCompleted.class::isInstance)
                .map(ProcessEvent.ExecutionCompleted.class::cast)
                .findFirst()
                .orElseThrow();
            assertThat(completed.execution()).isSameAs(result.getExecution());
            assertThat(completed.attribution().parentInvocationId()).isNull();
            assertThat(completed.attribution().callDepth()).isZero();
            assertThat(completed.attribution().modelType()).isEqualTo(ProcessModelType.TBBPM);
            assertThat(completed.attribution().sourceDigest()).matches("[0-9a-f]{64}");
            assertThat(completed.attribution().admittedAlias()).isEqualTo(ProcessRef.alias("default", CODE, "prod"));
            assertThat(completed.attribution().aliasRevision()).isEqualTo(1L);
            assertThat(completed.attribution().aliasTarget()).isEqualTo(expectedTarget);
            assertThat(completed.traceId()).isEqualTo(result.getExecution().getTraceId());

            String fallbackInvocationId = "request-without-routing-key";
            ProcessAliasTarget fallbackTarget = DeterministicAliasSelector.select(route, fallbackInvocationId);
            ProcessResult<Map<String, Object>> fallback = engine.execute(ProcessRef.alias("default", CODE, "prod"),
                    Map.of("alias", "fallback"),
                    ProcessExecutionOptions.builder().invocationId(fallbackInvocationId).build());

            assertThat(fallback.isSuccess()).as(String.valueOf(fallback.getError())).isTrue();
            assertThat(fallback.getExecution().getInvocationId()).isEqualTo(fallbackInvocationId);
            assertThat(fallback.getExecution().getProcessVersion()).isEqualTo(route.versionFor(fallbackTarget));
        }
    }

    @Test
    void routeAndRuntimeAvailabilityRemainDistinctPublicProcessOutcomes() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();

        try (ProcessEngine engine =
                AssembledProcessEngineFactory.create(config, EngineAssembly.assemble(config, localRoutingState))) {
            ProcessResult<Map<String, Object>> missingRoute =
                    engine.execute(ProcessRef.alias("default", CODE, "prod"), Map.of());
            ProcessResult<Map<String, Object>> missingRuntime =
                    engine.execute(ProcessRef.version("default", CODE, "v1"), Map.of());

            assertThat(missingRoute.isFailure()).isTrue();
            assertThat(missingRoute.getError().getCode()).isEqualTo("CF_EXEC_011");
            assertThat(missingRoute.getError().getMessage()).isEqualTo(
                    "Process route unavailable before action execution");
            assertThat(missingRuntime.isFailure()).isTrue();
            assertThat(missingRuntime.getError().getCode()).isEqualTo("CF_EXEC_012");
            assertThat(missingRuntime.getError().getMessage()).isEqualTo(
                    "Process runtime unavailable before action execution");
        }
    }
}
