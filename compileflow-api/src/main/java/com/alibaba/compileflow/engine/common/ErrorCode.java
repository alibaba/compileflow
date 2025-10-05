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
package com.alibaba.compileflow.engine.common;

/**
 * Defines standardized error codes for the CompileFlow framework.
 * Each code includes a unique identifier and a default message.
 *
 * @author yusu
 */
public enum ErrorCode {

    // ========== Compilation Errors ==========

    /**
     * A general failure occurred during the process compilation phase.
     */
    CF_COMPILE_001("CF_COMPILE_001", "Process compilation failed"),
    /**
     * The embedded Java code within the process definition failed to compile.
     */
    CF_COMPILE_002("CF_COMPILE_002", "Java code compilation failed"),
    /**
     * The compiled process class could not be loaded into the JVM.
     */
    CF_COMPILE_003("CF_COMPILE_003", "Class loading failed"),
    /**
     * The compilation process exceeded the configured timeout.
     */
    CF_COMPILE_004("CF_COMPILE_004", "Compilation timeout"),
    /**
     * The system lacks sufficient resources (e.g., threads) to perform compilation.
     */
    CF_COMPILE_005("CF_COMPILE_005", "Insufficient compilation resources"),

    // ========== Execution Errors ==========

    /**
     * A general failure occurred during the execution of a process instance.
     */
    CF_EXEC_001("CF_EXEC_001", "Process execution failed"),
    /**
     * An error occurred while executing parallel branches within a process.
     */
    CF_EXEC_002("CF_EXEC_002", "Parallel branch execution failed"),
    /**
     * A script task (e.g., Groovy, Aviator) failed to execute.
     */
    CF_EXEC_003("CF_EXEC_003", "Script execution error"),
    /**
     * The execution of a process instance exceeded the configured timeout.
     */
    CF_EXEC_004("CF_EXEC_004", "Execution timeout"),
    /**
     * The executor's thread pool rejected the execution task, typically due to saturation.
     */
    CF_EXEC_005("CF_EXEC_005", "Thread pool rejected execution"),
    /**
     * Failed to create a new instance of the process.
     */
    CF_EXEC_006("CF_EXEC_006", "Process instance creation failed"),
    /**
     * The execution thread was interrupted.
     */
    CF_EXEC_007("CF_EXEC_007", "Operation interrupted"),
    /**
     * A validation check failed just before or during process execution.
     */
    CF_EXEC_008("CF_EXEC_008", "Process execution validation failed"),

    // ========== Configuration Errors ==========

    /**
     * A provided configuration parameter is invalid or out of range.
     */
    CF_CONFIG_001("CF_CONFIG_001", "Invalid configuration parameter"),
    /**
     * The executor (thread pool) configuration is invalid.
     */
    CF_CONFIG_002("CF_CONFIG_002", "Executor configuration error"),
    /**
     * A required component or service could not be configured or initialized.
     */
    CF_CONFIG_003("CF_CONFIG_003", "Component configuration error"),
    /**
     * An SPI extension point is misconfigured or its implementation is invalid.
     */
    CF_CONFIG_004("CF_CONFIG_004", "Extension point configuration error"),
    /**
     * The Service Provider for the process engine could not be found or loaded.
     */
    CF_CONFIG_005("CF_CONFIG_005", "Provider configuration error"),

    // ========== Resource Errors ==========

    /**
     * The requested process definition could not be found.
     */
    CF_RESOURCE_001("CF_RESOURCE_001", "Process definition not found"),
    /**
     * A failure occurred while loading a resource (e.g., a process file).
     */
    CF_RESOURCE_002("CF_RESOURCE_002", "Resource loading failed"),
    /**
     * An error occurred while accessing the file system.
     */
    CF_RESOURCE_003("CF_RESOURCE_003", "File system access failed"),
    /**
     * A required network resource is unavailable.
     */
    CF_RESOURCE_004("CF_RESOURCE_004", "Network resource unavailable"),
    /**
     * An operation on a cache (e.g., process cache) failed.
     */
    CF_RESOURCE_005("CF_RESOURCE_005", "Cache operation failed"),

    // ========== Validation Errors ==========

    /**
     * A method parameter or input argument failed a validation check.
     */
    CF_VALIDATION_001("CF_VALIDATION_001", "Parameter validation failed"),
    /**
     * The process definition file is malformed or does not conform to the schema (e.g., BPMN, TBBPM).
     */
    CF_VALIDATION_002("CF_VALIDATION_002", "Invalid process definition format"),
    /**
     * A business rule or constraint validation failed.
     */
    CF_VALIDATION_003("CF_VALIDATION_003", "Business rule validation failed"),
    /**
     * A referenced node, element, or variable could not be found within the process definition.
     */
    CF_VALIDATION_004("CF_VALIDATION_004", "Node or element not found"),
    /**
     * The process definition has a structural flaw (e.g., disconnected nodes, invalid loops).
     */
    CF_VALIDATION_005("CF_VALIDATION_005", "Process structure validation failed"),

    // ========== Reflection Errors ==========

    /**
     * An object could not be instantiated via reflection.
     */
    CF_REFLECTION_001("CF_REFLECTION_001", "Object instantiation failed");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Looks up an {@code ErrorCode} by its string representation.
     *
     * @param code The string code (e.g., "CF_COMPILE_001").
     * @return The corresponding {@code ErrorCode}.
     * @throws IllegalArgumentException if the code is not found.
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException("Unknown error code: " + code);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", code, message);
    }
}
