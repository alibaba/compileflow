package com.alibaba.compileflow.engine.runtime;

import java.util.Map;

/**
 * Defines the interface for managing process runtimes in the CompileFlow Engine.
 * This interface provides methods to interact with process instances, allowing
 * the creation, triggering, and manipulation of processes according to the specified context.
 *
 * @author wuxiang
 * @author yusu
 */
public interface ProcessRuntime {

    /**
     * Starts a process instance with the given context.
     *
     * @param context The initial context containing variables and metadata for the process
     * @return A runtime result encapsulating the outcome of the process start operation
     */
    Map<String, Object> start(Map<String, Object> context);

    /**
     * Triggers a process instance with a specific tag and context.
     *
     * @param tag Identifier of the process instance to trigger
     * @param context The context containing variables and metadata for the trigger
     * @return A runtime result encapsulating the outcome of the trigger operation
     */
    Map<String, Object> trigger(String tag, Map<String, Object> context);

    /**
     * Triggers a process instance with a specific tag, event, and context.
     *
     * @param tag Identifier of the process instance to trigger
     * @param event The event to trigger within the process instance
     * @param context The context containing variables and metadata for the trigger
     * @return A runtime result encapsulating the outcome of the trigger operation
     */
    Map<String, Object> trigger(String tag, String event, Map<String, Object> context);

    String generateJavaCode();

    String generateTestJavaCode();

    void compile();

    void recompile();

    void compile(ClassLoader classLoader);

    void recompile(ClassLoader classLoader);

}
