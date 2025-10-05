package com.alibaba.compileflow.engine.core.extension;

/**
 * Extension chain callback interface for chained invocation scenarios.
 * Used in invokeChain scenarios where the result of one extension becomes the input for the next.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ExtensionChainCallback<E extends Extension, T> {

    /**
     * Executes the extension chain invocation.
     *
     * @param extension      Current extension instance
     * @param previousResult Result from the previous extension (initial value on first call)
     * @return Processing result from the current extension
     */
    T execute(E extension, T previousResult);
}
