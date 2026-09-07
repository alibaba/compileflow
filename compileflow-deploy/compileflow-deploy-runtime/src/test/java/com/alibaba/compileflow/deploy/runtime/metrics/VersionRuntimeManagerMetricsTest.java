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

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics.RuntimeInstallReason;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.routing.DesiredRoutingState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VersionRuntimeManagerMetricsTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "metrics.process";
    private static final String ALIAS = "production";

    private static VersionRuntimeManager manager(ProcessArtifactResolver resolver, ProcessArtifactRuntimeLoader loader,
            LocalRoutingState localRoutingState, DeploymentRuntimeMetrics metrics) {
        return VersionRuntimeManager.withOwnedRetryScheduler(resolver, loader, Runnable::run, localRoutingState,
                Duration.ofMinutes(1), 2, metrics);
    }

    private static LocalRoutingReconciler applier(VersionRuntimeManager manager, LocalRoutingState localRoutingState) {
        return new LocalRoutingReconciler(manager, localRoutingState, java.util.Set.of());
    }

    private static ProcessArtifactResolver artifactResolver() {
        return key -> {
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(ProcessModelType.TBBPM, key.code(), "<flow/>");
            return new ProcessArtifact(key, definition, ProcessArtifactDigest.compute(definition, Map.of()));
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
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        VersionRuntimeManager manager = manager(artifactResolver(), loader, localRoutingState, metrics);
        try {
            applier(manager, localRoutingState).apply(route("v1", 1L)).get(1, TimeUnit.SECONDS);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE)).isEqualTo(1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE)).isEqualTo(1L);
        } finally {
            manager.close();
        }
    }

    @Test
    void recordsResolutionAndConvergenceFailuresOncePerAttempt() {
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        ProcessArtifactResolver resolver = key -> {
            throw new IllegalStateException("repository unavailable");
        };
        VersionRuntimeManager manager = manager(resolver, loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> applier(manager, localRoutingState)
                .apply(route("v1", 1L))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_RESOLUTION)).isEqualTo(
                    1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, AliasConvergenceReason.INSTALLATION)).isEqualTo(
                    1L);
        } finally {
            manager.close();
        }
    }

    @Test
    void recordsRuntimeLoadFailureOnceWithItsSpecificReason() {
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        doThrow(new IllegalStateException("runtime unavailable")).when(loader).load(any(ProcessArtifact.class));
        VersionRuntimeManager manager = manager(artifactResolver(), loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> manager
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
            manager.close();
        }
    }

    @Test
    void executionInstallationFailureDoesNotPretendToBeAliasConvergence() {
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        VersionRuntimeManager manager = manager(key -> {
            throw new IllegalStateException("repository unavailable");
        }, loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> manager
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
            manager.close();
        }
    }

    @Test
    void distinguishesLocalReadyPublicationFailureFromInstallationFailure() {
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        localRoutingState.applyAliasRoute(route("existing", 2L).toAliasRoute());
        VersionRuntimeManager manager = manager(artifactResolver(), loader, localRoutingState, metrics);
        try {
            assertThatThrownBy(() -> applier(manager, localRoutingState)
                .apply(route("stale", 1L))
                .get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(metrics.runtimeInstallCount(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE)).isEqualTo(1L);
            assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, AliasConvergenceReason.PUBLICATION)).isEqualTo(
                    1L);
        } finally {
            manager.close();
        }
    }
}
