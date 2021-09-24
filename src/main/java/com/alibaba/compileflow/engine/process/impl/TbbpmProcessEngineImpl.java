package com.alibaba.compileflow.engine.process.impl;


import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.TbbpmModelConverter;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.runtime.impl.TbbpmProcessRuntime;

import java.util.Map;

/**
 * @author yusu
 */
public class TbbpmProcessEngineImpl extends AbstractProcessEngine<TbbpmModel>
    implements ProcessEngine<TbbpmModel> {

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> context) {
        TbbpmProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    public Map<String, Object> trigger(String code, String tag, Map<String, Object> context) {
        TbbpmProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(tag, context);
    }

    @Override
    public Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context) {
        TbbpmProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(tag, event, context);
    }

    @Override
    public Map<String, Object> start(String code, Map<String, Object> context) {
        TbbpmProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    protected FlowModelType getFlowModelType() {
        return FlowModelType.TBBPM;
    }

    @Override
    protected FlowModelConverter getFlowModelConverter() {
        return TbbpmModelConverter.getInstance();
    }

    @Override
    protected AbstractProcessRuntime getRuntimeFromModel(TbbpmModel tbbpmModel) {
        return TbbpmProcessRuntime.of(tbbpmModel);
    }

}
