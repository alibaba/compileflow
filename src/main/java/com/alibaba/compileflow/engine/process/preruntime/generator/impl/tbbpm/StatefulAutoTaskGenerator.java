package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.AutoTaskNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author wuxiang
 * @author yusu
 */
public class StatefulAutoTaskGenerator extends AbstractTbbpmActionNodeGenerator<AutoTaskNode> {

    public StatefulAutoTaskGenerator(AbstractProcessRuntime runtime,
                                     AutoTaskNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        generateActionMethodCode(codeTargetSupport, flowNode.getAction());
    }

}