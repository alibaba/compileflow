package com.alibaba.compileflow.engine.core.builder.generator.script.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.generator.script.ScriptExecutor;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.util.Map;

/**
 * @author yusu
 */
public class GroovyExecutor implements ScriptExecutor<Map<String, Object>> {

    @Override
    public Object execute(String expression, Map<String, Object> context) {
        try {
            Binding binding = new Binding();
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    binding.setVariable(entry.getKey(), entry.getValue());
                }
            }
            GroovyShell shell = new GroovyShell(binding);
            return shell.evaluate(expression);
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_EXEC_003,
                    "Groovy expression execution failed.",
                    e
            );
        }
    }

    @Override
    public String getName() {
        return "groovy";
    }
}
