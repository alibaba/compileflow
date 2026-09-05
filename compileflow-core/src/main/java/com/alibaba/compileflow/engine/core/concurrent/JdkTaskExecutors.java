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
package com.alibaba.compileflow.engine.core.concurrent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Creates task executors that use the best thread implementation available on the runtime JDK.
 *
 * @author yusu
 */
public final class JdkTaskExecutors {
    private static final int VIRTUAL_THREAD_MINIMUM_JAVA = 21;
    private static final VirtualThreadMethods VIRTUAL_THREAD_METHODS = resolveVirtualThreadMethods();

    private JdkTaskExecutors() {
    }

    /**
     * Creates an executor suitable for nested, potentially blocking orchestration tasks.
     *
     * <p>Java 21 and newer use one virtual thread per task. Java 17 uses a finite unqueued
     * platform-thread executor. When every owned platform thread is busy, the submitting thread
     * executes the task, preserving progress for nested branches while applying backpressure.</p>
     *
     * @param threadNamePrefix prefix used for worker thread names
     * @param exceptionHandler handler for uncaught worker failures
     * @return a new executor owned by the caller
     */
    public static ExecutorService newOrchestrationExecutor(String threadNamePrefix,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        return newOrchestrationExecutor(threadNamePrefix, exceptionHandler, UnaryOperator.identity());
    }

    static ExecutorService newOrchestrationExecutor(String threadNamePrefix,
            Thread.UncaughtExceptionHandler exceptionHandler, UnaryOperator<Runnable> taskDecorator) {
        return newOrchestrationExecutor(threadNamePrefix, defaultMaxPlatformThreads(), exceptionHandler, taskDecorator);
    }

    static ExecutorService newOrchestrationExecutor(String threadNamePrefix, int maxPlatformThreads,
            Thread.UncaughtExceptionHandler exceptionHandler) {
        return newOrchestrationExecutor(threadNamePrefix, maxPlatformThreads, exceptionHandler, UnaryOperator.identity());
    }

    static ExecutorService newOrchestrationExecutor(String threadNamePrefix, int maxPlatformThreads,
            Thread.UncaughtExceptionHandler exceptionHandler, UnaryOperator<Runnable> taskDecorator) {
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("threadNamePrefix must not be blank");
        }
        if (exceptionHandler == null) {
            throw new IllegalArgumentException("exceptionHandler must not be null");
        }
        if (maxPlatformThreads <= 0) {
            throw new IllegalArgumentException("maxPlatformThreads must be positive");
        }
        Objects.requireNonNull(taskDecorator, "taskDecorator");
        if (VIRTUAL_THREAD_METHODS != null) {
            return VIRTUAL_THREAD_METHODS.newExecutor(threadNamePrefix, exceptionHandler, taskDecorator);
        }
        return newPlatformThreadExecutor(threadNamePrefix, maxPlatformThreads, exceptionHandler, taskDecorator);
    }

    static boolean usesVirtualThreads() {
        return VIRTUAL_THREAD_METHODS != null;
    }

    static int defaultMaxPlatformThreads() {
        long cpuScaled = (long) Runtime.getRuntime().availableProcessors() * 4L;
        return (int) Math.max(8L, Math.min(64L, cpuScaled));
    }

    private static ExecutorService newPlatformThreadExecutor(String threadNamePrefix, int maxPlatformThreads,
            Thread.UncaughtExceptionHandler exceptionHandler, UnaryOperator<Runnable> taskDecorator) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory =
                task -> {
            Thread thread = new Thread(decorate(taskDecorator, task), threadNamePrefix + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(exceptionHandler);
            return thread;
        };
        RejectedExecutionHandler callerRuns =
                (task, executor) -> {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Orchestration executor is shut down");
            }
            decorate(taskDecorator, task).run();
        };
        return new ThreadPoolExecutor(0, maxPlatformThreads, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), factory,
                callerRuns);
    }

    private static Runnable decorate(UnaryOperator<Runnable> taskDecorator, Runnable task) {
        return Objects.requireNonNull(taskDecorator.apply(Objects.requireNonNull(task, "task")),
                "taskDecorator returned null");
    }

    private static VirtualThreadMethods resolveVirtualThreadMethods() {
        if (Runtime.version().feature() < VIRTUAL_THREAD_MINIMUM_JAVA) {
            return null;
        }
        try {
            Class<?> builderType = Class.forName("java.lang.Thread$Builder$OfVirtual");
            return new VirtualThreadMethods(Thread.class.getMethod("ofVirtual"),
                    builderType.getMethod("name", String.class, long.class),
                    builderType.getMethod("uncaughtExceptionHandler", Thread.UncaughtExceptionHandler.class),
                    builderType.getMethod("factory"),
                    Executors.class.getMethod("newThreadPerTaskExecutor", ThreadFactory.class));
        } catch (ClassNotFoundException | NoSuchMethodException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private record VirtualThreadMethods(Method ofVirtual, Method name, Method uncaughtExceptionHandler, Method factory,
            Method newThreadPerTaskExecutor) {
        ExecutorService newExecutor(String threadNamePrefix, Thread.UncaughtExceptionHandler exceptionHandler,
                UnaryOperator<Runnable> taskDecorator) {
            try {
                Object builder = ofVirtual.invoke(null);
                builder = name.invoke(builder, threadNamePrefix, 0L);
                builder = uncaughtExceptionHandler.invoke(builder, exceptionHandler);
                ThreadFactory baseFactory = (ThreadFactory) factory.invoke(builder);
                ThreadFactory decoratedFactory = task -> baseFactory.newThread(decorate(taskDecorator, task));
                return (ExecutorService) newThreadPerTaskExecutor.invoke(null, decoratedFactory);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("The runtime denied access to its public virtual-thread APIs", failure);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Virtual-thread executor creation failed", cause);
            }
        }
    }
}
