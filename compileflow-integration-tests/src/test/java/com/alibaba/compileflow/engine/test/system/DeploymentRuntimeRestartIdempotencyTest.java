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
class DeploymentRuntimeRestartIdempotencyTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private ProcessEngine engine;
    private InMemoryProcessVersionStore versionRepo;
    private InMemoryDeploymentProjectionStore projectionStore;
    private ExecutorService runtimeExecutor;
    private LocalRoutingState localRoutingState;

    private static String aliasPayload(String namespace, String code, String version, long revision) {
        return RoutingStateCodec.aliasStateJson(namespace, code, "prod", version, null, null, revision, "test",
                System.currentTimeMillis());
    }

    private static String tbbpmMinimal(String code) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"end\"/>\n"
                + "    </start>\n" + "    <end id=\"end\" name=\"End\" g=\"150,50,32,32\"/>\n" + "</bpm>";
    }

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.create();
        versionRepo = new InMemoryProcessVersionStore();
        projectionStore = new InMemoryDeploymentProjectionStore();
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
                runtimeExecutor.shutdownNow();
                assertThat(runtimeExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
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
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content),
                    Map.of()))
            .actor("test")
            .createdAt(System.currentTimeMillis())
            .build();
        versionRepo.save(record);
        // Start runtime #1 and publish routing state
        String key = RoutingStateKeys.aliasState(prefix, ns, code, "prod");
        List<String> keys = Collections.singletonList(key);
        try (DeploymentRuntime rt1 = newRuntime(keys)) {
            rt1.start();

            assertThat(projectionStore.compareAndSet(key, null, aliasPayload(ns, code, version, 1), "json",
                    OPERATION_TIMEOUT))
                .isTrue();

            Awaiter.await("runtime#1 installed demanded version", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
                .getInstalledVersionState()
                .contains(ns, code, version), () -> dumpSnapshots(ns, code));
        }
        // A restarted node has new node-local state while the persisted control-plane signal remains.
        localRoutingState = new LocalRoutingState();
        // Start runtime #2
        try (DeploymentRuntime rt2 = newRuntime(keys)) {
            rt2.start();

            Awaiter.await("runtime#2 re-ingested existing routing state and recovered deployed snapshot", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
                .getInstalledVersionState()
                .contains(ns, code, version), () -> dumpSnapshots(ns, code));
        }
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
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content),
                    Map.of()))
            .actor("test")
            .createdAt(System.currentTimeMillis())
            .build();
        versionRepo.save(record);

        String key = RoutingStateKeys.aliasState(prefix, ns, code, "prod");
        List<String> keys = Collections.singletonList(key);
        try (DeploymentRuntime rt = newRuntime(keys)) {
            rt.start();

            String payload = aliasPayload(ns, code, version, 1);
            assertThat(projectionStore.compareAndSet(key, null, payload, "json", OPERATION_TIMEOUT)).isTrue();
            assertThat(projectionStore.compareAndSet(key, payload, payload, "json", OPERATION_TIMEOUT)).isTrue();

            Awaiter.await("idempotent apply: alias is local-ready", WAIT_TIMEOUT, POLL_INTERVAL, () -> localRoutingState
                .getAliasRouteState()
                .resolve(ns, code, "prod")
                .filter(route -> version.equals(route.stableVersion().version()))
                .isPresent(), () -> dumpSnapshots(ns, code));
            // Must not regress/rollback
            assertThat(localRoutingState.getAliasRouteState().resolve(ns, code, "prod"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo(version));
            assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version)).isTrue();
        }
    }

    private DeploymentRuntime newRuntime(List<String> routingStateKeys) {
        return DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, routingStateKeys, engine, versionRepo,
                OPERATION_TIMEOUT, runtimeExecutor, localRoutingState);
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
