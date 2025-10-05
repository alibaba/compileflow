package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.tbbpm.definition.WaitEventTaskNode;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventTaskGenerator extends AbstractTbbpmStatefulNodeGenerator<WaitEventTaskNode> {

    public WaitEventTaskGenerator(GeneratorContext context,
                                  WaitEventTaskNode flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateCode(flowNode.getEvent(), codeTargetSupport);
    }

}
