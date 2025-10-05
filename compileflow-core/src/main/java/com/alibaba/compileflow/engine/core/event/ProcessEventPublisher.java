/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.infrastructure.LogContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executor.ExecutionContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stateless event publisher that dispatches events to all registered {@link ProcessEventListener}
 * SPI implementations.
 * <p>
 * This class replaces the stateful {@code ProcessEventBus} and delegates the discovery and
 * invocation of listeners to the framework's standard {@link ExtensionInvoker}.
 *
 * @author yusu
 */
public class ProcessEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEventPublisher.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void publish(ProcessEvent event) {
        if (event == null) {
            return;
        }

        ProcessEventListener.ProcessEventExtensionContext ctx = new ProcessEventListener.ProcessEventExtensionContext(event);

        ExtensionInvoker.getInstance().invokeAll(ctx, ProcessEventListener.EXT_EVENT_LISTENER_CODE,
                (ProcessEventListener listener) -> {
                    try {
                        if (listener.isAsync()) {
                            invokeAsync(listener, event);
                        } else {
                            invokeSync(listener, event);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Synchronous event listener [{}] threw an exception for event [{}].",
                                listener.getClass().getName(), event.getEventType(), e);
                    }
                    return null;
                }
        );
    }

    private void invokeSync(ProcessEventListener<?> listener, ProcessEvent event) {
        ((ProcessEventListener) listener).onEvent(event);
    }

    private void invokeAsync(ProcessEventListener<?> listener, ProcessEvent event) {
        ExecutionContextPropagator.ContextSnapshot snap = ExecutionContextPropagator.capture();
        CompletableFuture.runAsync(ExecutionContextPropagator.wrap(() ->
                        ((ProcessEventListener) listener).onEvent(event), snap), EngineExecutionContextHolder.event())
                .exceptionally(ex -> {
                    LOGGER.error("Asynchronous event listener [{}] threw an exception for event [{}].",
                            listener.getClass().getName(), event.getEventType(), ex);
                    return null;
                });
    }

    public void publishExecutionStarted(String processCode) {
        publish(new ProcessCoreEvents.ExecutionStarted(processCode,
                buildContext(null, null)));
    }

    public void publishExecutionCompleted(String processCode, long durationMs) {
        publish(new ProcessCoreEvents.ExecutionCompleted(processCode,
                buildContext(durationMs, null)));
    }

    public void publishExecutionFailed(String processCode, long durationMs, String errorMessage) {
        publish(new ProcessCoreEvents.ExecutionFailed(processCode,
                buildContext(durationMs, errorMessage)));
    }

    public void publishTriggerStarted(String processCode, String tag, String event) {
        publish(new ProcessCoreEvents.TriggerStarted(processCode, tag, event,
                buildContext(null, null)));
    }

    public void publishTriggerCompleted(String processCode, String tag, String event, long durationMs) {
        publish(new ProcessCoreEvents.TriggerCompleted(processCode, tag, event,
                buildContext(durationMs, null)));
    }

    public void publishTriggerFailed(String processCode, String tag, String event, long durationMs, String errorMessage) {
        publish(new ProcessCoreEvents.TriggerFailed(processCode, tag, event,
                buildContext(durationMs, errorMessage)));
    }

    public void publishCompilationCompleted(String[] codes, long durationMs) {
        publish(new ProcessCoreEvents.CompilationCompleted(codes,
                buildContext(durationMs, null)));
    }

    public void publishCompilationFailed(String[] codes, long durationMs, String errorMessage) {
        publish(new ProcessCoreEvents.CompilationFailed(codes,
                buildContext(durationMs, errorMessage)));
    }

    private ProcessEventContext buildContext(Long durationMs, String errorMessage) {
        return ProcessEventContext.builder()
                .traceId(LogContext.getTraceId())
                .threadName(Thread.currentThread().getName())
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .build();
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        private NamedThreadFactory(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public Thread newThread(Runnable r) {
            String threadName = String.format("compileflow-%s-%d", poolName, threadCounter.getAndIncrement());
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) ->
                    LOGGER.error("Uncaught exception in thread [{}].", th.getName(), ex));
            return t;
        }
    }

}
