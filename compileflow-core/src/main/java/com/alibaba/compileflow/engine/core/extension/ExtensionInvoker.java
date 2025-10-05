package com.alibaba.compileflow.engine.core.extension;


import java.util.List;
import java.util.function.BinaryOperator;

/**
 * An abstract singleton for discovering and invoking framework extensions.
 * <p>
 * This class provides a centralized mechanism for executing logic defined in {@link Extension}
 * implementations. It supports various invocation patterns, such as finding the first
 * matching extension, invoking all matching extensions, or chaining them together.
 * <p>
 * The concrete implementation of this class is responsible for the actual discovery
 * and lifecycle management of extensions, typically using the Java {@link java.util.ServiceLoader}.
 *
 * @author yusu
 */
public abstract class ExtensionInvoker {

    protected static ExtensionInvoker instance;

    public static ExtensionInvoker getInstance() {
        return instance;
    }

    /**
     * Finds and invokes the first extension that matches the given code.
     *
     * @param extensionCode     The unique code of the extension point.
     * @param extensionCallback The callback to execute with the found extension.
     * @param <T>               The return type of the callback.
     * @param <E>               The type of the {@link Extension}.
     * @return The result from the callback.
     */
    public abstract <T, E extends Extension> T invokeFirst(String extensionCode,
                                                           ExtensionCallback<E, T> extensionCallback);

    public abstract <T, E extends Extension> T invokeFirst(ExtensionContext context, String extensionCode,
                                                           ExtensionCallback<E, T> extensionCallback);

    public abstract <E extends Extension> void invokeFirst(String extensionCode,
                                                           ExtensionAction<E> extensionCallback);

    public abstract <E extends Extension> void invokeFirst(ExtensionContext context, String extensionCode,
                                                           ExtensionAction<E> extensionCallback);

    /**
     * Invokes the first matching extension in a fault-tolerant manner. If the extension
     * throws an exception, it is logged, and a fallback value is returned.
     *
     * @param extensionCode     The unique code of the extension point.
     * @param extensionCallback The callback to execute.
     * @param fallbackValue     The value to return if the invocation fails.
     * @return The result from the callback or the fallback value.
     */
    public abstract <T, E extends Extension> T invokeFirstSafe(String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback,
                                                               T fallbackValue);

    public abstract <T, E extends Extension> T invokeFirstSafe(ExtensionContext context, String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback,
                                                               T fallbackValue);

    /**
     * Invokes all extensions that match the given code and returns a list of their results.
     *
     * @param extensionCode     The unique code of the extension point.
     * @param extensionCallback The callback to execute for each extension.
     * @return A list of results from each invocation.
     */
    public abstract <T, E extends Extension> List<T> invokeAll(String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback);

    public abstract <T, E extends Extension> List<T> invokeAll(ExtensionContext context, String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback);

    /**
     * Invokes all matching extensions in a chain, where the output of one extension's
     * callback becomes the input for the next.
     *
     * @param extensionCode The unique code of the extension point.
     * @param initialValue  The initial value to pass to the first extension in the chain.
     * @param chainCallback The callback that defines the chain logic.
     * @return The final result after all extensions have been invoked.
     */
    public abstract <T, E extends Extension> T invokeChain(String extensionCode,
                                                           T initialValue,
                                                           ExtensionChainCallback<E, T> chainCallback);

    public abstract <T, E extends Extension> T invokeChain(ExtensionContext context, String extensionCode,
                                                           T initialValue,
                                                           ExtensionChainCallback<E, T> chainCallback);

    /**
     * Invokes all matching extensions and aggregates their results using a reduction function.
     *
     * @param extensionCode     The unique code of the extension point.
     * @param extensionCallback The callback to execute for each extension to get a value.
     * @param identity          The identity value for the reduction (the initial value).
     * @param reducer           A function to combine two results.
     * @return The final, aggregated result.
     */
    public abstract <T, R, E extends Extension> R invokeReduce(String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback,
                                                               R identity,
                                                               BinaryOperator<R> reducer);

    public abstract <T, R, E extends Extension> R invokeReduce(ExtensionContext context, String extensionCode,
                                                               ExtensionCallback<E, T> extensionCallback,
                                                               R identity,
                                                               BinaryOperator<R> reducer);

    public abstract Extension getFirstMatchingExtension(ExtensionContext context, String extensionCode);

    public abstract List<Extension> getAllMatchingExtensions(ExtensionContext context, String extensionCode);


}
