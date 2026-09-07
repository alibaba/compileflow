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

import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Thread-local holder for the current {@link EngineExecutionContext}.
 *
 * @author yusu
 */
public final class EngineExecutionContextHolder {
    private static final ThreadLocal<EngineExecutionContext> CONTEXT = new ThreadLocal<>();

    private EngineExecutionContextHolder() {
    }

    public static EngineExecutionContext current() {
        return CONTEXT.get();
    }

    public static void set(EngineExecutionContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static ExecutorService action() {
        return requireCurrent().action();
    }

    public static ExecutorService parallel() {
        return requireCurrent().parallel();
    }

    public static Duration actionTimeoutCancellationGracePeriod() {
        return requireCurrent().actionTimeoutCancellationGracePeriod();
    }

    public static Duration parallelCancellationGracePeriod() {
        return requireCurrent().parallelCancellationGracePeriod();
    }

    public static <T> T component(String name, Class<T> requiredType) {
        return requireCurrent().component(name, requiredType);
    }

    public static ScriptExecutor scriptExecutor(String name) {
        return requireCurrent().scriptExecutor(name);
    }

    /**
     * Evaluates one script program owned by the active Process runtime.
     */
    public static Object evaluateScript(ScriptProgramSpec spec, Map<String, Object> context) {
        return requireCurrent().evaluateScript(spec, context);
    }

    /**
     * Opens the runtime-owned script-program catalog for one generated Process invocation.
     */
    public static EngineExecutionContext.ScriptProgramsScope openScriptPrograms(
            Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        return requireCurrent().openScriptPrograms(scriptPrograms);
    }

    public static RetryPolicy retryPolicy(String name) {
        return requireCurrent().retryPolicy(name);
    }

    public static FailureHandler failureHandler(String name) {
        return requireCurrent().failureHandler(name);
    }

    public static String traceId() {
        return requireCurrent().traceId();
    }

    public static String processCode() {
        return requireCurrent().processCode();
    }

    public static long duration() {
        return requireCurrent().duration();
    }

    /**
     * Returns the execution context bound to the current thread.
     *
     * @return active execution context
     * @throws IllegalStateException if no process execution is active
     */
    public static EngineExecutionContext requireCurrent() {
        EngineExecutionContext context = current();
        if (context == null) {
            throw new IllegalStateException(
                    "No execution context available in current thread. "
                    + "Make sure to set the context using EngineExecutionContextHolder.set() "
                    + "before calling this method.");
        }
        return context;
    }
}
