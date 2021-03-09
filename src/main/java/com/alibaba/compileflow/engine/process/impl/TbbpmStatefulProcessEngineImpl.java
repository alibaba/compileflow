package com.alibaba.compileflow.engine.process.impl;


import com.alibaba.compileflow.engine.StatefulProcessEngine;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.TbbpmModelConverter;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.runtime.impl.TbbpmStatefulProcessRuntime;

import java.util.Map;

/**
 * @author yusu
 */
public class TbbpmStatefulProcessEngineImpl extends AbstractProcessEngine<TbbpmModel>
    implements StatefulProcessEngine<TbbpmModel> {

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> context) {
        TbbpmStatefulProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    public Map<String, Object> trigger(String code, String currentTag, Map<String, Object> context) {
        TbbpmStatefulProcessRuntime runtime = getProcessRuntime(code);
        return runtime.trigger(currentTag, context);
    }

    @Override
    public Map<String, Object> start(String code, Map<String, Object> context) {
        TbbpmStatefulProcessRuntime runtime = getProcessRuntime(code);
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
        return TbbpmStatefulProcessRuntime.of(tbbpmModel);
    }

}