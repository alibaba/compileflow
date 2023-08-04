package com.alibaba.compileflow.engine.process.preruntime.loader;

import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;

/**
 * @author yusu
 */
public interface FlowSourceLoader extends IExtensionPoint {

    String EXT_LOAD_FLOW_SOURCE_CODE = "com.alibaba.compileflow.engine.process.preruntime.loader.FlowSourceLoader.loadFlowSource";

    @ExtensionPoint(code = EXT_LOAD_FLOW_SOURCE_CODE, reducePolicy = ReducePolicy.FISRT_MATCH)
    FlowStreamSource loadFlowSource(String code, String content, FlowModelType flowModelType);

}
