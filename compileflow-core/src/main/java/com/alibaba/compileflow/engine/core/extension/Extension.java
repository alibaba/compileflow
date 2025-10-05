package com.alibaba.compileflow.engine.core.extension;

/**
 * A marker interface for all framework extensions.
 * <p>
 * Extensions are the primary mechanism for customizing or extending the functionality
 * of the CompileFlow engine. Implementations of this interface are typically discovered
 * at runtime using the Java {@link java.util.ServiceLoader} mechanism.
 * <p>
 * The {@link #support(ExtensionContext)} method allows an extension to dynamically
 * decide if it should be active for a given context, enabling conditional extensions.
 *
 * @param <Context> The type of the {@link ExtensionContext} that this extension operates on.
 * @author yusu
 */
public interface Extension<Context extends ExtensionContext> {

    /**
     * Determines if this extension should be applied in the given context.
     * <p>
     * Subclasses can override this method to implement conditional logic. For example,
     * an extension might only be active if a specific feature flag is enabled in the context.
     * By default, all extensions are considered supportive.
     *
     * @param context The current context, providing information that can be used
     *                to decide if the extension is applicable.
     * @return {@code true} if this extension supports the given context and should be
     * applied, {@code false} otherwise.
     */
    default boolean support(Context context) {
        return true;
    }

}
