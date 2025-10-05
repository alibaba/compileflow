package com.alibaba.compileflow.engine.core.builder.converter.loader;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * @author yusu
 */
public interface FlowSourceLoader extends Extension {

    String EXT_LOAD_FLOW_SOURCE_CODE = "com.alibaba.compileflow.engine.core.builder.converter.loader.FlowSourceLoader.loadFlowSource";

    @ExtensionPoint(code = EXT_LOAD_FLOW_SOURCE_CODE)
    FlowStreamSource loadFlowSource(ProcessSource processSource, FlowModelType flowModelType);

}
