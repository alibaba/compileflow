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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallationLease;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class PublishedProcessExecutionServiceTest {
    private static ProcessEngineRegistry mockRegistry() {
        return mock(ProcessEngineRegistry.class);
    }

    private static PublishedProcessExecutionService service(ProcessEngineRegistry registry) {
        return service(registry, mock(ProcessDeploymentService.class));
    }

    @SuppressWarnings("unchecked")
    private static PublishedProcessExecutionService service(ProcessEngineRegistry registry,
            ProcessDeploymentService deploymentService) {
        ObjectProvider<RuntimeInstaller> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new PublishedProcessExecutionService(registry, deploymentService, provider);
    }

    private static ProcessExecution execution(String invocationId, String namespace, String effectiveVersion) {
        Instant startedAt = Instant.parse("2026-07-24T00:00:00Z");
        return ProcessExecution
            .builder()
            .traceId("trace-" + invocationId)
            .invocationId(invocationId)
            .namespace(namespace)
            .processCode("payment.approve")
            .processVersion(
                    effectiveVersion == null ? null : ProcessRef.version(namespace, "payment.approve", effectiveVersion))
            .startedAt(startedAt)
            .completedAt(startedAt.plusMillis(5L))
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    @Test
    void executeReturnsControlledEngineAttribution() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessRef.Alias alias = ProcessRef.alias("tenant-a", "payment.approve", "production");
        AliasSelection selection =
                new AliasSelection(ProcessRef.version("tenant-a", "payment.approve", "v2"), ProcessAliasTarget.STABLE,
                        7L);
        when(registry.executeAliasWithSelection(any(ProcessRef.Alias.class), any(), anyMap(),
                any(ProcessExecutionOptions.class)))
            .thenReturn(
                    new ProcessEngineRegistry.AliasExecution(ProcessResult.success(Collections.<String, Object>singletonMap("o"
                                            + "k", true), execution("inv-client-1", "tenant-a", "v2")), selection));
        PublishedProcessExecutionService service = service(registry);

        ProcessExecutionOptions options = ProcessExecutionOptions
            .builder()
            .invocationId("inv-client-1")
            .aliasRouting(new AliasRoutingOptions("user-42"))
            .build();
        ProcessExecutionResponse response =
                service.execute(alias, Collections.<String, Object>singletonMap("amount", 100), options);

        ExecutionRoutingResponse routing = response.routing();
        assertThat(routing.namespace()).isEqualTo("tenant-a");
        assertThat(routing.requestedAlias()).isEqualTo("production");
        assertThat(routing.alias()).isEqualTo("production");
        assertThat(routing.effectiveVersion()).isEqualTo("v2");
        assertThat(routing.routeRevision()).isEqualTo(7L);
        assertThat(routing.target()).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(response.invocationId()).isEqualTo("inv-client-1");
        assertThat(response.traceId()).isEqualTo("trace-inv-client-1");
        assertThat(response.processCode()).isEqualTo("payment.approve");
        assertThat(response.message()).isEqualTo("Process executed successfully");

        ArgumentCaptor<Map<String, Object>> contextCaptor = mapCaptor();
        ArgumentCaptor<ProcessRef.Alias> refCaptor = ArgumentCaptor.forClass(ProcessRef.Alias.class);
        ArgumentCaptor<ProcessExecutionOptions> optionsCaptor = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(registry)
            .executeAliasWithSelection(refCaptor.capture(), any(), contextCaptor.capture(), optionsCaptor.capture());
        assertThat(refCaptor.getValue()).isEqualTo(alias);
        assertThat(contextCaptor.getValue()).containsOnly(Map.entry("amount", 100));
        assertThat(optionsCaptor.getValue().getInvocationId()).isEqualTo("inv-client-1");
        assertThat(optionsCaptor.getValue().getAliasRouting().routingKey()).isEqualTo("user-42");
    }

    @Test
    void executeGeneratesInvocationIdWhenCallerOmitsIt() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessRef.Alias alias = ProcessRef.alias("payment.approve", "production");
        AliasSelection selection =
                new AliasSelection(ProcessRef.version("payment.approve", "v1"), ProcessAliasTarget.STABLE, 1L);
        when(registry.executeAliasWithSelection(any(ProcessRef.Alias.class), any(), anyMap(),
                any(ProcessExecutionOptions.class)))
            .thenAnswer(invocation -> {
                ProcessExecutionOptions options = invocation.getArgument(3);
                return new ProcessEngineRegistry.AliasExecution(ProcessResult.success(Collections.<String, Object>singletonMap("o"
                                        + "k", true), execution(options.getInvocationId(), "default", "v1")), selection);
            });
        PublishedProcessExecutionService service = service(registry);

        ProcessExecutionResponse response =
                service.execute(alias, Collections.<String, Object>emptyMap(), ProcessExecutionOptions.defaults());

        String invocationId = response.invocationId();
        assertThat(invocationId).isNotNull().startsWith("inv-");
        ArgumentCaptor<Map<String, Object>> contextCaptor = mapCaptor();
        ArgumentCaptor<ProcessExecutionOptions> optionsCaptor = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(registry)
            .executeAliasWithSelection(any(ProcessRef.Alias.class), any(), contextCaptor.capture(),
                    optionsCaptor.capture());
        assertThat(contextCaptor.getValue()).isEmpty();
        assertThat(optionsCaptor.getValue().getInvocationId()).isEqualTo(invocationId);
        assertThat(optionsCaptor.getValue().getAliasRouting()).isEqualTo(AliasRoutingOptions.defaults());
    }

    @Test
    void executePreservesStructuredEngineFailure() {
        ProcessEngineRegistry registry = mockRegistry();
        when(registry.execute(any(ProcessRef.class), any(), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(ProcessResult.failure(new ProcessError("CF_EXEC_004", "Process execution failed"),
                    execution("inv-failure", "default", "v1")));
        PublishedProcessExecutionService service = service(registry);

        ProcessExecutionResponse response = service.execute(ProcessRef.version("payment.approve", "v1"),
                Collections.<String, Object>emptyMap(), ProcessExecutionOptions.defaults());

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("CF_EXEC_004");
        assertThat(response.error()).isEqualTo("Process execution failed");
        assertThat(response.invocationId()).isEqualTo("inv-failure");
        assertThat(response.traceId()).isEqualTo("trace-inv-failure");
        assertThat(response.result()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactExecutionUsesTheInstalledModelTypeWithoutReadingControlPlaneMetadata() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallationLease lease = mock(RuntimeInstallationLease.class);
        ProcessEngine engine = mock(ProcessEngine.class);
        ProcessRef.Version ref = ProcessRef.version("default", "payment.approve", "v1");
        when(installer.acquireInstallation(ref)).thenReturn(CompletableFuture.completedFuture(lease));
        when(lease.getModelType()).thenReturn(Optional.of(ProcessModelType.BPMN));
        when(registry.get(ProcessModelType.BPMN)).thenReturn(engine);
        when(engine.execute(any(ProcessRef.Version.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(ProcessResult.success(Collections.emptyMap(), execution("inv-exact", "default", "v1")));
        ObjectProvider<RuntimeInstaller> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(installer);
        PublishedProcessExecutionService service =
                new PublishedProcessExecutionService(registry, deploymentService, provider);

        ProcessExecutionResponse response = service.execute(ref, Collections.emptyMap(),
                ProcessExecutionOptions.builder().invocationId("inv-exact").build());

        assertThat(response.success()).isTrue();
        verify(registry).get(ProcessModelType.BPMN);
        verify(engine).execute(any(ProcessRef.Version.class), anyMap(), any(ProcessExecutionOptions.class));
        verify(lease).close();
        verifyNoInteractions(deploymentService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactExecutionReusesCallerOwnedInstallationWithoutAcquiringOrClosingIt() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        RuntimeInstallationLease installation = mock(RuntimeInstallationLease.class);
        ProcessEngine engine = mock(ProcessEngine.class);
        ProcessRef.Version ref = ProcessRef.version("default", "payment.approve", "v1");
        when(installation.getModelType()).thenReturn(Optional.of(ProcessModelType.BPMN));
        when(registry.get(ProcessModelType.BPMN)).thenReturn(engine);
        when(engine.execute(any(ProcessRef.Version.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(ProcessResult.success(Collections.emptyMap(), execution("inv-installed", "default", "v1")));
        ObjectProvider<RuntimeInstaller> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(installer);
        PublishedProcessExecutionService service =
                new PublishedProcessExecutionService(registry, deploymentService, provider);

        ProcessExecutionResponse response = service.executeInstalled(ref, installation, Collections.emptyMap(),
                ProcessExecutionOptions.builder().invocationId("inv-installed").build());

        assertThat(response.success()).isTrue();
        verify(installer, never()).acquireInstallation(any(ProcessRef.Version.class));
        verify(installation, never()).close();
        verify(registry).get(ProcessModelType.BPMN);
        verifyNoInteractions(deploymentService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aliasExecutionUsesLocallyInstalledModelTypeBeforeControlPlaneFallback() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        ProcessRef.Alias alias = ProcessRef.alias("default", "payment.approve", "production");
        ProcessRef.Version version = ProcessRef.version("default", "payment.approve", "v1");
        AliasSelection selection = new AliasSelection(version, ProcessAliasTarget.STABLE, 4L);
        when(installer.findInstalledModelType(version)).thenReturn(Optional.of(ProcessModelType.BPMN));
        when(registry.executeAliasWithSelection(any(ProcessRef.Alias.class), any(), anyMap(),
                any(ProcessExecutionOptions.class)))
            .thenAnswer(invocation -> {
                java.util.function.Function<ProcessRef.Version, ProcessModelType> resolver = invocation.getArgument(1);
                assertThat(resolver.apply(version)).isEqualTo(ProcessModelType.BPMN);
                return new ProcessEngineRegistry.AliasExecution(ProcessResult.success(Collections.emptyMap(),
                                execution("inv-alias-local", "default", "v1")), selection);
            });
        ObjectProvider<RuntimeInstaller> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(installer);
        PublishedProcessExecutionService service =
                new PublishedProcessExecutionService(registry, deploymentService, provider);

        ProcessExecutionResponse response = service.execute(alias, Collections.emptyMap(),
                ProcessExecutionOptions.builder().invocationId("inv-alias-local").build());

        assertThat(response.success()).isTrue();
        verify(installer).findInstalledModelType(version);
        verifyNoInteractions(deploymentService);
    }

    @Test
    void resolvesTheCurrentAliasPinForQueuedExecution() {
        ProcessEngineRegistry registry = mockRegistry();
        ProcessDeploymentService deploymentService = mock(ProcessDeploymentService.class);
        ProcessRef.Alias ref = ProcessRef.alias("tenant-a", "payment.approve", "production");
        AliasSelection selection =
                new AliasSelection(ProcessRef.version("tenant-a", "payment.approve", "v2"), ProcessAliasTarget.STABLE,
                        7L);
        AliasRoutingOptions routing = new AliasRoutingOptions("customer-42");
        when(registry.admitAlias(ref, routing)).thenReturn(selection);
        PublishedProcessExecutionService service = service(registry, deploymentService);

        assertThat(service.resolveAliasPin("payment.approve", "tenant-a", "production", routing)).satisfies(pin -> {
            assertThat(pin.alias()).isEqualTo(ref);
            assertThat(pin.version()).isEqualTo("v2");
            assertThat(pin.routeRevision()).isEqualTo(7L);
            assertThat(pin.target()).isEqualTo(ProcessAliasTarget.STABLE);
        });
        verify(registry).admitAlias(ref, routing);
        verifyNoInteractions(deploymentService);
    }
}
