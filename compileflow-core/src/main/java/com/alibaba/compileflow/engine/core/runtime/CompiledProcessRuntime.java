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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessExecutionException;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executable.ExecutableProcess;
import com.alibaba.compileflow.engine.core.runtime.executable.TriggerableProcess;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ProcessRuntime} realized as one generated and compiled Java class.
 *
 * @author yusu
 */
final class CompiledProcessRuntime implements ProcessRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompiledProcessRuntime.class);
    private final ProcessSemanticPlan semanticPlan;
    private final Class<? extends ExecutableProcess> executableClass;
    private final Map<ScriptProgramSpec, ScriptProgram> scriptPrograms;

    CompiledProcessRuntime(ProcessSemanticPlan semanticPlan, Class<? extends ExecutableProcess> executableClass,
            Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        this.semanticPlan = Objects.requireNonNull(semanticPlan, "semanticPlan");
        this.executableClass = Objects.requireNonNull(executableClass, "executableClass");
        this.scriptPrograms = Map.copyOf(Objects.requireNonNull(scriptPrograms, "scriptPrograms"));
    }

    @Override
    public ProcessSemanticPlan getSemanticPlan() {
        return semanticPlan;
    }

    @Override
    @SuppressWarnings("try")
    public Map<String, Object> execute(Map<String, Object> variables) {
        Objects.requireNonNull(variables, "variables");
        return invoke("execute", () -> {
            try (EngineExecutionContext.ScriptProgramsScope ignored =
                    EngineExecutionContextHolder.openScriptPrograms(scriptPrograms)) {
                return newExecutable().execute(variables);
            }
        });
    }

    @Override
    @SuppressWarnings("try")
    public Map<String, Object> trigger(ProcessTrigger trigger, Map<String, Object> variables) {
        ProcessTrigger requested = Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(variables, "variables");
        if (!TriggerableProcess.class.isAssignableFrom(executableClass)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_004,
                    "Process '" + semanticPlan.getProcessCode() + "' has no trigger entries.");
        }
        Class<? extends TriggerableProcess> triggerableClass = executableClass.asSubclass(TriggerableProcess.class);
        return invoke("trigger", () -> {
            try (EngineExecutionContext.ScriptProgramsScope ignored =
                    EngineExecutionContextHolder.openScriptPrograms(scriptPrograms)) {
                return newExecutable(triggerableClass).trigger(requested.nodeId(), requested.event(), variables);
            }
        });
    }

    private ExecutableProcess newExecutable() {
        return newExecutable(executableClass);
    }

    private <T> T newExecutable(Class<T> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new CompileFlowException(ErrorCode.CF_EXEC_001,
                    "Failed to instantiate generated process class: " + type.getName(), failure);
        }
    }

    private Map<String, Object> invoke(String operation, Invocation invocation) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(executableClass.getClassLoader());
            return invocation.run();
        } catch (ProcessExecutionException | CompileFlowException classified) {
            throw classified;
        } catch (Exception failure) {
            LOGGER.debug("Generated Process invocation failed: operation={}, processCode={}, failureType={}", operation,
                    semanticPlan.getProcessCode(), failure.getClass().getName());
            throw new CompileFlowException(ErrorCode.CF_EXEC_001, "Failed to " + operation + " process", failure)
                .withContext("processCode", semanticPlan.getProcessCode())
                .withContext("actionName", operation)
                .withContext("processClass", executableClass.getName());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @FunctionalInterface
    private interface Invocation {
        Map<String, Object> run() throws Exception;
    }
}
