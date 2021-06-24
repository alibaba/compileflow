package com.alibaba.compileflow.engine.runtime.instance;

import java.util.Map;

/**
 * @author yusu
 */
public interface StatefulProcessInstance extends ProcessInstance {

    Map<String, Object> trigger(Map<String, Object> context, String currentTag) throws Exception;

}