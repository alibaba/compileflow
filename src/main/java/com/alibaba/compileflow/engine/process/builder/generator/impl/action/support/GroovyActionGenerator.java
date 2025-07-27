package com.alibaba.compileflow.engine.process.builder.generator.impl.action.support;

import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.builder.generator.impl.action.AbstractScriptActionGenerator;
import com.alibaba.compileflow.engine.process.builder.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

public class GroovyActionGenerator extends AbstractScriptActionGenerator {

    public GroovyActionGenerator(AbstractProcessRuntime runtime, IAction action) {
        super(runtime, action);
    }

    @Override
    public String getActionType() {
        return "groovy";
    }

    @Override
    protected String getScriptExecutorName() {
        return "groovy";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (actionHandle == null) {
            codeTargetSupport.addBodyLine("//TODO");
        }
        addImportedType(codeTargetSupport, ScriptExecutorProvider.class);
        codeTargetSupport.addBodyLine("Map<String, Object> _ScriptContext = new HashMap<String, Object>();");
        generateScriptExecuteCode(codeTargetSupport);
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        return "executeGroovy" + getExpression().hashCode();
    }
}
