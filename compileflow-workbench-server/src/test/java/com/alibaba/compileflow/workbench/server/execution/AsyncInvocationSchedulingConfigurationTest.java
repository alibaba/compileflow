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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.Authentication;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.AuthenticationMode;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.Http;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.util.unit.DataSize;

class AsyncInvocationSchedulingConfigurationTest {
    private static final int WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE = SmartLifecycle.DEFAULT_PHASE - 1024;

    private static CompileFlowWorkbenchServerProperties serverProperties() {
        Authentication authentication = new Authentication(AuthenticationMode.DISABLED, "", "local-test");
        Http http = new Http(DataSize.ofMegabytes(10));
        return new CompileFlowWorkbenchServerProperties(authentication, http,
                new CompileFlowWorkbenchServerProperties.Database(CompileFlowWorkbenchServerProperties.Database.Provider.POSTGRESQL,
                        true), new CompileFlowWorkbenchServerProperties.PreviewExecution(false),
                new CompileFlowWorkbenchServerProperties.ExecutionLog(10_000, 1_000),
                new CompileFlowWorkbenchServerProperties.AsyncInvocation(2, Duration.ofMillis(250),
                        Duration.ofSeconds(6), Duration.ofMillis(1250)));
    }

    @Test
    void registersValidatedIntervalsWithoutReadingExternalConfigurationAgain() {
        CompileFlowWorkbenchServerProperties serverProperties = serverProperties();
        AsyncInvocationWorker worker = mock(AsyncInvocationWorker.class);
        AsyncInvocationSchedulingConfiguration configuration =
                new AsyncInvocationSchedulingConfiguration(worker, serverProperties, mock(TaskScheduler.class));
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        configuration.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList())
            .extracting(IntervalTask::getIntervalDuration)
            .containsExactly(Duration.ofSeconds(2));
        assertThat(registrar.getFixedDelayTaskList())
            .extracting(IntervalTask::getInitialDelayDuration)
            .allSatisfy(delay -> assertThat(delay).isBetween(Duration.ZERO, Duration.ofSeconds(2)));
        assertThat(registrar.getTriggerTaskList()).hasSize(2);

        Instant completed = Instant.parse("2026-09-03T00:00:00Z");
        SimpleTriggerContext context = new SimpleTriggerContext(completed, completed, completed);
        registrar
            .getTriggerTaskList()
            .forEach(task -> task.getRunnable().run());
        assertThat(registrar.getTriggerTaskList())
            .extracting(task -> Duration.between(completed, task.getTrigger().nextExecution(context)))
            .containsExactly(Duration.ofMillis(250), Duration.ofMillis(1250));

        when(worker.isAcceptingWork()).thenReturn(true);
        when(worker.dispatchQueuedInvocations()).thenReturn(true);
        when(worker.recoverExpiredRunningInvocations()).thenReturn(true);
        registrar
            .getTriggerTaskList()
            .forEach(task -> task.getRunnable().run());
        assertThat(registrar.getTriggerTaskList())
            .extracting(task -> Duration.between(completed, task.getTrigger().nextExecution(context)))
            .containsOnly(Duration.ofMillis(1));
    }

    @Test
    void isolatesDispatchRenewalAndRecoveryOnAThreeThreadScheduler() {
        ThreadPoolTaskScheduler scheduler = AsyncInvocationSchedulingConfiguration.asyncInvocationTaskScheduler();
        scheduler.initialize();
        try {
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("compileflow-async-scheduler-");
            assertThat(scheduler.getPhase())
                .isEqualTo(AsyncInvocationSchedulingConfiguration.SCHEDULER_PHASE)
                .isLessThan(AsyncInvocationWorker.LIFECYCLE_PHASE)
                .isLessThan(WEB_SERVER_GRACEFUL_SHUTDOWN_PHASE);
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void keepsTheSchedulerAliveAfterContextCloseSoRunningLeasesCanRenew() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        ThreadPoolTaskScheduler scheduler = AsyncInvocationSchedulingConfiguration.asyncInvocationTaskScheduler();
        scheduler.setApplicationContext(context);
        scheduler.initialize();
        try {
            scheduler.onApplicationEvent(new ContextClosedEvent(context));

            assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isFalse();
        } finally {
            scheduler.shutdown();
            context.close();
        }
    }

    @Test
    void contextCloseStopsAdmissionButKeepsLeaseRenewalRunningDuringDrain() {
        AsyncInvocationWorker service = mock(AsyncInvocationWorker.class);
        CompileFlowWorkbenchServerProperties serverProperties = serverProperties();
        AsyncInvocationSchedulingConfiguration configuration =
                new AsyncInvocationSchedulingConfiguration(service, serverProperties, mock(TaskScheduler.class));
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        configuration.configureTasks(registrar);

        runRegisteredTasks(registrar);

        verify(service, never()).dispatchQueuedInvocations();
        verify(service, never()).renewLocalRunningLeases();
        verify(service, never()).recoverExpiredRunningInvocations();

        when(service.isRunning()).thenReturn(true);
        when(service.isAcceptingWork()).thenReturn(true);
        runRegisteredTasks(registrar);

        verify(service).dispatchQueuedInvocations();
        verify(service).renewLocalRunningLeases();
        verify(service).recoverExpiredRunningInvocations();

        when(service.isAcceptingWork()).thenReturn(false);
        runRegisteredTasks(registrar);

        verify(service, times(1)).dispatchQueuedInvocations();
        verify(service, times(2)).renewLocalRunningLeases();
        verify(service, times(1)).recoverExpiredRunningInvocations();
    }

    private static void runRegisteredTasks(ScheduledTaskRegistrar registrar) {
        registrar
            .getFixedDelayTaskList()
            .forEach(task -> task.getRunnable().run());
        registrar.getTriggerTaskList().stream().map(TriggerTask::getRunnable).forEach(Runnable::run);
    }
}
