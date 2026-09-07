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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Alias Weighted Routing Integration Tests")
@Tag("integration")
@Tag("routing")
@Tag("gray-release")
@Execution(ExecutionMode.SAME_THREAD)
class AliasWeightedRoutingIntegrationTest {
    private static final String STATE_PREFIX = "compileflow.test.routing.";
    private static final String DEFAULT_NAMESPACE = "default";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ASYNC_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ASYNC_POLL_INTERVAL = Duration.ofMillis(50);
    private InMemoryProcessVersionStore versionRepository;
    private ProcessEngine engine;
    private LocalRoutingState localRoutingState;
    private InMemoryDeploymentProjectionStore projectionStore;
    private DeploymentRuntime deployRuntime;
    private ExecutorService executor;
    private AliasAdmission aliasAdmission;

    private static ProcessExecutionOptions routingOptions(String userId) {
        return ProcessExecutionOptions.builder().aliasRouting(new AliasRoutingOptions(userId)).build();
    }

    @BeforeEach
    void setUp() {
        // Constructs minimal dependencies: memory repository, engine, snapshots, router, and sync
        // projection store.
        versionRepository = new InMemoryProcessVersionStore();
        localRoutingState = new LocalRoutingState();
        aliasAdmission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState());
        ProcessEngineConfig config = ProcessEngineTestFactory.config();
        engine = EngineAssembly.create(config, EngineAssembly.assemble(config, localRoutingState, aliasAdmission));
        projectionStore = new InMemoryDeploymentProjectionStore();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-deploy-runtime");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        // Releases resources in reverse order of dependencies.
        try {
            if (deployRuntime != null) {
                deployRuntime.close();
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

    private void deployVersion(String code, String version, String content) {
        ProcessVersionRecord record = ProcessVersionRecord
            .builder()
            .namespace(DEFAULT_NAMESPACE)
            .code(code)
            .version(version)
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM, code, content),
                    Map.of()))
            .actor("test")
            .createdAt(System.currentTimeMillis())
            .build();

        versionRepository.save(record);
        // Deploy to engine
        engine
            .runtime()
            .load(ProcessRef.version(DEFAULT_NAMESPACE, code, version),
                    ProcessDefinition.inline(ProcessModelType.TBBPM, code, content));
    }

    private void publishAliasState(String code, String alias, String stableVersion, String candidateVersion,
            Integer candidateWeightBps, long revision) {
        String payload = RoutingStateCodec.aliasStateJson(DEFAULT_NAMESPACE, code, alias, stableVersion,
                candidateVersion, candidateWeightBps, revision, "test", System.currentTimeMillis());
        String key = RoutingStateKeys.aliasState(STATE_PREFIX, DEFAULT_NAMESPACE, code, alias);
        String current = projectionStore.read(key, OPERATION_TIMEOUT);
        assertThat(projectionStore.compareAndSet(key, current, payload, "json", OPERATION_TIMEOUT)).isTrue();
    }

    // ========== Helper Methods ==========
    private String createFlowContent(String code, String version) {
        // Create a minimal valid TBBPM flow with version marker as return variable
        // Keep the deployment fixture on the public Script(language, source) contract.
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <var name=\"version_marker\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n" + "\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n"
                + "        <transition to=\"mark_version\"/>\n" + "    </start>\n" + "\n"
                + "    <scriptTask id=\"mark_version\" name=\"Mark Version\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"java\">\n"
                + "            <output target=\"version_marker\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[return \"" + version + "\";]]></code>\n" + "        </action>\n"
                + "        <transition to=\"end\"/>\n" + "    </scriptTask>\n" + "\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";
    }

    private boolean isVersion(String expectedVersion, ProcessResult<Map<String, Object>> result) {
        if (!result.isSuccess()) {
            return false;
        }

        try {
            // Check version_marker return variable
            String actualVersion = (String) result.getOutput().get("version_marker");
            return expectedVersion.equals(actualVersion);
        } catch (Exception failure) {
            return false;
        }
    }

    private String findRoutingKeyForVersion(String code, String alias, String expectedVersion) {
        for (int i = 0; i < 200_000; i++) {
            String candidate = "routing-key-" + i;
            if (expectedVersion.equals(routeVersion(code, alias, candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No routing key found for version " + expectedVersion);
    }

    private String routeVersion(String code, String alias, String userId) {
        return aliasAdmission
            .admit(ProcessRef.alias(DEFAULT_NAMESPACE, code, alias), new AliasRoutingOptions(userId))
            .version()
            .version();
    }

    private String executeAndGetMarker(String code, String alias, String userId) {
        String v = routeVersion(code, alias, userId);
        assertThat(v).isNotBlank();
        awaitVersionExecutable(code, v);

        ProcessRef.Alias ref = ProcessRef.alias(DEFAULT_NAMESPACE, code, alias);

        ProcessResult<Map<String, Object>> result = engine.execute(ref, Map.of(), routingOptions(userId));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isNotNull();
        return (String) result.getOutput().get("version_marker");
    }

    private void awaitVersionExecutable(String code, String version) {
        String description =
                "version executable namespace=" + DEFAULT_NAMESPACE + ", code=" + code + ", version=" + version;
        Callable<Boolean> executable = () -> canExecuteExplicitVersion(code, version);
        Awaiter.await(description, ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL, executable, () -> dumpRoutingState(code));
    }

    private boolean canExecuteExplicitVersion(String code, String version) {
        if (!localRoutingState.getInstalledVersionState().contains(DEFAULT_NAMESPACE, code, version)) {
            return false;
        }
        ProcessRef.Version ref = ProcessRef.version(DEFAULT_NAMESPACE, code, version);
        ProcessResult<Map<String, Object>> result = engine.execute(ref, Collections.emptyMap());
        return isVersion(version, result);
    }

    private String dumpRoutingState(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("namespace=").append(DEFAULT_NAMESPACE).append(", code=").append(code).append('\n');
        sb.append("installedState=").append(localRoutingState.getInstalledVersionState()).append('\n');
        sb.append("aliasState(prod)=<not-public> (validate via router)\n");
        return sb.toString();
    }

    @Nested
    @DisplayName("A. Baseline Routing Tests")
    class BaselineRoutingTests {
        @Test
        @DisplayName("should route 100% traffic to a stable version")
        void shouldRoute100PercentToSingleVersion() throws Exception {
            // Input: Deploy v1 and v2
            String code = "routing.baseline.single";
            deployVersion(code, "v1", createFlowContent(code, "v1"));
            deployVersion(code, "v2", createFlowContent(code, "v2"));
            // Setup DeploymentRuntime with routing state keys
            List<String> stateKeys =
                    Collections.singletonList(RoutingStateKeys.aliasState(STATE_PREFIX, DEFAULT_NAMESPACE, code, "prod"));
            deployRuntime = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, stateKeys, engine,
                    versionRepository, OPERATION_TIMEOUT, executor, localRoutingState);
            deployRuntime.start();

            publishAliasState(code, "prod", "v1", null, null, 1L);
            // Wait until alias routing becomes effective (indirectly observable via router result)
            String userV1 = "probe-user";
            Awaiter.await("alias state becomes effective for code=" + code + ", alias=prod", ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL, () -> routeVersion(code,
                    "prod", userV1)
                .equals("v1"), () -> dumpRoutingState(code));
            awaitVersionExecutable(code, "v1");
            // Execute: Execute 100 times with prod alias
            int v1Count = 0;
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                ProcessExecutionOptions options = routingOptions("user_" + i);
                ProcessResult<Map<String, Object>> result =
                        engine.execute(ProcessRef.alias(DEFAULT_NAMESPACE, code, "prod"), Map.of(), options);
                // Hard assertion: every execution must return version_marker
                if (!result.isSuccess() || result.getOutput() == null
                        || !result.getOutput().containsKey("version_marker")) {
                    fail(
                            "Execution did not return version_marker. success=" + result.isSuccess() + ", error="
                            + result.getError() + ", data=" + result.getOutput());
                }

                if (isVersion("v1", result)) {
                    v1Count++;
                }
            }
            // Assertion: All traffic should go to v1
            assertThat(v1Count).as("All executions should route to v1 (100%% weight)").isEqualTo(iterations);
        }

        @Test
        @DisplayName("should fail closed when an explicitly requested alias is not found")
        void shouldFailClosedWhenExplicitAliasIsNotFound() throws Exception {
            String code = "routing.baseline.fallback";

            assertThatThrownBy(() -> aliasAdmission.admit(ProcessRef.alias(DEFAULT_NAMESPACE, code, "dev"),
                    AliasRoutingOptions.defaults()))
                .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                    .isEqualTo(ErrorCode.CF_EXEC_011))
                .hasMessageContaining("No serving version route")
                .hasMessageContaining("dev");
        }
    }

    @Nested
    @DisplayName("B. Weighted Routing Tests")
    class WeightedRoutingTests {
        @Test
        @DisplayName("should split traffic 50/50 between stable and candidate")
        void shouldSplitTraffic5050() throws Exception {
            // Input: Deploy v1 and v2
            String code = "routing.weighted.split";
            deployVersion(code, "v1", createFlowContent(code, "v1"));
            deployVersion(code, "v2", createFlowContent(code, "v2"));
            // Setup DeploymentRuntime
            List<String> stateKeys =
                    Collections.singletonList(RoutingStateKeys.aliasState(STATE_PREFIX, DEFAULT_NAMESPACE, code, "prod"));
            deployRuntime = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, stateKeys, engine,
                    versionRepository, OPERATION_TIMEOUT, executor, localRoutingState);
            deployRuntime.start();

            publishAliasState(code, "prod", "v1", "v2", 5_000, 1L);
            String anyUser = "probe-user";
            Awaiter.await("alias state becomes effective for code=" + code + ", alias=prod", ASYNC_WAIT_TIMEOUT,
                    ASYNC_POLL_INTERVAL,
                    () -> {
                        String selected = routeVersion(code, "prod", anyUser);
                        return "v1".equals(selected) || "v2".equals(selected);
                    }, () -> dumpRoutingState(code));
            awaitVersionExecutable(code, "v1");
            awaitVersionExecutable(code, "v2");
            // Execute: Execute 1000 times (large sample for statistical confidence)
            int v1Count = 0;
            int v2Count = 0;
            int iterations = 1000;

            for (int i = 0; i < iterations; i++) {
                ProcessExecutionOptions options = routingOptions("user_" + i);
                ProcessResult<Map<String, Object>> result =
                        engine.execute(ProcessRef.alias(DEFAULT_NAMESPACE, code, "prod"), Map.of(), options);

                if (!result.isSuccess() || result.getOutput() == null
                        || !result.getOutput().containsKey("version_marker")) {
                    fail(
                            "Execution did not return version_marker. success=" + result.isSuccess() + ", error="
                            + result.getError() + ", data=" + result.getOutput());
                }

                if (isVersion("v1", result)) {
                    v1Count++;
                } else if (isVersion("v2", result)) {
                    v2Count++;
                }
            }
            // Assertion: Should be approximately 50/50 (allow ±5% deviation)
            double v1Ratio = (double) v1Count / iterations;
            double v2Ratio = (double) v2Count / iterations;

            assertThat(v1Ratio).as("V1 traffic should be around 50%% (±5%%)").isBetween(0.45, 0.55);

            assertThat(v2Ratio).as("V2 traffic should be around 50%% (±5%%)").isBetween(0.45, 0.55);

            assertThat(v1Count + v2Count).as("All requests should be selected").isEqualTo(iterations);
        }

        @Test
        void missingRoutingKeyUsesInvocationIdentityAsTheCohort() throws Exception {
            String code = "routing.weighted.invocation";
            deployVersion(code, "v1", createFlowContent(code, "v1"));
            deployVersion(code, "v2", createFlowContent(code, "v2"));
            List<String> stateKeys =
                    Collections.singletonList(RoutingStateKeys.aliasState(STATE_PREFIX, DEFAULT_NAMESPACE, code, "prod"));
            deployRuntime = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, stateKeys, engine,
                    versionRepository, OPERATION_TIMEOUT, executor, localRoutingState);
            deployRuntime.start();

            publishAliasState(code, "prod", "v1", "v2", 5_000, 1L);
            Awaiter.await("invocation cohort route becomes effective", ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL,
                    () -> {
                        String selected = routeVersion(code, "prod", "probe-user");
                        return "v1".equals(selected) || "v2".equals(selected);
                    }, () -> dumpRoutingState(code));
            awaitVersionExecutable(code, "v1");
            awaitVersionExecutable(code, "v2");

            String stableInvocation = findRoutingKeyForVersion(code, "prod", "v1");
            String candidateInvocation = findRoutingKeyForVersion(code, "prod", "v2");
            ProcessRef.Alias alias = ProcessRef.alias(DEFAULT_NAMESPACE, code, "prod");

            ProcessResult<Map<String, Object>> stable =
                    engine.execute(alias, Map.of(),
                            ProcessExecutionOptions.builder().invocationId(stableInvocation).build());
            ProcessResult<Map<String, Object>> candidate =
                    engine.execute(alias, Map.of(),
                            ProcessExecutionOptions.builder().invocationId(candidateInvocation).build());

            assertThat(stable.getOutput()).containsEntry("version_marker", "v1");
            assertThat(candidate.getOutput()).containsEntry("version_marker", "v2");
            assertThat(stable.getExecution().getInvocationId()).isEqualTo(stableInvocation);
            assertThat(candidate.getExecution().getInvocationId()).isEqualTo(candidateInvocation);
        }
    }

    @Nested
    @DisplayName("C. Gray Release / Canary Deployment Tests")
    class GrayReleaseTests {
        @Test
        @DisplayName("should support gradual rollout: 10% -> 50% -> 100%")
        void shouldSupportGradualRollout() throws Exception {
            // Input: Deploy v1 (stable) and v2 (new version)
            String code = "routing.gray.gradual";
            deployVersion(code, "v1", createFlowContent(code, "v1"));
            deployVersion(code, "v2", createFlowContent(code, "v2"));
            // Setup DeploymentRuntime
            List<String> stateKeys =
                    Collections.singletonList(RoutingStateKeys.aliasState(STATE_PREFIX, DEFAULT_NAMESPACE, code, "prod"));
            deployRuntime = DeploymentRuntimeTestSupport.sourceRuntime(projectionStore, stateKeys, engine,
                    versionRepository, OPERATION_TIMEOUT, executor, localRoutingState);
            deployRuntime.start();
            // Phase 1: 10% canary with deterministic routing-key cohorts.
            publishAliasState(code, "prod", "v1", "v2", 1_000, 1L);
            Awaiter.await("Alias prod revision 1 visible: " + code, ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL, () -> localRoutingState
                .getAliasRouteState()
                .resolve(DEFAULT_NAMESPACE, code, "prod")
                .filter(route -> route.revision() == 1L)
                .isPresent(), () -> dumpRoutingState(code));
            awaitVersionExecutable(code, "v1");
            awaitVersionExecutable(code, "v2");

            String userV2 = findRoutingKeyForVersion(code, "prod", "v2");
            String userV1 = findRoutingKeyForVersion(code, "prod", "v1");

            assertThat(routeVersion(code, "prod", userV2)).as("candidate cohort should route to v2 under 90/10").isEqualTo(
                    "v2");
            assertThat(routeVersion(code, "prod", userV1)).as("stable cohort should route to v1 under 90/10").isEqualTo(
                    "v1");

            assertThat(executeAndGetMarker(code, "prod", userV2)).isEqualTo("v2");
            assertThat(executeAndGetMarker(code, "prod", userV1)).isEqualTo("v1");
            // Phase 2: 50% rollout with deterministic routing-key cohorts.
            publishAliasState(code, "prod", "v1", "v2", 5_000, 2L);
            Awaiter.await("Alias prod revision 2 visible: " + code, ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL, () -> localRoutingState
                .getAliasRouteState()
                .resolve(DEFAULT_NAMESPACE, code, "prod")
                .filter(route -> route.revision() == 2L)
                .isPresent(), () -> dumpRoutingState(code));

            String userV2_5050 = findRoutingKeyForVersion(code, "prod", "v2");
            String userV1_5050 = findRoutingKeyForVersion(code, "prod", "v1");

            assertThat(routeVersion(code, "prod", userV2_5050))
                .as("candidate cohort should route to v2 under 50/50")
                .isEqualTo("v2");
            assertThat(routeVersion(code, "prod", userV1_5050)).as("stable cohort should route to v1 under 50/50").isEqualTo(
                    "v1");

            assertThat(executeAndGetMarker(code, "prod", userV2_5050)).isEqualTo("v2");
            assertThat(executeAndGetMarker(code, "prod", userV1_5050)).isEqualTo("v1");
            // Phase 3: 100% rollout.
            publishAliasState(code, "prod", "v2", null, null, 3L);
            String description = "phase3 alias state becomes effective for code=" + code + ", alias=prod";
            Callable<Boolean> aliasEffective =
                    () -> localRoutingState
                .getAliasRouteState()
                .resolve(DEFAULT_NAMESPACE, code, "prod")
                .filter(route -> route.revision() == 3L)
                .isPresent();
            Awaiter.await(description, ASYNC_WAIT_TIMEOUT, ASYNC_POLL_INTERVAL, aliasEffective, () -> dumpRoutingState(
                    code));

            String anyUser = "probe-user";
            assertThat(routeVersion(code, "prod", anyUser)).as("100% should always route to v2").isEqualTo("v2");
            assertThat(executeAndGetMarker(code, "prod", anyUser)).isEqualTo("v2");
        }
    }
}
