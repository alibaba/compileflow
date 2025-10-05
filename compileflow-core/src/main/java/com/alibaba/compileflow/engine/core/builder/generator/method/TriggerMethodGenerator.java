package com.alibaba.compileflow.engine.core.builder.generator.method;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.constants.MethodConstants;

/**
 * @author yusu
 */
public class TriggerMethodGenerator extends AbstractCodeGenerator {

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("return " + MethodConstants.TRIGGER_METHOD_NAME +
                "(tag, null, _pContext);");
    }

}
