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
package com.alibaba.compileflow.deploy.runtime.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.RuntimeInstallReason;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeInstallerMetricsTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "metrics.process";
    private static final String ALIAS = "production";

    private static RuntimeInstaller installer(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            LocalRoutingState localRoutingState, ProcessDeploymentMetrics metrics) {
        return RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, Runnable::run, localRoutingState,
                Duration.ofMinutes(1), 2, metrics);
    }

    private static LocalRoutingReconciler applier(RuntimeInstaller installer, LocalRoutingState localRoutingState) {
        return new LocalRoutingReconciler(new VersionDemandPlanner(), installer, localRoutingState, java.util.Set.of());
    }

    private static ProcessArtifactResolver artifactResolver() {
        return key -> {
            ProcessDefinition.Inline definition = ProcessDefinition.inline(key.code(), "<flow/>");
            return new ProcessArtifact(key, ProcessModelType.TBBPM, definition,
                    ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of()));
        };
    }

    private static DesiredRoutingState route(String version, long revision) {
        return DesiredRoutingState
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .alias(ALIAS)
            .stableVersion(version)
            .actor("test")
            .updatedAt(revision)
            .revision(revision)
            .build();
    }

    @Test
    void recordsSuccessfulInstallationAndAliasConvergence() throws Exception {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        RuntimeInstaller installer = installer(artifactResolver(), loader, localRoutingState, metrics);
        try {
            applier(installer, localRoutingState).apply(route("v1", 1L)).get(1, TimeUnit.SECONDS);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE)).isEqualTo(1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE)).isEqualTo(1L);
        } finally {
            installer.close();
        }
    }

    @Test
    void recordsResolutionAndConvergenceFailuresOncePerAttempt() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessArtifactResolver resolver = key -> {
            throw new IllegalStateException("repository unavailable");
        };
        RuntimeInstaller installer = installer(resolver, loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> applier(installer, localRoutingState)
                .apply(route("v1", 1L))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_RESOLUTION)).isEqualTo(
                    1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, AliasConvergenceReason.INSTALLATION)).isEqualTo(
                    1L);
        } finally {
            installer.close();
        }
    }

    @Test
    void recordsRuntimeLoadFailureOnceWithItsSpecificReason() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        doThrow(new IllegalStateException("runtime unavailable")).when(loader).load(any(ProcessArtifact.class));
        RuntimeInstaller installer = installer(artifactResolver(), loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> installer
                .acquireInstallation(ProcessRef.version(NAMESPACE, CODE, "v1"))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, RuntimeInstallReason.RUNTIME_LOAD)).isEqualTo(
                    1L);
            for (RuntimeInstallReason reason : RuntimeInstallReason.values()) {
                if (reason != RuntimeInstallReason.RUNTIME_LOAD) {
                    assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, reason)).isZero();
                }
            }
        } finally {
            installer.close();
        }
    }

    @Test
    void executionInstallationFailureDoesNotPretendToBeAliasConvergence() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        RuntimeInstaller installer = installer(key -> {
            throw new IllegalStateException("repository unavailable");
        }, loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> installer
                .acquireInstallation(ProcessRef.version(NAMESPACE, CODE, "v1"))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_RESOLUTION)).isEqualTo(
                    1L);
            for (AliasConvergenceReason reason : AliasConvergenceReason.values()) {
                assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, reason)).isZero();
            }
        } finally {
            installer.close();
        }
    }

    @Test
    void distinguishesLocalReadyPublicationFailureFromInstallationFailure() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        localRoutingState.applyAliasRoute(route("existing", 2L).toAliasRoute());
        RuntimeInstaller installer = installer(artifactResolver(), loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> applier(installer, localRoutingState)
                .apply(route("stale", 1L))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE)).isEqualTo(1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, AliasConvergenceReason.PUBLICATION)).isEqualTo(
                    1L);
        } finally {
            installer.close();
        }
    }
}
