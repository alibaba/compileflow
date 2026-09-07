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
package com.alibaba.compileflow.deploy.runtime.version;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.routing.AliasVersionDemand;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VersionRuntimeManagerReconciliationTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "order.process";
    private static final String ALIAS = "production";
    private ProcessArtifactResolver resolver;
    @Mock
    private ProcessArtifactRuntimeLoader loader;
    private LocalRoutingState localRoutingState;
    private ExecutorService executor;
    private ScheduledExecutorService retryScheduler;
    private VersionRuntimeManager manager;

    private static ProcessRef.Version key(String version) {
        return ProcessRef.version(NAMESPACE, CODE, version);
    }

    private static ProcessArtifact artifact(ProcessRef.Version ref) {
        return artifact(ref, List.of());
    }

    private static ProcessArtifact artifact(ProcessRef.Version ref, List<ProcessRef.Version> dependencies) {
        String content = "<flow/>";
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, ref.code(), content);
        List<ProcessCallBinding> bindings = new ArrayList<>();
        Map<String, ProcessRef.Version> targets = new LinkedHashMap<>();
        for (int index = 0; index < dependencies.size(); index++) {
            ProcessRef.Version dependency = dependencies.get(index);
            String callSiteId = "call-" + index;
            bindings.add(new ProcessCallBinding(callSiteId, dependency));
            targets.put(callSiteId, dependency);
        }
        return new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, targets), bindings);
    }

    private static void awaitCondition(BooleanSupplier condition) {
        Awaitility
            .await()
            .atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(condition.getAsBoolean()).isTrue());
    }

    @BeforeEach
    void setUp() throws Exception {
        localRoutingState = new LocalRoutingState();
        executor = Executors.newFixedThreadPool(2);
        retryScheduler = Executors.newSingleThreadScheduledExecutor();
        resolver = VersionRuntimeManagerReconciliationTest::artifact;
        manager = new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);
    }

    @AfterEach
    void tearDown() {
        manager.close();
        executor.shutdownNow();
        retryScheduler.shutdownNow();
    }

    @Test
    void concurrentDemandWaitsForGraphPreparation() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        CountDownLatch preparationStarted = new CountDownLatch(1);
        CountDownLatch finishPreparation = new CountDownLatch(1);
        doAnswer(invocation -> {
            preparationStarted.countDown();
            assertThat(finishPreparation.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(loader).prepare(key("v1"));

        CompletableFuture<VersionRuntimeLease> first = manager.acquireInstallation(key("v1"));
        try {
            assertThat(preparationStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<VersionRuntimeLease> second = manager.acquireInstallation(key("v1"));

            assertThat(second).isNotDone();
            finishPreparation.countDown();
            first.get(2, TimeUnit.SECONDS).close();
            second.get(2, TimeUnit.SECONDS).close();
        } finally {
            finishPreparation.countDown();
        }
    }

    @Test
    void keepsPreviousVersionUntilReplacementIsInstalled() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");
        CountDownLatch replacementStarted = new CountDownLatch(1);
        CountDownLatch releaseReplacement = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                replacementStarted.countDown();
                assertThat(releaseReplacement.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ensureInstalled(Collections.singleton(key("v2")));

        assertThat(replacementStarted.await(2, TimeUnit.SECONDS)).isTrue();
        verify(loader, never()).release(NAMESPACE, CODE, "v1");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isTrue();

        releaseReplacement.countDown();
        awaitCondition(() -> localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")
                && !localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1"));
        InOrder order = inOrder(loader);
        order.verify(loader).load(argThat(artifact -> "v1".equals(artifact.getRef().version())));
        order.verify(loader).load(argThat(artifact -> "v2".equals(artifact.getRef().version())));
        order.verify(loader).release(NAMESPACE, CODE, "v1");
    }

    @Test
    void graphPreparationFailureReleasesNewRuntimeAndPreservesPreviousReadyVersion() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.getArgument(0);
            if ("v2".equals(ref.version())) {
                throw new IllegalStateException("missing transitive runtime");
            }
            return null;
        }).when(loader).prepare(any(ProcessRef.Version.class));

        CompletableFuture<Void> convergence = ensureInstalled(Set.of(key("v2")));

        assertThatThrownBy(() -> convergence.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isTrue();
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isFalse();
        verify(loader).release(NAMESPACE, CODE, "v2");
        verify(loader, never()).release(NAMESPACE, CODE, "v1");
    }

    @Test
    void executionInstallationLeaseRetainsOnlyTheRequestedVersion() throws Exception {
        manager.close();
        ProcessRef.Version parent = key("v1");
        ProcessRef.Version child = ProcessRef.version(NAMESPACE, "order.shared", "shared-v1");
        resolver = key -> key.equals(parent) ? artifact(parent) : artifact(child);
        manager = new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);

        VersionRuntimeLease lease = manager.acquireInstallation(parent).get(2, TimeUnit.SECONDS);

        assertThat(manager.snapshot().getDemandedVersions()).containsExactly(parent);
        assertThat(localRoutingState
            .getInstalledVersionState()
            .contains(parent.namespace(), parent.code(), parent.version()))
            .isTrue();
        assertThat(localRoutingState
            .getInstalledVersionState()
            .contains(child.namespace(), child.code(), child.version()))
            .isFalse();
        verify(loader).load(argThat(artifact -> artifact.getRef().equals(parent)));
        verify(loader, never()).load(argThat(artifact -> artifact.getRef().equals(child)));

        lease.close();

        awaitCondition(() -> manager.snapshot().getDemandedVersions().isEmpty()
                && !localRoutingState
            .getInstalledVersionState()
            .contains(parent.namespace(), parent.code(), parent.version()));
        verify(loader).release(parent.namespace(), parent.code(), parent.version());
    }

    @Test
    void executionInstallationLeaseSurvivesAnAliasRouteSwitch() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");
        VersionRuntimeLease execution = manager.acquireInstallation(key("v1")).get(2, TimeUnit.SECONDS);

        ensureInstalled(Set.of(key("v2"))).get(2, TimeUnit.SECONDS);

        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isTrue();
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isTrue();
        verify(loader, never()).release(NAMESPACE, CODE, "v1");

        execution.close();

        awaitCondition(() -> !localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1"));
        verify(loader).release(NAMESPACE, CODE, "v1");
    }

    @Test
    void releasingOneDemandPreservesSharedFailureBackoff() throws Exception {
        CountDownLatch installationStarted = new CountDownLatch(1);
        CountDownLatch failInstallation = new CountDownLatch(1);
        doAnswer(invocation -> {
            installationStarted.countDown();
            if (!failInstallation.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test installation did not resume");
            }
            throw new IllegalStateException("compiler unavailable");
        }).when(loader).load(any(ProcessArtifact.class));

        CompletableFuture<Void> aliasConvergence = ensureInstalled(Set.of(key("v1")));
        assertThat(installationStarted.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<VersionRuntimeLease> executionInstallation = manager.acquireInstallation(key("v1"));

        failInstallation.countDown();
        assertThatThrownBy(() -> aliasConvergence.get(2, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> executionInstallation.get(2, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

        awaitCondition(() -> manager
            .snapshot()
            .getBackedOffVersions()
            .stream()
            .anyMatch(backoff -> backoff.getKey().equals(key("v1"))));
        assertThat(manager.snapshot().getDemandedVersions()).containsExactly(key("v1"));
        verify(loader, times(1)).load(any(ProcessArtifact.class));
    }

    @Test
    void preservesPreviousVersionWhenReplacementFails() throws Exception {
        installAndAwait("v1");
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                throw new IllegalStateException("compiler unavailable");
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ensureInstalled(Collections.singleton(key("v2")));

        awaitCondition(() -> manager
            .snapshot()
            .getBackedOffVersions()
            .stream()
            .anyMatch(entry -> "v2".equals(entry.getKey().version())));
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isTrue();
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isFalse();
        verify(loader, never()).release(NAMESPACE, CODE, "v1");
    }

    @Test
    void rejectsAResolverArtifactWithTheWrongIdentity() {
        manager.close();
        ProcessArtifactResolver mismatchedResolver =
                key -> {
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(ProcessModelType.TBBPM, key.code(), "<flow/>");
            ProcessRef.Version wrongRef = ProcessRef.version(key.namespace(), key.code(), "other-version");
            return new ProcessArtifact(wrongRef, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        };
        manager = new VersionRuntimeManager(mismatchedResolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);

        CompletableFuture<Void> convergence = ensureInstalled(Set.of(key("v1")));

        assertThatThrownBy(convergence::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(DeploymentException.class)
            .satisfies(failure -> assertThat(((DeploymentException) failure.getCause()).getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
        verify(loader, never()).load(any(ProcessArtifact.class));
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isFalse();
    }

    @Test
    void releasesPartialNewOwnershipWhenOneRequiredVersionFails() throws Exception {
        installAndAwait("v1");
        CountDownLatch firstCandidateLoaded = new CountDownLatch(1);
        CountDownLatch partialOwnershipReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                firstCandidateLoaded.countDown();
            } else if ("v3".equals(ref.version())) {
                assertThat(firstCandidateLoaded.await(2, TimeUnit.SECONDS)).isTrue();
                throw new IllegalStateException("secret compiler detail");
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        doAnswer(invocation -> {
            if ("v2".equals(invocation.getArgument(2))) {
                partialOwnershipReleased.countDown();
            }
            return ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED;
        }).when(loader).release(any(String.class), any(String.class), any(String.class));

        CompletableFuture<Void> convergence = ensureInstalled(new LinkedHashSet<>(List.of(key("v2"), key("v3"))));

        assertThatThrownBy(() -> convergence.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(partialOwnershipReleased.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isTrue();
        awaitCondition(() -> !localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2"));
        assertThat(manager.snapshot().getBackedOffVersions().get(0).getReason()).doesNotContain(
                "secret compiler detail");
    }

    @Test
    void reservesSharedDependenciesUntilTheParentOwnershipEdgeIsCommitted() throws Exception {
        manager.close();
        ProcessRef.Version first = ProcessRef.version(NAMESPACE, "first", "v1");
        ProcessRef.Version second = ProcessRef.version(NAMESPACE, "second", "v1");
        ProcessRef.Version shared = ProcessRef.version(NAMESPACE, "shared", "v1");
        ProcessRef.Version failing = ProcessRef.version(NAMESPACE, "failing", "v1");
        ProcessRef.Version blocked = ProcessRef.version(NAMESPACE, "blocked", "v1");
        resolver = ref -> {
            if (ref.equals(first)) {
                return artifact(first, List.of(shared, failing));
            }
            if (ref.equals(second)) {
                return artifact(second, List.of(shared, blocked));
            }
            return artifact(ref);
        };
        manager = new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);
        CountDownLatch failingLoadStarted = new CountDownLatch(1);
        CountDownLatch blockedLoadStarted = new CountDownLatch(1);
        CountDownLatch finishBlockedLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if (ref.equals(failing)) {
                failingLoadStarted.countDown();
                assertThat(blockedLoadStarted.await(2, TimeUnit.SECONDS)).isTrue();
                throw new IllegalStateException("expected failure");
            }
            if (ref.equals(blocked)) {
                blockedLoadStarted.countDown();
                assertThat(finishBlockedLoad.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);

        CompletableFuture<VersionRuntimeLease> failedInstallation = manager.acquireInstallation(first);
        assertThat(failingLoadStarted.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<VersionRuntimeLease> successfulInstallation = manager.acquireInstallation(second);
        assertThat(blockedLoadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> failedInstallation.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
        verify(loader, never()).release(shared.namespace(), shared.code(), shared.version());

        finishBlockedLoad.countDown();
        VersionRuntimeLease lease = successfulInstallation.get(2, TimeUnit.SECONDS);
        try {
            assertThat(localRoutingState
                .getInstalledVersionState()
                .contains(shared.namespace(), shared.code(), shared.version()))
                .isTrue();
            verify(loader, never()).release(shared.namespace(), shared.code(), shared.version());
        } finally {
            lease.close();
        }
    }

    @Test
    void publishesAParentOnlyAfterItsDependenciesAreReady() throws Exception {
        manager.close();
        ProcessRef.Version parent = ProcessRef.version(NAMESPACE, "parent", "v1");
        ProcessRef.Version child = ProcessRef.version(NAMESPACE, "child", "v1");
        resolver = ref -> ref.equals(parent) ? artifact(parent, List.of(child)) : artifact(child);
        manager = new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);
        CountDownLatch childLoadStarted = new CountDownLatch(1);
        CountDownLatch finishChildLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if (ref.equals(child)) {
                childLoadStarted.countDown();
                assertThat(finishChildLoad.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        CompletableFuture<VersionRuntimeLease> installation = manager.acquireInstallation(parent);

        assertThat(childLoadStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(localRoutingState
            .getInstalledVersionState()
            .contains(parent.namespace(), parent.code(), parent.version()))
            .isFalse();
        finishChildLoad.countDown();

        VersionRuntimeLease lease = installation.get(2, TimeUnit.SECONDS);
        try {
            assertThat(localRoutingState
                .getInstalledVersionState()
                .contains(child.namespace(), child.code(), child.version()))
                .isTrue();
            assertThat(localRoutingState
                .getInstalledVersionState()
                .contains(parent.namespace(), parent.code(), parent.version()))
                .isTrue();
            InOrder loadOrder = inOrder(loader);
            loadOrder.verify(loader).load(argThat(loaded -> loaded.getRef().equals(child)));
            loadOrder.verify(loader).load(argThat(loaded -> loaded.getRef().equals(parent)));
        } finally {
            lease.close();
        }
    }

    @Test
    void prunesLateInstallationAfterDemandWasSuperseded() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");
        CountDownLatch staleStarted = new CountDownLatch(1);
        CountDownLatch releaseStale = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                staleStarted.countDown();
                assertThat(releaseStale.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ensureInstalled(Collections.singleton(key("v2")));
        assertThat(staleStarted.await(2, TimeUnit.SECONDS)).isTrue();
        ensureInstalled(Collections.singleton(key("v3")));
        awaitCondition(() -> localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v3"));

        releaseStale.countDown();
        awaitCondition(() -> localRoutingState
            .getInstalledVersionState()
            .list(NAMESPACE, CODE)
            .orElse(Collections.emptySet())
            .equals(Collections.singleton("v3")));
        verify(loader).release(NAMESPACE, CODE, "v1");
        verify(loader).release(NAMESPACE, CODE, "v2");
    }

    @Test
    void retriesFailedReleaseOfASupersededLateInstallation() throws Exception {
        manager.close();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduled = mock(ScheduledFuture.class);
        doReturn(scheduled).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        manager = new VersionRuntimeManager(resolver, loader, executor, scheduler, localRoutingState,
                Duration.ofMillis(20), 2);
        CountDownLatch staleStarted = new CountDownLatch(1);
        CountDownLatch finishStale = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProcessRef.Version ref = invocation.<ProcessArtifact>getArgument(0).getRef();
            if ("v2".equals(ref.version())) {
                staleStarted.countDown();
                assertThat(finishStale.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(NAMESPACE, CODE, "v2"))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.FAILED,
                    ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);

        ensureInstalled(Set.of(key("v2")));
        assertThat(staleStarted.await(2, TimeUnit.SECONDS)).isTrue();
        ensureInstalled(Set.of(key("v3"))).get(2, TimeUnit.SECONDS);

        finishStale.countDown();
        verify(loader, timeout(2000)).release(NAMESPACE, CODE, "v2");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isTrue();
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, timeout(2000)).schedule(retry.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        retry.getValue().run();

        verify(loader, times(2)).release(NAMESPACE, CODE, "v2");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2")).isFalse();
    }

    @Test
    void treatsNotOwnedReleaseAsAnIdempotentSuccess() throws Exception {
        manager.close();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        manager = new VersionRuntimeManager(resolver, loader, executor, scheduler, localRoutingState,
                Duration.ofMillis(20), 2);
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.NOT_OWNED);
        installAndAwait("v1");

        ensureInstalled(Collections.emptySet()).get(2, TimeUnit.SECONDS);

        verify(loader).release(NAMESPACE, CODE, "v1");
        verifyNoInteractions(scheduler);
    }

    @Test
    void retriesFailedDemandWithoutAnotherRoutingEvent() throws Exception {
        manager.close();
        manager = new VersionRuntimeManager(resolver, loader, executor, retryScheduler, localRoutingState,
                Duration.ofMillis(20), 2);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary compiler failure");
            }
            return null;
        }).when(loader).load(any(ProcessArtifact.class));

        ensureInstalled(Collections.singleton(key("v2")));

        awaitCondition(() -> localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v2"));
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(manager.snapshot().getBackedOffVersions()).isEmpty();
    }

    @Test
    void reconciliationWhileAnotherVersionIsPendingPreservesSingleFlight() throws Exception {
        manager.close();
        Queue<Runnable> submitted = new ArrayDeque<>();
        manager = new VersionRuntimeManager(resolver, loader, submitted::add, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 2);

        CompletableFuture<Void> convergence = ensureInstalled(new LinkedHashSet<>(List.of(key("v1"), key("v2"))));

        assertThat(submitted).hasSize(2);
        submitted.remove().run();
        assertThat(submitted).hasSize(1);
        submitted.remove().run();

        convergence.get(1, TimeUnit.SECONDS);
        verify(loader, times(1)).load(argThat(artifact -> "v2".equals(artifact.getRef().version())));
    }

    @Test
    void serializesPublicationAndReconcilesASupersedingGeneration() throws Exception {
        CountDownLatch firstPublicationStarted = new CountDownLatch(1);
        CountDownLatch finishFirstPublication = new CountDownLatch(1);
        AtomicInteger firstPublications = new AtomicInteger();
        AtomicInteger secondPublications = new AtomicInteger();
        AliasVersionDemand decision = AliasVersionDemand.forAlias(NAMESPACE, CODE, ALIAS, Set.of(key("v1")));

        CompletableFuture<Void> firstConvergence = manager.ensureInstalled(decision, () -> {
            firstPublications.incrementAndGet();
            firstPublicationStarted.countDown();
            try {
                assertThat(finishFirstPublication.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        assertThat(firstPublicationStarted.await(2, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> secondConvergence =
                manager.ensureInstalled(decision, secondPublications::incrementAndGet);

        assertThat(firstPublications).hasValue(1);
        assertThat(secondPublications).hasValue(0);

        finishFirstPublication.countDown();

        secondConvergence.get(2, TimeUnit.SECONDS);
        firstConvergence.get(2, TimeUnit.SECONDS);
        assertThat(secondPublications).hasValue(1);
    }

    @Test
    void schedulesOnlyOneReconciliationWhenMultipleVersionsFailToUnload() throws Exception {
        manager.close();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduled = mock(ScheduledFuture.class);
        doReturn(scheduled).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        manager = new VersionRuntimeManager(resolver, loader, Runnable::run, scheduler, localRoutingState,
                Duration.ofMinutes(1), 2);
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.FAILED);
        ensureInstalled(Set.of(key("v1"), key("v2")));

        ensureInstalled(Collections.emptySet());

        verify(scheduler, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(loader).release(NAMESPACE, CODE, "v1");
        verify(loader).release(NAMESPACE, CODE, "v2");
    }

    @Test
    void closeFailsPendingConvergenceAndSkipsAQueuedInstallation() {
        manager.close();
        Queue<Runnable> submitted = new ArrayDeque<>();
        manager = new VersionRuntimeManager(resolver, loader, submitted::add, retryScheduler, localRoutingState,
                Duration.ofMinutes(1), 1);
        CompletableFuture<Void> convergence = ensureInstalled(Set.of(key("v1")));

        manager.close();

        assertThatThrownBy(convergence::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VersionRuntimeManager is closed");
        assertThat(submitted).hasSize(1);

        submitted.remove().run();

        verify(loader, never()).load(any(ProcessArtifact.class));
        verify(loader, never()).release(NAMESPACE, CODE, "v1");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isFalse();
    }

    @Test
    void closeCancelsDelayedTasksOwnedByAnExternalScheduler() throws Exception {
        manager.close();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduled = mock(ScheduledFuture.class);
        doReturn(scheduled).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        manager = new VersionRuntimeManager(resolver, loader, Runnable::run, scheduler, localRoutingState,
                Duration.ofMinutes(1), 1);
        doAnswer(invocation -> {
            throw new IllegalStateException("temporary compiler failure");
        }).when(loader).load(any(ProcessArtifact.class));

        CompletableFuture<Void> convergence = ensureInstalled(Set.of(key("v1")));

        assertThatThrownBy(convergence::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
        verify(scheduler).schedule(any(Runnable.class), eq(60_000L), eq(TimeUnit.MILLISECONDS));

        manager.close();

        verify(scheduled).cancel(false);
        verify(scheduler, never()).shutdown();
        verify(scheduler, never()).shutdownNow();
    }

    @Test
    void completedTaskIsNotTrackedWhenItRunsBeforeScheduleReturns() throws Exception {
        manager.close();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduled = mock(ScheduledFuture.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return scheduled;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        manager = new VersionRuntimeManager(resolver, loader, Runnable::run, scheduler, localRoutingState,
                Duration.ofMillis(20), 2);
        when(loader.release(NAMESPACE, CODE, "v1"))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.FAILED,
                    ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");

        ensureInstalled(Collections.emptySet()).get(2, TimeUnit.SECONDS);
        manager.close();

        verify(scheduler).schedule(any(Runnable.class), eq(20L), eq(TimeUnit.MILLISECONDS));
        verify(scheduled, never()).cancel(false);
    }

    @Test
    void closeReleasesInstalledOwnership() throws Exception {
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        installAndAwait("v1");

        manager.close();

        verify(loader).release(NAMESPACE, CODE, "v1");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isFalse();
    }

    @Test
    void closeWaitsForLocalReadyPublicationBeforeReleasingOwnership() throws Exception {
        CountDownLatch publicationStarted = new CountDownLatch(1);
        CountDownLatch finishPublication = new CountDownLatch(1);
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        CompletableFuture<Void> convergence =
                manager.ensureInstalled(AliasVersionDemand.forAlias(NAMESPACE, CODE, ALIAS, Set.of(key("v1"))), () -> {
            publicationStarted.countDown();
            try {
                assertThat(finishPublication.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        assertThat(publicationStarted.await(2, TimeUnit.SECONDS)).isTrue();

        CountDownLatch closeStarted = new CountDownLatch(1);
        CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
            closeStarted.countDown();
            manager.close();
        });
        assertThat(closeStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(close).isNotDone();
        verify(loader, never()).release(NAMESPACE, CODE, "v1");

        finishPublication.countDown();

        close.get(2, TimeUnit.SECONDS);
        convergence.get(2, TimeUnit.SECONDS);
        verify(loader).release(NAMESPACE, CODE, "v1");
    }

    @Test
    void closeWaitsForRunningInstallationBeforeReleasingOwnership() throws Exception {
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch finishLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadStarted.countDown();
            assertThat(finishLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(any(String.class), any(String.class), any(String.class)))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        ensureInstalled(Set.of(key("v1")));
        assertThat(loadStarted.await(2, TimeUnit.SECONDS)).isTrue();

        CountDownLatch closeStarted = new CountDownLatch(1);
        CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
            closeStarted.countDown();
            manager.close();
        });
        assertThat(closeStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(close).isNotDone();
        verify(loader, never()).release(NAMESPACE, CODE, "v1");

        finishLoad.countDown();

        close.get(2, TimeUnit.SECONDS);
        verify(loader).release(NAMESPACE, CODE, "v1");
        assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, "v1")).isFalse();
    }

    private void installAndAwait(String version) throws Exception {
        ensureInstalled(Collections.singleton(key(version)));
        awaitCondition(() -> localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, version));
    }

    private CompletableFuture<Void> ensureInstalled(Set<ProcessRef.Version> versions) {
        return manager.ensureInstalled(AliasVersionDemand.forAlias(NAMESPACE, CODE, ALIAS, versions));
    }
}
