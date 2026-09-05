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
package com.alibaba.compileflow.engine.core.runtime.execution;

import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.MDC;

/**
 * Propagates engine execution context across async boundaries.
 *
 * @author yusu
 */
public final class ExecutionContextPropagator {
    private ExecutionContextPropagator() {
    }

    public static ContextSnapshot capture() {
        EngineExecutionContext context = EngineExecutionContextHolder.current();
        return capture(context != null && context.isMdcPropagationEnabled());
    }

    public static ContextSnapshot capture(boolean includeMdc) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Map<String, String> mdcContext = includeMdc ? captureMdc() : null;
        EngineExecutionContext engineContext = EngineExecutionContextHolder.current();
        ActionExecutionContext actionContext = ActionExecutionContext.currentOptional().orElse(null);
        ProcessContextPropagator.Snapshot applicationContext = engineContext == null
                ? ProcessContextPropagator.none().capture()
                : engineContext.contextPropagator().capture();
        return new ContextSnapshot(contextClassLoader, mdcContext, engineContext, actionContext,
                Objects.requireNonNull(applicationContext, "ProcessContextPropagator returned null snapshot"));
    }

    static <T> Callable<T> wrap(Callable<T> callable, ContextSnapshot snapshot) {
        ContextSnapshot capturedContext = snapshot != null ? snapshot : capture();
        return () -> {
            BoundContext context = BoundContext.open(capturedContext);
            try {
                return callable.call();
            } finally {
                context.close();
            }
        };
    }

    public static Runnable wrap(Runnable runnable, ContextSnapshot snapshot) {
        ContextSnapshot capturedContext = snapshot != null ? snapshot : capture();
        return () -> {
            BoundContext context = BoundContext.open(capturedContext);
            try {
                runnable.run();
            } finally {
                context.close();
            }
        };
    }

    private static ActionExecutionContext.Scope bindActionContext(ActionExecutionContext context) {
        return context == null ? ActionExecutionContext.suspend() : ActionExecutionContext.open(context);
    }

    private static Map<String, String> captureMdc() {
        try {
            return MDC.getCopyOfContextMap();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void replaceMdc(Map<String, String> context) {
        try {
            if (MapUtils.isNotEmpty(context)) {
                MDC.setContextMap(new HashMap<>(context));
            } else {
                MDC.clear();
            }
        } catch (RuntimeException ignored) {
            // Logging context must not change workflow behavior.
        }
    }

    private static void replaceEngineContext(EngineExecutionContext context) {
        if (context == null) {
            EngineExecutionContextHolder.clear();
        } else {
            EngineExecutionContextHolder.set(context);
        }
    }

    private static void replaceContextClassLoader(ClassLoader classLoader) {
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
        } catch (SecurityException ignored) {
            // A restrictive security manager may reject optional TCCL propagation.
        }
    }

    private static final class BoundContext implements AutoCloseable {
        private final ClassLoader originalClassLoader;
        private final Map<String, String> originalMdc;
        private final EngineExecutionContext originalEngineContext;
        private final ActionExecutionContext.Scope actionScope;
        private ProcessContextPropagator.Scope applicationScope = () -> {};

        private BoundContext(ClassLoader originalClassLoader, Map<String, String> originalMdc,
                EngineExecutionContext originalEngineContext, ActionExecutionContext.Scope actionScope) {
            this.originalClassLoader = originalClassLoader;
            this.originalMdc = originalMdc;
            this.originalEngineContext = originalEngineContext;
            this.actionScope = actionScope;
        }

        private static BoundContext open(ContextSnapshot snapshot) {
            BoundContext context = new BoundContext(Thread.currentThread().getContextClassLoader(), captureMdc(),
                    EngineExecutionContextHolder.current(), bindActionContext(snapshot.actionContext));
            try {
                if (snapshot.classLoader != null) {
                    replaceContextClassLoader(snapshot.classLoader);
                }
                replaceMdc(snapshot.mdcContext);
                replaceEngineContext(snapshot.engineContext);
                context.applicationScope = Objects.requireNonNull(snapshot.applicationContext.open(),
                        "ProcessContextPropagator snapshot returned null scope");
                return context;
            } catch (RuntimeException | Error failure) {
                try {
                    context.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public void close() {
            ActionExecutionContext.Scope engineScope = actionScope;
            ProcessContextPropagator.Scope ambientScope = applicationScope;
            try (engineScope;
                    ambientScope) {
                // Resource declarations restore application context before engine context.
            } finally {
                replaceMdc(originalMdc);
                replaceEngineContext(originalEngineContext);
                replaceContextClassLoader(originalClassLoader);
            }
        }
    }

    public static final class ContextSnapshot {
        final ClassLoader classLoader;
        final Map<String, String> mdcContext;
        final EngineExecutionContext engineContext;
        final ActionExecutionContext actionContext;
        final ProcessContextPropagator.Snapshot applicationContext;

        ContextSnapshot(ClassLoader classLoader, Map<String, String> mdcContext, EngineExecutionContext engineContext,
                ActionExecutionContext actionContext, ProcessContextPropagator.Snapshot applicationContext) {
            this.classLoader = classLoader;
            this.mdcContext = mdcContext;
            this.engineContext = engineContext;
            this.actionContext = actionContext;
            this.applicationContext = applicationContext;
        }
    }
}
