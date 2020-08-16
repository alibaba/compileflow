package com.alibaba.compileflow.engine.runtime;

import java.util.Map;

/**
 * @author wuxiang
 * @author yusu
 */
public interface ProcessRuntime {

    Map<String, Object> start(Map<String, Object> context);

}
