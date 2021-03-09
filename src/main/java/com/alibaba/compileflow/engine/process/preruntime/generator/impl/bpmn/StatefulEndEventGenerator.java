package com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.EndEvent;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author wuxiang
 * @author yusu
 */
public class StatefulEndEventGenerator extends AbstractBpmnNodeGenerator<EndEvent> {

    public StatefulEndEventGenerator(AbstractProcessRuntime runtime, EndEvent flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("running = false;");
    }

}
