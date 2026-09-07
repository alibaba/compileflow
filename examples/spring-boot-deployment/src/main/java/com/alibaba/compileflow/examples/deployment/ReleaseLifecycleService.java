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
package com.alibaba.compileflow.examples.deployment;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.command.RollbackRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class ReleaseLifecycleService {
    private static final String NAMESPACE = "default";
    private static final String ALIAS = "production";
    private static final String ACTOR = "release-operator";
    private final ProcessDeploymentService deploymentService;
    private final ProcessEngine processEngine;
    private final ProcessArtifactSource artifactSource;

    ReleaseLifecycleService(ProcessDeploymentService deploymentService, ProcessEngine processEngine,
            ProcessArtifactSource artifactSource) {
        this.deploymentService = deploymentService;
        this.processEngine = processEngine;
        this.artifactSource = artifactSource;
    }

    ReleaseLifecycleReport run() {
        String runKey = UUID.randomUUID().toString().replace("-", "");
        String code = "deployment.example.release." + runKey;
        ProcessRef.Version v1 = publish(code, "v1");
        ProcessRef.Version v2 = publish(code, "v2");
        ProcessRef.Version v3 = publish(code, "v3");
        ProcessRef.Alias alias = ProcessRef.alias(NAMESPACE, code, ALIAS);

        ProcessRollout initial = deploymentService.createRollout(CreateRolloutCommand.allAtOnce(runKey + "-initial",
                alias, v1, 0L, ACTOR, "Establish the production baseline"));
        var exactV1 = execute(v1, "exact-v1", null);
        var initialStable = execute(alias, "initial-stable", "initial-customer");

        ProcessRollout canary = deploymentService.createRollout(CreateRolloutCommand.canary(runKey + "-canary-v2", alias,
                v2, initial.getAliasRevision(), 1_000, ACTOR, "Expose ten percent of deterministic cohorts to v2"));
        var canarySamples = findBothCanaryTargets(alias, v1, v2);
        ProcessRollout widened = deploymentService.updateCanaryWeight(
                new UpdateCanaryWeightCommand(canary.getId(), 5_000, canary.getRolloutRevision(), ACTOR));
        ProcessRollout promoted = deploymentService.promoteRollout(
                new PromoteRolloutCommand(widened.getId(), widened.getRolloutRevision(), ACTOR));
        var promotedExecution = execute(alias, "promoted", "post-promotion-customer");

        ProcessRollout v3Canary = deploymentService.createRollout(CreateRolloutCommand.canary(runKey + "-canary-v3",
                alias, v3, promoted.getAliasRevision(), 1_000, ACTOR, "Probe v3 before aborting the release"));
        ProcessRollout aborted = deploymentService.abortRollout(
                new AbortRolloutCommand(v3Canary.getId(), v3Canary.getRolloutRevision(), ACTOR,
                        "Synthetic health regression"));
        var abortedExecution = execute(alias, "after-abort", "post-abort-customer");

        ProcessRollout rollback = deploymentService.rollbackRollout(
                new RollbackRolloutCommand(promoted.getId(), runKey + "-rollback-v1", aborted.getAliasRevision(), ACTOR));
        var rolledBackExecution = execute(alias, "after-rollback", "post-rollback-customer");
        var artifact = artifactSource.find(v2).orElseThrow();
        processEngine.runtime().load(v2, artifact.getDefinition());
        ReleaseLifecycleReport.ExecutionObservation exactV2;
        try {
            exactV2 = execute(v2, "exact-v2-after-rollback", null);
        } finally {
            processEngine.runtime().unload(v2);
        }

        return new ReleaseLifecycleReport(code, exactV1, initialStable, canarySamples.stable(),
                canarySamples.candidate(), promotedExecution, abortedExecution, rolledBackExecution, exactV2,
                List.of(observe("initial", initial), observe("canary-created", canary),
                        observe("canary-widened", widened), observe("promoted", promoted),
                        observe("v3-canary", v3Canary), observe("aborted", aborted), observe("rollback", rollback)));
    }

    private ProcessRef.Version publish(String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(NAMESPACE, code, version);
        deploymentService.publish(
                new PublishProcessVersionCommand(ref,
                        ProcessDefinition.inline(ProcessModelType.TBBPM, code, markerFlow(code, version)), ACTOR,
                        Map.of("release.channel", "example", "release.version", version)));
        return ref;
    }

    private ReleaseLifecycleReport.ExecutionObservation execute(ProcessRef ref, String invocation, String routingKey) {
        ProcessExecutionOptions.Builder options = ProcessExecutionOptions.builder().invocationId(invocation);
        if (routingKey != null) {
            options.aliasRouting(new AliasRoutingOptions(routingKey, Map.of("region", "cn-east")));
        }
        var result = processEngine.execute(ref, Map.of(), options.build());
        Map<String, Object> output = result.orElseThrow();
        ProcessRef.Version selected = result.getExecution().getProcessVersion();
        return new ReleaseLifecycleReport.ExecutionObservation(ref instanceof ProcessRef.Alias ? ALIAS : "exact",
                selected == null ? null : selected.version(), String.valueOf(output.get("version_marker")), routingKey);
    }

    private CanarySamples findBothCanaryTargets(ProcessRef.Alias alias, ProcessRef.Version stable,
            ProcessRef.Version candidate) {
        ReleaseLifecycleReport.ExecutionObservation stableSample = null;
        ReleaseLifecycleReport.ExecutionObservation candidateSample = null;
        for (int index = 0; index < 10_000 && (stableSample == null || candidateSample == null); index++) {
            String routingKey = "customer-" + index;
            var sample = execute(alias, "canary-" + index, routingKey);
            if (stable.version().equals(sample.selectedVersion())) {
                stableSample = sample;
            } else if (candidate.version().equals(sample.selectedVersion())) {
                candidateSample = sample;
            }
        }
        if (stableSample == null || candidateSample == null) {
            throw new IllegalStateException("Could not observe both deterministic canary cohorts");
        }
        return new CanarySamples(stableSample, candidateSample);
    }

    private ReleaseLifecycleReport.RolloutObservation observe(String operation, ProcessRollout rollout) {
        int auditEventCount = deploymentService.listRolloutEvents(rollout.getId()).size();
        return new ReleaseLifecycleReport.RolloutObservation(operation, rollout.getId(), rollout.getPhase().name(),
                rollout.getAliasRevision(), rollout.getRolloutRevision(), rollout.getTargetWeightBps(), auditEventCount);
    }

    private static String markerFlow(String code, String marker) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Release %s">
                <var name="version_marker" dataType="java.lang.String" inOutType="return"/>
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="mark"/>
                </start>
                <scriptTask id="mark" name="Record version" g="150,40,88,48">
                    <action type="script" language="java">
                        <output target="version_marker" dataType="java.lang.String"/>
                        <code><![CDATA[return "%s";]]></code>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" name="End" g="300,50,32,32"/>
            </bpm>
            """
            .formatted(code, marker, marker);
    }

    private record CanarySamples(ReleaseLifecycleReport.ExecutionObservation stable,
            ReleaseLifecycleReport.ExecutionObservation candidate) {}
}
