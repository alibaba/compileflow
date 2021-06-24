package com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn;


import com.alibaba.compileflow.engine.definition.bpmn.ExclusiveGateway;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 */
public class StatefulExclusiveGatewayGenerator extends AbstractBpmnActionNodeGenerator<ExclusiveGateway> {

    public StatefulExclusiveGatewayGenerator(AbstractProcessRuntime runtime,
                                             ExclusiveGateway flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
    }

}