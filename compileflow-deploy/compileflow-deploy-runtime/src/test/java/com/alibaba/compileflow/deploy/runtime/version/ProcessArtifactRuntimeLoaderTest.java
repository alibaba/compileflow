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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ProcessArtifactRuntimeLoaderTest {
    private ProcessRuntimeOwnership ownership;
    private ProcessCallInspector inspector;
    private ProcessExecutionGraphPreparer graphPreparer;

    private static ProcessArtifact artifact(ProcessModelType modelType, String content, String digest) {
        return artifact(modelType, "v1", content, digest);
    }

    private static ProcessArtifact artifact(ProcessModelType modelType, String version, String content, String digest) {
        ProcessRef.Version ref = ProcessRef.version("default", "runtime.flow", version);
        ProcessDefinition.Inline definition = ProcessDefinition.inline(modelType, "runtime.flow", content);
        return new ProcessArtifact(ref, definition,
                digest == null ? ProcessArtifactDigest.compute(definition, Map.of()) : digest);
    }

    private static ProcessEngine engineWithOwnership() {
        ProcessEngine engine = mock(ProcessEngine.class,
                withSettings()
                    .extraInterfaces(ProcessRuntimeOwnership.class, ProcessCallInspector.class,
                            ProcessExecutionGraphPreparer.class));
        when(((ProcessCallInspector) engine).inspectProcessCalls(any())).thenReturn(java.util.List.of());
        return engine;
    }

    private static void await(Future<?> future) throws Exception {
        try {
            future.get(1L, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    @BeforeEach
    void setUp() {
        ownership = mock(ProcessRuntimeOwnership.class);
        inspector = mock(ProcessCallInspector.class);
        graphPreparer = mock(ProcessExecutionGraphPreparer.class);
        when(inspector.inspectProcessCalls(any())).thenReturn(java.util.List.of());
    }

    @Test
    void verifiesArtifactAndLoadsExactlyOnce() {
        ProcessArtifactRuntimeLoader loader =
                new ProcessArtifactRuntimeLoader(ownership, inspector, graphPreparer, new DeploymentRuntimeMetrics());
        ProcessArtifact artifact = artifact(ProcessModelType.BPMN, "<definitions/>", null);

        loader.load(artifact);

        ArgumentCaptor<ProcessRef.Version> refCaptor = ArgumentCaptor.forClass(ProcessRef.Version.class);
        ArgumentCaptor<ProcessDefinition> definitionCaptor = ArgumentCaptor.forClass(ProcessDefinition.class);
        InOrder calls = inOrder(inspector, ownership);
        calls.verify(inspector).inspectProcessCalls(artifact.getDefinition());
        calls.verify(ownership).loadOwned(any(String.class), refCaptor.capture(), definitionCaptor.capture());
        assertThat(refCaptor.getValue()).isEqualTo(artifact.getRef());
        assertThat(definitionCaptor.getValue()).isEqualTo(artifact.getDefinition());
        verifyNoMoreInteractions(ownership);
    }

    @Test
    void rejectsSourceBindingMismatchBeforeInstallingRuntime() {
        ProcessRef.Version root = ProcessRef.version("shop", "runtime.flow", "v1");
        ProcessRef.Version child = ProcessRef.version("shop", "payment", "v3");
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, root.code(), "<definitions/>");
        ProcessCallBinding binding = new ProcessCallBinding("paymentCall", child);
        String digest = ProcessArtifactDigest.compute(definition, Map.of(binding.callSiteId(), binding.target()));
        ProcessArtifact artifact = new ProcessArtifact(root, definition, digest, java.util.List.of(binding));
        ProcessArtifactRuntimeLoader loader =
                new ProcessArtifactRuntimeLoader(ownership, inspector, graphPreparer, new DeploymentRuntimeMetrics());

        assertThatThrownBy(() -> loader.load(artifact))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));

        verify(inspector).inspectProcessCalls(definition);
        verify(ownership, never()).loadOwned(anyString(), any(ProcessRef.Version.class), any(ProcessDefinition.class));
    }

    @Test
    void passesTheDefinitionTypeToTheSharedRuntimeOwner() {
        DeploymentRuntimeMetrics metrics = new DeploymentRuntimeMetrics();
        ProcessArtifactRuntimeLoader loader =
                new ProcessArtifactRuntimeLoader(ownership, inspector, graphPreparer, metrics);
        ProcessArtifact artifact = artifact(ProcessModelType.BPMN, "<definitions/>", null);

        loader.load(artifact);
        verify(ownership).loadOwned(anyString(), eq(artifact.getRef()), eq(artifact.getDefinition()));
        assertThat(artifact.getDefinition().modelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(metrics.getErrorCount(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH)).isZero();
    }

    @Test
    void rejectsDigestMismatch() {
        ProcessArtifactRuntimeLoader loader =
                new ProcessArtifactRuntimeLoader(ownership, inspector, graphPreparer, new DeploymentRuntimeMetrics());
        ProcessArtifact artifact = artifact(ProcessModelType.BPMN, "<definitions/>", "0".repeat(64));

        assertThatThrownBy(() -> loader.load(artifact))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));

        verify(ownership, never()).loadOwned(any(String.class), any(ProcessRef.Version.class),
                any(ProcessDefinition.class));
    }

    @Test
    void loadsDifferentFormatsIntoTheSameRuntimeOwner() {
        ProcessEngine engine = engineWithOwnership();
        ProcessRuntimeOwnership runtimeOwnership = (ProcessRuntimeOwnership) engine;
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(engine, new DeploymentRuntimeMetrics());
        ProcessArtifact bpmnArtifact = artifact(ProcessModelType.BPMN, "bpmn-v1", "<definitions/>", null);
        ProcessArtifact tbbpmArtifact = artifact(ProcessModelType.TBBPM, "tbbpm-v1", "<package/>", null);

        loader.load(bpmnArtifact);
        loader.load(tbbpmArtifact);

        verify(runtimeOwnership)
            .loadOwned(any(String.class), org.mockito.ArgumentMatchers.eq(bpmnArtifact.getRef()),
                    org.mockito.ArgumentMatchers.eq(bpmnArtifact.getDefinition()));
        verify(runtimeOwnership)
            .loadOwned(any(String.class), org.mockito.ArgumentMatchers.eq(tbbpmArtifact.getRef()),
                    org.mockito.ArgumentMatchers.eq(tbbpmArtifact.getDefinition()));
    }

    @Test
    void reportsAnImmutableVersionConflictFromTheRuntimeOwner() {
        ProcessEngine sharedEngine = engineWithOwnership();
        ProcessRuntimeOwnership sharedOwnership = (ProcessRuntimeOwnership) sharedEngine;
        ProcessArtifactRuntimeLoader loader =
                new ProcessArtifactRuntimeLoader(sharedEngine, new DeploymentRuntimeMetrics());
        ProcessArtifact bpmnArtifact = artifact(ProcessModelType.BPMN, "v1", "<definitions/>", null);
        ProcessArtifact reboundArtifact = artifact(ProcessModelType.TBBPM, "v1", "<package/>", null);

        loader.load(bpmnArtifact);
        org.mockito.Mockito
            .doThrow(new IllegalStateException("immutable Version conflict"))
            .when(sharedOwnership)
            .loadOwned(anyString(), eq(reboundArtifact.getRef()), eq(reboundArtifact.getDefinition()));

        assertThatThrownBy(() -> loader.load(reboundArtifact))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.INTERNAL_ERROR));
        verify(sharedOwnership, times(2))
            .loadOwned(any(String.class), any(ProcessRef.Version.class), any(ProcessDefinition.class));
    }

    @Test
    void concurrentFailedAndSuccessfulLoadsPreserveTheSuccessfulInstallation() throws Exception {
        ProcessEngine engine = engineWithOwnership();
        ProcessRuntimeOwnership runtimeOwnership = (ProcessRuntimeOwnership) engine;
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(engine, new DeploymentRuntimeMetrics());
        ProcessArtifact artifact = artifact(ProcessModelType.BPMN, "<definitions/>", null);
        CountDownLatch firstLoadEntered = new CountDownLatch(1);
        CountDownLatch secondLoadEntered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstLoadEntered.countDown();
                secondLoadEntered.await(200L, TimeUnit.MILLISECONDS);
                throw new IllegalStateException("first installation failed");
            }
            secondLoadEntered.countDown();
            return null;
        })
            .when(runtimeOwnership)
            .loadOwned(anyString(), eq(artifact.getRef()), eq(artifact.getDefinition()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> failed = executor.submit(() -> loader.load(artifact));
            assertThat(firstLoadEntered.await(1L, TimeUnit.SECONDS)).isTrue();
            Future<?> successful = executor.submit(() -> loader.load(artifact));

            assertThatThrownBy(() -> await(failed))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("runtime load failed");
            successful.get(1L, TimeUnit.SECONDS);

            loader.prepare(artifact.getRef());
            verify((ProcessExecutionGraphPreparer) engine).prepareExact(artifact.getRef());
            verify(runtimeOwnership, times(2)).loadOwned(anyString(), eq(artifact.getRef()),
                    eq(artifact.getDefinition()));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1L, TimeUnit.SECONDS)).isTrue();
        }
    }
}
