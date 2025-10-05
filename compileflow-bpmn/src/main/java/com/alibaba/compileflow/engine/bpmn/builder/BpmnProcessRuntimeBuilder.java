package com.alibaba.compileflow.engine.bpmn.builder;

import com.alibaba.compileflow.engine.bpmn.builder.converter.BpmnModelConverter;
import com.alibaba.compileflow.engine.bpmn.builder.generator.BpmnProcessCodeGenerator;
import com.alibaba.compileflow.engine.bpmn.definition.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.runtime.BpmnProcessRuntime;
import com.alibaba.compileflow.engine.core.builder.AbstractProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.runtime.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;

/**
 * The BPMN 2.0-specific implementation of the {@link AbstractProcessRuntimeBuilder}.
 *
 * <p>This class wires together the concrete components required to build a BPMN
 * process, including the {@link BpmnModelConverter}, {@link BpmnProcessCodeGenerator},
 * and {@link BpmnProcessRuntime}.
 *
 * @author yusu
 */
public class BpmnProcessRuntimeBuilder extends AbstractProcessRuntimeBuilder<BpmnModel> {

    @Override
    protected FlowModelConverter<BpmnModel> getFlowModelConverter() {
        return BpmnModelConverter.getInstance();
    }

    @Override
    protected ProcessCodeGenerator getProcessCodeGenerator(BpmnModel flowModel) {
        return new BpmnProcessCodeGenerator(flowModel);
    }

    @Override
    protected AbstractProcessRuntime<BpmnModel> getProcessRuntime(BpmnModel flowModel, Class<? extends ProcessInstance> processInstanceClass) {
        return new BpmnProcessRuntime(flowModel, processInstanceClass);
    }

}
