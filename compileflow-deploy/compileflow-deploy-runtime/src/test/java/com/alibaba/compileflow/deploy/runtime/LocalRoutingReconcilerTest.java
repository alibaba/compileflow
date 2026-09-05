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
package com.alibaba.compileflow.deploy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.AliasVersionDemand;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class LocalRoutingReconcilerTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "order.alias";

    private static RuntimeInstaller installer(ProcessArtifactRuntimeLoader loader, Executor executor,
            LocalRoutingState localRoutingState) {
        return installer(loader, executor, localRoutingState, Duration.ofMinutes(1));
    }

    private static RuntimeInstaller installer(ProcessArtifactRuntimeLoader loader, Executor executor,
            LocalRoutingState localRoutingState, Duration failureBackoff) {
        ProcessArtifactResolver resolver =
                key -> {
            ProcessDefinition.Inline definition = ProcessDefinition.inline(key.code(), "<definitions/>");
            ProcessRef.Version ref = ProcessRef.version(key.namespace(), key.code(), key.version());
            return new ProcessArtifact(ref, ProcessModelType.TBBPM, definition,
                    ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, Map.of()));
        };
        return RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, executor, localRoutingState, failureBackoff,
                1_000);
    }

    private static LocalRoutingReconciler applier(RuntimeInstaller installer, LocalRoutingState localRoutingState) {
        return new LocalRoutingReconciler(new VersionDemandPlanner(), installer, localRoutingState, java.util.Set.of());
    }

    private static void awaitUndeployed(LocalRoutingState localRoutingState, String version) {
        Awaitility
            .await()
            .atMost(1, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(localRoutingState
                .getInstalledVersionState()
                .contains(NAMESPACE, CODE, version))
                .isFalse());
    }

    private static DesiredRoutingState route(String alias, String version, long revision) {
        return state(alias, version, false, revision);
    }

    private static DesiredRoutingState tombstone(String alias, long revision) {
        return state(alias, null, true, revision);
    }

    private static DesiredRoutingState state(String alias, String stableVersion, boolean deleted, long revision) {
        return DesiredRoutingState
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .alias(alias)
            .stableVersion(stableVersion)
            .deleted(deleted)
            .actor("test")
            .updatedAt(revision)
            .revision(revision)
            .build();
    }

    private static DesiredRoutingState targetedRoute() {
        return DesiredRoutingState
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .alias("production")
            .stableVersion("v1")
            .candidateVersion("v2")
            .candidateWeightBps(500)
            .targeting(new AliasTargeting("enterprise-cohort"))
            .actor("test")
            .updatedAt(1L)
            .revision(1L)
            .build();
    }

    @Test
    void targetingPolicyMustBeRegisteredBeforeLocalReadyConvergence() {
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        LocalRoutingReconciler reconciler = new LocalRoutingReconciler(new VersionDemandPlanner(), installer,
                new LocalRoutingState(), java.util.Set.of());

        assertThatThrownBy(() -> reconciler.apply(targetedRoute()))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED))
            .hasMessageContaining("enterprise-cohort");
        verify(installer, never()).ensureInstalled(any(), any());
    }

    @Test
    void registeredTargetingPolicyAllowsLocalReadyConvergence() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler reconciler = new LocalRoutingReconciler(new VersionDemandPlanner(), installer,
                localRoutingState, java.util.Set.of("enterprise-cohort"));
        try {
            reconciler.apply(targetedRoute()).get(1, TimeUnit.SECONDS);

            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.targeting())
                    .isEqualTo(new AliasTargeting("enterprise-cohort")));
        } finally {
            installer.close();
        }
    }

    @Test
    void oneAliasLoadDoesNotBlockAnotherAlias() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch previewStarted = new CountDownLatch(1);
        CountDownLatch releasePreview = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v-preview".equals(ref.version())) {
                previewStarted.countDown();
                assertThat(releasePreview.await(2, TimeUnit.SECONDS)).isTrue();
                throw new IllegalStateException("preview compile failed");
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        RuntimeInstaller installer = installer(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            CompletableFuture<Void> preview = applier.apply(route("preview", "v-preview", 1L));
            assertThat(previewStarted.await(1, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<Void> production = applier.apply(route("production", "v-production", 1L));

            production.get(1, TimeUnit.SECONDS);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v-production"));
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "preview")).isEmpty();

            releasePreview.countDown();
            assertThatThrownBy(() -> preview.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production")).isPresent();
        } finally {
            releasePreview.countDown();
            installer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void sharedRuntimeIsReleasedOnlyAfterTheLastAliasStopsUsingIt() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            applier.apply(route("production", "v1", 1L)).get(1, TimeUnit.SECONDS);
            applier.apply(route("preview", "v1", 1L)).get(1, TimeUnit.SECONDS);

            verify(loader, times(1)).load(any(ProcessArtifact.class));

            applier.apply(tombstone("production", 2L)).get(1, TimeUnit.SECONDS);

            verify(loader, never()).release(NAMESPACE, CODE, "v1");
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "preview")).isPresent();

            applier.apply(tombstone("preview", 2L)).get(1, TimeUnit.SECONDS);

            verify(loader, times(1)).release(NAMESPACE, CODE, "v1");
            assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isFalse();
        } finally {
            installer.close();
        }
    }

    @Test
    void swapsLocalReadyRouteBeforeReleasingTheOldRuntime() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        AtomicBoolean routeSwappedBeforeRelease = new AtomicBoolean(false);
        doAnswer(invocation -> {
            routeSwappedBeforeRelease.set(localRoutingState
                .getAliasRouteState()
                .resolve(NAMESPACE, CODE, "production")
                .map(route -> "v2".equals(route.stableVersion().version()))
                .orElse(false));
            return ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED;
        }).when(loader).release(NAMESPACE, CODE, "v1");
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            applier.apply(route("production", "v1", 1L)).get(1, TimeUnit.SECONDS);
            applier.apply(route("production", "v2", 2L)).get(1, TimeUnit.SECONDS);

            assertThat(routeSwappedBeforeRelease).isTrue();
        } finally {
            installer.close();
        }
    }

    @Test
    void supersededLoadCannotPublishOrLeakItsRuntime() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch staleStarted = new CountDownLatch(1);
        CountDownLatch releaseStale = new CountDownLatch(1);
        CountDownLatch staleReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v1".equals(ref.version())) {
                staleStarted.countDown();
                assertThat(releaseStale.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        doAnswer(invocation -> {
            if ("v1".equals(invocation.getArgument(2))) {
                staleReleased.countDown();
            }
            return ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED;
        }).when(loader).release(any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        RuntimeInstaller installer = installer(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            CompletableFuture<Void> superseded = applier.apply(route("production", "v1", 1L));
            assertThat(staleStarted.await(1, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<Void> newest = applier.apply(route("production", "v2", 2L));
            newest.get(1, TimeUnit.SECONDS);
            superseded.get(1, TimeUnit.SECONDS);

            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v2"));

            releaseStale.countDown();
            assertThat(staleReleased.await(1, TimeUnit.SECONDS)).isTrue();
            awaitUndeployed(localRoutingState, "v1");
        } finally {
            releaseStale.countDown();
            installer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void callerTimeoutDoesNotCancelLocalReadyConvergence() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadStarted.countDown();
            assertThat(releaseLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        RuntimeInstaller installer = installer(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        DesiredRoutingState desired = route("production", "v1", 1L);
        try {
            CompletableFuture<Void> convergence = applier.apply(desired);
            assertThat(loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(applier.apply(desired)).isSameAs(convergence);

            assertThatThrownBy(() -> applier.applyAndAwait(desired, Duration.ofMillis(10)))
                .isInstanceOf(DeploymentException.class)
                .satisfies(failure -> assertThat(((DeploymentException) failure).getErrorCode())
                    .isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED))
                .hasMessageContaining("timed out");
            assertThat(convergence.isCancelled()).isFalse();
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production")).isEmpty();

            releaseLoad.countDown();
            convergence.get(1, TimeUnit.SECONDS);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production")).isPresent();
        } finally {
            releaseLoad.countDown();
            installer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void awaitRequiresAPositiveWholeMillisecondTimeout() {
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        LocalRoutingReconciler applier = new LocalRoutingReconciler(mock(VersionDemandPlanner.class), installer,
                new LocalRoutingState(), java.util.Set.of());
        DesiredRoutingState desired = route("production", "v1", 1L);

        assertThatThrownBy(() -> applier.applyAndAwait(desired, Duration.ofNanos(1_500_000)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whole-millisecond");
        verify(installer, never()).ensureInstalled(any(), any());
    }

    @Test
    void awaitMapsCancelledConvergenceToTheDeploymentFailureContract() {
        VersionDemandPlanner planner = mock(VersionDemandPlanner.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        AliasVersionDemand demand = mock(AliasVersionDemand.class);
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        when(planner.plan(any())).thenReturn(demand);
        when(installer.ensureInstalled(any(), any())).thenReturn(cancelled);
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(planner, installer, new LocalRoutingState(), java.util.Set.of());

        assertThatThrownBy(() -> applier.applyAndAwait(route("production", "v1", 1L), Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                assertThat(failure.getAlias()).isEqualTo("production");
            })
            .hasMessageContaining("cancelled");
    }

    @Test
    void awaitMapsSynchronousInstallerFailureToTheDeploymentFailureContract() {
        VersionDemandPlanner planner = mock(VersionDemandPlanner.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        when(planner.plan(any())).thenReturn(mock(AliasVersionDemand.class));
        when(installer.ensureInstalled(any(), any())).thenThrow(new IllegalStateException("installer unavailable"));
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(planner, installer, new LocalRoutingState(), java.util.Set.of());

        assertThatThrownBy(() -> applier.applyAndAwait(route("production", "v1", 1L), Duration.ofSeconds(1)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
            })
            .hasMessageContaining("could not start");
    }

    @Test
    void failedDuplicateRevisionSharesAutomaticConvergenceWithoutAdvancingDesiredState() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary compilation failure");
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState, Duration.ofMillis(10));
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        DesiredRoutingState desired = route("production", "v1", 1L);
        try {
            assertThatThrownBy(() -> applier.apply(desired).get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            applier.apply(desired).get(1, TimeUnit.SECONDS);

            assertThat(attempts).hasValue(2);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.revision()).isEqualTo(1L));
        } finally {
            installer.close();
        }
    }

    @Test
    void ignoresOlderRevisionsAndRetainsTheTombstoneHighWatermark() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            applier.apply(route("production", "v1", Long.MAX_VALUE - 1)).get(1, TimeUnit.SECONDS);
            applier.apply(tombstone("production", Long.MAX_VALUE)).get(1, TimeUnit.SECONDS);

            applier.apply(route("production", "stale", Long.MAX_VALUE - 1)).get(1, TimeUnit.SECONDS);

            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production")).isEmpty();
            verify(loader, never())
                .load(org.mockito.ArgumentMatchers.argThat(artifact -> "stale".equals(artifact.getRef().version())));
        } finally {
            installer.close();
        }
    }

    @Test
    void rejectsConflictingContentAtTheSameRevision() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            applier.apply(route("production", "v1", 5L)).get(1, TimeUnit.SECONDS);

            assertThatThrownBy(() -> applier.apply(route("production", "v2", 5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same revision")
                .hasMessageContaining("revision=5");
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v1"));
        } finally {
            installer.close();
        }
    }

    @Test
    void synchronousInstallerRejectionDoesNotAdvanceDesiredState() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        installer.close();

        assertThatThrownBy(() -> applier.apply(route("production", "v1", 1L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RuntimeInstaller is closed");
        assertThat(applier.snapshot().getAliases()).isEmpty();
    }

    @Test
    void failsClosedWhenTheLocalReadySnapshotRejectsPublication() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        localRoutingState.applyAliasRoute(route("production", "existing", 2L).toAliasRoute());
        RuntimeInstaller installer = installer(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            assertThatThrownBy(() -> applier.apply(route("production", "stale", 1L)).get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local-ready snapshot rejected authoritative alias revision");
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("existing"));
            verify(loader).release(NAMESPACE, CODE, "stale");
        } finally {
            installer.close();
        }
    }

    @Test
    void snapshotKeepsThePreviousLocalReadyRevisionWhileAReplacementIsPending() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        CountDownLatch finishReplacement = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                replacementStarted.countDown();
                assertThat(finishReplacement.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RuntimeInstaller installer = installer(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(installer, localRoutingState);
        try {
            applier.apply(route("production", "v1", 1L)).get(1, TimeUnit.SECONDS);
            CompletableFuture<Void> replacement = applier.apply(route("production", "v2", 2L));
            assertThat(replacementStarted.await(1, TimeUnit.SECONDS)).isTrue();

            LocalReadyRoutingStateSnapshot state = applier.snapshot();

            assertThat(state.getDesiredAliasCount()).isEqualTo(1);
            assertThat(state.getLocalReadyAliasCount()).isEqualTo(1);
            assertThat(state.getPendingAliasCount()).isEqualTo(1);
            assertThat(state.getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getDesiredRevision()).isEqualTo(2L);
                    assertThat(alias.getLocalReadyRevision()).isEqualTo(1L);
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            LocalReadyRoutingStateSnapshot.ConvergenceState.PENDING);
                });

            finishReplacement.countDown();
            replacement.get(1, TimeUnit.SECONDS);
            assertThat(applier.snapshot().getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getLocalReadyRevision()).isEqualTo(2L);
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            LocalReadyRoutingStateSnapshot.ConvergenceState.LOCAL_READY);
                });
        } finally {
            finishReplacement.countDown();
            installer.close();
            executor.shutdownNow();
        }
    }
}
