package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventTaskNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventTaskGenerator extends AbstractTbbpmInOutActionNodeGenerator<WaitEventTaskNode> {

    public WaitEventTaskGenerator(AbstractProcessRuntime runtime,
                                  WaitEventTaskNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateCode(flowNode.getEventName(), codeTargetSupport);
    }

}
