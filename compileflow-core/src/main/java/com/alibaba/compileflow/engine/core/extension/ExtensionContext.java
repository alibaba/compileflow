package com.alibaba.compileflow.engine.core.extension;

/**
 * A marker interface for context objects passed to {@link Extension} implementations.
 * <p>
 * This interface allows extensions to receive relevant information about the current
 * operation, enabling them to make context-aware decisions. Implementations of this
 * interface should encapsulate the data needed by the extensions for a specific
 * extension point.
 *
 * @author yusu
 */
public interface ExtensionContext {

}
