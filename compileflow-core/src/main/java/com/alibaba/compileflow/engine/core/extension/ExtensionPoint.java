package com.alibaba.compileflow.engine.core.extension;

import java.lang.annotation.*;

/**
 * Marks a method as an extension point, which is a specific location in the
 * framework that can be customized by {@link Extension} implementations.
 * <p>
 * This annotation is used to formally define the contract for an extension.
 * The {@link #code()} attribute provides a unique identifier that links an
 * extension realization to its corresponding extension point.
 *
 * @author yusu
 * @see Extension
 * @see ExtensionRealization
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Inherited
public @interface ExtensionPoint {

    /**
     * A unique code that identifies this extension point. This code is used by
     * {@link ExtensionRealization} annotations to specify which extension point
     * a particular implementation targets.
     *
     * @return The unique extension code.
     */
    String code();

    /**
     * An optional description of the extension point's purpose.
     *
     * @return The description.
     */
    String desc() default "";

}
