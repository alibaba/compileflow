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
package com.alibaba.compileflow.deploy.runtime.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.AliasVersionDemand;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeInstallerSnapshotTest {
    private static AliasVersionDemand decision(ProcessRef.Version key, Set<ProcessRef.Version> versions) {
        return AliasVersionDemand.forAlias(key.namespace(), key.code(), "production", versions);
    }

    private static RuntimeInstaller installer(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            LocalRoutingState localRoutingState, Duration failureBackoff) {
        return RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, Runnable::run, localRoutingState,
                failureBackoff, 1_000);
    }

    @Test
    void snapshotExposesDemandedAndDeployedVersions() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactResolver resolver = mock(ProcessArtifactResolver.class);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessRef.Version key = ProcessRef.version("default", "payment.approve", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(key.code(), "<flow/>");
        when(resolver.resolve(key))
            .thenReturn(
                    new ProcessArtifact(key, ProcessModelType.TBBPM, definition,
                            ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of())));
        doNothing().when(loader).load(any(ProcessArtifact.class));
        try (RuntimeInstaller installer = installer(resolver, loader, localRoutingState, Duration.ofMinutes(5))) {
            installer.ensureInstalled(decision(key, Collections.singleton(key)));

            RuntimeInstallerSnapshot snapshot = installer.snapshot();
            assertThat(snapshot.getInflightCount()).isZero();
            assertThat(snapshot.getDemandedVersions()).containsExactly(key);
            assertThat(snapshot.getRetainedRuntimeCount()).isEqualTo(1);
            assertThat(snapshot.getPendingReleaseVersions()).isEmpty();
            assertThat(snapshot.getInstalledVersions())
                .containsExactly(
                        new RuntimeInstallerSnapshot.InstalledProcessSnapshot("default", "payment.approve",
                                Collections.singletonList("v1")));
        }
    }

    @Test
    void snapshotDeduplicatesVersionsDemandedByMultipleOwners() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactResolver resolver = mock(ProcessArtifactResolver.class);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessRef.Version key = ProcessRef.version("default", "payment.shared", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(key.code(), "<flow/>");
        when(resolver.resolve(key))
            .thenReturn(
                    new ProcessArtifact(key, ProcessModelType.TBBPM, definition,
                            ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of())));
        doNothing().when(loader).load(any(ProcessArtifact.class));
        try (RuntimeInstaller installer = installer(resolver, loader, localRoutingState, Duration.ofMinutes(5))) {
            installer.ensureInstalled(AliasVersionDemand.forAlias(key.namespace(), key.code(), "production",
                    Collections.singleton(key)));
            installer.ensureInstalled(AliasVersionDemand.forAlias(key.namespace(), key.code(), "staging",
                    Collections.singleton(key)));

            assertThat(installer.snapshot().getDemandedVersions()).containsExactly(key);
        }
    }

    @Test
    void snapshotExposesReleaseCleanupThatStillNeedsReconciliation() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactResolver resolver = mock(ProcessArtifactResolver.class);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessRef.Version key = ProcessRef.version("default", "payment.release", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(key.code(), "<flow/>");
        when(resolver.resolve(key))
            .thenReturn(
                    new ProcessArtifact(key, ProcessModelType.TBBPM, definition,
                            ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of())));
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.FAILED);
        try (RuntimeInstaller installer = installer(resolver, loader, localRoutingState, Duration.ofMinutes(1))) {
            installer.ensureInstalled(decision(key, Collections.singleton(key))).get();

            installer.ensureInstalled(decision(key, Collections.emptySet())).get();

            RuntimeInstallerSnapshot snapshot = installer.snapshot();
            assertThat(snapshot.getRetainedRuntimeCount()).isEqualTo(1);
            assertThat(snapshot.getPendingReleaseVersions()).containsExactly(key);
        }
    }

    @Test
    void snapshotExposesBackedOffInstallFailures() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactResolver resolver = mock(ProcessArtifactResolver.class);
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessRef.Version key = ProcessRef.version("default", "payment.fail", "v2");
        when(resolver.resolve(key)).thenThrow(new IllegalStateException("artifact missing"));
        try (RuntimeInstaller installer = installer(resolver, loader, localRoutingState, Duration.ofMinutes(1))) {
            installer.ensureInstalled(decision(key, Collections.singleton(key)));

            RuntimeInstallerSnapshot snapshot = installer.snapshot();
            assertThat(snapshot.getBackedOffVersions()).hasSize(1);
            RuntimeInstallerSnapshot.BackedOffVersion entry = snapshot.getBackedOffVersions().get(0);
            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getReason()).isEqualTo(IllegalStateException.class.getName());
            assertThat(entry.getRemainingMs()).isGreaterThan(0L);
        }
    }
}
