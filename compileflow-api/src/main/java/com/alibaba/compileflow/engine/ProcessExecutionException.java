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

import java.io.Serial;
import java.util.Objects;

/**
 * Exception raised by {@link ProcessResult#orElseThrow()} for a failed process result.
 *
 * <p>The exception exposes the typed process error and, while in memory, the controlled
 * execution attribution. Attribution is transient because process references are runtime API
 * values rather than a Java serialization protocol.
 *
 * @author yusu
 */
public final class ProcessExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * Typed process failure preserved across Java serialization.
     */
    private final ProcessError error;
    private final transient ProcessExecution execution;

    ProcessExecutionException(ProcessError error, ProcessExecution execution) {
        super(Objects.requireNonNull(error, "error").getMessage());
        this.error = error;
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    /**
     * Returns the typed process error.
     *
     * @return immutable process error
     */
    public ProcessError getError() {
        return error;
    }

    /**
     * Returns the execution attribution captured by the failed result.
     *
     * @return execution attribution, or {@code null} after Java deserialization
     */
    public ProcessExecution getExecution() {
        return execution;
    }
}
