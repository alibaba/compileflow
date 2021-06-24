package com.alibaba.compileflow.engine.process.impl;

import com.alibaba.compileflow.engine.StatefulProcessEngine;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.BpmnModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.BpmnModelConverter;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.runtime.impl.BpmnStatefulProcessRuntime;

import java.util.Map;

/**
 * @author yusu
 */
public class BpmnStatefulProcessEngineImpl extends AbstractProcessEngine<BpmnModel>
    implements StatefulProcessEngine<BpmnModel> {

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> context) {
        BpmnStatefulProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    public Map<String, Object> trigger(String code, String currentTag, Map<String, Object> context) {
        BpmnStatefulProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(currentTag, context);
    }

    @Override
    public Map<String, Object> start(String code, Map<String, Object> context) {
        BpmnStatefulProcessRuntime runtime = getProcessRuntime(code);
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
        return BpmnStatefulProcessRuntime.of(bpmnModel);
    }

}