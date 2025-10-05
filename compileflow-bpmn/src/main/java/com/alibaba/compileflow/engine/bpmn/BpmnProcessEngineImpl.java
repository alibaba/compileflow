package com.alibaba.compileflow.engine.bpmn;

import com.alibaba.compileflow.engine.bpmn.builder.BpmnProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.bpmn.builder.converter.BpmnModelConverter;
import com.alibaba.compileflow.engine.bpmn.builder.generator.BpmnProcessCodeGenerator;
import com.alibaba.compileflow.engine.bpmn.definition.BpmnModel;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.core.builder.AbstractProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;

/**
 * The BPMN 2.0-specific implementation of the process engine.
 *
 * <p>This class acts as a composition root, providing the BPMN-specific
 * implementations of the core builder components (e.g., runtime builder, model
 * converter, and code generator) to the abstract base engine.
 *
 * @author yusu
 */
public class BpmnProcessEngineImpl extends AbstractProcessEngine<BpmnModel> {

    public BpmnProcessEngineImpl(ProcessEngineConfig config) {
        super(config);
    }

    @Override
    protected AbstractProcessRuntimeBuilder getProcessRuntimeBuilder() {
        return new BpmnProcessRuntimeBuilder();
    }

    @Override
    public FlowModelConverter<BpmnModel> getFlowModelConverter() {
        return BpmnModelConverter.getInstance();
    }

    @Override
    public ProcessCodeGenerator getProcessCodeGenerator(BpmnModel flowModel) {
        return new BpmnProcessCodeGenerator(flowModel);
    }

}
