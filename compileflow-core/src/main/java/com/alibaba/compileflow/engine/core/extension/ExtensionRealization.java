package com.alibaba.compileflow.engine.core.extension;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a class as an implementation (a "realization") of an {@link Extension}.
 * <p>
 * This annotation is used by the framework's extension discovery mechanism
 * (typically based on {@link java.util.ServiceLoader}) to identify and register
 * concrete extension implementations.
 * <p>
 * The {@link #priority()} attribute allows for ordering of extensions. When multiple
 * implementations are found for the same extension point, the one with the higher
 * priority value (lower numerical value) will be chosen or executed first, depending
 * on the invocation strategy.
 *
 * @author yusu
 * @see Extension
 * @see ExtensionInvoker
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface ExtensionRealization {

    /**
     * The priority of this extension implementation. A lower value indicates a
     * higher priority. The default priority is 500.
     *
     * @return The priority value.
     */
    int priority() default 500;

}
