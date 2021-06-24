package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.DecisionNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 */
public class StatefulDecisionGenerator extends AbstractTbbpmActionNodeGenerator<DecisionNode> {

    public StatefulDecisionGenerator(AbstractProcessRuntime runtime,
                                     DecisionNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        super.generateCode(codeTargetSupport);
    }

}
