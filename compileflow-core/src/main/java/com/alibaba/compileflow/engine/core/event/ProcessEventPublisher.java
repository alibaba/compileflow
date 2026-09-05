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

import com.alibaba.compileflow.engine.ProcessError;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessObservabilityConfig;
import com.alibaba.compileflow.engine.core.observability.LogContext;
import com.alibaba.compileflow.engine.core.runtime.execution.ExecutionContextPropagator;
import com.alibaba.compileflow.engine.core.runtime.execution.ExecutionContextPropagator.ContextSnapshot;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent.ExecutionAttribution;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches engine lifecycle events to the listeners frozen into the engine configuration.
 * <p>
 * Listener failures are isolated: one failing listener is logged and does not affect
 * process execution or other listeners. When asynchronous dispatch is enabled, events
 * are delivered on the engine-owned event executor, which the engine closes on shutdown.
 *
 * @author yusu
 */
public final class ProcessEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEventPublisher.class);
    private final List<ProcessEventListener> listeners;
    private final boolean async;
    private final boolean mdcPropagationEnabled;
    private final Executor eventExecutor;

    /**
     * Creates a publisher bound to one engine's listener set.
     *
     * @param observabilityConfig immutable observability configuration
     * @param listeners           immutable ordered listener snapshot
     * @param eventExecutor       engine-owned executor for asynchronous dispatch, or {@code null}
     *                            to force synchronous dispatch
     */
    public ProcessEventPublisher(ProcessObservabilityConfig observabilityConfig, List<ProcessEventListener> listeners,
            Executor eventExecutor) {
        Objects.requireNonNull(observabilityConfig, "observabilityConfig");
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners"));
        this.async = observabilityConfig.isEventsAsync() && eventExecutor != null;
        this.mdcPropagationEnabled = observabilityConfig.isMdcPropagationEnabled();
        this.eventExecutor = eventExecutor;
    }

    public void publishExecutionStarted(String namespace, String processCode, String invocationId) {
        publish(occurredAt -> new ProcessEvent.ExecutionStarted(namespace, processCode, invocationId,
                LogContext.getTraceId(), occurredAt));
    }

    public void publishExecutionCompleted(ProcessExecution execution, ExecutionAttribution attribution, long durationMs) {
        publish(occurredAt -> new ProcessEvent.ExecutionCompleted(execution, attribution, durationMs, occurredAt));
    }

    public void publishExecutionFailed(ProcessExecution execution, ExecutionAttribution attribution, long durationMs,
            ProcessError error) {
        publish(occurredAt -> new ProcessEvent.ExecutionFailed(execution, attribution, durationMs, error, occurredAt));
    }

    public void publishTriggerStarted(String namespace, String processCode, String invocationId, ProcessTrigger trigger) {
        publish(occurredAt -> new ProcessEvent.TriggerStarted(namespace, processCode, invocationId, trigger,
                LogContext.getTraceId(), occurredAt));
    }

    public void publishTriggerCompleted(ProcessExecution execution, ExecutionAttribution attribution,
            ProcessTrigger trigger, long durationMs) {
        publish(occurredAt -> new ProcessEvent.TriggerCompleted(execution, attribution, trigger, durationMs, occurredAt));
    }

    public void publishTriggerFailed(ProcessExecution execution, ExecutionAttribution attribution,
            ProcessTrigger trigger, long durationMs, ProcessError error) {
        publish(occurredAt -> new ProcessEvent.TriggerFailed(execution, attribution, trigger, durationMs, error,
                occurredAt));
    }

    private void publish(EventFactory eventFactory) {
        if (listeners.isEmpty()) {
            return;
        }
        ProcessEvent event = eventFactory.create(Instant.now());
        if (async) {
            try {
                ContextSnapshot context = ExecutionContextPropagator.capture(mdcPropagationEnabled);
                eventExecutor.execute(() -> dispatchAsync(event, context));
            } catch (RejectedExecutionException ex) {
                recordDropped(event, "event executor rejected delivery", ex);
            } catch (RuntimeException failure) {
                recordDropped(event, "application context capture failed", failure);
            }
        } else {
            dispatch(event);
        }
    }

    private void dispatchAsync(ProcessEvent event, ContextSnapshot context) {
        try {
            ExecutionContextPropagator
                .wrap(() -> dispatch(event), context)
                .run();
        } catch (RuntimeException failure) {
            recordDropped(event, "application context binding failed", failure);
        }
    }

    private static void recordDropped(ProcessEvent event, String reason, RuntimeException failure) {
        ProcessEventDeliveryMetrics.global().recordDropped();
        LOGGER.warn("Dropping best-effort async {} event: reason={}, failureType={}", event.getType(), reason,
                failure.getClass().getName());
        LOGGER.debug("Async process event delivery failure: type={}, reason={}", event.getType(), reason, failure);
    }

    private void dispatch(ProcessEvent event) {
        for (ProcessEventListener listener : listeners) {
            try {
                if (listener.supports(event)) {
                    listener.onEvent(event);
                }
            } catch (RuntimeException failure) {
                String listenerType = listener.getClass().getName();
                LOGGER.warn("Process event listener {} failed on {} event: failureType={}", listenerType,
                        event.getType(), failure.getClass().getName());
                LOGGER.debug("Process event listener failure detail: listener={}, event={}", listenerType,
                        event.getType(), failure);
            }
        }
    }

    @FunctionalInterface
    private interface EventFactory {
        ProcessEvent create(Instant occurredAt);
    }
}
