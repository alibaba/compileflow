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
package com.alibaba.compileflow.deploy.runtime.routing;

import com.alibaba.compileflow.engine.ProcessModelType;
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
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
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

    private static VersionRuntimeManager manager(ProcessArtifactRuntimeLoader loader, Executor executor,
            LocalRoutingState localRoutingState) {
        return manager(loader, executor, localRoutingState, Duration.ofMinutes(1));
    }

    private static VersionRuntimeManager manager(ProcessArtifactRuntimeLoader loader, Executor executor,
            LocalRoutingState localRoutingState, Duration failureBackoff) {
        ProcessArtifactResolver resolver =
                key -> {
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(ProcessModelType.TBBPM, key.code(), "<definitions/>");
            ProcessRef.Version ref = ProcessRef.version(key.namespace(), key.code(), key.version());
            return new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        };
        return VersionRuntimeManager.withOwnedRetryScheduler(resolver, loader, executor, localRoutingState,
                failureBackoff, 1_000);
    }

    private static LocalRoutingReconciler applier(VersionRuntimeManager manager, LocalRoutingState localRoutingState) {
        return new LocalRoutingReconciler(manager, localRoutingState, java.util.Set.of());
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
        VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
        LocalRoutingReconciler reconciler =
                new LocalRoutingReconciler(manager, new LocalRoutingState(), java.util.Set.of());

        assertThatThrownBy(() -> reconciler.apply(targetedRoute()))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED))
            .hasMessageContaining("enterprise-cohort");
        verify(manager, never()).ensureInstalled(any(), any());
    }

    @Test
    void registeredTargetingPolicyAllowsLocalReadyConvergence() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler reconciler =
                new LocalRoutingReconciler(manager, localRoutingState, java.util.Set.of("enterprise-cohort"));
        try {
            reconciler.apply(targetedRoute()).get(1, TimeUnit.SECONDS);

            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.targeting())
                    .isEqualTo(new AliasTargeting("enterprise-cohort")));
        } finally {
            manager.close();
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
        VersionRuntimeManager manager = manager(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
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
            manager.close();
            executor.shutdownNow();
        }
    }

    @Test
    void sharedRuntimeIsReleasedOnlyAfterTheLastAliasStopsUsingIt() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
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
            manager.close();
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
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        try {
            applier.apply(route("production", "v1", 1L)).get(1, TimeUnit.SECONDS);
            applier.apply(route("production", "v2", 2L)).get(1, TimeUnit.SECONDS);

            assertThat(routeSwappedBeforeRelease).isTrue();
        } finally {
            manager.close();
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
        VersionRuntimeManager manager = manager(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
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
            manager.close();
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
        VersionRuntimeManager manager = manager(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        DesiredRoutingState desired = route("production", "v1", 1L);
        try (LocalRoutingConvergence waiter = new LocalRoutingConvergence(applier, 1)) {
            CompletableFuture<Void> convergence = applier.apply(desired);
            assertThat(loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(applier.apply(desired)).isSameAs(convergence);

            assertThatThrownBy(() -> waiter.applyAndAwait(desired, Duration.ofMillis(10)))
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
            manager.close();
            executor.shutdownNow();
        }
    }

    @Test
    void awaitRequiresAPositiveWholeMillisecondTimeout() {
        VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(manager, new LocalRoutingState(), java.util.Set.of());
        DesiredRoutingState desired = route("production", "v1", 1L);

        try (LocalRoutingConvergence waiter = new LocalRoutingConvergence(applier, 1)) {
            assertThatThrownBy(() -> waiter.applyAndAwait(desired, Duration.ofNanos(1_500_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole-millisecond");
        }
        verify(manager, never()).ensureInstalled(any(), any());
    }

    @Test
    void awaitMapsCancelledConvergenceToTheDeploymentFailureContract() {
        VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        when(manager.ensureInstalled(any(), any())).thenReturn(cancelled);
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(manager, new LocalRoutingState(), java.util.Set.of());

        try (LocalRoutingConvergence waiter = new LocalRoutingConvergence(applier, 1)) {
            assertThatThrownBy(() -> waiter.applyAndAwait(route("production", "v1", 1L), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                    assertThat(failure.getAlias()).isEqualTo("production");
                })
                .hasMessageContaining("cancelled");
        }
    }

    @Test
    void awaitMapsSynchronousManagerFailureToTheDeploymentFailureContract() {
        VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
        when(manager.ensureInstalled(any(), any())).thenThrow(new IllegalStateException("manager unavailable"));
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(manager, new LocalRoutingState(), java.util.Set.of());

        try (LocalRoutingConvergence waiter = new LocalRoutingConvergence(applier, 1)) {
            assertThatThrownBy(() -> waiter.applyAndAwait(route("production", "v1", 1L), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                    assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
                })
                .hasMessageContaining("could not start");
        }
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
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState, Duration.ofMillis(10));
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        DesiredRoutingState desired = route("production", "v1", 1L);
        try {
            assertThatThrownBy(() -> applier.apply(desired).get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(applier.snapshot().getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            RoutingConvergenceSnapshot.ConvergenceState.FAILED);
                    assertThat(alias.getFailureReason()).isEqualTo(IllegalStateException.class.getName());
                });

            applier.apply(desired).get(1, TimeUnit.SECONDS);

            assertThat(attempts).hasValue(2);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.revision()).isEqualTo(1L));
            assertThat(applier.snapshot().getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            RoutingConvergenceSnapshot.ConvergenceState.LOCAL_READY);
                    assertThat(alias.getFailureReason()).isNull();
                });
        } finally {
            manager.close();
        }
    }

    @Test
    void ignoresOlderRevisionsAndRetainsTheTombstoneHighWatermark() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        try {
            applier.apply(route("production", "v1", Long.MAX_VALUE - 1)).get(1, TimeUnit.SECONDS);
            applier.apply(tombstone("production", Long.MAX_VALUE)).get(1, TimeUnit.SECONDS);

            applier.apply(route("production", "stale", Long.MAX_VALUE - 1)).get(1, TimeUnit.SECONDS);

            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production")).isEmpty();
            verify(loader, never())
                .load(org.mockito.ArgumentMatchers.argThat(artifact -> "stale".equals(artifact.getRef().version())));
        } finally {
            manager.close();
        }
    }

    @Test
    void rejectsConflictingContentAtTheSameRevision() throws Exception {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        try {
            applier.apply(route("production", "v1", 5L)).get(1, TimeUnit.SECONDS);

            assertThatThrownBy(() -> applier.apply(route("production", "v2", 5L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same revision")
                .hasMessageContaining("revision=5");
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("v1"));
        } finally {
            manager.close();
        }
    }

    @Test
    void synchronousManagerRejectionDoesNotAdvanceDesiredState() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        manager.close();

        assertThatThrownBy(() -> applier.apply(route("production", "v1", 1L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VersionRuntimeManager is closed");
        assertThat(applier.snapshot().getAliases()).isEmpty();
    }

    @Test
    void failsClosedWhenTheLocalReadySnapshotRejectsPublication() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        localRoutingState.applyAliasRoute(route("production", "existing", 2L).toAliasRoute());
        VersionRuntimeManager manager = manager(loader, Runnable::run, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        try {
            assertThatThrownBy(() -> applier.apply(route("production", "stale", 1L)).get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local-ready snapshot rejected authoritative alias revision");
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, "production"))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo("existing"));
            verify(loader).release(NAMESPACE, CODE, "stale");
        } finally {
            manager.close();
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
        VersionRuntimeManager manager = manager(loader, executor, localRoutingState);
        LocalRoutingReconciler applier = applier(manager, localRoutingState);
        try {
            applier.apply(route("production", "v1", 1L)).get(1, TimeUnit.SECONDS);
            CompletableFuture<Void> replacement = applier.apply(route("production", "v2", 2L));
            assertThat(replacementStarted.await(1, TimeUnit.SECONDS)).isTrue();

            RoutingConvergenceSnapshot state = applier.snapshot();

            assertThat(state.getDesiredAliasCount()).isEqualTo(1);
            assertThat(state.getLocalReadyAliasCount()).isEqualTo(1);
            assertThat(state.getPendingAliasCount()).isEqualTo(1);
            assertThat(state.getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getDesiredRevision()).isEqualTo(2L);
                    assertThat(alias.getLocalReadyRevision()).isEqualTo(1L);
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            RoutingConvergenceSnapshot.ConvergenceState.PENDING);
                });

            finishReplacement.countDown();
            replacement.get(1, TimeUnit.SECONDS);
            assertThat(applier.snapshot().getAliases())
                .singleElement()
                .satisfies(alias -> {
                    assertThat(alias.getLocalReadyRevision()).isEqualTo(2L);
                    assertThat(alias.getConvergenceState()).isEqualTo(
                            RoutingConvergenceSnapshot.ConvergenceState.LOCAL_READY);
                });
        } finally {
            finishReplacement.countDown();
            manager.close();
            executor.shutdownNow();
        }
    }
}
