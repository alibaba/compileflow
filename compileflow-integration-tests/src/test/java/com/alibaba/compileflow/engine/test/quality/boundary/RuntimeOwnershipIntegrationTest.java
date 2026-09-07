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
package com.alibaba.compileflow.engine.test.quality.boundary;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingState;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeOwnershipIntegrationTest {
    private static final String CODE = "test.runtime-ownership";
    private static final String FLOW =
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.runtime-ownership" name="Runtime Ownership">
            <start id="start" name="Start" g="50,50,32,32">
                <transition to="end"/>
            </start>
            <end id="end" name="End" g="140,50,32,32"/>
        </bpm>
        """;

    private static DesiredRoutingState aliasState(String stableVersion, boolean deleted, long revision) {
        return DesiredRoutingState
            .builder()
            .namespace("default")
            .code(CODE)
            .alias("production")
            .stableVersion(stableVersion)
            .deleted(deleted)
            .actor("test")
            .updatedAt(revision)
            .revision(revision)
            .build();
    }

    @Test
    void aliasTombstoneReleasesOnlyTheManagedOwner() {
        ProcessEngineConfig config = ProcessEngineTestFactory.builder().discoverPlugins(false).build();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRef.Version ref = ProcessRef.version("default", CODE, "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, FLOW);

        try (ProcessEngine engine = EngineAssembly.create(config, EngineAssembly.assemble(config, localRoutingState))) {
            engine.runtime().load(ref, definition);
            ProcessArtifactResolver resolver =
                    key -> new ProcessArtifact(key, definition, ProcessArtifactDigest.compute(definition, Map.of()));
            try (VersionRuntimeManager manager = VersionRuntimeManager.withOwnedRetryScheduler(resolver,
                    new ProcessArtifactRuntimeLoader(engine), Runnable::run, localRoutingState, Duration.ofMillis(10), 2)) {
                LocalRoutingReconciler applier = new LocalRoutingReconciler(manager, localRoutingState, Set.of());

                applier.apply(aliasState("v1", false, 1L)).join();
                assertThat(localRoutingState.getAliasRouteState().resolve("default", CODE, "production"))
                    .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v1"));

                applier.apply(aliasState(null, true, 2L)).join();

                assertThat(localRoutingState.getAliasRouteState().resolve("default", CODE, "production")).isEmpty();
                assertThat(manager.snapshot().getDemandedVersions()).isEmpty();
                assertThat(localRoutingState.getInstalledVersionState().contains("default", CODE, "v1")).isTrue();
                assertThat(engine.execute(ref, Collections.emptyMap()).isSuccess()).isTrue();
            }

            engine.runtime().unload(ref);
            assertThat(localRoutingState.getInstalledVersionState().contains("default", CODE, "v1")).isFalse();
        }
    }
}
