package com.alibaba.compileflow.engine.process.impl;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.BpmnModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.BpmnModelConverter;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.runtime.impl.BpmnProcessRuntime;

import java.util.Map;

/**
 * @author yusu
 */
public class BpmnProcessEngineImpl extends AbstractProcessEngine<BpmnModel>
    implements ProcessEngine<BpmnModel> {

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> context) {
        BpmnProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    public Map<String, Object> trigger(String code, String tag, Map<String, Object> context) {
        BpmnProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(tag, context);
    }

    @Override
    public Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context) {
        BpmnProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(tag, event, context);
    }

    @Override
    public Map<String, Object> start(String code, Map<String, Object> context) {
        BpmnProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    protected FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    protected FlowModelConverter getFlowModelConverter() {
        return BpmnModelConverter.getInstance();
    }

    @Override
    protected AbstractProcessRuntime getRuntimeFromModel(BpmnModel bpmnModel) {
        return BpmnProcessRuntime.of(bpmnModel);
    }

}
