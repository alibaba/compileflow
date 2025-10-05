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
package com.alibaba.compileflow.engine.core.executor;

import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated executors for one engine instance (compilation/execution/event/schedule).
 * Resource-heavy; prefer engine singleton and close to release.
 *
 * @author yusu
 */
public final class ProcessEngineExecutors implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessEngineExecutors.class);

    // Shutdown timeouts (seconds)
    private static final long GRACEFUL_SECONDS = 10L;
    private static final long FORCED_SECONDS = 5L;

    private static final String THREAD_NAME_COMPILATION = "compilation";
    private static final String THREAD_NAME_EXECUTION = "execution";
    private static final String THREAD_NAME_EVENT = "event";
    private static final String THREAD_NAME_SCHEDULE = "schedule";

    // Thread name pattern
    private static final String THREAD_NAME_PATTERN = "compileflow-%s-%s-%d";
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    private final String engineId;
    private final ExecutorService compilationExecutor;
    private final ExecutorService executionExecutor;
    private final ExecutorService eventExecutor;
    private final ScheduledExecutorService scheduleExecutor;

    private ProcessEngineExecutors(String engineId, ProcessExecutorConfig config) {
        this.engineId = engineId;
        this.compilationExecutor = createCompilationExecutor(config);
        this.executionExecutor = createExecutionExecutor(config);
        this.eventExecutor = createEventExecutor(config);
        this.scheduleExecutor = createScheduleExecutor(config);

        LOGGER.info("ProcessEngineExecutors initialized for engineId [{}]: " +
                        "compilation[threads={}, queue={}, keepAlive={}s], " +
                        "execution[core={}, max={}, queue={}, keepAlive={}s], " +
                        "event[threads={}, max={}, queue={}, keepAlive={}s], " +
                        "schedule[threads={}]",
                engineId,
                config.getCompilationThreads(),
                ProcessPropertyProvider.Executor.getCompilationQueueSize(),
                ProcessPropertyProvider.Executor.getCompilationKeepAliveSeconds(),
                config.getExecutionThreads(),
                Math.max(config.getExecutionThreads(),
                        config.getExecutionThreads() * ProcessPropertyProvider.Executor.getExecutionMaxThreadsMultiplier()),
                ProcessPropertyProvider.Executor.getExecutionQueueSize(),
                ProcessPropertyProvider.Executor.getExecutionKeepAliveSeconds(),
                config.getEventThreads(),
                Math.max(config.getEventThreads(),
                        config.getEventThreads() * ProcessPropertyProvider.Executor.getEventMaxThreadsMultiplier()),
                ProcessPropertyProvider.Executor.getEventQueueSize(),
                ProcessPropertyProvider.Executor.getEventKeepAliveSeconds(),
                config.getScheduleThreads());
    }

    /**
     * Create executors with an auto-generated engine id.
     */
    public static ProcessEngineExecutors create(ProcessExecutorConfig config) {
        return create(generateEngineId(), config);
    }

    /**
     * Create executors with a specific engine id and configuration.
     */
    public static ProcessEngineExecutors create(String engineId, ProcessExecutorConfig config) {
        if (StringUtils.isEmpty(engineId)) {
            throw new IllegalArgumentException("Engine ID cannot be empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("ExecutorConfig cannot be null");
        }
        return new ProcessEngineExecutors(engineId, config);
    }

    private static String generateEngineId() {
        return "engine-" + INSTANCE_COUNTER.incrementAndGet();
    }

    /**
     * Compilation executor.
     */
    public ExecutorService compilation() {
        return compilationExecutor;
    }

    /**
     * Execution executor.
     */
    public ExecutorService execution() {
        return executionExecutor;
    }

    /**
     * Event executor.
     */
    public ExecutorService event() {
        return eventExecutor;
    }

    /**
     * Schedule executor.
     */
    public ScheduledExecutorService schedule() {
        return scheduleExecutor;
    }

    /**
     * Engine id this executor set is bound to.
     */
    public String engineId() {
        return engineId;
    }

    /**
     * Graceful shutdown then forced shutdown if needed.
     */
    @Override
    public void close() {
        LOGGER.info("Shutting down executors for engineId [{}]...", engineId);

        shutdownExecutor("compilation", compilationExecutor);
        shutdownExecutor("execution", executionExecutor);
        shutdownExecutor("event", eventExecutor);
        shutdownExecutor("schedule", scheduleExecutor);

        LOGGER.info("All executors for engineId [{}] have been shut down.", engineId);
    }

    @Override
    public String toString() {
        return String.format("ProcessEngineExecutors{engineId='%s'}", engineId);
    }

    /**
     * Fixed-size pool for compilation with caller-runs backpressure.
     */
    private ExecutorService createCompilationExecutor(ProcessExecutorConfig config) {
        int threads = config.getCompilationThreads();
        int queue = ProcessPropertyProvider.Executor.getCompilationQueueSize();
        long keepAlive = ProcessPropertyProvider.Executor.getCompilationKeepAliveSeconds();
        return new ThreadPoolExecutor(
                threads, threads,
                keepAlive, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                threadFactory(THREAD_NAME_COMPILATION),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Scalable pool for execution with caller-runs backpressure.
     */
    private ExecutorService createExecutionExecutor(ProcessExecutorConfig config) {
        int core = config.getExecutionThreads();
        int max = Math.max(core, core * ProcessPropertyProvider.Executor.getExecutionMaxThreadsMultiplier());
        int queue = ProcessPropertyProvider.Executor.getExecutionQueueSize();
        long keepAlive = ProcessPropertyProvider.Executor.getExecutionKeepAliveSeconds();
        return new ThreadPoolExecutor(
                core, max,
                keepAlive, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                threadFactory(THREAD_NAME_EXECUTION),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Scalable pool for events with discard-oldest policy.
     */
    private ExecutorService createEventExecutor(ProcessExecutorConfig config) {
        int core = config.getEventThreads();
        int max = Math.max(core, core * ProcessPropertyProvider.Executor.getEventMaxThreadsMultiplier());
        int queue = ProcessPropertyProvider.Executor.getEventQueueSize();
        long keepAlive = ProcessPropertyProvider.Executor.getEventKeepAliveSeconds();
        return new ThreadPoolExecutor(
                core, max,
                keepAlive, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                threadFactory(THREAD_NAME_EVENT),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    /**
     * Fixed-size pool for scheduled tasks.
     */
    private ScheduledExecutorService createScheduleExecutor(ProcessExecutorConfig config) {
        return new ScheduledThreadPoolExecutor(
                config.getScheduleThreads(),
                threadFactory(THREAD_NAME_SCHEDULE)
        );
    }

    private ThreadFactory threadFactory(String executorType) {
        return new EngineThreadFactory(engineId, executorType);
    }

    /**
     * Graceful shutdown with timeout; force on timeout.
     */
    private void shutdownExecutor(String name, ExecutorService executor) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(GRACEFUL_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Executor [{}] for engineId [{}] did not terminate gracefully after {}s. Forcing shutdown...",
                        name, engineId, GRACEFUL_SECONDS);
                executor.shutdownNow();
                if (!executor.awaitTermination(FORCED_SECONDS, TimeUnit.SECONDS)) {
                    LOGGER.error("Executor [{}] for engineId [{}] failed to terminate after forced shutdown.", name, engineId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            LOGGER.warn("Interrupted while shutting down executor [{}]. Forcing immediate shutdown.", name, e);
        }
    }

    /**
     * ThreadFactory with named threads and default uncaught exception handler.
     */
    private static final class EngineThreadFactory implements ThreadFactory {
        private final String engineId;
        private final String executorType;
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        EngineThreadFactory(String engineId, String executorType) {
            this.engineId = engineId;
            this.executorType = executorType;
        }

        @Override
        public Thread newThread(Runnable r) {
            String threadName = String.format(THREAD_NAME_PATTERN,
                    engineId, executorType, threadCounter.getAndIncrement());
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, ex) ->
                    LOGGER.error("Uncaught exception in thread [{}]", t.getName(), ex));
            return thread;
        }
    }
}
