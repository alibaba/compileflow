package com.alibaba.compileflow.engine;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents the outcome of a process execution, encapsulating either a successful result
 * or a failure with an error message. It provides a functional-style API for handling
 * both cases.
 *
 * @param <T> The type of the successful result data.
 * @author yusu
 */
public class ProcessResult<T> {
    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final String traceId;

    private ProcessResult(boolean success, T data, String errorMessage, String traceId) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
    }

    /**
     * Creates a successful result with the given data.
     *
     * @param data The result data.
     * @param <T>  The type of the data.
     * @return A new successful {@code ProcessResult}.
     */
    public static <T> ProcessResult<T> success(T data) {
        return new ProcessResult<>(true, data, null, null);
    }

    /**
     * Creates a successful result with the given data and a trace ID.
     *
     * @param data    The result data.
     * @param traceId The trace identifier for this execution.
     * @param <T>     The type of the data.
     * @return A new successful {@code ProcessResult}.
     */
    public static <T> ProcessResult<T> success(T data, String traceId) {
        return new ProcessResult<>(true, data, null, traceId);
    }

    /**
     * Creates a failed result with an error message.
     *
     * @param errorMessage A message describing the failure.
     * @param <T>          The type of the data this result would have held on success.
     * @return A new failed {@code ProcessResult}.
     */
    public static <T> ProcessResult<T> failure(String errorMessage) {
        return new ProcessResult<>(false, null, errorMessage, null);
    }

    /**
     * Creates a failed result with an error message and a trace ID.
     *
     * @param errorMessage A message describing the failure.
     * @param traceId      The trace identifier for this execution.
     * @param <T>          The type of the data this result would have held on success.
     * @return A new failed {@code ProcessResult}.
     */
    public static <T> ProcessResult<T> failure(String errorMessage, String traceId) {
        return new ProcessResult<>(false, null, errorMessage, traceId);
    }

    /**
     * Checks if the process execution was successful.
     *
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Checks if the process execution failed.
     *
     * @return {@code true} if failed, {@code false} otherwise.
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Returns the result data if the execution was successful, otherwise {@code null}.
     *
     * @return The result data, or {@code null} on failure.
     */
    public T getData() {
        return data;
    }

    /**
     * Returns the error message if the execution failed, otherwise {@code null}.
     *
     * @return The error message, or {@code null} on success.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the trace identifier associated with this execution, if available.
     *
     * @return The trace ID, or {@code null}.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * If the execution was successful, applies the given mapping function to the data,
     * returning a new {@code ProcessResult} with the transformed data. If the execution failed,
     * it returns a new failed {@code ProcessResult} with the original error message.
     *
     * @param mapper A function to apply to the successful data.
     * @param <U>    The type of the data in the resulting {@code ProcessResult}.
     * @return A new {@code ProcessResult} with the mapped data or the original failure details.
     */
    public <U> ProcessResult<U> map(Function<T, U> mapper) {
        if (success && data != null) {
            try {
                U transformedData = mapper.apply(data);
                return new ProcessResult<>(true, transformedData, null, traceId);
            } catch (Exception e) {
                return new ProcessResult<>(false, null, "Data transformation failed: " + e.getMessage(), traceId);
            }
        }
        return new ProcessResult<>(false, null, errorMessage, traceId);
    }

    /**
     * If the execution was successful, performs the given action with the result data.
     *
     * @param action The action to perform on the successful data.
     * @return This {@code ProcessResult} instance for chaining.
     */
    public ProcessResult<T> onSuccess(Consumer<T> action) {
        if (success && data != null) {
            action.accept(data);
        }
        return this;
    }

    /**
     * If the execution failed, performs the given action with the error message.
     *
     * @param action The action to perform with the error message.
     * @return This {@code ProcessResult} instance for chaining.
     */
    public ProcessResult<T> onFailure(Consumer<String> action) {
        if (!success) {
            action.accept(errorMessage);
        }
        return this;
    }

    /**
     * Returns the result data if successful, otherwise returns the provided default value.
     *
     * @param defaultValue The value to return if the execution failed.
     * @return The result data or the default value.
     */
    public T orElse(T defaultValue) {
        return success ? data : defaultValue;
    }

    /**
     * Returns the result data if successful, otherwise throws a {@link RuntimeException}.
     *
     * @return The result data.
     * @throws RuntimeException if the process execution failed.
     */
    public T orElseThrow() {
        if (success) {
            return data;
        }
        throw new RuntimeException("Process execution failed: " + errorMessage);
    }

    /**
     * Returns the result data if successful, otherwise supplies a default value lazily.
     *
     * @param supplier default supplier when failed
     * @return data or supplier result
     */
    public T orElseGet(Supplier<T> supplier) {
        return success ? data : supplier.get();
    }

    /**
     * Returns data or throws the exception provided by the supplier when failed.
     *
     * @param exceptionSupplier supplier of exception to throw on failure
     * @param <X>               exception type
     * @return data when successful
     * @throws X if failed
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (success) {
            return data;
        }
        throw exceptionSupplier.get();
    }

    @Override
    public String toString() {
        return success
                ? "ProcessResult{success, traceId=" + traceId + ", data=" + String.valueOf(data) + "}"
                : "ProcessResult{failure, traceId=" + traceId + ", error='" + errorMessage + "'}";
    }

}
