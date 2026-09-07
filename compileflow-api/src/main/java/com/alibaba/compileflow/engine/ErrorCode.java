/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
    CF_COMPILE_001("Process compilation failed"),
    /**
     * The embedded Java code within the process definition failed to compile.
     */
    CF_COMPILE_002("Java code compilation failed"),
    // ========== Runtime Readiness Errors ==========
    /**
     * A process runtime did not become ready because loading or warm-up timed out, was interrupted,
     * or was cancelled.
     */
    CF_RUNTIME_001("Process runtime did not become ready"),
    // ========== Execution Errors ==========
    /**
     * A general failure occurred during the execution of a process instance.
     */
    CF_EXEC_001("Process execution failed"),
    /**
     * A configured ScriptExecutor failed to compile or execute a script task.
     */
    CF_EXEC_003("Script execution error"),
    /**
     * The execution of a process instance exceeded the configured timeout.
     */
    CF_EXEC_004("Execution timeout"),
    /**
     * Local bounded execution capacity was exhausted.
     */
    CF_EXEC_005("Local execution capacity exhausted"),
    /**
     * The execution thread was interrupted.
     */
    CF_EXEC_007("Operation interrupted"),
    /**
     * A validation check failed just before or during process execution.
     */
    CF_EXEC_008("Process execution validation failed"),
    /**
     * Typed result conversion failed after the process itself completed successfully.
     */
    CF_EXEC_009("Execution result mapping failed after process completion"),
    /**
     * Typed input conversion failed before the process started.
     */
    CF_EXEC_010("Execution input mapping failed before process start"),
    /**
     * The requested Alias route was unavailable or changed before any process action ran.
     */
    CF_EXEC_011("Process route unavailable before action execution"),
    /**
     * The selected process runtime was unavailable on this node before any process action ran.
     */
    CF_EXEC_012("Process runtime unavailable before action execution"),
    /**
     * A nested or recursive process call exceeded the configured depth before its actions ran.
     */
    CF_EXEC_013("Process call depth exceeded before action execution"),
    /**
     * The resolved process-call graph contains an invalid target, authority mismatch, or cycle.
     */
    CF_EXEC_014("Process call graph is invalid before action execution"),
    // ========== Configuration Errors ==========
    /**
     * A provided configuration parameter is invalid or out of range.
     */
    CF_CONFIG_001("Invalid configuration parameter"),
    /**
     * A required component or service could not be configured or initialized.
     */
    CF_CONFIG_003("Component configuration error"),
    /**
     * The Service Provider for the process engine could not be found or loaded.
     */
    CF_CONFIG_005("Provider configuration error"),
    // ========== Resource Errors ==========
    /**
     * The requested process definition could not be found.
     */
    CF_RESOURCE_001("Process definition not found"),
    /**
     * A failure occurred while loading a resource (e.g., a classpath process definition).
     */
    CF_RESOURCE_002("Resource loading failed"),
    /**
     * A process definition violated a configured source or size policy.
     */
    CF_RESOURCE_003("Process definition resource policy violation"),
    // ========== Validation Errors ==========
    /**
     * A method parameter or input argument failed a validation check.
     */
    CF_VALIDATION_001("Parameter validation failed"),
    /**
     * The process definition content is malformed or does not conform to the schema (e.g., BPMN, TBBPM).
     */
    CF_VALIDATION_002("Invalid process definition format"),
    /**
     * A referenced node, element, or variable could not be found within the process definition.
     */
    CF_VALIDATION_004("Node or element not found"),
    /**
     * The process definition has a structural flaw (e.g., disconnected nodes, invalid loops).
     */
    CF_VALIDATION_005("Process structure validation failed"),
    /**
     * The source construct is valid, but CompileFlow's shared Process semantics do not define it.
     */
    CF_VALIDATION_006("Unsupported Process semantic");
    private final String message;

    ErrorCode(String message) {
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
        if (code == null) {
            throw new IllegalArgumentException("Unknown error code: null");
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown error code: " + code, invalid);
        }
    }

    /**
     * Returns stable machine-readable error code.
     *
     * @return stable machine-readable error code
     */
    public String getCode() {
        return name();
    }

    /**
     * Returns default human-readable error description.
     *
     * @return default human-readable error description
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return name() + ": " + message;
    }
}
