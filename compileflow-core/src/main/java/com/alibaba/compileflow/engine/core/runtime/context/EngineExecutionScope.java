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
package com.alibaba.compileflow.engine.core.runtime.context;

import com.alibaba.compileflow.engine.core.observability.LogContext;
import java.util.Map;
import java.util.Objects;

/**
 * Restorable, thread-confined binding for one engine execution context.
 *
 * @author yusu
 */
public final class EngineExecutionScope implements AutoCloseable {
    private static final ThreadLocal<EngineExecutionScope> CURRENT = new ThreadLocal<>();
    private final Thread owner;
    private final EngineExecutionScope previousScope;
    private final EngineExecutionContext context;
    private final EngineExecutionContext previousContext;
    private final Map<String, String> previousLogContext;
    private boolean closed;

    private EngineExecutionScope(EngineExecutionContext context) {
        this.owner = Thread.currentThread();
        this.previousScope = CURRENT.get();
        this.context = Objects.requireNonNull(context, "context");
        this.previousContext = EngineExecutionContextHolder.current();
        this.previousLogContext = LogContext.getContext();

        EngineExecutionContextHolder.set(context);
        try {
            LogContext.setTraceId(context.traceId());
            LogContext.setProcessCode(context.processCode());
            CURRENT.set(this);
        } catch (RuntimeException | Error failure) {
            restoreExecutionContext(previousContext);
            try {
                LogContext.setContext(previousLogContext);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Binds an execution context to the current thread until the returned scope is closed.
     *
     * @param context execution context to bind
     * @return restorable execution scope
     */
    public static EngineExecutionScope open(EngineExecutionContext context) {
        return new EngineExecutionScope(context);
    }

    /**
     * Returns the context bound by this scope.
     *
     * @return bound execution context
     */
    public EngineExecutionContext context() {
        return context;
    }

    /**
     * Restores the context and MDC state that existed before this scope was opened.
     */
    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Engine execution scope must close on its owner thread");
        }
        if (closed) {
            return;
        }
        if (CURRENT.get() != this || EngineExecutionContextHolder.current() != context) {
            throw new IllegalStateException("Engine execution scopes must close in LIFO order");
        }
        closed = true;

        try {
            LogContext.setContext(previousLogContext);
        } finally {
            restoreExecutionContext(previousContext);
            if (previousScope == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previousScope);
            }
        }
    }

    private static void restoreExecutionContext(EngineExecutionContext previous) {
        if (previous == null) {
            EngineExecutionContextHolder.clear();
        } else {
            EngineExecutionContextHolder.set(previous);
        }
    }
}
