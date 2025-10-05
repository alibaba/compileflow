package com.alibaba.compileflow.engine.core.runtime.executor;

import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Captures and propagates TCCL, MDC, and EngineExecutionContext to thread-pool tasks.
 * - Restores original values in finally blocks (including EngineExecutionContext).
 * - Allows null snapshots and falls back to capture() when needed.
 * - Guards against SecurityException when setting TCCL.
 * - Copies MDC maps before setting to avoid external mutation.
 *
 * @author yusu
 */
public class ExecutionContextPropagator {

    public static ContextSnapshot capture() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Map<String, String> mdc = null;
        try {
            mdc = MDC.getCopyOfContextMap();
        } catch (Exception ignored) {
        }
        final EngineExecutionContext etc = EngineExecutionContextHolder.current();
        return new ContextSnapshot(cl, mdc, etc);
    }

    static <T> Callable<T> wrap(Callable<T> callable, ContextSnapshot snapshot) {
        final ContextSnapshot snap = (snapshot != null) ? snapshot : capture();
        return () -> {
            final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
            Map<String, String> originalMdc = null;
            try {
                originalMdc = MDC.getCopyOfContextMap();
            } catch (Exception ignored) {
            }
            final EngineExecutionContext originalEngineCtx = EngineExecutionContextHolder.current();

            try {
                // TCCL
                if (snap.classLoader != null) {
                    try {
                        Thread.currentThread().setContextClassLoader(snap.classLoader);
                    } catch (SecurityException ignored) {
                    }
                }
                // MDC
                if (MapUtils.isNotEmpty(snap.mdc)) {
                    MDC.setContextMap(new HashMap<>(snap.mdc));
                } else {
                    MDC.clear();
                }
                // EngineExecutionContext
                if (snap.engineCtx != null) {
                    EngineExecutionContextHolder.set(snap.engineCtx);
                } else {
                    EngineExecutionContextHolder.clear();
                }

                return callable.call();
            } finally {
                // MDC restore
                try {
                    if (originalMdc != null && !originalMdc.isEmpty()) {
                        MDC.setContextMap(originalMdc);
                    } else {
                        MDC.clear();
                    }
                } catch (Exception e) {
                    try {
                        MDC.clear();
                    } catch (Exception ignored) {
                    }
                }
                // EngineExecutionContext restore
                if (originalEngineCtx != null) {
                    EngineExecutionContextHolder.set(originalEngineCtx);
                } else {
                    EngineExecutionContextHolder.clear();
                }
                // TCCL restore
                try {
                    Thread.currentThread().setContextClassLoader(originalCl);
                } catch (SecurityException ignored) {
                }
            }
        };
    }

    public static Runnable wrap(Runnable runnable, ContextSnapshot snapshot) {
        final ContextSnapshot snap = (snapshot != null) ? snapshot : capture();
        return () -> {
            final ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
            Map<String, String> originalMdc = null;
            try {
                originalMdc = MDC.getCopyOfContextMap();
            } catch (Exception ignored) {
            }
            final EngineExecutionContext originalEngineCtx = EngineExecutionContextHolder.current();

            try {
                // TCCL
                if (snap.classLoader != null) {
                    try {
                        Thread.currentThread().setContextClassLoader(snap.classLoader);
                    } catch (SecurityException ignored) {
                    }
                }
                // MDC
                if (snap.mdc != null && !snap.mdc.isEmpty()) {
                    MDC.setContextMap(new HashMap<>(snap.mdc));
                } else {
                    MDC.clear();
                }
                // EngineExecutionContext
                if (snap.engineCtx != null) {
                    EngineExecutionContextHolder.set(snap.engineCtx);
                } else {
                    EngineExecutionContextHolder.clear();
                }

                runnable.run();
            } finally {
                // MDC restore
                try {
                    if (originalMdc != null && !originalMdc.isEmpty()) {
                        MDC.setContextMap(originalMdc);
                    } else {
                        MDC.clear();
                    }
                } catch (Exception e) {
                    try {
                        MDC.clear();
                    } catch (Exception ignored) {
                    }
                }
                // EngineExecutionContext restore
                if (originalEngineCtx != null) {
                    EngineExecutionContextHolder.set(originalEngineCtx);
                } else {
                    EngineExecutionContextHolder.clear();
                }
                // TCCL restore
                try {
                    Thread.currentThread().setContextClassLoader(originalCl);
                } catch (SecurityException ignored) {
                }
            }
        };
    }

    public static final class ContextSnapshot {
        final ClassLoader classLoader;
        final Map<String, String> mdc;
        final EngineExecutionContext engineCtx;

        ContextSnapshot(ClassLoader classLoader, Map<String, String> mdc, EngineExecutionContext engineCtx) {
            this.classLoader = classLoader;
            this.mdc = mdc;
            this.engineCtx = engineCtx;
        }
    }
}
