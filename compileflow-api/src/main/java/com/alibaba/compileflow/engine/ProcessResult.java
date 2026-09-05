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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Immutable outcome of one process execution or trigger operation.
 *
 * <p>A successful result contains output and no error. A failed result contains a typed
 * {@link ProcessError} and no output. Both outcomes carry controlled execution attribution.
 * Success is independent of the output value: a {@code null} output is still successful and does
 * not activate fallback methods.
 *
 * @param <T> successful output type
 * @author yusu
 */
public final class ProcessResult<T> {
    private final T output;
    private final ProcessError error;
    private final ProcessExecution execution;

    private ProcessResult(T output, ProcessError error, ProcessExecution execution) {
        this.output = output;
        this.error = error;
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    /**
     * Creates a successful result.
     *
     * @param output    successful output, which may be {@code null}
     * @param execution controlled execution attribution
     * @param <T>       output type
     * @return successful process result
     */
    public static <T> ProcessResult<T> success(T output, ProcessExecution execution) {
        return new ProcessResult<>(output, null, execution);
    }

    /**
     * Creates a failed result.
     *
     * @param error     typed process error
     * @param execution controlled execution attribution
     * @param <T>       output type that would have been returned on success
     * @return failed process result
     */
    public static <T> ProcessResult<T> failure(ProcessError error, ProcessExecution execution) {
        return new ProcessResult<>(null, Objects.requireNonNull(error, "error"), execution);
    }

    /**
     * Returns whether execution completed successfully.
     *
     * @return {@code true} when no process error is present
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Returns whether execution failed.
     *
     * @return {@code true} when a process error is present
     */
    public boolean isFailure() {
        return error != null;
    }

    /**
     * Returns successful output.
     *
     * @return output on success, or {@code null} on failure
     */
    public T getOutput() {
        return output;
    }

    /**
     * Returns the typed process error.
     *
     * @return process error on failure, or {@code null} on success
     */
    public ProcessError getError() {
        return error;
    }

    /**
     * Returns controlled attribution for the invocation.
     *
     * @return immutable execution attribution
     */
    public ProcessExecution getExecution() {
        return execution;
    }

    /**
     * Transforms successful output while preserving failure and execution attribution.
     *
     * <p>The mapper is invoked for successful results even when output is {@code null}. Mapper
     * failures propagate because this callback runs after process execution has completed.
     *
     * @param mapper transformation applied to successful output
     * @param <U>    transformed output type
     * @return mapped success or the same typed failure
     */
    public <U> ProcessResult<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        if (isSuccess()) {
            return success(mapper.apply(output), execution);
        }
        return failure(error, execution);
    }

    /**
     * Returns successful output or the supplied fallback value.
     *
     * @param defaultValue fallback used for a failed result
     * @return successful output or the fallback value
     */
    public T orElse(T defaultValue) {
        return isSuccess() ? output : defaultValue;
    }

    /**
     * Returns successful output or lazily obtains a fallback value.
     *
     * @param supplier fallback supplier invoked only for a failed result
     * @return successful output or the supplied fallback value
     */
    public T orElseGet(Supplier<? extends T> supplier) {
        return isSuccess() ? output : Objects.requireNonNull(supplier, "supplier").get();
    }

    /**
     * Returns successful output or throws the typed execution failure.
     *
     * @return successful output
     * @throws ProcessExecutionException when this result is failed
     */
    public T orElseThrow() {
        if (isSuccess()) {
            return output;
        }
        throw new ProcessExecutionException(error, execution);
    }

    /**
     * Returns successful output or throws a caller-provided exception.
     *
     * @param exceptionSupplier supplier invoked only for a failed result
     * @param <X>               exception type
     * @return successful output
     * @throws X when this result is failed
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (isSuccess()) {
            return output;
        }
        throw Objects.requireNonNull(exceptionSupplier, "exceptionSupplier").get();
    }

    @Override
    public String toString() {
        return "ProcessResult{success=" + isSuccess() + ", outputPresent=" + (output != null) + ", errorCode="
                + (error == null ? null : error.getCode()) + ", execution=" + execution + '}';
    }
}
