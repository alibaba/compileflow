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

import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Schedules async invocation dispatch and lease maintenance.
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
final class AsyncInvocationSchedulingConfiguration implements SchedulingConfigurer {
    static final String SCHEDULER_BEAN_NAME = "asyncInvocationTaskScheduler";
    static final int SCHEDULER_PHASE = AsyncInvocationWorker.LIFECYCLE_PHASE - 1;
    private static final int SCHEDULER_THREADS = 3;
    private static final Duration BACKLOG_YIELD = Duration.ofMillis(1);
    private final AsyncInvocationWorker worker;
    private final CompileFlowWorkbenchServerProperties.AsyncInvocation properties;
    private final TaskScheduler scheduler;

    AsyncInvocationSchedulingConfiguration(AsyncInvocationWorker worker,
            CompileFlowWorkbenchServerProperties serverProperties,
            @Qualifier(SCHEDULER_BEAN_NAME) TaskScheduler scheduler) {
        this.worker = worker;
        this.properties = serverProperties.getAsyncInvocation();
        this.scheduler = scheduler;
    }

    @Bean(name = SCHEDULER_BEAN_NAME)
    static ThreadPoolTaskScheduler asyncInvocationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_THREADS);
        scheduler.setThreadNamePrefix("compileflow-async-scheduler-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        // Context close stops new claims, but renewal must survive until worker drain completes.
        scheduler.setAcceptTasksAfterContextClose(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setPhase(SCHEDULER_PHASE);
        return scheduler;
    }

    private static void addFixedDelayTask(ScheduledTaskRegistrar taskRegistrar, Runnable task, Duration interval) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(task, interval, randomInitialDelay(interval)));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(scheduler);
        AdaptiveTask dispatch = new AdaptiveTask(() -> worker.isAcceptingWork(), worker::dispatchQueuedInvocations,
                properties.getDispatchInterval());
        taskRegistrar.addTriggerTask(dispatch, dispatch);
        addFixedDelayTask(taskRegistrar, whenWorkerRunning(worker::renewLocalRunningLeases),
                properties.leaseRenewalDelay());
        AdaptiveTask recovery = new AdaptiveTask(() -> worker.isAcceptingWork(),
                worker::recoverExpiredRunningInvocations, properties.getLeaseRecoveryInterval());
        taskRegistrar.addTriggerTask(recovery, recovery);
    }

    private Runnable whenWorkerRunning(Runnable task) {
        return () -> {
            if (worker.isRunning()) {
                task.run();
            }
        };
    }

    private static Duration randomInitialDelay(Duration interval) {
        long upper = interval.toNanos();
        return upper <= 1L ? Duration.ZERO : Duration.ofNanos(ThreadLocalRandom.current().nextLong(upper));
    }

    private static final class AdaptiveTask implements Runnable, Trigger {
        private final BooleanSupplier enabled;
        private final BooleanSupplier work;
        private final Duration idleDelay;
        private final Duration initialDelay;
        private final AtomicBoolean saturated = new AtomicBoolean();

        private AdaptiveTask(BooleanSupplier enabled, BooleanSupplier work, Duration idleDelay) {
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.work = Objects.requireNonNull(work, "work");
            this.idleDelay = Objects.requireNonNull(idleDelay, "idleDelay");
            this.initialDelay = randomInitialDelay(idleDelay);
        }

        @Override
        public void run() {
            saturated.set(enabled.getAsBoolean() && work.getAsBoolean());
        }

        @Override
        public Instant nextExecution(TriggerContext context) {
            Instant completed = context.lastCompletion();
            if (completed == null) {
                return context.getClock().instant().plus(initialDelay);
            }
            return completed.plus(saturated.get() ? BACKLOG_YIELD : idleDelay);
        }
    }
}
