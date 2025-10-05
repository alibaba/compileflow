package com.alibaba.compileflow.engine.core.runtime.executor;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ClassUtils;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.core.runtime.instance.StatefulProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles the direct execution of a compiled process class.
 *
 * <p><b>Responsibilities:</b></p>
 * <ol>
 *   <li><b>Instantiation:</b> Creates a new instance of the compiled process class.</li>
 *   <li><b>Environment Setup:</b> Sets the {@link EngineExecutionContext} and, crucially,
 *       the {@code Thread.currentThread().getContextClassLoader()} to the one that loaded
 *       the compiled class. This ensures that any classes referenced within the process
 *       definition are loaded correctly.</li>
 *   <li><b>Invocation:</b> Calls the appropriate method ({@code execute} or {@code trigger})
 *       on the process instance.</li>
 *   <li><b>Environment Teardown:</b> Restores the original context class loader and clears
 *       the thread-local context in a {@code finally} block to prevent resource leaks.</li>
 * </ol>
 *
 * @author yusu
 */
public class ProcessExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessExecutor.class);

    /**
     * Executes a stateless process.
     *
     * @param processCode  The unique code of the process, used for error reporting.
     * @param processClass The compiled {@code .class} of the process.
     * @param context      The business data and variables for the execution.
     * @return A map containing the output variables from the execution.
     */
    public Map<String, Object> execute(String processCode,
                                       Class<? extends ProcessInstance> processClass,
                                       Map<String, Object> context) {
        return runInstance(processClass, () -> {
            ProcessInstance instance = createProcessInstance(processClass);
            return instance.execute(context);
        }, "execute", processCode);
    }

    /**
     * Triggers a waiting node in a stateful process.
     *
     * @param processCode  The unique code of the process.
     * @param processClass The compiled {@code .class} of the process.
     * @param tag          The unique tag of the waiting node to be triggered.
     * @param context      The event data to be passed to the process.
     * @return A map containing the output variables from the continued execution.
     */
    public Map<String, Object> trigger(String processCode,
                                       Class<? extends StatefulProcessInstance> processClass,
                                       String tag, Map<String, Object> context) {
        return runInstance(processClass, () -> {
            StatefulProcessInstance instance = createProcessInstance(processClass);
            return instance.trigger(tag, context);
        }, "trigger", processCode);
    }

    /**
     * Triggers a specific event on a stateful(waiting) node in a stateful process.
     *
     * @param processCode  The unique code of the process.
     * @param processClass The compiled {@code .class} of the process.
     * @param tag          The unique tag of the stateful(waiting) node to be triggered.
     * @param event        The specific event to trigger.
     * @param context      The event data to be passed to the process.
     * @return A map containing the output variables from the continued execution.
     */
    public Map<String, Object> trigger(String processCode,
                                       Class<? extends StatefulProcessInstance> processClass,
                                       String tag, String event, Map<String, Object> context) {
        return runInstance(processClass, () -> {
            StatefulProcessInstance instance = createProcessInstance(processClass);
            return instance.trigger(tag, event, context);
        }, "trigger", processCode);
    }

    private <T> T createProcessInstance(Class<T> clazz) {
        try {
            return ClassUtils.newInstance(clazz);
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_006,
                    "Failed to create new instance for process class: " + clazz.getName(),
                    e
            );
        }
    }

    /**
     * The core execution wrapper that manages the thread context and class loader.
     */
    private Map<String, Object> runInstance(Class<?> processClass,
                                            ThrowableAction<Map<String, Object>> action,
                                            String actionName, String processCode) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(processClass.getClassLoader());
            return action.run();
        } catch (CompileFlowException e) {
            LOGGER.debug("CompileFlow exception in ProcessExecutor: actionName={}, processCode={}, errorCode={}, message={}",
                    actionName, processCode, e.getErrorCode().getCode(), e.getMessage());

            throw e;
        } catch (Exception e) {
            LOGGER.debug("Exception caught in ProcessExecutor: actionName={}, processCode={}, exceptionType={}, message={}",
                    actionName, processCode, e.getClass().getSimpleName(), e.getMessage());

            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_001,
                    String.format("Failed to %s process", actionName),
                    e
            ).withContext("processCode", processCode)
                    .withContext("actionName", actionName)
                    .withContext("processClass", processClass.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * A functional interface representing an action that can throw a checked exception.
     * This allows lambda expressions to be used for actions that might fail.
     */
    @FunctionalInterface
    private interface ThrowableAction<R> {
        R run() throws Exception;
    }

}
