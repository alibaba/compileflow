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
package com.alibaba.compileflow.engine.test.system;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
@Tag("deploy")
@Tag("runtime")
@Execution(ExecutionMode.SAME_THREAD)
class DeployRuntimeRestartIdempotencyTest {
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private ProcessEngine engine;
    private InMemoryProcessVersionRepository versionRepo;
    private InMemoryDeploymentSyncChannel channel;
    private ExecutorService runtimeExecutor;
    private LocalRoutingState localRoutingState;

    private static String aliasPayload(String namespace, String code, String version, long revision) {
        return RoutingStatePayloads.aliasStateJson(namespace, code, "prod", version, null, null, revision, "test",
                System.currentTimeMillis());
    }

    private static String tbbpmMinimal(String code) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"end\"/>\n"
                + "    </start>\n" + "    <end id=\"end\" name=\"End\" g=\"150,50,32,32\"/>\n" + "</bpm>";
    }

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createTbbpm();
        versionRepo = new InMemoryProcessVersionRepository();
        channel = new InMemoryDeploymentSyncChannel();
        localRoutingState = new LocalRoutingState();
        runtimeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-deploy-runtime");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (engine != null) {
                engine.close();
            }
        } finally {
            if (runtimeExecutor != null) {
                runtimeExecutor.shutdown();
            }
            if (localRoutingState != null) {
                localRoutingState.clear();
            }
        }
    }

    @Test
    @DisplayName("runtime restart should re-ingest the existing alias route")
    void shouldReIngestExistingRoutingStateAfterRuntimeRestart() throws Exception {
        String ns = "default";
        String code = "deploy.runtime.restart";
        String version = "v1";
        String prefix = "compileflow.test.runtime.";
        // Store a deployable version in the repository.
        String content = tbbpmMinimal(code);
        ProcessVersionRecord record = ProcessVersionRecord
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
            .build();
        versionRepo.save(record);
        // Start runtime #1 and publish routing state
        String key = RoutingStateKeys.aliasState(prefix, ns, code, "prod");
        List<String> keys = Collections.singletonList(key);
        DeployRuntime rt1 = newRuntime(keys);
        rt1.start();

        assertThat(channel.compareAndSet(key, null, aliasPayload(ns, code, version, 1), "json", CHANNEL_TIMEOUT)).isTrue();

        Awaiter.await("runtime#1 installed demanded version", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
            .getInstalledVersionState()
            .contains(ns, code, version), () -> dumpSnapshots(ns, code));

        rt1.close();
        // Clear localRoutingState to simulate node restart (but keep channel state as persisted
        // control-plane signal)
        localRoutingState.clear();
        // Start runtime #2
        DeployRuntime rt2 = newRuntime(keys);
        rt2.start();

        Awaiter.await("runtime#2 re-ingested existing routing state and recovered deployed snapshot", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
            .getInstalledVersionState()
            .contains(ns, code, version), () -> dumpSnapshots(ns, code));

        rt2.close();
    }

    @Test
    @DisplayName("writing the same alias route twice should be idempotent")
    void shouldBeIdempotentWhenSameRoutingStateIsWrittenTwice() throws Exception {
        String ns = "default";
        String code = "deploy.runtime.idempotent";
        String version = "v1";
        String prefix = "compileflow.test.runtime.";

        String content = tbbpmMinimal(code);
        ProcessVersionRecord record = ProcessVersionRecord
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
            .build();
        versionRepo.save(record);

        String key = RoutingStateKeys.aliasState(prefix, ns, code, "prod");
        List<String> keys = Collections.singletonList(key);
        DeployRuntime rt = newRuntime(keys);
        rt.start();

        String payload = aliasPayload(ns, code, version, 1);
        assertThat(channel.compareAndSet(key, null, payload, "json", CHANNEL_TIMEOUT)).isTrue();
        assertThat(channel.compareAndSet(key, payload, payload, "json", CHANNEL_TIMEOUT)).isTrue();

        Awaiter.await("idempotent apply: alias is local-ready", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
            .getAliasRouteState()
            .resolve(ns, code, "prod")
            .filter(route -> version.equals(route.stableVersion().version()))
            .isPresent(), () -> dumpSnapshots(ns, code));
        // Must not regress/rollback
        assertThat(localRoutingState.getAliasRouteState().resolve(ns, code, "prod"))
            .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo(version));
        assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version)).isTrue();

        rt.close();
    }

    private DeployRuntime newRuntime(List<String> routingStateKeys) {
        return DeployRuntimeTestSupport.dbRuntime(channel, routingStateKeys, engine, ProcessModelType.TBBPM, versionRepo,
                CHANNEL_TIMEOUT, runtimeExecutor, localRoutingState);
    }

    private String dumpSnapshots(String ns, String code) {
        StringBuilder sb = new StringBuilder();
        sb
            .append("aliasState=")
            .append(localRoutingState.getAliasRouteState().resolve(ns, code, "prod").orElse(null))
            .append('\n');
        sb.append("installedState=").append(localRoutingState.getInstalledVersionState()).append('\n');
        return sb.toString();
    }
}
