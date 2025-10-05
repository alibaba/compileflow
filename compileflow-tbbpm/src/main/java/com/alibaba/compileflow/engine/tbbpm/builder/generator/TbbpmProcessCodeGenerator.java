package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.core.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.definition.FlowModel;

/**
 * TBBPM-specific process code generator implementation.
 * This class is part of the TBBPM module and should not be referenced from core.
 *
 * @author yusu
 */
public class TbbpmProcessCodeGenerator extends AbstractProcessCodeGenerator {

    public TbbpmProcessCodeGenerator(FlowModel flowModel) {
        super(flowModel);
    }

    @Override
    protected void addImportedType(ClassTarget testClassTarget) {
    }

    @Override
    protected NodeGeneratorProvider createNodeGeneratorProvider() {
        return new TbbpmNodeGeneratorProvider();
    }

}
