package com.alibaba.compileflow.engine.core.extension;

/**
 * A functional interface representing an action to be performed on an {@link Extension}.
 * This is used within the extension mechanism to execute custom logic.
 *
 * @param <E> The specific type of {@link Extension} this action operates on.
 * @author yusu
 */
@FunctionalInterface
public interface ExtensionAction<E extends Extension> {

    /**
     * Executes a specific action on the given extension instance.
     *
     * @param extension The extension instance to act upon.
     * @throws Exception if an error occurs during execution.
     */
    void execute(E extension) throws Exception;

}
