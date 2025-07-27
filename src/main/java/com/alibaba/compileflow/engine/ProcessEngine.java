/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine;

import java.util.Map;

/**
 * Provides methods for executing, triggering, and managing process flows.
 *
 * @param <T> The type extending {@link FlowModel} representing a flow model
 * @author wuxiang
 * @author yusu
 */
public interface ProcessEngine<T extends FlowModel> {

    /**
     * Executes a process flow with the given code and context.
     *
     * @param code    Unique identifier of the process flow
     * @param context Key-value pairs representing the execution context
     * @return Result of the execution as a map of key-value pairs
     */
    Map<String, Object> execute(String code, Map<String, Object> context);

    /**
     * Triggers a process flow with a specific tag and context.
     *
     * @param code    Unique identifier of the process flow
     * @param tag     Identifier of the flow's tag
     * @param context Key-value pairs representing the execution context
     * @return Result of the trigger as a map of key-value pairs
     */
    Map<String, Object> trigger(String code, String tag, Map<String, Object> context);

    /**
     * Triggers a process flow with a specific tag, event, and context.
     *
     * @param code    Unique identifier of the process flow
     * @param tag     Identifier of the flow's tag
     * @param event   Event to trigger
     * @param context Key-value pairs representing the execution context
     * @return Result of the trigger as a map of key-value pairs
     */
    Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context);

    /**
     * Pre-compiles a set of process flows by their codes.
     *
     * @param codes Array of unique identifiers for process flows to compile
     */
    void preCompile(String... codes);

    /**
     * Pre-compiles a set of process flows with a custom class loader.
     *
     * @param classLoader Custom class loader to use during compilation
     * @param codes       Array of unique identifiers for process flows to compile
     */
    void preCompile(ClassLoader classLoader, String... codes);

    /**
     * Re-compiles a set of process flows by their codes.
     *
     * @param codes Array of unique identifiers for process flows to re-compile
     */
    void reCompile(String... codes);

    /**
     * Re-compiles a set of process flows with a custom class loader.
     *
     * @param classLoader Custom class loader to use during compilation
     * @param codes       Array of unique identifiers for process flows to re-compile
     */
    void reCompile(ClassLoader classLoader, String... codes);

    /**
     * Loads a process flow by its code.
     *
     * @param code Unique identifier of the process flow to load
     * @return A flow model instance representing the loaded process flow
     */
    T load(String code);

    /**
     * Retrieves Java code representation of a process flow.
     *
     * @param code Unique identifier of the process flow
     * @return Java code as a string
     */
    String getJavaCode(String code);

    /**
     * Retrieves test code for a process flow.
     *
     * @param code Unique identifier of the process flow
     * @return Test code as a string
     */
    String getTestJavaCode(String code);

    // --- Methods with content input ---

    /**
     * Executes a process flow with the given code, context, and BPM content.
     *
     * @param code    Unique identifier of the process flow
     * @param context Key-value pairs representing the execution context
     * @param content BPM content in string format
     * @return Result of the execution as a map of key-value pairs
     */
    Map<String, Object> execute(String code, Map<String, Object> context, String content);

    /**
     * Triggers a process flow with a specific tag, context, and BPM content.
     *
     * @param code    Unique identifier of the process flow
     * @param tag     Identifier of the flow's tag
     * @param context Key-value pairs representing the execution context
     * @param content BPM content in string format
     * @return Result of the trigger as a map of key-value pairs
     */
    Map<String, Object> trigger(String code, String tag, Map<String, Object> context, String content);

    /**
     * Triggers a process flow with a specific tag, event, context, and BPM content.
     *
     * @param code    Unique identifier of the process flow
     * @param tag     Identifier of the flow's tag
     * @param event   Event to trigger
     * @param context Key-value pairs representing the execution context
     * @param content BPM content in string format
     * @return Result of the trigger as a map of key-value pairs
     */
    Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context, String content);

    /**
     * Pre-compiles a set of process flows with their corresponding BPM content.
     *
     * @param code2ContentMap Mapping between unique identifiers and BPM content strings
     */
    void preCompile(Map<String, String> code2ContentMap);

    /**
     * Pre-compiles a set of process flows with their corresponding BPM content and a custom class loader.
     *
     * @param classLoader     Custom class loader to use during compilation
     * @param code2ContentMap Mapping between unique identifiers and BPM content strings
     */
    void preCompile(ClassLoader classLoader, Map<String, String> code2ContentMap);

    /**
     * Loads a process flow by its code and BPM content.
     *
     * @param code    Unique identifier of the process flow to load
     * @param content BPM content in string format
     * @return A flow model instance representing the loaded process flow with given content
     */
    T load(String code, String content);

    /**
     * Retrieves Java code representation of a process flow with its BPM content.
     *
     * @param code    Unique identifier of the process flow
     * @param content BPM content in string format
     * @return Java code as a string
     */
    String getJavaCode(String code, String content);

    /**
     * Retrieves test code for a process flow with its BPM content.
     *
     * @param code    Unique identifier of the process flow
     * @param content BPM content in string format
     * @return Test code as a string
     */
    String getTestJavaCode(String code, String content);

}
