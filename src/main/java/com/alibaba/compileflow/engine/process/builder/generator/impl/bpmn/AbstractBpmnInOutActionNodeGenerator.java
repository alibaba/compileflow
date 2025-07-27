package com.alibaba.compileflow.engine.process.builder.generator.impl.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.FlowNode;
import com.alibaba.compileflow.engine.process.builder.generator.impl.AbstractInOutActionNodeGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

public abstract class AbstractBpmnInOutActionNodeGenerator<N extends FlowNode>
        extends AbstractInOutActionNodeGenerator<N> {

    public AbstractBpmnInOutActionNodeGenerator(AbstractProcessRuntime runtime, N flowNode) {
        super(runtime, flowNode);
    }

}
