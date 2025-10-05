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
package com.alibaba.compileflow.engine.core.runtime.context;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.core.executor.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.executor.DynamicClassExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link ThreadLocal} holder for the current {@link EngineExecutionContext}.
 * <p>
 * This class provides static access to the context of the currently executing process,
 * including its variables, state, and associated engine components like executors.
 * It is a critical component for enabling process logic (especially in generated code)
 * to interact with the engine runtime without needing to pass the context object
 * through every method call.
 * <p>
 * <b>Important:</b> The context is thread-bound. It is essential to clear the context
 * in a {@code finally} block after an execution completes to prevent memory leaks in
 * thread-pooled environments.
 *
 * <pre>{@code
 * EngineExecutionContextHolder.set(context);
 * try {
 *     // ... process execution logic ...
 * } finally {
 *     EngineExecutionContextHolder.clear();
 * }
 * }</pre>
 *
 * @author yusu
 */
public final class EngineExecutionContextHolder {

    private static final ThreadLocal<EngineExecutionContext> CONTEXT = new ThreadLocal<>();

    private EngineExecutionContextHolder() {
    }

    /**
     * Retrieves the {@link EngineExecutionContext} bound to the current thread.
     *
     * @return The current context, or {@code null} if none is set.
     */
    public static EngineExecutionContext current() {
        return CONTEXT.get();
    }

    /**
     * Binds the given {@link EngineExecutionContext} to the current thread.
     *
     * @param context The context to set.
     */
    public static void set(EngineExecutionContext context) {
        CONTEXT.set(context);
    }

    /**
     * Clears the {@link EngineExecutionContext} from the current thread.
     * <p>
     * <b>Critical:</b> This method must be called in a {@code finally} block
     * to prevent memory leaks in application server environments.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Checks if an {@link EngineExecutionContext} is currently bound to this thread.
     *
     * @return {@code true} if a context is available, {@code false} otherwise.
     */
    public static boolean isAvailable() {
        return CONTEXT.get() != null;
    }

    /**
     * Returns the compilation executor for the current execution context.
     *
     * @return The compilation {@link ExecutorService}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ExecutorService compilation() {
        return requireContext().compilation();
    }

    /**
     * Returns the execution executor for the current execution context.
     *
     * @return The execution {@link ExecutorService}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ExecutorService execution() {
        return requireContext().execution();
    }

    /**
     * Returns the event executor for the current execution context.
     *
     * @return The event {@link ExecutorService}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ExecutorService event() {
        return requireContext().event();
    }

    /**
     * Returns the schedule executor for the current execution context.
     *
     * @return The {@link ScheduledExecutorService}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ScheduledExecutorService schedule() {
        return requireContext().schedule();
    }

    /**
     * Returns the dynamic class executor for the current execution context.
     *
     * @return The {@link DynamicClassExecutor}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static DynamicClassExecutor dynamicClassExecutor() {
        return requireContext().dynamicClassExecutor();
    }

    /**
     * Returns the {@link ProcessEngine} instance for the current execution.
     *
     * @return The current {@link ProcessEngine}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ProcessEngine processEngine() {
        return requireContext().processEngine();
    }

    /**
     * Returns the {@link ProcessEngineExecutors} for the current execution.
     *
     * @return The current {@link ProcessEngineExecutors}.
     * @throws IllegalStateException if no execution context is available.
     */
    public static ProcessEngineExecutors executors() {
        return requireContext().executors();
    }

    // ========== Simplified Accessors for Generated Code ==========

    /**
     * Simplified accessor for the dynamic class executor.
     * Same as {@link #dynamicClassExecutor()} but with a shorter name for cleaner generated code.
     */
    public static DynamicClassExecutor executor() {
        return dynamicClassExecutor();
    }

    /**
     * Simplified accessor for the process engine.
     * Same as {@link #processEngine()} but with a shorter name for cleaner generated code.
     */
    public static ProcessEngine engine() {
        return processEngine();
    }

    /**
     * Simplified accessor for the compilation executor.
     * Same as {@link #compilation()} but with a shorter name for cleaner generated code.
     */
    public static ExecutorService compile() {
        return compilation();
    }

    /**
     * Simplified accessor for the execution executor.
     * Same as {@link #execution()} but with a shorter name for cleaner generated code.
     */
    public static ExecutorService execute() {
        return execution();
    }

    /**
     * Returns the trace ID for the current execution.
     *
     * @return The trace ID string.
     * @throws IllegalStateException if no execution context is available.
     */
    public static String traceId() {
        return requireContext().traceId();
    }

    /**
     * Returns the process code for the current execution.
     *
     * @return The process code string.
     * @throws IllegalStateException if no execution context is available.
     */
    public static String processCode() {
        return requireContext().processCode();
    }

    /**
     * Returns the execution duration in milliseconds.
     *
     * @return The duration of the execution.
     * @throws IllegalStateException if no execution context is available.
     */
    public static long duration() {
        return requireContext().duration();
    }

    /**
     * Retrieves the current {@link EngineExecutionContext}, throwing an exception if it is not present.
     *
     * @return The non-null current execution context.
     * @throws IllegalStateException if no context is available in the current thread.
     */
    private static EngineExecutionContext requireContext() {
        EngineExecutionContext context = current();
        if (context == null) {
            throw new IllegalStateException(
                    "No execution context available in current thread. " +
                            "Make sure to set the context using EngineExecutionContextHolder.set() " +
                            "before calling this method."
            );
        }
        return context;
    }
}
