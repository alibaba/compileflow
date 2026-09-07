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
package com.alibaba.compileflow.workbench.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDataMapper;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.workbench.server.process.CreateProcessRequest;
import com.alibaba.compileflow.workbench.server.execution.ProcessExecutionResponse;
import com.alibaba.compileflow.workbench.server.process.UpdateProcessRequest;
import com.alibaba.compileflow.workbench.server.process.ProcessController;
import com.alibaba.compileflow.workbench.server.execution.PublishedProcessExecutionService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EmbeddedDeploymentExecutionIntegrationTest {
    @Autowired
    private ProcessController flowController;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private PublishedProcessExecutionService executionService;
    @Autowired
    private ProcessDeploymentService processDeploymentService;
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private ProcessRuntimeManager processRuntimeManager;
    @Autowired
    private ProcessToolingService processToolingService;
    @Autowired
    private ProcessDataMapper processDataMapper;
    @Autowired
    private ProcessArtifactRuntimeLoader processRuntimeLoader;
    @Autowired
    private LocalRoutingState localRoutingState;
    @Autowired
    private ApplicationContext applicationContext;

    private static CreateDeploymentCommand deployment(String code, String version, String strategy,
            Integer canaryWeightBps, long expectedRouteRevision) {
        return new CreateDeploymentCommand(UUID.randomUUID().toString(), code, version, "production",
                expectedRouteRevision, strategy, null, canaryWeightBps, null);
    }

    private static String markerProcess(String code, String marker) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"" + code + "\" name=\"" + code + "\">\n"
                + "    <var name=\"version_marker\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n"
                + "        <transition to=\"marker\"/>\n" + "    </start>\n"
                + "    <scriptTask id=\"marker\" name=\"Marker\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"qlexpress\">\n"
                + "            <output target=\"version_marker\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[\"" + marker + "\"]]></code>\n" + "        </action>\n"
                + "        <transition to=\"end\"/>\n" + "    </scriptTask>\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";
    }

    @Test
    void composesOneEngineWithSharedRuntimeAndTooling() {
        assertThat(processRuntimeManager).isSameAs(processEngine.runtime());
        assertThat(processToolingService).isSameAs(processEngine.tooling());
        assertThat(processDataMapper).isNotNull();
        assertThat(processRuntimeLoader).isNotNull();
        assertThat(applicationContext.getBeansOfType(ProcessEngine.class)).containsOnlyKeys("processEngine");
        assertThat(applicationContext.getBeansOfType(ProcessDataMapper.class)).containsOnlyKeys("processDataMapper");
        assertThat(applicationContext.getBeansOfType(ProcessArtifactRuntimeLoader.class))
            .containsOnlyKeys("embeddedProcessRuntimeLoader");
        assertThat(applicationContext.getBeansOfType(ProcessRuntimeManager.class)).containsOnlyKeys("processEngine");
        assertThat(applicationContext.getBeansOfType(ProcessToolingService.class)).containsOnlyKeys("processEngine");
    }

    @Test
    void routesCanaryAndExecutesThePromotedLocalReadyVersion() {
        String code = "embedded-routing-" + System.currentTimeMillis();
        flowController.createProcess(
                new CreateProcessRequest(code, "Embedded Routing", ProcessModelType.TBBPM, markerProcess(code, "v1"),
                        null, Collections.emptyList()));

        publish(code, "v1", markerProcess(code, "v1"));
        DeploymentView baseline = deploymentService.createDeployment(deployment(code, "v1", "all_at_once", null, 0L));
        assertThat(baseline.version).isEqualTo("v1");

        flowController.updateProcess(code,
                new UpdateProcessRequest("Embedded Routing", markerProcess(code, "v2"), null, Collections.emptyList(),
                        0L));
        publish(code, "v2", markerProcess(code, "v2"));
        DeploymentView canary =
                deploymentService.createDeployment(deployment(code, "v2", "canary", 5_000, baseline.routeRevision));
        assertThat(canary.baselineVersion).isEqualTo("v1");
        assertThat(localRoutingState.getAliasRouteState().resolve("default", code, "production"))
            .hasValueSatisfying(route -> {
                assertThat(route.stableVersion().version()).isEqualTo("v1");
                assertThat(route.candidateVersion().version()).isEqualTo("v2");
                assertThat(route.candidateWeightBps()).isEqualTo(5_000);
                assertThat(route.revision()).isEqualTo(canary.routeRevision);
            });

        DeploymentView promoted = deploymentService.promoteCanary(canary.id, canary.revision);
        assertThat(localRoutingState.getAliasRouteState().resolve("default", code, "production"))
            .hasValueSatisfying(route -> {
                assertThat(route.stableVersion().version()).isEqualTo("v2");
                assertThat(route.hasCandidate()).isFalse();
                assertThat(route.revision()).isEqualTo(promoted.routeRevision);
            });
        assertThat(promoted.version).isEqualTo("v2");

        assertExecution(code, "v2");
    }

    private void assertExecution(String code, String expectedVersion) {
        ProcessExecutionResponse response = executionService.execute(ProcessRef.alias(code, "production"),
                Collections.emptyMap(),
                ProcessExecutionOptions.builder().aliasRouting(new AliasRoutingOptions("integration-user")).build());

        assertThat(response.success()).isTrue();
        assertThat(response.result()).containsEntry("version_marker", expectedVersion);
        assertThat(response.routing().effectiveVersion()).isEqualTo(expectedVersion);
    }

    private void publish(String code, String version, String xml) {
        processDeploymentService.publish(
                new PublishProcessVersionCommand(ProcessRef.version("default", code, version),
                        ProcessDefinition.inline(ProcessModelType.TBBPM, code, xml), "integration-test",
                        Collections.emptyMap()));
    }
}
