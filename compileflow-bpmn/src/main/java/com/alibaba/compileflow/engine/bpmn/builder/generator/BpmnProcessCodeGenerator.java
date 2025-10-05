package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.core.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.definition.FlowModel;

/**
 * BPMN-specific process code generator implementation.
 * This class is part of the BPMN module and should not be referenced from core.
 *
 * @author yusu
 */
public class BpmnProcessCodeGenerator extends AbstractProcessCodeGenerator {

    public BpmnProcessCodeGenerator(FlowModel flowModel) {
        super(flowModel);
    }

    @Override
    protected void addImportedType(ClassTarget classTarget) {
    }

    @Override
    protected NodeGeneratorProvider createNodeGeneratorProvider() {
        return new BpmnNodeGeneratorProvider();
    }

}
