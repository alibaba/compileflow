package com.alibaba.compileflow.engine.core.extension;

/**
 * @author yusu
 */
public interface ExtensionCallback<E extends Extension, T> {

    T execute(E extension) throws Exception;

}
