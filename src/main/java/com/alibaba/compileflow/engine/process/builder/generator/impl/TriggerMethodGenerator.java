package com.alibaba.compileflow.engine.process.builder.generator.impl;

import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.builder.generator.constansts.MethodConstants;

/**
 * @author yusu
 */
public class TriggerMethodGenerator extends AbstractGenerator {

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("return " + MethodConstants.TRIGGER_METHOD_NAME +
                "(tag, null, _pContext);");
    }

}
