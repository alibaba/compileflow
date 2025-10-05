package com.alibaba.compileflow.engine.tbbpm;

import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.core.builder.AbstractProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.tbbpm.builder.TbbpmProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.TbbpmModelConverter;
import com.alibaba.compileflow.engine.tbbpm.builder.generator.TbbpmProcessCodeGenerator;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;

/**
 * The TBBPM-specific implementation of the process engine.
 *
 * <p>This class acts as a composition root, providing the TBBPM-specific
 * implementations of the core builder components (e.g., runtime builder, model
 * converter, and code generator) to the abstract base engine.
 *
 * @author yusu
 */
public class TbbpmProcessEngineImpl extends AbstractProcessEngine<TbbpmModel> {

    public TbbpmProcessEngineImpl(ProcessEngineConfig config) {
        super(config);
    }

    @Override
    protected AbstractProcessRuntimeBuilder getProcessRuntimeBuilder() {
        return new TbbpmProcessRuntimeBuilder();
    }

    @Override
    public FlowModelConverter<TbbpmModel> getFlowModelConverter() {
        return TbbpmModelConverter.getInstance();
    }

    @Override
    public ProcessCodeGenerator getProcessCodeGenerator(TbbpmModel flowModel) {
        return new TbbpmProcessCodeGenerator(flowModel);
    }

}
