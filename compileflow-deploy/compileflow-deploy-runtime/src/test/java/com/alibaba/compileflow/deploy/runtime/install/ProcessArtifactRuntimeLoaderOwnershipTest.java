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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
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
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.owner", "<definitions/>");
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(ownership, ignored -> java.util.List.of(),
                ProcessModelType.BPMN, ignored -> null,
                new com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics());
        when(ownership.retainOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(true);
        when(ownership.releaseOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);

        loader.load(
                new ProcessArtifact(ref, ProcessModelType.BPMN, definition,
                        ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of())));
        assertThat(loader.retain("default", "order.owner", "v1")).isTrue();
        assertThat(loader.release("default", "order.owner", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.RETAINED);

        verify(ownership)
            .loadOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref),
                    org.mockito.ArgumentMatchers.eq(definition));
    }

    @Test
    void retainedRuntimeKeepsItsModelBindingOnMultiEngineNodes() {
        ProcessEngine bpmnEngine = engineWithOwnership();
        ProcessEngine tbbpmEngine = engineWithOwnership();
        ProcessRuntimeOwnership bpmnOwnership = (ProcessRuntimeOwnership) bpmnEngine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.multi-engine", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.multi-engine", "<definitions/>");
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(Map.of(ProcessModelType.BPMN, bpmnEngine,
                        ProcessModelType.TBBPM, tbbpmEngine), new ProcessDeploymentMetrics());
        when(bpmnOwnership.releaseOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);
        when(bpmnOwnership.retainOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref)))
            .thenReturn(true);

        loader.load(
                new ProcessArtifact(ref, ProcessModelType.BPMN, definition,
                        ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of())));
        assertThat(loader.release("default", "order.multi-engine", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.RETAINED);

        assertThat(loader.retain("default", "order.multi-engine", "v1")).isTrue();
        verify(bpmnOwnership).retainOwned(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ref));
    }

    @Test
    void serializesSameVersionLoadAndReleaseBeforeUpdatingTheModelBinding() throws Exception {
        ProcessEngine bpmnEngine = engineWithOwnership();
        ProcessEngine tbbpmEngine = engineWithOwnership();
        ProcessRuntimeOwnership bpmnOwnership = (ProcessRuntimeOwnership) bpmnEngine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.concurrent-owner", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.concurrent-owner", "<definitions/>");
        ProcessArtifact artifact = new ProcessArtifact(ref, ProcessModelType.BPMN, definition,
                ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of()));
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(Map.of(ProcessModelType.BPMN, bpmnEngine,
                        ProcessModelType.TBBPM, tbbpmEngine), new ProcessDeploymentMetrics());
        AtomicInteger loadCalls = new AtomicInteger();
        CountDownLatch secondLoadEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (loadCalls.incrementAndGet() == 2) {
                secondLoadEntered.countDown();
            }
            return null;
        })
            .when(bpmnOwnership)
            .loadOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref), org.mockito.ArgumentMatchers.eq(definition));
        CountDownLatch releaseEntered = new CountDownLatch(1);
        CountDownLatch finishRelease = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseEntered.countDown();
            assertThat(finishRelease.await(2, TimeUnit.SECONDS)).isTrue();
            return ProcessRuntimeOwnership.ReleaseOutcome.REMOVED;
        }).when(bpmnOwnership).releaseOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref));
        when(bpmnOwnership.retainOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref))).thenReturn(true);
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
        assertThat(loader.retain(ref.namespace(), ref.code(), ref.version())).isTrue();
        verify(bpmnOwnership).retainOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref));
    }

    @Test
    void releaseWaitsForAnInitialLoadOfTheSameVersion() throws Exception {
        ProcessEngine bpmnEngine = engineWithOwnership();
        ProcessEngine tbbpmEngine = engineWithOwnership();
        ProcessRuntimeOwnership bpmnOwnership = (ProcessRuntimeOwnership) bpmnEngine;
        ProcessRef.Version ref = ProcessRef.version("default", "order.initial-load", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.initial-load", "<definitions/>");
        ProcessArtifact artifact = new ProcessArtifact(ref, ProcessModelType.BPMN, definition,
                ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of()));
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(Map.of(ProcessModelType.BPMN, bpmnEngine,
                        ProcessModelType.TBBPM, tbbpmEngine), new ProcessDeploymentMetrics());
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch finishLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadEntered.countDown();
            assertThat(finishLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        })
            .when(bpmnOwnership)
            .loadOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref), org.mockito.ArgumentMatchers.eq(definition));
        CountDownLatch releaseEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseEntered.countDown();
            return ProcessRuntimeOwnership.ReleaseOutcome.REMOVED;
        }).when(bpmnOwnership).releaseOwned(anyString(), org.mockito.ArgumentMatchers.eq(ref));

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
