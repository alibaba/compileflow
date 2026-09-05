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
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStatePayloads;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import com.alibaba.compileflow.deploy.control.repository.InMemoryProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.runtime.DeployRuntime;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.Awaiter;
import com.alibaba.compileflow.engine.test.support.helpers.DeployRuntimeTestSupport;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(2);

    @Test
    @DisplayName("ProcessEngine close should not leak engine threads")
    void engineCloseShouldNotLeakThreads() throws Exception {
        int before = countEngineThreads();

        for (int i = 0; i < 3; i++) {
            ProcessEngine engine = ProcessEngineTestFactory.createTbbpm();
            engine.close();
        }

        AtomicInteger previousCount = new AtomicInteger(Integer.MIN_VALUE);
        AtomicInteger stableSamples = new AtomicInteger();
        Awaiter.await("Engine threads stabilized after close", Duration.ofSeconds(3), Duration.ofMillis(100),
                () -> {
                    int current = countEngineThreads();
                    if (previousCount.getAndSet(current) == current) {
                        return stableSamples.incrementAndGet() >= 2;
                    }
                    stableSamples.set(0);
                    return false;
                }, () -> "Current thread count: " + countEngineThreads());

        int after = countEngineThreads();
        // Allow minor JVM shared thread fluctuations (e.g., GC/JIT/IDE plugins), but engine threads
        // should not grow persistently.
        assertThat(after)
            .as("Engine threads after close should not grow unexpectedly (before=%s after=%s)", before, after)
            .isLessThanOrEqualTo(before + 2);
    }

    @Test
    @DisplayName("DeployRuntime close should stop reacting to routing state updates")
    void deployRuntimeCloseShouldStopReacting() throws Exception {
        String ns = "default";
        String code = "resource.runtime.close";
        String version = "v1";

        InMemoryProcessVersionRepository repo = new InMemoryProcessVersionRepository();
        ProcessEngine engine = ProcessEngineTestFactory.createTbbpm();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();

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
                .modelType(ProcessModelType.TBBPM)
                .processDefinition(ProcessDefinition.inline(code, content))
                .artifactDigest(ProcessArtifactDigest.compute(ProcessModelType.TBBPM,
                        ProcessDefinition.inline(code, content), Map.of()))
                .actor("test")
                .createdAt(System.currentTimeMillis())
                .build());

            String key = RoutingStateKeys.aliasState("compileflow.test.resource.", ns, code, "prod");
            List<String> keys = Collections.singletonList(key);
            DeployRuntime rt = DeployRuntimeTestSupport.dbRuntime(channel, keys, engine, ProcessModelType.TBBPM, repo,
                    CHANNEL_TIMEOUT, executor, localRoutingState);
            rt.start();

            assertThat(channel.compareAndSet(key, null, aliasPayload(ns, code, version, 1), "json", CHANNEL_TIMEOUT)).isTrue();
            Awaiter.await("Version deployed to snapshot", Duration.ofMillis(8000), Duration.ofMillis(50), () -> localRoutingState
                .getInstalledVersionState()
                .contains(ns, code, version), () -> "Deployed snapshot state: "
                    + localRoutingState.getInstalledVersionState());
            assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version)).isTrue();
            // Close runtime
            rt.close();
            // Clear snapshot and write again; closed runtime should NOT reinstall
            localRoutingState.clear();
            String current = channel.read(key, CHANNEL_TIMEOUT);
            assertThat(channel.compareAndSet(key, current, aliasPayload(ns, code, version, 2), "json", CHANNEL_TIMEOUT)).isTrue();
            // Verify runtime remains stopped (wait long enough to observe any reaction)
            long verifyStart = System.currentTimeMillis();
            Awaiter.await("Verify runtime remains stopped", Duration.ofMillis(1500), Duration.ofMillis(100),
                    () -> {
                        boolean stillStopped = !localRoutingState
                            .getInstalledVersionState()
                            .contains(ns, code, version);
                        // Ensure we've waited at least 500ms to observe any potential reaction
                        return stillStopped && (System.currentTimeMillis() - verifyStart > 500);
                    },
                    () -> "Snapshot contains version: "
                    + localRoutingState.getInstalledVersionState().contains(ns, code, version));

            assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version))
                .as("Closed runtime must not react to routing state updates")
                .isFalse();
        } finally {
            try {
                engine.close();
            } finally {
                executor.shutdown();
                localRoutingState.clear();
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
        return RoutingStatePayloads.aliasStateJson(namespace, code, "prod", version, null, null, revision, "test",
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
