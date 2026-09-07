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
package com.alibaba.compileflow.engine.test.quality.resource;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
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
@Tag("resource")
@Execution(ExecutionMode.SAME_THREAD)
class ResourceManagementTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);

    @Test
    @DisplayName("ProcessEngine close should not leak engine threads")
    void engineCloseShouldNotLeakThreads() throws Exception {
        int before = countEngineThreads();

        for (int i = 0; i < 3; i++) {
            ProcessEngine engine = ProcessEngineTestFactory.create();
            try {
                assertThat(engine
                    .execute(ProcessDefinition.inline(ProcessModelType.TBBPM, "resource.threads",
                                    markerFlow("resource.threads", "ready")), Map.of())
                    .orElseThrow())
                    .containsEntry("version_marker", "ready");
                assertThat(countEngineThreads()).as("The test must exercise engine-owned worker threads").isGreaterThan(
                        before);
            } finally {
                engine.close();
            }
        }

        Awaiter.await("Engine workers stopped", Duration.ofSeconds(3), Duration.ofMillis(100), () -> countEngineThreads() <= before, () -> "threads="
                + countEngineThreads());

        int after = countEngineThreads();
        assertThat(after)
            .as("Engine threads after close should not grow unexpectedly (before=%s after=%s)", before, after)
            .isLessThanOrEqualTo(before);
    }

    @Test
    @DisplayName("DeploymentRuntime close should stop reacting to routing state updates")
    void deployRuntimeCloseShouldStopReacting() throws Exception {
        String ns = "default";
        String code = "resource.runtime.close";
        String version = "v1";

        InMemoryProcessVersionStore repo = new InMemoryProcessVersionStore();
        ProcessEngine engine = ProcessEngineTestFactory.create();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-deploy-runtime");
            t.setDaemon(true);
            return t;
        });

        try {
            String content = markerFlow(code, version);
            repo.save(ProcessVersionRecord
                .builder()
                .namespace(ns)
                .code(code)
                .version(version)
                .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content))
                .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, code,
                                content), Map.of()))
                .actor("test")
                .createdAt(System.currentTimeMillis())
                .build());

            String key = RoutingStateKeys.aliasState("compileflow.test.resource.", ns, code, "prod");
            List<String> keys = Collections.singletonList(key);
            try (DeploymentRuntime rt = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, keys, engine, repo,
                    OPERATION_TIMEOUT, executor, localRoutingState)) {
                rt.start();

                assertThat(projectionStore.compareAndSet(key, null, aliasPayload(ns, code, version, 1), "json",
                        OPERATION_TIMEOUT))
                    .isTrue();
                Awaiter.await("Version and route deployed to local state", Duration.ofMillis(8000), Duration.ofMillis(
                        50), () -> localRoutingState.getInstalledVersionState().contains(ns, code, version)
                        && localRoutingState
                            .getAliasRouteState()
                            .resolve(ns, code, "prod")
                            .filter(route -> route.revision() == 1L)
                            .isPresent(), () -> "Local state: " + localRoutingState);
                assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version)).isTrue();
            }
            long appliedRevision =
                    localRoutingState.getAliasRouteState().resolve(ns, code, "prod").orElseThrow().revision();
            String current = projectionStore.read(key, OPERATION_TIMEOUT);
            assertThat(projectionStore.compareAndSet(key, current, aliasPayload(ns, code, version, 2), "json",
                    OPERATION_TIMEOUT))
                .isTrue();
            long verifyStart = System.currentTimeMillis();
            Awaiter.await("Verify runtime remains stopped", Duration.ofMillis(1500), Duration.ofMillis(100),
                    () -> {
                        long currentRevision =
                        localRoutingState.getAliasRouteState().resolve(ns, code, "prod").orElseThrow().revision();
                        return currentRevision == appliedRevision && (System.currentTimeMillis() - verifyStart > 500);
                    }, () -> "Applied route: " + localRoutingState.getAliasRouteState().resolve(ns, code, "prod"));

            assertThat(localRoutingState.getAliasRouteState().resolve(ns, code, "prod").orElseThrow().revision())
                .as("Closed runtime must not react to routing state updates")
                .isEqualTo(appliedRevision);
        } finally {
            try {
                engine.close();
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("LocalRoutingState must be instance-isolated across engines")
    void snapshotsShouldBeIsolatedAcrossInstances() {
        LocalRoutingState s1 = new LocalRoutingState();
        LocalRoutingState s2 = new LocalRoutingState();

        ProcessRef.Alias alias = ProcessRef.alias("default", "code", "prod");
        s1.applyAliasRoute(ProcessAliasRoute.stable(alias, "v1", 1L));

        assertThat(s1.getAliasRouteState().resolve("default", "code", "prod"))
            .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v1"));
        assertThat(s2.getAliasRouteState().resolve("default", "code", "prod")).isEmpty();
    }

    // ===== helpers =====
    private int countEngineThreads() {
        int count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t == null) {
                continue;
            }
            String name = t.getName();
            if (name != null && name.startsWith("compileflow-engine-")) {
                if (t.isAlive()) {
                    count++;
                }
            }
        }
        return count;
    }

    private String aliasPayload(String namespace, String code, String version, long revision) {
        return RoutingStateCodec.aliasStateJson(namespace, code, "prod", version, null, null, revision, "test",
                System.currentTimeMillis());
    }

    private String markerFlow(String code, String marker) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <var name=\"version_marker\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n" + "\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"calc\"/>\n"
                + "    </start>\n" + "\n" + "    <scriptTask id=\"calc\" name=\"Calc\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"java\">\n"
                + "            <output target=\"version_marker\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[return \"" + marker + "\";]]></code>\n" + "        </action>\n"
                + "        <transition to=\"end\"/>\n" + "    </scriptTask>\n" + "\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";
    }
}
