package com.alibaba.compileflow.engine.process.preruntime.compiler;

import com.alibaba.compileflow.engine.common.extension.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.consts.ReducePolicy;

/**
 * @author yusu
 */
public interface FlowClassLoader extends ExtensionPoint {

    String CODE = "com.alibaba.compileflow.engine.process.preruntime.compiler.FlowClassLoader";
    String EXT_LOAD_FLOW_CLASS_CODE = CODE + ".loadClass";

    @Extension(code = EXT_LOAD_FLOW_CLASS_CODE, reducePolicy = ReducePolicy.FISRT_MATCH)
    Class<?> loadClass(String fullClassName);

}
