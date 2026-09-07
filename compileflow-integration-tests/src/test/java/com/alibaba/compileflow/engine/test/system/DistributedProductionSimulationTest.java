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
@DisplayName("Distributed Production Simulation: Unified Runtime Pipeline")
@Tag("slow")
@Execution(ExecutionMode.SAME_THREAD)
public class DistributedProductionSimulationTest {
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);
    private InMemoryProcessVersionStore versionRepository;
    private ProcessEngine engine;
    private ExecutorService executor;
    private LocalRoutingState localRoutingState;

    private static String minimalTbbpmFlow(String code, String marker) {
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
        executor = Executors.newSingleThreadExecutor(r -> {
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
            if (executor != null) {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void shouldInstallDemandedVersionAfterRoutingStatePublished() throws Exception {
        String ns = "default";
        String code = "dist.unified.flow";
        String version = "1";

        String content = minimalTbbpmFlow(code, "v1");

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
        versionRepository.save(record);

        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        String statePrefix = "compileflow.test.deployment.";
        String routingKey = RoutingStateKeys.aliasState(statePrefix, ns, code, "prod");
        List<String> stateKeys = Collections.singletonList(routingKey);

        try (DeploymentRuntime rt = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, stateKeys, engine,
                versionRepository, OPERATION_TIMEOUT, executor, localRoutingState)) {
            rt.start();

            String routePayload = RoutingStateCodec.aliasStateJson(ns, code, "prod", version, null, null, 1L, "test",
                    System.currentTimeMillis());
            assertThat(projectionStore.compareAndSet(routingKey, null, routePayload, "json", OPERATION_TIMEOUT)).isTrue();

            Awaiter.await("installedState contains demanded version ns=" + ns + ", code=" + code + ", version="
                    + version, Duration.ofSeconds(5), Duration.ofMillis(50), () -> localRoutingState
                .getInstalledVersionState()
                .contains(ns, code, version), () -> {
                StringBuilder sb = new StringBuilder();
                sb
                    .append("aliasState=")
                    .append(localRoutingState.getAliasRouteState().resolve(ns, code, "prod").orElse(null))
                    .append('\n');
                sb.append("installedState=").append(localRoutingState.getInstalledVersionState()).append('\n');
                return sb.toString();
            });

            assertThat(localRoutingState.getInstalledVersionState().contains(ns, code, version))
                .as("Expected runtime to install demanded version and mark deployed snapshot")
                .isTrue();
        }
    }
}
