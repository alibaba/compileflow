package com.alibaba.compileflow.engine.runtime.impl;


import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.runtime.ProcessCodeGenerator;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmnProcessRuntime extends AbstractProcessRuntime<BpmnModel> {

    private BpmnProcessRuntime(BpmnModel bpmnModel) {
        super(bpmnModel);
    }

    public static BpmnProcessRuntime of(BpmnModel bpmnModel) {
        return new BpmnProcessRuntime(bpmnModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    protected boolean isBpmn20() {
        return false;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected ProcessCodeGenerator getProcessCodeGenerator() {
        return new BpmnProcessCodeGenerator(this);
    }

}
