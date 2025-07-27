package com.alibaba.compileflow.engine.process.builder.loader;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.FlowStreamSource;

/**
 * @author yusu
 */
public interface FlowSourceLoader extends Extension {

    String EXT_LOAD_FLOW_SOURCE_CODE = "com.alibaba.compileflow.engine.process.builder.loader.FlowSourceLoader.loadFlowSource";

    @ExtensionPoint(code = EXT_LOAD_FLOW_SOURCE_CODE, reducePolicy = ReducePolicy.FISRT_MATCH)
    FlowStreamSource loadFlowSource(String code, String content, FlowModelType flowModelType);

}
