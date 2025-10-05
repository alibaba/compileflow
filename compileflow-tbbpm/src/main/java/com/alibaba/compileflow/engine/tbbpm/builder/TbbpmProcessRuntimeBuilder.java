package com.alibaba.compileflow.engine.tbbpm.builder;

import com.alibaba.compileflow.engine.core.builder.AbstractProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.runtime.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.TbbpmModelConverter;
import com.alibaba.compileflow.engine.tbbpm.builder.generator.TbbpmProcessCodeGenerator;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.runtime.TbbpmProcessRuntime;

/**
 * The TBBPM-specific implementation of the {@link AbstractProcessRuntimeBuilder}.
 *
 * <p>This class wires together the concrete components required to build a TBBPM
 * process, including the {@link TbbpmModelConverter}, {@link TbbpmProcessCodeGenerator},
 * and {@link TbbpmProcessRuntime}.
 *
 * @author yusu
 */
public class TbbpmProcessRuntimeBuilder extends AbstractProcessRuntimeBuilder<TbbpmModel> {
    @Override
    protected FlowModelConverter<TbbpmModel> getFlowModelConverter() {
        return TbbpmModelConverter.getInstance();
    }

    @Override
    protected ProcessCodeGenerator getProcessCodeGenerator(TbbpmModel flowModel) {
        return new TbbpmProcessCodeGenerator(flowModel);
    }

    @Override
    protected AbstractProcessRuntime<TbbpmModel> getProcessRuntime(TbbpmModel flowModel,
                                                                   Class<? extends ProcessInstance> processInstanceClass) {
        return new TbbpmProcessRuntime(flowModel, processInstanceClass);
    }

}
