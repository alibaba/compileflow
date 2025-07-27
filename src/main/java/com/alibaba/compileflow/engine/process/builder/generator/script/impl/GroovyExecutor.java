package com.alibaba.compileflow.engine.process.builder.generator.script.impl;

import com.alibaba.compileflow.engine.process.builder.generator.script.ScriptExecutor;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.util.Map;

public class GroovyExecutor implements ScriptExecutor<Map<String, Object>> {

    @Override
    public Object execute(String expression, Map<String, Object> context) {
        Binding binding = new Binding();
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
        GroovyShell shell = new GroovyShell(binding);
        return shell.evaluate(expression);
    }

    @Override
    public String getName() {
        return "groovy";
    }
}
