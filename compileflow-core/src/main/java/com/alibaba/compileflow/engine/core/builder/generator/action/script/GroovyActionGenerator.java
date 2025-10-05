package com.alibaba.compileflow.engine.core.builder.generator.action.script;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.action.AbstractScriptActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.core.definition.action.IAction;

/**
 * @author yusu
 */
public class GroovyActionGenerator extends AbstractScriptActionGenerator {

    public GroovyActionGenerator(GeneratorContext context,
                                 IAction action) {
        super(context, action);
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
        addImportedType(codeTargetSupport, ScriptExecutorProvider.class);
        codeTargetSupport.addBodyLine("Map<String, Object> _ScriptContext = new HashMap<String, Object>();");
        generateScriptExecuteCode(codeTargetSupport);
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        return "executeGroovy" + Integer.toUnsignedLong(getExpression().hashCode());
    }
}
