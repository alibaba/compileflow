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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeployDurableAdaptersTest {
    private static final ProcessRef.Alias ALIAS = ProcessRef.alias("sales", "approval", "prod");
    private static final ProcessRef.Version VERSION = ProcessRef.version("sales", "approval", "v1");
    private static final ProcessRef.Version OTHER_VERSION = ProcessRef.version("other", "approval", "v1");
    private static final String DEFINITION = "<bpm code=\"approval\"><start id=\"s\"/></bpm>";

    @Test
    void aliasStateIsMappedOnlyForNewRunAdmission() {
        ProcessAliasState state = new ProcessAliasState(ALIAS, VERSION, ProcessRef.version("sales", "approval", "v2"),
                2500, 7, "deployer", Instant.ofEpochMilli(1));
        ProcessDeploymentService deployments = (ProcessDeploymentService) Proxy.newProxyInstance(ProcessDeploymentService.class
            .getClassLoader(), new Class<?>[] {ProcessDeploymentService.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("getAlias")) {
                return Optional.of(state);
            }
            throw new AssertionError("Unexpected Deploy call: " + method.getName());
        });

        var mapped = new DeployAliasStateSourceAdapter(deployments).find(ALIAS).orElseThrow();

        assertThat(mapped.alias()).isEqualTo(ALIAS);
        assertThat(mapped.stableVersion()).isEqualTo(VERSION);
        assertThat(mapped.candidateVersion()).isEqualTo(ProcessRef.version("sales", "approval", "v2"));
        assertThat(mapped.candidateWeightBps()).isEqualTo(2500);
        assertThat(mapped.revision()).isEqualTo(7);
    }

    @Test
    void exactTbbpmArtifactIsVerifiedBeforeCrossingTheBoundary() {
        ProcessArtifact artifact = artifact(ProcessModelType.TBBPM, digest(ProcessModelType.TBBPM, VERSION));

        var mapped = new DeployDurableVersionDefinitionSourceAdapter(version -> Optional.of(artifact))
            .find(VERSION)
            .orElseThrow();

        assertThat(mapped.modelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(mapped.definition().code()).isEqualTo(VERSION.code());
        assertThat(mapped.definition().content()).isEqualTo(DEFINITION);
    }

    @Test
    void modelTypeIsPreservedAndDigestMismatchFailsClosed() {
        ProcessArtifact bpmn = artifact(ProcessModelType.BPMN, digest(ProcessModelType.BPMN, VERSION));
        var mapped =
                new DeployDurableVersionDefinitionSourceAdapter(version -> Optional.of(bpmn))
            .find(VERSION)
            .orElseThrow();
        assertThat(mapped.modelType()).isEqualTo(ProcessModelType.BPMN);

        ProcessArtifact corrupted = artifact(ProcessModelType.TBBPM, "0".repeat(64));
        assertThatThrownBy(() -> new DeployDurableVersionDefinitionSourceAdapter(version -> Optional.of(corrupted))
            .find(VERSION))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.ARTIFACT_DIGEST_MISMATCH));
    }

    @Test
    void mismatchedSourceIdentitiesFailClosed() {
        ProcessAliasState otherAlias = new ProcessAliasState(ProcessRef.alias("other", "approval", "prod"),
                ProcessRef.version("other", "approval", "v1"), null, null, 1, "deployer", Instant.ofEpochMilli(1));
        ProcessDeploymentService deployments = (ProcessDeploymentService) Proxy.newProxyInstance(ProcessDeploymentService.class
            .getClassLoader(), new Class<?>[] {ProcessDeploymentService.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("getAlias")) {
                return Optional.of(otherAlias);
            }
            throw new AssertionError("Unexpected Deploy call: " + method.getName());
        });
        assertThatThrownBy(() -> new DeployAliasStateSourceAdapter(deployments).find(ALIAS))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Alias");

        ProcessArtifact otherArtifact =
                artifact(OTHER_VERSION, ProcessModelType.TBBPM, digest(ProcessModelType.TBBPM, OTHER_VERSION));
        assertThatThrownBy(() -> new DeployDurableVersionDefinitionSourceAdapter(version -> Optional.of(otherArtifact))
            .find(VERSION))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different Process Version");
    }

    private static ProcessArtifact artifact(ProcessModelType modelType, String digest) {
        return artifact(VERSION, modelType, digest);
    }

    private static ProcessArtifact artifact(ProcessRef.Version version, ProcessModelType modelType, String digest) {
        return new ProcessArtifact(version, modelType, ProcessDefinition.inline(version.code(), DEFINITION), digest);
    }

    private static String digest(ProcessModelType modelType, ProcessRef.Version version) {
        return ProcessArtifactDigest.compute(modelType, ProcessDefinition.inline(version.code(), DEFINITION), Map.of());
    }
}
