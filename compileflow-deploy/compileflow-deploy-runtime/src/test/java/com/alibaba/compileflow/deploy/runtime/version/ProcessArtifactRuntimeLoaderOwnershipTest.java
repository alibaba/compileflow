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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.semantic.ProcessCallInspector;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.engine.core.runtime.ProcessExecutionGraphPreparer;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessArtifactRuntimeLoaderOwnershipTest {
    private static ProcessEngine engineWithOwnership() {
        ProcessEngine engine = mock(ProcessEngine.class,
                withSettings()
                    .extraInterfaces(ProcessRuntimeOwnership.class, ProcessCallInspector.class,
                            ProcessExecutionGraphPreparer.class));
        when(((ProcessCallInspector) engine).inspectProcessCalls(any())).thenReturn(java.util.List.of());
        return engine;
    }

    @Test
    void usesOneOwnerIdentityAcrossItsLifecycleOperations() {
        ProcessRuntimeOwnership ownership = mock(ProcessRuntimeOwnership.class);
        ProcessRef.Version ref = ProcessRef.version("default", "order.owner", "v1");
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.BPMN, "order.owner", "<definitions/>");
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(ownership, ignored -> java.util.List.of(),
                ignored -> null, new com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics());
        when(ownership.releaseOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);

        ProcessArtifact artifact =
                new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        loader.load(artifact);
        loader.load(artifact);
        assertThat(loader.release("default", "order.owner", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.RETAINED);

        org.mockito.ArgumentCaptor<String> owner = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ownership, times(2)).loadOwned(owner.capture(), eq(ref), eq(definition));
        assertThat(owner.getAllValues()).hasSize(2).containsOnly(owner.getValue());
        assertThat(owner.getValue()).isNotBlank();
        verify(ownership).releaseOwned(owner.getValue(), ref);
    }

    @Test
    void reacquiresSharedRuntimeOwnershipThroughVerifiedLoad() {
        ProcessEngine engine = engineWithOwnership();
        ProcessRuntimeOwnership runtimeOwnership = (ProcessRuntimeOwnership) engine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.multi-frontend", "v1");
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.BPMN, "order.multi-frontend", "<definitions/>");
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(engine, new DeploymentRuntimeMetrics());
        when(runtimeOwnership.releaseOwned(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);
        ProcessArtifact artifact =
                new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        loader.load(artifact);
        assertThat(loader.release("default", "order.multi-frontend", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.RETAINED);

        loader.load(artifact);
        loader.prepare(ref);

        org.mockito.ArgumentCaptor<String> owner = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(runtimeOwnership, times(2)).loadOwned(owner.capture(), eq(ref), eq(definition));
        assertThat(owner.getAllValues()).containsOnly(owner.getValue());
        verify(runtimeOwnership).releaseOwned(owner.getValue(), ref);
        verify((ProcessCallInspector) engine, times(2)).inspectProcessCalls(definition);
        verify((ProcessExecutionGraphPreparer) engine).prepareExact(ref);
    }

    @Test
    void serializesSameVersionLoadAndRelease() throws Exception {
        ProcessEngine engine = engineWithOwnership();
        ProcessRuntimeOwnership runtimeOwnership = (ProcessRuntimeOwnership) engine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.concurrent-owner", "v1");
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.BPMN, "order.concurrent-owner", "<definitions/>");
        ProcessArtifact artifact =
                new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(engine, new DeploymentRuntimeMetrics());
        AtomicInteger loadCalls = new AtomicInteger();
        CountDownLatch secondLoadEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (loadCalls.incrementAndGet() == 2) {
                secondLoadEntered.countDown();
            }
            return null;
        })
            .when(runtimeOwnership)
            .loadOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref), org.mockito.ArgumentMatchers.eq(definition));
        CountDownLatch releaseEntered = new CountDownLatch(1);
        CountDownLatch finishRelease = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseEntered.countDown();
            assertThat(finishRelease.await(2, TimeUnit.SECONDS)).isTrue();
            return ProcessRuntimeOwnership.ReleaseOutcome.REMOVED;
        }).when(runtimeOwnership).releaseOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref));
        loader.load(artifact);

        CompletableFuture<ProcessArtifactRuntimeLoader.ReleaseResult> release =
                CompletableFuture.supplyAsync(() -> loader.release(ref.namespace(), ref.code(), ref.version()));
        assertThat(releaseEntered.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> reload = CompletableFuture.runAsync(() -> loader.load(artifact));
        try {
            assertThat(secondLoadEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            finishRelease.countDown();
        }

        assertThat(release.get(2, TimeUnit.SECONDS)).isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        reload.get(2, TimeUnit.SECONDS);
        assertThat(secondLoadEntered.getCount()).isZero();
        loader.prepare(ref);
        verify((ProcessExecutionGraphPreparer) engine).prepareExact(ref);
    }

    @Test
    void releaseWaitsForAnInitialLoadOfTheSameVersion() throws Exception {
        ProcessEngine engine = engineWithOwnership();
        ProcessRuntimeOwnership runtimeOwnership = (ProcessRuntimeOwnership) engine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.initial-load", "v1");
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.BPMN, "order.initial-load", "<definitions/>");
        ProcessArtifact artifact =
                new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(engine, new DeploymentRuntimeMetrics());
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch finishLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadEntered.countDown();
            assertThat(finishLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        })
            .when(runtimeOwnership)
            .loadOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref), org.mockito.ArgumentMatchers.eq(definition));
        CountDownLatch releaseEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseEntered.countDown();
            return ProcessRuntimeOwnership.ReleaseOutcome.REMOVED;
        }).when(runtimeOwnership).releaseOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref));

        CompletableFuture<Void> load = CompletableFuture.runAsync(() -> loader.load(artifact));
        assertThat(loadEntered.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ProcessArtifactRuntimeLoader.ReleaseResult> release =
                CompletableFuture.supplyAsync(() -> loader.release(ref.namespace(), ref.code(), ref.version()));
        try {
            assertThat(releaseEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            finishLoad.countDown();
        }

        load.get(2, TimeUnit.SECONDS);
        assertThat(release.get(2, TimeUnit.SECONDS)).isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        assertThat(releaseEntered.getCount()).isZero();
    }
}
