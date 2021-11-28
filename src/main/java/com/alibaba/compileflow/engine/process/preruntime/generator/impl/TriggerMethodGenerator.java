package com.alibaba.compileflow.engine.process.preruntime.generator.impl;

import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.MethodConstants;

/**
 * @author yusu
 */
public class TriggerMethodGenerator extends AbstractGenerator {

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("return " + MethodConstants.TRIGGER_METHOD_NAME +
            "(tag, SystemEventConstants.CONTINUE_EVENT, _pContext);");
    }

}
