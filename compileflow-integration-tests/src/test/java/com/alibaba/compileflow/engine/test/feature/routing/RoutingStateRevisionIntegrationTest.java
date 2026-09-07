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
package com.alibaba.compileflow.engine.test.feature.routing;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import com.alibaba.compileflow.deploy.testkit.InMemoryProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.runtime.DeploymentRuntime;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.Awaiter;
import com.alibaba.compileflow.engine.test.support.helpers.DeploymentRuntimeTestSupport;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@Tag("integration")
@Tag("routing")
@Tag("revision")
@Execution(ExecutionMode.SAME_THREAD)
class RoutingStateRevisionIntegrationTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);
    private static final String STATE_PREFIX = "compileflow.test.revision.";
    private static final String NAMESPACE = "default";
    private static final String ALIAS = "prod";
    private InMemoryProcessVersionStore versionRepository;
    private ProcessEngine engine;
    private LocalRoutingState localRoutingState;
    private InMemoryDeploymentProjectionStore projectionStore;
    private ExecutorService executor;
    private DeploymentRuntime runtime;
    private AliasAdmission aliasAdmission;

    private static String aliasPayload(String code, String version, long revision) {
        return RoutingStateCodec.aliasStateJson(NAMESPACE, code, ALIAS, version, null, null, revision, "test",
                System.currentTimeMillis());
    }

    private static String flowReturningMarker(String code, String marker) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <var name=\"version_marker\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"calc\"/>\n"
                + "    </start>\n" + "    <scriptTask id=\"calc\" name=\"Calc\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"java\">\n"
                + "            <output target=\"version_marker\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[return \"" + marker + "\";]]></code>\n" + "        </action>\n"
                + "        <transition to=\"end\"/>\n" + "    </scriptTask>\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";
    }

    @BeforeEach
    void setUp() {
        versionRepository = new InMemoryProcessVersionStore();
        engine = ProcessEngineTestFactory.create();
        localRoutingState = new LocalRoutingState();
        projectionStore = new InMemoryDeploymentProjectionStore();
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-deploy-runtime");
            thread.setDaemon(true);
            return thread;
        });
        aliasAdmission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState());
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (runtime != null) {
                runtime.close();
            }
        } finally {
            try {
                if (engine != null) {
                    engine.close();
                }
            } finally {
                if (executor != null) {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    @Test
    void aliasRouteRejectsAnOlderRevisionEndToEnd() throws Exception {
        String code = "routing.revision.alias";
        deployReady(code, "v1", flowReturningMarker(code, "v1"));
        deployReady(code, "v2", flowReturningMarker(code, "v2"));
        String key = RoutingStateKeys.aliasState(STATE_PREFIX, NAMESPACE, code, ALIAS);
        runtime = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, Collections.singletonList(key), engine,
                versionRepository, OPERATION_TIMEOUT, executor, localRoutingState);
        runtime.start();

        assertThat(projectionStore.compareAndSet(key, null, aliasPayload(code, "v2", 10L), "json", OPERATION_TIMEOUT)).isTrue();
        awaitRoute(code, "v2");
        awaitDeployed(code, "v2");

        String current = projectionStore.read(key, OPERATION_TIMEOUT);
        assertThat(projectionStore.compareAndSet(key, current, aliasPayload(code, "v1", 5L), "json", OPERATION_TIMEOUT))
            .isTrue();
        assertThat(route(code)).isEqualTo("v2");
        assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, code, ALIAS))
            .hasValueSatisfying(route -> assertThat(route.revision()).isEqualTo(10L));
    }

    private void deployReady(String code, String version, String content) {
        ProcessVersionRecord record = ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(code)
            .version(version)
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content),
                    Map.of()))
            .actor("test")
            .createdAt(System.currentTimeMillis())
            .build();
        versionRepository.save(record);
        engine
            .runtime()
            .load(ProcessRef.version(NAMESPACE, code, version),
                    ProcessDefinition.inline(ProcessModelType.TBBPM, code, content));
    }

    private void awaitRoute(String code, String expectedVersion) {
        Awaiter.await("alias route " + ALIAS + " converges to " + expectedVersion, Duration.ofSeconds(5), Duration.ofMillis(
                50), () -> expectedVersion.equals(route(code)), () -> "localRoutingState=" + localRoutingState);
    }

    private void awaitDeployed(String code, String expectedVersion) {
        Awaiter.await("version " + expectedVersion + " is installed locally", Duration.ofSeconds(5), Duration.ofMillis(
                50), () -> localRoutingState.getInstalledVersionState().contains(NAMESPACE, code, expectedVersion), () -> "localRoutingState="
                + localRoutingState);
    }

    private String route(String code) {
        return aliasAdmission
            .admit(ProcessRef.alias(NAMESPACE, code, ALIAS), new AliasRoutingOptions("user-1"))
            .version()
            .version();
    }
}
