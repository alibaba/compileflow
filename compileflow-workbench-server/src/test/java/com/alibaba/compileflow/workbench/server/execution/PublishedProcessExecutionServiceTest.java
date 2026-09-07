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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.engine.core.routing.AliasSelectionExecutor;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeLease;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class PublishedProcessExecutionServiceTest {
    private final ProcessEngine engine =
            mock(ProcessEngine.class, withSettings().extraInterfaces(AliasSelectionExecutor.class));
    private final AliasSelectionExecutor executor = (AliasSelectionExecutor) engine;
    private final AliasAdmission admission = mock(AliasAdmission.class);
    private final VersionRuntimeManager manager = mock(VersionRuntimeManager.class);
    private final ProcessRef.Alias alias = ProcessRef.alias("tenant-a", "payment.approve", "production");
    private final ProcessRef.Version version = ProcessRef.version("tenant-a", "payment.approve", "v2");
    private final AliasSelection selection = new AliasSelection(version, ProcessAliasTarget.STABLE, 7L);

    @SuppressWarnings("unchecked")
    private PublishedProcessExecutionService service(boolean installations) {
        ObjectProvider<VersionRuntimeManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(installations ? manager : null);
        return new PublishedProcessExecutionService(engine, admission, provider);
    }

    private ProcessExecution execution(String invocationId) {
        Instant start = Instant.parse("2026-07-24T00:00:00Z");
        return ProcessExecution
            .builder()
            .traceId("trace-" + invocationId)
            .invocationId(invocationId)
            .namespace(version.namespace())
            .processCode(version.code())
            .processVersion(version)
            .startedAt(start)
            .completedAt(start.plusMillis(5))
            .build();
    }

    private ProcessResult<Map<String, Object>> success(String id) {
        return ProcessResult.success(Map.of("ok", true), execution(id));
    }

    @Test
    void aliasExecutionPreservesSelectionAndCallerAttribution() {
        when(admission.admit(eq(alias), any())).thenReturn(selection);
        when(executor.execute(eq(alias), eq(selection), anyMap(), any())).thenReturn(success("inv-client"));
        var options = ProcessExecutionOptions
            .builder()
            .invocationId("inv-client")
            .aliasRouting(new AliasRoutingOptions("user-42"))
            .build();
        var response = service(false).execute(alias, Map.of("amount", 100), options);
        assertThat(response.success()).isTrue();
        assertThat(response.invocationId()).isEqualTo("inv-client");
        assertThat(response.traceId()).isEqualTo("trace-inv-client");
        assertThat(response.routing().effectiveVersion()).isEqualTo("v2");
        assertThat(response.routing().routeRevision()).isEqualTo(7L);
        assertThat(response.routing().target()).isEqualTo(ProcessAliasTarget.STABLE);
        var captured = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(executor).execute(eq(alias), eq(selection), eq(Map.of("amount", 100)), captured.capture());
        assertThat(captured.getValue().getAliasRouting().routingKey()).isEqualTo("user-42");
    }

    @Test
    void generatesOneInvocationIdAndUsesItAsDefaultRoutingKey() {
        when(admission.admit(eq(alias), any())).thenReturn(selection);
        when(executor.execute(eq(alias), eq(selection), anyMap(), any()))
            .thenAnswer(call -> success(((ProcessExecutionOptions) call.getArgument(3)).getInvocationId()));
        var response = service(false).execute(alias, Map.of(), ProcessExecutionOptions.defaults());
        assertThat(response.invocationId()).startsWith("inv-");
        verify(admission).admit(alias, new AliasRoutingOptions(response.invocationId()));
    }

    @Test
    void preservesStructuredEngineFailure() {
        when(engine.execute(any(ProcessRef.class), anyMap(), any(ProcessExecutionOptions.class)))
            .thenReturn(ProcessResult.failure(new ProcessError("CF_EXEC_004", "Process execution failed"),
                    execution("inv-failure")));
        var response = service(false).execute(version, Map.of(), ProcessExecutionOptions.defaults());
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("CF_EXEC_004");
        assertThat(response.error()).isEqualTo("Process execution failed");
        assertThat(response.invocationId()).isEqualTo("inv-failure");
        assertThat(response.result()).isNull();
    }

    @Test
    void exactExecutionAcquiresAndClosesInstallationWithoutFormatRouting() {
        VersionRuntimeLease lease = mock(VersionRuntimeLease.class);
        when(manager.acquireInstallation(version)).thenReturn(CompletableFuture.completedFuture(lease));
        when(engine.execute(eq(version), anyMap(), any(ProcessExecutionOptions.class))).thenReturn(success("inv-exact"));
        assertThat(service(true).execute(version, Map.of(), ProcessExecutionOptions.defaults()).success()).isTrue();
        var captured = ArgumentCaptor.forClass(ProcessExecutionOptions.class);
        verify(engine).execute(eq(version), eq(Map.of()), captured.capture());
        assertThat(captured.getValue().getInvocationId()).startsWith("inv-");
        assertThat(captured.getValue().getAliasRouting().isEmpty()).isTrue();
        verify(lease).close();
        verifyNoInteractions(admission);
    }

    @Test
    void callerOwnedInstallationIsNotReacquiredOrClosed() {
        VersionRuntimeLease lease = mock(VersionRuntimeLease.class);
        when(engine.execute(eq(version), anyMap(), any(ProcessExecutionOptions.class))).thenReturn(
                success("inv-installed"));
        assertThat(service(true)
            .executeInstalled(version, lease, Map.of(), ProcessExecutionOptions.defaults())
            .success())
            .isTrue();
        verifyNoInteractions(manager, lease, admission);
    }

    @Test
    void resolvesCurrentAliasPinForQueuedExecution() {
        AliasRoutingOptions routing = new AliasRoutingOptions("customer-42");
        when(admission.admit(alias, routing)).thenReturn(selection);
        assertThat(service(false).resolveAliasPin(alias.code(), alias.namespace(), alias.alias(), routing))
            .isEqualTo(new PublishedProcessExecutionService.AliasPin(alias, "v2", 7L, ProcessAliasTarget.STABLE));
        verifyNoInteractions(engine, manager);
    }

    @Test
    void queuedPinIsNeverReadmittedEvenWhenUnavailable() {
        when(executor.execute(eq(alias), eq(selection), anyMap(), any()))
            .thenReturn(ProcessResult.failure(new ProcessError("CF_EXEC_012", "Runtime unavailable"),
                    execution("inv-pinned")));
        var pin = new PublishedProcessExecutionService.AliasPin(alias, "v2", 7L, ProcessAliasTarget.STABLE);
        var response = service(false).execute(pin, Map.of(), ProcessExecutionOptions.defaults());
        assertThat(response.errorCode()).isEqualTo("CF_EXEC_012");
        verify(executor).execute(eq(alias), eq(selection), anyMap(), any());
        verifyNoInteractions(admission);
    }

    @Test
    void retriesUnpinnedAliasAdmissionOnceOnlyForUnavailableRuntime() {
        when(admission.admit(eq(alias), any())).thenReturn(selection);
        when(executor.execute(eq(alias), eq(selection), anyMap(), any()))
            .thenReturn(ProcessResult.failure(new ProcessError("CF_EXEC_012", "Runtime unavailable"),
                    execution("inv-retry")))
            .thenReturn(success("inv-retry"));
        assertThat(service(false).execute(alias, Map.of(), ProcessExecutionOptions.defaults()).success()).isTrue();
        verify(admission, times(2)).admit(eq(alias), any());
    }
}
