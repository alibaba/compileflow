package com.alibaba.compileflow.engine.runtime.instance;

import java.util.Map;

/**
 * Represents a stateful process instance that can be triggered and transitioned between states.
 *
 * @author yusu
 */
public interface StatefulProcessInstance extends ProcessInstance {

    /**
     * Triggers a state transition for the process instance with a specific tag and context.
     *
     * @param tag     Identifier of the process instance to trigger
     * @param context The context containing variables and metadata for the trigger
     * @return A runtime result encapsulating the outcome of the trigger operation
     * @throws Exception If an error occurs during the process execution
     */
    Map<String, Object> trigger(String tag, Map<String, Object> context) throws Exception;

    /**
     * Triggers a state transition for the process instance with a specific tag, event, and context.
     *
     * @param tag     Identifier of the process instance to trigger
     * @param event   The event to trigger within the process instance
     * @param context The context containing variables and metadata for the trigger
     * @return A runtime result encapsulating the outcome of the trigger operation
     * @throws Exception If an error occurs during the process execution
     */
    Map<String, Object> trigger(String tag, String event, Map<String, Object> context) throws Exception;

}
