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
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NestedProcessResolutionIntegrationTest {
    private static final String NAMESPACE = "tenant-a";
    private static final String ALIAS = "production";
    private static final String PARENT = "test.nested.parent";
    private static final String CHILD = "test.nested.child";

    private static ProcessEngine engine(ProcessEngineConfig config, LocalRoutingState localRoutingState) {
        return AssembledProcessEngineFactory.create(config, EngineAssembly.assemble(config, localRoutingState));
    }

    private static void load(ProcessEngine engine, String code, String version, String definition) {
        engine.runtime().load(ProcessRef.version(NAMESPACE, code, version), ProcessDefinition.inline(code, definition));
    }

    private static void loadParent(ProcessEngine engine, String version, String childVersion) {
        load(engine, PARENT, version, parentFlow(childVersion));
    }

    private static void canary(LocalRoutingState localRoutingState, String code, String stable, String candidate,
            long revision) {
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, code, ALIAS);
        localRoutingState.applyAliasRoute(ProcessAliasRoute.canary(alias, stable, candidate, 5_000, revision));
    }

    private static String routingKeyFor(ProcessAliasRoute route, ProcessAliasTarget target) {
        for (int index = 0; index < 10_000; index++) {
            String routingKey = "customer-" + index;
            if (DeterministicAliasSelector.select(route, routingKey) == target) {
                return routingKey;
            }
        }
        throw new AssertionError("No routing key found for " + target);
    }

    private static String parentFlow(String childVersion) {
        return """
            <bpm code="%s">
              <var name="marker" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32">
                <transition to="child"/>
              </start>
              <bpmCall id="child" code="%s" version="%s" g="60,0,100,48">
                <output source="marker" target="marker"/>
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(PARENT, CHILD, childVersion);
    }

    private static String childFlow(String marker) {
        return markerFlow(CHILD, marker);
    }

    private static String markerFlow(String code, String marker) {
        return """
            <bpm code="%s">
              <var name="marker" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32">
                <transition to="set"/>
              </start>
              <scriptTask id="set" g="60,0,100,48">
                <action type="script" language="java">
                    <output dataType="java.lang.String"
                         target="marker"/>
                    <code>return "%s";</code>

                </action>
                <transition to="end"/>
              </scriptTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(code, marker);
    }

    private static String parentCallsChildTwiceFlow(String firstVersion, String secondVersion) {
        return """
            <bpm code="%s">
              <var name="first" dataType="java.lang.String" inOutType="return"/>
              <var name="second" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32">
                <transition to="firstCall"/>
              </start>
              <bpmCall id="firstCall" code="%s" version="%s" g="60,0,100,48">
                <output source="marker" target="first"/>
                <transition to="secondCall"/>
              </bpmCall>
              <bpmCall id="secondCall" code="%s" version="%s" g="200,0,100,48">
                <output source="marker" target="second"/>
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="340,0,32,32"/>
            </bpm>
            """
            .formatted(PARENT, CHILD, firstVersion, CHILD, secondVersion);
    }

    private static String callFlow(String code, String child, String version) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="call"/></start>
              <bpmCall id="call" code="%s" version="%s" g="40,0,80,32"><transition to="end"/></bpmCall>
              <end id="end" g="140,0,32,32"/>
            </bpm>
            """
            .formatted(code, child, version);
    }

    private static String twoCallFlow(String code, String first, String second) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="first"/></start>
              <bpmCall id="first" code="%s" version="v1" g="40,0,80,32">
                <transition to="second"/>
              </bpmCall>
              <bpmCall id="second" code="%s" version="v1" g="140,0,80,32">
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="240,0,32,32"/>
            </bpm>
            """
            .formatted(code, first, second);
    }

    @Test
    void rootAliasRoutesOnceAndChildCallUsesItsDeclaredVersion() {
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, PARENT, ALIAS);
        ProcessAliasRoute route = ProcessAliasRoute.canary(alias, "parent-v1", "parent-v2", 5_000, 1L);
        AtomicInteger routeReads = new AtomicInteger();
        List<ProcessEvent> events = new CopyOnWriteArrayList<>();
        ProcessEngineConfig config = ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .observability(ProcessObservabilityConfig.builder().eventsAsync(false).build())
            .eventListener(events::add)
            .aliasRouteSource(requested -> {
                routeReads.incrementAndGet();
                assertThat(requested).isEqualTo(alias);
                return Optional.of(route);
            })
            .build();
        LocalRoutingState localRoutingState = new LocalRoutingState();

        try (ProcessEngine engine = engine(config, localRoutingState)) {
            load(engine, CHILD, "child-v1", childFlow("child-v1"));
            load(engine, CHILD, "child-v2", childFlow("child-v2"));
            loadParent(engine, "parent-v1", "child-v1");
            loadParent(engine, "parent-v2", "child-v1");

            ProcessExecutionOptions options = ProcessExecutionOptions
                .builder()
                .aliasRouting(new AliasRoutingOptions(routingKeyFor(route, ProcessAliasTarget.CANDIDATE)))
                .build();
            ProcessResult<Map<String, Object>> result = engine.execute(alias, Map.of(), options);

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("marker", "child-v1");
            assertThat(result.getExecution().getProcessVersion().version()).isEqualTo("parent-v2");
            assertThat(routeReads).hasValue(1);
            assertInvocationLineage(events);
        }
    }

    @Test
    void callSitesCanBindTheSameCodeToDifferentExactVersions() {
        AtomicInteger aliasReads = new AtomicInteger();
        ProcessEngineConfig config =
                ProcessEngineTestFactory
            .tbbpmBuilder()
            .discoverPlugins(false)
            .aliasRouteSource(alias -> {
                aliasReads.incrementAndGet();
                if (!PARENT.equals(alias.code())) {
                    throw new AssertionError("Child ProcessCall must not resolve an Alias");
                }
                return Optional.of(ProcessAliasRoute.stable(alias, "parent-v1", 1L));
            })
            .build();

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            load(engine, CHILD, "child-v1", childFlow("child-v1"));
            load(engine, CHILD, "child-v2", childFlow("child-v2"));
            load(engine, PARENT, "parent-v1", parentCallsChildTwiceFlow("child-v1", "child-v2"));

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.alias(NAMESPACE, PARENT, ALIAS), Map.of());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("first", "child-v1").containsEntry("second", "child-v2");
            assertThat(aliasReads).hasValue(1);
        }
    }

    @Test
    void exactParentVersionExecutesItsDeclaredExactChild() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = engine(config, localRoutingState)) {
            load(engine, CHILD, "child-v1", childFlow("child-v1"));
            load(engine, CHILD, "child-v2", childFlow("child-v2"));
            loadParent(engine, "parent-v1", "child-v2");

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.version(NAMESPACE, PARENT, "parent-v1"), Map.of());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("marker", "child-v2");
        }
    }

    @Test
    void directParentMayUseAnExactChildVersion() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            engine.runtime().load(ProcessRef.version(CHILD, "child-v1"),
                    ProcessDefinition.inline(CHILD, childFlow("child-v1")));

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessDefinition.inline(PARENT, parentFlow("child-v1")), Map.of());

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("marker", "child-v1");
        }
    }

    @Test
    void exactParentVersionCannotUseAClasspathTarget() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();
        String parent = parentFlow("child-v1")
            .replace("version=\"child-v1\"", "classpath=\"resource/calls/child.bpm\"");

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            load(engine, PARENT, "parent-v1", parent);

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.version(NAMESPACE, PARENT, "parent-v1"), Map.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_014.getCode());
        }
    }

    @Test
    void missingVersionTargetFailsBeforeTheProcessCanExecute() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();
        LocalRoutingState localRoutingState = new LocalRoutingState();

        try (ProcessEngine engine = engine(config, localRoutingState)) {
            loadParent(engine, "parent-v1", "missing-v1");

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.version(NAMESPACE, PARENT, "parent-v1"), Map.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_012.getCode());
        }
    }

    @Test
    void processCallDepthIsRootInclusiveAndRejectedBeforeExecution() {
        ProcessEngineConfig config =
                ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).maxCallDepth(1).build();

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            load(engine, CHILD, "child-v1", childFlow("child-v1"));
            loadParent(engine, "parent-v1", "child-v1");

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.version(NAMESPACE, PARENT, "parent-v1"), Map.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_013.getCode());
            assertThat(result.getError().getMessage()).contains(PARENT, CHILD);
        }
    }

    @Test
    void processCallDepthUsesTheLongestPathForSharedDescendants() {
        ProcessEngineConfig config =
                ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).maxCallDepth(4).build();
        String root = "test.nested.depth-root";
        String first = "test.nested.depth-first";
        String second = "test.nested.depth-second";
        String bridge = "test.nested.depth-bridge";
        String shared = "test.nested.depth-shared";
        String leaf = "test.nested.depth-leaf";

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            load(engine, leaf, "v1", markerFlow(leaf, "depth-leaf"));
            load(engine, shared, "v1", callFlow(shared, leaf, "v1"));
            load(engine, first, "v1", callFlow(first, shared, "v1"));
            load(engine, bridge, "v1", callFlow(bridge, shared, "v1"));
            load(engine, second, "v1", callFlow(second, bridge, "v1"));
            load(engine, root, "v1", twoCallFlow(root, first, second));

            ProcessResult<Map<String, Object>> result =
                    engine.execute(ProcessRef.version(NAMESPACE, root, "v1"), Map.of());

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_013.getCode());
            assertThat(result.getError().getMessage()).contains(leaf, shared);
        }
    }

    @Test
    void unloadingAChildInvalidatesResolvedExactCallGraphs() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();
        ProcessRef.Version child = ProcessRef.version(NAMESPACE, CHILD, "child-v1");
        ProcessRef.Version parent = ProcessRef.version(NAMESPACE, PARENT, "parent-v1");

        try (ProcessEngine engine = engine(config, new LocalRoutingState())) {
            load(engine, CHILD, child.version(), childFlow("child-v1"));
            loadParent(engine, parent.version(), child.version());
            assertThat(engine.execute(parent, Map.of()).isSuccess()).isTrue();

            engine.runtime().unload(child);

            ProcessResult<Map<String, Object>> result = engine.execute(parent, Map.of());
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_EXEC_012.getCode());

            load(engine, CHILD, child.version(), childFlow("child-reloaded"));
            assertThat(engine.execute(parent, Map.of()).orElseThrow()).containsEntry("marker", "child-reloaded");
        }
    }

    private static void assertInvocationLineage(List<ProcessEvent> events) {
        Map<String, ProcessEvent.ExecutionCompleted> completed = events
            .stream()
            .filter(ProcessEvent.ExecutionCompleted.class::isInstance)
            .map(ProcessEvent.ExecutionCompleted.class::cast)
            .collect(java.util.stream.Collectors.toMap(event -> event.execution().getProcessCode(), event -> event));
        ProcessEvent.ExecutionCompleted parent = completed.get(PARENT);
        ProcessEvent.ExecutionCompleted child = completed.get(CHILD);

        assertThat(parent.execution().getNamespace()).isEqualTo(NAMESPACE);
        assertThat(parent.attribution().parentInvocationId()).isNull();
        assertThat(parent.attribution().callDepth()).isZero();
        assertThat(child.execution().getNamespace()).isEqualTo(NAMESPACE);
        assertThat(child.execution().getInvocationId()).isNotEqualTo(parent.execution().getInvocationId());
        assertThat(child.attribution().parentInvocationId()).isEqualTo(parent.execution().getInvocationId());
        assertThat(child.attribution().callDepth()).isOne();
        assertThat(child.execution().getTraceId()).isEqualTo(parent.execution().getTraceId());
    }
}
