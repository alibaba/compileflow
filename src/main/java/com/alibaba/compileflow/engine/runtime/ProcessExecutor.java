package com.alibaba.compileflow.engine.runtime;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.util.ClassUtils;
import com.alibaba.compileflow.engine.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.runtime.instance.StatefulProcessInstance;

import java.util.Map;

/**
 * 流程执行器 (Process Executor)
 * 职责：
 * 1. 根据编译好的 Class 创建流程实例。
 * 2. 调用实例的 execute 或 trigger 方法。
 */
public class ProcessExecutor {

    public Map<String, Object> execute(String processCode, Class<? extends ProcessInstance> processClass, Map<String, Object> context) {
        return safeRun(() -> {
            ProcessInstance instance = getProcessInstance(processClass);
            return instance.execute(context);
        }, "execute", processCode);
    }

    public Map<String, Object> trigger(String processCode, Class<? extends StatefulProcessInstance> processClass,
                                       String tag, Map<String, Object> context) {
        return safeRun(() -> {
            StatefulProcessInstance instance = getProcessInstance(processClass);
            return instance.trigger(tag, context);
        }, "trigger", processCode);
    }


    public Map<String, Object> trigger(String processCode, Class<? extends StatefulProcessInstance> processClass,
                                       String tag, String event, Map<String, Object> context) {
        return safeRun(() -> {
            StatefulProcessInstance instance = getProcessInstance(processClass);
            return instance.trigger(tag, event, context);
        }, "trigger", processCode);
    }

    private <T> T getProcessInstance(Class<T> clazz) {
        try {
            return ClassUtils.newInstance(clazz);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to create new instance for process class: " + clazz.getName(), e);
        }
    }

    private Map<String, Object> safeRun(ThrowableAction<Map<String, Object>> action, String actionName, String processCode) {
        try {
            return action.run();
        } catch (CompileFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileFlowException("Failed to " + actionName + " process, code is " + processCode, e);
        }
    }

    @FunctionalInterface
    private interface ThrowableAction<R> {
        R run() throws Exception;
    }

}
