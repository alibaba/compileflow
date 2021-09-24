package com.alibaba.compileflow.engine.runtime.instance;

import java.util.Map;

/**
 * @author yusu
 */
public interface StatefulProcessInstance extends ProcessInstance {

    Map<String, Object> trigger(String tag, Map<String, Object> context) throws Exception;

    Map<String, Object> trigger(String tag, String event, Map<String, Object> context) throws Exception;

}
