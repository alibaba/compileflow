package com.alibaba.compileflow.engine.process.builder.compiler;

import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;

/**
 * @author yusu
 */
public interface FlowClassLoaderFactory extends Extension {

    String EXT_FLOW_CLASS_LOADER_CODE = "com.alibaba.compileflow.engine.process.builder.compiler.FlowClassLoaderFactory.getFlowClassLoader";

    @ExtensionPoint(code = EXT_FLOW_CLASS_LOADER_CODE, reducePolicy = ReducePolicy.FISRT_MATCH)
    ClassLoader getFlowClassLoader();

}
