package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.StatefulTask;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractStatefulNodeCodeGenerator;

/**
 * @author yusu
 */
public abstract class AbstractBpmnStatefulNodeGenerator<N extends StatefulTask>
        extends AbstractStatefulNodeCodeGenerator<N> {

    public AbstractBpmnStatefulNodeGenerator(GeneratorContext context, N flowNode) {
        super(context, flowNode);
    }

}
