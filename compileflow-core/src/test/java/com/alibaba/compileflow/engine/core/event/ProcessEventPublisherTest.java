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
package com.alibaba.compileflow.engine.core.event;

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionScope;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ProcessEventPublisherTest {
    private static ProcessEventPublisher publisher(List<ProcessEvent> received) {
        return new ProcessEventPublisher(ProcessObservabilityConfig.builder().eventsAsync(false).build(),
                List.of(received::add), null);
    }

    private static ProcessExecution execution() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return ProcessExecution
            .builder()
            .traceId("trace-1")
            .invocationId("request-1")
            .namespace("default")
            .processCode("order.approve")
            .processVersion(ProcessRef.version("default", "order.approve", "v2"))
            .startedAt(now.minusMillis(12L))
            .completedAt(now)
            .build();
    }

    private static ProcessEvent.ExecutionAttribution attribution() {
        return new ProcessEvent.ExecutionAttribution(null, 0, ProcessModelType.TBBPM, null, null, null, null);
    }

    @Test
    void dispatchesTypedLifecycleEventsToConfiguredListeners() {
        List<ProcessEvent> received = new CopyOnWriteArrayList<>();
        ProcessEventPublisher publisher = publisher(received);
        ProcessExecution execution = execution();
        ProcessError error = new ProcessError("CF_EXEC_001", "failed");

        publisher.publishExecutionStarted("default", "order.approve", "request-1");
        publisher.publishExecutionCompleted(execution, attribution(), 12L);
        publisher.publishExecutionFailed(execution, attribution(), 12L, error);

        assertThat(received)
            .extracting(ProcessEvent::getType)
            .containsExactly(ProcessEvent.Type.EXECUTION_STARTED, ProcessEvent.Type.EXECUTION_COMPLETED,
                    ProcessEvent.Type.EXECUTION_FAILED);
        assertThat(received.get(0))
            .isInstanceOfSatisfying(ProcessEvent.ExecutionStarted.class, event -> assertThat(event.invocationId())
                .isEqualTo("request-1"));
        assertThat(received.get(1)).isInstanceOfSatisfying(ProcessEvent.ExecutionCompleted.class, event -> {
            assertThat(event.execution()).isSameAs(execution);
            assertThat(event.durationMs()).isEqualTo(12L);
        });
        assertThat(received.get(2))
            .isInstanceOfSatisfying(ProcessEvent.ExecutionFailed.class, event -> assertThat(event.error())
                .isSameAs(error));
    }

    @Test
    void isolatesFailingListeners() {
        List<ProcessEvent> received = new CopyOnWriteArrayList<>();
        ProcessEventListener failing = event -> {
            throw new IllegalStateException("listener failure");
        };
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(false)
                    .build(), List.of(failing, received::add), null);

        assertThatCode(() -> publisher.publishExecutionStarted("default", "order.approve", "request-1"))
            .doesNotThrowAnyException();
        assertThat(received).hasSize(1);
    }

    @Test
    void evaluatesListenerSupportBeforeDelivery() {
        List<ProcessEvent> received = new CopyOnWriteArrayList<>();
        ProcessEventListener completedOnly = new ProcessEventListener() {
            @Override
            public boolean supports(ProcessEvent event) {
                return event.getType() == ProcessEvent.Type.EXECUTION_COMPLETED;
            }

            @Override
            public void onEvent(ProcessEvent event) {
                received.add(event);
            }
        };
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(false)
                    .build(), List.of(completedOnly), null);

        publisher.publishExecutionStarted("default", "order.approve", "request-1");
        publisher.publishExecutionCompleted(execution(), attribution(), 12L);

        assertThat(received).extracting(ProcessEvent::getType).containsExactly(ProcessEvent.Type.EXECUTION_COMPLETED);
    }

    @Test
    void isolatesListenerSupportFailures() {
        List<ProcessEvent> received = new CopyOnWriteArrayList<>();
        ProcessEventListener failingPredicate = new ProcessEventListener() {
            @Override
            public boolean supports(ProcessEvent event) {
                throw new IllegalStateException("predicate failure");
            }

            @Override
            public void onEvent(ProcessEvent event) {
                throw new AssertionError("unsupported listener must not be invoked");
            }
        };
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(false)
                    .build(), List.of(failingPredicate, received::add), null);

        assertThatCode(() -> publisher.publishExecutionStarted("default", "order.approve", "request-1"))
            .doesNotThrowAnyException();
        assertThat(received).hasSize(1);
    }

    @Test
    void skipsDispatchWithoutListeners() {
        ProcessEventPublisher publisher =
                new ProcessEventPublisher(ProcessObservabilityConfig.defaults(), List.of(), null);

        assertThatCode(() -> {
            publisher.publishExecutionStarted("default", "order.approve", "request-1");
        }).doesNotThrowAnyException();
    }

    @Test
    void dispatchesTriggerEvents() {
        List<ProcessEvent> received = new CopyOnWriteArrayList<>();
        ProcessEventPublisher publisher = publisher(received);
        ProcessTrigger trigger = ProcessTrigger.on("wait1", "resume");
        ProcessExecution execution = execution();
        ProcessError triggerError = new ProcessError("CF_EXEC_001", "boom");

        publisher.publishTriggerStarted("default", "order.approve", "request-1", trigger);
        publisher.publishTriggerCompleted(execution, attribution(), trigger, 3L);
        publisher.publishTriggerFailed(execution, attribution(), trigger, 3L, triggerError);
        assertThat(received)
            .extracting(ProcessEvent::getType)
            .containsExactly(ProcessEvent.Type.TRIGGER_STARTED, ProcessEvent.Type.TRIGGER_COMPLETED,
                    ProcessEvent.Type.TRIGGER_FAILED);
        assertThat(received.get(0))
            .isInstanceOfSatisfying(ProcessEvent.TriggerStarted.class, event -> assertThat(event.trigger())
                .isEqualTo(trigger));
    }

    @Test
    void dispatchesAsynchronouslyOnTheEventExecutor() {
        List<String> dispatchThreads = new CopyOnWriteArrayList<>();
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(true)
                    .build(), List.of(event -> dispatchThreads.add(Thread.currentThread().getName())), Runnable::run);

        publisher.publishExecutionStarted("default", "order.approve", "request-1");

        assertThat(dispatchThreads).hasSize(1);
    }

    @Test
    void propagatesAndRestoresMdcForAsynchronousDeliveryWhenEnabled() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<String> observedTenant = new AtomicReference<>();
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(true)
                    .mdcPropagationEnabled(true)
                    .build(), List.of(event -> observedTenant.set(MDC.get("tenant"))), queued::set);
        MDC.put("tenant", "tenant-a");
        try {
            publisher.publishExecutionStarted("default", "order.approve", "request-1");
        } finally {
            MDC.remove("tenant");
        }

        queued.get().run();

        assertThat(observedTenant).hasValue("tenant-a");
        assertThat(MDC.get("tenant")).isNull();
    }

    @Test
    void dropsAsyncDeliveryWhenTheEventExecutorRejects() {
        List<String> dispatchThreads = new CopyOnWriteArrayList<>();
        long droppedBefore = ProcessEventDeliveryMetrics.global().droppedCount();
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
            .builder()
            .eventsAsync(true)
            .build(), List.of(event -> dispatchThreads.add(Thread.currentThread().getName())), command -> {
            throw new RejectedExecutionException("saturated");
        });

        publisher.publishExecutionStarted("default", "order.approve", "request-1");

        assertThat(dispatchThreads).isEmpty();
        assertThat(ProcessEventDeliveryMetrics.global().droppedCount()).isEqualTo(droppedBefore + 1L);
    }

    @Test
    void dropsAsyncDeliveryWhenApplicationContextPropagationFails() {
        long droppedBefore = ProcessEventDeliveryMetrics.global().droppedCount();
        ProcessEventPublisher publisher = new ProcessEventPublisher(ProcessObservabilityConfig
                    .builder()
                    .eventsAsync(true)
                    .build(), List.of(event -> {}), Runnable::run);
        ProcessEngineExecutors executors =
                ProcessEngineExecutors.create("event-context-test", ProcessExecutorConfig.defaults());
        try {
            EngineExecutionContext captureFailure = executionContext(executors, () -> {
                throw new IllegalStateException("capture failed");
            });
            try (EngineExecutionScope scope = EngineExecutionScope.open(captureFailure)) {
                assertThat(scope.context()).isSameAs(captureFailure);
                assertThatCode(() -> publisher.publishExecutionStarted("default", "order.approve", "request-1"))
                    .doesNotThrowAnyException();
            }

            EngineExecutionContext openFailure = executionContext(executors, () -> () -> {
                throw new IllegalStateException("open failed");
            });
            try (EngineExecutionScope scope = EngineExecutionScope.open(openFailure)) {
                assertThat(scope.context()).isSameAs(openFailure);
                assertThatCode(() -> publisher.publishExecutionStarted("default", "order.approve", "request-2"))
                    .doesNotThrowAnyException();
            }
        } finally {
            executors.close();
        }

        assertThat(ProcessEventDeliveryMetrics.global().droppedCount()).isEqualTo(droppedBefore + 2L);
    }

    @Test
    void requiresObservabilityConfiguration() {
        assertThatNullPointerException().isThrownBy(() -> new ProcessEventPublisher(null, List.of(), null));
    }

    private static EngineExecutionContext executionContext(ProcessEngineExecutors executors,
            ProcessContextPropagator propagator) {
        return EngineExecutionContext
            .builder()
            .namespace("default")
            .processCode("order.approve")
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(ScriptExecutorRegistry.builtIns(ProcessEngineConfig.tbbpm()))
            .contextPropagator(propagator)
            .build();
    }
}
