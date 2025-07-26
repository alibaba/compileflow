package com.alibaba.compileflow.engine.extension.mock;

import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;

public interface MockService extends Extension {
    @ExtensionPoint(code = "mockService", reducePolicy = ReducePolicy.FISRT_MATCH)
    String hello();
}
