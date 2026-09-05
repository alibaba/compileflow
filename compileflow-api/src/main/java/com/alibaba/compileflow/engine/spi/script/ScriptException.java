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
package com.alibaba.compileflow.engine.spi.script;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.Objects;

/**
 * Stable provider-independent script failure classification.
 * <p>
 * Durable execution may persist the {@link Kind} as an engine fact. Provider class
 * names, exception types and messages remain diagnostics and must not influence
 * process semantics.
 *
 * @author yusu
 */
public final class ScriptException extends CompileFlowException {
    private static final long serialVersionUID = 1L;
    private final Kind kind;

    /**
     * Creates a classified script failure.
     *
     * @param kind    stable failure kind
     * @param message safe diagnostic message
     */
    public ScriptException(Kind kind, String message) {
        super(errorCode(kind), message, null);
        this.kind = kind;
    }

    /**
     * Creates a classified script failure with a process-local cause.
     *
     * @param kind    stable failure kind
     * @param message safe diagnostic message
     * @param cause   provider-specific cause
     */
    public ScriptException(Kind kind, String message, Throwable cause) {
        super(errorCode(kind), message, cause);
        this.kind = kind;
    }

    /**
     * Returns the stable provider-independent classification.
     *
     * @return failure kind
     */
    public Kind kind() {
        return kind;
    }

    private static ErrorCode errorCode(Kind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case INVALID_SOURCE -> ErrorCode.CF_COMPILE_001;
            case TIMED_OUT -> ErrorCode.CF_EXEC_004;
            case CANCELLED -> ErrorCode.CF_EXEC_007;
            case COMPILATION_FAILED, EVALUATION_FAILED, LIMIT_EXCEEDED, NON_PORTABLE_RESULT -> ErrorCode.CF_EXEC_003;
        };
    }

    /**
     * Script failure kinds understood by the engine.
     */
    public enum Kind {
        /**
         * Source is invalid for the declared language.
         */
        INVALID_SOURCE,
        /**
         * A valid source could not be compiled into a runtime program.
         */
        COMPILATION_FAILED,
        /**
         * Evaluation failed without a more specific stable classification.
         */
        EVALUATION_FAILED,
        /**
         * Evaluation exceeded its cooperative deadline.
         */
        TIMED_OUT,
        /**
         * A configured resource or result bound was exceeded.
         */
        LIMIT_EXCEEDED,
        /**
         * Evaluation produced a value outside the engine portable-value contract.
         */
        NON_PORTABLE_RESULT,
        /**
         * Evaluation was cancelled or the executing thread was interrupted.
         */
        CANCELLED
    }
}
