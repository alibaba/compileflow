package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.EndNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author wuxiang
 * @author yusu
 */
public class StatefulEndGenerator extends AbstractTbbpmNodeGenerator<EndNode> {

    public StatefulEndGenerator(AbstractProcessRuntime runtime,
                                EndNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("running = false;");
    }

}
